package org.synergy.injection;

/** Rootless input bridge backed by the enabled Synergy accessibility service. */
public final class Injection {
    private static int pointerX;
    private static int pointerY;

    private Injection() { }

    public static void setPermissionsForInputDevice() { }

    public static boolean isReady() {
        return SynergyAccessibilityService.isReady();
    }

    public static void startInjection(String ignoredDeviceName) {
        pointerX = 0;
        pointerY = 0;
    }

    public static void stopInjection() { }
    public static void stop() { }

    public static void keydown(int key, int mask) {
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

    public static void mousedown(int buttonId) { }

    public static void mouseup(int buttonId) {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.click(pointerX, pointerY, buttonId);
    }

    public static void mousewheel(int x, int y) {
        SynergyAccessibilityService service = SynergyAccessibilityService.getInstance();
        if (service != null) service.scroll(pointerX, pointerY, y);
    }
}
