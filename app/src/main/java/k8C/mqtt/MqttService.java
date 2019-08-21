package k8C.mqtt;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MqttService extends Service {
    List<Topic> topics;
    PowerManager.WakeLock wakeLock;
    MQTT mqtt;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // foreground notification always show in status bar, indicate that the Notification Service always running
        startForeground(1, new NotificationCompat.Builder(this, "service")
                .addAction(0, "STOP", PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class), 0))
                .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSmallIcon(R.drawable.app1).setContentTitle("MQTT service is running").build());
        // get topics data from storage
        String topicsJson = PreferenceManager.getDefaultSharedPreferences(this).getString("topics", null);
        if (topicsJson == null) {
            stopSelf();
            Log.e(MainActivity.TAG, "no data to process, service terminated");
            return START_NOT_STICKY;
        }
        topics = new Gson().fromJson(topicsJson, new TypeToken<List<Topic>>() {
        }.getType());
        // wakelock is used to ensure the service is not suspended when the Android device is sleeping
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mqtt::k8c");
        wakeLock.acquire();
        mqtt = new MQTT();
        mqtt.initialize();
        MQTT.client.setCallback(new MqttCallbackExtended() {
            boolean notify = false;
            float value;
            String notificationText;
            int i;

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.e(MainActivity.TAG, "service connectComplete");
                mqtt.connectComplete(topics);
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.e(MainActivity.TAG, "service connectionLost");
                MQTT.connect();
            }

            @Override
            public void messageArrived(String topicName, MqttMessage message) throws Exception {
                Log.e(MainActivity.TAG, topicName + ": " + message);
                i = 2;
                for (Topic topic : topics) {
                    if (topic.name.equals(topicName)) {
                        if (topic.max != null) {
                            value = Float.parseFloat(message.toString());
                            if (topic.max < value) {
                                notify = true;
                                notificationText = "Value is " + value + " > " + topic.max;
                            }
                        } else if (topic.min != null) {
                            value = Float.parseFloat(message.toString());
                            if (topic.min > value) {
                                notify = true;
                                notificationText = "Value is " + value + " < " + topic.min;
                            }
                        }
                        if (notify) {
                            NotificationManagerCompat.from(MqttService.this).notify(i, new NotificationCompat.Builder(MqttService.this, "mqttTopic")
                                    .setContentIntent(PendingIntent.getActivity(MqttService.this, 0, new Intent(MqttService.this, MainActivity.class), 0))
                                    .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                    .setSmallIcon(R.drawable.app2).setContentTitle("WARNING Topic ".concat(topicName))
                                    .setContentText(notificationText).setAutoCancel(true).build());
                            notify = false;
                        }
                        break;
                    }
                    i++;
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        if (MQTT.client.isConnected()) {
            mqtt.subscribePersist(topics); //subscribe to all topics with notify=true and isSubscribed=false
            mqtt.unsubscribePersist(topics); //unsubscribe to all topics with notify=false and isSubscribed=true
        } else if(!MQTT.isConnecting) MQTT.connect();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.e(MainActivity.TAG, "service onDestroy");
        wakeLock.release(); // release wakelock to allow the cpu to sleep
    }
}