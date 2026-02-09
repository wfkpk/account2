package com.example.account.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ssoapi.Account
import com.example.ssoapi.SsoApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AccountViewModel"
    }

    private val ssoApiClient = SsoApiClient(application)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<Account?>(null)
    val activeAccount: StateFlow<Account?> = _activeAccount.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        fetchAccountsOnStartup()
    }

    private fun fetchAccountsOnStartup() {
        viewModelScope.launch {
            Log.d(TAG, "Fetching accounts from SSO service on startup...")
            try {
                val accountsList = ssoApiClient.getAllAccounts()
                _accounts.value = accountsList

                val active = ssoApiClient.getActiveAccount()
                _activeAccount.value = active

                Log.d(TAG, "Startup fetch complete: ${accountsList.size} accounts, active: ${active?.mail}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching accounts on startup", e)
            } finally {
                _isInitialized.value = true
            }
        }
    }

    fun login(mail: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            _errorMessage.value = null

            val result = ssoApiClient.login(mail, password)
            result.fold(
                onSuccess = { account ->
                    Log.d(TAG, "Login successful for: ${account.mail}")
                    // Use the returned account directly to update state,
                    // since the service may not have persisted it yet when we query
                    val activeAccount = account.copy(isActive = true)
                    _activeAccount.value = activeAccount
                    val currentAccounts = _accounts.value.toMutableList()
                    currentAccounts.removeAll { it.guid == account.guid }
                    currentAccounts.add(activeAccount)
                    _accounts.value = currentAccounts
                    _loginState.value = LoginState.Success
                },
                onFailure = { error ->
                    Log.e(TAG, "Login failed: ${error.message}")
                    _loginState.value = LoginState.Error(error.message ?: "Login failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }

    fun register(mail: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            _errorMessage.value = null

            val result = ssoApiClient.register(mail, password)
            result.fold(
                onSuccess = { account ->
                    Log.d(TAG, "Registration successful for: ${account.mail}")
                    val activeAccount = account.copy(isActive = true)
                    _activeAccount.value = activeAccount
                    val currentAccounts = _accounts.value.toMutableList()
                    currentAccounts.removeAll { it.guid == account.guid }
                    currentAccounts.add(activeAccount)
                    _accounts.value = currentAccounts
                    _loginState.value = LoginState.Success
                },
                onFailure = { error ->
                    Log.e(TAG, "Registration failed: ${error.message}")
                    _loginState.value = LoginState.Error(error.message ?: "Registration failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }

    fun logout(guid: String) {
        Log.d(TAG, "Logout called with guid='$guid'")
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            _errorMessage.value = null

            val result = ssoApiClient.logout(guid)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Logged out account: $guid")
                    refreshAccounts()
                    _loginState.value = LoginState.Idle
                },
                onFailure = { error ->
                    Log.e(TAG, "Logout failed: ${error.message}")
                    _loginState.value = LoginState.Error(error.message ?: "Logout failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            _errorMessage.value = null

            val result = ssoApiClient.logoutAll()
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Logged out all accounts")
                    _activeAccount.value = null
                    _accounts.value = emptyList()
                    _loginState.value = LoginState.Idle
                },
                onFailure = { error ->
                    _loginState.value = LoginState.Error(error.message ?: "Logout all failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }

    fun switchAccount(guid: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            _errorMessage.value = null

            val result = ssoApiClient.switchAccount(guid)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Switched to account: $guid")
                    refreshAccounts()
                    _loginState.value = LoginState.Idle
                },
                onFailure = { error ->
                    _loginState.value = LoginState.Error(error.message ?: "Switch account failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }

    private suspend fun refreshAccounts() {
        Log.d(TAG, "Refreshing accounts from AIDL service")
        _accounts.value = ssoApiClient.getAllAccounts()
        _activeAccount.value = ssoApiClient.getActiveAccount()
        Log.d(TAG, "Accounts refreshed: ${_accounts.value.size} accounts, active: ${_activeAccount.value?.mail}")
    }

    fun clearError() {
        _errorMessage.value = null
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Idle
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ssoApiClient.unbind()
    }
}

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
