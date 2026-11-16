package com.mobile.ktracea.storage

import com.mobile.ktracea.model.HttpTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransactionRepository(
    private val maxCapacity: Int = 500
) {
    private val mutex = Mutex()
    private val _transactions = MutableStateFlow<List<HttpTransaction>>(emptyList())
    val transactions: StateFlow<List<HttpTransaction>> = _transactions.asStateFlow()

    suspend fun recordOrUpdate(transaction: HttpTransaction) {
        mutex.withLock {
            val list = _transactions.value.toMutableList()
            val existingIndex = list.indexOfFirst { it.id == transaction.id }
            if (existingIndex >= 0) {
                list[existingIndex] = transaction
            } else {
                list.add(0, transaction)
                if (list.size > maxCapacity) {
                    list.removeAt(list.lastIndex)
                }
            }
            _transactions.value = list
        }
    }

    suspend fun clear() {
        mutex.withLock {
            _transactions.value = emptyList()
        }
    }

    fun getById(id: String): HttpTransaction? {
        return _transactions.value.firstOrNull { it.id == id }
    }
}
