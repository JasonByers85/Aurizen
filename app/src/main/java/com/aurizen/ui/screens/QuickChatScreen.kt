package com.aurizen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurizen.viewmodels.QuickChatViewModel

@Composable
internal fun QuickChatRoute(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: QuickChatViewModel = viewModel(factory = QuickChatViewModel.getFactory(context))

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val response by viewModel.response.collectAsStateWithLifecycle()
    val isInputEnabled by viewModel.isInputEnabled.collectAsStateWithLifecycle()

    QuickChatScreen(
        isLoading = isLoading,
        response = response,
        isInputEnabled = isInputEnabled,
        onSendMessage = viewModel::sendMessage,
        onBack = onBack
    )
}

@Composable
fun QuickChatScreen(
    isLoading: Boolean,
    response: String,
    isInputEnabled: Boolean,
    onSendMessage: (String) -> Unit,
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
                    text = "Quick Support",
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
                    text = "💡 Ask me anything about stress management, wellness tips, or how you're feeling. Each conversation is independent and private.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Quick action buttons (only show when not loading and no response)
            if (!isLoading && response.isEmpty()) {
                QuickActionButtons { action ->
                    onSendMessage(action)
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
                            Text(
                                text = "Share what's on your mind and I'll provide personalized wellness support and advice.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // Extra space to ensure input area is visible
            Spacer(modifier = Modifier.height(80.dp))
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
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text("How are you feeling? What's on your mind?") },
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 4,
                        enabled = isInputEnabled,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                onSendMessage(userInput)
                                userInput = ""
                            }
                        },
                        enabled = isInputEnabled && userInput.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.Send,
                            contentDescription = "Send",
                            tint = if (isInputEnabled && userInput.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButtons(onActionSelected: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Quick Actions:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onActionSelected("I'm feeling stressed and need help managing it") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Stress Help", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { onActionSelected("I'm having trouble sleeping and need advice") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Sleep Tips", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onActionSelected("I need motivation and positive encouragement") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Motivation", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = { onActionSelected("Give me a quick wellness tip for today") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Daily Tip", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}