package com.aurizen.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import kotlinx.coroutines.*
import java.io.File
import com.aurizen.core.InferenceModel
import com.aurizen.core.ModelDownloader
import com.aurizen.core.Model
import com.aurizen.core.ModelLoadFailException
import com.aurizen.core.ModelSessionCreateFailException
import com.aurizen.activities.LoginActivity
import com.aurizen.R

private const val TAG = "LoadingScreen"

@Composable
internal fun LoadingRoute(
    onModelLoaded: () -> Unit = {},
    onGoBack: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    var errorMessage by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloader: ModelDownloader? by remember { mutableStateOf(null) }

    // UI State Management
    if (errorMessage.isNotEmpty()) {
        ErrorMessage(errorMessage, onGoBack)
    } else if (isDownloading) {
        DownloadIndicator(progress) {
            downloader?.cancelDownload()
            isDownloading = false
            errorMessage = "Download Cancelled"
        }
    } else {
        LoadingIndicator()
    }

    // Main Loading Logic
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting model loading process...")

                // Check if model already exists
                val modelExists = InferenceModel.modelExists(context)
                Log.d(TAG, "Model exists: $modelExists")

                if (!modelExists) {
                    withContext(Dispatchers.Main) {
                        isDownloading = true
                    }

                    Log.d(TAG, "Starting download...")
                    downloader = ModelDownloader(context)

                    // Download with OkHttp directly to internal storage
                    val downloadResult = downloadModelSafely(
                        downloader = downloader!!,
                        model = InferenceModel.model,
                        onProgressUpdate = { newProgress ->
                            progress = newProgress
                        }
                    )

                    if (!downloadResult.success) {
                        throw Exception(downloadResult.error ?: "Download failed")
                    }

                    Log.d(TAG, "Download completed")
                }

                // Verify model exists
                if (!InferenceModel.modelExists(context)) {
                    throw Exception("Model file not found after download")
                }

                Log.d(TAG, "Creating InferenceModel...")
                InferenceModel.resetInstance(context)
                Log.d(TAG, "Model loaded successfully")

                // Success
                withContext(Dispatchers.Main) {
                    onModelLoaded()
                }

            } catch (e: ModelSessionCreateFailException) {
                Log.e(TAG, "Model session creation failed", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to create model session"
                }
            } catch (e: ModelLoadFailException) {
                Log.e(TAG, "Model load failed", e)
                deleteModelFile(context)
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load model"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Loading error", e)
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: "Unknown error"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                }
            }
        }
    }
}

// Safe download function using OkHttp for internal storage
private suspend fun downloadModelSafely(
    downloader: ModelDownloader,
    model: Model,
    onProgressUpdate: (Int) -> Unit
): DownloadResult = withContext(Dispatchers.IO) {

    var result = DownloadResult(false, null)
    val job = launch {
        downloader.downloadModel(
            model = model,
            onProgressUpdate = onProgressUpdate,
            onComplete = { success, error ->
                result = DownloadResult(success, error)
            }
        )
    }

    // Wait for download to complete
    job.join()
    result
}

private data class DownloadResult(
    val success: Boolean,
    val error: String?
)

private fun deleteModelFile(context: Context) {
    try {
        val modelFile = File(context.filesDir, InferenceModel.model.path)
        if (modelFile.exists()) {
            modelFile.delete()
            Log.d(TAG, "Deleted invalid model file")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting model file", e)
    }
}

@Composable
fun DownloadIndicator(progress: Int, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // AuriZen Logo
        Image(
            painter = painterResource(id = R.drawable.aurizen_logo),
            contentDescription = "AuriZen Logo",
            modifier = Modifier
                .height(90.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        
        Text(
            text = "Downloading AI Model...",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (progress > 0) {
            Text(
                text = "Progress: $progress%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        } else {
            Text(
                text = "Preparing download...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CircularProgressIndicator(
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Text(
            text = "Download continues in the background and won't be interrupted by phone sleep.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(onClick = onCancel) {
            Text("Cancel Download")
        }
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // Large AuriZen Logo
        Image(
            painter = painterResource(id = R.drawable.aurizen_logo),
            contentDescription = "AuriZen Logo",
            modifier = Modifier
                .height(110.dp)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        
        Text(
            text = "Loading...",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Setting up your AI wellness companion",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
    }
}

@Composable
fun ErrorMessage(
    errorMessage: String,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    val isAuthError = errorMessage.contains("HuggingFace authentication required", ignoreCase = true) ||
                     errorMessage.contains("auth token", ignoreCase = true)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        // AuriZen Logo
        Image(
            painter = painterResource(id = R.drawable.aurizen_logo),
            contentDescription = "AuriZen Logo",
            modifier = Modifier
                .height(90.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        
        Text(
            text = if (isAuthError) "Authentication Required" else "Oops! Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (isAuthError) {
            Text(
                text = "AuriZen needs access to download the AI model from Hugging Face.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "This is a one-time setup to enable your AI wellness companion.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            Button(
                onClick = {
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Login to Hugging Face")
            }
            
            OutlinedButton(onClick = onGoBack) {
                Text("Cancel")
            }
        } else {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = "Check the logs for more details, or try again.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onGoBack) {
                Text("Try Again")
            }
        }
    }
}