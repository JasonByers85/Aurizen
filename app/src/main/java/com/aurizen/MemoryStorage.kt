package com.aurizen

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

data class UserMemory(
    val id: String = UUID.randomUUID().toString(),
    val memory: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MemoryStorage private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences
    private val gson = Gson()

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "user_memories_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun storeMemory(memoryText: String) {
        val memories = getAllMemories().toMutableList()
        val newMemory = UserMemory(memory = memoryText.trim())
        memories.add(newMemory)
        
        val json = gson.toJson(memories)
        sharedPreferences.edit().putString(MEMORIES_KEY, json).apply()
    }

    fun getAllMemories(): List<UserMemory> {
        val json = sharedPreferences.getString(MEMORIES_KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<UserMemory>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun updateMemory(updatedMemory: UserMemory) {
        val memories = getAllMemories().toMutableList()
        val index = memories.indexOfFirst { it.id == updatedMemory.id }
        
        if (index != -1) {
            memories[index] = updatedMemory
            val json = gson.toJson(memories)
            sharedPreferences.edit().putString(MEMORIES_KEY, json).apply()
        }
    }

    fun deleteMemory(memoryId: String) {
        val memories = getAllMemories().filter { it.id != memoryId }
        val json = gson.toJson(memories)
        sharedPreferences.edit().putString(MEMORIES_KEY, json).apply()
    }

    fun clearAllMemories() {
        sharedPreferences.edit().remove(MEMORIES_KEY).apply()
    }

    companion object {
        private const val MEMORIES_KEY = "user_memories"
        
        @Volatile
        private var INSTANCE: MemoryStorage? = null

        fun getInstance(context: Context): MemoryStorage {
            return INSTANCE ?: synchronized(this) {
                val instance = MemoryStorage(context)
                INSTANCE = instance
                instance
            }
        }
    }
}