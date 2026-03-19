package com.example.myapplication

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.TrafficStats
import android.os.*
import android.telephony.*
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*

class sockets : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var etServerIP: EditText
    private lateinit var tvLog: TextView
    private lateinit var btnCheckConnection: Button

    private val handler = Handler(Looper.getMainLooper())

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
            val ip = etServerIP.text.toString()
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
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.WAKE_LOCK
        )
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
        val ip = etServerIP.text.toString()
        if (ip.isNotEmpty()) {
            DataCollectorService.serverIP = ip
        }
        startService(Intent(this, DataCollectorService::class.java))
        log("Background service started automatically")
    }

    private fun checkConnection() {
        log("Checking connection to ${DataCollectorService.serverIP}:${DataCollectorService.port}...")
        btnCheckConnection.isEnabled = false
        tvConnectionStatus.text = "Connecting..."
        tvConnectionStatus.setTextColor(0xFFFFFF00.toInt())

        Thread {
            val ok = sendPing()
            handler.post {
                btnCheckConnection.isEnabled = true
                log("Connection check result: $ok")
                if (ok) {
                    tvConnectionStatus.text = "Connected"
                    tvConnectionStatus.setTextColor(0xFF00FF00.toInt())
                    log("Connected to server!")
                } else {
                    tvConnectionStatus.text = "Connection failed"
                    tvConnectionStatus.setTextColor(0xFFFF0000.toInt())
                    log("Failed to connect to ${DataCollectorService.serverIP}:${DataCollectorService.port}")
                }
            }
        }.start()
    }

    private fun sendPing(): Boolean {
        return try {
            ZContext().use { ctx ->
                val socket = ctx.createSocket(SocketType.REQ)
                socket.setReceiveTimeOut(3000)
                socket.connect("tcp://${DataCollectorService.serverIP}:${DataCollectorService.port}")
                socket.send("ping".toByteArray(), 0)
                val response = socket.recv(0)
                if (response != null) {
                    val responseStr = String(response, ZMQ.CHARSET).trim()
                    log("Server response: '$responseStr'")
                    responseStr.startsWith("pong")
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            log("Ping error: ${e.message}")
            false
        }
    }

    private fun log(msg: String) {
        handler.post {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val time = sdf.format(Date())
            tvLog.append("[$time] $msg\n")
            try {
                val layout = tvLog.layout
                if (layout != null) {
                    val scrollAmount = layout.getLineTop(tvLog.lineCount) - tvLog.height
                    if (scrollAmount > 0) {
                        tvLog.scrollTo(0, scrollAmount)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    class DataCollectorService : Service() {

        val LOG_TAG: String = "DATA_COLLECTOR"
        private val serviceJob = Job()
        private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

        private val wakeLock: PowerManager.WakeLock by lazy {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp:LocationWakeLock"
            )
        }

        private var currentLocation: Location? = null
        private var isConnected = false

        companion object {
            var serverIP = "172.20.10.2"
            const val port = 8080

            var filterLocation = true
            var filterTelephony = true
            var filterTraffic = true
            var filterLte = true
            var filterGsm = true
            var filterWcdma = true
        }

        private val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        override fun onCreate() {
            super.onCreate()
            Log.d(LOG_TAG, "Service onCreate started")
            startForegroundService()
            Log.d(LOG_TAG, "startForegroundService completed")

            try {
                wakeLock.acquire(10 * 60 * 1000L)
                Log.d(LOG_TAG, "WakeLock acquired")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "WakeLock error: ${e.message}")
            }

            startLocation()
            Log.d(LOG_TAG, "startLocation completed")

            checkServerConnection()
            Log.d(LOG_TAG, "checkServerConnection started")

            startDataCollection()
            Log.d(LOG_TAG, "startDataCollection started")
        }

        private fun startForegroundService() {
            val notificationIntent = Intent(this, sockets::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, "location_channel")
                .setContentTitle(" Location Collection Active")
                .setContentText("Collecting location and cell tower data every 5 seconds")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            startForeground(1, notification)
        }

        private fun checkServerConnection() {
            serviceScope.launch {
                isConnected = sendPing()
                Log.d(LOG_TAG, "Initial connection status: $isConnected")
                if (!isConnected) {
                    delay(10000)
                    checkServerConnection()
                }
            }
        }

        private suspend fun sendPing(): Boolean {
            return withContext(Dispatchers.IO) {
                var result = false
                try {
                    Log.d(LOG_TAG, "Attempting to ping $serverIP:$port")
                    ZContext().use { ctx ->
                        val socket = ctx.createSocket(SocketType.REQ)
                        socket.setReceiveTimeOut(5000)
                        socket.connect("tcp://$serverIP:$port")

                        val sent = socket.send("ping".toByteArray(), 0)
                        Log.d(LOG_TAG, "Message sent: $sent")

                        val response = socket.recv(0)
                        if (response != null) {
                            val responseStr = String(response, ZMQ.CHARSET).trim()
                            Log.d(LOG_TAG, "Received: '$responseStr'")
                            result = responseStr.startsWith("pong")
                        } else {
                            Log.e(LOG_TAG, "No response received (timeout)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Ping error: ${e.message}")
                }
                Log.d(LOG_TAG, "Ping result: $result")
                result
            }
        }

        private fun startLocation() {
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) return

                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000, 5f, locationListener
                )
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000, 5f, locationListener
                )

                currentLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) { }
        }

        private fun startDataCollection() {
            serviceScope.launch {
                while (true) {
                    delay(5000)
                    if (isConnected) {
                        try {
                            val data = collectAllData()
                            if (data.length() > 0) {
                                val file = saveJson(data)
                                sendAllFiles()
                            }
                        } catch (e: Exception) { }
                    }
                }
            }
        }

        private fun collectAllData(): JSONObject {
            val json = JSONObject()
            json.put("timestamp", System.currentTimeMillis())

            if (filterLocation && currentLocation != null) {
                json.put("location", collectLocation())
            }

            if (filterTelephony) {
                val cellInfo = collectCellInfo()
                if (cellInfo.length() > 0) {
                    json.put("telephony", cellInfo)
                }
            }

            if (filterTraffic) {
                json.put("traffic", collectTraffic())
            }

            return json
        }

        private fun collectLocation(): JSONObject {
            val json = JSONObject()
            currentLocation?.let {
                json.put("latitude", it.latitude)
                json.put("longitude", it.longitude)
                json.put("altitude", it.altitude)
                json.put("accuracy", it.accuracy)
                json.put("speed", it.speed)
                json.put("bearing", it.bearing)
                json.put("provider", it.provider)
            }
            return json
        }

        private fun collectCellInfo(): JSONObject {
            val json = JSONObject()
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return json
                }

                val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val allCellInfo = tm.allCellInfo

                if (allCellInfo != null && allCellInfo.isNotEmpty()) {
                    var cellIndex = 0
                    allCellInfo.forEach { cell ->
                        val cellJson = JSONObject()
                        var includeCell = false

                        when (cell) {
                            is CellInfoLte -> {
                                if (filterLte) {
                                    val identity = cell.cellIdentity
                                    val strength = cell.cellSignalStrength
                                    cellJson.put("type", "LTE")
                                    cellJson.put("ci", identity.ci)
                                    cellJson.put("pci", identity.pci)
                                    cellJson.put("tac", identity.tac)
                                    cellJson.put("earfcn", identity.earfcn)
                                    cellJson.put("mcc", identity.mcc)
                                    cellJson.put("mnc", identity.mnc)
                                    cellJson.put("dbm", strength.dbm)
                                    cellJson.put("rsrp", strength.dbm)
                                    includeCell = true
                                }
                            }
                            is CellInfoGsm -> {
                                if (filterGsm) {
                                    val identity = cell.cellIdentity
                                    val strength = cell.cellSignalStrength
                                    cellJson.put("type", "GSM")
                                    cellJson.put("cid", identity.cid)
                                    cellJson.put("lac", identity.lac)
                                    cellJson.put("dbm", strength.dbm)
                                    includeCell = true
                                }
                            }
                            is CellInfoWcdma -> {
                                if (filterWcdma) {
                                    val identity = cell.cellIdentity
                                    val strength = cell.cellSignalStrength
                                    cellJson.put("type", "WCDMA")
                                    cellJson.put("cid", identity.cid)
                                    cellJson.put("lac", identity.lac)
                                    cellJson.put("psc", identity.psc)
                                    cellJson.put("dbm", strength.dbm)
                                    includeCell = true
                                }
                            }
                        }

                        if (includeCell) {
                            json.put("cell_$cellIndex", cellJson)
                            cellIndex++
                        }
                    }
                }
            } catch (e: Exception) { }
            return json
        }

        private fun collectTraffic(): JSONObject {
            val json = JSONObject()
            try {
                json.put("total_rx_bytes", TrafficStats.getTotalRxBytes())
                json.put("total_tx_bytes", TrafficStats.getTotalTxBytes())
                json.put("mobile_rx_bytes", TrafficStats.getMobileRxBytes())
                json.put("mobile_tx_bytes", TrafficStats.getMobileTxBytes())
            } catch (e: Exception) { }
            return json
        }

        private fun getHeapDir(): File {
            val dir = File(getExternalFilesDir(null), "HeapMap")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        private fun saveJson(data: JSONObject): File {
            val fileName = "data_${System.currentTimeMillis()}.json"
            val file = File(getHeapDir(), fileName)
            file.writeText(data.toString())
            return file
        }

        private suspend fun sendAllFiles() {
            withContext(Dispatchers.IO) {
                val files = getHeapDir().listFiles { f -> f.extension == "json" } ?: return@withContext
                files.sortedBy { it.lastModified() }.forEach { file ->
                    if (sendFile(file)) {
                        file.delete()
                    }
                }
            }
        }

        private fun sendFile(file: File): Boolean {
            return try {
                ZContext().use { ctx ->
                    val socket = ctx.createSocket(SocketType.REQ)
                    socket.setReceiveTimeOut(5000)
                    socket.connect("tcp://$serverIP:$port")
                    socket.send(file.readText().toByteArray(ZMQ.CHARSET), 0)
                    socket.recv(0) != null
                }
            } catch (e: Exception) {
                false
            }
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            return START_STICKY
        }

        override fun onDestroy() {
            super.onDestroy()
            serviceJob.cancel()
            try {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.removeUpdates(locationListener)
            } catch (e: Exception) { }
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            stopForeground(true)
            Log.d(LOG_TAG, "Service destroyed")
        }

        override fun onBind(intent: Intent?): IBinder? = null
    }
}