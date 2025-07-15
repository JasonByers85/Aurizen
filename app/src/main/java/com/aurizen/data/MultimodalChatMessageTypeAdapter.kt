package com.aurizen.data

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class MultimodalChatMessageTypeAdapter : JsonSerializer<MultimodalChatMessage>, JsonDeserializer<MultimodalChatMessage> {
    
    override fun serialize(src: MultimodalChatMessage?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val jsonObject = JsonObject()
        
        when (src) {
            is MultimodalChatMessage.TextMessage -> {
                jsonObject.addProperty("type", "TEXT")
                jsonObject.addProperty("content", src.content)
                jsonObject.addProperty("messageSide", src.messageSide.name)
                jsonObject.addProperty("messageId", src.messageId)
                jsonObject.addProperty("messageTimestamp", src.messageTimestamp)
            }
            is MultimodalChatMessage.AudioClipMessage -> {
                jsonObject.addProperty("type", "AUDIO_CLIP")
                jsonObject.addProperty("audioData", android.util.Base64.encodeToString(src.audioData, android.util.Base64.DEFAULT))
                jsonObject.addProperty("sampleRate", src.sampleRate)
                jsonObject.addProperty("messageSide", src.messageSide.name)
                jsonObject.addProperty("messageId", src.messageId)
                jsonObject.addProperty("messageTimestamp", src.messageTimestamp)
                jsonObject.addProperty("transcription", src.transcription)
                jsonObject.addProperty("filePath", src.filePath)
            }
            null -> return JsonNull.INSTANCE
        }
        
        return jsonObject
    }
    
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MultimodalChatMessage {
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("Invalid JSON")
        
        val type = jsonObject.get("type")?.asString ?: throw JsonParseException("Missing type field")
        
        return when (type) {
            "TEXT" -> {
                val content = jsonObject.get("content")?.asString ?: ""
                val messageSide = MessageSide.valueOf(jsonObject.get("messageSide")?.asString ?: "USER")
                val messageId = jsonObject.get("messageId")?.asString ?: java.util.UUID.randomUUID().toString()
                val messageTimestamp = jsonObject.get("messageTimestamp")?.asLong ?: System.currentTimeMillis()
                
                MultimodalChatMessage.TextMessage(
                    content = content,
                    messageSide = messageSide,
                    messageId = messageId,
                    messageTimestamp = messageTimestamp
                )
            }
            "AUDIO_CLIP" -> {
                val audioDataString = jsonObject.get("audioData")?.asString ?: ""
                val audioData = android.util.Base64.decode(audioDataString, android.util.Base64.DEFAULT)
                val sampleRate = jsonObject.get("sampleRate")?.asInt ?: 16000
                val messageSide = MessageSide.valueOf(jsonObject.get("messageSide")?.asString ?: "USER")
                val messageId = jsonObject.get("messageId")?.asString ?: java.util.UUID.randomUUID().toString()
                val messageTimestamp = jsonObject.get("messageTimestamp")?.asLong ?: System.currentTimeMillis()
                val transcription = jsonObject.get("transcription")?.asString
                val filePath = jsonObject.get("filePath")?.asString
                
                MultimodalChatMessage.AudioClipMessage(
                    audioData = audioData,
                    sampleRate = sampleRate,
                    messageSide = messageSide,
                    messageId = messageId,
                    messageTimestamp = messageTimestamp,
                    transcription = transcription,
                    filePath = filePath
                )
            }
            else -> throw JsonParseException("Unknown message type: $type")
        }
    }
}