package com.aurizen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

@Composable
internal fun DreamInterpreterRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: DreamInterpreterViewModel = viewModel(factory = DreamInterpreterViewModel.getFactory(context))

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val interpretation by viewModel.interpretation.collectAsStateWithLifecycle()
    val isInputEnabled by viewModel.isInputEnabled.collectAsStateWithLifecycle()
    val dreamHistory by viewModel.dreamHistory.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    DreamInterpreterScreen(
        isLoading = isLoading,
        interpretation = interpretation,
        isInputEnabled = isInputEnabled,
        dreamHistory = dreamHistory,
        selectedTab = selectedTab,
        onInterpretDream = viewModel::interpretDream,
        onDreamDeleted = viewModel::deleteDreamEntry,
        onRegenerateInterpretation = viewModel::regenerateInterpretation,
        onTabSelected = viewModel::setSelectedTab,
        onBack = onBack
    )
}

@Composable
fun DreamInterpreterScreen(
    isLoading: Boolean,
    interpretation: String,
    isInputEnabled: Boolean,
    dreamHistory: List<DreamEntry>,
    selectedTab: Int,
    onInterpretDream: (String) -> Unit,
    onDreamDeleted: (DreamEntry) -> Unit,
    onRegenerateInterpretation: (DreamEntry) -> Unit,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var dreamDescription by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when content changes
    LaunchedEffect(isLoading, interpretation) {
        if (interpretation.isNotEmpty() || isLoading) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
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
                    text = "Dream Interpreter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Tab row
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text("Interpreter") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text("Dream Diary") }
            )
        }

        // Tab content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> DreamInterpreterTab(
                    isLoading = isLoading,
                    interpretation = interpretation,
                    isInputEnabled = isInputEnabled,
                    dreamHistory = dreamHistory,
                    onInterpretDream = onInterpretDream,
                    onDreamDeleted = onDreamDeleted,
                    scrollState = scrollState,
                    dreamDescription = dreamDescription,
                    onDreamDescriptionChange = { dreamDescription = it }
                )
                1 -> DreamDiaryTab(
                    dreamHistory = dreamHistory,
                    onDreamDeleted = onDreamDeleted,
                    onRegenerateInterpretation = onRegenerateInterpretation,
                    onTabSelected = onTabSelected
                )
            }
        }
    }
}

@Composable
private fun DreamInterpreterTab(
    isLoading: Boolean,
    interpretation: String,
    isInputEnabled: Boolean,
    dreamHistory: List<DreamEntry>,
    onInterpretDream: (String) -> Unit,
    onDreamDeleted: (DreamEntry) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    dreamDescription: String,
    onDreamDescriptionChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Dream interpretation guidance (only show when not loading and no interpretation)
            if (!isLoading && interpretation.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "💭 Understanding Your Dreams",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Dreams are windows into your subconscious mind, revealing hidden thoughts, emotions, and desires that influence your waking life. Through symbols, metaphors, and narratives, your mind processes experiences, fears, hopes, and unresolved conflicts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Each dream element carries potential meaning - from people and places to emotions and actions. By exploring these connections, you can gain insights into your psychological state, relationships, personal growth, and inner wisdom.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Share your dream in detail below, and let's explore what your subconscious might be communicating to you.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Interpretation area
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
                                Text("Interpreting your dream...")
                            }
                        }
                        interpretation.isNotEmpty() -> {
                            Text(
                                text = interpretation,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> {
                            Text(
                                text = "Your dream interpretation will appear here...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Center),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Extra space to ensure input area is visible
            Spacer(modifier = Modifier.height(120.dp))
        }

        // Input area - Fixed at bottom
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    TextField(
                        value = dreamDescription,
                        onValueChange = onDreamDescriptionChange,
                        label = { Text("Describe your dream") },
                        placeholder = { Text("Tell me about your dream in as much detail as you remember...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6,
                        enabled = isInputEnabled,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDreamDescriptionChange("") },
                            modifier = Modifier.weight(1f),
                            enabled = dreamDescription.isNotEmpty()
                        ) {
                            Text("Clear")
                        }

                        Button(
                            onClick = {
                                if (dreamDescription.isNotBlank()) {
                                    onInterpretDream(dreamDescription)
                                    onDreamDescriptionChange("")
                                }
                            },
                            modifier = Modifier.weight(2f),
                            enabled = isInputEnabled && dreamDescription.isNotBlank()
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.aurizen),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Interpret Dream")
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun DreamDiaryTab(
    dreamHistory: List<DreamEntry>,
    onDreamDeleted: (DreamEntry) -> Unit,
    onRegenerateInterpretation: (DreamEntry) -> Unit,
    onTabSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (dreamHistory.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📔 Your Dream Diary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your interpreted dreams will appear here. Start by interpreting your first dream!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { onTabSelected(0) }) {
                        Text("Interpret a Dream")
                    }
                }
            }
        } else {
            // Dream diary entries
            Text(
                text = "📔 Dream Diary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Group dreams by month/year
            val groupedDreams = dreamHistory.groupBy { dream ->
                try {
                    val date = if (dream.timestamp > 0) java.util.Date(dream.timestamp) else java.util.Date()
                    val formatter = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                    formatter.format(date) ?: "Unknown Date"
                } catch (e: Exception) {
                    "Unknown Date"
                }
            }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for ((monthYear, dreams) in groupedDreams) {
                    item {
                        // Month/year header
                        Text(
                            text = monthYear,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(dreams) { dream ->
                        DreamDiaryCard(
                            dream = dream,
                            onDreamDeleted = onDreamDeleted,
                            onRegenerateInterpretation = onRegenerateInterpretation,
                            onTabSelected = onTabSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DreamDiaryCard(
    dream: DreamEntry,
    onDreamDeleted: (DreamEntry) -> Unit,
    onRegenerateInterpretation: (DreamEntry) -> Unit,
    onTabSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Date
            val dateText = try {
                val date = if (dream.timestamp > 0) java.util.Date(dream.timestamp) else java.util.Date()
                val dateFormatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                dateFormatter.format(date) ?: "Unknown Date"
            } catch (e: Exception) {
                "Unknown Date"
            }
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Original dream description
            val dreamDescription = try {
                val desc = dream.description ?: ""
                if (desc.length > 150) desc.take(150) + "..." else desc
            } catch (e: Exception) {
                "Dream entry"
            }
            Text(
                text = dreamDescription,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            // AI summary (if available)
            if (dream.summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Summary: ${dream.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { 
                        onRegenerateInterpretation(dream)
                        onTabSelected(0) // Switch back to interpreter tab
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reinterpret", style = MaterialTheme.typography.labelSmall)
                }
                
                IconButton(
                    onClick = { onDreamDeleted(dream) }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete dream",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Data class for dream entries
data class DreamEntry(
    val description: String,
    val interpretation: String,
    val timestamp: Long,
    val summary: String = "" // Brief one-sentence summary for diary view
)