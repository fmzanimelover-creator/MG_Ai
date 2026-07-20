package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    @Query("SELECT * FROM offline_stories")
    suspend fun getAllStories(): List<OfflineStory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: OfflineStory)

    @Query("SELECT COUNT(*) FROM offline_stories")
    suspend fun getStoriesCount(): Int
}
