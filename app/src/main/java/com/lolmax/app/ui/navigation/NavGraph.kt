package com.lolmax.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.lolmax.app.di.AppViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.lolmax.app.LolMaxApp
import com.lolmax.app.ui.exercises.ExerciseReportScreen
import com.lolmax.app.ui.exercises.ExercisesScreen
import com.lolmax.app.ui.profile.CalculatorScreen
import com.lolmax.app.ui.profile.PowerliftingScreen
import com.lolmax.app.ui.profile.ProfileScreen
import com.lolmax.app.ui.templates.TemplateEditScreen
import com.lolmax.app.ui.templates.TemplatesScreen
import com.lolmax.app.ui.workouts.LogWorkoutScreen
import com.lolmax.app.ui.workouts.WorkoutDetailScreen
import com.lolmax.app.ui.workouts.WorkoutsScreen

object Routes {
    const val WORKOUTS = "workouts"
    const val TEMPLATES = "templates"
    const val EXERCISES = "exercises"
    const val PROFILE = "profile"
    const val WORKOUT_DETAIL = "workout/{workoutId}"
    const val LOG_WORKOUT = "logWorkout?workoutId={workoutId}&templateId={templateId}"
    const val TEMPLATE_EDIT = "templateEdit?templateId={templateId}"
    const val EXERCISE_REPORT = "exercise/{exerciseId}"
    const val POWERLIFTING = "powerlifting"
    const val CALCULATOR = "calculator"

    fun workoutDetail(id: String) = "workout/$id"
    fun logWorkout(workoutId: String? = null, templateId: String? = null): String {
        val parts = mutableListOf<String>()
        workoutId?.let { parts += "workoutId=$it" }
        templateId?.let { parts += "templateId=$it" }
        val qs = parts.joinToString("&")
        return if (qs.isEmpty()) "logWorkout" else "logWorkout?$qs"
    }
    fun templateEdit(templateId: String? = null): String =
        if (templateId == null) "templateEdit" else "templateEdit?templateId=$templateId"
    fun exerciseReport(id: String) = "exercise/$id"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Routes.WORKOUTS, "Workouts", Icons.Filled.ListAlt),
    TopLevelDestination(Routes.TEMPLATES, "Templates", Icons.Filled.FitnessCenter),
    TopLevelDestination(Routes.EXERCISES, "Exercises", Icons.Filled.BarChart),
    TopLevelDestination(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

@Stable
class LolMaxAppState(val navController: NavHostController) {
    fun navigateToTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun rememberLolMaxAppState(navController: NavHostController): LolMaxAppState =
    remember(navController) { LolMaxAppState(navController) }

@Composable
fun LolMaxNavGraph(appState: LolMaxAppState) {
    val nav = appState.navController
    val context = LocalContext.current
    val container = (context.applicationContext as LolMaxApp).container
    val factory = remember(container) { AppViewModelFactory(container) }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (topLevelDestinations.any { it.route == currentRoute }) {
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { appState.navigateToTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.WORKOUTS,
            modifier = Modifier.padding(padding),
        ) {
        composable(Routes.WORKOUTS) {
            WorkoutsScreen(
                onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) },
                onStartWorkout = { nav.navigate(Routes.logWorkout()) },
                factory = factory,
            )
        }
        composable(
            Routes.WORKOUT_DETAIL,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) { entry ->
            WorkoutDetailScreen(
                workoutId = entry.arguments?.getString("workoutId") ?: "",
                onBack = { nav.popBackStack() },
                onEdit = { id -> nav.navigate(Routes.logWorkout(workoutId = id)) },
                onOpenExercise = { id -> nav.navigate(Routes.exerciseReport(id)) },
                factory = factory,
            )
        }
        composable(
            Routes.LOG_WORKOUT,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("templateId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            LogWorkoutScreen(
                workoutId = entry.arguments?.getString("workoutId"),
                templateId = entry.arguments?.getString("templateId"),
                onDone = { id ->
                    if (id == null) {
                        nav.popBackStack()
                    } else {
                        // Collapse any existing workout-detail entry so saving from an
                        // edit doesn't stack a duplicate detail screen.
                        nav.navigate(Routes.workoutDetail(id)) {
                            popUpTo(Routes.WORKOUT_DETAIL) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onBack = { nav.popBackStack() },
                factory = factory,
            )
        }
        composable(Routes.TEMPLATES) {
            TemplatesScreen(
                onOpenTemplate = { nav.navigate(Routes.templateEdit(it)) },
                onCreateTemplate = { nav.navigate(Routes.templateEdit()) },
                onStartTemplate = { id -> nav.navigate(Routes.logWorkout(templateId = id)) },
                factory = factory,
            )
        }
        composable(
            Routes.TEMPLATE_EDIT,
            arguments = listOf(
                navArgument("templateId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            TemplateEditScreen(
                templateId = entry.arguments?.getString("templateId"),
                onDone = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
                factory = factory,
            )
        }
        composable(Routes.EXERCISES) {
            ExercisesScreen(
                onOpenExercise = { nav.navigate(Routes.exerciseReport(it)) },
                factory = factory,
            )
        }
        composable(
            Routes.EXERCISE_REPORT,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            ExerciseReportScreen(
                exerciseId = entry.arguments?.getString("exerciseId") ?: "",
                onBack = { nav.popBackStack() },
                factory = factory,
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onOpenPowerlifting = { nav.navigate(Routes.POWERLIFTING) },
                onOpenCalculator = { nav.navigate(Routes.CALCULATOR) },
                factory = factory,
            )
        }
        composable(Routes.POWERLIFTING) {
            PowerliftingScreen(onBack = { nav.popBackStack() }, factory = factory)
        }
        composable(Routes.CALCULATOR) {
            CalculatorScreen(onBack = { nav.popBackStack() }, factory = factory)
        }
        }
    }
}
