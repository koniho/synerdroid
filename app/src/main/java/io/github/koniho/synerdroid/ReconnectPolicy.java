package io.github.koniho.synerdroid;

final class ReconnectPolicy {
    private final int initialSeconds;
    private final int maximumSeconds;
    private int nextSeconds;

    ReconnectPolicy(int initialSeconds, int maximumSeconds) {
        if (initialSeconds < 1 || maximumSeconds < initialSeconds) {
            throw new IllegalArgumentException("Invalid reconnect bounds");
        }
        this.initialSeconds = initialSeconds;
        this.maximumSeconds = maximumSeconds;
        this.nextSeconds = initialSeconds;
    }

    int nextDelaySeconds() {
        int result = nextSeconds;
        nextSeconds = Math.min(maximumSeconds, nextSeconds * 2);
        return result;
    }

    void reset() {
        nextSeconds = initialSeconds;
    }
}
