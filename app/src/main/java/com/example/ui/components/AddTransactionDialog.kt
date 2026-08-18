package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CustomCategory
import com.example.data.model.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    transaction: TransactionEntity? = null,
    customCategories: List<CustomCategory> = CustomCategory.DEFAULT_CATEGORIES,
    onDismiss: () -> Unit,
    onConfirm: (date: String, title: String, category: String, income: Double?, expense: Double?) -> Unit
) {
    val today = SimpleDateFormat("yyyy/M/d", Locale.TAIWAN).format(Date())

    var date by remember { mutableStateOf(transaction?.date ?: today) }
    var title by remember { mutableStateOf(transaction?.title ?: "") }
    var selectedCategoryCode by remember { mutableStateOf(transaction?.category ?: customCategories.firstOrNull()?.code ?: "C") }
    var isIncomeType by remember { mutableStateOf(transaction?.income != null && transaction.income > 0) }
    var amountText by remember {
        mutableStateOf(
            if (transaction?.income != null && transaction.income > 0) transaction.income.toInt().toString()
            else if (transaction?.expense != null) transaction.expense.toInt().toString()
            else ""
        )
    }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val currentCatObj = customCategories.find { it.code.equals(selectedCategoryCode, ignoreCase = true) }
        ?: CustomCategory.UNKNOWN

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(if (transaction == null) R.string.dialog_add_title else R.string.dialog_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Segmented Button / Filter Chip for Income vs Expense
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isIncomeType,
                        onClick = {
                            isIncomeType = false
                            val defaultExp = customCategories.firstOrNull { !it.isIncome }
                            if (defaultExp != null && currentCatObj.isIncome) {
                                selectedCategoryCode = defaultExp.code
                            }
                        },
                        label = { Text(stringResource(R.string.dialog_type_expense)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isIncomeType,
                        onClick = {
                            isIncomeType = true
                            val defaultInc = customCategories.firstOrNull { it.isIncome }
                            if (defaultInc != null && !currentCatObj.isIncome) {
                                selectedCategoryCode = defaultInc.code
                            }
                        },
                        label = { Text(stringResource(R.string.dialog_type_income)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.dialog_date_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dialog_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.dialog_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Dropdown
                val availableOptions = remember(customCategories, isIncomeType) {
                    val filtered = customCategories.filter { it.isIncome == isIncomeType }
                    if (filtered.isNotEmpty()) filtered else customCategories
                }

                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = if (currentCatObj.code.isNotBlank()) "${currentCatObj.code} - ${currentCatObj.name}" else currentCatObj.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.dialog_category_label)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(currentCatObj.parseColor())
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        availableOptions.forEach { cat ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(cat.parseColor())
                                    )
                                },
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("${cat.code} - ${cat.name}")
                                        Text(
                                            text = if (cat.isIncome) "[${stringResource(R.string.home_income)}]" else "[${stringResource(R.string.home_expense)}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCategoryCode = cat.code
                                    isIncomeType = cat.isIncome
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val income = if (isIncomeType) amount else null
                    val expense = if (!isIncomeType) amount else null
                    onConfirm(date, title, selectedCategoryCode, income, expense)
                },
                enabled = title.isNotBlank() && amountText.isNotBlank()
            ) {
                Text(stringResource(R.string.dialog_btn_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_btn_cancel))
            }
        }
    )
}
