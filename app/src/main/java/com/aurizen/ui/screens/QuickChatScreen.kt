package com.aurizen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurizen.viewmodels.QuickChatViewModel
import com.aurizen.core.FunctionCallingSystem

@Composable
internal fun QuickChatRoute(
    onBack: () -> Unit,
    onNavigateToMeditation: (String) -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: QuickChatViewModel = viewModel(factory = QuickChatViewModel.getFactory(context))

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val response by viewModel.response.collectAsStateWithLifecycle()
    val isInputEnabled by viewModel.isInputEnabled.collectAsStateWithLifecycle()
    val functionCallResult by viewModel.functionCallResult.collectAsStateWithLifecycle()

    QuickChatScreen(
        isLoading = isLoading,
        response = response,
        isInputEnabled = isInputEnabled,
        functionCallResult = functionCallResult,
        onSendMessage = viewModel::sendMessage,
        onClearFunctionResult = viewModel::clearFunctionCallResult,
        onNavigateToMeditation = onNavigateToMeditation,
        onNavigateToGoals = onNavigateToGoals,
        onNavigateToSettings = onNavigateToSettings,
        onBack = onBack
    )
}

@Composable
fun QuickChatScreen(
    isLoading: Boolean,
    response: String,
    isInputEnabled: Boolean,
    functionCallResult: FunctionCallingSystem.FunctionCallResult?,
    onSendMessage: (String) -> Unit,
    onClearFunctionResult: () -> Unit,
    onNavigateToMeditation: (String) -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    var userInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when keyboard appears or content changes
    LaunchedEffect(isLoading, response) {
        if (response.isNotEmpty() || isLoading) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // This pushes content up when keyboard appears
    ) {
        // Top bar - Fixed at top
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Text(
                    text = "Wellness Guidance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = "🌟 Get personalized guidance for your wellness journey. Select a category to explore specific areas.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Wellness categories (always show when not loading)
            if (!isLoading) {
                WellnessCategoryCards { topic ->
                    onSendMessage(topic)
                }
            }

            // Response area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when {
                        isLoading -> {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Getting wellness insights...")
                            }
                        }
                        response.isNotEmpty() -> {
                            Text(
                                text = response,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select a wellness category above to get personalized guidance and recommendations.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "💡 Each session is tailored to your personal profile and current wellness state.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Enhanced function call result card
            functionCallResult?.let { result ->
                EnhancedFunctionCallResultCard(
                    result = result,
                    onClearResult = onClearFunctionResult,
                    onNavigateToMeditation = onNavigateToMeditation,
                    onNavigateToGoals = onNavigateToGoals,
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            // Professional help disclaimer
            ProfessionalHelpDisclaimer()

            // Extra space to ensure input area is visible
            Spacer(modifier = Modifier.height(60.dp))
        }

        // Compact context input area - Fixed at bottom
        if (isInputEnabled) {
            CompactContextInput(
                userInput = userInput,
                onInputChange = { userInput = it },
                onSend = { input ->
                    if (input.isNotBlank()) {
                        onSendMessage("Additional context: $input")
                        userInput = ""
                    }
                }
            )
        }
    }
}

@Composable
fun CompactContextInput(
    userInput: String,
    onInputChange: (String) -> Unit,
    onSend: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = userInput,
                onValueChange = onInputChange,
                placeholder = { Text("Add context...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 1,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodySmall
            )

            if (userInput.isNotBlank()) {
                IconButton(
                    onClick = { onSend(userInput) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Default.Send,
                        contentDescription = "Send context",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WellnessCategoryCards(onTopicSelected: (String) -> Unit) {
    var expandedCategory by remember { mutableStateOf<WellnessCategory?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Select wellness category:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        WellnessCategory.entries.forEach { category ->
            WellnessCategoryCard(
                category = category,
                isExpanded = expandedCategory == category,
                onCategoryClick = {
                    expandedCategory = if (expandedCategory == category) null else category
                },
                onTopicSelected = onTopicSelected
            )
        }
    }
}

@Composable
fun WellnessCategoryCard(
    category: WellnessCategory,
    isExpanded: Boolean,
    onCategoryClick: () -> Unit,
    onTopicSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Category header
            Surface(
                onClick = onCategoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                color = category.color.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = category.color.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = category.color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = category.color.copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = category.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Expanded items
            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.items.forEach { item ->
                        WellnessItemButton(
                            item = item,
                            categoryColor = category.color,
                            onTopicSelected = onTopicSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WellnessItemButton(
    item: WellnessItem,
    categoryColor: Color = MaterialTheme.colorScheme.primary,
    onTopicSelected: (String) -> Unit
) {
    Surface(
        onClick = { onTopicSelected(item.prompt) },
        modifier = Modifier.fillMaxWidth(),
        color = categoryColor.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = categoryColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = item.emoji,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

enum class WellnessCategory(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val items: List<WellnessItem>
) {
    MENTAL_HEALTH(
        title = "Mental Health",
        icon = Icons.Default.Psychology,
        color = Color(0xFF6B73FF),
        items = listOf(
            WellnessItem("🧠", "Anxiety Relief", "anxiety management and coping strategies"),
            WellnessItem("💭", "Stress Management", "stress reduction and stress management techniques"),
            WellnessItem("🌤️", "Depression Support", "depression support and mood improvement strategies"),
            WellnessItem("⚡", "Emotional Regulation", "emotional regulation and managing overwhelming emotions"),
            WellnessItem("😊", "Mood Boost", "mood enhancement and emotional well-being")
        )
    ),
    PHYSICAL_HEALTH(
        title = "Physical Health",
        icon = Icons.Default.FitnessCenter,
        color = Color(0xFF4CAF50),
        items = listOf(
            WellnessItem("😴", "Sleep Improvement", "sleep hygiene and improving sleep quality"),
            WellnessItem("🏃", "Exercise Motivation", "exercise motivation and building sustainable fitness habits"),
            WellnessItem("🥗", "Nutrition Guidance", "nutrition guidance and healthy eating habits"),
            WellnessItem("🩹", "Pain Management", "pain management and coping with chronic discomfort"),
            WellnessItem("⚡", "Energy Boost", "natural energy enhancement and combating fatigue")
        )
    ),
    PERSONAL_GROWTH(
        title = "Personal Growth",
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        color = Color(0xFFFF9800),
        items = listOf(
            WellnessItem("🎯", "Goal Achievement", "goal setting and achieving personal objectives"),
            WellnessItem("🔄", "Habit Building", "building positive habits and breaking negative patterns"),
            WellnessItem("💪", "Confidence Building", "self-confidence and self-esteem enhancement"),
            WellnessItem("📈", "Productivity", "productivity improvement and time management"),
            WellnessItem("🚀", "Motivation", "motivation and maintaining positive momentum")
        )
    ),
    DAILY_WELLNESS(
        title = "Daily Wellness",
        icon = Icons.Default.Lightbulb,
        color = Color(0xFF9C27B0),
        items = listOf(
            WellnessItem("💡", "Daily Tips", "personalized daily wellness practices"),
            WellnessItem("🧘", "Mindfulness", "mindfulness practices and present-moment awareness"),
            WellnessItem("🌸", "Self-Care", "self-care practices and prioritizing personal well-being"),
            WellnessItem("⚖️", "Life Balance", "work-life balance and creating life harmony"),
            WellnessItem("📅", "Routine Building", "establishing healthy daily routines")
        )
    )
}

data class WellnessItem(
    val emoji: String,
    val title: String,
    val prompt: String
)

@Composable
fun EnhancedFunctionCallResultCard(
    result: FunctionCallingSystem.FunctionCallResult,
    onClearResult: () -> Unit,
    onNavigateToMeditation: (String) -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    result.actionButton?.let { actionButton ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title based on result type
                val (title, icon) = when (result.resultType) {
                    FunctionCallingSystem.ResultType.MEDITATION_CREATED -> "Meditation Created!" to "🧘‍♀️"
                    FunctionCallingSystem.ResultType.GOAL_CREATED -> "Goal Created!" to "🎯"
                    FunctionCallingSystem.ResultType.MEMORY_STORED -> "Memory Stored!" to "💭"
                    else -> "Action Available" to "✨"
                }
                
                Text(
                    text = "$icon $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                // Details based on result type
                when (result.resultType) {
                    FunctionCallingSystem.ResultType.MEDITATION_CREATED -> {
                        val details = result.resultDetails as? FunctionCallingSystem.MeditationCreationDetails
                        details?.let { meditation ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "📝 ${meditation.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "⏱️ ${meditation.duration} minutes • Focus: ${meditation.focus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "💡 Saved to your meditation library for future access",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    FunctionCallingSystem.ResultType.GOAL_CREATED -> {
                        val details = result.resultDetails as? FunctionCallingSystem.GoalCreationDetails
                        details?.let { goal ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "📋 ${goal.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${goal.categoryEmoji} ${goal.category} • Target: ${goal.targetDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                if (goal.notes.isNotEmpty()) {
                                    Text(
                                        text = "📝 ${goal.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    FunctionCallingSystem.ResultType.MEMORY_STORED -> {
                        val details = result.resultDetails as? FunctionCallingSystem.MemoryStorageDetails
                        details?.let { memory ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "📝 \"${memory.memoryContent}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "💡 I'll remember this for future conversations",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    else -> {
                        // Fallback for unknown types
                        Text(
                            text = result.response,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // Handle action button click
                            actionButton.parameter?.let { parameter ->
                                when (actionButton.action) {
                                    FunctionCallingSystem.ButtonAction.START_MEDITATION -> {
                                        onNavigateToMeditation(parameter)
                                    }
                                    FunctionCallingSystem.ButtonAction.VIEW_GOALS -> {
                                        onNavigateToGoals()
                                    }
                                    FunctionCallingSystem.ButtonAction.VIEW_MEMORIES -> {
                                        onNavigateToSettings()
                                    }
                                    else -> {
                                        // Handle other actions if needed
                                    }
                                }
                            } ?: run {
                                // Handle actions without parameters
                                when (actionButton.action) {
                                    FunctionCallingSystem.ButtonAction.VIEW_GOALS -> {
                                        onNavigateToGoals()
                                    }
                                    FunctionCallingSystem.ButtonAction.VIEW_MEMORIES -> {
                                        onNavigateToSettings()
                                    }
                                    else -> {
                                        // Handle other parameterless actions if needed
                                    }
                                }
                            }
                            onClearResult()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(actionButton.text)
                    }
                    
                    OutlinedButton(
                        onClick = onClearResult,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfessionalHelpDisclaimer() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "If you're experiencing serious mental health concerns, please reach out to a mental health professional. Seeking help is a sign of strength, not weakness.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                lineHeight = 18.sp
            )
        }
    }
}