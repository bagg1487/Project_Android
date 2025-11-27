package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileOutputStream

class location : AppCompatActivity(), LocationListener {
    companion object {
        private const val PERMISSION_REQUEST_ALL = 100
    }

    private lateinit var update_button: Button
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvTime: TextView
    private lateinit var locationManager: LocationManager
    val LOG_TAG: String = "LOCATION_ACTIVITY"
    private var hasAllPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_location)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvTime = findViewById(R.id.tvTime)
        update_button = findViewById(R.id.update_button)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        update_button.setOnClickListener {
            checkAndRequestPermissions()
            updateCurrentLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val hasPermissions = permissions.all { permission ->
            ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            hasAllPermissions = true
            updateCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_ALL)
        }
    }

    private fun saveLocationToJson(location: Location) {
            val jsonData = """
        {
            "latitude": ${location.latitude},
            "longitude": ${location.longitude},
            "altitude": ${location.altitude},
            "time": "${getCurrentTime()}",
            "provider": "${location.provider}"
        }
        """.trimIndent()


            val fileName = "location_${System.currentTimeMillis()}.json"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileOutputStream(file).use { fos ->
                fos.write(jsonData.toByteArray())
            }
    }

    private fun updateCurrentLocation() {
        try {
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastLocation != null) {
                tvLatitude.text = "Широта: ${lastLocation.latitude}"
                tvLongitude.text = "Долгота: ${lastLocation.longitude}"
                tvAltitude.text = "Высота: ${lastLocation.altitude}"
                tvTime.text = "Время: ${getCurrentTime()}"

                saveLocationToJson(lastLocation)
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, this)

        } catch (e: SecurityException) {
            Log.e(LOG_TAG, "Нет прав для получения местоположения")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_ALL) {
            hasAllPermissions = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (hasAllPermissions) {
                Toast.makeText(applicationContext, "Все разрешения получены", Toast.LENGTH_SHORT).show()
                updateCurrentLocation()
            } else {
                Toast.makeText(applicationContext, "Нужны все разрешения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getCurrentTime(): String {
        val timeZone = TimeZone.getTimeZone("Asia/Novosibirsk")
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.timeZone = timeZone
        return sdf.format(Date())
    }

    override fun onLocationChanged(location: Location) {
        tvLatitude.text = "Широта: ${location.latitude}"
        tvLongitude.text = "Долгота: ${location.longitude}"
        tvAltitude.text = "Высота: ${location.altitude}"
        tvTime.text = "Время: ${getCurrentTime()}"

        saveLocationToJson(location)
    }


    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}