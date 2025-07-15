package com.aurizen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.res.painterResource
import com.aurizen.R
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*
import com.aurizen.data.PersonalGoalsStorage
import com.aurizen.data.PersonalGoal
import com.aurizen.data.GoalCategory
import com.aurizen.data.GoalType
import com.aurizen.core.InferenceModel
import com.google.mediapipe.tasks.genai.llminference.ProgressListener

@Composable
internal fun PersonalGoalsRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val goalsStorage = remember { PersonalGoalsStorage.getInstance(context) }
    
    var goals by remember { mutableStateOf(emptyList<PersonalGoal>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        goals = goalsStorage.getAllGoals()
    }
    
    PersonalGoalsScreen(
        goals = goals,
        onBack = onBack,
        onAddGoal = { title, category, goalType, targetDate, notes ->
            val newGoal = PersonalGoal(
                title = title,
                category = category,
                goalType = goalType,
                targetDate = targetDate,
                notes = notes
            )
            goalsStorage.saveGoal(newGoal)
            goals = goalsStorage.getAllGoals()
        },
        onUpdateProgress = { goalId, progress ->
            goalsStorage.updateGoalProgress(goalId, progress)
            goals = goalsStorage.getAllGoals()
        },
        onToggleDailyGoal = { goalId ->
            goalsStorage.markDailyGoalCompleted(goalId)
            goals = goalsStorage.getAllGoals()
        },
        onDeleteGoal = { goalId ->
            goalsStorage.deleteGoal(goalId)
            goals = goalsStorage.getAllGoals()
        },
        onShowAddDialog = { showAddDialog = true }
    )
    
    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, category, goalType, targetDate, notes ->
                val newGoal = PersonalGoal(
                    title = title,
                    category = category,
                    goalType = goalType,
                    targetDate = targetDate,
                    notes = notes
                )
                goalsStorage.saveGoal(newGoal)
                goals = goalsStorage.getAllGoals()
                showAddDialog = false
            }
        )
    }
}

