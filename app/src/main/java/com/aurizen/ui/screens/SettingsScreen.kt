package com.aurizen.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.*
import com.aurizen.data.MemoryStorage
import com.aurizen.data.MoodStorage
import com.aurizen.data.DreamStorage
import com.aurizen.data.PersonalGoalsStorage
import com.aurizen.data.UserProfile
import com.aurizen.data.UserMemory
import com.aurizen.ui.theme.ThemeManager
import com.aurizen.ui.theme.ThemeMode

@Composable
internal fun SettingsRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val memoryStorage = remember { MemoryStorage.getInstance(context) }
    val moodStorage = remember { MoodStorage.getInstance(context) }
    val dreamStorage = remember { DreamStorage.getInstance(context) }
    val goalsStorage = remember { PersonalGoalsStorage.getInstance(context) }
    val userProfile = remember { UserProfile.getInstance(context) }
    val themeManager = remember { ThemeManager.getInstance(context) }
    
    SettingsScreen(
        memoryStorage = memoryStorage,
        moodStorage = moodStorage,
        dreamStorage = dreamStorage,
        goalsStorage = goalsStorage,
        userProfile = userProfile,
        themeManager = themeManager,
        context = context,
        onBack = onBack
    )
}

@Composable
fun SettingsScreen(
    memoryStorage: MemoryStorage,
    moodStorage: MoodStorage,
    dreamStorage: DreamStorage,
    goalsStorage: PersonalGoalsStorage,
    userProfile: UserProfile,
    themeManager: ThemeManager,
    context: Context,
    onBack: () -> Unit
) {
    var memories by remember { mutableStateOf(memoryStorage.getAllMemories()) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showEditMemoryDialog by remember { mutableStateOf<UserMemory?>(null) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Theme Selection Section
        ThemeSelectionSection(themeManager = themeManager)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Memory Management Section
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stored Memories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Button(
                        onClick = { showAddMemoryDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Memory")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (memories.isEmpty()) {
                    Text(
                        text = "No memories stored yet.\n\nTell AuriZen to \"remember\" something and it will appear here for you to manage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "${memories.size} memories stored",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(memories) { memory ->
                            MemoryItem(
                                memory = memory,
                                onEdit = { showEditMemoryDialog = memory },
                                onDelete = { memoryToDelete ->
                                    // Delete from storage using proper method
                                    memoryStorage.deleteMemory(memoryToDelete.id)
                                    // Update local list to reflect the change
                                    memories = memories.filter { it.id != memoryToDelete.id }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Delete All Data Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Delete all stored data including memories, moods, dreams, personal goals, and conversation context",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete All Data")
                }
            }
        }
    }
    
    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = {
                Text("Delete All App Data?")
            },
            text = {
                Column {
                    Text("This will permanently delete ALL stored data including:")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("• All stored memories and personal information")
                    Text("• Mood tracking history")
                    Text("• Dream journal entries")
                    Text("• Personal goals and progress")
                    Text("• AI conversation context and learned preferences")
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        "This action cannot be undone. AuriZen will be reset to a fresh state.",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Clear all data
                        memoryStorage.clearAllMemories()
                        moodStorage.clearAllMoodEntries()
                        dreamStorage.clearAllDreamEntries()
                        goalsStorage.clearAllGoals()
                        userProfile.clearProfile(context)

                        memories = emptyList()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Add Memory Dialog
    if (showAddMemoryDialog) {
        AddMemoryDialog(
            onSave = { memoryText ->
                memoryStorage.storeMemory(memoryText)
                memories = memoryStorage.getAllMemories()
                showAddMemoryDialog = false
            },
            onDismiss = { showAddMemoryDialog = false }
        )
    }
    
    // Edit Memory Dialog
    showEditMemoryDialog?.let { memory ->
        EditMemoryDialog(
            memory = memory,
            onSave = { updatedMemory ->
                // Update in storage
                memoryStorage.updateMemory(updatedMemory)
                
                // Update local list to reflect the change
                memories = memoryStorage.getAllMemories()
                
                showEditMemoryDialog = null
            },
            onDismiss = { showEditMemoryDialog = null }
        )
    }
}

@Composable
private fun MemoryItem(
    memory: UserMemory,
    onEdit: () -> Unit,
    onDelete: (UserMemory) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = memory.memory,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(memory.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Memory?") },
            text = { Text("Are you sure you want to delete this memory?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(memory)
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
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddMemoryDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var memoryText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Memory") },
        text = {
            Column {
                OutlinedTextField(
                    value = memoryText,
                    onValueChange = { memoryText = it },
                    label = { Text("Memory") },
                    placeholder = { Text("Enter information for AuriZen to remember...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(memoryText.trim())
                },
                enabled = memoryText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditMemoryDialog(
    memory: UserMemory,
    onSave: (UserMemory) -> Unit,
    onDismiss: () -> Unit
) {
    var memoryText by remember { mutableStateOf(memory.memory) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Memory") },
        text = {
            Column {
                OutlinedTextField(
                    value = memoryText,
                    onValueChange = { memoryText = it },
                    label = { Text("Memory") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        memory.copy(
                            memory = memoryText.trim()
                        )
                    )
                },
                enabled = memoryText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ThemeSelectionSection(themeManager: ThemeManager) {
    val currentTheme by themeManager.getThemeState()
    var showThemeDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "App Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = themeManager.getThemeDisplayName(currentTheme),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = themeManager.getThemeDescription(currentTheme),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Button(
                    onClick = { showThemeDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Change Theme")
                }
            }
        }
    }
    
    // Theme Selection Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { newTheme ->
                themeManager.setTheme(newTheme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
            themeManager = themeManager
        )
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    themeManager: ThemeManager
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(themeManager.getAllThemes()) { theme ->
                    ThemeOptionItem(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onSelected = { onThemeSelected(theme) },
                        themeManager = themeManager
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ThemeOptionItem(
    theme: ThemeMode,
    isSelected: Boolean,
    onSelected: () -> Unit,
    themeManager: ThemeManager
) {
    Card(
        onClick = onSelected,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = themeManager.getThemeDisplayName(theme),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Text(
                    text = themeManager.getThemeDescription(theme),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}