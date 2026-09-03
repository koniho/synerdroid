package io.github.koniho.synerdroid;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ReconnectPolicyTest {
    @Test public void backsOffAndCapsAtMaximum() {
        ReconnectPolicy policy = new ReconnectPolicy(2, 60);
        assertEquals(2, policy.nextDelaySeconds());
        assertEquals(4, policy.nextDelaySeconds());
        assertEquals(8, policy.nextDelaySeconds());
        assertEquals(16, policy.nextDelaySeconds());
        assertEquals(32, policy.nextDelaySeconds());
        assertEquals(60, policy.nextDelaySeconds());
        assertEquals(60, policy.nextDelaySeconds());
    }

    @Test public void successfulConnectionResetsBackoff() {
        ReconnectPolicy policy = new ReconnectPolicy(2, 60);
        policy.nextDelaySeconds();
        policy.nextDelaySeconds();
        policy.reset();
        assertEquals(2, policy.nextDelaySeconds());
    }
}
