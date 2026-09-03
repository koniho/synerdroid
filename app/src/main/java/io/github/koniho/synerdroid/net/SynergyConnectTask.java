package io.github.koniho.synerdroid.net;
// Modified for Synerdroid by Alexander Ho, 2026.

import android.os.AsyncTask;
import io.github.koniho.synerdroid.client.Client;

public final class SynergyConnectTask extends AsyncTask<Client, Void, Void> {
    @Override
    protected Void doInBackground(Client... clients) {
        for (Client client : clients) {
            if (client != null) client.connect();
        }
        return null;
    }
}
