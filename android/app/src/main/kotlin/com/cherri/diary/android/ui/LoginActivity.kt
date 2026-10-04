package com.cherri.diary.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cherri.diary.android.R
import com.cherri.diary.android.cherri
import com.cherri.diary.android.data.LoginRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CherriDiary_Login)
        super.onCreate(savedInstanceState)
        if (cherri.tokens.token() != null) { openMain(); return }
        setContentView(R.layout.activity_login)
        val url = findViewById<TextInputEditText>(R.id.login_server)
        val serverLayout = findViewById<TextInputLayout>(R.id.login_server_layout)
        val serverToggle = findViewById<MaterialButton>(R.id.login_server_toggle)
        val username = findViewById<TextInputEditText>(R.id.login_username)
        val password = findViewById<TextInputEditText>(R.id.login_password)
        val message = findViewById<TextView>(R.id.login_message)
        val progress = findViewById<ProgressBar>(R.id.login_progress)
        val submit = findViewById<MaterialButton>(R.id.login_submit)
        val remember = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.login_remember)
        remember.isChecked = cherri.tokens.rememberLogin()
        findViewById<MaterialButton>(R.id.login_forgot).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.login_forgot)
                .setMessage(R.string.login_recovery).setPositiveButton("Đóng", null).show()
        }
        url.setText(cherri.tokens.baseUrl())
        serverToggle.setOnClickListener {
            serverLayout.visibility = if (serverLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        submit.setOnClickListener {
            if (username.text.isNullOrBlank() || password.text.isNullOrEmpty()) {
                message.setText(R.string.login_required); message.visibility = View.VISIBLE
                return@setOnClickListener
            }
            submit.isEnabled = false
            val rememberSelected = remember.isChecked
            remember.isEnabled = false; serverToggle.isEnabled = false
            submit.setText(R.string.login_busy)
            progress.visibility = View.VISIBLE
            message.visibility = View.GONE
            username.isEnabled = false; password.isEnabled = false; url.isEnabled = false
            lifecycleScope.launch {
                try {
                    cherri.tokens.setBaseUrl(url.text.toString())
                    val result = cherri.api.service().login(LoginRequest(username.text.toString().trim(), password.text.toString()))
                    cherri.tokens.save(result.accessToken, result.user.fullName, rememberSelected)
                    password.text?.clear()
                    openMain()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    message.text = errorText(e); message.visibility = View.VISIBLE
                } finally {
                    submit.isEnabled = true; submit.setText(R.string.login_submit)
                    remember.isEnabled = true; serverToggle.isEnabled = true
                    progress.visibility = View.GONE
                    username.isEnabled = true; password.isEnabled = true; url.isEnabled = true
                }
            }
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (submit.isEnabled) submit.performClick()
                true
            } else false
        }
    }
    private fun openMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
}
