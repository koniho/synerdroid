package io.github.koniho.synerdroid.injection;
// Modified for Synerdroid by Alexander Ho, 2026.

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;

import io.github.koniho.synerdroid.diagnostics.CrashReporter;

/** Synergy's remote-input bridge with a compact fallback on-screen keyboard. */
public final class SynerdroidInputMethodService extends InputMethodService {
    private static volatile SynerdroidInputMethodService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object remoteDeleteLock = new Object();
    private int pendingRemoteDeletes;
    private boolean remoteDeleteScheduled;
    private LinearLayout keyboardView;
    private boolean shifted;
    private boolean symbols;

    public static boolean isReady() { return instance != null; }

    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
        instance = this;
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public View onCreateInputView() {
        keyboardView = new LinearLayout(this);
        keyboardView.setOrientation(LinearLayout.VERTICAL);
        keyboardView.setClipChildren(false);
        keyboardView.setPadding(dp(4), dp(6), dp(4), dp(64));
        keyboardView.setBackgroundColor(Color.rgb(18, 26, 30));
        buildKeyboard();
        return keyboardView;
    }

    private void buildKeyboard() {
        keyboardView.removeAllViews();
        if (symbols) {
            addTextRow("1234567890", 0f, 0f);
            addTextRow("@#$_&-+()/", 0f, 0f);
            addTextRow("*\"':;!?%", 0.5f, 0.5f);
            addTextRow("[]{}<>\\=", 0.6f, 0.6f);
        } else {
            addTextRow("1234567890", 0f, 0f);
            addTextRow("qwertyuiop", 0f, 0f);
            addTextRow("asdfghjkl", 0.55f, 0.55f);
            LinearLayout letters = newRow();
            letters.addView(key("\u21e7", 1.55f, view -> toggleShift(), true));
            addCharacters(letters, "zxcvbnm");
            letters.addView(key("\u232b", 1.55f, view -> deleteBackward(), true));
            keyboardView.addView(letters);
        }
        LinearLayout bottom = newRow();
        bottom.addView(key(symbols ? "ABC" : "?123", 1.55f, view -> {
            symbols = !symbols;
            shifted = false;
            buildKeyboard();
        }, true));
        bottom.addView(key(",", 1f, view -> commit(","), false));
        bottom.addView(key("space", 5.1f, view -> commit(" "), false));
        bottom.addView(key(".", 1f, view -> commit("."), false));
        bottom.addView(key("\u21b5", 1.55f, view -> sendAndroidKey(KeyEvent.KEYCODE_ENTER), true));
        keyboardView.addView(bottom);
    }

    private void addTextRow(String characters, float leftWeight, float rightWeight) {
        LinearLayout row = newRow();
        if (leftWeight > 0f) row.addView(spacer(leftWeight));
        addCharacters(row, characters);
        if (rightWeight > 0f) row.addView(spacer(rightWeight));
        keyboardView.addView(row);
    }

    private void addCharacters(LinearLayout row, String characters) {
        for (int i = 0; i < characters.length(); i++) {
            String value = String.valueOf(characters.charAt(i));
            row.addView(key(value, 1f, view -> {
                String text = ((Button) view).getText().toString();
                commit(text);
                if (shifted) toggleShift();
            }, false));
        }
    }

