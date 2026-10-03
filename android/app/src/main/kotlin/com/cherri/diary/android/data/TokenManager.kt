@file:Suppress("DEPRECATION")

package com.cherri.diary.android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import com.cherri.diary.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Suppress("DEPRECATION")
class TokenManager(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(context, "cherri_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    fun token(): String? = preferences.getString("token", null)
    fun save(token: String, name: String) { preferences.edit { putString("token", token); putString("name", name) } }
    fun clear() { preferences.edit { remove("token"); remove("name"); remove("live_session_id") } }
    fun name(): String = preferences.getString("name", "Nhân viên")!!
    fun baseUrl(): String = preferences.getString("base_url", BuildConfig.DEFAULT_API_URL)!!
    fun setBaseUrl(value: String) {
        val url = (value.trim().trimEnd('/') + "/").toHttpUrlOrNull() ?: error("URL backend không hợp lệ")
        require(url.isHttps || (BuildConfig.DEBUG && url.scheme == "http")) { "Bản release yêu cầu HTTPS" }
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "URL không được chứa tài khoản, query hoặc fragment" }
        if (baseUrl() != url.toString()) clear()
        preferences.edit { putString("base_url", url.toString()) }
    }
    fun liveSessionId(): Long? = preferences.getLong("live_session_id", -1).takeIf { it > 0 }
    fun setLiveSessionId(id: Long?) { preferences.edit { putLong("live_session_id", id ?: -1) } }
}
