package com.example.data.repository

import com.example.data.dao.TransactionDao
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TransactionRepository(private val dao: TransactionDao) {

    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()

    suspend fun insertTransaction(date: String, title: String, category: String, income: Double?, expense: Double?): Long {
        val currentMax = dao.getMaxItemNo() ?: 0
        val nextItemNo = currentMax + 1

        // Calculate latest running subtotal
        val existing = dao.getAllTransactions().first()
        val prevSubtotal = existing.lastOrNull()?.subtotal ?: 0.0
        val inc = income ?: 0.0
        val exp = expense ?: 0.0
        val newSubtotal = prevSubtotal + inc - exp

        val entity = TransactionEntity(
            itemNo = nextItemNo,
            date = date,
            title = title,
            category = category,
            income = income,
            expense = expense,
            subtotal = newSubtotal,
            isSynced = false
        )
        val id = dao.insertTransaction(entity)
        recalculateSubtotals()
        return id
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        dao.updateTransaction(transaction)
        recalculateSubtotals()
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        dao.deleteTransaction(transaction)
        recalculateSubtotals()
    }

    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        dao.deleteAll()
        if (transactions.isNotEmpty()) {
            dao.insertAll(transactions)
        }
        recalculateSubtotals()
    }

    suspend fun clearAll() {
        dao.deleteAll()
    }

    private suspend fun recalculateSubtotals() {
        val list = dao.getAllTransactions().first()
        var runningSubtotal = 0.0
        val updated = mutableListOf<TransactionEntity>()
        list.forEachIndexed { index, item ->
            val inc = item.income ?: 0.0
            val exp = item.expense ?: 0.0
            runningSubtotal += (inc - exp)
            val newItemNo = index + 1
            if (item.subtotal != runningSubtotal || item.itemNo != newItemNo) {
                updated.add(item.copy(itemNo = newItemNo, subtotal = runningSubtotal))
            }
        }
        if (updated.isNotEmpty()) {
            dao.insertAll(updated)
        }
    }

    suspend fun checkAndSeedInitialData() {
        // No longer seeding fake data. DB remains empty for new users.
    }

    private fun getSampleData(): List<TransactionEntity> {
        return emptyList()
    }
}
