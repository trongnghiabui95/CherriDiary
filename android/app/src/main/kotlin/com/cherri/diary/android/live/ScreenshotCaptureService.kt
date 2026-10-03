package com.cherri.diary.android.live

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.core.graphics.createBitmap
import java.io.File
import java.util.UUID

class ScreenshotCaptureService : Service() {
    companion object {
        const val CAPTURE = "com.cherri.diary.CAPTURE_FRAME"
        @Volatile var isReady = false
            private set
    }
    private val thread = HandlerThread("CherriScreenshot")
    private lateinit var worker: Handler
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var pending = false
    private var bounds: Rect? = null
    private var stopping = false
    private var timeout: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(101, liveNotification("Phiên chụp comment đang bật · Tắt khi kết thúc live"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        thread.start(); worker = Handler(thread.looper)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        val requestedBounds = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("bounds", Rect::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra("bounds") as? Rect)
        if (intent.action == CAPTURE) {
            worker.postDelayed({
                if (projection == null || !isReady) { result(null, "Phiên chụp đã dừng. Bấm lại để cấp quyền."); stopSelf() }
                else requestFrame(requestedBounds)
            }, 400)
            return START_NOT_STICKY
        }
        // Consume a newly consented token only once. Following taps reuse the display, not the token.
        if (projection != null) return START_NOT_STICKY
        val consent = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("consent", Intent::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra("consent") as? Intent)
        if (consent == null) { result(null, "Không có quyền chụp màn hình"); stopSelf(); return START_NOT_STICKY }
        worker.postDelayed({
            try {
                projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent.getIntExtra("resultCode", 0), consent)
                projection!!.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        isReady = false
                        if (!stopping) { if (pending) result(null, "Phiên chụp màn hình đã dừng"); stopSelf() }
                    }
                    override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
                        if (display != null && (width != newWidth || height != newHeight)) resize(newWidth, newHeight)
                    }
                }, worker)
                val window = getSystemService(WindowManager::class.java)
                val screen = if (Build.VERSION.SDK_INT >= 30) window.maximumWindowMetrics.bounds
                    else Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                attachReader(screen.width(), screen.height())
                display = projection!!.createVirtualDisplay("CherriProof", width, height, resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, worker)
                isReady = true
                requestFrame(requestedBounds)
            } catch (_: Exception) { result(null, "Không chụp được màn hình. Cấp lại quyền và thử lại."); stopSelf() }
        }, 650)
        return START_NOT_STICKY
    }
    private fun attachReader(newWidth: Int, newHeight: Int) {
        width = newWidth; height = newHeight
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ source ->
                if (source !== reader) return@setOnImageAvailableListener
                val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try {
                    // Discard frames unless staff explicitly requested a screenshot.
                    if (pending && !stopping) {
                        val plane = image.planes[0]
                        val paddedWidth = width + (plane.rowStride - plane.pixelStride * width) / plane.pixelStride
                        val padded = createBitmap(paddedWidth, height)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val crop = bounds?.let(::Rect)?.takeIf { it.intersect(0, 0, width, height) && it.width() >= 16 && it.height() >= 16 }
                            ?: Rect(0, 0, width, height)
                        val bitmap = Bitmap.createBitmap(padded, crop.left, crop.top, crop.width(), crop.height())
                        val directory = File(cacheDir, "proofs").apply { mkdirs() }
                        val file = File(directory, "${UUID.randomUUID()}.png")
                        try {
                            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            result(file, null)
                        } catch (e: Exception) { file.delete(); throw e }
                        finally { if (bitmap != padded) bitmap.recycle(); padded.recycle() }
                    }
                } catch (_: Exception) { result(null, "Không lưu được ảnh màn hình") }
                finally { image.close() }
            }, worker)
        }
    }
    private fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        val oldReader = reader
        attachReader(newWidth, newHeight)
        display?.resize(width, height, resources.displayMetrics.densityDpi)
        display?.surface = reader!!.surface
        oldReader?.close()
    }
    private fun requestFrame(requestedBounds: Rect?) {
        bounds = requestedBounds
        pending = true
        timeout?.let(worker::removeCallbacks)
        timeout = Runnable { if (pending) result(null, "Không nhận được ảnh. Màn hình TikTok có thể bị bảo vệ.") }.also { worker.postDelayed(it, 8000) }
    }
    private fun result(file: File?, error: String?) {
        pending = false
        timeout?.let(worker::removeCallbacks)
        sendBroadcast(Intent(FloatingWindowService.CAPTURE_READY).setPackage(packageName)
            .putExtra("proofPath", file?.absolutePath).putExtra("error", error))
    }
    override fun onDestroy() {
        isReady = false; stopping = true
        worker.post {
            worker.removeCallbacksAndMessages(null)
            display?.release(); reader?.close(); projection?.stop(); thread.quitSafely()
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
