package project.thesis.vgu.mqtt;

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

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class MqttService extends Service {
    //    Topic[] topics;
    List<Topic> topics;
    PowerManager.WakeLock wakeLock;
    MqttConnection mqtt;
    Action action;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String topicsJson = PreferenceManager.getDefaultSharedPreferences(this).getString("topics", null);
        // foreground notification always show in status bar, indicate that the background service always running, required in android Oreo and above
        startForeground(1, new NotificationCompat.Builder(this, "service")
                .addAction(0, "STOP", PendingIntent.getBroadcast(this, 0, new Intent(this, Receiver.class), 0))
                .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSmallIcon(R.drawable.app2).setContentTitle("MQTT service is running").build());
        if (topicsJson == null) {
            stopSelf();
            Log.e(MainActivity.TAG, "no data to process, service terminated");
            return START_NOT_STICKY;
        }
        wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mqtt::k8c");
        wakeLock.acquire(); // used to ensure the service is not suspended when the Android device is sleeping
        topics = new Gson().fromJson(topicsJson, new TypeToken<List<Topic>>() {
        }.getType());
        /*for (int i = 0; i < topicList.size(); i++) {
            if (!topicList.get(i).notify) {
                topicList.remove(i);
                i--;
            }
        }
        topics = topicList.toArray(new Topic[topicList.size()]);*/

        mqtt = new MqttConnection();
        action = new Action();
        mqtt.initialize(); // create client
        MqttConnection.client.setCallback(new MqttCallbackExtended() {
            boolean notify = false;
            float value;
            String notificationText;
            int i;

            @Override
            public void connectComplete(boolean reconnect, String serverURI) { // when connected, subscribe to all topics
                Log.e(MainActivity.TAG, "service connectComplete");
                if (MqttConnection.connectToken.getSessionPresent()) {
                    if (!action.subscribeOk) action.subscribe(topics);
                    if (!action.unsubscribeOk) action.unsubscribeMulti(topics);
                } else {
                    List<String> subscribeList = new ArrayList<>();
                    for (Topic topic : topics) // subscribe to all topics with notify = true
                        if (topic.notify) subscribeList.add(topic.name);
                    action.subscribeMulti(subscribeList.toArray(new String[subscribeList.size()]));
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.e(MainActivity.TAG, "service connectionLost");
                mqtt.connect();
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
        if (MqttConnection.client.isConnected()) {
            action.subscribe(topics); action.unsubscribeMulti(topics);
        } else mqtt.connect();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.e(MainActivity.TAG, "service onDestroy");
        wakeLock.release(); // release wakelock to allow the cpu to sleep
    }

    static class Action {
        boolean subscribeOk = false, unsubscribeOk = false;

        void subscribeMulti(String[] topicNames) {
            int[] qos = new int[topicNames.length];
            Arrays.fill(qos, 1);
            try {
                MqttConnection.client.subscribe(topicNames, qos, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        subscribeOk = true;
                        Log.e(MainActivity.TAG, "subscribeMulti success ");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                        subscribeOk = MqttConnection.client.isConnected();
                        Log.e(MainActivity.TAG, "subscribeMulti fail " + e.getMessage());
                    }
                });
            } catch (MqttException e) {
                subscribeOk = false;
                Log.e(MainActivity.TAG, "subscribeMulti exception " + e.getMessage());
                e.printStackTrace();
            }
        }

        void subscribe(List<Topic> topics) {
            List<String> list = new ArrayList<>();
            for (Topic topic : topics)
                if (!topic.isSubscribed && topic.notify) list.add(topic.name);
            subscribeMulti(list.toArray(new String[list.size()]));
        }

        void unsubscribeMulti(List<Topic> topics) {
            List<String> list = new ArrayList<>();
            for (Topic topic : topics)
                if (topic.isSubscribed && !topic.notify) list.add(topic.name);
            try {
                MqttConnection.client.unsubscribe(list.toArray(new String[list.size()]), null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        unsubscribeOk = true;
                        Log.e(MainActivity.TAG, "unsubscribeMulti success ");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                        unsubscribeOk = MqttConnection.client.isConnected();
                        Log.e(MainActivity.TAG, "unsubscribeMulti fail " + e.getMessage());
                    }
                });
            } catch (MqttException e) {
                unsubscribeOk = false;
                Log.e(MainActivity.TAG, "unsubscribeMulti exception " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
