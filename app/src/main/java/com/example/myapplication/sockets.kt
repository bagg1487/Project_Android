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
import android.os.Environment
import android.widget.EditText
import java.io.File

class sockets : AppCompatActivity() {
    private var log_tag : String = "MY_LOG_TAG"
    private lateinit var tvLog: TextView
    private lateinit var tvSockets: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var etServerIP: EditText
    private lateinit var btnSend: Button
    private lateinit var btnShow: Button
    private lateinit var btnCheckConnection: Button
    private lateinit var handler: Handler
    private var serverIP = "172.20.10.3"
    private val port = 8080
    private var isConnected = false
    private var reconnectHandler = Handler(Looper.getMainLooper())
    private var lastSentFileName: String = ""
    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (!isConnected) {
                checkConnection()
            } else {
                forceSendLastFile()
            }
            reconnectHandler.postDelayed(this, 5000)
        }
    }

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
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        etServerIP = findViewById(R.id.etServerIP)
        btnCheckConnection = findViewById(R.id.btnCheckConnection)

        btnCheckConnection.setOnClickListener {
            checkConnection()
        }

        btnSend.setOnClickListener {
            if (!isConnected) {
                handler.post {
                    appendToLog("Сначала подключись к серверу")
                }
                return@setOnClickListener
            }
            Thread {
                try {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val files = downloadsDir.listFiles { file ->
                        file.name.startsWith("location_") && file.name.endsWith(".json")
                    }
                    if (files == null || files.isEmpty()) {
                        handler.post {
                            appendToLog("Нет JSON файлов")
                        }
                        return@Thread
                    }
                    val file = files.last()
                    sendFileToServer(file)
                } catch (e: Exception) {
                    handler.post {
                        appendToLog("Ошибка: ${e.message}")
                    }
                }
            }.start()
        }

        btnShow.setOnClickListener {
            if (!isConnected) {
                handler.post {
                    appendToLog("Сначала подключись к серверу")
                }
                return@setOnClickListener
            }
            Thread {
                sendToServer("show")
            }.start()
        }

        reconnectHandler.post(reconnectRunnable)
    }

    private fun forceSendLastFile() {
        Thread {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val files = downloadsDir.listFiles { file ->
                    file.name.startsWith("location_") && file.name.endsWith(".json")
                }

                if (files == null || files.isEmpty()) {
                    return@Thread
                }

                val sortedFiles = files.sortedByDescending { it.lastModified() }
                val latestFile = sortedFiles.firstOrNull() ?: return@Thread

                sendFileToServer(latestFile)
            } catch (e: Exception) {
                Log.e(log_tag, "Ошибка при отправке: ${e.message}")
            }
        }.start()
    }

    private fun sendFileToServer(file: File) {
        try {
            val fileContent = file.readText()
            val socket = ZContext().createSocket(SocketType.REQ)
            socket.setReceiveTimeOut(5000)
            socket.connect("tcp://$serverIP:$port")
            socket.send(fileContent.toByteArray(ZMQ.CHARSET), 0)

            val reply = socket.recv(0)
            if (reply == null) {
                isConnected = false
                handler.post {
                    appendToLog("Потеря соединения с сервером")
                }
                socket.close()
                return
            }

            val response = String(reply, ZMQ.CHARSET)

            if (response.startsWith("OK:")) {
                lastSentFileName = file.name
                handler.post {
                    appendToLog("✅ Отправлен файл: ${file.name}")
                    appendToLog("📥 Ответ сервера: $response")
                }
            } else {
                handler.post {
                    appendToLog("⚠️ Ошибка сервера: $response")
                }
            }

            socket.close()
            isConnected = true
        } catch (e: Exception) {
            isConnected = false
            handler.post {
                appendToLog("❌ Ошибка отправки файла: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reconnectHandler.removeCallbacks(reconnectRunnable)
    }

    private fun checkConnection() {
        serverIP = etServerIP.text.toString().trim()
        if (serverIP.isEmpty()) {
            serverIP = "172.20.10.3"
        }

        val currentIP = serverIP
        Log.d(log_tag, "Пытаюсь подключиться к $currentIP:$port")

        handler.post {
            appendToLog("🔄 Подключаюсь к $currentIP...")
        }

        Thread {
            try {
                val socket = ZContext().createSocket(SocketType.REQ)
                socket.setReceiveTimeOut(3000)
                Log.d(log_tag, "Создан сокет, подключаюсь...")

                socket.connect("tcp://$currentIP:$port")
                Log.d(log_tag, "Подключился, отправляю ping...")

                socket.send("ping".toByteArray(ZMQ.CHARSET), 0)
                Log.d(log_tag, "Ping отправлен, жду ответ...")

                val reply = socket.recv(0)
                Log.d(log_tag, "Получен ответ: ${String(reply, ZMQ.CHARSET)}")

                socket.close()

                if (reply != null) {
                    isConnected = true
                    handler.post {
                        tvConnectionStatus.text = "Подключено к $currentIP"
                        tvConnectionStatus.setTextColor(0xFF00FF00.toInt())
                        appendToLog("✅ Подключено к серверу $currentIP")
                    }
                    forceSendLastFile()
                }
            } catch (e: Exception) {
                Log.e(log_tag, "Ошибка подключения: ${e.message}")
                isConnected = false
                handler.post {
                    tvConnectionStatus.text = "Ошибка подключения к $currentIP"
                    tvConnectionStatus.setTextColor(0xFFFF0000.toInt())
                    appendToLog("❌ Ошибка: ${e.message}")
                }
            }
        }.start()
    }

    private fun sendToServer(mes: String){
        try{
            val socket = ZContext().createSocket(SocketType.REQ)
            socket.setReceiveTimeOut(5000)
            socket.connect("tcp://${serverIP}:${port}")

            val request = mes
            socket.send(request.toByteArray(ZMQ.CHARSET), 0)
            Log.d(log_tag, "[CLIENT] Send: $request")

            val reply = socket.recv(0)
            if (reply == null) {
                isConnected = false
                handler.post {
                    appendToLog("Потеря соединения с сервером")
                }
                socket.close()
                return
            }

            val response = String(reply, ZMQ.CHARSET)
            Log.d(log_tag, "[SERVER] Received: $response")

            handler.post {
                tvSockets.text = "Received MSG from Server"
                appendToLog("[SERVER] Received: $response")
            }
            socket.close()
            isConnected = true
        }
        catch (e: Exception) {
            isConnected = false
            Log.e(log_tag, "Ошибка ZeroMQ", e)
            handler.post {
                tvSockets.text = "Ошибка"
                appendToLog("Ошибка: ${e.message}")
            }
        }
    }

    private fun appendToLog(text: String) {
        tvLog.append("\n$text")
        val layout = tvLog.layout
        if (layout != null) {
            val scrollAmount = tvLog.layout.getLineTop(tvLog.lineCount) - tvLog.height
            if (scrollAmount > 0) {
                tvLog.scrollTo(0, scrollAmount)
            } else {
                tvLog.scrollTo(0, 0)
            }
        }
    }
}