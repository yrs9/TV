package com.app.screenshare

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.app.screenshare.services.MediaProjectionService

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var etRoomId: EditText
    private lateinit var btnStartStop: Button
    private var isStreaming = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val roomId = etRoomId.text.toString().trim()
            if (roomId.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال رقم الغرفة Room ID", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            // بدء الخدمة الخلفية لالتقاط الشاشة وبثها
            val serviceIntent = Intent(this, MediaProjectionService::class.java).apply {
                putExtra("code", result.resultCode}
                putExtra("data", result.data)
                putExtra("roomId", roomId)
            }
            startForegroundService(serviceIntent)
            isStreaming = true
            btnStartStop.text = "إيقاف البث"
            Toast.makeText(this, "جاري بدء البث...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "تم رفض إذن التقاط الشاشة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        etRoomId = findViewById(R.id.etRoomId)
        btnStartStop = findViewById(R.id.btnStartStop)

        btnStartStop.setOnClickListener {
            if (!isStreaming) {
                // طلب إذن التقاط الشاشة من نظام أندرويد
                val intent = projectionManager.createScreenCaptureIntent()
                screenCaptureLauncher.launch(intent)
            } else {
                // إيقاف البث
                val serviceIntent = Intent(this, MediaProjectionService::class.java)
                stopService(serviceIntent)
                isStreaming = false
                btnStartStop.text = "بدء البث"
                Toast.makeText(this, "تم إيقاف البث", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
