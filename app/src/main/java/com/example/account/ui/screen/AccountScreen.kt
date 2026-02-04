package com.example.account.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.account.viewmodel.AccountViewModel
import com.example.account.viewmodel.LoginState
import com.example.ssoapi.Account

@Composable
fun AccountScreen(
    viewModel: AccountViewModel? = null,
    onLogout: () -> Unit = {},
    onLogoutAll: () -> Unit = {},
    onAddAccount: () -> Unit = {}
) {
    val accounts by viewModel?.accounts?.collectAsState() ?: androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(emptyList<Account>()) 
    }
    val activeAccount by viewModel?.activeAccount?.collectAsState() ?: androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf<Account?>(null) 
    }
    val loginState by viewModel?.loginState?.collectAsState() ?: androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf<LoginState>(LoginState.Idle) 
    }
    val isLoading = loginState is LoginState.Loading
    val maxAccounts = 6
    val canAddMoreAccounts = accounts.size < maxAccounts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Accounts",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Active account indicator with Logout button
        activeAccount?.let { active ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active Account",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = active.name.ifEmpty { active.email.ifEmpty { "Unknown" } },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (active.email.isNotEmpty()) {
                            Text(
                                text = active.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    // Logout active account button
                    OutlinedButton(
                        onClick = {
                            val identifier = active.email.ifEmpty { active.name }
                            viewModel?.logout(identifier)
                            onLogout()
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Logout")
                    }
                }
            }
        }
        
        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        // Switch Account section - only show if more than 1 account
        val otherAccounts = accounts.filter { account ->
            val accountIdentifier = account.email.ifEmpty { account.name }
            val activeIdentifier = activeAccount?.let { it.email.ifEmpty { it.name } }
            accountIdentifier != activeIdentifier
        }
        
        if (otherAccounts.isNotEmpty()) {
            Text(
                text = "Switch Account",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = otherAccounts,
                    key = { account -> 
                        account.email.ifEmpty { account.name }.ifEmpty { otherAccounts.indexOf(account).toString() }
                    }
                ) { account ->
                    AccountSwitchItem(
                        account = account,
                        onSwitchAccount = { 
                            viewModel?.switchAccount(account)
                        },
                        isLoading = isLoading
                    )
                }
            }
        } else if (accounts.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No accounts found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Only 1 account or loading, show spacer
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Account count indicator
        Text(
            text = "${accounts.size}/$maxAccounts accounts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Login button (to add new account)
        Button(
            onClick = onAddAccount,
            modifier = Modifier.fillMaxWidth(),
            enabled = canAddMoreAccounts && !isLoading
        ) {
            Text(if (canAddMoreAccounts) "Login" else "Maximum accounts reached (6)")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Logout All button
        OutlinedButton(
            onClick = { 
                viewModel?.logoutAll()
                onLogoutAll()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = accounts.isNotEmpty() && !isLoading,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Logout All Accounts")
        }
    }
}

/**
 * Account item for the switch account list.
 * Only shows account info and a Switch button.
 */
@Composable
fun AccountSwitchItem(
    account: Account,
    onSwitchAccount: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = !isLoading) { onSwitchAccount() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    val displayChar = account.name.firstOrNull() 
                        ?: account.email.firstOrNull() 
                        ?: '?'
                    Text(
                        text = displayChar.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
                
                Column(
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        text = account.name.ifEmpty { account.email.ifEmpty { "Unknown" } },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (account.email.isNotEmpty() && account.email != account.name) {
                        Text(
                            text = account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Switch button
            Button(
                onClick = onSwitchAccount,
                enabled = !isLoading
            ) {
                Text("Switch")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AccountScreenPreview() {
    AccountScreen(viewModel = null)
}
