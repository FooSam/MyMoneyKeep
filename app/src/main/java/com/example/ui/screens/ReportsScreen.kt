package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PieChart
import com.example.ui.theme.ColorCategoryA
import com.example.ui.theme.ColorCategoryB
import com.example.ui.theme.ColorCategoryC
import com.example.ui.theme.ColorCategoryD
import com.example.ui.theme.ColorExpense
import com.example.ui.theme.ColorIncome
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.BookkeepingViewModel
import com.example.ui.viewmodel.CategorySummary
import com.example.ui.viewmodel.ReportTimeRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: BookkeepingViewModel) {
    val reportData by viewModel.reportData.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val periodLabel by viewModel.selectedPeriodLabel.collectAsState()
    val availablePeriodOptions by viewModel.availablePeriodOptions.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()

    var periodMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "消費報表與圓餅圖分析",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // Time Range Tabs: 週 / 月 / 季 / 年
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ReportTimeRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ReportTimeRange.entries.size),
                            onClick = { viewModel.setReportTimeRange(range) },
                            selected = selectedRange == range
                        ) {
                            Text(text = range.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Period Selector Dropdown with Quick Nav Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentIndex = availablePeriodOptions.indexOf(periodLabel)

                    // Prev Period Button (older date / next option)
                    IconButton(
                        onClick = {
                            if (currentIndex < availablePeriodOptions.size - 1 && currentIndex != -1) {
                                viewModel.setReportPeriodLabel(availablePeriodOptions[currentIndex + 1])
                            } else if (availablePeriodOptions.isNotEmpty()) {
                                viewModel.setReportPeriodLabel(availablePeriodOptions.last())
                            }
                        },
                        enabled = availablePeriodOptions.size > 1
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Period")
                    }

                    // Center Selector Dropdown
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { periodMenuExpanded = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Period",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "統計區間：$periodLabel",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        DropdownMenu(
                            expanded = periodMenuExpanded,
                            onDismissRequest = { periodMenuExpanded = false }
                        ) {
                            availablePeriodOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = opt,
                                            fontWeight = if (opt == periodLabel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (opt == periodLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        viewModel.setReportPeriodLabel(opt)
                                        periodMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Next Period Button (newer date / previous option)
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                viewModel.setReportPeriodLabel(availablePeriodOptions[currentIndex - 1])
                            } else if (availablePeriodOptions.isNotEmpty()) {
                                viewModel.setReportPeriodLabel(availablePeriodOptions.first())
                            }
                        },
                        enabled = availablePeriodOptions.size > 1
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Period")
                    }
                }
            }

            // Summary Cards Row (總收入 | 總支出 | 淨結餘)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        title = "總收入",
                        amount = reportData.totalIncome,
                        color = ColorIncome,
                        currency = selectedCurrency,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "總支出",
                        amount = reportData.totalExpense,
                        color = ColorExpense,
                        currency = selectedCurrency,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        title = "淨結餘",
                        amount = reportData.netBalance,
                        color = if (reportData.netBalance >= 0) MaterialTheme.colorScheme.primary else ColorExpense,
                        currency = selectedCurrency,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Interactive Pie Chart Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${selectedRange.label} - 支出比例圓餅圖",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        PieChart(
                            categorySummaries = reportData.categorySummaries,
                            totalExpense = reportData.totalExpense,
                            currency = selectedCurrency
                        )
                    }
                }
            }

            // Category Breakdown Title
            item {
                Text(
                    text = "類別消費統計分析 (A/B/C/D)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Category List Items
            items(reportData.categorySummaries) { catSummary ->
                CategoryBreakdownCard(catSummary, currency = selectedCurrency)
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: Double,
    color: Color,
    currency: AppCurrency = AppCurrency.TWD,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currency.format(amount),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = color
            )
        }
    }
}

@Composable
fun CategoryBreakdownCard(
    summary: CategorySummary,
    currency: AppCurrency = AppCurrency.TWD
) {
    val catColor = try {
        Color(android.graphics.Color.parseColor(summary.colorHex))
    } catch (e: Exception) {
        Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(catColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "類別 ${summary.code}：${summary.label}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${summary.itemCount}筆)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = currency.format(summary.totalAmount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = catColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { (summary.percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = catColor,
                    trackColor = catColor.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${String.format("%.1f", summary.percentage)}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
