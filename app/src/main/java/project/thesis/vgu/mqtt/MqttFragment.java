package project.thesis.vgu.mqtt;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
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
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
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
    MqttConnection mqtt;
    MainHandler handler;
    MqttCallbackExtended mqttCallback;
    boolean notifyInBackground = true; // Options Menu setting for MqttService
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
            }
        };

        mqttCallback = new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.e(MainActivity.TAG, "connectComplete");
                handler.obtainMessage(7).sendToTarget(); // change status textview color to green
                Log.e(MainActivity.TAG, "session present: " + MqttConnection.connectToken.getSessionPresent());
                if (MqttConnection.connectToken.getSessionPresent()) {
                    if (!handler.subscribeOk) handler.subscribePersist(topics);
                    if (!handler.unsubscribeOk) handler.unsubscribePersist(topics);
                } else {
                    List<String> subscribeList = new ArrayList<>();
                    for (Topic topic : topics) // subscribePersist to all topics with isSubscribed = true
                        if (topic.isSubscribed) subscribeList.add(topic.name);
                    handler.subscribeNonPersist(subscribeList.toArray(new String[subscribeList.size()]));
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                handler.obtainMessage(8).sendToTarget(); // change status textview color to red
                Log.e(MainActivity.TAG, "connectionLost");
                mqtt.connect();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                handler.obtainMessage(0, new String[]{topic, message.toString()}).sendToTarget();
                Log.e(MainActivity.TAG, topic + ": " + message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        };
        mqtt = new MqttConnection();
        mqtt.initialize();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.e(MainActivity.TAG, "MqttFragment onCreateView");
        //if (savedInstanceState != null) Log.e(MainActivity.TAG, "onCreateView savedInstanceState != null");
        View view = inflater.inflate(R.layout.fragment_mqtt, container, false);
        setHasOptionsMenu(true);
        tv = view.findViewById(R.id.tv);
        String topicsJson = PreferenceManager.getDefaultSharedPreferences(context).getString("topics", null);
        if (topicsJson != null)
            topics = new Gson().fromJson(topicsJson, new TypeToken<List<Topic>>() {
            }.getType());
        else {
            Topic cond = new Topic(), ph = new Topic(), temp = new Topic();
            cond.name = "k8C/f/cond";
            ph.name = "k8C/f/ph";
            temp.name = "k8C/f/temp";
            topics = new ArrayList<>();
            topics.add(cond);
            topics.add(ph);
            topics.add(temp);
        }
        ListView listView = view.findViewById(R.id.list);
        topicAdapter = new TopicAdapter();
        listView.setAdapter(topicAdapter);
        registerForContextMenu(listView);
        handler = new MainHandler(topics, topicAdapter, tv, context.getApplicationContext());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), connectionCallback);
        if (notifyInBackground)
            notifyInBackground = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("notifyInBackground", false);
        if (notifyInBackground) //service might be running
            context.stopService(new Intent(context, MqttService.class));
        MqttConnection.client.setCallback(mqttCallback);
        if (MqttConnection.client.isConnected()) {
            handler.subscribePersist(topics);
            handler.unsubscribePersist(topics);
            tv.setTextColor(0xffff4444);
        } else {
            mqtt.connect();
            tv.setTextColor(0xff669900);
        }
    }

    @Override
    public void onStop() {
        Log.e(MainActivity.TAG, "MqttFragment onStop");
        ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(connectionCallback);
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("topics", new Gson().toJson(topics)).putBoolean("notifyInBackground", notifyInBackground).apply();
        if (notifyInBackground) {
            for (Topic topic : topics)
                if (topic.notify) {
                    if (Build.VERSION.SDK_INT >= 26) // start ForeGround service in android Oreo and above
                        context.startForegroundService(new Intent(context, MqttService.class));
                    else context.startService(new Intent(context, MqttService.class));
                    break;
                }
            handler.subscribeOk = false;
            handler.unsubscribeOk = false;
        } else mqtt.disconnect();
        super.onStop();
    }

    @Override
    public void onDestroy() {
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
                handler.subscribe(topics.get(position).name);
                break;
            case R.id.unsubscribe:
                handler.unsubscribe(topics.get(position).name);
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        menu.getItem(0).setChecked(notifyInBackground);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.service_setting) {
            notifyInBackground = !item.isChecked();
            item.setChecked(notifyInBackground);
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
                                        handler.publish(topics.get(number).name, ((EditText) publishDialog.findViewById(R.id.publishText)).getText().toString().getBytes(),
                                                ((CheckBox) publishDialog.findViewById(R.id.retain)).isChecked());
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

    static class MainHandler extends Handler {
        List<Topic> topics;
        WeakReference<TopicAdapter> topicAdapterReference;
        WeakReference<TextView> tvReference;
        Context context;
        boolean subscribeOk = false, unsubscribeOk = false;

        public MainHandler(List<Topic> tp, TopicAdapter ta, TextView tv, Context ct) {
            topics = tp;
            topicAdapterReference = new WeakReference<>(ta);
            tvReference = new WeakReference<>(tv);
            context = ct;
        }

        void changeStatus(String topicName, boolean status) {
            for (Topic topic : topics)
                if (topic.name.equals(topicName)) {
                    topic.isSubscribed = status;
                    topicAdapterReference.get().notifyDataSetChanged();
                    break;
                }
        }

        @Override
        public void handleMessage(Message msg) {
            TextView tv = tvReference.get();
            if (tv != null) {
                TopicAdapter topicAdapter = topicAdapterReference.get();
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
                    case 9:
                        List<String> topicNames = Arrays.asList((String[]) msg.obj);
                        for (Topic topic : topics)
                            if (topicNames.contains(topic.name)) topic.isSubscribed = false;
                        topicAdapterReference.get().notifyDataSetChanged();
                        break;
                }
            }
        }

        void subscribe(String topic) {
            try {
                MqttConnection.client.subscribe(topic, 1, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        obtainMessage(1, asyncActionToken.getTopics()[0]).sendToTarget();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        obtainMessage(2, asyncActionToken.getTopics()[0]).sendToTarget();
                    }
                });
            } catch (MqttException e) {
                Log.e(MainActivity.TAG, "subscribePersist exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        void unsubscribe(String topic) {
            try {
                MqttConnection.client.unsubscribe(topic, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        obtainMessage(3, asyncActionToken.getTopics()[0]).sendToTarget();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        obtainMessage(4).sendToTarget();
                    }
                });
            } catch (MqttException e) {
                Log.e(MainActivity.TAG, "unsubscribe exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        void publish(String topic, byte[] message, boolean retain) {
            try {
                MqttConnection.client.publish(topic, message, 1, retain, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        obtainMessage(5).sendToTarget();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        obtainMessage(6).sendToTarget();
                    }
                });
            } catch (MqttException e) {
                Log.e(MainActivity.TAG, "publish exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        void subscribeNonPersist(String[] topics) {
            int[] qos = new int[topics.length];
            Arrays.fill(qos, 1);
            try {
                MqttConnection.client.subscribe(topics, qos, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        subscribeOk = true;
                        Log.e(MainActivity.TAG, "subscribeNonPersist success ");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                        if (MqttConnection.client.isConnected()) {
                            subscribeOk = true;
                            obtainMessage(9, asyncActionToken.getTopics()).sendToTarget();
                        } else subscribeOk = false;
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
            for (Topic topic : topics)
                if (topic.isSubscribed && !topic.notify) subscribeList.add(topic.name);
            if (subscribeList.size() == 0) {
                subscribeOk = true;
                return;
            }
            subscribeNonPersist(subscribeList.toArray(new String[subscribeList.size()]));
        }

        void unsubscribePersist(List<Topic> topics) {
            List<String> unsubscribeList = new ArrayList<>();
            for (Topic topic : topics)
                if (!topic.isSubscribed && topic.notify) unsubscribeList.add(topic.name);
            if (unsubscribeList.size() == 0) {
                unsubscribeOk = true;
                return;
            }
            try {
                MqttConnection.client.unsubscribe(unsubscribeList.toArray(new String[unsubscribeList.size()]), null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        unsubscribeOk = true;
                        Log.e(MainActivity.TAG, "unsubscribePersist success ");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                        unsubscribeOk = MqttConnection.client.isConnected();
                        Log.e(MainActivity.TAG, "unsubscribePersist fail " + e.getMessage());
                    }
                });
            } catch (MqttException e) {
                unsubscribeOk = false;
                Log.e(MainActivity.TAG, "unsubscribePersist exception " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
/*connectionReceiver = new BroadcastReceiver() {@Override
            public void onReceive(Context context, Intent intent) {
                NetworkInfo ni = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
                if (ni != null && ni.isConnected()) {}}};
          registerReceiver(connectionReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));*/