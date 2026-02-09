package com.example.account

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.account.ui.screen.AccountScreen
import com.example.account.ui.screen.LoginScreen
import com.example.account.ui.theme.AccountTheme
import com.example.account.viewmodel.AccountViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AccountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AccountTheme {
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: AccountViewModel) {
    val navController = rememberNavController()
    val accounts by viewModel.accounts.collectAsState()
    val isInitialized by viewModel.isInitialized.collectAsState()

    if (!isInitialized) {
        LoadingScreen()
        return
    }

    val startDestination = if (accounts.isNotEmpty()) "accounts" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("accounts") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("accounts") {
            LaunchedEffect(accounts) {
                if (accounts.isEmpty()) {
                    navController.navigate("login") {
                        popUpTo("accounts") { inclusive = true }
                    }
                }
            }

            AccountScreen(
                viewModel = viewModel,
                onLogout = {},
                onLogoutAll = {},
                onAddAccount = {
                    viewModel.resetLoginState()
                    navController.navigate("login")
                }
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting to SSO Service...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
