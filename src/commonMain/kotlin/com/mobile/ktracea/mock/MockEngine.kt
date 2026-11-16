package com.mobile.ktracea.mock

import com.mobile.ktracea.model.MockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MockEngine {
    private val mutex = Mutex()
    private val _rules = MutableStateFlow<List<MockRule>>(emptyList())
    val rules: StateFlow<List<MockRule>> = _rules.asStateFlow()

    suspend fun addRule(rule: MockRule) {
        mutex.withLock {
            val current = _rules.value.toMutableList()
            current.removeAll { it.id == rule.id }
            current.add(0, rule)
            _rules.value = current
        }
    }

    suspend fun removeRule(id: String) {
        mutex.withLock {
            _rules.value = _rules.value.filterNot { it.id == id }
        }
    }

    suspend fun toggleRule(id: String) {
        mutex.withLock {
            _rules.value = _rules.value.map {
                if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
            }
        }
    }

    suspend fun clearRules() {
        mutex.withLock {
            _rules.value = emptyList()
        }
    }

    fun findMatchingRule(url: String, method: String): MockRule? {
        val currentRules = _rules.value
        return currentRules.firstOrNull { it.matches(url, method) }
    }
}
