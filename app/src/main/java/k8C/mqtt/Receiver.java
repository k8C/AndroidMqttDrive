package k8C.mqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.PreferenceManager;

// receiver for 'STOP' action button of MqttService foreground notification
public class Receiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("notifyInBackground", false)) {
                if (Build.VERSION.SDK_INT >= 26) // start ForeGround service in android Oreo and above
                    context.startForegroundService(new Intent(context, MqttService.class));
                else context.startService(new Intent(context, MqttService.class));
            }
        } else {
            MQTT.disconnect();
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("notifyInBackground", false).apply();
            context.stopService(new Intent(context, MqttService.class)); // stop the MqttService
        }
    }
}