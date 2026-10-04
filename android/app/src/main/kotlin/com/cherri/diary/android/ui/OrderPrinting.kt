package com.cherri.diary.android.ui

import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.text.TextUtils
import android.webkit.WebView
import android.webkit.WebViewClient
import com.cherri.diary.android.data.OrderView

internal fun Activity.printOrder(order: OrderView) {
    fun escape(value: String) = TextUtils.htmlEncode(value)
    val web = WebView(this)
    val rows = order.items.joinToString("") { "<tr><td>${escape(it.shortCode)}</td><td>${it.quantity}</td><td>${escape(money(it.priceAtPurchase))}</td></tr>" }
    val html = """<html><meta charset="utf-8"><style>body{font-family:sans-serif;padding:24px;color:#35252d}h1{color:#b22d54}table{width:100%;border-collapse:collapse}td,th{padding:10px;border-bottom:1px solid #ddd;text-align:left}</style>
        <h1>Cherri Diary</h1><h2>${escape(order.orderCode)}</h2>
        <p>${escape(order.customer.name)}<br>${escape(order.customer.phoneNumber ?: "")}<br>${escape(order.customer.address ?: "")}</p>
        <table><tr><th>Mã hàng</th><th>Số lượng</th><th>Đơn giá</th></tr>$rows</table>
        <p>Tổng tiền: ${escape(money(order.totalAmount))}<br>Cọc: ${escape(money(order.depositAmount))}<br>Còn thu: ${escape(money(order.remainingAmount))}</p>
        <p>Trạng thái: ${escape(order.status)} · Vận đơn: ${escape(order.trackingCode ?: "")}</p></html>"""
    web.webViewClient = object : WebViewClient() {
        private var started = false
        override fun onPageFinished(view: WebView, url: String?) {
            if (started || isFinishing) return
            started = true
            val delegate = web.createPrintDocumentAdapter(order.orderCode)
            val adapter = object : PrintDocumentAdapter() {
                override fun onStart() = delegate.onStart()
                override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, signal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?) =
                    delegate.onLayout(oldAttributes, newAttributes, signal, callback, extras)
                override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, signal: CancellationSignal?, callback: WriteResultCallback?) =
                    delegate.onWrite(pages, destination, signal, callback)
                override fun onFinish() { delegate.onFinish(); web.destroy() }
            }
            val paper = getSharedPreferences("print_settings", 0).getString("paper", "A4")
            getSystemService(PrintManager::class.java).print(order.orderCode, adapter,
                PrintAttributes.Builder().setMediaSize(if (paper == "A5") PrintAttributes.MediaSize.ISO_A5 else PrintAttributes.MediaSize.ISO_A4).build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
