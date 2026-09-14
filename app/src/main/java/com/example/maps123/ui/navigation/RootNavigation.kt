
package com.example.maps123.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.maps123.MainActivity
import com.example.maps123.data.local.AppDatabase
import com.example.maps123.data.repository.AuthRepository
import com.example.maps123.data.session.SessionManager
import com.example.maps123.data.supabase.PasswordRecoveryManager
import com.example.maps123.ui.MainViewModel
import com.example.maps123.ui.screens.HomeScreen
import com.example.maps123.ui.screens.LoginScreen
import com.example.maps123.ui.screens.RegisterScreen
import com.example.maps123.ui.screens.AnnouncementDetailScreen
import androidx.navigation.navDeepLink
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.example.maps123.ui.screens.UpdatePasswordDialog

@Composable
fun RootNavigation(
    viewModel: MainViewModel,
    pendingChatId: String? = null,
    pendingPostId: String? = null,
    pendingOpenFriends: Boolean = false
) {
    val context = LocalContext.current
    val sessionManager = remember(context) { SessionManager(context) }
    val scope = rememberCoroutineScope()
    val passwordUpdateRequired by PasswordRecoveryManager.isPasswordUpdateRequired.collectAsState()
    val navController = rememberNavController()
    val startDestination = if (sessionManager.hasActiveSession()) {
        "home"
    } else {
        "login"
    }

    LaunchedEffect(pendingPostId) {
        if (!pendingPostId.isNullOrBlank() && sessionManager.hasActiveSession()) {
            navController.navigate("post/$pendingPostId") { launchSingleTop = true }
            (context as? MainActivity)?.consumeDeepLinkPostId()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        composable("login") {
            LoginScreen(
                onSuccess = { email ->
                    sessionManager.saveEmail(email)
                    viewModel.resetChatSession()
                    AppDatabase.clearInstance()
                    viewModel.initializeChat()
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onContinueAsGuest = {
                    sessionManager.saveGuestSession()
                    AppDatabase.clearInstance()
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGoRegister = {
                    navController.navigate("register")
                }
            )
        }

        composable("register") {
            RegisterScreen(
                onRegistered = { email ->
                    sessionManager.saveEmail(email)
                    viewModel.resetChatSession()
                    AppDatabase.clearInstance()
                    viewModel.initializeChat()
                    navController.navigate("home") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                isGuest = sessionManager.isGuestSession(),
                pendingChatId = pendingChatId,
                pendingOpenFriends = pendingOpenFriends,
                onLoginRequested = {
                    scope.launch {
                        sessionManager.clearSession()
                        AppDatabase.clearInstance()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                },
                onLogout = {
                    scope.launch {
                        sessionManager.clearSession()
                        viewModel.resetChatSession()
                        val intent = Intent(context, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    }
                }
            )
        }

        composable(
            route = "post/{postId}",
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://maps123.example.com/post/{postId}" },
                navDeepLink { uriPattern = "maps123://post/{postId}" }
            ),
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId")
            if (postId != null) {
                AnnouncementDetailScreen(
                    announcementId = postId,
                    repo = viewModel.announcementRepository,
                    chatRepository = viewModel.chatRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (passwordUpdateRequired) {
        UpdatePasswordDialog(
            onPasswordUpdated = {
                PasswordRecoveryManager.completePasswordUpdate()
                sessionManager.saveEmail(AuthRepository.currentUserEmail().orEmpty())
                viewModel.resetChatSession()
                AppDatabase.clearInstance()
                viewModel.initializeChat()
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            }
        )
    }
}
