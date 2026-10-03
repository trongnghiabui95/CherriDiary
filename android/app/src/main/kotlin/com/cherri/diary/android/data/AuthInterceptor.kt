package com.cherri.diary.android.data

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokens: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        if (!chain.request().url.encodedPath.endsWith("/auth/login")) tokens.token()?.let {
            builder.header("Authorization", "Bearer $it")
        }
        val response = chain.proceed(builder.build())
        if (response.code == 401) tokens.clear()
        return response
    }
}
