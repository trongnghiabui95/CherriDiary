package com.cherri.diary.android.live

import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatTextView
import com.cherri.diary.android.R
import com.cherri.diary.android.cherri
import com.cherri.diary.android.ui.QuickOrderBottomSheet
import com.cherri.diary.android.ui.toast
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs

class FloatingWindowService : Service() {
    companion object {
        const val CAPTURE_READY = "com.cherri.diary.CAPTURE_READY"
        const val STOP = "com.cherri.diary.STOP_OVERLAY"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var bubble: TextView? = null
    private var capturing = false
    private var receiverRegistered = false
    private var selectedText = ""
    private var activeSheet: QuickOrderBottomSheet? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != CAPTURE_READY) return
            capturing = false
            handler.removeCallbacksAndMessages(null)
            bubble?.visibility = View.VISIBLE
            val file = intent.getStringExtra("proofPath")?.let(::File)?.takeIf {
                it.isFile && it.canonicalFile.parentFile == File(cacheDir, "proofs").canonicalFile
            }
            intent.getStringExtra("error")?.let { toast(it) }
            openSheet(file)
        }
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 34) startForeground(100, liveNotification("Nút chốt đơn đang bật"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(100, liveNotification("Nút chốt đơn đang bật"))
        windows = getSystemService(WindowManager::class.java)
        ContextCompat.registerReceiver(this, receiver, IntentFilter(CAPTURE_READY), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !Settings.canDrawOverlays(this) || cherri.tokens.token() == null) { stopSelf(); return START_NOT_STICKY }
        if (bubble == null) attachBubble()
        return START_NOT_STICKY
    }
    private fun attachBubble() {
        val density = resources.displayMetrics.density
        val size = (88 * density).toInt()
        val layout = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = 20; y = 220
        }
        val button = object : AppCompatTextView(ContextThemeWrapper(this, R.style.Theme_CherriDiary)) {
            override fun performClick(): Boolean = super.performClick()
        }.apply {
            text = "Chốt Đơn\nLive"; contentDescription = "Chốt Đơn Live"; gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE); textSize = 14f
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFB22D54.toInt()) }
            setOnClickListener { capture() }
        }
        var downX = 0f; var downY = 0f; var originalX = 0; var originalY = 0
        button.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originalX = layout.x; originalY = layout.y; true }
                MotionEvent.ACTION_MOVE -> {
                    layout.x = (originalX + event.rawX - downX).toInt().coerceIn(0, (resources.displayMetrics.widthPixels - size).coerceAtLeast(0))
                    layout.y = (originalY + event.rawY - downY).toInt().coerceIn(0, (resources.displayMetrics.heightPixels - size).coerceAtLeast(0))
                    windows.updateViewLayout(view, layout); true
                }
                MotionEvent.ACTION_UP -> { if (abs(event.rawX - downX) < 12 && abs(event.rawY - downY) < 12) view.performClick(); true }
                else -> false
            }
        }
        try { windows.addView(button, layout); bubble = button }
        catch (_: RuntimeException) { toast("Không hiển thị được nút nổi; kiểm tra quyền"); stopSelf() }
    }
    private fun capture() {
        if (capturing) return
        if (cherri.tokens.token() == null) { toast("Đăng nhập lại trong Cherri Diary"); stopSelf(); return }
        activeSheet?.dismiss()
        val selected = TikTokAccessibilityService.selectedComment()
        selectedText = selected?.text ?: ""
        capturing = true
        // Keep overlay visible until the activity launches (Android 15 background launch requirement).
        try {
            if (ScreenshotCaptureService.isReady) startService(Intent(this, ScreenshotCaptureService::class.java)
                .setAction(ScreenshotCaptureService.CAPTURE).putExtra("bounds", selected?.bounds))
            else startActivity(Intent(this, CaptureConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("bounds", selected?.bounds))
            bubble?.visibility = View.INVISIBLE
            handler.postDelayed({
                if (capturing) { capturing = false; bubble?.visibility = View.VISIBLE; toast("Chụp màn hình chưa hoàn tất. Bấm lại nút nổi để thử lại.") }
            }, 120_000)
        } catch (_: RuntimeException) { capturing = false; bubble?.visibility = View.VISIBLE; toast("Mở Cherri Diary để cấp quyền chụp màn hình") }
    }
    private fun openSheet(file: File?) {
        activeSheet?.dismiss()
        val themed = ContextThemeWrapper(this, R.style.Theme_CherriDiary)
        activeSheet = QuickOrderBottomSheet(themed, scope, file, selectedText, overlay = true).also { it.show() }
    }
    override fun onDestroy() {
        stopService(Intent(this, ScreenshotCaptureService::class.java))
        activeSheet?.dismiss(); handler.removeCallbacksAndMessages(null); scope.cancel()
        if (receiverRegistered) unregisterReceiver(receiver)
        bubble?.let { runCatching { windows.removeView(it) } }; bubble = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
