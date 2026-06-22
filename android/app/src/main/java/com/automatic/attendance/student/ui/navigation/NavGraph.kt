package com.automatic.attendance.student.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.automatic.attendance.student.ui.screens.DashboardScreen
import com.automatic.attendance.student.ui.screens.LoginScreen
import com.automatic.attendance.student.ui.screens.SplashScreen
import com.automatic.attendance.student.ui.screens.AttendanceHistoryScreen
import com.automatic.attendance.student.ui.screens.CurrentAttendanceScreen
import com.automatic.attendance.student.ui.screens.ActiveSessionsScreen
import com.automatic.attendance.student.ui.screens.SessionDetailScreen

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val ACTIVE_SESSIONS = "active_sessions"
    const val SESSION_DETAIL = "session_detail/{sessionId}"
    const val ATTENDANCE_HISTORY = "attendance_history"
    const val CURRENT_ATTENDANCE = "current_attendance/{sessionId}"
}

@Composable
fun AppNavHost(modifier: Modifier = Modifier, start: String = Routes.SPLASH, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = start, modifier = modifier) {
        composable(Routes.SPLASH) { SplashScreen(navController) }
        composable(Routes.LOGIN) { LoginScreen(navController) }
        composable(Routes.DASHBOARD) { DashboardScreen(navController) }
        composable(Routes.ACTIVE_SESSIONS) { ActiveSessionsScreen(navController) }
        composable(Routes.SESSION_DETAIL) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            SessionDetailScreen(navController, sessionId)
        }
        composable(Routes.ATTENDANCE_HISTORY) { AttendanceHistoryScreen(navController) }
        composable(Routes.CURRENT_ATTENDANCE) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            CurrentAttendanceScreen(navController, sessionId)
        }
    }
}
