package org.synergy.net;

public class TCPSocketFactory implements SocketFactoryInterface {
    private final boolean tlsEnabled;
    private final String expectedFingerprint;

    public TCPSocketFactory() {
        this(false, "");
    }

    public TCPSocketFactory(boolean tlsEnabled, String expectedFingerprint) {
        this.tlsEnabled = tlsEnabled;
        this.expectedFingerprint = expectedFingerprint;
    }

    @Override public DataSocketInterface create() {
        return new TCPSocket(tlsEnabled, expectedFingerprint);
    }

    @Override public ListenSocketInterface createListen() {
        return new TCPListenSocket();
    }
}
