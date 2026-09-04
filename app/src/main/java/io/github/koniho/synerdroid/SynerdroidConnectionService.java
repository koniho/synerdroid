package io.github.koniho.synerdroid;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.content.pm.PackageManager;

import io.github.koniho.synerdroid.base.Event;
import io.github.koniho.synerdroid.base.EventQueue;
import io.github.koniho.synerdroid.base.EventType;
import io.github.koniho.synerdroid.client.Client;
import io.github.koniho.synerdroid.common.screens.BasicScreen;
import io.github.koniho.synerdroid.injection.Injection;
import io.github.koniho.synerdroid.net.NetworkAddress;
import io.github.koniho.synerdroid.net.SocketFactoryInterface;
import io.github.koniho.synerdroid.net.SynergyConnectTask;
import io.github.koniho.synerdroid.net.TCPSocketFactory;

public final class SynerdroidConnectionService extends Service {
    public interface Listener {
        void onStatus(String message);
        void onConnectionState(boolean active);
    }

    private static final String CHANNEL_ID = "synerdroid_connection";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_CONNECT = "io.github.koniho.synerdroid.CONNECT";
    private static final String ACTION_DISCONNECT = "io.github.koniho.synerdroid.DISCONNECT";
    private static volatile Listener listener;
    private static volatile boolean active;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Client client;
    private Thread eventThread;
    private boolean stopping;
    private int generation;
    private final ReconnectPolicy reconnectPolicy = new ReconnectPolicy(2, 60);
    private String name;
    private String host;
    private int port;
    private boolean tls;
    private String fingerprint;

    public static void setListener(Listener value) {
        listener = value;
        if (value != null) value.onConnectionState(active);
    }

    public static boolean isActive() { return active; }

    public static void connect(Context context, String name, String host, int port,
            boolean tls, String fingerprint) {
        Intent intent = new Intent(context, SynerdroidConnectionService.class)
                .setAction(ACTION_CONNECT)
                .putExtra("name", name)
                .putExtra("host", host)
                .putExtra("port", port)
                .putExtra("tls", tls)
                .putExtra("fingerprint", fingerprint);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void disconnect(Context context) {
        context.startService(new Intent(context, SynerdroidConnectionService.class)
                .setAction(ACTION_DISCONNECT));
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startEventLoop();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_REDELIVER_INTENT;
        if (ACTION_DISCONNECT.equals(intent.getAction())) {
            stopConnection();
            return START_NOT_STICKY;
        }
        if (!ACTION_CONNECT.equals(intent.getAction())) return START_NOT_STICKY;

        name = intent.getStringExtra("name");
        host = intent.getStringExtra("host");
        port = intent.getIntExtra("port", 24800);
        tls = intent.getBooleanExtra("tls", true);
        fingerprint = intent.getStringExtra("fingerprint");
        Client previous = client;
        client = null;
        stopping = false;
        generation++;
        if (previous != null) previous.disconnect(null);
        reconnectPolicy.reset();
        setActive(true);
        startForeground(NOTIFICATION_ID, notification("Connecting to " + host + "..."));
        connectNow(generation);
        return START_REDELIVER_INTENT;
    }

    private void connectNow(int expectedGeneration) {
        if (stopping || expectedGeneration != generation) return;
        startEventLoop();
        if (!Injection.isReady()) {
            report("Cannot reconnect: accessibility service is disabled.");
            stopConnection();
            return;
        }
        report("Connecting in background to " + host + ":" + port + "...");
        try {
            SocketFactoryInterface factory = new TCPSocketFactory(tls, fingerprint);
            BasicScreen screen = new BasicScreen();
            int[] desktopSize = Injection.getDesktopSize();
            screen.setShape(desktopSize[0], desktopSize[1]);
            report("Android display topology: " + desktopSize[0] + " x " + desktopSize[1] + ".");
            Client next = new Client(getApplicationContext(), name,
                    new NetworkAddress(host, port), factory, null, screen,
                    this::report, disconnected -> onClientDisconnected(disconnected, expectedGeneration));
            client = next;
            new SynergyConnectTask().execute(next);
        } catch (RuntimeException error) {
            report("Connection setup failed: " + error.getMessage());
            scheduleReconnect(expectedGeneration);
        }
    }

    private void onClientDisconnected(Client disconnected, int expectedGeneration) {
        if (client == disconnected) client = null;
        if (!stopping && expectedGeneration == generation) scheduleReconnect(expectedGeneration);
    }

    private void scheduleReconnect(int expectedGeneration) {
        if (stopping || expectedGeneration != generation) return;
        int delay = reconnectPolicy.nextDelaySeconds();
        report("Reconnecting in " + delay + " seconds...");
        updateNotification("Reconnecting to " + host + "...");
        handler.postDelayed(() -> connectNow(expectedGeneration), delay * 1000L);
    }

    private void stopConnection() {
        stopping = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (eventThread != null) eventThread.interrupt();
        eventThread = null;
        Client old = client;
        client = null;
        if (old != null) new Thread(() -> old.disconnect(null), "Synerdroid stop").start();
        Injection.stop();
        setActive(false);
        stopForeground(true);
        stopSelf();
        report("Background connection stopped.");
    }

    private void startEventLoop() {
        if (eventThread != null) return;
        eventThread = new Thread(() -> {
            try {
                Event event = EventQueue.getInstance().getEvent(new Event(), -1.0);
                while (!Thread.currentThread().isInterrupted()
                        && event.getType() != EventType.QUIT) {
                    EventQueue.getInstance().dispatchEvent(event);
                    event = EventQueue.getInstance().getEvent(event, -1.0);
                }
            } catch (Throwable error) {
                report("Input loop stopped: " + error);
                eventThread = null;
                scheduleReconnect(generation);
            }
        }, "Synerdroid protocol");
        eventThread.start();
    }

    private void report(String message) {
        if (message.contains("handshake complete")) {
            reconnectPolicy.reset();
            updateNotification("Connected as " + name);
        }
        Listener current = listener;
        if (current != null) current.onStatus(message);
    }

    private void setActive(boolean value) {
        active = value;
        Listener current = listener;
        if (current != null) current.onConnectionState(value);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Synerdroid connection", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, SynerdroidActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disconnect = PendingIntent.getService(this, 1,
                new Intent(this, SynerdroidConnectionService.class).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.icon)
                .setContentTitle("Synerdroid")
                .setContentText(text)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Disconnect", disconnect).build())
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, notification(text));
    }

    @Override public void onDestroy() {
        stopping = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        if (eventThread != null) eventThread.interrupt();
        eventThread = null;
        Client old = client;
        client = null;
        if (old != null) old.disconnect(null);
        setActive(false);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
