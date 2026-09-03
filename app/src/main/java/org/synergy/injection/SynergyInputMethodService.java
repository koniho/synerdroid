package org.synergy.injection;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

/** Optional IME bridge for rootless text and navigation-key delivery. */
public final class SynergyInputMethodService extends InputMethodService {
    private static volatile SynergyInputMethodService instance;

    public static boolean isReady() { return instance != null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean sendKey(int key) {
        SynergyInputMethodService service = instance;
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
