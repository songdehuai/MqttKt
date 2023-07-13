package com.ppz.mqttkt

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.blankj.utilcode.util.LogUtils
import io.moquette.BrokerConstants
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import io.moquette.broker.security.IAuthenticator
import io.moquette.broker.security.IAuthorizatorPolicy
import io.moquette.broker.subscriptions.Topic
import io.moquette.interception.AbstractInterceptHandler
import io.moquette.interception.InterceptHandler
import io.moquette.interception.messages.InterceptConnectMessage
import io.moquette.interception.messages.InterceptConnectionLostMessage
import io.moquette.interception.messages.InterceptDisconnectMessage
import io.netty.buffer.Unpooled
import io.netty.handler.codec.mqtt.MqttMessageBuilders
import io.netty.handler.codec.mqtt.MqttQoS
import java.nio.charset.Charset
import java.util.Properties
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.concurrent.thread


class MqttServer : Service() {


    companion object {

        private val TAG = "MQTT服务端"

        private const val MQTT_SERVER = "MqttServer"

        private var logger: Logger? = null
        private var mqttBroker: Server? = null

        fun start(context: Context) {
            val intent = Intent(context, MqttServer::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MqttServer::class.java)
            context.stopService(intent)
        }


        fun internalPublish(topic: String, payload: String) {
            val msgByteBuf = Unpooled.copiedBuffer(payload, Charset.defaultCharset())
            val message = MqttMessageBuilders.publish()
                .topicName(topic)
                .retained(false)
                .qos(MqttQoS.EXACTLY_ONCE)
                .payload(msgByteBuf)
                .build()
            mqttBroker?.internalPublish(message, MQTT_SERVER)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startService()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
    }

    private val connectListener = object : AbstractInterceptHandler() {
        override fun getID(): String {
            return "connectListener"
        }

        override fun onConnect(msg: InterceptConnectMessage?) {
            super.onConnect(msg)
        }

        override fun onDisconnect(msg: InterceptDisconnectMessage?) {
            super.onDisconnect(msg)

        }

        override fun onConnectionLost(msg: InterceptConnectionLostMessage?) {
            super.onConnectionLost(msg)
        }
    }

    private val authorizatorPolicy = object : IAuthorizatorPolicy {
        override fun canWrite(topic: Topic?, user: String?, client: String?): Boolean {
            return true
        }

        override fun canRead(topic: Topic?, user: String?, client: String?): Boolean {
            return true
        }

    }

    private val mqttAuthenticator = IAuthenticator { clientId, username, password ->
        LogUtils.i(TAG, "设备连接", "允许连接", "clientId: $clientId, userName: $username, password: ${String(password)}")
        true
    }


    private fun startService() {
        logger = LogManager.getLogManager().getLogger("moquette")
        val properties = Properties().apply {
            set(BrokerConstants.PORT_PROPERTY_NAME, "1883")
            set(BrokerConstants.WEB_SOCKET_PORT_PROPERTY_NAME, "8883")
            set(BrokerConstants.INFLIGHT_WINDOW_SIZE, "20")
            set(BrokerConstants.IMMEDIATE_BUFFER_FLUSH_PROPERTY_NAME, "true")
            set(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "false")
            set(BrokerConstants.ENABLE_TELEMETRY_NAME, "false")
        }
        val handlers = listOf<InterceptHandler>(connectListener)
        mqttBroker = Server()
        thread {
            mqttBroker?.startServer(MemoryConfig(properties), handlers, null, mqttAuthenticator, null)
        }
    }

    private fun stopService() {
        mqttBroker?.stopServer()
    }

}