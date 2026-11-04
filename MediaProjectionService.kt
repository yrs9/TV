package com.app.screenshare.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.app.screenshare.webrtc.WebRTCClient
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer

class MediaProjectionService : Service() {

    private lateinit var webRTCClient: WebRTCClient
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: VideoCapturer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("code", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("data")
        val roomId = intent?.getStringExtra("roomId") ?: "default_room"

        createNotificationChannel()
        val notification = Notification.Builder(this, "screen_share_channel")
            .setContentTitle("بث الشاشة قيد التشغيل")
            .setContentText("جاري بث شاشة الهاتف مباشرة...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)

        // إعداد WebRTC والاتصال بخادم الإشارة (استبدل الآي بي بآي بي حاسوبك المحلي على الشبكة)
        webRTCClient = WebRTCClient(applicationContext, roomId)
        webRTCClient.initWebRTC()
        webRTCClient.connectSignalingServer("ws://192.168.1.100:8080")

        // بدء التقاط الشاشة عبر MediaProjection
        if (resultCode != 0 && data != null) {
            startScreenCapture(resultCode, data)
        }

        return START_NOT_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", webRTCClient.getEglContext())
        videoCapturer = createScreenCapturer(resultCode, data)
        
        webRTCClient.startStreaming(videoCapturer!!, surfaceTextureHelper!!)
    }

    private fun createScreenCapturer(resultCode: Int, data: Intent): VideoCapturer {
        return ScreenCapturerAndroid(data, object : org.webrtc.MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopSelf()
            }
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "screen_share_channel",
            "Screen Share Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        webRTCClient.close()
    }
}
