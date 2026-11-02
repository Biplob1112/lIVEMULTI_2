package com.example.livemulti

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView

class MainActivity : AppCompatActivity(), ConnectChecker {

    private lateinit var engine: StreamEngine
    private lateinit var etYoutube: EditText
    private lateinit var etFacebook: EditText
    private lateinit var etText: EditText
    private lateinit var btnLive: Button
    private lateinit var tvStatus: TextView

    private val prefs by lazy { getSharedPreferences("livemulti", Context.MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) engine.startPreview()
        else toast("Camera and microphone permission are required")
    }

    private val logoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)?.let { engine.setLogo(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val glView = findViewById<OpenGlView>(R.id.openGlView)
        etYoutube = findViewById(R.id.etYoutubeKey)
        etFacebook = findViewById(R.id.etFacebookKey)
        etText = findViewById(R.id.etOverlayText)
        btnLive = findViewById(R.id.btnLive)
        tvStatus = findViewById(R.id.tvStatus)

        // Keys are saved on this phone only (plain storage: fine for testing; keep your phone locked).
        etYoutube.setText(prefs.getString("yt", ""))
        etFacebook.setText(prefs.getString("fb", ""))
        etText.setText(prefs.getString("text", ""))

        engine = StreamEngine(this, glView, this)

        findViewById<Button>(R.id.btnApplyText).setOnClickListener {
            prefs.edit().putString("text", etText.text.toString()).apply()
            engine.setOverlayText(etText.text.toString())
        }
        findViewById<Button>(R.id.btnLogo).setOnClickListener { logoPicker.launch("image/*") }
        findViewById<Button>(R.id.btnSwitch).setOnClickListener { engine.switchCamera() }
        btnLive.setOnClickListener { toggleLive() }

        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
    }

    private fun toggleLive() {
        if (engine.isStreaming) {
            engine.stopLive()
            btnLive.text = "GO LIVE"
            status("Stopped")
            return
        }
        val yt = etYoutube.text.toString().trim()
        val fb = etFacebook.text.toString().trim()
        if (yt.isEmpty() && fb.isEmpty()) {
            toast("Enter at least one stream key")
            return
        }
        prefs.edit().putString("yt", yt).putString("fb", fb).apply()

        val urls = mutableListOf<String>()
        if (yt.isNotEmpty()) urls += "rtmp://a.rtmp.youtube.com/live2/$yt"
        if (fb.isNotEmpty()) urls += "rtmps://live-api-s.facebook.com:443/rtmp/$fb"

        engine.goLive(urls)
        btnLive.text = "STOP"
        status("Connecting to ${urls.size} destination(s)...")
    }

    // ---- ConnectChecker callbacks (called from background threads) ----
    override fun onConnectionStarted(url: String) = status("Connecting...")
    override fun onConnectionSuccess() = status("LIVE")
    override fun onConnectionFailed(reason: String) = status("Connection failed: $reason")
    override fun onNewBitrate(bitrate: Long) {}
    override fun onDisconnect() = status("Disconnected")
    override fun onAuthError() = status("Auth error (check stream key)")
    override fun onAuthSuccess() {}

    private fun status(msg: String) = runOnUiThread { tvStatus.text = msg }
    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}
