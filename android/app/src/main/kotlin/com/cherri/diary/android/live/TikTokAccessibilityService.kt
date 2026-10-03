package com.cherri.diary.android.live

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

data class SelectedComment(val text: String, val bounds: Rect?, val timestamp: Long)

class TikTokAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var selected: SelectedComment? = null
        fun selectedComment(): SelectedComment? = selected?.takeIf { SystemClock.elapsedRealtime() - it.timestamp <= 30_000 }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() !in setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")) return
        val source = event.source ?: return
        // Use only the selected node, not a scan of the whole live UI.
        val text = (listOfNotNull(source.text?.toString(), source.contentDescription?.toString()) + event.text.map { it.toString() })
            .distinct().joinToString(" ").trim().take(4000)
        if (text.isBlank()) return
        val bounds = Rect().also(source::getBoundsInScreen).takeUnless(Rect::isEmpty)
        selected = SelectedComment(text, bounds, SystemClock.elapsedRealtime())
    }
    override fun onInterrupt() { selected = null }
    override fun onDestroy() { selected = null; super.onDestroy() }
}
