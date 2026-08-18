package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CategoryType
import com.example.data.model.CustomCategory
import com.example.data.model.TransactionEntity
import com.example.ui.components.AddTransactionDialog
import com.example.ui.theme.ColorExpense
import com.example.ui.theme.ColorIncome
import com.example.ui.viewmodel.AppCurrency
import com.example.ui.viewmodel.BookkeepingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(viewModel: BookkeepingViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()

    val sortField by viewModel.sortField.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()

    val customCategories by viewModel.customCategories.collectAsState()
    val prefilledTransaction by viewModel.prefilledTransaction.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    LaunchedEffect(prefilledTransaction) {
        prefilledTransaction?.let { prefilled ->
            editingTransaction = prefilled
            viewModel.clearPrefilledTransaction()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ledger_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.home_input_placeholder)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            // Table Header Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(28.dp)
                    )
                    
                    // Date Header
                    Row(
                        modifier = Modifier.width(76.dp).clickable { viewModel.toggleSort(BookkeepingViewModel.SortField.DATE) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.ledger_sort_date), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        if (sortField == BookkeepingViewModel.SortField.DATE) {
                            Text(if (sortDirection == BookkeepingViewModel.SortDirection.ASC) "↑" else "↓", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Title Header
                    Row(
                        modifier = Modifier.weight(1f).clickable { viewModel.toggleSort(BookkeepingViewModel.SortField.TITLE) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${stringResource(R.string.ledger_sort_title)} / ${stringResource(R.string.ledger_sort_category)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        if (sortField == BookkeepingViewModel.SortField.TITLE) {
                            Text(if (sortDirection == BookkeepingViewModel.SortDirection.ASC) "↑" else "↓", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Amount Header
                    Row(
                        modifier = Modifier.width(70.dp).clickable { viewModel.toggleSort(BookkeepingViewModel.SortField.AMOUNT) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.ledger_sort_amount), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        if (sortField == BookkeepingViewModel.SortField.AMOUNT) {
                            Text(if (sortDirection == BookkeepingViewModel.SortDirection.ASC) "↑" else "↓", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Text(
                        text = stringResource(R.string.reports_net_savings),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(64.dp)
                    )
                }
            }

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ledger_empty_records),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(transactions, key = { it.id }) { item ->
                        val catObj = viewModel.getCategoryByCode(item.category)
                        TransactionRowItem(
                            item = item,
                            category = catObj,
                            currency = selectedCurrency,
                            onEdit = { editingTransaction = item },
                            onDelete = { viewModel.deleteTransaction(item) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            customCategories = customCategories,
            onDismiss = { showAddDialog = false },
            onConfirm = { date, title, category, income, expense ->
                viewModel.addManualTransaction(date, title, category, income, expense)
                showAddDialog = false
            }
        )
    }

    editingTransaction?.let { target ->
        AddTransactionDialog(
            transaction = target,
            customCategories = customCategories,
            onDismiss = { editingTransaction = null },
            onConfirm = { date, title, category, income, expense ->
                viewModel.updateTransaction(
                    target.copy(
                        date = date,
                        title = title,
                        category = category,
                        income = income,
                        expense = expense
                    )
                )
                editingTransaction = null
            }
        )
    }
}

@Composable
fun TransactionRowItem(
    item: TransactionEntity,
    category: CustomCategory? = null,
    currency: AppCurrency = AppCurrency.TWD,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMenu = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 項目編號
            Text(
                text = item.itemNo.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )

            // 日期
            Text(
                text = item.date,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                modifier = Modifier.width(76.dp)
            )

            // 標題與類別 Badge
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val catObj = category ?: CustomCategory.UNKNOWN
                    val catColor = catObj.parseColor()
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = catColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (catObj.code.isNotBlank()) "${catObj.code} ${catObj.name}" else catObj.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = catColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // 金額 (收入 / 支出)
            Column(
                modifier = Modifier.width(76.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (item.income != null && item.income > 0) {
                    Text(
                        text = "+${currency.format(item.income)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = ColorIncome
                    )
                }
                if (item.expense != null && item.expense > 0) {
                    Text(
                        text = "-${currency.format(item.expense)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = ColorExpense
                    )
                }
            }

            // 小計
            Text(
                text = currency.format(item.subtotal),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(70.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ledger_btn_edit)) },
                leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit") },
                onClick = {
                    showMenu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ledger_btn_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}
