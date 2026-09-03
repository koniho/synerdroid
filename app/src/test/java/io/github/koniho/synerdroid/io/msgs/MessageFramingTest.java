package io.github.koniho.synerdroid.io.msgs;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import org.junit.Test;

public class MessageFramingTest {
    @Test public void extensionBytesStayInsideDeclaredPayload() throws Exception {
        byte[] wire = { 0,0,0,14, 'D','K','R','P',
                (byte)0xef,8, 0,0, 0,1, 0,42, 'e','n',
                0,0,0,4, 'C','N','O','P' };
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(wire));
        MessageHeader repeat = new MessageHeader(input);
        byte[] payload = new byte[repeat.getDataSize()];
        input.readFully(payload);
        assertEquals(MessageType.DKEYREPEAT, repeat.getType());
        assertEquals(10, repeat.getDataSize());
        assertEquals(MessageType.CNOOP, new MessageHeader(input).getType());
    }
}
