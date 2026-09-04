package io.github.koniho.synerdroid.injection;
// Modified for Synerdroid by Alexander Ho, 2026.

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.SparseArray;

public final class SynerdroidAccessibilityService extends AccessibilityService {
    private static volatile SynerdroidAccessibilityService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private CursorOverlayView cursorView;
    private WindowManager.LayoutParams cursorParams;
    private DisplayManager displayManager;
    private Display activeDisplay;

    public static SynerdroidAccessibilityService getInstance() { return instance; }
    public static boolean isReady() { return instance != null; }

    @Override
    protected void onServiceConnected() {
        instance = this;
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        activeDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
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
        return displayContext().getResources().getDisplayMetrics().widthPixels;
    }

    public int getScreenHeight() {
        return displayContext().getResources().getDisplayMetrics().heightPixels;
    }

    public int getActiveDisplayId() {
        return activeDisplay == null ? Display.DEFAULT_DISPLAY : activeDisplay.getDisplayId();
    }

    public synchronized int[] moveAcrossDisplays(int x, int y, int dx, int dy) {
        RectF currentBounds = topologyBounds(getActiveDisplayId());
        if (currentBounds == null) {
            return new int[] {
                    Math.max(0, Math.min(getScreenWidth() - 1, x + dx)),
                    Math.max(0, Math.min(getScreenHeight() - 1, y + dy))
            };
        }
        float currentDensity = displayContext().getResources().getDisplayMetrics().density;
        float globalX = currentBounds.left + (x + dx) / currentDensity;
        float globalY = currentBounds.top + (y + dy) / currentDensity;
        SparseArray<RectF> topology = topologyBounds();
        for (int i = 0; i < topology.size(); i++) {
            RectF bounds = topology.valueAt(i);
            if (!bounds.contains(globalX, globalY)) continue;
            Display destination = displayManager.getDisplay(topology.keyAt(i));
            if (destination == null || destination.getState() == Display.STATE_OFF) continue;
            if (destination.getDisplayId() != getActiveDisplayId()) activateDisplay(destination);
            float density = displayContext().getResources().getDisplayMetrics().density;
            return new int[] {
                    Math.max(0, Math.min(getScreenWidth() - 1,
                            Math.round((globalX - bounds.left) * density))),
                    Math.max(0, Math.min(getScreenHeight() - 1,
                            Math.round((globalY - bounds.top) * density)))
            };
        }
        return new int[] {
                Math.max(0, Math.min(getScreenWidth() - 1, x + dx)),
                Math.max(0, Math.min(getScreenHeight() - 1, y + dy))
        };
    }

    @SuppressWarnings("unchecked")
    private SparseArray<RectF> topologyBounds() {
        if (displayManager == null || Build.VERSION.SDK_INT < 36) return new SparseArray<>();
        try {
            Object topology = DisplayManager.class.getMethod("getDisplayTopology")
                    .invoke(displayManager);
            if (topology == null) return new SparseArray<>();
            return (SparseArray<RectF>) topology.getClass().getMethod("getAbsoluteBounds")
                    .invoke(topology);
        } catch (ReflectiveOperationException | ClassCastException error) {
            return new SparseArray<>();
        }
    }

    private RectF topologyBounds(int displayId) {
        SparseArray<RectF> bounds = topologyBounds();
        return bounds.get(displayId);
    }

    private void activateDisplay(Display display) {
        activeDisplay = display;
        mainHandler.post(this::createCursorOverlay);
    }

    private android.content.Context displayContext() {
        return activeDisplay == null ? this : createDisplayContext(activeDisplay);
    }

    public void movePointer(int x, int y) {
        mainHandler.post(() -> {
            if (cursorView == null) return;
            cursorView.setVisibility(android.view.View.VISIBLE);
            cursorView.setPointerPosition(x, y);
        });
    }

    public void hidePointer() {
        mainHandler.post(() -> {
            if (cursorView != null) cursorView.setVisibility(android.view.View.INVISIBLE);
        });
    }

    private void createCursorOverlay() {
        removeCursorOverlay();
        android.content.Context context = displayContext();
        windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        cursorView = new CursorOverlayView(context);
        cursorParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        cursorParams.gravity = Gravity.TOP | Gravity.START;
        cursorParams.x = 0;
        cursorParams.y = 0;
        cursorView.setVisibility(android.view.View.INVISIBLE);
        windowManager.addView(cursorView, cursorParams);
    }

    private int getOverlayTopInset() {
        if (cursorView == null || cursorView.getRootWindowInsets() == null) return 0;
        return cursorView.getRootWindowInsets()
                .getSystemWindowInsetTop();
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

    public void drag(int startX, int startY, int endX, int endY) {
        mainHandler.post(() -> gesture(startX, startY, endX, endY, 450));
    }

    public void showRecents() {
        mainHandler.post(() -> performGlobalAction(GLOBAL_ACTION_RECENTS));
    }

    public void showRecentsAndMove(boolean towardRight) {
        mainHandler.post(() -> {
            performGlobalAction(GLOBAL_ACTION_RECENTS);
            mainHandler.postDelayed(() -> moveRecents(towardRight), 300);
        });
    }

    public void moveRecents(boolean towardRight) {
        int width = getScreenWidth();
        int height = getScreenHeight();
        float startX = width * (towardRight ? 0.72f : 0.28f);
        float endX = width * (towardRight ? 0.28f : 0.72f);
        mainHandler.post(() -> gesture(startX, height * 0.55f, endX, height * 0.55f, 220));
    }

    public void selectRecents() {
        int width = getScreenWidth();
        int height = getScreenHeight();
        mainHandler.postDelayed(() -> gesture(width * 0.5f, height * 0.55f,
                width * 0.5f, height * 0.55f, 1), 600);
    }

    public void showNotifications() {
        mainHandler.post(() -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS));
    }

    public void goBack() {
        mainHandler.post(() -> performGlobalAction(GLOBAL_ACTION_BACK));
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
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(stroke);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activeDisplay != null) {
            builder.setDisplayId(activeDisplay.getDisplayId());
        }
        dispatchGesture(builder.build(), null, null);
    }

    public void keyDown(int key, int mask) {
        mainHandler.post(() -> handleKey(key));
    }

    private void handleKey(int key) {
        if (Injection.isModifierKey(key)) return;
        if (key == 27 || key == 61211 || key == 65288 || key == 65307 || key == 269025062) {
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
        } else if (key >= 32 && key <= Character.MAX_CODE_POINT
                && !(key >= 0xE000 && key <= 0xF8FF)
                && Character.isValidCodePoint(key)) {
            next = current + new String(Character.toChars(key));
        } else {
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }
}
