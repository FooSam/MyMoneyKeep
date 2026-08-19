package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.model.CurrencyInfo
import com.example.data.model.ExchangeTimeRange
import com.example.data.model.HistoricalRatePoint
import com.example.data.model.SupportedCurrencies
import com.example.ui.viewmodel.BookkeepingViewModel
import com.example.util.CalculatorEngine
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyCalculatorScreen(
    viewModel: BookkeepingViewModel,
    onNavigateToLedgerWithPrefill: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentLang by viewModel.selectedLanguage.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val targetCurrency by viewModel.targetCurrency.collectAsState()
    val calcExpression by viewModel.calcExpression.collectAsState()
    val calculatedBaseAmount by viewModel.calculatedBaseAmount.collectAsState()
    val convertedTargetAmount by viewModel.convertedTargetAmount.collectAsState()
    val currentRate by viewModel.currentRate.collectAsState()
    val historicalRates by viewModel.historicalRates.collectAsState()
    val timeRange by viewModel.exchangeTimeRange.collectAsState()
    val isLoading by viewModel.isExchangeLoading.collectAsState()
    val lastUpdate by viewModel.lastExchangeUpdate.collectAsState()
    val isFromCache by viewModel.isExchangeFromCache.collectAsState()
    val allRates by viewModel.exchangeRates.collectAsState()

    var showCurrencyDialogForBase by remember { mutableStateOf(false) }
    var showCurrencyDialogForTarget by remember { mutableStateOf(false) }
    var isTrendExpanded by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.exchange_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (lastUpdate.isNotEmpty()) {
                            val cacheNotice = if (isFromCache) " (離線)" else ""
                            Text(
                                text = stringResource(R.string.exchange_last_updated, lastUpdate) + cacheNotice,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshExchangeRates(force = true)
                            Toast.makeText(context, context.getString(R.string.exchange_updating), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Rates",
                            modifier = if (isLoading) Modifier.rotate(spinAngle) else Modifier,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 幣別與國旗選擇卡片 (對標易匯率)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左側基準幣別按鈕
                    CurrencySelectButton(
                        currency = baseCurrency,
                        modifier = Modifier.weight(1f),
                        onClick = { showCurrencyDialogForBase = true }
                    )

                    // 中間對調按鈕
                    IconButton(
                        onClick = { viewModel.swapCurrencies() },
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap Currencies",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // 右側目標幣別按鈕
                    CurrencySelectButton(
                        currency = targetCurrency,
                        modifier = Modifier.weight(1f),
                        onClick = { showCurrencyDialogForTarget = true }
                    )
                }
            }

            // 2. 即時匯率比例標籤
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.exchange_rate_label,
                            baseCurrency.code,
                            String.format(Locale.US, "%.4f", currentRate),
                            targetCurrency.code
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "1 ${targetCurrency.code} = ${String.format(Locale.US, "%.4f", if (currentRate > 0) 1.0 / currentRate else 0.0)} ${baseCurrency.code}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 3. 雙向金額換算顯示區 (Display Screen)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 輸入基準金額
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${baseCurrency.flagEmoji} ${baseCurrency.code} (${baseCurrency.symbol})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (calcExpression.length > 20) calcExpression.takeLast(20) else calcExpression,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (calcExpression != "0") {
                            IconButton(onClick = { viewModel.clearCalcInput() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 換算後目標金額
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${targetCurrency.flagEmoji} ${targetCurrency.code} (${targetCurrency.symbol})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val formattedTarget = if (targetCurrency.decimalPlaces == 0) {
                                "${targetCurrency.symbol}${String.format(Locale.US, "%,d", Math.round(convertedTargetAmount))}"
                            } else {
                                "${targetCurrency.symbol}${String.format(Locale.US, "%,.2f", convertedTargetAmount)}"
                            }
                            Text(
                                text = formattedTarget,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4. 自訂計算機數字鍵盤 (Custom Keypad) - 緊接在金額顯示區下方，一體化操作！
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 第一列: C, ÷, ×, ⌫
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CalcButton("C", color = MaterialTheme.colorScheme.errorContainer, textColor = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("C") }
                        CalcButton("÷", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("÷") }
                        CalcButton("×", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("×") }
                        CalcButton("⌫", color = MaterialTheme.colorScheme.surfaceVariant, textColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("⌫") }
                    }

                    // 第二列: 7, 8, 9, -
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CalcButton("7", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("7") }
                        CalcButton("8", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("8") }
                        CalcButton("9", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("9") }
                        CalcButton("-", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("-") }
                    }

                    // 第三列: 4, 5, 6, +
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CalcButton("4", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("4") }
                        CalcButton("5", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("5") }
                        CalcButton("6", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("6") }
                        CalcButton("+", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("+") }
                    }

                    // 第四列: 1, 2, 3, =
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CalcButton("1", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("1") }
                        CalcButton("2", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("2") }
                        CalcButton("3", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("3") }
                        CalcButton("=", color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f)) { viewModel.onCalcInput("=") }
                    }

                    // 第五列: 0, 00, ., 📥 帶入記帳
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        CalcButton("0", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("0") }
                        CalcButton("00", modifier = Modifier.weight(1f)) { viewModel.onCalcInput("00") }
                        CalcButton(".", modifier = Modifier.weight(1f)) { viewModel.onCalcInput(".") }
                        
                        // 帶入記帳按鈕
                        Button(
                            onClick = {
                                val note = context.getString(
                                    R.string.exchange_note_template,
                                    CalculatorEngine.formatNumber(calculatedBaseAmount),
                                    baseCurrency.code,
                                    String.format(Locale.US, "%.4f", currentRate)
                                )
                                viewModel.prefillTransactionFromExchange(convertedTargetAmount, note)
                                Toast.makeText(context, context.getString(R.string.exchange_btn_import_bookkeeping), Toast.LENGTH_SHORT).show()
                                onNavigateToLedgerWithPrefill()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.exchange_btn_import_bookkeeping),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 5. 歷史匯率走勢折線圖 (移至下方，支援展開/收合)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTrendExpanded = !isTrendExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isTrendExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isTrendExpanded) stringResource(R.string.exchange_trend_collapse) else stringResource(R.string.exchange_trend_expand),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.exchange_trend_title, baseCurrency.code, targetCurrency.code),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (isTrendExpanded) {
                            // 週期按鈕
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimeRangeButton(stringResource(R.string.exchange_time_range_1w), timeRange == ExchangeTimeRange.ONE_WEEK) {
                                    viewModel.setExchangeTimeRange(ExchangeTimeRange.ONE_WEEK)
                                }
                                TimeRangeButton(stringResource(R.string.exchange_time_range_1m), timeRange == ExchangeTimeRange.ONE_MONTH) {
                                    viewModel.setExchangeTimeRange(ExchangeTimeRange.ONE_MONTH)
                                }
                                TimeRangeButton(stringResource(R.string.exchange_time_range_3m), timeRange == ExchangeTimeRange.THREE_MONTHS) {
                                    viewModel.setExchangeTimeRange(ExchangeTimeRange.THREE_MONTHS)
                                }
                                TimeRangeButton(stringResource(R.string.exchange_time_range_1y), timeRange == ExchangeTimeRange.ONE_YEAR) {
                                    viewModel.setExchangeTimeRange(ExchangeTimeRange.ONE_YEAR)
                                }
                            }
                        }
                    }

                    if (isTrendExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // 繪製平滑走勢圖
                        ExchangeRateLineChart(
                            historicalPoints = historicalRates,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }
            }

            // 6. 多國常用匯率對照清單
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.exchange_popular_currencies),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val popularCurrencies = remember(baseCurrency) {
                        SupportedCurrencies.list.filter { it.code != baseCurrency.code && it.isPopular }
                    }

                    popularCurrencies.forEach { curr ->
                        val r = allRates[curr.code] ?: 0.0
                        val converted = calculatedBaseAmount * r
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    viewModel.setTargetCurrency(curr)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = curr.flagEmoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = curr.getDisplayName(currentLang),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "1 ${baseCurrency.code} = ${String.format(Locale.US, "%.4f", r)} ${curr.code}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = if (curr.decimalPlaces == 0) {
                                    "${curr.symbol}${String.format(Locale.US, "%,d", Math.round(converted))}"
                                } else {
                                    "${curr.symbol}${String.format(Locale.US, "%,.2f", converted)}"
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 幣別選擇彈窗 (基準)
    if (showCurrencyDialogForBase) {
        CurrencyPickerDialog(
            title = stringResource(R.string.exchange_select_currency),
            onDismiss = { showCurrencyDialogForBase = false },
            onSelect = { curr ->
                viewModel.setBaseCurrency(curr)
                showCurrencyDialogForBase = false
            }
        )
    }

    // 幣別選擇彈窗 (目標)
    if (showCurrencyDialogForTarget) {
        CurrencyPickerDialog(
            title = stringResource(R.string.exchange_select_currency),
            onDismiss = { showCurrencyDialogForTarget = false },
            onSelect = { curr ->
                viewModel.setTargetCurrency(curr)
                showCurrencyDialogForTarget = false
            }
        )
    }
}

