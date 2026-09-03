package io.github.koniho.synerdroid.injection;
// Modified for Synerdroid by Alexander Ho, 2026.

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;

/** Synergy's remote-input bridge with a compact fallback on-screen keyboard. */
public final class SynerdroidInputMethodService extends InputMethodService {
    private static volatile SynerdroidInputMethodService instance;
    private LinearLayout keyboardView;
    private boolean shifted;

    public static boolean isReady() { return instance != null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public View onCreateInputView() {
        keyboardView = new LinearLayout(this);
        keyboardView.setOrientation(LinearLayout.VERTICAL);
        keyboardView.setPadding(dp(4), dp(6), dp(4), dp(8));
        keyboardView.setBackgroundColor(Color.rgb(32, 32, 36));
        addTextRow("1234567890");
        addTextRow("qwertyuiop");
        addTextRow("asdfghjkl");
        addTextRow("zxcvbnm");

        LinearLayout actions = newRow();
        actions.addView(key("⇧", 1.1f, view -> toggleShift()));
        actions.addView(key("⌫", 1.1f, view -> sendAndroidKey(KeyEvent.KEYCODE_DEL)));
        actions.addView(key("space", 3.2f, view -> commit(" ")));
        actions.addView(key("↵", 1.1f, view -> sendAndroidKey(KeyEvent.KEYCODE_ENTER)));
        actions.addView(key("🌐", 1.1f, view -> switchKeyboard()));
        keyboardView.addView(actions);
        return keyboardView;
    }

    private void addTextRow(String characters) {
        LinearLayout row = newRow();
        for (int i = 0; i < characters.length(); i++) {
            String value = String.valueOf(characters.charAt(i));
            row.addView(key(value, 1f, view -> {
                String text = ((Button) view).getText().toString();
                commit(text);
                if (shifted) toggleShift();
            }));
        }
        keyboardView.addView(row);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(2), 0, dp(2));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        return row;
    }

    private Button key(String label, float weight, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(66, 66, 72));
        background.setCornerRadius(dp(7));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }

    private void toggleShift() {
        shifted = !shifted;
        if (keyboardView == null) return;
        for (int rowIndex = 0; rowIndex < keyboardView.getChildCount(); rowIndex++) {
            View rowView = keyboardView.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            for (int keyIndex = 0; keyIndex < row.getChildCount(); keyIndex++) {
                View keyView = row.getChildAt(keyIndex);
                if (!(keyView instanceof Button)) continue;
                Button button = (Button) keyView;
                String text = button.getText().toString();
                if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
                    button.setText(shifted ? text.toUpperCase() : text.toLowerCase());
                }
            }
        }
    }

    private void commit(String text) {
        InputConnection input = getCurrentInputConnection();
        if (input != null) input.commitText(text, 1);
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
        InputConnection input = service.getCurrentInputConnection();
        if (input == null) return false;

        int androidKey = mapSpecialKey(key);
        if (androidKey != KeyEvent.KEYCODE_UNKNOWN) {
            input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, androidKey));
            input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, androidKey));
            return true;
        }
        if (key >= 32 && key < 0xE000 && Character.isValidCodePoint(key)) {
            return input.commitText(new String(Character.toChars(key)), 1);
        }
        return false;
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
