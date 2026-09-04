package io.github.koniho.synerdroid.net;
// Modified for Synerdroid by Alexander Ho, 2026.

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.github.koniho.synerdroid.base.Event;
import io.github.koniho.synerdroid.base.EventQueue;
import io.github.koniho.synerdroid.base.EventType;

public class TCPSocket implements DataSocketInterface {
    private static final int SOCKET_CONNECTION_TIMEOUT_MILLIS = 5000;

    private final boolean tlsEnabled;
    private final String expectedFingerprint;
    private Socket socket;
    private boolean connected;

    public TCPSocket() {
        this(false, "");
    }

    public TCPSocket(boolean tlsEnabled, String expectedFingerprint) {
        this.tlsEnabled = tlsEnabled;
        this.expectedFingerprint = normalizeFingerprint(expectedFingerprint);
    }

    @Override public void bind(NetworkAddress address) { }

    @Override public void close() {
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) { }
        }
        connected = false;
    }

    @Override public boolean isReady() { return connected; }
    @Override public InputStream getInputStream() throws IOException { return socket.getInputStream(); }
    @Override public OutputStream getOutputStream() throws IOException { return socket.getOutputStream(); }

    @Override
    public void connect(NetworkAddress address) throws IOException {
        if (tlsEnabled && expectedFingerprint.length() != 64) {
            throw new IOException("TLS requires a 64-digit SHA-256 server fingerprint");
        }

        Socket transport = new Socket();
        transport.connect(new InetSocketAddress(address.getAddress(), address.getPort()),
                SOCKET_CONNECTION_TIMEOUT_MILLIS);
        transport.setTcpNoDelay(true);
        transport.setTrafficClass(8);

        if (tlsEnabled) {
            socket = createTlsSocket(transport, address.getHostname(), address.getPort());
            ((SSLSocket) socket).startHandshake();
        } else {
            socket = transport;
        }

        connected = true;
        sendEvent(EventType.SOCKET_CONNECTED);
    }

    private Socket createTlsSocket(Socket transport, String host, int port) throws IOException {
        try {
            X509TrustManager pinningTrustManager = new X509TrustManager() {
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    throw new CertificateException("Client certificates are not supported");
                }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    if (chain == null || chain.length == 0) {
                        throw new CertificateException("Server sent no TLS certificate");
                    }
                    String actual = sha256(chain[0]);
                    if (!MessageDigest.isEqual(actual.getBytes(), expectedFingerprint.getBytes())) {
                        throw new CertificateException("Server fingerprint mismatch; received "
                                + formatFingerprint(actual));
                    }
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { pinningTrustManager }, null);
            SSLSocket ssl = (SSLSocket) context.getSocketFactory()
                    .createSocket(transport, host, port, true);
            ssl.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
            return ssl;
        } catch (GeneralSecurityException e) {
            throw new IOException("Unable to initialize TLS", e);
        }
    }

    private static String sha256(X509Certificate certificate) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format(Locale.US, "%02X", value));
            return result.toString();
        } catch (GeneralSecurityException e) {
            throw new CertificateException("Unable to calculate certificate fingerprint", e);
        }
    }

    public static String fetchServerFingerprint(String host, int port) throws IOException {
        try {
            X509TrustManager probeTrustManager = new X509TrustManager() {
                @Override public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    throw new CertificateException("Client certificates are not supported");
                }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    if (chain == null || chain.length == 0) {
                        throw new CertificateException("Server sent no TLS certificate");
                    }
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { probeTrustManager }, null);
            try (Socket transport = new Socket()) {
                transport.connect(new InetSocketAddress(host, port),
                        SOCKET_CONNECTION_TIMEOUT_MILLIS);
                transport.setSoTimeout(SOCKET_CONNECTION_TIMEOUT_MILLIS);
                try (SSLSocket ssl = (SSLSocket) context.getSocketFactory()
                        .createSocket(transport, host, port, true)) {
                    ssl.setEnabledProtocols(new String[] { "TLSv1.3", "TLSv1.2" });
                    ssl.startHandshake();
                    java.security.cert.Certificate[] certificates =
                            ssl.getSession().getPeerCertificates();
                    if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate)) {
                        throw new IOException("Server sent no X.509 certificate");
                    }
                    return sha256((X509Certificate) certificates[0]);
                }
            }
        } catch (GeneralSecurityException error) {
            throw new IOException("Unable to inspect TLS certificate", error);
        }
    }

    public static String normalizeFingerprint(String fingerprint) {
        return fingerprint == null ? "" : fingerprint.replaceAll("(?i)sha256|[^0-9a-f]", "")
                .toUpperCase(Locale.US);
    }

    public static String formatFingerprint(String normalized) {
        return normalized.replaceAll("(..)(?!$)", "$1:");
    }

    @Override public Object getEventTarget() { return this; }

    private void sendEvent(EventType eventType) {
        EventQueue.getInstance().addEvent(new Event(eventType, getEventTarget(), null));
    }
}
