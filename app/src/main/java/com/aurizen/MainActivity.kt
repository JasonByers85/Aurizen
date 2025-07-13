package com.aurizen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurizen.ui.theme.AuriZenTheme

const val START_SCREEN = "start_screen"
const val LOAD_SCREEN = "load_screen"
const val HOME_SCREEN = "home_screen"
const val QUICK_CHAT_SCREEN = "quick_chat_screen"
const val MEDITATION_SCREEN = "meditation_screen"
const val MEDITATION_SESSION_SCREEN = "meditation_session_screen"
const val BREATHING_SCREEN = "breathing_screen"
const val MOOD_TRACKER_SCREEN = "mood_tracker_screen"
const val DREAM_INTERPRETER_SCREEN = "dream_interpreter_screen"
const val PERSONAL_GOALS_SCREEN = "personal_goals_screen"
const val TTS_SETTINGS_SCREEN = "tts_settings_screen"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuriZenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                        val navController = rememberNavController()
                        val startDestination = intent.getStringExtra("NAVIGATE_TO") ?: START_SCREEN

                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable(START_SCREEN) {
                                SelectionRoute(
                                    onModelSelected = {
                                        navController.navigate(LOAD_SCREEN) {
                                            popUpTo(START_SCREEN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(LOAD_SCREEN) {
                                LoadingRoute(
                                    onModelLoaded = {
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(LOAD_SCREEN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onGoBack = {
                                        navController.navigate(START_SCREEN) {
                                            popUpTo(LOAD_SCREEN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(HOME_SCREEN) {
                                HomeRoute(
                                    onNavigateToQuickChat = {
                                        navController.navigate(QUICK_CHAT_SCREEN)
                                    },
                                    onNavigateToMeditation = {
                                        navController.navigate(MEDITATION_SCREEN)
                                    },
                                    onNavigateToBreathing = {
                                        navController.navigate(BREATHING_SCREEN)
                                    },
                                    onNavigateToMoodTracker = {
                                        navController.navigate(MOOD_TRACKER_SCREEN)
                                    },
                                    onNavigateToDreamInterpreter = {
                                        navController.navigate(DREAM_INTERPRETER_SCREEN)
                                    },
                                    onNavigateToPersonalGoals = {
                                        navController.navigate(PERSONAL_GOALS_SCREEN)
                                    }
                                )
                            }

                            composable(QUICK_CHAT_SCREEN) {
                                QuickChatRoute(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(MEDITATION_SCREEN) {
                                MeditationRoute(
                                    onBack = { navController.popBackStack() },
                                    onStartSession = { meditationType ->
                                        navController.navigate("$MEDITATION_SESSION_SCREEN/$meditationType")
                                    }
                                )
                            }

                            composable("$MEDITATION_SESSION_SCREEN/{meditationType}") { backStackEntry ->
                                val meditationType = backStackEntry.arguments?.getString("meditationType") ?: "basic"
                                
                                // Use unified meditation session screen for all meditation types
                                UnifiedMeditationSessionRoute(
                                    meditationType = meditationType,
                                    onBack = { navController.popBackStack() },
                                    onComplete = {
                                        navController.navigate(MEDITATION_SCREEN) {
                                            popUpTo(MEDITATION_SESSION_SCREEN) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(BREATHING_SCREEN) {
                                BreathingRoute(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(MOOD_TRACKER_SCREEN) {
                                MoodTrackerRoute(
                                    onBack = { navController.popBackStack() },
                                    onNavigateToCustomMeditation = { focus, mood, moodContext ->
                                        // Create custom meditation type with mood context
                                        // Encode the mood context safely for URL
                                        val encodedContext = java.net.URLEncoder.encode(moodContext, "UTF-8")
                                        val customMeditationType = "custom:$focus|$mood|$encodedContext|10"
                                        navController.navigate("$MEDITATION_SESSION_SCREEN/$customMeditationType")
                                    }
                                )
                            }

                            composable(DREAM_INTERPRETER_SCREEN) {
                                DreamInterpreterRoute(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(PERSONAL_GOALS_SCREEN) {
                                PersonalGoalsRoute(
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(TTS_SETTINGS_SCREEN) {
                                TTSSettingsRoute(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                    }
                }
            }
        }
    }

}