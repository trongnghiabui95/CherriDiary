package com.cherri.diary.android.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.cherri.diary.android.R
import com.cherri.diary.android.cherri
import com.cherri.diary.android.data.LoginRequest
import com.cherri.diary.android.ui.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CherriDiary_Login)
        super.onCreate(savedInstanceState)
        if (cherri.tokens.token() != null) { openMain(); return }
        setContent {
            CherriTheme {
                CherriLoginScreen(cherri.tokens.baseUrl(), cherri.tokens.rememberLogin(), busy, error,
                    onLogin = { server, username, password, remember ->
                        if (!busy) {
                            busy = true; error = null
                            lifecycleScope.launch {
                                try {
                                    cherri.tokens.setBaseUrl(server)
                                    val result = cherri.api.service().login(LoginRequest(username, password))
                                    cherri.tokens.save(result.accessToken, result.user.fullName, remember)
                                    openMain()
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = errorText(e) }
                                finally { busy = false }
                            }
                        }
                    }, onForgotPassword = {
                        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.login_forgot)
                            .setMessage(R.string.login_recovery).setPositiveButton("Đóng", null).show()
                    })
            }
        }
    }
    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
}
