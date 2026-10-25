package com.hanyo.apiquota.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hanyo.apiquota.AppContainer
import com.hanyo.apiquota.data.model.Account
import com.hanyo.apiquota.data.model.QuotaResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuotaViewModel(private val container: AppContainer) : ViewModel() {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _results = MutableStateFlow<Map<String, QuotaResult>>(emptyMap())
    val results: StateFlow<Map<String, QuotaResult>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val accs = container.accountStore.getAccounts().filter { it.enabled }
                _accounts.value = accs
                _results.value = container.repository.fetchAll(accs)
            } finally {
                _loading.value = false
                _lastUpdated.value = System.currentTimeMillis()
            }
        }
    }

    fun addAccount(account: Account) {
        container.accountStore.add(account)
        refresh()
    }

    fun deleteAccount(id: String) {
        container.accountStore.remove(id)
        refresh()
    }

    fun updateAccount(account: Account) {
        container.accountStore.update(account)
        refresh()
    }

    companion object {
        fun Factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                QuotaViewModel(container) as T
        }
    }
}
