package org.synergy;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.synergy.base.Event;
import org.synergy.base.EventQueue;
import org.synergy.base.EventType;
import org.synergy.base.Log;
import org.synergy.client.Client;
import org.synergy.common.screens.BasicScreen;
import org.synergy.diagnostics.CrashReporter;
import org.synergy.injection.Injection;
import org.synergy.net.NetworkAddress;
import org.synergy.net.SocketFactoryInterface;
import org.synergy.net.SynergyConnectTask;
import org.synergy.net.TCPSocket;
import org.synergy.net.TCPSocketFactory;

import java.text.DateFormat;
import java.util.Date;

public class Synergy extends Activity {
    private static final String PROP_CLIENT_NAME = "clientName";
    private static final String PROP_SERVER_HOST = "serverHost";
    private static final String PROP_TLS_ENABLED = "tlsEnabled";
    private static final String PROP_TLS_FINGERPRINT = "tlsFingerprint";

    private Thread mainLoopThread;
    private TextView statusView;
    private Button connectButton;

    private final class MainLoopThread extends Thread {
        @Override public void run() {
            try {
                Event event = EventQueue.getInstance().getEvent(new Event(), -1.0);
                while (event.getType() != EventType.QUIT && mainLoopThread == Thread.currentThread()) {
                    EventQueue.getInstance().dispatchEvent(event);
                    event = EventQueue.getInstance().getEvent(event, -1.0);
                }
            } catch (Throwable error) {
                appendStatus("Input loop stopped: " + error.getMessage());
            } finally {
                mainLoopThread = null;
                Injection.stop();
                runOnUiThread(() -> connectButton.setEnabled(true));
            }
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashReporter.install(this);
        setContentView(R.layout.main);
        statusView = findViewById(R.id.statusTextView);
        migrateLegacyPreferences();
        String previousLog = CrashReporter.readRecentLog(this);
        String previousCrash = CrashReporter.readPreviousCrash(this);
        if (!previousLog.isEmpty()) statusView.setText(previousLog.trim());
        if (!previousCrash.isEmpty()) {
            statusView.append("\n\nPREVIOUS CRASH\n" + previousCrash);
        }
        connectButton = findViewById(R.id.connectButton);

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        setTextIfPresent(R.id.clientNameEditText,
                preferences.getString(PROP_CLIENT_NAME, "android"));
        setTextIfPresent(R.id.serverHostEditText,
                preferences.getString(PROP_SERVER_HOST, ""));
        ((CheckBox) findViewById(R.id.tlsCheckBox)).setChecked(
                preferences.getBoolean(PROP_TLS_ENABLED, true));
        setTextIfPresent(R.id.tlsFingerprintEditText,
                preferences.getString(PROP_TLS_FINGERPRINT, ""));

        findViewById(R.id.accessibilityButton).setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        connectButton.setOnClickListener(view -> connect());
        Log.setLogLevel(Log.Level.INFO);
    }

    @Override protected void onPause() {
        saveSettings();
        super.onPause();
    }

     protected void onResume() {
        super.onResume();
        if (statusView != null) {
            appendStatus(Injection.isReady()
                    ? "Accessibility service is enabled."
                    : "Accessibility service is not enabled.");
        }
    }

    private void migrateLegacyPreferences() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        String host = preferences.getString(PROP_SERVER_HOST, "");
        if ("igsarmewmactck".equalsIgnoreCase(host)) {
            preferences.edit().remove(PROP_SERVER_HOST).apply();
        }
    }

    private void clearInputFocus() {
        EditText[] fields = {
                findViewById(R.id.clientNameEditText),
                findViewById(R.id.serverHostEditText),
                findViewById(R.id.serverPortEditText),
                findViewById(R.id.tlsFingerprintEditText)
        };
        for (EditText field : fields) field.clearFocus();
        android.view.inputmethod.InputMethodManager keyboard =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(connectButton.getWindowToken(), 0);
        connectButton.requestFocus();
    }

    private void setTextIfPresent(int id, String value) {
        if (!TextUtils.isEmpty(value)) ((EditText) findViewById(id)).setText(value);
    }

    private void connect() {
        clearInputFocus();
        statusView.setText("");
        if (!Injection.isReady()) {
            appendStatus("Cannot connect: enable the Synergy accessibility service first.");
            Toast.makeText(this, R.string.service_required, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        String clientName = textOf(R.id.clientNameEditText);
        String serverHost = textOf(R.id.serverHostEditText);
        String portValue = textOf(R.id.serverPortEditText);
        boolean tlsEnabled = ((CheckBox) findViewById(R.id.tlsCheckBox)).isChecked();
        String fingerprint = textOf(R.id.tlsFingerprintEditText);

        if (TextUtils.isEmpty(clientName) || TextUtils.isEmpty(serverHost)) {
            appendStatus("Client name and server address are required.");
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(portValue);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            appendStatus("Port must be a number from 1 to 65535.");
            return;
        }

        if (tlsEnabled && TCPSocket.normalizeFingerprint(fingerprint).length() != 64) {
            appendStatus("TLS fingerprint must contain 64 hexadecimal digits.");
            Toast.makeText(this, R.string.invalid_fingerprint, Toast.LENGTH_LONG).show();
            return;
        }

        saveSettings();

        appendStatus("Resolving " + serverHost + "…");
        appendStatus(tlsEnabled ? "TLS enabled; certificate pin configured." : "Warning: TLS disabled.");
        connectButton.setEnabled(false);

        try {
            SocketFactoryInterface socketFactory = new TCPSocketFactory(tlsEnabled, fingerprint);
            NetworkAddress serverAddress = new NetworkAddress(serverHost, port);
            Injection.startInjection("Synergy Accessibility");
            BasicScreen screen = new BasicScreen();
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            screen.setShape(metrics.widthPixels, metrics.heightPixels);

            Client client = new Client(getApplicationContext(), clientName, serverAddress,
                    socketFactory, null, screen, this::appendStatus);
            new SynergyConnectTask().execute(client);

            if (mainLoopThread == null) {
                mainLoopThread = new MainLoopThread();
                mainLoopThread.start();
            }
        } catch (Exception error) {
            appendStatus("Connection failed: " + error.getMessage());
            connectButton.setEnabled(true);
        }
    }

    private void saveSettings() {
        if (statusView == null) return;
        getPreferences(MODE_PRIVATE).edit()
                .putString(PROP_CLIENT_NAME, textOf(R.id.clientNameEditText))
                .putString(PROP_SERVER_HOST, textOf(R.id.serverHostEditText))
                .putBoolean(PROP_TLS_ENABLED, ((CheckBox) findViewById(R.id.tlsCheckBox)).isChecked())
                .putString(PROP_TLS_FINGERPRINT, textOf(R.id.tlsFingerprintEditText))
                .apply();
    }

    private String textOf(int id) {
        return ((EditText) findViewById(id)).getText().toString().trim();
    }

    private void appendStatus(String message) {
        runOnUiThread(() -> {
            String time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date());
            if (statusView.length() > 0) statusView.append("\n");
            String line = time + "  " + message;
            statusView.append(line);
            CrashReporter.append(this, line);
        });
    }
}
