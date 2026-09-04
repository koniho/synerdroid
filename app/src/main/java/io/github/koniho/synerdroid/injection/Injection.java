package io.github.koniho.synerdroid.injection;
// Modified for Synerdroid by Alexander Ho, 2026.

/** Rootless input bridge backed by the enabled Synergy accessibility service. */
public final class Injection {
    private static int pointerX;
    private static int pointerY;
    private static int pressX;
    private static int pressY;
    private static int pressDisplayId;
    private static int pressedButton = -1;
    private static boolean screenFocused;
    private static float pointerSpeed = 1.25f;
    private static float remainderX;
    private static float remainderY;
    private static boolean invertScroll;
    private static boolean switchingApps;
    public static final int DISPLAY_RIGHT = 0;
    public static final int DISPLAY_LEFT = 1;
    public static final int DISPLAY_ABOVE = 2;
    public static final int DISPLAY_BELOW = 3;
    private static int externalDisplayPosition = DISPLAY_RIGHT;

    private Injection() { }

    public static void setPermissionsForInputDevice() { }

    public static boolean isReady() {
        return SynerdroidAccessibilityService.isReady();
    }

    public static void setInvertScroll(boolean invert) {
        invertScroll = invert;
    }

    public static void setPointerSpeed(float speed) {
        pointerSpeed = Math.max(0.5f, Math.min(2.0f, speed));
    }

    public static void setExternalDisplayPosition(int position) {
        externalDisplayPosition = Math.max(DISPLAY_RIGHT, Math.min(DISPLAY_BELOW, position));
    }

    public static void startInjection(String ignoredDeviceName) {
        pointerX = 0;
        pointerY = 0;
        remainderX = remainderY = 0f;
        pressedButton = -1;
        screenFocused = false;
        switchingApps = false;
        hidePointer();
    }

    public static void stopInjection() { leaveScreen(); }
    public static void stop() { leaveScreen(); }

    public static void enterScreen() {
        screenFocused = true;
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        if (service != null) service.movePointer(pointerX, pointerY);
    }

    public static void leaveScreen() {
        screenFocused = false;
        switchingApps = false;
        hidePointer();
    }

    private static void hidePointer() {
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        if (service != null) service.hidePointer();
    }

    public static void keydown(int key, int mask) {
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        if (isModifierKey(key)) return;
        boolean alt = (mask & 0x0004) != 0;
        boolean shift = (mask & 0x0001) != 0;
        if (service != null && alt && isTab(key)) {
            boolean towardRight = !shift;
            if (switchingApps) service.moveRecents(towardRight);
            else service.showRecentsAndMove(towardRight);
            switchingApps = true;
            return;
        }
        if (service != null && alt && shift && (key == 78 || key == 110)) {
            service.showNotifications();
            return;
        }
        if (SynerdroidInputMethodService.sendKey(key, mask)) return;
        if (service != null) service.keyDown(key, mask);
    }

    public static boolean isModifierKey(int key) {
        return key >= 0xFFE1 && key <= 0xFFEE
                || key >= 0xEFE1 && key <= 0xEFEE
                || key == 0xFE03 || key == 0xEE03;
    }

    private static boolean isTab(int key) {
        return key == 9 || key == 61193 || key == 65289;
    }

    public static void keyup(int key, int mask) {
        if (switchingApps && isAltKey(key)) {
            SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
            if (service != null) service.selectRecents();
            switchingApps = false;
        }
    }

    private static boolean isAltKey(int key) {
        return key == 0xFFE9 || key == 0xFFEA || key == 0xEFE9 || key == 0xEFEA;
    }

