package com.example.quotationmaker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

data class QItem(
    val description: String = "",
    val rate: Double = 0.0,
    val qty: Double = 1.0,
    val unit: String = "Person"
) {
    val total: Double get() = rate * qty

    fun qtyLabel(): String {
        val plural = if (qty != 1.0 && !unit.endsWith("s", ignoreCase = true)) "s" else ""
        return "${fmtNum(qty)} $unit$plural".trim()
    }

    fun rateLabel(): String = "${money(rate, suffix = false)} Per $unit".trim()

    fun toJson() = JSONObject()
        .put("description", description)
        .put("rate", rate)
        .put("qty", qty)
        .put("unit", unit)

    companion object {
        fun fromJson(o: JSONObject) = QItem(
            o.optString("description"),
            o.optDouble("rate", 0.0),
            o.optDouble("qty", 1.0),
            o.optString("unit", "Person")
        )
    }
}

data class Quotation(
    val id: String = UUID.randomUUID().toString(),
    val number: String = "",
    val date: String = "",
    val fromName: String = "",
    val fromRegNo: String = "",
    val fromAddress: String = "",
    val fromPhone: String = "",
    val fromEmail: String = "",
    val toName: String = "",
    val toAddress: String = "",
    val items: List<QItem> = listOf(QItem()),
    val gstPercent: Double = 0.0,
    val paymentTerms: String = "Payment shall be made within 15 days after completion of the work.",
    val bankHolder: String = "",
    val bankName: String = "",
    val accountNo: String = "",
    val ifsc: String = ""
) {
    val subtotal: Double get() = items.sumOf { it.total }
    val gstAmount: Double get() = subtotal * gstPercent / 100.0
    val total: Double get() = subtotal + gstAmount

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("number", number).put("date", date)
        .put("fromName", fromName).put("fromRegNo", fromRegNo)
        .put("fromAddress", fromAddress).put("fromPhone", fromPhone).put("fromEmail", fromEmail)
        .put("toName", toName).put("toAddress", toAddress)
        .put("items", JSONArray().also { arr -> items.forEach { arr.put(it.toJson()) } })
        .put("gstPercent", gstPercent)
        .put("paymentTerms", paymentTerms)
        .put("bankHolder", bankHolder).put("bankName", bankName)
        .put("accountNo", accountNo).put("ifsc", ifsc)

    companion object {
        fun fromJson(o: JSONObject): Quotation {
            val arr = o.optJSONArray("items") ?: JSONArray()
            return Quotation(
                id = o.optString("id", UUID.randomUUID().toString()),
                number = o.optString("number"),
                date = o.optString("date"),
                fromName = o.optString("fromName"),
                fromRegNo = o.optString("fromRegNo"),
                fromAddress = o.optString("fromAddress"),
                fromPhone = o.optString("fromPhone"),
                fromEmail = o.optString("fromEmail"),
                toName = o.optString("toName"),
                toAddress = o.optString("toAddress"),
                items = (0 until arr.length()).map { QItem.fromJson(arr.getJSONObject(it)) },
                gstPercent = o.optDouble("gstPercent", 0.0),
                paymentTerms = o.optString("paymentTerms"),
                bankHolder = o.optString("bankHolder"),
                bankName = o.optString("bankName"),
                accountNo = o.optString("accountNo"),
                ifsc = o.optString("ifsc")
            )
        }
    }
}

/** Saves quotations as JSON in SharedPreferences. */
class QuotationStore(context: Context) {
    private val prefs = context.getSharedPreferences("quotations", Context.MODE_PRIVATE)

    fun load(): List<Quotation> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Quotation.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<Quotation>) {
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun save(q: Quotation): List<Quotation> {
        val list = load().toMutableList()
        val idx = list.indexOfFirst { it.id == q.id }
        if (idx >= 0) list[idx] = q else list.add(0, q)
        persist(list)
        return list
    }

    fun delete(id: String): List<Quotation> {
        val list = load().filterNot { it.id == id }
        persist(list)
        return list
    }
}

fun fmtNum(d: Double): String =
    if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

fun money(d: Double, suffix: Boolean = true): String {
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    return "₹" + nf.format(d) + if (suffix) "/-" else ""
}
