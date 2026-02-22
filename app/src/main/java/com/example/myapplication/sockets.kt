package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.TrafficStats
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import android.telephony.CellInfoLte
import android.telephony.CellInfoGsm
import android.telephony.CellInfoCdma
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthGsm
import android.telephony.TelephonyManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class sockets : AppCompatActivity() {
    private lateinit var tvLog: TextView
    private lateinit var tvSockets: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var etServerIP: EditText
    private lateinit var btnSend: Button
    private lateinit var btnShow: Button
    private lateinit var btnCheckConnection: Button
    private lateinit var btnCollectData: Button
    private lateinit var tvDataDisplay: TextView
    private lateinit var handler: Handler
    private var serverIP = "172.20.10.3"
    private val port = 8080
    private var isConnected = false
    private var reconnectHandler = Handler(Looper.getMainLooper())
    private var lastFullData: JSONObject? = null
    private var currentLocation: Location? = null
    private var lastLocationData: JSONObject? = null
    private var lastCellData: JSONObject? = null
    private var lastTrafficData: JSONObject? = null
    private var backgroundHandler = Handler(Looper.getMainLooper())
    private var isBackgroundRunning = false
    private val tag = "SOCKETS_DEBUG"

    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (!isConnected) checkConnection() else forceSendLastFile()
            reconnectHandler.postDelayed(this, 5000)
        }
    }

    private val backgroundSendRunnable = object : Runnable {
        override fun run() {
            if (isConnected && isBackgroundRunning) {
                collectAndSendDataBackground()
                backgroundHandler.postDelayed(this, 5000)
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            Log.d(tag, "Location changed: ${location.latitude}, ${location.longitude}")
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

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
        btnCollectData = findViewById(R.id.btnCollectData)
        tvDataDisplay = findViewById(R.id.tvDataDisplay)

        requestPermissions()

        btnCheckConnection.setOnClickListener { checkConnection() }

        btnSend.setOnClickListener {
            if (!isConnected) {
                appendToLog("Not connected to server")
                return@setOnClickListener
            }
            Thread {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val files = downloadsDir.listFiles { f -> f.name.startsWith("location_") && f.name.endsWith(".json") }
                files?.lastOrNull()?.let { sendFileToServer(it) }
            }.start()
        }

        btnShow.setOnClickListener {
            if (!isConnected) {
                appendToLog("Not connected to server")
                return@setOnClickListener
            }
            Thread { sendToServer("show") }.start()
        }

        btnCollectData.setOnClickListener { collectAndDisplayData() }

        reconnectHandler.post(reconnectRunnable)
        appendToLog("App started")
    }

    private fun startBackgroundSending() {
        if (!isBackgroundRunning) {
            isBackgroundRunning = true
            backgroundHandler.post(backgroundSendRunnable)
            appendToLog("Background sending started")
        }
    }

    private fun stopBackgroundSending() {
        isBackgroundRunning = false
        backgroundHandler.removeCallbacks(backgroundSendRunnable)
        appendToLog("Background sending stopped")
    }

    private fun collectAndSendDataBackground() {
        Thread {
            try {
                Log.d(tag, "Collecting data in background")
                val locationData = collectLocationData()
                val cellData = collectCellInfo()
                val trafficData = collectTrafficData()

                lastLocationData = locationData
                lastCellData = cellData
                lastTrafficData = trafficData

                val allData = JSONObject().apply {
                    put("location", lastLocationData)
                    put("telephony", lastCellData)
                    put("traffic", lastTrafficData)
                    put("timestamp", System.currentTimeMillis())
                }
                lastFullData = allData

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "network_data_${System.currentTimeMillis()}.json"
                val file = File(downloadsDir, fileName)
                file.writeText(allData.toString(2))
                Log.d(tag, "Data saved to $fileName")

                if (isConnected) {
                    sendFileToServer(file)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in background collection: ${e.message}")
            }
        }.start()
    }

    private fun collectTrafficData(): JSONObject {
        val trafficJson = JSONObject()
        try {
            val mobileRxBytes = if (TrafficStats.getMobileRxBytes() > 0) TrafficStats.getMobileRxBytes() else 0L
            val mobileTxBytes = if (TrafficStats.getMobileTxBytes() > 0) TrafficStats.getMobileTxBytes() else 0L
            val totalRxBytes = if (TrafficStats.getTotalRxBytes() > 0) TrafficStats.getTotalRxBytes() else 0L
            val totalTxBytes = if (TrafficStats.getTotalTxBytes() > 0) TrafficStats.getTotalTxBytes() else 0L

            trafficJson.put("mobile_rx_bytes", mobileRxBytes)
            trafficJson.put("mobile_tx_bytes", mobileTxBytes)
            trafficJson.put("total_rx_bytes", totalRxBytes)
            trafficJson.put("total_tx_bytes", totalTxBytes)
            trafficJson.put("mobile_total_bytes", mobileRxBytes + mobileTxBytes)
            trafficJson.put("total_bytes", totalRxBytes + totalTxBytes)

            val packageManager = packageManager
            val packages = packageManager.getInstalledApplications(0)

            val appTrafficList = JSONObject()
            var totalAppBytes = 0L

            for (app in packages) {
                try {
                    val uid = app.uid
                    val appRx = TrafficStats.getUidRxBytes(uid)
                    val appTx = TrafficStats.getUidTxBytes(uid)
                    val appTotal = if (appRx > 0 && appTx > 0) appRx + appTx else 0L

                    if (appTotal > 0) {
                        totalAppBytes += appTotal
                        val appInfo = JSONObject().apply {
                            put("rx_bytes", appRx)
                            put("tx_bytes", appTx)
                            put("total_bytes", appTotal)
                            put("package_name", app.packageName)
                            put("app_name", packageManager.getApplicationLabel(app) ?: app.packageName)
                        }
                        appTrafficList.put(app.packageName, appInfo)
                    }
                } catch (e: Exception) {
                    // Skip apps without traffic data
                }
            }

            trafficJson.put("total_app_bytes", totalAppBytes)

            val meanBytes = if (appTrafficList.length() > 0) totalAppBytes / appTrafficList.length() else 0L
            val sigmaBytes = (Math.sqrt(appTrafficList.length().toDouble()) * meanBytes / 2.0).toLong()

            val topApps = JSONObject()
            val keys = appTrafficList.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val appInfo = appTrafficList.getJSONObject(key)
                val total = appInfo.getLong("total_bytes")
                if (total > meanBytes + sigmaBytes) {
                    topApps.put(key, appInfo)
                }
            }

            trafficJson.put("traffic_stats", JSONObject().apply {
                put("mean_bytes", meanBytes)
                put("sigma_bytes", sigmaBytes)
                put("total_apps", appTrafficList.length())
                put("top_apps_count", topApps.length())
            })

            trafficJson.put("top_apps", topApps)

        } catch (e: Exception) {
            Log.e(tag, "Error collecting traffic data: ${e.message}")
        }
        return lastTrafficData ?: trafficJson
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        val toRequest = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest, 100)
            appendToLog("Requesting permissions: ${toRequest.joinToString()}")
        } else {
            startLocationUpdates()
            getLastKnownLocation()
            appendToLog("All permissions granted")
        }
    }

    private fun getLastKnownLocation() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                currentLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (currentLocation != null) {
                    Log.d(tag, "Last known location: ${currentLocation!!.latitude}")
                }
            }
        } catch (_: Exception) {}
    }

    private fun startLocationUpdates() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, locationListener)
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5f, locationListener)
                Log.d(tag, "Location updates started")
            }
        } catch (_: Exception) {}
    }

    private fun collectLocationData(): JSONObject {
        val locationJson = JSONObject()
        if (currentLocation == null) getLastKnownLocation()
        currentLocation?.let {
            locationJson.put("Latitude", it.latitude)
            locationJson.put("Longitude", it.longitude)
            locationJson.put("Altitude", it.altitude)
            locationJson.put("Accuracy", it.accuracy)
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            locationJson.put("Current Time", df.format(Date()))
            Log.d(tag, "Location collected: ${it.latitude}, ${it.longitude}")
        } ?: run {
            lastLocationData?.let { return it }
            Log.d(tag, "No location data available")
        }
        return locationJson
    }

    private fun collectCellInfo(): JSONObject {
        val cellJson = JSONObject()
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val cellInfoList = tm.allCellInfo
                if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                    cellInfoList.forEachIndexed { idx, ci ->
                        when (ci) {
                            is CellInfoLte -> {
                                val lte = JSONObject().apply {
                                    val cellIdentity = ci.cellIdentity as CellIdentityLte
                                    val cellSignal = ci.cellSignalStrength as CellSignalStrengthLte
                                    put("CI", cellIdentity.ci)
                                    put("PCI", cellIdentity.pci)
                                    put("TAC", cellIdentity.tac)
                                    put("EARFCN", cellIdentity.earfcn)
                                    put("MCC", cellIdentity.mcc)
                                    put("MNC", cellIdentity.mnc)
                                    put("ASU Level", cellSignal.asuLevel)
                                    put("Dbm", cellSignal.dbm)
                                    put("Level", cellSignal.level)
                                }
                                cellJson.put("LTE_$idx", lte)
                                Log.d(tag, "LTE cell found: PCI=${(ci.cellIdentity as CellIdentityLte).pci}")
                            }
                            is CellInfoGsm -> {
                                val gsm = JSONObject().apply {
                                    val cellIdentity = ci.cellIdentity as CellIdentityGsm
                                    val cellSignal = ci.cellSignalStrength as CellSignalStrengthGsm
                                    put("LAC", cellIdentity.lac)
                                    put("CID", cellIdentity.cid)
                                    put("ARFCN", cellIdentity.arfcn)
                                    put("BSIC", cellIdentity.bsic)
                                    put("MCC", cellIdentity.mcc)
                                    put("MNC", cellIdentity.mnc)
                                    put("Dbm", cellSignal.dbm)
                                    put("ASU Level", cellSignal.asuLevel)
                                    put("Level", cellSignal.level)
                                }
                                cellJson.put("GSM_$idx", gsm)
                                Log.d(tag, "GSM cell found: CID=${(ci.cellIdentity as CellIdentityGsm).cid}")
                            }
                            is CellInfoCdma -> {
                                cellJson.put("CDMA_$idx", JSONObject().put("Type", "CDMA"))
                                Log.d(tag, "CDMA cell found")
                            }
                        }
                    }
                } else {
                    Log.d(tag, "No cell info available")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error collecting cell info: ${e.message}")
        }
        return lastCellData ?: cellJson
    }

    private fun collectAndDisplayData() {
        Thread {
            try {
                Log.d(tag, "Collecting data for display")
                val locData = collectLocationData()
                val cellData = collectCellInfo()
                val trafficData = collectTrafficData()

                lastLocationData = locData
                lastCellData = cellData
                lastTrafficData = trafficData

                val allData = JSONObject().apply {
                    put("location", locData)
                    put("telephony", cellData)
                    put("traffic", trafficData)
                    put("timestamp", System.currentTimeMillis())
                }
                lastFullData = allData

                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "network_data_${System.currentTimeMillis()}.json")
                file.writeText(allData.toString(2))

                handler.post {
                    tvDataDisplay.text = allData.toString(2)
                    appendToLog("Data collected and saved")
                }

                if (isConnected) {
                    sendFileToServer(file)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in collectAndDisplayData: ${e.message}")
                handler.post { appendToLog("Error: ${e.message}") }
            }
        }.start()
    }

    private fun forceSendLastFile() {
        Thread {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val files = downloadsDir.listFiles { f -> f.name.startsWith("network_data_") && f.name.endsWith(".json") } ?: return@Thread
            files.sortedByDescending { it.lastModified() }.firstOrNull()?.let {
                sendFileToServer(it)
                Log.d(tag, "Forced send last file: ${it.name}")
            }
        }.start()
    }

    private fun sendFileToServer(file: File) {
        try {
            Log.d(tag, "Sending file to server: ${file.name}")
            val socket = ZContext().createSocket(SocketType.REQ)
            socket.setReceiveTimeOut(5000)
            socket.connect("tcp://$serverIP:$port")
            socket.send(file.readText().toByteArray(ZMQ.CHARSET), 0)
            val response = socket.recv(0)
            socket.close()

            if (response != null) {
                val responseStr = String(response, ZMQ.CHARSET)
                Log.d(tag, "Server response: $responseStr")
                handler.post { appendToLog("Sent: ${file.name} - $responseStr") }
                isConnected = true
            } else {
                Log.e(tag, "No response from server")
                isConnected = false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending file: ${e.message}")
            isConnected = false
        }
    }

    private fun sendToServer(mes: String) {
        try {
            Log.d(tag, "Sending message to server: $mes")
            val socket = ZContext().createSocket(SocketType.REQ)
            socket.setReceiveTimeOut(5000)
            socket.connect("tcp://$serverIP:$port")
            socket.send(mes.toByteArray(ZMQ.CHARSET), 0)
            val response = socket.recv(0)
            socket.close()

            if (response != null) {
                val responseStr = String(response, ZMQ.CHARSET)
                Log.d(tag, "Server response: $responseStr")
                handler.post { appendToLog("Server: $responseStr") }
                isConnected = true
            } else {
                isConnected = false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending message: ${e.message}")
            isConnected = false
        }
    }

    private fun appendToLog(text: String) {
        handler.post {
            tvLog.append("\n${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} - $text")
            Log.d(tag, text)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reconnectHandler.removeCallbacks(reconnectRunnable)
        stopBackgroundSending()
        try {
            (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locationListener)
        } catch (_: Exception) {}
        Log.d(tag, "App destroyed")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                startLocationUpdates()
                getLastKnownLocation()
                appendToLog("Permissions granted")
            } else {
                appendToLog("Permissions denied: ${grantResults.joinToString()}")
            }
        }
    }

    private fun checkConnection() {
        serverIP = etServerIP.text.toString().trim().ifEmpty { "172.20.10.3" }
        val currentIP = serverIP
        appendToLog("Checking connection to $currentIP...")

        Thread {
            try {
                val socket = ZContext().createSocket(SocketType.REQ)
                socket.setReceiveTimeOut(3000)
                socket.connect("tcp://$currentIP:$port")
                socket.send("ping".toByteArray(ZMQ.CHARSET), 0)
                val response = socket.recv(0)
                socket.close()

                if (response != null) {
                    val responseStr = String(response, ZMQ.CHARSET)
                    isConnected = true
                    handler.post {
                        tvConnectionStatus.text = "Connected to $currentIP"
                        tvConnectionStatus.setTextColor(0xFF00FF00.toInt())
                        appendToLog("Connected to server: $responseStr")
                    }
                    forceSendLastFile()
                    startBackgroundSending()
                } else {
                    throw Exception("No response")
                }
            } catch (e: Exception) {
                isConnected = false
                handler.post {
                    tvConnectionStatus.text = "Connection error to $currentIP"
                    tvConnectionStatus.setTextColor(0xFFFF0000.toInt())
                    appendToLog("Connection failed: ${e.message}")
                }
            }
        }.start()
    }
}