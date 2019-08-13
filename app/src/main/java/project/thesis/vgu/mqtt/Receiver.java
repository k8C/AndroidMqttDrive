package project.thesis.vgu.mqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

// receiver for 'STOP' action button of MqttService foreground notification
public class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("notifyInBackground", false)) {
                if (MainActivity.atLeastOreo) // start ForeGround service in android Oreo and above
                    context.startForegroundService(new Intent(context, MqttService.class));
                else context.startService(new Intent(context, MqttService.class));
            }
        } else {
            new MqttConnection().disconnect();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("notifyInBackground", false).apply();
            context.stopService(new Intent(context, MqttService.class)); // stop the MqttService
        }
    }
}