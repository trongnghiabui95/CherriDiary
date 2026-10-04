package com.cherri.diary.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

internal fun LinearLayout.featureCard(title: String, subtitle: String): LinearLayout {
    val card = MaterialCardView(context).apply {
        radius = context.dp(20).toFloat()
        cardElevation = context.dp(2).toFloat()
        setCardBackgroundColor(0xFFFFF5F5.toInt())
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(16) }
    }
    val content = context.column().apply { setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(16)) }
    content.label(title).apply { textSize = 20f; setTextColor(0xFF212121.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD) }
    content.label(subtitle).apply { textSize = 13f; setTextColor(0xFF666666.toInt()) }
    card.addView(content); addView(card)
    return content
}

internal fun LinearLayout.actionRow() = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    layoutParams = LinearLayout.LayoutParams(-1, -2)
}.also(::addView)

internal fun LinearLayout.action(text: String, style: String = "primary", click: () -> Unit) =
    MaterialButton(context).apply {
        this.text = text; isAllCaps = false; cornerRadius = context.dp(16)
        minimumHeight = context.dp(48)
        val accent = if (style == "danger") 0xFFB3261E.toInt() else 0xFFD81B60.toInt()
        setTextColor(if (style == "primary") Color.WHITE else accent)
        backgroundTintList = ColorStateList.valueOf(if (style == "primary") accent else Color.WHITE)
        if (style != "primary") { strokeWidth = context.dp(1); strokeColor = ColorStateList.valueOf(accent) }
        layoutParams = if (orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(4) }
        else LinearLayout.LayoutParams(-1, -2)
        setOnClickListener { click() }
    }.also(::addView)

internal fun LinearLayout.toggle(label: String) = SwitchMaterial(context).apply {
    text = label; textSize = 15f; minHeight = context.dp(48)
    layoutParams = if (orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f)
        else LinearLayout.LayoutParams(-1, -2)
}.also(::addView)
