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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurizen.ui.theme.AuriZenTheme
import com.aurizen.ui.theme.ThemeManager
import com.aurizen.ui.screens.SelectionRoute
import com.aurizen.ui.screens.LoadingRoute
import com.aurizen.ui.screens.HomeRoute
import com.aurizen.ui.screens.QuickChatRoute
import com.aurizen.ui.screens.MeditationRoute
import com.aurizen.ui.screens.UnifiedMeditationSessionRoute
import com.aurizen.features.breathing.BreathingRoute
import com.aurizen.ui.screens.MoodTrackerRoute
import com.aurizen.ui.screens.DreamInterpreterRoute
import com.aurizen.ui.screens.PersonalGoalsRoute
import com.aurizen.ui.screens.TTSSettingsRoute
import com.aurizen.ui.screens.SettingsRoute
import com.aurizen.ui.screens.TalkRoute

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
const val SETTINGS_SCREEN = "settings_screen"
const val TALK_SCREEN = "talk_screen"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val themeManager = remember { ThemeManager.getInstance(context) }
            val currentTheme by themeManager.getThemeState()
            
            AuriZenTheme(themeMode = currentTheme) {
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
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate(SETTINGS_SCREEN)
                                    },
                                    onNavigateToTalk = {
                                        navController.navigate(TALK_SCREEN)
                                    }
                                )
                            }

                            composable(QUICK_CHAT_SCREEN) {
                                QuickChatRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(MEDITATION_SCREEN) {
                                MeditationRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
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
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onComplete = {
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(BREATHING_SCREEN) {
                                BreathingRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(MOOD_TRACKER_SCREEN) {
                                MoodTrackerRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
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
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(PERSONAL_GOALS_SCREEN) {
                                PersonalGoalsRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(TTS_SETTINGS_SCREEN) {
                                TTSSettingsRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(SETTINGS_SCREEN) {
                                SettingsRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(TALK_SCREEN) {
                                TalkRoute(
                                    onBack = { 
                                        navController.navigate(HOME_SCREEN) {
                                            popUpTo(HOME_SCREEN) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                    }
                }
            }
        }
    }

}