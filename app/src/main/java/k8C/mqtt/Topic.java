package k8C.mqtt;

/**
 * Created by AnhKhoaChu on 3/9/2019.
 */
class Topic {
    boolean isSubscribed = true, notify; //isSubscribed: subscription setting in MqttFragment, indicated by the red green status icon; notify: notification setting for both MqttFragment and MqttService
    Float max, min; //upper and lower limits of value
    String name, value; //mqtt topic and mqtt message
}
