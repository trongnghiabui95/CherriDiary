package com.cherri.diary.android.ui

import android.content.Context
import android.text.InputType
import android.widget.*
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

fun Context.column() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(24, 20, 24, 24)
}
fun LinearLayout.label(value: String) = TextView(context).apply { text = value; textSize = 16f; setPadding(0, 12, 0, 8) }.also(::addView)
fun LinearLayout.field(hint: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT) = EditText(context).apply {
    this.hint = hint; setText(value); inputType = type
}.also(::addView)
fun LinearLayout.button(value: String, action: () -> Unit) = Button(context).apply { text = value; setOnClickListener { action() } }.also(::addView)
fun LinearLayout.choice(values: List<String>) = Spinner(context).apply {
    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
}.also(::addView)
fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
fun money(value: BigDecimal): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫"
fun errorText(e: Throwable): String = if (e is HttpException) {
    runCatching { JsonParser.parseString(e.response()?.errorBody()?.string()).asJsonObject["message"].asString }
        .getOrDefault("Lỗi HTTP ${e.code()}")
} else e.message ?: "Không kết nối được backend"
