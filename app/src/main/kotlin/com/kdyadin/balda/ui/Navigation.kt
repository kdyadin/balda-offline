package com.kdyadin.balda.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kdyadin.balda.ui.game.GameViewModel
import com.kdyadin.balda.ui.screens.AboutScreen
import com.kdyadin.balda.ui.screens.GameScreen
import com.kdyadin.balda.ui.screens.MenuScreen
import com.kdyadin.balda.ui.screens.NewGameScreen
import com.kdyadin.balda.ui.screens.ResultsScreen
import com.kdyadin.balda.ui.screens.RulesScreen
import com.kdyadin.balda.ui.screens.SettingsScreen

object Routes {
    const val MENU = "menu"
    const val NEW_GAME = "new_game"
    const val GAME = "game"
    const val RESULTS = "results"
    const val SETTINGS = "settings"
    const val RULES = "rules"
    const val ABOUT = "about"
}

/** ViewModel партии живёт на уровне Activity: одна партия за раз, доступна и на экране игры, и на итогах. */
@Composable
fun activityGameViewModel(): GameViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(viewModelStoreOwner = activity, factory = GameViewModel.factory(activity.application))
}

@Composable
fun BaldaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MenuScreen(
                onContinue = { navController.navigate(Routes.GAME) },
                onNewGame = { navController.navigate(Routes.NEW_GAME) },
                onRules = { navController.navigate(Routes.RULES) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.NEW_GAME) {
            NewGameScreen(
                onBack = { navController.popBackStack() },
                onGameStarted = {
                    navController.navigate(Routes.GAME) {
                        popUpTo(Routes.MENU)
                    }
                },
            )
        }
        composable(Routes.GAME) {
            GameScreen(
                onExitToMenu = {
                    navController.navigate(Routes.MENU) { popUpTo(Routes.MENU) { inclusive = true } }
                },
                onFinished = {
                    navController.navigate(Routes.RESULTS) { popUpTo(Routes.MENU) }
                },
            )
        }
        composable(Routes.RESULTS) {
            ResultsScreen(
                onPlayAgain = {
                    navController.navigate(Routes.GAME) { popUpTo(Routes.MENU) }
                },
                onMenu = {
                    navController.navigate(Routes.MENU) { popUpTo(Routes.MENU) { inclusive = true } }
                },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.RULES) { RulesScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
    }
}
