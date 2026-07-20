package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "model"
    val text: String,
    val messageType: String = "text", // "text", "image", "video"
    val mediaUri: String? = null, // Local Uri or download path for generated images/videos
    val timestamp: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false,
    val hasVoice: Boolean = false
)

@Entity(tableName = "offline_stories")
data class OfflineStory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "general"
)
