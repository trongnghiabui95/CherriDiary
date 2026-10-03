package com.cherri.diary.service

import com.cherri.diary.api.*
import com.cherri.diary.domain.CustomerRepository
import com.cherri.diary.domain.ProductRepository
import com.cherri.diary.domain.ProductStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentParserService(private val products: ProductRepository, private val customers: CustomerRepository) {
    private val phonePattern = Regex("(?<![\\d\\w])(?:0[35789]\\d{8}|\\+84[35789]\\d{8})(?!\\d)")
    private val nickPattern = Regex("(?<![\\w@])@([a-zA-Z0-9_.]{1,100})(?![a-zA-Z0-9_.])")
    private val itemPattern = Regex("(?<![\\w@])([a-zA-Z][a-zA-Z0-9_-]{0,29})(?:\\s+[xX]?([-+]?[0-9]+(?:[.,][0-9]+)?))?(?![\\w-])")

    @Transactional(readOnly = true)
    fun parse(comment: String): ParsedComment {
        val warnings = mutableListOf<String>()
        val phones = phonePattern.findAll(comment).map { Identity.phone(it.value)!! }.distinct().toList()
        val nicks = nickPattern.findAll(comment).map { Identity.tiktok(it.groupValues[1])!! }.distinct().toList()
        if (phones.size > 1 || nicks.size > 1) warnings += "Comment có nhiều khách hàng; chọn lại thông tin trước khi chốt"
        val phone = phones.singleOrNull()
        val nick = nicks.singleOrNull()
        val matches = (phones.mapNotNull(customers::findByPhoneNumber) + nicks.mapNotNull(customers::findByTiktokId)).distinctBy { it.id }
        if (matches.size > 1) warnings += "SĐT và TikTok ID thuộc các hồ sơ khác nhau"
        val stripped = nickPattern.replace(phonePattern.replace(comment, " "), " ")
        val quantities = linkedMapOf<String, Int>()
        itemPattern.findAll(stripped).forEach { match ->
            val code = Identity.code(match.groupValues[1])
            val product = products.findByShortCode(code)
            if (product != null || code.any(Char::isDigit)) {
                val quantity = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: if (match.groupValues[2].isEmpty()) 1 else 0
                if (quantity !in 1..10000 || (quantities[code] ?: 0).toLong() + quantity > 10000) {
                    warnings += "Số lượng $code phải từ 1 đến 10000"
                } else quantities[code] = (quantities[code] ?: 0) + quantity
            }
        }
        val items = quantities.map { (code, quantity) ->
            val product = products.findByShortCode(code)
            if (product == null) warnings += "Không tìm thấy mã hàng $code"
            else if (product.status != ProductStatus.ACTIVE || product.stockQuantity < quantity) warnings += "$code không bán được hoặc không đủ kho"
            ParsedItem(code, quantity, product?.toView())
        }
        if (items.isEmpty()) warnings += "Không nhận diện được mã hàng"
        if (phone == null && nick == null) warnings += "Chưa có SĐT hoặc TikTok ID"
        return ParsedComment(comment, phone, nick, matches.singleOrNull()?.toView(), items, matches.any { it.isBlacklisted }, warnings)
    }
}
