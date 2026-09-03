package org.synergy.net;

import android.os.AsyncTask;
import org.synergy.client.Client;

public final class SynergyConnectTask extends AsyncTask<Client, Void, Void> {
    @Override
    protected Void doInBackground(Client... clients) {
        for (Client client : clients) {
            if (client != null) client.connect();
        }
        return null;
    }
}
