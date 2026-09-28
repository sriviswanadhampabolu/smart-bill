package com.grocer.billing.feature.account

import androidx.compose.runtime.Composable
import com.grocer.billing.core.data.repository.AuthRepository

/**
 * ProfileScreen facade linking to AccountDashboardScreen.
 */
@Composable
fun ProfileScreen(
    authRepository: AuthRepository,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit
) {
    AccountDashboardScreen(
        authRepository = authRepository,
        onNavigateBack = onNavigateBack,
        onLogout = onLogout
    )
}
