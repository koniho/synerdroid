package io.github.koniho.synerdroid;
// Modified for Synerdroid by Alexander Ho, 2026.

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
import android.widget.SeekBar;
import android.widget.Toast;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import io.github.koniho.synerdroid.base.Event;
import io.github.koniho.synerdroid.base.EventQueue;
import io.github.koniho.synerdroid.base.EventType;
import io.github.koniho.synerdroid.base.Log;
import io.github.koniho.synerdroid.client.Client;
import io.github.koniho.synerdroid.common.screens.BasicScreen;
import io.github.koniho.synerdroid.diagnostics.CrashReporter;
import io.github.koniho.synerdroid.injection.Injection;
import io.github.koniho.synerdroid.net.NetworkAddress;
import io.github.koniho.synerdroid.net.SocketFactoryInterface;
import io.github.koniho.synerdroid.net.SynergyConnectTask;
import io.github.koniho.synerdroid.net.TCPSocket;
import io.github.koniho.synerdroid.net.TCPSocketFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;

public class SynerdroidActivity extends Activity {
    private static final String PROP_CLIENT_NAME = "clientName";
    private static final String PROP_SERVER_HOST = "serverHost";
    private static final String PROP_TLS_ENABLED = "tlsEnabled";
    private static final String PROP_TLS_FINGERPRINT = "tlsFingerprint";
    private static final String PROP_POINTER_SPEED = "pointerSpeed";
    private static final String PROP_INVERT_SCROLL = "invertScroll";

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
                appendStatus("Input loop stopped:\n" + stackTrace(error));
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
        CrashReporter.clearConnectionLog(this);
        String previousCrash = CrashReporter.readPreviousCrash(this);
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
        SeekBar pointerSpeed = findViewById(R.id.pointerSpeedSeekBar);
        pointerSpeed.setProgress(preferences.getInt(PROP_POINTER_SPEED, 75));
        updatePointerSpeed(pointerSpeed.getProgress());
        CheckBox invertScroll = findViewById(R.id.invertScrollCheckBox);
        invertScroll.setChecked(preferences.getBoolean(PROP_INVERT_SCROLL, false));
        Injection.setInvertScroll(invertScroll.isChecked());
        invertScroll.setOnCheckedChangeListener((button, checked) -> {
            Injection.setInvertScroll(checked);
            saveSettings();
        });
        pointerSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
             public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updatePointerSpeed(progress);
            }
             public void onStartTrackingTouch(SeekBar seekBar) { }
             public void onStopTrackingTouch(SeekBar seekBar) { saveSettings(); }
        });

        findViewById(R.id.accessibilityButton).setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        findViewById(R.id.keyboardButton).setOnClickListener(view -> configureKeyboard());
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

    private void updatePointerSpeed(int progress) {
        float speed = 0.5f + progress / 100f;
        Injection.setPointerSpeed(speed);
        ((TextView) findViewById(R.id.pointerSpeedValue)).setText(
                String.format(java.util.Locale.US, "%.2f×", speed));
    }

    private void configureKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        boolean enabled = false;
        for (InputMethodInfo info : manager.getEnabledInputMethodList()) {
            if (getPackageName().equals(info.getPackageName())) {
                enabled = true;
                break;
            }
        }
        if (enabled) {
            appendStatus("Choose Synerdroid Keyboard in the keyboard picker.");
            manager.showInputMethodPicker();
        } else {
            appendStatus("Enable Synerdroid Keyboard, return here, then tap Keyboard settings again to select it.");
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
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
            appendStatus("Cannot connect: enable the Synerdroid accessibility service first.");
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
            Injection.startInjection("Synerdroid Accessibility");
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
                .putInt(PROP_POINTER_SPEED, ((SeekBar) findViewById(R.id.pointerSpeedSeekBar)).getProgress())
                .putBoolean(PROP_INVERT_SCROLL, ((CheckBox) findViewById(R.id.invertScrollCheckBox)).isChecked())
                .apply();
    }

    private String textOf(int id) {
        return ((EditText) findViewById(id)).getText().toString().trim();
    }

    private static String stackTrace(Throwable error) {
        StringWriter output = new StringWriter();
        error.printStackTrace(new PrintWriter(output));
        return output.toString();
    }

    private void appendStatus(String message) {
        runOnUiThread(() -> {
            String time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date());
            if (statusView.length() > 0) statusView.append("\n");
            String line = time + "  " + message;
            statusView.append(line);
        });
    }
}
