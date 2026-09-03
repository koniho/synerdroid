package org.synergy.diagnostics;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public final class CrashReporter {
    private static final String CRASH_FILE = "last-crash.txt";

    private CrashReporter() { }

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            StringWriter text = new StringWriter();
            error.printStackTrace(new PrintWriter(text));
            write(new File(app.getFilesDir(), CRASH_FILE),
                    "Uncaught exception on " + thread.getName() + "\n" + text, false);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    public static void clearConnectionLog(Context context) {
        new File(context.getFilesDir(), "connection.log").delete();
    }

    public static String readPreviousCrash(Context context) {
        File file = new File(context.getFilesDir(), CRASH_FILE);
        String value = read(file);
        if (!value.isEmpty()) file.delete();
        return value;
    }

    private static String read(File file) {
        try {
            if (!file.exists()) return "";
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void write(File file, String value, boolean append) {
        try (FileOutputStream output = new FileOutputStream(file, append)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }
}
