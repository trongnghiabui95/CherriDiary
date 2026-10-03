package com.cherri.diary.android.live

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class CaptureConsentActivity : ComponentActivity() {
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val bounds = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("bounds", Rect::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra("bounds") as? Rect)
            ContextCompat.startForegroundService(this, Intent(this, ScreenshotCaptureService::class.java)
                .putExtra("resultCode", result.resultCode).putExtra("consent", result.data).putExtra("bounds", bounds))
        } else sendBroadcast(Intent(FloatingWindowService.CAPTURE_READY).setPackage(packageName)
            .putExtra("error", "Không có quyền chụp màn hình. Bạn có thể kiểm tra và nhập comment thủ công."))
        finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val request = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                else manager.createScreenCaptureIntent()
            consent.launch(request)
        }
    }
}
