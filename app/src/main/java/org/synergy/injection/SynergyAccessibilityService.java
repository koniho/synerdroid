package org.synergy.injection;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public final class SynergyAccessibilityService extends AccessibilityService {
    private static volatile SynergyAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private CursorOverlayView cursorView;
    private WindowManager.LayoutParams cursorParams;

    public static SynergyAccessibilityService getInstance() { return instance; }
    public static boolean isReady() { return instance != null; }

    @Override
    protected void onServiceConnected() {
        instance = this;
        createCursorOverlay();
    }

    @Override
    public void onDestroy() {
        removeCursorOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    public int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    public int getScreenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    public void movePointer(int x, int y) {
        mainHandler.post(() -> {
            if (cursorView == null || cursorParams == null) return;
            cursorParams.x = x;
            cursorParams.y = y;
            cursorView.setVisibility(android.view.View.VISIBLE);
            windowManager.updateViewLayout(cursorView, cursorParams);
        });
    }

    private void createCursorOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        cursorView = new CursorOverlayView(this);
        int size = Math.round(36 * getResources().getDisplayMetrics().density);
        cursorParams = new WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        cursorParams.gravity = Gravity.TOP | Gravity.START;
        cursorParams.x = 0;
        cursorParams.y = 0;
        cursorView.setVisibility(android.view.View.INVISIBLE);
        windowManager.addView(cursorView, cursorParams);
    }

    private void removeCursorOverlay() {
        if (windowManager != null && cursorView != null) {
            try { windowManager.removeView(cursorView); } catch (RuntimeException ignored) { }
        }
        cursorView = null;
        cursorParams = null;
    }

    public void click(int x, int y, int buttonId) {
        if (buttonId != 1 && buttonId != 0) return;
        mainHandler.post(() -> gesture(x, y, x, y, 1));
    }

    public void scroll(int x, int y, int amount) {
        if (amount == 0) return;
        int distance = Math.max(180, getScreenHeight() / 3);
        int endY = Math.max(1, Math.min(getScreenHeight() - 1, y + (amount > 0 ? -distance : distance)));
        mainHandler.post(() -> gesture(x, y, x, endY, 250));
    }

    private void gesture(float startX, float startY, float endX, float endY, long duration) {
        Path path = new Path();
        path.moveTo(startX, startY);
        if (startX != endX || startY != endY) path.lineTo(endX, endY);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, duration);
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    public void keyDown(int key, int mask) {
        mainHandler.post(() -> handleKey(key));
    }

    private void handleKey(int key) {
        if (key == 27 || key == 61211) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null || !focused.isEditable()) return;

        CharSequence currentValue = focused.getText();
        String current = currentValue == null ? "" : currentValue.toString();
        String next;
        if (key == 8 || key == 61192) {
            next = current.isEmpty() ? current : current.substring(0, current.offsetByCodePoints(current.length(), -1));
        } else if (key == 10 || key == 13 || key == 61197) {
            next = current + "\n";
        } else if (key >= 32 && key <= Character.MAX_CODE_POINT && Character.isValidCodePoint(key)) {
            next = current + new String(Character.toChars(key));
        } else {
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }
}
