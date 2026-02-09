# SSO API Library (ssoapi.aar)

## Overview

The SSO API library provides an Android client for communicating with the SSO Service (`com.example.service`) via AIDL (Android Interface Definition Language). It handles service binding, authentication callbacks, and account management through a clean coroutine-based API.

## Building the .aar

Run the following Gradle command from the project root:

```bash
./gradlew :ssoapi:assembleRelease
```

The `.aar` file will be generated at:

```
ssoapi/build/outputs/aar/ssoapi-release.aar
```

For a debug build:

```bash
./gradlew :ssoapi:assembleDebug
```

Output: `ssoapi/build/outputs/aar/ssoapi-debug.aar`

## Integration

### 1. Add the .aar to your project

Copy `ssoapi-release.aar` into your app's `libs/` directory, then add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/ssoapi-release.aar"))

    // Required transitive dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 2. Manifest queries (required for Android 11+)

The library's manifest already includes the required `<queries>` tag for binding to `com.example.service`. If you are using manifest merger (default), this is handled automatically.

If not, add to your app's `AndroidManifest.xml`:

```xml
<queries>
    <package android:name="com.example.service" />
</queries>
```

### 3. Prerequisites

The SSO Service app (`com.example.service`) must be installed on the device. The library communicates with it via AIDL IPC.

## API Reference

### SsoApiClient

Main entry point. Create an instance with any Android `Context`:

```kotlin
val ssoClient = SsoApiClient(context)
```

All methods are `suspend` functions and must be called from a coroutine scope.

---

### Authentication Methods

#### `login(mail: String, password: String): Result<Account>`

Authenticates a user with email and password. On success, the account is saved to the SSO Service's database and set as the active account.

```kotlin
val result = ssoClient.login("user@example.com", "password123")
result.fold(
    onSuccess = { account ->
        // account.guid, account.mail, account.sessionToken available
    },
    onFailure = { error ->
        // error.message contains the failure reason
    }
)
```

#### `register(mail: String, password: String): Result<Account>`

Registers a new user account. On success, the account is saved and set as active.

```kotlin
val result = ssoClient.register("newuser@example.com", "password123")
result.fold(
    onSuccess = { account -> /* registered */ },
    onFailure = { error -> /* registration failed */ }
)
```

---

### Account Management Methods

#### `logout(guid: String): Result<Unit>`

Signs out a specific account by its GUID. Removes it from the SSO Service's database and Android AccountManager.

```kotlin
val result = ssoClient.logout(account.guid)
```

#### `logoutAll(): Result<Unit>`

Signs out all accounts. Clears the SSO Service's database and AccountManager entries.

```kotlin
val result = ssoClient.logoutAll()
```

#### `switchAccount(guid: String): Result<Unit>`

Sets a different stored account as the active account.

```kotlin
val result = ssoClient.switchAccount(targetAccount.guid)
```

#### `getActiveAccount(): Account?`

Returns the currently active account, or `null` if none.

```kotlin
val active = ssoClient.getActiveAccount()
```

#### `getAllAccounts(): List<Account>`

Returns all signed-in accounts. Returns an empty list if none or if the service is unavailable.

```kotlin
val accounts = ssoClient.getAllAccounts()
```

---

### Token Methods (Advanced)

#### `fetchToken(mail: String, password: String): Result<Account>`

Fetches only the authentication token without saving anything. Returns an `Account` with `guid` and `sessionToken` populated.

```kotlin
val result = ssoClient.fetchToken("user@example.com", "password123")
```

#### `fetchAccountInfo(guid: String, sessionToken: String): Result<Account>`

Fetches full account information for a given GUID and session token without saving anything.

```kotlin
val result = ssoClient.fetchAccountInfo(guid, sessionToken)
```

---

### Lifecycle Management

#### `unbind()`

Unbinds from the SSO Service. Call this when you are done using the client (e.g., in `ViewModel.onCleared()` or `Activity.onDestroy()`).

```kotlin
override fun onCleared() {
    super.onCleared()
    ssoClient.unbind()
}
```

#### `isConnected(): Boolean`

Returns whether the client is currently bound to the SSO Service.

---

## Data Models

### Account

```kotlin
data class Account(
    val guid: String,          // Unique identifier
    val mail: String,          // Email address
    val profileImage: String?, // Optional profile image URL
    val sessionToken: String,  // Authentication session token
    val isActive: Boolean      // Whether this is the active account
) : Parcelable
```

### AuthResult

```kotlin
data class AuthResult(
    val success: Boolean,  // Operation succeeded
    val fail: Boolean,     // Operation failed
    val message: String?   // Error details (when fail=true)
) : Parcelable
```

---

## AIDL Interface

The library defines the following AIDL interface (`sso.aidl`) for communication with the service:

| Method                                    | Type     | Description           |
| ----------------------------------------- | -------- | --------------------- |
| `login(mail, password, callback)`         | Callback | Authenticate user     |
| `register(mail, password, callback)`      | Callback | Register new user     |
| `fetchToken(mail, password, callback)`    | Callback | Get token only        |
| `fetchAccountInfo(guid, token, callback)` | Callback | Get account info      |
| `logout(guid)`                            | Direct   | Sign out account      |
| `logoutAll()`                             | Direct   | Sign out all accounts |
| `switchAccount(guid)`                     | Direct   | Switch active account |
| `getActiveAccount()`                      | Direct   | Get active account    |
| `getAllAccounts()`                        | Direct   | Get all accounts      |

---

## Connection Behavior

- **On-demand binding**: The client connects to the SSO Service only when a method is called, not on instantiation.
- **Connection timeout**: 5 seconds. If the service is not available, methods return failure results.
- **Callback timeout**: 30 seconds for callback-based operations (login, register, fetchToken, fetchAccountInfo).
- **Auto-reconnect**: If a previous connection was lost, the client will attempt to reconnect on the next call.

## Error Handling

All methods return `Result<T>` (Kotlin stdlib). Common failure scenarios:

| Error                                | Cause                                        |
| ------------------------------------ | -------------------------------------------- |
| `Could not connect to SSO Service`   | SSO Service app not installed or not running |
| `Service not connected`              | Service disconnected mid-operation           |
| `Login timeout` / `Register timeout` | Service didn't respond within 30 seconds     |
| `RemoteException`                    | IPC communication failure                    |

## Example: ViewModel Integration

```kotlin
class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val ssoClient = SsoApiClient(application)

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            val result = ssoClient.login(email, password)
            result.fold(
                onSuccess = { account ->
                    // Update UI with signed-in account
                },
                onFailure = { error ->
                    // Show error to user
                }
            )
        }
    }

    fun getAccounts() {
        viewModelScope.launch {
            val accounts = ssoClient.getAllAccounts()
            // Update UI with accounts list
        }
    }

    override fun onCleared() {
        super.onCleared()
        ssoClient.unbind()
    }
}
```

## Module Structure

```
ssoapi/
├── build.gradle.kts
├── consumer-rules.pro
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── aidl/com/example/ssoapi/
    │   ├── Account.aidl
    │   ├── AuthResult.aidl
    │   ├── IAuthCallback.aidl
    │   └── sso.aidl
    └── java/com/example/ssoapi/
        ├── Account.kt
        ├── AuthResult.kt
        └── SsoApiClient.kt
```
