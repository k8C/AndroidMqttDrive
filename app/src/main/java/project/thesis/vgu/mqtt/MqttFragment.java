package project.thesis.vgu.mqtt;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

public class MqttFragment extends Fragment {
    ConnectivityManager.NetworkCallback connectionCallback; // callback for network connected/disconnected
    List<Topic> topics;
    TopicAdapter topicAdapter;
    TextView tv; // mqtt server connection status
    MqttAsyncClient client;
    Handler handler;
    IMqttActionListener subscribeListener, unsubscribeListener, publishListener;
    boolean notifyInBackground; // Options Menu setting for MqttService
    Context context;


    public MqttFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setRetainInstance(true); //retain variables across configchanges, onCreate and onDestroy not called
        Log.e(MainActivity.TAG, "MqttFragment onCreate");
        context = getContext();
        connectionCallback = new ConnectivityManager.NetworkCallback() {
            boolean noConnection = false;

            @Override
            public void onLost(Network network) {
                Snackbar.make(getView(), "No Connection", Snackbar.LENGTH_INDEFINITE).show();
                noConnection = true;
            }

            @Override
            public void onAvailable(Network network) {
                if (noConnection)
                    Snackbar.make(getView(), "Connected", Snackbar.LENGTH_SHORT).show();
                //client.connect;
            }
        };
        handler = new Handler() {
            void changeStatus(String topicName, boolean status) {
                for (Topic topic : topics)
                    if (topic.name.equals(topicName)) {
                        topic.isSubscribed = status;
                        topicAdapter.notifyDataSetChanged();
                        break;
                    }
            }

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        String[] data = (String[]) msg.obj;
                        for (Topic topic : topics)
                            if (topic.name.equals(data[0])) {
                                topic.value = data[1];
                                topicAdapter.notifyDataSetChanged();
                                if (topic.notify && ((topic.max != null && topic.max < Float.parseFloat(data[1]))
                                        || (topic.min != null && topic.min > Float.parseFloat(data[1]))))
                                    NotificationManagerCompat.from(context).notify(1, new NotificationCompat.Builder(context, "mqttTopic")
                                            .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                            .setSmallIcon(R.drawable.app2).setContentTitle("WARNING Topic ".concat(data[0])).build());
                                break;
                            }
                        break;
                    case 1:
                        changeStatus((String) msg.obj, true);
                        break;
                    case 2:
                        String topicName = (String) msg.obj;
                        changeStatus(topicName, false);
                        Toast.makeText(context, "Subscribe failed to ".concat(topicName), Toast.LENGTH_SHORT).show();
                        break;
                    case 3:
                        changeStatus((String) msg.obj, false);
                        break;
                    case 4:
                        Toast.makeText(context, "Unsubscribe failed", Toast.LENGTH_SHORT).show();
                        break;
                    case 5:
                        Toast.makeText(context, "Published successfully", Toast.LENGTH_SHORT).show();
                        break;
                    case 6:
                        Toast.makeText(context, "Publish failed", Toast.LENGTH_SHORT).show();
                        break;
                    case 7:
                        tv.setTextColor(0xff669900); //0xff669900,0xff99cc00 - red
                        break;
                    case 8:
                        tv.setTextColor(0xffff4444); //0xffcc0000,0xffff4444 - green
                        break;
                }
            }
        };
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.e(MainActivity.TAG, "MqttFragment onCreateView");
        //if (savedInstanceState != null) Log.e(MainActivity.TAG, "onCreateView savedInstanceState != null");
        View view = inflater.inflate(R.layout.fragment_mqtt, container, false);
        setHasOptionsMenu(true);
        SharedPreferences appData = PreferenceManager.getDefaultSharedPreferences(context); // phone storage store list of topics and app settings
        notifyInBackground = appData.getBoolean("notifyInBackground", false);
        tv = view.findViewById(R.id.tv);
        String topicsJson = appData.getString("topics", null);
        if (topicsJson != null)
            topics = new Gson().fromJson(topicsJson, new TypeToken<List<Topic>>() {
            }.getType());
        else {
            Topic cond = new Topic(), ph = new Topic(), temp = new Topic();
            cond.name = "monokia/f/cond";
            ph.name = "monokia/f/ph";
            temp.name = "monokia/f/temp";
            topics = new ArrayList<Topic>();
            topics.add(cond);
            topics.add(ph);
            topics.add(temp);
        }

        ListView listView = view.findViewById(R.id.list);
        topicAdapter = new TopicAdapter();
        listView.setAdapter(topicAdapter);
        registerForContextMenu(listView);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), connectionCallback);
        try {
            if (client == null) { // first time connect
                MqttConnectOptions option = new MqttConnectOptions();
                option.setAutomaticReconnect(true);
                option.setMaxReconnectDelay(5);
                option.setCleanSession(true);
                option.setUserName("monokia");
                option.setPassword("b19057d0daee4a4db05b4c0c1ed9166d".toCharArray());
                try {
                    client = new MqttAsyncClient("tcp://io.adafruit.com:1883", "k8c53795cakn", null);
                } catch (MqttException e) {
                    Log.e(MainActivity.TAG, "constructor exception: " + e.getMessage());
                    e.printStackTrace();
                }
                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        handler.obtainMessage(7).sendToTarget(); // change status textview color to green
                        try {
                            for (Topic topic : topics) // subscribe to all topics with isSubscribed = true
                                if (topic.isSubscribed) {
                                    Log.e(MainActivity.TAG, "subscribe again");
                                    client.subscribe(topic.name, 1, null, new IMqttActionListener() {
                                        @Override
                                        public void onSuccess(IMqttToken asyncActionToken) {
                                        }

                                        @Override
                                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                            handler.obtainMessage(2, asyncActionToken.getTopics()[0]).sendToTarget(); // topic status icon color red
                                        }
                                    });
                                }
                        } catch (MqttException e) {
                            Log.e(MainActivity.TAG, "subscribe mqttException: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        handler.obtainMessage(8).sendToTarget(); // change status textview color to red
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        handler.obtainMessage(0, new String[]{topic, message.toString()}).sendToTarget();
                        Log.e(MainActivity.TAG, topic + ": " + message);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        Log.e(MainActivity.TAG, "deliveryComplete");
                    }
                });
                client.connect(option, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.e(MainActivity.TAG, "connect success");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(MainActivity.TAG, "connect fail");
                        try {
                            client.reconnect();
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else client.reconnect(); // if has already been connected once, reconnect
        } catch (MqttException e) {
            Log.e(MainActivity.TAG, "onStart connect exception");
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        Log.e(MainActivity.TAG, "MqttFragment onStop");
        try {
            client.disconnect(0, null, null);
        } catch (MqttException e) {
            Log.e(MainActivity.TAG, "onStop disconnect exception");
            e.printStackTrace();
        }
        ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(connectionCallback);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("topics", new Gson().toJson(topics)).apply();
        if (notifyInBackground) for (Topic topic : topics)
            if (topic.notify) {
                if (MainActivity.atLeastOreo) // start ForeGround service in android Oreo and above
                    context.startForegroundService(new Intent(context, MqttService.class));
                else context.startService(new Intent(context, MqttService.class));
                break;
            }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        try {
            client.close(true); // release all resources related to the activity context to avoid memory leak
        } catch (MqttException e) {
            Log.e(MainActivity.TAG, "onDestroy close exception");
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getActivity().getMenuInflater().inflate(R.menu.listview_contextmenu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
        switch (item.getItemId()) {
            case R.id.subscribe:
                try {
                    client.subscribe(topics.get(position).name, 1, null, subscribeListener);
                } catch (MqttException e) {
                    Log.e(MainActivity.TAG, "subscribe exception");
                    e.printStackTrace();
                }
                break;
            case R.id.unsubscribe:
                try {
                    client.unsubscribe(topics.get(position).name, null, unsubscribeListener);
                } catch (MqttException e) {
                    Log.e(MainActivity.TAG, "unsubscribe exception");
                    e.printStackTrace();
                }
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.e(MainActivity.TAG, "onCreateOptionsMenu");
        inflater.inflate(R.menu.menu_main, menu);
        menu.getItem(0).setChecked(notifyInBackground);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.service_setting) {
            notifyInBackground = !item.isChecked();
            item.setChecked(notifyInBackground);
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("notifyInBackground", notifyInBackground).apply();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    class TopicAdapter extends BaseAdapter {
        int number;
        AlertDialog publishDialog, settingDialog;
        EditText max, min;

        @Override
        public int getCount() {
            return topics.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView != null) viewHolder = (ViewHolder) convertView.getTag();
            else {
                convertView = getLayoutInflater().inflate(R.layout.topic_row, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.topicTextView = convertView.findViewById(R.id.topic);
                viewHolder.statusIcon = convertView.findViewById(R.id.status);
                viewHolder.messageTextView = convertView.findViewById(R.id.value);
                viewHolder.publishButton = convertView.findViewById(R.id.arrow);
                viewHolder.toggleBell = convertView.findViewById(R.id.bell);
                viewHolder.settingButton = convertView.findViewById(R.id.gear);
                viewHolder.publishButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View publishButton) {
                        number = (int) publishButton.getTag();
                        if (publishDialog != null) publishDialog.show();
                        else publishDialog = new AlertDialog.Builder(context)
                                .setView(getLayoutInflater().inflate(R.layout.publish_dialog, null))
                                .setPositiveButton("Publish", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            client.publish(topics.get(number).name, ((EditText) publishDialog.findViewById(R.id.publishText)).getText().toString().getBytes(), 1, ((CheckBox) publishDialog.findViewById(R.id.retain)).isChecked(), null, publishListener);
                                        } catch (MqttException e) {
                                            Log.e(MainActivity.TAG, "publish exception");
                                            e.printStackTrace();
                                        }
                                    }
                                }).show();
                    }
                });
                viewHolder.toggleBell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View toggleButton) {
                        topics.get((int) toggleButton.getTag()).notify = ((ToggleButton) toggleButton).isChecked();
                    }
                });
                viewHolder.settingButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View settingButton) {
                        number = (int) settingButton.getTag();
                        if (settingDialog == null) {
                            settingDialog = new AlertDialog.Builder(context).setTitle("Notify when value:")
                                    .setView(getLayoutInflater().inflate(R.layout.setting_dialog, null))
                                    .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
                            settingDialog.setCanceledOnTouchOutside(false);
                            settingDialog.create();
                            settingDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Topic topic = topics.get(number);
                                    String maxText = max.getText().toString(), minText = min.getText().toString();
                                    if (!maxText.isEmpty() && minText.isEmpty()) { // either maxText or minText is null
                                        topic.max = Float.valueOf(maxText);
                                        topic.min = null;
                                    } else if (maxText.isEmpty() && !minText.isEmpty()) {
                                        topic.min = Float.valueOf(minText);
                                        topic.max = null;
                                    } else {
                                        Toast.makeText(context, "Please check again", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    settingDialog.dismiss();
                                }
                            });
                            max = settingDialog.findViewById(R.id.max);
                            min = settingDialog.findViewById(R.id.min);
                        }
                        Topic topic = topics.get(number);
                        max.setText(topic.max == null ? null : topic.max.toString());
                        min.setText(topic.min == null ? null : topic.min.toString());
                        settingDialog.show();
                    }
                });
                convertView.setTag(viewHolder);
            }
            Topic topic = topics.get(position);
            viewHolder.topicTextView.setText(topic.name);
            viewHolder.statusIcon.setBackgroundColor(topic.isSubscribed ? 0xff99cc00 : 0xffcc0000); //0xff669900,0xff99cc00 - red : 0xffcc0000,0xffff4444 - green
            viewHolder.messageTextView.setText(topic.value);
            viewHolder.publishButton.setTag(position);
            viewHolder.toggleBell.setChecked(topic.notify);
            viewHolder.toggleBell.setTag(position);
            viewHolder.settingButton.setTag(position);
            return convertView;
        }

        class ViewHolder {
            TextView topicTextView, messageTextView;
            View statusIcon;
            ImageButton publishButton, settingButton;
            ToggleButton toggleBell;
        }
    }
}
/*connectionReceiver = new BroadcastReceiver() {@Override
            public void onReceive(Context context, Intent intent) {
                NetworkInfo ni = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {}}};
          registerReceiver(connectionReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));*/