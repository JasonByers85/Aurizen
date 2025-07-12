package com.aurizen

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ModelDownloader(private val context: Context) {
    private val client = OkHttpClient()
    private var currentCall: Call? = null
    private val TAG = "ModelDownloader"

    suspend fun downloadModel(
        model: Model,
        onProgressUpdate: (Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting download: ${model.url}")

        // Use internal storage directly - no DownloadManager needed
        val outputFile = File(context.filesDir, model.path)

        // Delete existing file if it exists
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // Ensure directory exists
        outputFile.parentFile?.mkdirs()

        try {
            val requestBuilder = Request.Builder().url(model.url)

            // Add authentication if needed
            if (model.needsAuth) {
                val token = SecureStorage.getToken(context)
                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                    Log.d(TAG, "Added auth header for download")
                } else {
                    Log.e(TAG, "No auth token found")
                    onComplete(false, "HuggingFace authentication required. Please set up your token.")
                    return@withContext
                }
            }

            val request = requestBuilder.build()
            currentCall = client.newCall(request)

            val response = currentCall?.execute()
            if (response?.isSuccessful != true) {
                val errorMsg = "Download failed: HTTP ${response?.code}"
                Log.e(TAG, errorMsg)
                onComplete(false, errorMsg)
                return@withContext
            }

            val responseBody = response.body
            if (responseBody == null) {
                Log.e(TAG, "Response body is null")
                onComplete(false, "Download failed: Empty response")
                return@withContext
            }

            val contentLength = responseBody.contentLength()
            Log.d(TAG, "Content length: $contentLength bytes")
            Log.d(TAG, "Downloading to: ${outputFile.absolutePath}")

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastProgress = -1

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Check if cancelled
                        if (!isActive) {
                            Log.d(TAG, "Download cancelled")
                            outputFile.delete()
                            onComplete(false, "Download cancelled")
                            return@withContext
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        val progress = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt()
                        } else {
                            0
                        }

                        // Only update UI if progress changed significantly
                        if (progress != lastProgress && progress >= 0) {
                            withContext(Dispatchers.Main) {
                                onProgressUpdate(progress)
                            }
                            lastProgress = progress

                            // Log progress every 10%
                            if (progress % 10 == 0 && progress > 0) {
                                Log.d(TAG, "Download progress: $progress% ($totalBytesRead/$contentLength bytes)")
                            }
                        }
                    }
                    outputStream.flush()
                }
            }

            Log.d(TAG, "Download completed successfully. File size: ${outputFile.length()} bytes")

            // Verify file was created and has content
            if (outputFile.exists() && outputFile.length() > 0) {
                onComplete(true, null)
            } else {
                Log.e(TAG, "Downloaded file is missing or empty")
                onComplete(false, "Downloaded file verification failed")
            }

        } catch (e: IOException) {
            Log.e(TAG, "Download failed with IOException", e)
            outputFile.delete() // Clean up partial file
            onComplete(false, "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed with exception", e)
            outputFile.delete() // Clean up partial file
            onComplete(false, "Download error: ${e.message}")
        }
    }

    fun cancelDownload() {
        Log.d(TAG, "Cancelling download")
        currentCall?.cancel()
    }
}