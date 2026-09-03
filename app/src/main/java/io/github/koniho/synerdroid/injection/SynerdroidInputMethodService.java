package io.github.koniho.synerdroid.injection;
// Modified for Synerdroid by Alexander Ho, 2026.

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.PopupWindow;
import android.widget.TextView;

import io.github.koniho.synerdroid.diagnostics.CrashReporter;

import java.util.Map;
import java.util.WeakHashMap;

/** Synergy's remote-input bridge with a compact fallback on-screen keyboard. */
public final class SynerdroidInputMethodService extends InputMethodService {
    private static volatile SynerdroidInputMethodService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object remoteDeleteLock = new Object();
    private int pendingRemoteDeletes;
    private boolean remoteDeleteScheduled;
    private PopupWindow keyPreview;
    private Runnable repeatingKey;
    private Button forwardedTouchKey;
    private final Runnable hidePreview = this::dismissKeyPreview;
    private final Map<Button, ValueAnimator> keyHighlights = new WeakHashMap<>();
    private LinearLayout keyboardView;
    private boolean shifted;
    private boolean symbols;
    private boolean alternateSymbols;

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
        keyboardView.setOnTouchListener((view, event) -> forwardBelowSpacebar(event));
        buildKeyboard();
        return keyboardView;
    }

    private void buildKeyboard() {
        keyboardView.removeAllViews();
        if (symbols) {
            if (alternateSymbols) {
                addTextRow("~\u0060|\u2022\u221a\u03c0\u00f7\u00d7\u00b6\u0394", 0f, 0f);
                addTextRow("\u00a3\u00a2\u20ac\u00a5^\u00b0={}\\", 0f, 0f);
            } else {
                addTextRow("1234567890", 0f, 0f);
                addTextRow("@#$_&-+()/", 0f, 0f);
            }
            LinearLayout symbolsLast = newRow();
            symbolsLast.addView(key(alternateSymbols ? "?123" : "=\\<", 1.55f, view -> {
                alternateSymbols = !alternateSymbols;
                buildKeyboard();
            }, true));
            addCharacters(symbolsLast, alternateSymbols ? "[]<>\u00a7\u00a9\u00ae" : "*\"':;!?");
            symbolsLast.addView(key("\u232b", 1.55f, view -> deleteBackward(), true));
            keyboardView.addView(symbolsLast);
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
            alternateSymbols = false;
            shifted = false;
            buildKeyboard();
        }, true));
        bottom.addView(key(",", 1f, view -> commit(","), false));
        if (symbols) {
            bottom.addView(key(alternateSymbols ? "2/2" : "1/2", 1f, view -> {
                alternateSymbols = !alternateSymbols;
                buildKeyboard();
            }, true));
        }
        bottom.addView(key("space", symbols ? 4.1f : 5.1f, view -> commit(" "), false));
        bottom.addView(key(".", 1f, view -> commit("."), false));
        bottom.addView(key("\u21b5", 1.55f, view -> sendAndroidKey(KeyEvent.KEYCODE_ENTER), true));
        keyboardView.addView(bottom);
    }

    private void addTextRow(String characters, float leftWeight, float rightWeight) {
        LinearLayout row = newRow();
        View left = leftWeight > 0f ? spacer(leftWeight) : null;
        if (left != null) row.addView(left);
        addCharacters(row, characters);
        if (left != null) forwardTouches(left);
        if (rightWeight > 0f) {
            View right = spacer(rightWeight);
            forwardTouches(right);
            row.addView(right);
        }
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

    private View spacer(float weight) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight));
        return space;
    }

    private void forwardTouches(View area) {
        area.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                forwardedTouchKey = findClosestKey(event.getRawX(), event.getRawY());
            }
            Button target = forwardedTouchKey;
            if (target == null) return true;
            boolean handled = target.dispatchTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                forwardedTouchKey = null;
            }
            return handled;
        });
    }

    private boolean forwardBelowSpacebar(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            Button spacebar = findKeyByLabel("space");
            if (spacebar == null) return false;
            int[] location = new int[2];
            spacebar.getLocationOnScreen(location);
            boolean below = event.getRawY() >= location[1] + spacebar.getHeight();
            boolean aligned = event.getRawX() >= location[0]
                    && event.getRawX() < location[0] + spacebar.getWidth();
            if (!below || !aligned) return false;
            forwardedTouchKey = spacebar;
        }
        Button target = forwardedTouchKey;
        if (target == null) return false;
        boolean handled = target.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            forwardedTouchKey = null;
        }
        return handled;
    }

    private Button findKeyByLabel(String label) {
        for (int rowIndex = 0; rowIndex < keyboardView.getChildCount(); rowIndex++) {
            View rowView = keyboardView.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            for (int keyIndex = 0; keyIndex < row.getChildCount(); keyIndex++) {
                View key = row.getChildAt(keyIndex);
                if (key instanceof Button
                        && ((Button) key).getText().toString().equals(label)) {
                    return (Button) key;
                }
            }
        }
        return null;
    }

    private Button findClosestKey(float screenX, float screenY) {
        Button closest = null;
        double closestDistance = Double.MAX_VALUE;
        int[] location = new int[2];
        for (int rowIndex = 0; rowIndex < keyboardView.getChildCount(); rowIndex++) {
            View rowView = keyboardView.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            for (int keyIndex = 0; keyIndex < row.getChildCount(); keyIndex++) {
                View candidate = row.getChildAt(keyIndex);
                if (!(candidate instanceof Button) || candidate.getVisibility() != View.VISIBLE) continue;
                candidate.getLocationOnScreen(location);
                float dx = screenX - (location[0] + candidate.getWidth() / 2f);
                float dy = screenY - (location[1] + candidate.getHeight() / 2f);
                double distance = dx * dx + dy * dy;
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = (Button) candidate;
                }
            }
        }
        return closest;
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, 0);
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
        button.setBackground(new InsetDrawable(background, dp(3), dp(2), dp(3), dp(2)));
        button.setTag(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(0, 0, 0, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        button.setHapticFeedbackEnabled(true);
        button.setOnTouchListener((view, event) -> {
            Button touched = (Button) view;
            String text = touched.getText().toString();
            boolean repeatable = isRepeatable(text);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                highlightKey(touched);
                showKeyPreview(touched, text);
                if (repeatable) {
                    touched.performClick();
                    repeatingKey = new Runnable() {
                        @Override public void run() {
                            if (repeatingKey != this) return;
                            touched.performClick();
                            touched.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            mainHandler.postDelayed(this, 65);
                        }
                    };
                    mainHandler.postDelayed(repeatingKey, 400);
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (!repeatable && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    touched.performClick();
                }
                stopKeyRepeat();
                dismissKeyPreview();
            }
            return true;
        });
        return button;
    }

    private void highlightKey(Button button) {
        Object tag = button.getTag();
        if (!(tag instanceof GradientDrawable)) return;
        ValueAnimator previous = keyHighlights.remove(button);
        if (previous != null) previous.cancel();
        boolean special = button.getText().length() > 1
                || button.getText().toString().equals("\u21e7")
                || button.getText().toString().equals("\u21b5")
                || button.getText().toString().equals("\u232b");
        int base = special ? Color.rgb(47, 70, 82) : Color.rgb(40, 50, 56);
        int accent = Color.rgb(167, 128, 255);
        GradientDrawable shape = (GradientDrawable) tag;
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), base, accent, base);
        animator.setDuration(240);
        animator.addUpdateListener(value -> shape.setColor((Integer) value.getAnimatedValue()));
        keyHighlights.put(button, animator);
        animator.start();
    }

    private boolean isRepeatable(String label) {
        return label.length() == 1 && !label.equals("\u21e7") && !label.equals("\u21b5")
                || label.equals("\u232b");
    }

    private void stopKeyRepeat() {
        Runnable repeat = repeatingKey;
        repeatingKey = null;
        if (repeat != null) mainHandler.removeCallbacks(repeat);
    }

    private void showKeyPreview(Button anchor, String label) {
        dismissKeyPreview();
        if (label.length() != 1) return;
        TextView preview = new TextView(this);
        preview.setText(label);
        preview.setTextSize(28);
        preview.setTextColor(Color.WHITE);
        preview.setGravity(Gravity.CENTER);
        GradientDrawable bubble = new GradientDrawable();
        bubble.setShape(GradientDrawable.OVAL);
        bubble.setColor(Color.rgb(75, 92, 102));
        preview.setBackground(bubble);
        int size = dp(60);
        keyPreview = new PopupWindow(preview, size, size, false);
        keyPreview.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        keyPreview.setClippingEnabled(false);
        keyPreview.setElevation(dp(12));
        keyPreview.showAsDropDown(anchor, (anchor.getWidth() - size) / 2,
                -anchor.getHeight() - size - dp(8));
    }

    private void dismissKeyPreview() {
        if (keyPreview != null) {
            keyPreview.dismiss();
            keyPreview = null;
        }
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
        sendAndroidKey(keyCode, 0);
    }

    private void sendAndroidKey(int keyCode, int metaState) {
        InputConnection input = getCurrentInputConnection();
        if (input == null) return;
        input.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState));
        input.sendKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState));
    }

    private void switchKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        manager.showInputMethodPicker();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static boolean sendKey(int key, int mask) {
        SynerdroidInputMethodService service = instance;
        if (service == null) return false;
        int androidKey = mapSpecialKey(key);
        boolean printable = key >= 32 && key < 0xE000 && Character.isValidCodePoint(key);
        if (androidKey == KeyEvent.KEYCODE_UNKNOWN && !printable) return false;
        if (androidKey == KeyEvent.KEYCODE_DEL) {
            service.queueRemoteDelete();
        } else {
            service.mainHandler.post(() -> service.dispatchRemoteKey(key, androidKey, mask));
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

    private void dispatchRemoteKey(int key, int androidKey, int mask) {
        animateRemoteKey(key, androidKey);
        try {
            if (androidKey == KeyEvent.KEYCODE_DEL) {
                deleteBackward();
            } else if (androidKey != KeyEvent.KEYCODE_UNKNOWN) {
                sendAndroidKey(androidKey, metaState(mask));
            } else {
                InputConnection input = getCurrentInputConnection();
                if (input != null) {
                    int modifiedKeyCode = androidKeyCodeForCharacter(key);
                    int meta = metaState(mask);
                    if (modifiedKeyCode != KeyEvent.KEYCODE_UNKNOWN
                            && (meta & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON
                            | KeyEvent.META_META_ON)) != 0) {
                        sendAndroidKey(modifiedKeyCode, meta);
                    } else {
                        String text = new String(Character.toChars(key));
                        if ((meta & KeyEvent.META_SHIFT_ON) != 0) text = text.toUpperCase();
                        input.commitText(text, 1);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A focused editor may close while queued remote input is draining.
        }
    }

    private int metaState(int mask) {
        int meta = 0;
        if ((mask & 0x0001) != 0) meta |= KeyEvent.META_SHIFT_ON;
        if ((mask & 0x0002) != 0) meta |= KeyEvent.META_CTRL_ON;
        if ((mask & 0x0004) != 0) meta |= KeyEvent.META_ALT_ON;
        if ((mask & 0x0018) != 0) meta |= KeyEvent.META_META_ON;
        if ((mask & 0x1000) != 0) meta |= KeyEvent.META_CAPS_LOCK_ON;
        if ((mask & 0x2000) != 0) meta |= KeyEvent.META_NUM_LOCK_ON;
        return meta;
    }

    private int androidKeyCodeForCharacter(int key) {
        if (key >= "a".charAt(0) && key <= "z".charAt(0)) {
            return KeyEvent.KEYCODE_A + key - "a".charAt(0);
        }
        if (key >= "A".charAt(0) && key <= "Z".charAt(0)) {
            return KeyEvent.KEYCODE_A + key - "A".charAt(0);
        }
        if (key >= "0".charAt(0) && key <= "9".charAt(0)) {
            return KeyEvent.KEYCODE_0 + key - "0".charAt(0);
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    private void animateRemoteKey(int key, int androidKey) {
        if (keyboardView == null) return;
        String wanted = androidKey == KeyEvent.KEYCODE_DEL ? "\u232b"
                : key == 32 ? "space" : new String(Character.toChars(key));
        for (int rowIndex = 0; rowIndex < keyboardView.getChildCount(); rowIndex++) {
            View rowView = keyboardView.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            for (int keyIndex = 0; keyIndex < row.getChildCount(); keyIndex++) {
                View keyView = row.getChildAt(keyIndex);
                if (!(keyView instanceof Button)) continue;
                Button button = (Button) keyView;
                if (!button.getText().toString().equalsIgnoreCase(wanted)) continue;
                highlightKey(button);
                showKeyPreview(button, button.getText().toString());
                mainHandler.removeCallbacks(hidePreview);
                mainHandler.postDelayed(hidePreview, 180);
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
