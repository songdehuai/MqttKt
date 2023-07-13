package com.ppz.mqttkt

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.NetworkUtils

class MainActivity : AppCompatActivity() {

    private val btnSend by lazy { findViewById<Button>(R.id.btn_send) }
    private val btnClean by lazy { findViewById<Button>(R.id.btn_clean) }
    private val btnConnect by lazy { findViewById<Button>(R.id.btn_connect) }
    private val btnDisConnect by lazy { findViewById<Button>(R.id.btn_dis_connect) }
    private val btnSubscribe by lazy { findViewById<Button>(R.id.btn_subscribe) }
    private val tvStatusClient by lazy { findViewById<TextView>(R.id.tv_status_client) }
    private val tvStatusServer by lazy { findViewById<TextView>(R.id.tv_status_server) }

    private val etText by lazy { findViewById<EditText>(R.id.et_text) }

    private val etLog by lazy { findViewById<EditText>(R.id.et_log) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        tvStatusServer.text = "${NetworkUtils.getIPAddress(true)} :已启动"

        MqttClient.connectAction {
            tvStatusClient.post {
                tvStatusClient.setTextColor(if (it) Color.GREEN else Color.RED)
                tvStatusClient.text = if (it) "已连接" else "未连接"
            }
        }

        btnClean.setOnClickListener {
            clean()
        }

        btnConnect.setOnClickListener {
            MqttClient.start(this)
        }

        btnDisConnect.setOnClickListener {
            MqttClient.stop(this)
        }

        btnSubscribe.setOnClickListener {
            MqttClient.subscribe("/topic/up") { topic, message ->
                log("topic:${topic}, ${message} ${System.currentTimeMillis()}")
            }
        }

        btnSend.setOnClickListener {
            val message = """{"message":"${etText.text} ${System.currentTimeMillis()}"}"""
            MqttClient.publish("/topic/down", message)
        }

        MqttServer.start(this)
    }

    private fun clean() {
        etLog.post {
            etLog.setText("")
        }
    }

    private fun log(log: String) {
        etLog.post {
            etLog.append("${log}\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MqttClient.stop(this)
    }


}