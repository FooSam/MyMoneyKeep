package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.model.TransactionEntity
import com.example.ui.viewmodel.AppCurrency
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetCalculationTest {

    @Test
    fun testWidgetStatCalculations() {
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val todayStr = "$currentYear/$currentMonth/$currentDay"

        val txList = listOf(
            TransactionEntity(
                id = 1,
                itemNo = 1,
                date = todayStr,
                title = "早餐",
                category = "C",
                income = null,
                expense = 65.0,
                subtotal = -65.0
            ),
            TransactionEntity(
                id = 2,
                itemNo = 2,
                date = todayStr,
                title = "午餐",
                category = "C",
                income = null,
                expense = 120.0,
                subtotal = -185.0
            ),
            TransactionEntity(
                id = 3,
                itemNo = 3,
                date = todayStr,
                title = "退費收入",
                category = "A",
                income = 500.0,
                expense = null,
                subtotal = 315.0
            ),
            TransactionEntity(
                id = 4,
                itemNo = 4,
                date = "$currentYear/$currentMonth/1",
                title = "月初房租",
                category = "B",
                income = null,
                expense = 12000.0,
                subtotal = -11685.0
            )
        )

        var todayExpense = 0.0
        var todayIncome = 0.0
        var monthExpense = 0.0
        var monthIncome = 0.0

        for (tx in txList) {
            val inc = tx.income ?: 0.0
            val exp = tx.expense ?: 0.0
            val cleanDate = tx.date.trim().replace("-", "/")
            if (cleanDate == todayStr) {
                todayExpense += exp
                todayIncome += inc
            }
            if (cleanDate.startsWith("$currentYear/$currentMonth/")) {
                monthExpense += exp
                monthIncome += inc
            }
        }

        assertEquals(185.0, todayExpense, 0.001)
        assertEquals(500.0, todayIncome, 0.001)
        assertEquals(12185.0, monthExpense, 0.001)
        assertEquals(500.0, monthIncome, 0.001)
        assertEquals(-11685.0, monthIncome - monthExpense, 0.001)
    }
}