    private Space spacer(float weight) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight));
        return space;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(2), 0, dp(2));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        return row;
    }

    private Button key(String label, float weight, View.OnClickListener listener, boolean special) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(label.length() > 2 ? 15 : 22);
        button.setTextColor(Color.rgb(225, 233, 238));
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setStateListAnimator(null);
        GradientDrawable background = new GradientDrawable();
        background.setColor(special ? Color.rgb(47, 70, 82) : Color.rgb(40, 50, 56));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        button.setHapticFeedbackEnabled(true);
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                view.animate().scaleX(2f).scaleY(2f).setDuration(70).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
        return button;
    }

    private void toggleShift() {
        shifted = !shifted;
        buildKeyboard();
        if (!symbols && shifted) {
            for (int rowIndex = 1; rowIndex <= 3; rowIndex++) {
                LinearLayout row = (LinearLayout) keyboardView.getChildAt(rowIndex);
                for (int keyIndex = 0; keyIndex < row.getChildCount(); keyIndex++) {
                    View keyView = row.getChildAt(keyIndex);
                    if (!(keyView instanceof Button)) continue;
                    Button button = (Button) keyView;
                    String text = button.getText().toString();
                    if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
                        button.setText(text.toUpperCase());
                    }
                }
            }
        }
    }

    private void commit(String text) {
        InputConnection input = getCurrentInputConnection();
        if (input != null) input.commitText(text, 1);
    }

    private void deleteBackward() {
        deleteBackward(1);
    }

    private void deleteBackward(int count) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        try {
            input.beginBatchEdit();
            CharSequence selected = input.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                input.commitText("", 1);
            } else if (!input.deleteSurroundingText(count, 0)) {
                input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
            }
        } catch (RuntimeException ignored) {
            // The target editor may disappear while a key is being handled.
        } finally {
            try { input.endBatchEdit(); } catch (RuntimeException ignored) { }
        }
    }

    private void sendAndroidKey(int keyCode) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void switchKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        manager.showInputMethodPicker();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static boolean sendKey(int key) {
        SynerdroidInputMethodService service = instance;
        if (service == null) return false;
        int androidKey = mapSpecialKey(key);
        boolean printable = key >= 32 && key < 0xE000 && Character.isValidCodePoint(key);
        if (androidKey == KeyEvent.KEYCODE_UNKNOWN && !printable) return false;
        if (androidKey == KeyEvent.KEYCODE_DEL) {
            service.queueRemoteDelete();
        } else {
            service.mainHandler.post(() -> service.dispatchRemoteKey(key, androidKey));
        }
        return true;
    }

    private void queueRemoteDelete() {
        synchronized (remoteDeleteLock) {
            pendingRemoteDeletes = Math.min(64, pendingRemoteDeletes + 1);
            if (remoteDeleteScheduled) return;
            remoteDeleteScheduled = true;
        }
        mainHandler.post(this::drainRemoteDeletes);
    }

    private void drainRemoteDeletes() {
        int count;
        synchronized (remoteDeleteLock) {
            count = pendingRemoteDeletes;
            pendingRemoteDeletes = 0;
            remoteDeleteScheduled = false;
        }
        if (count > 0) deleteBackward(count);
    }

    private void dispatchRemoteKey(int key, int androidKey) {
        animateRemoteKey(key, androidKey);
        try {
            if (androidKey == KeyEvent.KEYCODE_DEL) {
                deleteBackward();
            } else if (androidKey != KeyEvent.KEYCODE_UNKNOWN) {
                sendAndroidKey(androidKey);
            } else {
                InputConnection input = getCurrentInputConnection();
                if (input != null) input.commitText(new String(Character.toChars(key)), 1);
            }
        } catch (RuntimeException ignored) {
            // A focused editor may close while queued remote input is draining.
        }
    }

    private void animateRemoteKey(int key, int androidKey) {
        if (keyboardView == null) return;
        String wanted = androidKey == KeyEvent.KEYCODE_DEL ? "\u232b"
                : new String(Character.toChars(key));
        for (int rowIndex = 0; rowIndex < keyboardView.getChildCount(); rowIndex++) {
            View rowView = keyboardView.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            for (int keyIndex = 0; keyIndex < row.getChildCount(); keyIndex++) {
                View keyView = row.getChildAt(keyIndex);
                if (!(keyView instanceof Button)) continue;
                Button button = (Button) keyView;
                if (!button.getText().toString().equalsIgnoreCase(wanted)) continue;
                button.animate().cancel();
                button.setScaleX(2f);
                button.setScaleY(2f);
                button.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                return;
            }
        }
    }

    private static int mapSpecialKey(int key) {
        switch (key) {
            case 8: case 61192: case 65288: return KeyEvent.KEYCODE_DEL;
            case 9: case 61193: case 65289: return KeyEvent.KEYCODE_TAB;
            case 10: case 13: case 61197: case 65293: return KeyEvent.KEYCODE_ENTER;
            case 61265: case 65361: return KeyEvent.KEYCODE_DPAD_LEFT;
            case 61266: case 65362: return KeyEvent.KEYCODE_DPAD_UP;
            case 61267: case 65363: return KeyEvent.KEYCODE_DPAD_RIGHT;
            case 61268: case 65364: return KeyEvent.KEYCODE_DPAD_DOWN;
            case 61222: case 65360: return KeyEvent.KEYCODE_MOVE_HOME;
            case 61223: case 65367: return KeyEvent.KEYCODE_MOVE_END;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
