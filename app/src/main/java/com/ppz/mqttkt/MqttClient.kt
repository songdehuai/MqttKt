package com.ppz.mqttkt

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import com.blankj.utilcode.util.DeviceUtils
import com.blankj.utilcode.util.LogUtils
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.util.concurrent.ConcurrentSkipListMap


class MqttClient : Service() {


    companion object {

        private const val MQTT_HOST = "127.0.0.1"

        private const val MQTT_PORT = 1883

        private const val MQTT_USER_NAME = "mqtt_test"

        private const val MQTT_PASSWD = "mqtt_test"

        private val MQTT_CLIENT_ID by lazy { "${DeviceUtils.getAndroidID()}" }

        private val TAG = "MQTT远程"

        private var mqtt3AsyncClient: Mqtt3AsyncClient? = null

        private val subscribeCallBack = ConcurrentSkipListMap<String, ((topic: String, payload: String) -> Unit)?>()

        private var connectAction: ((Boolean) -> Unit)? = null

        fun connectAction(connectAction: ((Boolean) -> Unit)) {
            this.connectAction = connectAction
        }

        fun start(context: Context) {
            val intent = Intent(context, com.ppz.mqttkt.MqttClient::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, com.ppz.mqttkt.MqttClient::class.java)
            context.stopService(intent)
        }

        fun subscribe(topic: String, action: ((topic: String, payload: String) -> Unit)?) {
            subscribeCallBack[topic] = action
            mqtt3AsyncClient?.let { client ->
                client.subscribeWith().topicFilter(topic).callback { mqtt3Publish ->
                    String(mqtt3Publish.payloadAsBytes).let {
                        subscribeCallBack[topic]?.invoke(mqtt3Publish.topic.toString(), it)
                        LogUtils.i(TAG, "MQTT收到消息", mqtt3Publish.topic.toString(), it)
                    }
                }.send().whenComplete { connAck, throwable ->
                    if (throwable == null) {
                        LogUtils.i(TAG, "MQTT订阅成功", topic)
                    } else {
                        LogUtils.i(TAG, "MQTT订阅失败", topic, throwable.message)
                        subscribeCallBack[topic] = null
                    }
                }
            }
        }

        fun publish(topic: String, payload: String) {
            mqtt3AsyncClient?.publish(
                Mqtt3Publish.builder().topic(topic).payload(payload.toByteArray()).qos(MqttQos.EXACTLY_ONCE).build()
            )
        }

    }

    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        reconnect()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            LogUtils.i("网络可用")
            connect()
        }

        override fun onLost(network: Network) {
            disConnect()
            connectAction?.invoke(false)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {

        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        subscribeCallBack.clear()
        disConnect()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun reconnect() {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun disConnect() {
        mqtt3AsyncClient?.disconnect()?.whenComplete { disconnectAck, throwable ->
            if (throwable == null) {
                connectAction?.invoke(false)
                LogUtils.i(TAG, "MQTT断开成功")
            } else {
                LogUtils.i(TAG, "MQTT断开失败", throwable.message)
            }
        }
        mqtt3AsyncClient = null
    }

    private fun reSubscribe() {
        subscribeCallBack.forEach { t, u ->
            subscribe(t, u)
        }
    }

    private fun connect() {
        mqtt3AsyncClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(MQTT_CLIENT_ID)
            .serverHost(MQTT_HOST)
            .serverPort(MQTT_PORT)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener {
                LogUtils.i(TAG, "MQTT连接成功")
                reSubscribe()
                connectAction?.invoke(true)
            }
            .addDisconnectedListener {
                it.cause.printStackTrace()
                LogUtils.i(TAG, "MQTT连接断开")
            }
            .buildAsync()
        mqtt3AsyncClient?.let { client ->
            client.connectWith()
                .simpleAuth()
                .username(MQTT_USER_NAME)
                .password(MQTT_PASSWD.toByteArray())
                .applySimpleAuth()
                .send()
                .whenComplete { connAck, throwable ->
                    if (throwable != null) {
                        connectAction?.invoke(false)
                        LogUtils.i(TAG, "MQTT连接失败", throwable.message)
                    }
                }
        }
    }
}