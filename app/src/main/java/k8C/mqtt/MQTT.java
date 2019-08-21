package k8C.mqtt;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by AnhKhoaChu on 8/9/2019.
 */
class MQTT {
    static MqttAsyncClient client; // common client for both the Activity and the Notification Service
    static boolean sessionPresent, isConnecting = false;
    boolean subscribeOk = false, unsubscribeOk = false;

    void initialize() { // create new client if not exists to connect to mqtt server
        if (client == null)
            try {
                client = new MqttAsyncClient("tcp://io.adafruit.com:1883", "k8c53795cakn", null);
            } catch (MqttException e) {
                Log.e(MainActivity.TAG, "constructor exception: " + e.getMessage());
                e.printStackTrace();
            }
    }

    static void connect() { // connect to server, reconnect if failed
        isConnecting = true; // sometimes while disconnecting, a new connect request will fail
        final MqttConnectOptions option = new MqttConnectOptions(); // prepare data for the connection
        option.setCleanSession(false);
        option.setUserName("k8C");
        option.setPassword("b19057d0daee4a4db05b4c0c1ed9166d".toCharArray());
        try {
            client.connect(option, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    sessionPresent = asyncActionToken.getSessionPresent();
                    Log.e(MainActivity.TAG, "connect success, session present: " + sessionPresent);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(MainActivity.TAG, "connect fail");
                    try {
                        Thread.sleep(7654, 321);
                    } catch (InterruptedException e) {
                    }
                    if (isConnecting)
                        try {
                            client.connect(option, null, this);
                        } catch (MqttException e) {
                            Log.e(MainActivity.TAG, "reconnect exception " + e.getMessage());
                            e.printStackTrace();
                        }
                }
            });
        } catch (MqttException e) {
            Log.e(MainActivity.TAG, "connect exception " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void disconnect() { // remove reference to Activity or Service and disconnect
        client.setCallback(null);
        isConnecting = false; // stop reconnecting
        try {
            client.disconnect(null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.e(MainActivity.TAG, "disconnect success");
                    if (isConnecting) connect();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(MainActivity.TAG, "disconnect fail");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG, "disconnect exception " + e.getMessage());
        }
    }

    void connectComplete(List<Topic> topics) {
        if (sessionPresent) {
            if (!subscribeOk) subscribePersist(topics);
            if (!unsubscribeOk) unsubscribePersist(topics);
        } else {
            List<String> subscribeList = new ArrayList<>();
            buildSubsribeListNonPersist(subscribeList, topics);
            subscribeNonPersist(subscribeList);
        }
    }

    void subscribeNonPersist(List<String> topicsName) {
        int[] qos = new int[topicsName.size()];
        Arrays.fill(qos, 1);
        try {
            client.subscribe(topicsName.toArray(new String[topicsName.size()]), qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribeOk = true;
                    Log.e(MainActivity.TAG, "subscribeNonPersist success ");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    onSubscribeFailure(asyncActionToken);
                    Log.e(MainActivity.TAG, "subscribeNonPersist fail " + e.getMessage());
                }
            });
        } catch (MqttException e) {
            subscribeOk = false;
            Log.e(MainActivity.TAG, "subscribeNonPersist exception " + e.getMessage());
            e.printStackTrace();
        }
    }

    void subscribePersist(List<Topic> topics) {
        List<String> subscribeList = new ArrayList<>();
        buildSubsribeList(subscribeList, topics);
        if (subscribeList.size() == 0) {
            subscribeOk = true;
            return;
        }
        subscribeNonPersist(subscribeList);
    }

    void unsubscribePersist(List<Topic> topics) {
        List<String> unsubscribeList = new ArrayList<>();
        buildUnsubscribeList(unsubscribeList, topics);
        if (unsubscribeList.size() == 0) {
            unsubscribeOk = true;
            return;
        }
        try {
            client.unsubscribe(unsubscribeList.toArray(new String[unsubscribeList.size()]), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    unsubscribeOk = true;
                    Log.e(MainActivity.TAG, "unsubscribePersist success ");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    unsubscribeOk = client.isConnected();
                    Log.e(MainActivity.TAG, "unsubscribePersist fail " + e.getMessage());
                }
            });
        } catch (MqttException e) {
            unsubscribeOk = false;
            Log.e(MainActivity.TAG, "unsubscribePersist exception " + e.getMessage());
            e.printStackTrace();
        }
    }

    void buildSubsribeListNonPersist(List<String> subscribeList, List<Topic> topics) {
        for (Topic topic : topics) // subscribe to all topics with notify = true
            if (topic.notify) subscribeList.add(topic.name);
    }

    void onSubscribeFailure(IMqttToken asyncActionToken) {
        subscribeOk = MQTT.client.isConnected();
    }

    void buildSubsribeList(List<String> list, List<Topic> topics) {
        for (Topic topic : topics)
            if (!topic.isSubscribed && topic.notify) list.add(topic.name);
    }

    void buildUnsubscribeList(List<String> list, List<Topic> topics) {
        for (Topic topic : topics)
            if (topic.isSubscribed && !topic.notify) list.add(topic.name);
    }
}