    public static void movemouse(int dx, int dy) {
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        if (service == null) return;
        float scaledX = dx * pointerSpeed + remainderX;
        float scaledY = dy * pointerSpeed + remainderY;
        int moveX = Math.round(scaledX);
        int moveY = Math.round(scaledY);
        remainderX = scaledX - moveX;
        remainderY = scaledY - moveY;
        int oldWidth = service.getScreenWidth();
        int oldHeight = service.getScreenHeight();
        int nextX = pointerX + moveX;
        int nextY = pointerY + moveY;
        boolean phone = service.isOnDefaultDisplay();
        boolean crossed = phone
                ? crossesPhoneEdge(nextX, nextY, oldWidth, oldHeight)
                : crossesExternalReturnEdge(nextX, nextY, oldWidth, oldHeight);
        if (crossed) {
            boolean switched = phone
                    ? service.switchToExternalDisplay()
                    : service.switchToDefaultDisplay();
            if (switched) {
                int newWidth = service.getScreenWidth();
                int newHeight = service.getScreenHeight();
                if (externalDisplayPosition == DISPLAY_RIGHT) {
                    nextX = phone ? nextX - oldWidth : newWidth + nextX;
                    nextY = scaleCoordinate(nextY, oldHeight, newHeight);
                } else if (externalDisplayPosition == DISPLAY_LEFT) {
                    nextX = phone ? newWidth + nextX : nextX - oldWidth;
                    nextY = scaleCoordinate(nextY, oldHeight, newHeight);
                } else if (externalDisplayPosition == DISPLAY_ABOVE) {
                    nextY = phone ? newHeight + nextY : nextY - oldHeight;
                    nextX = scaleCoordinate(nextX, oldWidth, newWidth);
                } else {
                    nextY = phone ? nextY - oldHeight : newHeight + nextY;
                    nextX = scaleCoordinate(nextX, oldWidth, newWidth);
                }
            }
        }
        pointerX = Math.max(0, Math.min(service.getScreenWidth() - 1, nextX));
        pointerY = Math.max(0, Math.min(service.getScreenHeight() - 1, nextY));
        if (screenFocused) service.movePointer(pointerX, pointerY);
    }

    public static void mousedown(int buttonId) {
        if (buttonId == 3 || buttonId == 4 || buttonId == 8) {
            SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
            if (service != null) service.goBack();
            return;
        }
        pressedButton = buttonId;
        pressX = pointerX;
        pressY = pointerY;
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        pressDisplayId = service == null ? 0 : service.getActiveDisplayId();
    }

    public static void mouseup(int buttonId) {
        if (buttonId == 3 || buttonId == 4 || buttonId == 8) return;
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        if (service != null && pressedButton == buttonId
                && pressDisplayId == service.getActiveDisplayId()) {
            int distance = Math.abs(pointerX - pressX) + Math.abs(pointerY - pressY);
            if (pressY <= Math.round(48 * service.getResources().getDisplayMetrics().density)
                    && pointerY - pressY > 60) {
                service.showNotifications();
            } else if (distance > 12) {
                service.drag(pressX, pressY, pointerX, pointerY);
            } else {
                service.click(pointerX, pointerY, buttonId);
            }
        }
        pressedButton = -1;
    }

    public static void mousewheel(int x, int y) {
        SynerdroidAccessibilityService service = SynerdroidAccessibilityService.getInstance();
        if (service != null) service.scroll(pointerX, pointerY, invertScroll ? -y : y);
    }

    private static int scaleCoordinate(int value, int oldSize, int newSize) {
        if (oldSize <= 1 || newSize <= 1) return 0;
        return Math.round(value * (newSize - 1f) / (oldSize - 1f));
    }

    private static boolean crossesPhoneEdge(int x, int y, int width, int height) {
        if (externalDisplayPosition == DISPLAY_RIGHT) return x >= width;
        if (externalDisplayPosition == DISPLAY_LEFT) return x < 0;
        if (externalDisplayPosition == DISPLAY_ABOVE) return y < 0;
        return y >= height;
    }

    private static boolean crossesExternalReturnEdge(int x, int y, int width, int height) {
        if (externalDisplayPosition == DISPLAY_RIGHT) return x < 0;
        if (externalDisplayPosition == DISPLAY_LEFT) return x >= width;
        if (externalDisplayPosition == DISPLAY_ABOVE) return y >= height;
        return y < 0;
    }
}
