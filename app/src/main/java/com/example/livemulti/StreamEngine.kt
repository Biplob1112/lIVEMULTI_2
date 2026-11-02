package com.example.livemulti

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.library.multiple.MultiStream
import com.pedro.library.util.sources.MultiType   // NOTE: if this import fails, see README ("If it doesn't compile")
import com.pedro.library.view.OpenGlView

/**
 * All streaming logic lives here. One camera encode is sent to several RTMP servers.
 * If RootEncoder changes its API in a newer version, only this file needs fixing.
 */
class StreamEngine(
    private val context: Context,
    private val view: OpenGlView,
    checker: ConnectChecker
) {
    private val stream = MultiStream(context, checker)
    private var textFilter: TextObjectFilterRender? = null
    private var logoFilter: ImageObjectFilterRender? = null

    val isStreaming: Boolean get() = stream.isStreaming

    fun startPreview() {
        if (stream.isOnPreview) return
        val rotation = CameraHelper.getCameraOrientation(context)
        // 720p, 30fps, 2.5 Mbps. Remember: each destination uploads this separately.
        val videoOk = stream.prepareVideo(1280, 720, 30, 2_500_000, 2, rotation)
        val audioOk = stream.prepareAudio(44100, true, 128 * 1000)
        if (videoOk && audioOk) stream.startPreview(view)
    }

    fun stopPreview() {
        if (stream.isOnPreview) stream.stopPreview()
    }

    fun switchCamera() = stream.switchCamera()

    /** urls: full RTMP(S) URLs including the stream key. */
    fun goLive(urls: List<String>) {
        if (stream.isStreaming) return
        urls.forEach { stream.startStream(MultiType.RTMP, it) }
    }

    fun stopLive() {
        if (stream.isStreaming) stream.stopStream()
    }

    fun setOverlayText(text: String) {
        val gl = stream.getGlInterface()
        if (text.isBlank()) {
            textFilter?.let { gl.removeFilter(it) }
            textFilter = null
            return
        }
        if (textFilter == null) {
            textFilter = TextObjectFilterRender().also { gl.addFilter(it) }
        }
        textFilter?.apply {
            setText(text, 28f, Color.WHITE)
            setScale(40f, 10f)
            setPosition(TranslateTo.BOTTOM)
        }
    }

    fun setLogo(bitmap: Bitmap) {
        val gl = stream.getGlInterface()
        if (logoFilter == null) {
            logoFilter = ImageObjectFilterRender().also { gl.addFilter(it) }
        }
        logoFilter?.apply {
            setImage(bitmap)
            setScale(20f, 12f)
            setPosition(TranslateTo.TOP_RIGHT)
        }
    }

    fun release() {
        stopLive()
        stopPreview()
    }
}
