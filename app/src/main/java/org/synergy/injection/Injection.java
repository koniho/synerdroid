package org.synergy.injection;

/** Rootless input bridge backed by the enabled Synergy accessibility service. */
public final class Injection {
    private static int pointerX;
    private static int pointerY;
    private static int pressX;
    private static int pressY;
    private static int pressedButton = -1;

    private Injection() { }

    public static void setPermissionsForInputDevice() { }

    public static boolean isReady() {
        return SynergyAccessibilityService.isReady();
    }

    public static void startInjection(String ignoredDeviceName) {
        pointerX = 0;
        pointerY = 0;
        pressedButton = -1;
    }

    public static void stopInjection() { }
    public static void stop() { }

    public static void keydown(int key, int mask) {
        if (SynergyInputMethodService.sendKey(key)) return;
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.keyDown(key, mask);
    }

    public static void keyup(int key, int mask) { }

    public static void movemouse(int dx, int dy) {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service == null) return;
        pointerX = Math.max(0, Math.min(service.getScreenWidth() - 1, pointerX + dx));
        pointerY = Math.max(0, Math.min(service.getScreenHeight() - 1, pointerY + dy));
        service.movePointer(pointerX, pointerY);
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
            if (distance > 12) service.drag(pressX, pressY, pointerX, pointerY);
            else service.click(pointerX, pointerY, buttonId);
        }
        pressedButton = -1;
    }

    public static void mousewheel(int x, int y) {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.scroll(pointerX, pointerY, y);
    }
}
