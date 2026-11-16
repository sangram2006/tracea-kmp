package com.mobile.ktracea

import com.mobile.ktracea.model.HttpTransaction
import com.mobile.ktracea.model.KTraceaConfig
import com.mobile.ktracea.storage.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object KTracea {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var _config = KTraceaConfig()
    private var _repository = TransactionRepository(_config.maxTransactions)

    val config: KTraceaConfig
        get() = _config

    val repository: TransactionRepository
        get() = _repository

    fun configure(config: KTraceaConfig = KTraceaConfig()) {
        _config = config
        _repository = TransactionRepository(config.maxTransactions)
    }

    fun recordTransaction(transaction: HttpTransaction) {
        scope.launch {
            _repository.recordOrUpdate(transaction)
        }
    }

    fun clearAll() {
        scope.launch {
            _repository.clear()
        }
    }
}
