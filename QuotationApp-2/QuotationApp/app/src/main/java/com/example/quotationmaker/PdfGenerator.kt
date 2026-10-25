package com.example.quotationmaker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Draws a quotation on A4 pages, laid out like the sample quotation. */
object PdfGenerator {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 36f
    private val maroon = Color.rgb(122, 17, 30)
    private val rose = Color.rgb(200, 80, 92)

    private fun paint(size: Float, color: Int = Color.BLACK, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split("\n")) {
            if (para.isBlank()) { out.add(""); continue }
            var line = ""
            for (word in para.split(" ")) {
                val test = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(test) <= maxWidth) line = test
                else {
                    if (line.isNotEmpty()) out.add(line)
                    line = word
                }
            }
            if (line.isNotEmpty()) out.add(line)
        }
        return out
    }

    fun generate(context: Context, q: Quotation): File {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var c: Canvas = page.canvas

        val title = paint(20f, rose, true)
        val sub = paint(18f, rose)
        val small = paint(9f, Color.DKGRAY)
        val body = paint(11f)
        val bold = paint(11f, Color.BLACK, true)
        val white = paint(11f, Color.WHITE, true)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = maroon; style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val fill = Paint().apply { color = maroon; style = Paint.Style.FILL }

        // Header
        var y = 56f
        c.drawText(q.fromName.uppercase(), (W - title.measureText(q.fromName.uppercase())) / 2f, y, title)
        y += 30f
        c.drawText("QUOTATION", (W - sub.measureText("QUOTATION")) / 2f, y, sub)
        y += 26f

        // Hospital / sender details, right aligned
        val right = W - MARGIN
        val fromLines = buildList {
            if (q.fromRegNo.isNotBlank()) add("Reg no: ${q.fromRegNo}")
            addAll(wrap(q.fromAddress, small, 220f))
            if (q.fromPhone.isNotBlank()) add("Mobile number: ${q.fromPhone}")
            if (q.fromEmail.isNotBlank()) add("Mail: ${q.fromEmail}")
        }
        for (l in fromLines) {
            c.drawText(l, right - small.measureText(l), y, small)
            y += 13f
        }

        // Quotation no / date on the left, same block
        var ly = 108f + 26f
        if (q.number.isNotBlank()) { c.drawText("Quotation No: ${q.number}", MARGIN, ly, body); ly += 15f }
        if (q.date.isNotBlank()) { c.drawText("Date: ${q.date}", MARGIN, ly, body); ly += 15f }

        y = maxOf(y, ly) + 14f
        c.drawText("TO", MARGIN, y, bold); y += 15f
        c.drawText(q.toName, MARGIN, y, paint(12f, maroon, true)); y += 15f
        for (l in wrap(q.toAddress, body, W - 2 * MARGIN)) { c.drawText(l, MARGIN, y, body); y += 14f }
        y += 18f

        // Table
        val x0 = MARGIN
        val colW = floatArrayOf(36f, 215f, 100f, 80f, 92f) // = 523
        val xs = FloatArray(colW.size + 1).also { a -> a[0] = x0; for (i in colW.indices) a[i + 1] = a[i] + colW[i] }
        val heads = listOf("Sr", "Description", "Rate", "Qty", "Total")

        fun drawHead() {
            c.drawRect(x0, y, xs.last(), y + 24f, fill)
            for (i in heads.indices) c.drawText(heads[i], xs[i] + 8f, y + 16f, white)
            y += 24f
        }
        drawHead()

        q.items.forEachIndexed { index, item ->
            val descLines = wrap(item.description, body, colW[1] - 16f)
            val rowH = maxOf(34f, descLines.size * 15f + 18f)
            if (y + rowH > H - 90f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                y = 50f
                drawHead()
            }
            for (i in colW.indices) c.drawRect(xs[i], y, xs[i + 1], y + rowH, border)
            c.drawText("${index + 1}", xs[0] + 8f, y + rowH / 2f + 4f, body)
            var dy = y + (rowH - descLines.size * 15f) / 2f + 11f
            for (l in descLines) { c.drawText(l, xs[1] + 8f, dy, body); dy += 15f }
            val rLines = wrap(item.rateLabel(), body, colW[2] - 12f)
            var ry = y + (rowH - rLines.size * 15f) / 2f + 11f
            for (l in rLines) { c.drawText(l, xs[2] + 6f, ry, body); ry += 15f }
            val qLines = wrap(item.qtyLabel(), body, colW[3] - 12f)
            var qy = y + (rowH - qLines.size * 15f) / 2f + 11f
            for (l in qLines) { c.drawText(l, xs[3] + 6f, qy, body); qy += 15f }
            c.drawText(money(item.total), xs[4] + 6f, y + rowH / 2f + 4f, body)
            y += rowH
        }

        // Totals
        fun totalRow(label: String, value: String, strong: Boolean = false) {
            if (y + 26f > H - 90f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                y = 50f
            }
            val lx = xs[2]
            c.drawRect(lx, y + 6f, xs[4], y + 30f, border)
            c.drawRect(xs[4], y + 6f, xs.last(), y + 30f, border)
            val p = if (strong) bold else body
            c.drawText(label, lx + (xs[4] - lx - p.measureText(label)) / 2f, y + 22f, p)
            c.drawText(value, xs[4] + 6f, y + 22f, p)
            y += 28f
        }
        totalRow("Subtotal", money(q.subtotal))
        if (q.gstPercent > 0) totalRow("GST (${fmtNum(q.gstPercent)}%)", money(q.gstAmount))
        totalRow("Total", money(q.total), strong = true)

        // Terms + bank
        y += 36f
        fun need(h: Float) {
            if (y + h > H - 40f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                c = page.canvas
                y = 50f
            }
        }
        if (q.paymentTerms.isNotBlank()) {
            need(60f)
            c.drawText("Payment Terms:", MARGIN, y, bold); y += 16f
            for (l in wrap(q.paymentTerms, body, W - 2 * MARGIN)) { c.drawText(l, MARGIN, y, body); y += 14f }
            y += 12f
        }
        val bankLines = buildList {
            if (q.bankHolder.isNotBlank()) add(q.bankHolder.uppercase())
            if (q.bankName.isNotBlank()) add("Bank: ${q.bankName}")
            if (q.accountNo.isNotBlank()) add("A/C No: ${q.accountNo}")
            if (q.ifsc.isNotBlank()) add("IFSC: ${q.ifsc}")
        }
        if (bankLines.isNotEmpty()) {
            need(16f + bankLines.size * 14f)
            c.drawText("Bank Details:", MARGIN, y, bold); y += 16f
            for (l in bankLines) { c.drawText(l, MARGIN, y, body); y += 14f }
        }

        doc.finishPage(page)

        val dir = File(context.cacheDir, "quotations").apply { mkdirs() }
        val safe = (q.number.ifBlank { "quotation" }).replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "Quotation_$safe.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
