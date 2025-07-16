package com.aurizen.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurizen.viewmodels.MoodTrackerViewModel
import com.aurizen.data.MoodEntry
import com.aurizen.utils.MoodUtils
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import com.aurizen.R
import androidx.compose.material3.Slider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@Composable
internal fun MoodTrackerRoute(
    onBack: () -> Unit,
    onNavigateToCustomMeditation: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: MoodTrackerViewModel = viewModel(factory = MoodTrackerViewModel.getFactory(context))
    
    val moodHistory by viewModel.moodHistory.collectAsStateWithLifecycle()
    val aiInsights by viewModel.aiInsights.collectAsStateWithLifecycle()
    val isLoadingInsights by viewModel.isLoadingInsights.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.loadMoodHistory()
    }
    
    MoodTrackerScreen(
        moodHistory = moodHistory,
        aiInsights = aiInsights,
        isLoadingInsights = isLoadingInsights,
        onBack = onBack,
        onSaveMood = { mood, note, energy, stress, triggers -> viewModel.saveMood(mood, note, energy, stress, triggers) },
        onGenerateInsights = { viewModel.generateMoodInsights() },
        onClearHistory = { viewModel.clearMoodHistory() },
        onGenerateCustomMeditation = {
            val (focus, mood, experience) = viewModel.generateMeditationParams()
            val moodContext = viewModel.getMoodContext()
            onNavigateToCustomMeditation(focus, mood, moodContext) // Pass actual mood context
        },
        context = context
    )
}

