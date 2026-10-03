package com.cherri.diary.service

import com.cherri.diary.api.invalid
import java.util.Locale

object Identity {
    fun phone(value: String?): String? {
        val cleaned = value?.trim()?.takeIf { it.isNotEmpty() }?.replace(Regex("[\\s().-]"), "") ?: return null
        val normalized = if (cleaned.startsWith("+84")) "0" + cleaned.drop(3) else cleaned
        if (!normalized.matches(Regex("0[35789]\\d{8}"))) invalid("Số điện thoại Việt Nam không hợp lệ")
        return normalized
    }
    fun tiktok(value: String?): String? {
        val normalized = value?.trim()?.removePrefix("@")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        if (!normalized.matches(Regex("[a-z0-9_.]{1,100}"))) invalid("TikTok ID không hợp lệ")
        return normalized
    }
    fun code(value: String) = value.trim().uppercase(Locale.ROOT)
}
