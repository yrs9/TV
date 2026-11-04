package com.app.screenshare.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.json.JSONObject
import java.net.URI

class WebRTCClient(
    private val context: Context,
    private val roomId: String
) {
    private val TAG = "WebRTCClient"
    private lateinit var eglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var webSocket: org.java_websocket.client.WebSocketClient? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null

    init {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
    }

    fun getEglContext(): EglBase.Context = eglBase.coreContext

    fun initWebRTC() {
        eglBase = EglBase.create()
        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.coreContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.coreContext))
            .createPeerConnectionFactory()
    }

    fun connectSignalingServer(serverUrl: String) {
        val serverUri = URI(serverUrl)

        webSocket = object : org.java_websocket.client.WebSocketClient(serverUri) {
            override fun onOpen(handshakeData: org.java_websocket.handshakedata.ServerHandshake?) {
                Log.d(TAG, "Connected to signaling server")
                val json = JSONObject().apply {
                    put("type", "join")
                    put("roomId", roomId)
                    put("role", "sender")
                }
                send(json.toString())
            }

            override fun onMessage(message: String?) {
                val json = JSONObject(message)
                when (json.getString("type")) {
                    "peer-joined" -> {
                        createOffer()
                    }
                    "answer" -> {
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"))
                        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {}, sdp)
                    }
                    "candidate" -> {
                        val candidate = IceCandidate(
                            json.getString("id"),
                            json.getInt("label"),
                            json.getString("candidate")
                        )
                        peerConnection?.addIceCandidate(candidate)
                    }
                }
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        webSocket?.connect()
    }

    fun startStreaming(videoCapturer: VideoCapturer, surfaceTextureHelper: SurfaceTextureHelper) {
        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        ))

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val json = JSONObject().apply {
                        put("type", "candidate")
                        put("id", it.sdpMid)
                        put("label", it.sdpMLineIndex)
                        put("candidate", it.sdp)
                    }
                    webSocket?.send(json.toString())
                }
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        // ضبط دقة الشاشة (مثلا 720p بـ 30 إطار لضمان سلاسة فائقة)
        videoSource = peerConnectionFactory.createVideoSource(false)
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
        videoCapturer.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
        peerConnection?.addTrack(localVideoTrack, listOf("stream"))
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {}, desc)
                val json = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc?.description)
                }
                webSocket?.send(json.toString())
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun close() {
        try {
            webSocket?.close()
            peerConnection?.dispose()
            localVideoTrack?.dispose()
            videoSource?.dispose()
            PeerConnectionFactory.stopInternalTracingEngine()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
