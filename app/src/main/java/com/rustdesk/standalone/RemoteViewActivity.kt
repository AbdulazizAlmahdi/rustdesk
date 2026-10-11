package com.rustdesk.standalone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rustdesk.standalone.client.DirectClientConnection
import com.rustdesk.standalone.databinding.ActivityRemoteViewBinding

class RemoteViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteViewBinding
    private var clientConnection: DirectClientConnection? = null

    companion object {
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PIN = "extra_pin"

        fun start(context: Context, host: String, port: Int, pin: String) {
            val intent = Intent(context, RemoteViewActivity::class.java).apply {
                putExtra(EXTRA_HOST, host)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_PIN, pin)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 8443)
        val pin = intent.getStringExtra(EXTRA_PIN) ?: "123456"

        binding.tvRemoteInfo.text = "متصل بـ: $host:$port"
        binding.progressBarConnecting.visibility = View.VISIBLE

        binding.btnDisconnect.setOnClickListener {
            clientConnection?.disconnect()
            finish()
        }

        setupTouchListener()
        startConnection(host, port, pin)
    }

    private fun startConnection(host: String, port: Int, pin: String) {
        clientConnection = DirectClientConnection(
            host = host,
            port = port,
            pin = pin,
            onConnected = { _, _ ->
                binding.progressBarConnecting.visibility = View.GONE
                Toast.makeText(this, "تم الاتصال المباشر بنجاح!", Toast.LENGTH_SHORT).show()
            },
            onFrameReceived = { bitmap ->
                binding.ivRemoteScreen.setImageBitmap(bitmap)
            },
            onError = { error ->
                binding.progressBarConnecting.visibility = View.GONE
                Toast.makeText(this, "خطأ بالاتصال: $error", Toast.LENGTH_LONG).show()
            }
        )
        clientConnection?.connect()
    }

    private fun setupTouchListener() {
        binding.ivRemoteScreen.setOnTouchListener { view, event ->
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            if (w <= 0 || h <= 0) return@setOnTouchListener true

            val normX = (event.x / w).coerceIn(0f, 1f)
            val normY = (event.y / h).coerceIn(0f, 1f)

            val actionStr = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> "DOWN"
                MotionEvent.ACTION_MOVE -> "MOVE"
                MotionEvent.ACTION_UP -> "UP"
                else -> return@setOnTouchListener true
            }

            clientConnection?.sendInput(actionStr, normX, normY)
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clientConnection?.disconnect()
    }
}
