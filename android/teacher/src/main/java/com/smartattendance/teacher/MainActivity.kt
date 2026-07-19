package com.smartattendance.teacher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartattendance.shared.data.PreferencesManager
import com.smartattendance.teacher.ui.attendance.LiveAttendanceScreen
import com.smartattendance.teacher.ui.attendance.LiveAttendanceViewModel
import com.smartattendance.teacher.ui.dashboard.DashboardViewModel
import com.smartattendance.teacher.ui.dashboard.TeacherDashboardScreen
import com.smartattendance.teacher.ui.login.TeacherLoginScreen
import com.smartattendance.teacher.ui.login.TeacherLoginViewModel
import com.smartattendance.teacher.ui.summary.SessionSummaryScreen
import com.smartattendance.teacher.ui.summary.SessionSummaryViewModel
import com.smartattendance.teacher.ui.theme.SmartAttendanceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            SmartAttendanceTheme {

                val preferencesManager = remember {
                    PreferencesManager(applicationContext)
                }

                val accessToken by preferencesManager.accessToken.collectAsState(initial = "")
                val navController = rememberNavController()

                val dashboardViewModel: DashboardViewModel = viewModel()
                val liveAttendanceViewModel: LiveAttendanceViewModel = viewModel()
                val summaryViewModel: SessionSummaryViewModel = viewModel()
                val loginViewModel: TeacherLoginViewModel = viewModel()

                LaunchedEffect(accessToken) {
                    val targetRoute = if (accessToken.isBlank()) {
                        TeacherRoute.LOGIN.route
                    } else {
                        TeacherRoute.DASHBOARD.route
                    }

                    navController.navigate(targetRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = TeacherRoute.LOGIN.route,
                ) {
                    composable(TeacherRoute.LOGIN.route) {
                        TeacherLoginScreen(
                            viewModel = loginViewModel,
                        )
                    }

                    composable(TeacherRoute.DASHBOARD.route) {
                        TeacherDashboardScreen(
                            viewModel = dashboardViewModel,
                            onViewAttendance = {
                                navController.navigate(TeacherRoute.LIVE_ATTENDANCE.route)
                            },
                            onViewSummary = {
                                summaryViewModel.loadSummary(dashboardViewModel.uiState.value.activeSessionId)
                                navController.navigate(TeacherRoute.SESSION_SUMMARY.route)
                            },
                            onLogout = {
                                dashboardViewModel.logout()
                            },
                        )
                    }

                    composable(TeacherRoute.LIVE_ATTENDANCE.route) {
                        LiveAttendanceScreen(
                            viewModel = liveAttendanceViewModel,
                            onBack = { navController.popBackStack() },
                            onFinishSession = { navController.popBackStack() },
                        )
                    }

                    composable(TeacherRoute.SESSION_SUMMARY.route) {
                        SessionSummaryScreen(
                            viewModel = summaryViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

            }

        }

    }

}

private enum class TeacherRoute(val route: String) {
    LOGIN("login"),
    DASHBOARD("dashboard"),
    LIVE_ATTENDANCE("live_attendance"),
    SESSION_SUMMARY("session_summary"),
}