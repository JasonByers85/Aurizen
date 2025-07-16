package com.aurizen.utils

import androidx.compose.ui.graphics.Color
import com.aurizen.data.MoodEntry

object MoodUtils {
    
    fun getMoodScore(mood: String): Float {
        return when (mood.lowercase()) {
            "ecstatic" -> 5f
            "happy" -> 4f
            "confident" -> 4f
            "calm" -> 3f
            "tired" -> 2f
            "anxious" -> 2f
            "stressed" -> 1.5f
            "sad" -> 1f
            else -> 2.5f // neutral
        }
    }
    
    fun getMoodColor(mood: String): Color {
        return when (mood.lowercase()) {
            "ecstatic" -> Color(0xFF4CAF50) // Bright green
            "happy" -> Color(0xFF8BC34A) // Light green
            "confident" -> Color(0xFF03A9F4) // Bright blue
            "calm" -> Color(0xFF00BCD4) // Cyan
            "tired" -> Color(0xFFFF9800) // Orange
            "anxious" -> Color(0xFFFF5722) // Deep orange
            "stressed" -> Color(0xFFF44336) // Red
            "sad" -> Color(0xFF9C27B0) // Purple
            else -> Color(0xFF9E9E9E) // Grey
        }
    }
    
    fun getMoodEmoji(mood: String): String {
        return when (mood.lowercase()) {
            "happy" -> "😊"
            "calm" -> "😌"
            "ecstatic" -> "🤩"
            "confident" -> "😎"
            "sad" -> "😔"
            "anxious" -> "😰"
            "tired" -> "😴"
            "stressed" -> "😫"
            else -> "😐"
        }
    }
    
    fun getMoodSuggestions(moodHistory: List<MoodEntry>): List<String> {
        if (moodHistory.isEmpty()) {
            return listOf(
                "Start tracking your mood to get personalized suggestions",
                "Try a short meditation session",
                "Take a few deep breaths"
            )
        }
        
        val recentMoods = moodHistory.takeLast(7)
        val averageScore = recentMoods.map { getMoodScore(it.mood) }.average()
        val averageEnergy = recentMoods.map { it.energyLevel }.average()
        val averageStress = recentMoods.map { it.stressLevel }.average()
        
        return when {
            averageScore < 2.5 -> listOf(
                "Consider a short walk outside",
                "Try a 5-minute breathing exercise",
                "Reach out to a friend or loved one",
                if (averageEnergy < 2.5) "Focus on rest and gentle activities" else "Channel your energy into positive actions",
                if (averageStress > 3.5) "Practice stress-reduction techniques" else "Listen to calming music"
            )
            averageScore > 3.5 -> listOf(
                "Great mood streak! Keep up your routine",
                "Share your positive energy with others",
                "Document what's working well for you",
                "Practice gratitude meditation",
                "Celebrate your progress"
            )
            else -> listOf(
                "Stay consistent with self-care",
                "Notice small positive moments",
                "Maintain healthy boundaries",
                "Try a balanced meditation session",
                "Focus on present moment awareness"
            )
        }
    }
    
    fun getMoodTrend(moodHistory: List<MoodEntry>): String {
        if (moodHistory.size < 2) return ""
        
        val recentMoods = moodHistory.takeLast(7)
        val olderMoods = moodHistory.dropLast(7).takeLast(7)
        
        if (olderMoods.isEmpty()) return "Building trend data"
        
        val recentAverage = recentMoods.map { getMoodScore(it.mood) }.average()
        val olderAverage = olderMoods.map { getMoodScore(it.mood) }.average()
        
        val difference = recentAverage - olderAverage
        
        return when {
            difference > 0.5 -> "📈 Trending up - Things are improving!"
            difference < -0.5 -> "📉 Trending down - View your mood insights"
            else -> "📊 Stable - Maintaining consistent patterns"
        }
    }
    
    fun shouldShowCheckInReminder(moodHistory: List<MoodEntry>): Boolean {
        if (moodHistory.isEmpty()) return true
        
        val lastEntry = moodHistory.maxByOrNull { it.timestamp }
        val daysSinceLastEntry = (System.currentTimeMillis() - (lastEntry?.timestamp ?: 0)) / (1000 * 60 * 60 * 24)
        
        return daysSinceLastEntry >= 2 // Show reminder after 2 days
    }
    
    fun getCheckInMessage(moodHistory: List<MoodEntry>): String {
        val daysSinceLastEntry = if (moodHistory.isEmpty()) {
            0
        } else {
            val lastEntry = moodHistory.maxByOrNull { it.timestamp }
            ((System.currentTimeMillis() - (lastEntry?.timestamp ?: 0)) / (1000 * 60 * 60 * 24)).toInt()
        }
        
        return when {
            moodHistory.isEmpty() -> "Hi! 🌟 Ready to start tracking your mood journey? I'm here to help you understand your emotional patterns."
            daysSinceLastEntry >= 7 -> "Hey there! 🌸 It's been a week since your last check-in. I hope you're doing well - how are you feeling today?"
            daysSinceLastEntry >= 3 -> "Hi! 🌼 I noticed you haven't tracked your mood in a few days. No pressure, but I'm here when you're ready to check in."
            else -> "Good to see you! 🌻 How has your mood been today?"
        }
    }
    
    // Utility function to generate dummy mood data for testing
    fun generateDummyMoodData(): List<MoodEntry> {
        val moods = listOf("ecstatic", "happy", "confident", "calm", "tired", "anxious", "stressed", "sad")
        val notes = listOf(
            "Had a great day at work",
            "Feeling productive today",
            "Enjoying some quiet time",
            "Bit overwhelmed with tasks",
            "Good workout session",
            "Relaxing evening",
            "Challenging day but managed well",
            "Feeling grateful",
            "Long day but rewarding",
            "Peaceful morning",
            ""
        )
        
        val entries = mutableListOf<MoodEntry>()
        val currentTime = System.currentTimeMillis()
        
        // Generate entries for the last 14 days
        for (daysBack in 0..13) {
            val dayTimestamp = currentTime - (daysBack * 24 * 60 * 60 * 1000)
            
            // Sometimes add multiple entries per day (30% chance)
            val entriesPerDay = if (kotlin.random.Random.nextFloat() < 0.3f) 2 else 1
            
            for (entryIndex in 0 until entriesPerDay) {
                val hourOffset = if (entriesPerDay == 1) {
                    kotlin.random.Random.nextInt(8, 20) // Single entry between 8am-8pm
                } else {
                    if (entryIndex == 0) kotlin.random.Random.nextInt(8, 14) // Morning entry
                    else kotlin.random.Random.nextInt(16, 22) // Evening entry
                }
                
                val timestamp = dayTimestamp + (hourOffset * 60 * 60 * 1000)
                val mood = moods.random()
                val note = notes.random()
                val energy = kotlin.random.Random.nextFloat() * 4 + 1 // 1-5 range
                val stress = kotlin.random.Random.nextFloat() * 4 + 1 // 1-5 range
                
                entries.add(MoodEntry(
                    mood = mood,
                    note = note,
                    timestamp = timestamp,
                    energyLevel = energy,
                    stressLevel = stress,
                    triggers = emptyList()
                ))
            }
        }
        
        return entries.sortedBy { it.timestamp }
    }
}