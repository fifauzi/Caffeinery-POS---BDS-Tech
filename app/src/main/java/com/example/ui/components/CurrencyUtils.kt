package com.example.ui.components

import java.util.Locale

object CurrencyUtils {
    fun formatIdr(amount: Double): String {
        return "Rp " + String.format(Locale.GERMAN, "%,.0f", amount)
    }

    fun formatRupiah(amount: Double): String = formatIdr(amount)

    fun formatIdrShort(amount: Double): String {
        return when {
            amount >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", amount / 1_000_000_000)
            amount >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000)
            amount >= 1_000 -> String.format(Locale.US, "%.0fK", amount / 1_000)
            else -> String.format(Locale.US, "%.0f", amount)
        }
    }

    fun formatPercent(percent: Double): String {
        return if (percent % 1.0 == 0.0) "${percent.toLong()}%" else "${percent}%"
    }

    fun formatDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    fun cleanDecimalInput(input: String): String {
        val normalized = input.replace(',', '.')
        val sb = StringBuilder()
        var hasDot = false
        for (ch in normalized) {
            if (ch.isDigit()) {
                sb.append(ch)
            } else if (ch == '.' && !hasDot) {
                sb.append(ch)
                hasDot = true
            }
        }
        return sb.toString()
    }
}
