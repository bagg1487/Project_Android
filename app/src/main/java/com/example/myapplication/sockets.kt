package com.example.myapplication

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.TrafficStats
import android.os.*
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.*

class sockets : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var etServerIP: EditText
    private lateinit var tvLog: TextView
    private lateinit var btnCheckConnection: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sockets)

        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        etServerIP = findViewById(R.id.etServerIP)
        tvLog = findViewById(R.id.tvLog)
        btnCheckConnection = findViewById(R.id.btnCheckConnection)

        tvLog.text = "Ready...\n"
        tvConnectionStatus.text = "Не подключено"
        tvConnectionStatus.setTextColor(0xFFFF0000.toInt())

        createNotificationChannel()
        requestPermissions()

        btnCheckConnection.setOnClickListener {
            val ip = etServerIP.text.toString().trim()
            if (ip.isNotEmpty()) {
                DataCollectorService.serverIP = ip
            }
            checkConnection()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for location collection service"
                setSound(null, null)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(Manifest.permission.FOREGROUND_SERVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val req = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (req.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, req.toTypedArray(), 1)
        } else {
            startBackgroundService()
            log("Permissions granted")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startBackgroundService()
            log("Permissions granted")
        } else {
            log("Permissions denied")
        }
    }

    private fun startBackgroundService() {
        val ip = etServerIP.text.toString().trim()
        if (ip.isNotEmpty()) {
            DataCollectorService.serverIP = ip
        }
        val intent = Intent(this, DataCollectorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        log("Background service started automatically")
    }

    private fun checkConnection() {
        val currentIp = DataCollectorService.serverIP
        log("Checking connection to $currentIp:${DataCollectorService.port}...")
        btnCheckConnection.isEnabled = false
        tvConnectionStatus.text = "Connecting..."
        tvConnectionStatus.setTextColor(0xFFFFFF00.toInt())

        lifecycleScope.launch(Dispatchers.IO) {
            val ok = sendPing(currentIp)
            withContext(Dispatchers.Main) {
                btnCheckConnection.isEnabled = true
                tvConnectionStatus.text = if (ok) "Connected" else "Connection failed"
                tvConnectionStatus.setTextColor(if (ok) 0xFF00FF00.toInt() else 0xFFFF0000.toInt())
                log(if (ok) "Connected to server!" else "Failed to connect to $currentIp")
            }
        }
    }

    private fun sendPing(ip: String): Boolean {
        return try {
            ZContext().use { ctx ->
                ctx.createSocket(SocketType.REQ).use { socket ->
                    socket.receiveTimeOut = 5000
                    socket.sendTimeOut = 3000
                    socket.connect("tcp://$ip:${DataCollectorService.port}")
                    socket.send("ping".toByteArray(), 0)
                    val response = socket.recv(0)
                    if (response != null) {
                        val responseStr = String(response, ZMQ.CHARSET).trim().replace("\u0000", "")
                        log("Server response: '$responseStr'")
                        responseStr == "pong"
                    } else {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            log("Ping error: ${e.message}")
            false
        }
    }

    private fun log(msg: String) {
        val activityRef = WeakReference(this)
        lifecycleScope.launch(Dispatchers.Main) {
            val activity = activityRef.get() ?: return@launch
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val time = sdf.format(Date())
            activity.tvLog.append("[$time] $msg\n")
            try {
                val layout = activity.tvLog.layout
                if (layout != null) {
                    val scrollAmount = layout.getLineTop(activity.tvLog.lineCount) - activity.tvLog.height
                    if (scrollAmount > 0) {
                        activity.tvLog.scrollTo(0, scrollAmount)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    class DataCollectorService : Service() {

        private val LOG_TAG = "DATA_COLLECTOR"
        private val NOTIFICATION_ID = 101

        private val serviceJob = Job()
        private val scope = CoroutineScope(Dispatchers.IO + serviceJob)

        companion object {
            @Volatile var serverIP = "172.20.10.2"
            const val port = 8080
        }

        private var context: ZContext? = null
        private var socket: ZMQ.Socket? = null

        @Volatile private var isConnected = false
        private var zmqJob: Job? = null

        private val sendQueue = ConcurrentLinkedQueue<File>()

        @Volatile private var currentLocation: Location? = null

        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun onCreate() {
            super.onCreate()
            startForegroundNotification()
            startZmq()
            startLocation()
            startLoop()
        }

        private fun startForegroundNotification() {
            val notification = NotificationCompat.Builder(this, "location_channel")
                .setContentTitle("Сбор данных")
                .setContentText("Приложение собирает метрики в фоновом режиме")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        }

        private fun startZmq() {
            zmqJob?.cancel()
            zmqJob = scope.launch {
                while (isActive) {
                    try {
                        Log.d(LOG_TAG, "ZMQ: Connecting to $serverIP:$port")

                        closeZmqResources()

                        val ctx = ZContext()
                        context = ctx
                        val sock = ctx.createSocket(SocketType.REQ)
                        socket = sock

                        sock.linger = 0
                        sock.receiveTimeOut = 10000
                        sock.sendTimeOut = 5000

                        sock.connect("tcp://$serverIP:$port")

                        Log.d(LOG_TAG, "ZMQ: Connected, sending ping...")
                        sock.send("ping")

                        val reply = sock.recv()
                        if (reply != null) {
                            val replyStr = String(reply, ZMQ.CHARSET).trim().replace("\u0000", "")
                            Log.d(LOG_TAG, "ZMQ: Reply received: '$replyStr'")
                            if (replyStr == "pong") {
                                isConnected = true
                                Log.d(LOG_TAG, "ZMQ: CONNECTED SUCCESSFULLY!")
                                return@launch
                            }
                        }
                        isConnected = false
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "ZMQ: Connect error: ${e.message}")
                        isConnected = false
                    }
                    delay(5000)
                }
            }
        }

        private fun reconnect() {
            isConnected = false
            startZmq()
        }

        private fun startLoop() {
            scope.launch {
                while (isActive) {
                    delay(5000)
                    try {
                        val data = collectAllData()
                        val file = saveJson(data)
                        sendQueue.add(file)
                        processQueue()
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Loop error: ${e.message}")
                    }
                }
            }
        }

        private suspend fun processQueue() {
            if (!isConnected) return

            while (sendQueue.isNotEmpty() && isConnected) {
                val file = sendQueue.peek() ?: break
                val ok = withContext(Dispatchers.IO) { sendFile(file) }

                if (ok) {
                    file.delete()
                    sendQueue.poll()
                } else {
                    reconnect()
                    break
                }
            }
        }

        private fun sendFile(file: File): Boolean {
            return try {
                if (!file.exists()) return true
                val data = file.readText()

                socket?.let { s ->
                    s.send(data)
                    val reply = s.recv()
                    reply != null
                } ?: false
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Send error: ${e.message}")
                false
            }
        }

        private fun collectAllData(): JSONObject {
            return JSONObject().apply {
                put("timestamp", System.currentTimeMillis())

                currentLocation?.let {
                    put("location", JSONObject().apply {
                        put("latitude", it.latitude)
                        put("longitude", it.longitude)
                        put("altitude", it.altitude)
                        put("accuracy", it.accuracy)
                    })
                }

                put("traffic", JSONObject().apply {
                    put("total_rx_bytes", TrafficStats.getTotalRxBytes())
                    put("total_tx_bytes", TrafficStats.getTotalTxBytes())
                })
            }
        }

        private fun saveJson(data: JSONObject): File {
            val dir = File(getExternalFilesDir(null), "HeapMap")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "data_${System.currentTimeMillis()}.json")
            file.writeText(data.toString())
            return file
        }

        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        private fun startLocation() {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000, 5f
                ) { location ->
                    currentLocation = location
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Location setup failed: ${e.message}")
            }
        }

        private fun closeZmqResources() {
            try {
                socket?.close()
                context?.close()
            } catch (_: Exception) {}
            socket = null
            context = null
        }

        override fun onBind(intent: Intent?): IBinder? = null

        override fun onDestroy() {
            super.onDestroy()
            serviceJob.cancel()
            closeZmqResources()
        }
    }
}