@Composable
fun PersonalGoalsScreen(
    goals: List<PersonalGoal>,
    onBack: () -> Unit,
    onAddGoal: (String, GoalCategory, GoalType, Long, String) -> Unit,
    onUpdateProgress: (String, Float) -> Unit,
    onToggleDailyGoal: (String) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onShowAddDialog: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    // Sort goals: daily goals first, then by creation date
    val sortedGoals = goals.sortedWith(
        compareBy<PersonalGoal> { it.getEffectiveGoalType() != GoalType.DAILY }
            .thenBy { it.createdDate }
    )
    
    // Filter goals
    val activeGoals = sortedGoals.filter { !it.isCompleted }
    val completedGoals = sortedGoals.filter { it.isCompleted }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Top bar with tabs - matching mood tracker style
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "Personal Goals",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FloatingActionButton(
                            onClick = onShowAddDialog,
                            modifier = Modifier.size(42.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Goal", modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    // Tab selection
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Active (${activeGoals.size})") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Done (${completedGoals.size})") }
                        )
                    }
                }
            }
        }
        
        // Quick stats row - compact design
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatsCard(
                    icon = Icons.Default.TrendingUp,
                    label = "Active",
                    value = activeGoals.size.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    icon = Icons.Default.CheckCircle,
                    label = "Done",
                    value = completedGoals.size.toString(),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                StatsCard(
                    icon = Icons.Default.Star,
                    label = "Total",
                    value = goals.size.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        val currentGoals = if (selectedTabIndex == 0) activeGoals else completedGoals
        
        if (currentGoals.isEmpty()) {
            // Empty state
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTabIndex == 0) "No active goals yet" else "No completed goals yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedTabIndex == 0) 
                                "Set personal goals to track your progress and stay motivated on your wellness journey" 
                            else 
                                "Completed goals will appear here once you finish them",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            // Group goals by type
            val dailyGoals = currentGoals.filter { it.getEffectiveGoalType() == GoalType.DAILY }
            val oneTimeGoals = currentGoals.filter { it.getEffectiveGoalType() != GoalType.DAILY }

            // Daily Goals Section
            if (dailyGoals.isNotEmpty()) {
                item {
                    Text(
                        text = "Daily Goals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                items(dailyGoals) { goal ->
                    GoalCard(
                        goal = goal,
                        onUpdateProgress = { progress -> onUpdateProgress(goal.id, progress) },
                        onToggleDailyGoal = { onToggleDailyGoal(goal.id) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }

            // One-time Goals Section
            if (oneTimeGoals.isNotEmpty()) {
                item {
                    Text(
                        text = "Goals with Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }

                items(oneTimeGoals) { goal ->
                    GoalCard(
                        goal = goal,
                        onUpdateProgress = { progress -> onUpdateProgress(goal.id, progress) },
                        onToggleDailyGoal = { onToggleDailyGoal(goal.id) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: PersonalGoal,
    onUpdateProgress: (Float) -> Unit,
    onToggleDailyGoal: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMotivationDialog by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val daysLeft = ((goal.targetDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
    val context = LocalContext.current
    val goalsStorage = remember { PersonalGoalsStorage.getInstance(context) }
    val isDailyGoalCompletedToday = goalsStorage.isDailyGoalCompletedToday(goal.id)

    // Compact card design with expandable functionality
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                goal.isCompleted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
                goal.getEffectiveGoalType() == GoalType.DAILY && isDailyGoalCompletedToday -> 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (goal.getEffectiveGoalType() == GoalType.DAILY && isDailyGoalCompletedToday) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Compact header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category emoji
                    Text(
                        text = goal.category.emoji,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        // Title
                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Progress bar for one-time goals or status for daily goals
                        if (goal.getEffectiveGoalType() != GoalType.DAILY && !goal.isCompleted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearProgressIndicator(
                                    progress = { goal.progress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                    text = "${(goal.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            // Status text for daily goals or completed goals
                            Text(
                                text = when {
                                    goal.getEffectiveGoalType() == GoalType.DAILY -> {
                                        when {
                                            isDailyGoalCompletedToday -> "✅ Completed today • ${goal.getEffectiveDailyProgress().currentStreak} day streak"
                                            goal.getEffectiveDailyProgress().currentStreak > 0 -> "🔥 ${goal.getEffectiveDailyProgress().currentStreak} day streak"
                                            else -> "🌱 Ready to start"
                                        }
                                    }
                                    goal.isCompleted -> "🎉 Completed!"
                                    else -> "Goal status"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    goal.getEffectiveGoalType() == GoalType.DAILY && isDailyGoalCompletedToday -> 
                                        MaterialTheme.colorScheme.primary
                                    goal.isCompleted -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                },
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Quick action button and expand icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Daily goal completion toggle
                    if (goal.getEffectiveGoalType() == GoalType.DAILY) {
                        IconButton(
                            onClick = onToggleDailyGoal,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isDailyGoalCompletedToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (isDailyGoalCompletedToday) "Mark incomplete" else "Mark complete",
                                tint = if (isDailyGoalCompletedToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // Expand/collapse icon
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            // Expandable content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Divider
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Detailed status and date info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            goal.getEffectiveGoalType() == GoalType.DAILY -> "Daily Goal"
                            goal.isCompleted -> "Completed Goal"
                            daysLeft < 0 -> "⚠️ ${-daysLeft} days overdue"
                            daysLeft == 0 -> "🎯 Due today"
                            daysLeft == 1 -> "📅 Due tomorrow"
                            else -> "📆 ${daysLeft} days remaining"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            goal.isCompleted -> MaterialTheme.colorScheme.tertiary
                            daysLeft < 0 -> MaterialTheme.colorScheme.error
                            daysLeft <= 7 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = dateFormat.format(Date(goal.targetDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Progress controls for one-time goals
                if (goal.getEffectiveGoalType() != GoalType.DAILY && !goal.isCompleted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val progressActions = listOf(
                            0.1f to "10%",
                            0.25f to "25%",
                            0.5f to "50%",
                            1f to "Done"
                        )
                        
                        progressActions.forEach { (increment, label) ->
                            OutlinedButton(
                                onClick = {
                                    val newProgress = (goal.progress + increment).coerceAtMost(1f)
                                    onUpdateProgress(newProgress)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (increment == 1f) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                            ) {
                                Text(
                                    text = if (increment == 1f) "✓ $label" else "+$label",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                // Notes section
                if (goal.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "💭 ${goal.notes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                // Action buttons
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // AI Motivation button
                    OutlinedButton(
                        onClick = { showMotivationDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Motivation",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    // Delete button
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    "Delete Goal?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { 
                Text(
                    "Are you sure you want to delete '${goal.title}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Motivation dialog
    if (showMotivationDialog) {
        MotivationDialog(
            goal = goal,
            onDismiss = { showMotivationDialog = false }
        )
    }
}

@Composable
private fun MotivationDialog(
    goal: PersonalGoal,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var motivationMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    LaunchedEffect(goal.id) {
        isLoading = true
        hasError = false
        try {
            val inferenceModel = InferenceModel.getInstance(context)
            val prompt = buildMotivationPrompt(goal)
            
            // Use a simple progress listener that collects the full response
            var fullResponse = ""
            val progressListener = ProgressListener<String> { partialResult, done ->
                fullResponse += partialResult
                if (done) {
                    motivationMessage = fullResponse.ifBlank { getMotivationMessage(goal) }
                    isLoading = false
                }
            }
            
            inferenceModel.generateResponseAsync(prompt, progressListener)
            
        } catch (e: Exception) {
            hasError = true
            motivationMessage = getMotivationMessage(goal) // Fallback to static message
            isLoading = false
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AuriZen Motivation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = { 
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generating personalized motivation...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = motivationMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Thank you!")
            }
        }
    )
}

private fun buildMotivationPrompt(goal: PersonalGoal): String {
    val goalType = goal.getEffectiveGoalType()
    val category = goal.category
    val progress = goal.progress
    val dailyProgress = goal.getEffectiveDailyProgress()
    val isCompleted = goal.isCompleted
    
    val goalTypeText = if (goalType == GoalType.DAILY) "daily habit" else "one-time goal"
    val categoryText = category.displayName.lowercase()
    
    val progressContext = when {
        isCompleted -> "completed"
        goalType == GoalType.DAILY -> {
            when {
                dailyProgress.currentStreak >= 7 -> "has a strong ${dailyProgress.currentStreak}-day streak"
                dailyProgress.currentStreak >= 3 -> "has a ${dailyProgress.currentStreak}-day streak"
                dailyProgress.totalDaysCompleted > 0 -> "has completed ${dailyProgress.totalDaysCompleted} days but may have missed some"
                else -> "is just starting"
            }
        }
        else -> {
            when {
                progress >= 0.75f -> "is 75% complete"
                progress >= 0.5f -> "is halfway done"
                progress >= 0.25f -> "is 25% complete"
                else -> "is just starting"
            }
        }
    }
    
    return """
        You are AuriZen, a supportive wellness AI assistant. A user has a ${goalTypeText} called "${goal.title}" in the ${categoryText} category that ${progressContext}.
        
        Please provide a short, encouraging, and personalized motivational message (1-2 sentences) that:
        - Acknowledges their current progress
        - Provides specific encouragement for their situation
        - Maintains a warm, supportive tone
        - Focuses on wellness and personal growth
        - Uses appropriate emojis to make it engaging
        
        Keep the message concise but meaningful, as if you're a caring friend who understands their wellness journey.
    """.trimIndent()
}

private fun getMotivationMessage(goal: PersonalGoal): String {
    val goalType = goal.getEffectiveGoalType()
    val category = goal.category
    val progress = goal.progress
    val dailyProgress = goal.getEffectiveDailyProgress()
    val isCompleted = goal.isCompleted
    
    return when {
        isCompleted -> {
            "🎉 Congratulations on completing '${goal.title}'! Your dedication and consistency have paid off. This achievement is a testament to your commitment to wellness and personal growth. Use this momentum to tackle your next goal with confidence!"
        }
        goalType == GoalType.DAILY -> {
            when {
                dailyProgress.currentStreak >= 7 -> "🔥 Amazing work! You've maintained a ${dailyProgress.currentStreak}-day streak with '${goal.title}'. This consistency is building powerful habits that will transform your life. Each day you choose to continue is a victory - keep this incredible momentum going!"
                dailyProgress.currentStreak >= 3 -> "⭐ You're building great momentum with '${goal.title}'! Your ${dailyProgress.currentStreak}-day streak shows real commitment. Remember, small daily actions create lasting change. Trust the process and celebrate each day you show up for yourself."
                dailyProgress.totalDaysCompleted > 0 -> "💪 Every day you complete '${goal.title}' is progress toward a better you. You've already shown you can do this ${dailyProgress.totalDaysCompleted} times! Don't let perfect be the enemy of good - consistency beats perfection every time."
                else -> "🌱 Starting '${goal.title}' is the first step toward transformation. Daily habits are the compound interest of self-improvement. Be patient with yourself, stay consistent, and trust that small daily actions will create remarkable results over time."
            }
        }
        category == GoalCategory.HEALTH || category == GoalCategory.FITNESS -> {
            when {
                progress >= 0.75f -> "🏆 You're so close to achieving '${goal.title}'! Your body and mind are already experiencing the benefits of your hard work. This final push will cement the healthy habits you've been building. Your future self will thank you for not giving up now!"
                progress >= 0.5f -> "💪 You're halfway there with '${goal.title}'! Your commitment to your health is inspiring. Remember, every workout, every healthy meal, every positive choice is an investment in your long-term wellbeing. Keep pushing forward - you've got this!"
                progress >= 0.25f -> "🌟 Great start on '${goal.title}'! Your body is already beginning to adapt and grow stronger. Health transformations take time, but you're planting seeds that will bloom into lifelong wellness. Stay consistent and trust the process."
                else -> "🚀 Your health journey with '${goal.title}' starts now! Remember, the best time to start was yesterday, but the second best time is today. Every small step toward better health is a victory. Be proud of taking this important step for yourself."
            }
        }
        category == GoalCategory.MENTAL_WELLNESS -> {
            when {
                progress >= 0.75f -> "🧘 Your mental wellness journey with '${goal.title}' is nearly complete! The peace and clarity you're cultivating will serve you for a lifetime. Mental health is just as important as physical health - you're making a wise investment in your overall wellbeing."
                progress >= 0.5f -> "🌸 You're making beautiful progress with '${goal.title}'. Mental wellness is a journey, not a destination. Each step toward inner peace and emotional balance is strengthening your resilience and capacity for joy. Keep nurturing your mind and spirit."
                progress >= 0.25f -> "💫 Your commitment to '${goal.title}' shows real wisdom. Prioritizing mental wellness is one of the most important things you can do for yourself. You're building emotional strength and inner peace that will positively impact every area of your life."
                else -> "🌅 Beginning '${goal.title}' is a beautiful act of self-care. Mental wellness requires the same attention and dedication as physical health. You're taking a crucial step toward a more balanced, peaceful, and fulfilling life. Be gentle with yourself as you grow."
            }
        }
        else -> {
            when {
                progress >= 0.75f -> "🎯 You're almost there with '${goal.title}'! Your persistence and dedication are about to pay off in a big way. This final stretch is where champions are made. Push through - you're closer than you think to achieving something amazing!"
                progress >= 0.5f -> "⚡ Excellent progress on '${goal.title}'! You've proven to yourself that you have what it takes to succeed. The habits and discipline you're building now will serve you in all areas of life. Keep up the fantastic work!"
                progress >= 0.25f -> "🌟 You're making solid progress with '${goal.title}'! Every step forward is a victory worth celebrating. Remember, success isn't just about the destination - it's about who you become along the journey. You're already growing stronger and more capable."
                else -> "🚀 Your journey with '${goal.title}' begins with a single step, and you've taken it! Goals are dreams with deadlines, and you're turning your dreams into reality. Stay focused, be patient with yourself, and trust in your ability to achieve great things."
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onAdd: (String, GoalCategory, GoalType, Long, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GoalCategory.HEALTH) }
    var selectedGoalType by remember { mutableStateOf(GoalType.ONE_TIME) }
    var targetDate by remember { mutableStateOf(System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)) }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Enhanced header with gradient
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Create New Goal",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Set up your personal wellness goal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Form content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Goal title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Goal Title") },
                        placeholder = { Text("e.g., Exercise daily, Learn Python, Read 12 books") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    
                    // Category selection with enhanced design
                    var categoryExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = "${selectedCategory.emoji} ${selectedCategory.displayName}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            GoalCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = category.emoji,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Column {
                                                Text(
                                                    text = category.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = category
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Goal type selection with enhanced design
                    var goalTypeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = goalTypeExpanded,
                        onExpandedChange = { goalTypeExpanded = !goalTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedGoalType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Goal Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = goalTypeExpanded,
                            onDismissRequest = { goalTypeExpanded = false }
                        ) {
                            GoalType.entries.forEach { goalType ->
                                DropdownMenuItem(
                                    text = { 
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (goalType == GoalType.DAILY) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                } else {
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                                                }
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Text(
                                                    text = goalType.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (goalType == GoalType.DAILY) 
                                                        "Build daily habits with streak tracking" 
                                                    else 
                                                        "Complete specific achievements with progress tracking",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedGoalType = goalType
                                        goalTypeExpanded = false
                                        // Auto-adjust target date based on goal type
                                        targetDate = if (goalType == GoalType.DAILY) {
                                            System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L)
                                        } else {
                                            System.currentTimeMillis() + (90 * 24 * 60 * 60 * 1000L)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Target date with enhanced design
                    OutlinedTextField(
                        value = dateFormat.format(Date(targetDate)),
                        onValueChange = {},
                        readOnly = true,
                        label = { 
                            Text(if (selectedGoalType == GoalType.DAILY) "Track Until Date" else "Target Date")
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Select date",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Notes field with enhanced design
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        placeholder = { Text("Add motivation, specific details, or reminders") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                
                // Action buttons with enhanced design
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }
                        
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onAdd(title, selectedCategory, selectedGoalType, targetDate, notes)
                                }
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Goal")
                        }
                    }
                }
            }
        }
    }
    
    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { date ->
                targetDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun StatsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

