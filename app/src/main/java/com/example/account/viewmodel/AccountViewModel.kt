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

/**
 * ViewModel for managing account-related operations using the SsoApiClient.
 * All account operations are performed via AIDL to the SSO service.
 * 
 * The SSO service connection is ON-DEMAND - it connects only when making a call.
 * No local storage is used - all data comes from the SSO service.
 */
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
    
    // Tracks if we've finished initial loading (fetching accounts from service)
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    init {
        // Fetch accounts from SSO service on startup (on-demand connection)
        fetchAccountsOnStartup()
    }
    
    private fun fetchAccountsOnStartup() {
        viewModelScope.launch {
            Log.d(TAG, "Fetching accounts from SSO service on startup...")
            try {
                // This will connect on-demand, get accounts, then we can check
                val accountsList = ssoApiClient.getAllAccounts()
                _accounts.value = accountsList
                
                val active = ssoApiClient.getActiveAccount()
                _activeAccount.value = active
                
                Log.d(TAG, "Startup fetch complete: ${accountsList.size} accounts, active: ${active?.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching accounts on startup", e)
            } finally {
                _isInitialized.value = true
            }
        }
    }
    
    /**
     * Perform login with username and password via AIDL service.
     * Connection is made on-demand.
     * Note: username is used as both email and name fields.
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            // Use username as both email and name
            val account = Account(
                email = username,
                name = username,
                isActive = true
            )
            
            val result = ssoApiClient.login(account)
            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success
                    refreshAccounts()
                },
                onFailure = { error ->
                    _loginState.value = LoginState.Error(error.message ?: "Login failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }
    
    /**
     * Perform logout for a specific account via AIDL service.
     * Connection is made on-demand.
     * @param accountIdentifier The ID or username of the account to logout
     */
    fun logout(accountIdentifier: String) {
        Log.d(TAG, "Logout called with identifier='$accountIdentifier'")
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            val result = ssoApiClient.logout(accountIdentifier)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Logged out account: $accountIdentifier")
                    _loginState.value = LoginState.Idle
                    refreshAccounts()
                },
                onFailure = { error ->
                    Log.e(TAG, "Logout failed: ${error.message}")
                    _loginState.value = LoginState.Error(error.message ?: "Logout failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }
    
    /**
     * Logout all accounts via AIDL service.
     * Connection is made on-demand.
     */
    fun logoutAll() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
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
    
    /**
     * Switch to a different account via AIDL service.
     * Connection is made on-demand.
     */
    fun switchAccount(account: Account) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            
            val result = ssoApiClient.switchAccount(account)
            result.fold(
                onSuccess = {
                    _loginState.value = LoginState.Success
                    refreshAccounts()
                },
                onFailure = { error ->
                    _loginState.value = LoginState.Error(error.message ?: "Switch account failed")
                    _errorMessage.value = error.message
                }
            )
        }
    }
    
    /**
     * Refresh the list of accounts from the AIDL service.
     * Connection is made on-demand.
     */
    suspend fun refreshAccounts() {
        Log.d(TAG, "Refreshing accounts from AIDL service")
        _accounts.value = ssoApiClient.getAllAccounts()
        _activeAccount.value = ssoApiClient.getActiveAccount()
        Log.d(TAG, "Accounts refreshed: ${_accounts.value.size} accounts, active: ${_activeAccount.value?.name}")
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Reset login state to idle.
     */
    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        ssoApiClient.unbind()
    }
}

/**
 * Represents the state of the login operation.
 */
sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
