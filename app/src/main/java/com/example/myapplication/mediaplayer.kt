package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import android.os.Handler
import android.widget.Button

class mediaplayer : AppCompatActivity() {
    private lateinit var sbMySeekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var currentSongText: TextView
    private var musicFiles = mutableListOf<File>()
    private lateinit var musicList: ListView
    private lateinit var mediaPlayer: MediaPlayer
    private val handler = Handler()
    private val isPlaying = false
    private lateinit var playButton: Button
    private lateinit var rewindBackButton: Button
    private lateinit var rewindForwardButton: Button
    private lateinit var stopButton: Button
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var audioManager: AudioManager
    private fun loadMusicFiles() {
        val downloadDirection = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if(downloadDirection.exists()){
            downloadDirection.listFiles()?.forEach{ file ->
                if(file.name.endsWith(".mp3")){
                    musicFiles.add(file)
                }
            }
        }
    }

    private fun showMusicList(){
        val adapter = ArrayAdapter(this, R.layout.mediaplayer_list, musicFiles.map { it.nameWithoutExtension })
        musicList.adapter = adapter
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicFiles()
                showMusicList()
            }
        }
    }
    private fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
    private fun updateSeekBar(){
        handler.postDelayed({
            if(mediaPlayer.isPlaying){
                val currentPos = mediaPlayer.currentPosition
                sbMySeekBar.progress = currentPos
                currentTimeText.text = formatTime(currentPos)
                updateSeekBar()
            }
        }, 1000)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mediaplayer)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 123)

        sbMySeekBar = findViewById(R.id.seekBar)
        currentTimeText = findViewById(R.id.currentTimeText)
        musicList = findViewById(R.id.musicList)
        currentSongText = findViewById(R.id.currentSongText)
        totalTimeText = findViewById(R.id.totalTimeText)
        playButton = findViewById(R.id.pauseplayButton)
        rewindBackButton = findViewById(R.id.rewindBackButton)
        rewindForwardButton = findViewById(R.id.rewindForwardButton)
        mediaPlayer = MediaPlayer()
        stopButton = findViewById(R.id.stopButton)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)

        musicList.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            val selectedFile = musicFiles[position]
            currentSongText.text = selectedFile.nameWithoutExtension
            mediaPlayer.reset()
            mediaPlayer.setDataSource(selectedFile.absolutePath)
            mediaPlayer.prepare()
            val duration = mediaPlayer.duration
            sbMySeekBar.max = duration
            totalTimeText.text = formatTime(duration)
            mediaPlayer.start()
            playButton.text = "⏸️"
            updateSeekBar()
            }

        playButton.setOnClickListener {
            if(mediaPlayer.isPlaying){
                mediaPlayer.pause()
                playButton.text = "▶️"
            }
            else {
                mediaPlayer.start()
                playButton.text = "⏸️"
                updateSeekBar()
            }
        }

        sbMySeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if(fromUser){
                    currentTimeText.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                handler.removeCallbacksAndMessages(null)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mediaPlayer.seekTo(seekBar.progress)
                updateSeekBar()
                mediaPlayer.start()
                playButton.text = "⏸️"
            }
        })

        rewindBackButton.setOnClickListener {
            val newPosition = mediaPlayer.currentPosition - 5000
            if(newPosition < 0){
                mediaPlayer.seekTo(0)
            }
            else{
                mediaPlayer.seekTo(newPosition)
            }
        }

        rewindForwardButton.setOnClickListener {
            val newPosition = mediaPlayer.currentPosition + 5000
            if(newPosition > mediaPlayer.duration){
                mediaPlayer.seekTo(mediaPlayer.duration)
            }
            else{
                mediaPlayer.seekTo(newPosition)
            }
        }

        stopButton.setOnClickListener {
            mediaPlayer.pause()
            mediaPlayer.seekTo(0)
            playButton.text = "▶️"
            sbMySeekBar.progress = 0
            currentTimeText.text = "0:00"

        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

    }

    override fun onResume() {
        super.onResume()
        if (::audioManager.isInitialized) {
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }
}