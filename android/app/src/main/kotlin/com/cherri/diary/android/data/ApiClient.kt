package com.cherri.diary.android.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(private val tokens: TokenManager) {
    val gson = Gson()
    val http = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokens))
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private var cachedUrl: String? = null
    private var cachedService: ApiService? = null
    @Synchronized
    fun service(): ApiService {
        val url = tokens.baseUrl()
        if (url != cachedUrl) {
            cachedService = Retrofit.Builder().baseUrl(url).client(http)
                .addConverterFactory(GsonConverterFactory.create(gson)).build().create(ApiService::class.java)
            cachedUrl = url
        }
        return cachedService!!
    }
}
