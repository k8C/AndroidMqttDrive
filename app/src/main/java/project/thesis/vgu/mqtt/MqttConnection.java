package project.thesis.vgu.mqtt;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Created by AnhKhoaChu on 8/9/2019.
 */
class MqttConnection {
    static MqttAsyncClient client;
    MqttConnectOptions option;
    static IMqttToken connectToken;
    static boolean requestConnect;

    MqttConnection() {
    }

    void connect() {
        requestConnect = true;
        try {
            connectToken = client.connect(option, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.e(MainActivity.TAG, "connect success");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(MainActivity.TAG, "connect fail");
                    SystemClock.sleep(5000);
                    try {
//                            client.reconnect();
                        connectToken = client.connect(option, null, this);
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

    void initialize() {
        if (client == null) {
            try {
                client = new MqttAsyncClient("tcp://io.adafruit.com:1883", "k8c53795cakn", null);
            } catch (MqttException e) {
                Log.e(MainActivity.TAG, "constructor exception: " + e.getMessage());
                e.printStackTrace();
            }
            option = new MqttConnectOptions();
            option.setCleanSession(false);
            option.setUserName("k8C");
            option.setPassword("b19057d0daee4a4db05b4c0c1ed9166d".toCharArray());
        }
    }

    void disconnect() {
        requestConnect = false;
        try {
            client.disconnect(null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.e(MainActivity.TAG, "disconnect success");
                    if (requestConnect) connect();
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

    Handler handler;
    IMqttActionListener subscribeListener, unsubscribeListener, publishListener;
    boolean subscribeOk, unsubscribeOk;

    MqttConnection(Handler hl) {
        handler = hl;
        subscribeListener = new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                handler.obtainMessage(1, asyncActionToken.getTopics()[0]).sendToTarget();
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                handler.obtainMessage(2, asyncActionToken.getTopics()[0]).sendToTarget();
            }
        };
        unsubscribeListener = new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                handler.obtainMessage(3, asyncActionToken.getTopics()[0]).sendToTarget();
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                handler.obtainMessage(4).sendToTarget();
            }
        };
        publishListener = new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                handler.obtainMessage(5).sendToTarget();
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                handler.obtainMessage(6).sendToTarget();
            }
        };
    }

    void subscribe(String[] topics) {
        try {
            client.subscribe(topics, new int[]{1, 1}, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribeOk = true;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    subscribeOk = client.isConnected();
                }
            });
        } catch (MqttException e) {
            subscribeOk = false;
            Log.e(MainActivity.TAG, "topics subscribe fail " + e.getMessage());
            e.printStackTrace();
        }
    }

    class Listener {

    }
}
