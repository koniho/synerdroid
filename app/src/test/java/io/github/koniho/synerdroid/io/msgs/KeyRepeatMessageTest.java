package io.github.koniho.synerdroid.io.msgs;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import org.junit.Test;

public class KeyRepeatMessageTest {
    @Test public void readsPortableKeyIdsAsUnsignedValues() throws Exception {
        byte[] payload = { (byte) 0xef, 0x08, 0, 1, 0, 7, (byte) 0xab, (byte) 0xcd };
        KeyRepeatMessage message = new KeyRepeatMessage(
                new DataInputStream(new ByteArrayInputStream(payload)));
        assertEquals(0xef08, message.getID());
        assertEquals(1, message.getMask());
        assertEquals(7, message.getCount());
        assertEquals(0xabcd, message.getButton());
    }
}