@Composable
private fun CurrencySelectButton(
    currency: CurrencyInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = currency.flagEmoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = currency.code,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = currency.nameZhTW.substringBefore(" ("),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeRangeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CalcButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color,
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
        }
    }
}

@Composable
private fun ExchangeRateLineChart(
    historicalPoints: List<HistoricalRatePoint>,
    modifier: Modifier = Modifier
) {
    if (historicalPoints.isEmpty() || historicalPoints.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.reports_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minRate = historicalPoints.minOf { it.rate }
    val maxRate = historicalPoints.maxOf { it.rate }
    val range = (maxRate - minRate).coerceAtLeast(0.0001)

    Column(modifier = modifier) {
        // 最高與最低標籤
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最高: ${String.format(Locale.US, "%.4f", maxRate)}",
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
            Text(
                text = "最低: ${String.format(Locale.US, "%.4f", minRate)}",
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val width = size.width
            val height = size.height
            val padding = 16f
            val graphWidth = width - padding * 2
            val graphHeight = height - padding * 2

            // 繪製背景網格虛線
            val numGridLines = 3
            for (i in 0..numGridLines) {
                val y = padding + (graphHeight / numGridLines) * i
                drawLine(
                    color = gridColor,
                    start = Offset(padding, y),
                    end = Offset(width - padding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 計算點位
            val points = historicalPoints.mapIndexed { index, item ->
                val x = padding + (index.toFloat() / (historicalPoints.size - 1)) * graphWidth
                val normalizedY = ((item.rate - minRate) / range).toFloat()
                val y = (height - padding) - normalizedY * graphHeight
                Offset(x, y)
            }

            // 建立平滑路徑
            val path = Path()
            val fillPath = Path()

            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, height - padding)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val midX = (prev.x + curr.x) / 2
                    path.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }

                fillPath.lineTo(points.last().x, height - padding)
                fillPath.close()

                // 繪製漸層底色
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = padding,
                        endY = height
                    )
                )

                // 繪製折線
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // 繪製最新點位小圓點
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = points.last()
                )
            }
        }

        // X 軸首尾日期
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = historicalPoints.first().date, style = MaterialTheme.typography.labelSmall, color = textColor)
            Text(text = historicalPoints.last().date, style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}

@Composable
private fun CurrencyPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (CurrencyInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allCurrencies = remember { SupportedCurrencies.list }

    val filteredList = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allCurrencies
        } else {
            val q = searchQuery.trim().lowercase()
            allCurrencies.filter {
                it.code.lowercase().contains(q) ||
                it.nameZhTW.lowercase().contains(q) ||
                it.nameZhCN.lowercase().contains(q) ||
                it.nameEn.lowercase().contains(q)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.exchange_search_currency)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredList) { curr ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(curr) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = curr.flagEmoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${curr.code} - ${curr.nameZhTW}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${curr.nameEn} (${curr.symbol})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.ledger_btn_cancel))
                }
            }
        }
    }
}
