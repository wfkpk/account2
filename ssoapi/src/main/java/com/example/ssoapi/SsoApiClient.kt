package com.example.ssoapi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * SsoApiClient is the main entry point for interacting with the SSO Service.
 * It handles binding to the external middleware service (com.example.service)
 * and provides methods for login, logout, and account switching.
 * 
 * This client uses ON-DEMAND connection - it connects when a call is made,
 * not maintaining a persistent connection.
 */
class SsoApiClient(private val context: Context) {
    
    companion object {
        private const val TAG = "SsoApiClient"
        private const val SSO_SERVICE_PACKAGE = "com.example.service"
        private const val SSO_SERVICE_CLASS = "com.example.service.SsoService"
        private const val SSO_SERVICE_ACTION = "com.example.service.SSO_SERVICE"
        private const val CONNECTION_TIMEOUT_MS = 5000L
    }
    
    private var ssoService: sso? = null
    private var isBound = false
    private var pendingConnection: ((Boolean) -> Unit)? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected: $name")
            ssoService = sso.Stub.asInterface(service)
            isBound = true
            pendingConnection?.invoke(true)
            pendingConnection = null
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected: $name")
            ssoService = null
            isBound = false
        }
    }
    
    /**
     * Connect to the SSO Service on-demand.
     * This is a suspend function that waits for the connection to be established.
     * @return true if connected successfully, false otherwise
     */
    private suspend fun ensureConnected(): Boolean = withContext(Dispatchers.Main) {
        // Already connected
        if (isBound && ssoService != null) {
            Log.d(TAG, "Already connected to service")
            return@withContext true
        }
        
        val intent = Intent(SSO_SERVICE_ACTION).apply {
            setPackage(SSO_SERVICE_PACKAGE)
            setClassName(SSO_SERVICE_PACKAGE, SSO_SERVICE_CLASS)
        }
        
        try {
            // Use suspendCancellableCoroutine to wait for connection
            val connected = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    pendingConnection = { success ->
                        if (continuation.isActive) {
                            continuation.resume(success)
                        }
                    }
                    
                    val bindResult = try {
                        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Cannot bind to SSO service (SecurityException): ${e.message}")
                        false
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to bind to service: ${e.message}")
                        false
                    }
                    
                    if (!bindResult) {
                        pendingConnection = null
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                    
                    continuation.invokeOnCancellation {
                        pendingConnection = null
                    }
                }
            }
            
            if (connected == true) {
                Log.d(TAG, "Successfully connected to SSO service")
                return@withContext true
            } else {
                Log.w(TAG, "Connection timeout or failed")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to service", e)
            return@withContext false
        }
    }
    
    /**
     * Unbind from the SSO Service. Call this when done using the client.
     */
    fun unbind() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service", e)
            }
            isBound = false
            ssoService = null
        }
    }
    
    /**
     * Check if the client is currently bound to the service.
     */
    fun isConnected(): Boolean = isBound && ssoService != null
    
    /**
     * Login with the given account credentials.
     * This calls the external service which will make the server call.
     * Connects on-demand if not already connected.
     * @param account The account to login with
     * @return Result indicating success or failure
     */
    suspend fun login(account: Account): Result<Unit> {
        // Connect on-demand
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Could not connect to SSO Service. Please ensure the service is installed."))
        }
        
        return withContext(Dispatchers.IO) {
            val service = ssoService
            if (service == null) {
                return@withContext Result.failure(IllegalStateException("Service not connected."))
            }
            
            try {
                service.login(account)
                Log.d(TAG, "Login call completed for account: ${account.name}")
                Result.success(Unit)
            } catch (e: RemoteException) {
                Log.e(TAG, "Login failed with RemoteException", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Logout a specific account by ID or username.
     * Connects on-demand if not already connected.
     * @param accountIdentifier The ID or username of the account to logout
     * @return Result indicating success or failure
     */
    suspend fun logout(accountIdentifier: String): Result<Unit> {
        Log.d(TAG, "Logout called with identifier='$accountIdentifier'")
        
        // Connect on-demand
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Could not connect to SSO Service."))
        }
        
        return withContext(Dispatchers.IO) {
            val service = ssoService
            if (service == null) {
                return@withContext Result.failure(IllegalStateException("Service not connected."))
            }
            
            try {
                Log.d(TAG, "Calling service.logout('$accountIdentifier')")
                service.logout(accountIdentifier)
                Log.d(TAG, "Logout call completed for: $accountIdentifier")
                Result.success(Unit)
            } catch (e: RemoteException) {
                Log.e(TAG, "Logout failed with RemoteException", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Logout all accounts.
     * Connects on-demand if not already connected.
     * @return Result indicating success or failure
     */
    suspend fun logoutAll(): Result<Unit> {
        // Connect on-demand
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Could not connect to SSO Service."))
        }
        
        return withContext(Dispatchers.IO) {
            val service = ssoService
            if (service == null) {
                return@withContext Result.failure(IllegalStateException("Service not connected."))
            }
            
            try {
                service.logoutAll()
                Log.d(TAG, "Logout all accounts completed")
                Result.success(Unit)
            } catch (e: RemoteException) {
                Log.e(TAG, "Logout all failed with RemoteException", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Logout all failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Switch to a different account.
     * Connects on-demand if not already connected.
     * @param account The account to switch to
     * @return Result indicating success or failure
     */
    suspend fun switchAccount(account: Account): Result<Unit> {
        // Connect on-demand
        if (!ensureConnected()) {
            return Result.failure(IllegalStateException("Could not connect to SSO Service."))
        }
        
        return withContext(Dispatchers.IO) {
            val service = ssoService
            if (service == null) {
                return@withContext Result.failure(IllegalStateException("Service not connected."))
            }
            
            try {
                service.switchAccount(account)
                Log.d(TAG, "Switch account call completed for account: ${account.name}")
                Result.success(Unit)
            } catch (e: RemoteException) {
                Log.e(TAG, "Switch account failed with RemoteException", e)
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Switch account failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get the currently active account.
     * Connects on-demand if not already connected.
     * @return The active account, or null if no account is active or service unavailable
     */
    suspend fun getActiveAccount(): Account? {
        // Connect on-demand
        if (!ensureConnected()) {
            Log.w(TAG, "Could not connect to get active account")
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val account = ssoService?.activeAccount
                if (account != null) {
                    Log.d(TAG, "Active account: email='${account.email}', name='${account.name}'")
                } else {
                    Log.d(TAG, "No active account")
                }
                account
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to get active account", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get active account", e)
                null
            }
        }
    }
    
    /**
     * Get all logged-in accounts.
     * Connects on-demand if not already connected.
     * @return List of all accounts, or empty list if none or service unavailable
     */
    suspend fun getAllAccounts(): List<Account> {
        // Connect on-demand
        if (!ensureConnected()) {
            Log.w(TAG, "Could not connect to get all accounts")
            return emptyList()
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val rawAccounts = ssoService?.allAccounts
                // Filter out any null accounts from the list
                val accounts = rawAccounts?.filterNotNull() ?: emptyList()
                Log.d(TAG, "Got ${accounts.size} accounts from service")
                // Log each account for debugging
                accounts.forEachIndexed { index, account ->
                    Log.d(TAG, "Account[$index]: email='${account.email}', name='${account.name}', isActive=${account.isActive}")
                }
                accounts
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to get all accounts", e)
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get all accounts", e)
                emptyList()
            }
        }
    }
}
