package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.R
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ


class sockets : AppCompatActivity() {
    private var log_tag : String = "MY_LOG_TAG"
    private lateinit var tvLog: TextView
    private lateinit var tvSockets: TextView
    private lateinit var btnSend: Button
    private lateinit var btnShow: Button
    private lateinit var handler: Handler
    private val serverIP = "192.168.0.117"
    private val port = 8080


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sockets)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        handler = Handler(Looper.getMainLooper())
        tvLog = findViewById(R.id.tvLog)
        btnSend = findViewById(R.id.btnSendToServer)
        btnShow = findViewById(R.id.btnShowAllData)
        tvSockets = findViewById(R.id.tvSockets)

        btnSend.setOnClickListener {
            Thread {
                sendToServer("Hello from Android!")
            }.start()
        }
        btnShow.setOnClickListener {
            Thread {
                sendToServer("show")
            }.start()
        }
    }

    private fun sendToServer(mes: String){
        try{
            val socket = ZContext().createSocket(SocketType.REQ)

            socket.connect("tcp://${serverIP}:${port}")

            val request = mes
            socket.send(request.toByteArray(ZMQ.CHARSET), 0)
            Log.d(log_tag, "[CLIENT] Send: $request")

            handler.post{
                tvLog.append("\n[CLIENT] Send: $request")
            }
            Thread.sleep(1000)
            val reply = socket.recv(0)
            val response = String(reply, ZMQ.CHARSET)
            Log.d(log_tag, "[SERVER] Received: $response")

            handler.post {
                tvSockets.text = "Received MSG from Server "
                tvLog.append("\n[SERVER] Received: $response")
            }
            socket.close()
        }
        catch (e: Exception) {
            Log.e(log_tag, "Ошибка ZeroMQ", e)
            handler.post {
                tvSockets.text = "Ошибка"
                tvLog.append("\nОшибка: ${e.message}")
            }
        }
    }

}