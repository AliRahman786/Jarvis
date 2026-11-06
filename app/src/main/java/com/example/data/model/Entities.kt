package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val category: String = CATEGORY_FACT, // FACT, PREFERENCE, PROJECT, SYSTEM, USER
    val source: String = "CONVERSATION",
    val tags: String = "",
    val importance: Int = 1, // 1 to 5
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CATEGORY_FACT = "FACT"
        const val CATEGORY_PREFERENCE = "PREFERENCE"
        const val CATEGORY_PROJECT = "PROJECT"
        const val CATEGORY_SYSTEM = "SYSTEM"
        const val CATEGORY_USER = "USER"
    }
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String = "default",
    val sender: String, // USER, JARVIS, SYSTEM, TOOL
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val imageUrl: String? = null
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val dueTimestamp: Long,
    val isCompleted: Boolean = false,
    val priority: String = "NORMAL",
    val createdAt: Long = System.currentTimeMillis()
)
