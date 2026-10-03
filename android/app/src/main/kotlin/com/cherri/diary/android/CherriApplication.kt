package com.cherri.diary.android

import android.app.Application
import android.content.Context
import com.cherri.diary.android.data.ApiClient
import com.cherri.diary.android.data.TokenManager
import java.io.File

class CherriApplication : Application() {
    val tokens by lazy { TokenManager(this) }
    val api by lazy { ApiClient(tokens) }
    override fun onCreate() {
        super.onCreate()
        File(cacheDir, "proofs").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
    }
}
val Context.cherri: CherriApplication get() = applicationContext as CherriApplication
