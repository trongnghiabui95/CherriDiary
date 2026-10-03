package com.cherri.diary.android.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cherri.diary.android.cherri
import com.cherri.diary.android.data.LoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (cherri.tokens.token() != null) { openMain(); return }
        val body = column()
        setContentView(ScrollView(this).apply { addView(body) })
        body.label("Cherri Diary · Đăng nhập").textSize = 26f
        val url = body.field("Backend URL (kết thúc /api/v1/)", cherri.tokens.baseUrl(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val username = body.field("Tài khoản")
        val password = body.field("Mật khẩu", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val message = body.label("")
        lateinit var submit: android.widget.Button
        submit = body.button("Đăng nhập") {
            submit.isEnabled = false
            lifecycleScope.launch {
                try {
                    cherri.tokens.setBaseUrl(url.text.toString())
                    val result = cherri.api.service().login(LoginRequest(username.text.toString().trim(), password.text.toString()))
                    cherri.tokens.save(result.accessToken, result.user.fullName)
                    password.text.clear()
                    openMain()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message.text = errorText(e) }
                finally { submit.isEnabled = true }
            }
        }
    }
    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
}
