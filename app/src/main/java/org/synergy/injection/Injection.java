package org.synergy.injection;

/** Rootless input bridge backed by the enabled Synergy accessibility service. */
public final class Injection {
    private static int pointerX;
    private static int pointerY;
    private static int pressX;
    private static int pressY;
    private static int pressedButton = -1;
    private static boolean screenFocused;
    private static float pointerSpeed = 1.25f;
    private static float remainderX;
    private static float remainderY;

    private Injection() { }

    public static void setPermissionsForInputDevice() { }

    public static boolean isReady() {
        return SynergyAccessibilityService.isReady();
    }

    public static void setPointerSpeed(float speed) {
        pointerSpeed = Math.max(0.5f, Math.min(2.0f, speed));
    }

    public static void startInjection(String ignoredDeviceName) {
        pointerX = 0;
        pointerY = 0;
        remainderX = remainderY = 0f;
        pressedButton = -1;
        screenFocused = false;
        hidePointer();
    }

    public static void stopInjection() { leaveScreen(); }
    public static void stop() { leaveScreen(); }

    public static void enterScreen() {
        screenFocused = true;
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.movePointer(pointerX, pointerY);
    }

    public static void leaveScreen() {
        screenFocused = false;
        hidePointer();
    }

    private static void hidePointer() {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.hidePointer();
    }

    public static void keydown(int key, int mask) {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        boolean alt = (mask & 0x0004) != 0;
        boolean shift = (mask & 0x0001) != 0;
        if (service != null && alt && isTab(key)) {
            service.showRecents();
            return;
        }
        if (service != null && alt && shift && (key == 78 || key == 110)) {
            service.showNotifications();
            return;
        }
        if (SynergyInputMethodService.sendKey(key)) return;
        if (service != null) service.keyDown(key, mask);
    }

    private static boolean isTab(int key) {
        return key == 9 || key == 61193 || key == 65289;
    }

    public static void keyup(int key, int mask) { }

    public static void movemouse(int dx, int dy) {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service == null) return;
        float scaledX = dx * pointerSpeed + remainderX;
        float scaledY = dy * pointerSpeed + remainderY;
        int moveX = Math.round(scaledX);
        int moveY = Math.round(scaledY);
        remainderX = scaledX - moveX;
        remainderY = scaledY - moveY;
        pointerX = Math.max(0, Math.min(service.getScreenWidth() - 1, pointerX + moveX));
        pointerY = Math.max(0, Math.min(service.getScreenHeight() - 1, pointerY + moveY));
        if (screenFocused) service.movePointer(pointerX, pointerY);
    }

    public static void mousedown(int buttonId) {
        if (buttonId == 3 || buttonId == 4 || buttonId == 8) {
            SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
            if (service != null) service.goBack();
            return;
        }
        pressedButton = buttonId;
        pressX = pointerX;
        pressY = pointerY;
    }

    public static void mouseup(int buttonId) {
        if (buttonId == 3 || buttonId == 4 || buttonId == 8) return;
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null && pressedButton == buttonId) {
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
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.scroll(pointerX, pointerY, y);
    }
}
