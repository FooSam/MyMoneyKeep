package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

object CalculatorEngine {

    fun formatNumber(number: Double, maxDecimals: Int = 4): String {
        if (number.isNaN() || number.isInfinite()) return "0"
        if (number == number.toLong().toDouble()) {
            return number.toLong().toString()
        }
        val bd = BigDecimal.valueOf(number).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    /**
     * 計算算式並回傳數值。若算式以運算子結尾，會先剔除末尾運算子後再行計算。
     */
    fun evaluate(expression: String): Double {
        val clean = expression.trim()
            .replace("×", "*")
            .replace("÷", "/")
        if (clean.isEmpty()) return 0.0

        val normalized = clean.trimEnd('+', '-', '*', '/', '.')
        if (normalized.isEmpty()) return 0.0

        return try {
            evalSimpleMath(normalized)
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * 支援標準先乘除後加減與左右運算的簡單高精度數學解析器
     */
    private fun evalSimpleMath(expr: String): Double {
        val tokens = mutableListOf<String>()
        var currentNumber = StringBuilder()

        for (i in expr.indices) {
            val c = expr[i]
            if (c.isDigit() || c == '.') {
                currentNumber.append(c)
            } else if (c == '+' || c == '-' || c == '*' || c == '/') {
                if (currentNumber.isNotEmpty()) {
                    tokens.add(currentNumber.toString())
                    currentNumber = StringBuilder()
                } else if (c == '-' && (tokens.isEmpty() || tokens.last() in listOf("+", "-", "*", "/"))) {
                    // 負數開頭
                    currentNumber.append(c)
                    continue
                }
                tokens.add(c.toString())
            }
        }
        if (currentNumber.isNotEmpty()) {
            tokens.add(currentNumber.toString())
        }

        if (tokens.isEmpty()) return 0.0

        // 階段 1：處理乘法與除法
        val stage1 = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                if (stage1.isNotEmpty() && i + 1 < tokens.size) {
                    val prevVal = stage1.removeAt(stage1.size - 1).toDoubleOrNull() ?: 0.0
                    val nextVal = tokens[i + 1].toDoubleOrNull() ?: 0.0
                    val res = if (token == "*") {
                        prevVal * nextVal
                    } else {
                        if (nextVal == 0.0) 0.0 else prevVal / nextVal
                    }
                    stage1.add(res.toString())
                    i += 2
                    continue
                }
            }
            stage1.add(token)
            i++
        }

        // 階段 2：處理加法與減法
        if (stage1.isEmpty()) return 0.0
        var total = stage1[0].toDoubleOrNull() ?: 0.0
        var j = 1
        while (j < stage1.size) {
            val op = stage1[j]
            val nextVal = stage1.getOrNull(j + 1)?.toDoubleOrNull() ?: 0.0
            if (op == "+") {
                total += nextVal
            } else if (op == "-") {
                total -= nextVal
            }
            j += 2
        }

        return total
    }

    /**
     * 當使用者按鍵盤按鈕時更新算式字串
     */
    fun processInput(current: String, key: String): String {
        return when (key) {
            "C" -> "0"
            "⌫" -> {
                if (current.length <= 1 || current == "0") "0" else current.dropLast(1)
            }
            "+", "-", "×", "÷" -> {
                val lastChar = current.lastOrNull()
                if (lastChar != null && lastChar in "+-×÷") {
                    current.dropLast(1) + key
                } else {
                    current + key
                }
            }
            "." -> {
                // 檢查最後一個數字區段是否已經有小數點
                val lastSegment = current.split('+', '-', '×', '÷').lastOrNull() ?: ""
                if (lastSegment.contains('.')) {
                    current
                } else {
                    current + "."
                }
            }
            "=" -> {
                val result = evaluate(current)
                formatNumber(result)
            }
            else -> { // 數字 0-9
                if (current == "0") {
                    key
                } else {
                    current + key
                }
            }
        }
    }
}
