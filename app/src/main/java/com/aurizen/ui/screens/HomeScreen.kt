package com.aurizen.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import com.aurizen.ui.theme.AuriZenGradientBackground
import com.aurizen.data.UserProfile
import com.aurizen.R
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.saveable.rememberSaveable
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context

@Composable
internal fun HomeRoute(
    onNavigateToQuickChat: () -> Unit,
    onNavigateToMeditation: () -> Unit,
    onNavigateToBreathing: () -> Unit,
    onNavigateToMoodTracker: () -> Unit,
    onNavigateToDreamInterpreter: () -> Unit,
    onNavigateToPersonalGoals: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTalk: () -> Unit
) {
    val context = LocalContext.current
    val userProfile = remember { UserProfile.getInstance(context) }

    HomeScreen(
        userProfile = userProfile,
        onNavigateToQuickChat = onNavigateToQuickChat,
        onNavigateToMeditation = onNavigateToMeditation,
        onNavigateToBreathing = onNavigateToBreathing,
        onNavigateToMoodTracker = onNavigateToMoodTracker,
        onNavigateToDreamInterpreter = onNavigateToDreamInterpreter,
        onNavigateToPersonalGoals = onNavigateToPersonalGoals,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToTalk = onNavigateToTalk
    )
}

@Composable
fun HomeScreen(
    userProfile: UserProfile,
    onNavigateToQuickChat: () -> Unit,
    onNavigateToMeditation: () -> Unit,
    onNavigateToBreathing: () -> Unit,
    onNavigateToMoodTracker: () -> Unit,
    onNavigateToDreamInterpreter: () -> Unit,
    onNavigateToPersonalGoals: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTalk: () -> Unit
) {
    AuriZenGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with Logo
            HeaderSection(onNavigateToSettings = onNavigateToSettings)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Main content in a single scrollable column
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome message
                item {
                    WelcomeMessage()
                }
                
                // Main grid of features
                item {
                    MainFeaturesGrid(
                        onNavigateToMeditation = onNavigateToMeditation,
                        onNavigateToMoodTracker = onNavigateToMoodTracker,
                        onNavigateToQuickChat = onNavigateToQuickChat,
                        onNavigateToBreathing = onNavigateToBreathing,
                        onNavigateToDreamInterpreter = onNavigateToDreamInterpreter,
                        onNavigateToPersonalGoals = onNavigateToPersonalGoals,
                        onNavigateToTalk = onNavigateToTalk
                    )
                }
            }

            // Privacy footer stays at bottom
            CompactPrivacyCard()
        }
    }
}

@SuppressLint("ResourceType")
@Composable
private fun HeaderSection(onNavigateToSettings: () -> Unit) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        // Logo centered with both image and text side by side
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo image (doesn't change color)
            Image(
                painter = painterResource(id = R.drawable.aurizen),
                contentDescription = "AuriZen Logo",
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Logo text (tinted with theme colors)
            Image(
                painter = painterResource(id = R.drawable.aurizen_logo),
                contentDescription = "AuriZen Text",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier
                    .offset(x = (-30).dp, y = 10.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }

        // Settings button on right
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun WelcomeMessage() {
    var currentIndex by remember { mutableIntStateOf(0) }
    
    val messages = listOf(
        "🌿 Your private AI wellness companion",
        "🔒 Private & fully offline AI",
        "🧘 Custom meditations for your mood",
        "🌱 Mindful moments, no tracking",
        "🎵 Binaural beats & soundscapes"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            currentIndex = (currentIndex + 1) % messages.size
        }
    }

    AnimatedContent(
        targetState = messages[currentIndex],
        transitionSpec = {
            fadeIn(animationSpec = tween(600)) togetherWith 
            fadeOut(animationSpec = tween(600))
        },
        label = "welcome_message"
    ) { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MainFeaturesGrid(
    onNavigateToMeditation: () -> Unit,
    onNavigateToMoodTracker: () -> Unit,
    onNavigateToQuickChat: () -> Unit,
    onNavigateToBreathing: () -> Unit,
    onNavigateToDreamInterpreter: () -> Unit,
    onNavigateToPersonalGoals: () -> Unit,
    onNavigateToTalk: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Featured meditation (full width)
        FeaturedCard(
            title = "Guided Meditation",
            description = "AI-crafted sessions for your current mood",
            icon = Icons.Default.SelfImprovement,
            onClick = onNavigateToMeditation
        )
        
        // Top row - most used features
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactFeatureCard(
                title = "Talk",
                description = "Voice chat",
                icon = Icons.Default.RecordVoiceOver,
                onClick = onNavigateToTalk,
                modifier = Modifier.weight(1f)
            )
            
            CompactFeatureCard(
                title = "Quick Support",
                description = "Text chat & advice",
                icon = Icons.Default.Psychology,
                onClick = onNavigateToQuickChat,
                modifier = Modifier.weight(1f),
            )
        }
        
        // Second row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactFeatureCard(
                title = "Mood Tracker",
                description = "Track emotions",
                icon = Icons.Default.Mood,
                onClick = onNavigateToMoodTracker,
                modifier = Modifier.weight(1f)
            )
            CompactFeatureCard(
                title = "Personal Goals",
                description = "Track progress",
                icon = Icons.Default.Flag,
                onClick = onNavigateToPersonalGoals,
                modifier = Modifier.weight(1f)
            )

        }
        
        // Third row - supporting features
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompactFeatureCard(
                title = "Breathing",
                description = "Calm exercises",
                icon = Icons.Default.Air,
                onClick = onNavigateToBreathing,
                modifier = Modifier.weight(1f)
            )

            CompactFeatureCard(
                title = "Dream Insights",
                description = "Understand dreams",
                icon = Icons.Default.Bedtime,
                onClick = onNavigateToDreamInterpreter,
                modifier = Modifier.weight(1f)
            )
            

        }
    }
}

@Composable
private fun FeaturedCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CompactFeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useAuriZenIcon: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (useAuriZenIcon) {
                Image(
                    painter = painterResource(id = R.drawable.aurizen),
                    contentDescription = title,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactPrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Privacy",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "All AI processing happens locally. Your data stays private.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