@Composable
fun MoodTrackerScreen(
    moodHistory: List<MoodEntry>,
    aiInsights: String,
    isLoadingInsights: Boolean,
    onBack: () -> Unit,
    onSaveMood: (String, String, Float, Float, List<String>) -> Unit,
    onGenerateInsights: () -> Unit,
    onClearHistory: () -> Unit,
    onGenerateCustomMeditation: () -> Unit,
    context: Context
) {
    var selectedMood by remember { mutableStateOf("") }
    var moodNote by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var energyLevel by remember { mutableStateOf(3f) }
    var stressLevel by remember { mutableStateOf(3f) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Top bar with tabs
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
                            text = "Mood Tracker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Tab selection
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Track Mood") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("History") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Insights") }
                        )
                    }
                }
            }
        }
        
        when (selectedTab) {
            0 -> {
                // Track Mood Tab
                
                
                
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📊 How are you feeling?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Track your mood to understand patterns and celebrate progress.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(160.dp)
                    ) {
                        val moods = listOf(
                            "Ecstatic" to "ecstatic",
                            "Happy" to "happy",
                            "Confident" to "confident",
                            "Calm" to "calm",
                            "Tired" to "tired",
                            "Anxious" to "anxious",
                            "Stressed" to "stressed",
                            "Sad" to "sad"
                        )
                        
                        items(moods) { (display, value) ->
                            MoodButton(
                                text = display,
                                color = MoodUtils.getMoodColor(value),
                                isSelected = selectedMood == value,
                                onClick = { selectedMood = value }
                            )
                        }
                    }
                }
                
                // Combined Energy & Stress Levels
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Energy Level
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Energy",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(60.dp)
                                )
                                Slider(
                                    value = energyLevel,
                                    onValueChange = { energyLevel = it },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${energyLevel.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(20.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Stress Level
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Stress",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(60.dp)
                                )
                                Slider(
                                    value = stressLevel,
                                    onValueChange = { stressLevel = it },
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${stressLevel.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(20.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
                
                // Note field below sliders
                item {
                    TextField(
                        value = moodNote,
                        onValueChange = { moodNote = it },
                        label = { Text("Add a note (optional)") },
                        placeholder = { Text("What's influencing your mood today?") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                
                
                item {
                    Button(
                        onClick = {
                            if (selectedMood.isNotEmpty()) {
                                onSaveMood(selectedMood, moodNote, energyLevel, stressLevel, emptyList())
                                selectedMood = ""
                                moodNote = ""
                                energyLevel = 3f
                                stressLevel = 3f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedMood.isNotEmpty()
                    ) {
                        Text("Save Mood Entry")
                    }
                }
            }
            
            1 -> {
                // History Tab
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📈 Your Mood Journey",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${moodHistory.size} entries recorded",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            OutlinedButton(
                                onClick = { showClearDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
                
                if (moodHistory.isNotEmpty()) {
                    item {
                        // Mood visualization
                        MoodVisualization(moodHistory = moodHistory)
                    }
                    
                    item {
                        Column {
                            // Recent mood entries header
                            Text(
                                text = "Recent Entries",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Recent entries row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(80.dp)
                            ) {
                                items(moodHistory.takeLast(10).reversed()) { entry ->
                                    MoodHistoryCard(entry)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Timeline,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No mood history yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Start tracking your mood to see patterns over time",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                
                // Mood trend analysis at bottom of history tab
                if (moodHistory.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = MoodUtils.getMoodTrend(moodHistory),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            
            2 -> {
                // Insights Tab
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🧠 AI Mood Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Get personalized insights about your mood patterns and suggestions for improvement.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                item {
                    Button(
                        onClick = onGenerateInsights,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoadingInsights && moodHistory.isNotEmpty()
                    ) {
                        if (isLoadingInsights) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing...")
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.aurizen),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Mood Insights")
                        }
                    }
                }
                
                if (aiInsights.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.aurizen),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Your Mood Analysis",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = aiInsights.replace("\n\n", "\n"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                    
                    // Custom meditation suggestion button - only show when not loading
                    if (!isLoadingInsights) {
                        item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🧘‍♀️ Want me to create a meditation for you?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Based on your mood patterns, I'll generate a personalized meditation session designed just for your current emotional state.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = onGenerateCustomMeditation,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.aurizen),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create My Meditation")
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    
    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Mood History") },
            text = { Text("Are you sure you want to delete all your mood entries? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MoodVisualization(moodHistory: List<MoodEntry>) {
    var selectedPeriod by remember { mutableStateOf(3) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with period selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mood Trend (Last $selectedPeriod Days)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                // Period dropdown
                var expanded by remember { mutableStateOf(false) }
                
                Box {
                    Card(
                        onClick = { expanded = true },
                        modifier = Modifier.wrapContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedPeriod}d",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(3, 7, 14).forEach { period ->
                            DropdownMenuItem(
                                text = { Text("${period} days") },
                                onClick = {
                                    selectedPeriod = period
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Get theme colors outside Canvas context
            val centerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            val dayBoundaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2
                
                // Get entries from selected period for intraday view
                val periodAgo = System.currentTimeMillis() - (selectedPeriod * 24 * 60 * 60 * 1000)
                val recentEntries = moodHistory.filter { it.timestamp >= periodAgo }
                    .sortedBy { it.timestamp }
                
                if (recentEntries.isEmpty()) return@Canvas
                
                // Calculate time range for positioning - use full period for proper scaling
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (selectedPeriod * 24 * 60 * 60 * 1000)
                val timeRange = endTime - startTime
                
                // Draw day boundary lines (vertical lines at midnight)
                for (day in 0..selectedPeriod) {
                    val dayStartTime = System.currentTimeMillis() - (day * 24 * 60 * 60 * 1000)
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = dayStartTime
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    
                    val dayBoundaryTime = calendar.timeInMillis
                    if (dayBoundaryTime >= startTime && dayBoundaryTime <= endTime) {
                        val x = ((dayBoundaryTime - startTime).toFloat() / timeRange) * width
                        drawLine(
                            color = dayBoundaryColor,
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 2f
                        )
                    }
                }
                
                // Draw horizontal center line
                drawLine(
                    color = centerLineColor,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 1f
                )
                
                // Draw mood entries as points and connect them
                for (i in recentEntries.indices) {
                    val entry = recentEntries[i]
                    val x = ((entry.timestamp - startTime).toFloat() / timeRange) * width
                    val score = MoodUtils.getMoodScore(entry.mood)
                    val normalizedScore = (score - 3f) / 2f // Normalize to -1 to 1 range
                    val y = centerY - (normalizedScore * centerY * 0.8f)
                    
                    // Connect with line to previous entry
                    if (i > 0) {
                        val prevEntry = recentEntries[i - 1]
                        val prevX = ((prevEntry.timestamp - startTime).toFloat() / timeRange) * width
                        val prevScore = MoodUtils.getMoodScore(prevEntry.mood)
                        val prevNormalizedScore = (prevScore - 3f) / 2f
                        val prevY = centerY - (prevNormalizedScore * centerY * 0.8f)
                        
                        drawLine(
                            color = MoodUtils.getMoodColor(entry.mood).copy(alpha = 0.6f),
                            start = Offset(prevX, prevY),
                            end = Offset(x, y),
                            strokeWidth = 2f
                        )
                    }
                    
                    // Draw mood point
                    drawCircle(
                        color = MoodUtils.getMoodColor(entry.mood),
                        radius = 8f,
                        center = Offset(x, y)
                    )
                    
                    // Draw inner white circle for better visibility
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(x, y)
                    )
                    
                    // Draw small colored center
                    drawCircle(
                        color = MoodUtils.getMoodColor(entry.mood),
                        radius = 2f,
                        center = Offset(x, y)
                    )
                }
                
                // Draw question marks for days with no mood tracking
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val existingDays = recentEntries.map { entry ->
                    dateFormat.format(Date(entry.timestamp))
                }.toSet()
                
                for (day in 0 until selectedPeriod) {
                    val dayStartTime = System.currentTimeMillis() - (day * 24 * 60 * 60 * 1000)
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = dayStartTime
                    calendar.set(Calendar.HOUR_OF_DAY, 12) // Middle of the day
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    
                    val dayString = dateFormat.format(Date(dayStartTime))
                    
                    // If this day has no mood entries, draw a question mark
                    if (!existingDays.contains(dayString)) {
                        val dayMiddleTime = calendar.timeInMillis
                        if (dayMiddleTime >= startTime && dayMiddleTime <= endTime) {
                            val x = ((dayMiddleTime - startTime).toFloat() / timeRange) * width
                            
                            // Draw question mark using native canvas
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                
                                drawText("?", x, centerY + 8f, paint)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Time labels for intraday view
            if (moodHistory.isNotEmpty()) {
                val periodAgo = System.currentTimeMillis() - (selectedPeriod * 24 * 60 * 60 * 1000)
                val recentEntries = moodHistory.filter { it.timestamp >= periodAgo }
                
                if (recentEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                        val today = Date()
                        
                        // Show start date and end date
                        val startDate = Date(today.time - (selectedPeriod - 1) * 24 * 60 * 60 * 1000)
                        
                        Text(
                            text = dayFormat.format(startDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                        
                        // Show middle date for longer periods
                        if (selectedPeriod > 7) {
                            val middleDate = Date(today.time - (selectedPeriod / 2) * 24 * 60 * 60 * 1000)
                            Text(
                                text = dayFormat.format(middleDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 9.sp
                            )
                        }
                        
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Mood color legend
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(listOf("ecstatic", "happy", "confident", "calm", "tired", "anxious", "stressed", "sad")) { mood ->
                    MoodLegendItem(
                        color = MoodUtils.getMoodColor(mood),
                        label = mood.replaceFirstChar { it.uppercase() }
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodLegendItem(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Color indicator circle
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MoodHistoryCard(entry: MoodEntry) {
    Card(
        modifier = Modifier.width(80.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color circle for mood
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MoodUtils.getMoodColor(entry.mood), CircleShape)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.mood.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MoodButton(
    text: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f), // Make it square
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                color.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, color)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Color circle
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

// Data classes are imported from MoodStorage.kt