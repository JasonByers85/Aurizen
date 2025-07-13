package com.aurizen

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

enum class ChatMessageType {
    TEXT,
    AUDIO_CLIP
}

enum class MessageSide {
    USER,
    ASSISTANT,
    SYSTEM
}

sealed class MultimodalChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val side: MessageSide,
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatMessageType
) {
    
    data class TextMessage(
        val content: String,
        val messageSide: MessageSide,
        val messageId: String = UUID.randomUUID().toString(),
        val messageTimestamp: Long = System.currentTimeMillis()
    ) : MultimodalChatMessage(messageId, messageSide, messageTimestamp, ChatMessageType.TEXT)
    
    data class AudioClipMessage(
        val audioData: ByteArray,
        val sampleRate: Int = 16000, // Default to 16kHz
        val messageSide: MessageSide,
        val messageId: String = UUID.randomUUID().toString(),
        val messageTimestamp: Long = System.currentTimeMillis(),
        val transcription: String? = null,
        val filePath: String? = null
    ) : MultimodalChatMessage(messageId, messageSide, messageTimestamp, ChatMessageType.AUDIO_CLIP) {
        
        fun getDurationInSeconds(): Float {
            // Assuming 16-bit mono audio
            val bytesPerSample = 2
            val totalSamples = audioData.size / bytesPerSample
            return totalSamples.toFloat() / sampleRate
        }
        
        fun genByteArrayForWav(): ByteArray {
            val audioLength = audioData.size
            val totalDataLen = audioLength + 36
            val channels = 1 // Mono
            val byteRate = sampleRate * channels * 2 // 16-bit
            
            val headerBuffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            
            // RIFF header
            headerBuffer.put("RIFF".toByteArray())
            headerBuffer.putInt(totalDataLen)
            headerBuffer.put("WAVE".toByteArray())
            
            // FORMAT sub-chunk
            headerBuffer.put("fmt ".toByteArray())
            headerBuffer.putInt(16) // Sub-chunk size
            headerBuffer.putShort(1) // Audio format (PCM)
            headerBuffer.putShort(channels.toShort())
            headerBuffer.putInt(sampleRate)
            headerBuffer.putInt(byteRate)
            headerBuffer.putShort((channels * 2).toShort()) // Block align
            headerBuffer.putShort(16) // Bits per sample
            
            // DATA sub-chunk
            headerBuffer.put("data".toByteArray())
            headerBuffer.putInt(audioLength)
            
            // Combine header and audio data
            val outputStream = ByteArrayOutputStream()
            outputStream.write(headerBuffer.array())
            outputStream.write(audioData)
            
            return outputStream.toByteArray()
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AudioClipMessage
            return audioData.contentEquals(other.audioData) &&
                    sampleRate == other.sampleRate &&
                    messageSide == other.messageSide &&
                    messageId == other.messageId
        }
        
        override fun hashCode(): Int {
            var result = audioData.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + messageSide.hashCode()
            result = 31 * result + messageId.hashCode()
            return result
        }
    }
    
}

// Extension functions for easier migration from simple ChatMessage
fun MultimodalChatMessage.isFromUser(): Boolean = side == MessageSide.USER

fun MultimodalChatMessage.getDisplayContent(): String {
    return when (this) {
        is MultimodalChatMessage.TextMessage -> content
        is MultimodalChatMessage.AudioClipMessage -> transcription ?: "🎤 Voice message (${String.format("%.1f", getDurationInSeconds())}s)"
    }
}