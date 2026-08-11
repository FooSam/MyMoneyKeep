package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorCategoryA
import com.example.ui.theme.ColorCategoryB
import com.example.ui.theme.ColorCategoryC
import com.example.ui.theme.ColorCategoryD
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.CategorySummary
import kotlin.math.atan2

@Composable
fun PieChart(
    categorySummaries: List<CategorySummary>,
    modifier: Modifier = Modifier,
    totalExpense: Double,
    currency: AppCurrency = AppCurrency.TWD
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val expenseCategories = categorySummaries.filter { !it.isIncome && it.totalAmount > 0 }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(categorySummaries) {
        animationProgress.animateTo(1f, animationSpec = tween(800))
    }

    val total = expenseCategories.sumOf { it.totalAmount }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center
    ) {
        if (expenseCategories.isEmpty() || total <= 0) {
            Text(
                text = "此區間無支出記錄",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val colors = listOf(
                ColorCategoryB, // 固定支出
                ColorCategoryC, // 一般支出
                ColorCategoryD  // 特別支出
            )

            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .pointerInput(expenseCategories) {
                        detectTapGestures { tapOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = tapOffset.x - center.x
                            val dy = tapOffset.y - center.y
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (angle < 0) angle += 360f

                            var currentAngle = 270f
                            expenseCategories.forEachIndexed { idx, cat ->
                                val sweep = (cat.totalAmount / total * 360f).toFloat()
                                val start = currentAngle % 360f
                                val end = (currentAngle + sweep) % 360f

                                if (start < end) {
                                    if (angle in start..end) selectedIndex = idx
                                } else {
                                    if (angle >= start || angle <= end) selectedIndex = idx
                                }
                                currentAngle += sweep
                            }
                        }
                    }
            ) {
                val strokeWidth = 38.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                var startAngle = 270f
                expenseCategories.forEachIndexed { index, cat ->
                    val sweepAngle = ((cat.totalAmount / total) * 360f).toFloat() * animationProgress.value
                    val isSelected = selectedIndex == index
                    val catColor = try {
                        Color(android.graphics.Color.parseColor(cat.colorHex))
                    } catch (e: Exception) {
                        Color.Gray
                    }

                    drawArc(
                        color = catColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = if (isSelected) strokeWidth + 12f else strokeWidth)
                    )
                    startAngle += sweepAngle
                }
            }

            // Center text summary
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val selectedCat = selectedIndex?.let { expenseCategories.getOrNull(it) }
                if (selectedCat != null) {
                    Text(
                        text = selectedCat.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currency.format(selectedCat.totalAmount),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format("%.1f", selectedCat.percentage)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "總支出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currency.format(totalExpense),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
