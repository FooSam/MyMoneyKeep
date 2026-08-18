package com.example

import com.example.data.model.SupportedCurrencies
import com.example.ui.viewmodel.AppLanguage
import com.example.util.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CalculatorEngineTest {

    @Test
    fun testSimpleAdditionAndSubtraction() {
        assertEquals(150.0, CalculatorEngine.evaluate("100 + 50"), 0.0001)
        assertEquals(50.0, CalculatorEngine.evaluate("100 - 50"), 0.0001)
    }

    @Test
    fun testMultiplicationAndDivision() {
        assertEquals(5000.0, CalculatorEngine.evaluate("100 × 50"), 0.0001)
        assertEquals(2.0, CalculatorEngine.evaluate("100 ÷ 50"), 0.0001)
    }

    @Test
    fun testPrecedence() {
        // 100 + 50 * 2 = 200
        assertEquals(200.0, CalculatorEngine.evaluate("100 + 50 × 2"), 0.0001)
        // 100 * 2 + 50 = 250
        assertEquals(250.0, CalculatorEngine.evaluate("100 × 2 + 50"), 0.0001)
    }

    @Test
    fun testDecimalAndTrailingOperator() {
        assertEquals(12.5, CalculatorEngine.evaluate("10 + 2.5"), 0.0001)
        // 末尾有運算子時應安全剔除後計算
        assertEquals(10.0, CalculatorEngine.evaluate("10 +"), 0.0001)
        assertEquals(15.0, CalculatorEngine.evaluate("10 + 5 ×"), 0.0001)
    }

    @Test
    fun testProcessInputFlow() {
        var input = "0"
        input = CalculatorEngine.processInput(input, "1")
        input = CalculatorEngine.processInput(input, "0")
        input = CalculatorEngine.processInput(input, "0")
        assertEquals("100", input)

        input = CalculatorEngine.processInput(input, "+")
        input = CalculatorEngine.processInput(input, "5")
        input = CalculatorEngine.processInput(input, "0")
        assertEquals("100+50", input)

        val evaluated = CalculatorEngine.evaluate(input)
        assertEquals(150.0, evaluated, 0.0001)

        val resultStr = CalculatorEngine.processInput(input, "=")
        assertEquals("150", resultStr)
    }

    @Test
    fun testCurrencyLookupAndLocalization() {
        val twd = SupportedCurrencies.findByCode("TWD")
        assertEquals("🇹🇼", twd.flagEmoji)
        assertEquals("新台幣 (TWD)", twd.getDisplayName(AppLanguage.TRADITIONAL_CHINESE))
        assertEquals("New Taiwan Dollar (TWD)", twd.getDisplayName(AppLanguage.ENGLISH))
        assertEquals("日本円 (JPY)", SupportedCurrencies.findByCode("JPY").getDisplayName(AppLanguage.JAPANESE))
        assertEquals("미국 달러 (USD)", SupportedCurrencies.findByCode("USD").getDisplayName(AppLanguage.KOREAN))

        val unknown = SupportedCurrencies.findByCode("XYZ")
        assertNotNull(unknown)
        assertEquals("XYZ", unknown.code)
    }
}
