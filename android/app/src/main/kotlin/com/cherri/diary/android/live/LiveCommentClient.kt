package com.cherri.diary.android.live

import com.cherri.diary.android.data.*
import okhttp3.*

class LiveCommentClient(private val api: ApiClient, private val tokens: TokenManager, private val sessionId: Long,
    private val onComment: (LiveComment) -> Unit, private val onError: (String) -> Unit) {
    private var socket: WebSocket? = null
    fun connect() {
        val url = tokens.baseUrl().trimEnd('/') + "/live-comments?liveSessionId=$sessionId"
        socket = api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { api.gson.fromJson(text, LiveComment::class.java) }.onSuccess(onComment)
                    .onFailure { onError("Comment gateway không hợp lệ") }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { onError("Mất kết nối comment gateway. Bấm kết nối để thử lại.") }
        })
    }
    fun close() { socket?.close(1000, "Leaving live session"); socket = null }
}
