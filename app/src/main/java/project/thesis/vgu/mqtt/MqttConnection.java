package project.thesis.vgu.mqtt;

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
    static MqttConnectOptions option;
    static IMqttToken connectToken;
    static boolean requestConnect;

    private MqttConnection() {
    }

    static void connect() {
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
                        Log.e(MainActivity.TAG, "reconnect exception" + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
        } catch (MqttException e) {
            Log.e(MainActivity.TAG, "connect exception" + e.getMessage());
            e.printStackTrace();
        }
    }

    static void initialize() {
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

    static void disconnect() {
        requestConnect = false;
        try {
            client.disconnect(null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.e(MainActivity.TAG, "disconnect success");
                    if (requestConnect) MqttConnection.connect();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(MainActivity.TAG, "disconnect fail");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG, "disconnect exception" + e.getMessage());
        }
    }
}
