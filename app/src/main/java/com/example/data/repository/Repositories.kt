package com.example.data.repository

import com.example.data.dao.ConversationDao
import com.example.data.dao.MemoryDao
import com.example.data.dao.ReminderDao
import com.example.data.model.ConversationEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.ReminderEntity
import com.example.data.prefs.PreferencesManager
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    val memoryCount: Flow<Int> = memoryDao.count()

    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesByCategory(category)

    suspend fun searchMemories(query: String): List<MemoryEntity> =
        memoryDao.searchMemories(query)

    suspend fun getRecentMemories(limit: Int = 10): List<MemoryEntity> =
        memoryDao.getRecentMemories(limit)

    suspend fun saveMemory(content: String, category: String = MemoryEntity.CATEGORY_FACT, tags: String = "", importance: Int = 1): Long {
        val memory = MemoryEntity(
            content = content,
            category = category,
            tags = tags,
            importance = importance,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return memoryDao.insertMemory(memory)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun clearAll() {
        memoryDao.clearAll()
    }
}

class ConversationRepository(private val conversationDao: ConversationDao) {
    val currentMessages: Flow<List<ConversationEntity>> = conversationDao.getSessionMessages()
    val messageCount: Flow<Int> = conversationDao.count()

    suspend fun getRecentHistory(limit: Int = 15): List<ConversationEntity> =
        conversationDao.getRecentMessages(limit).reversed()

    suspend fun addMessage(
        sender: String,
        content: String,
        toolName: String? = null,
        toolArgs: String? = null,
        toolResult: String? = null,
        imageUrl: String? = null
    ): Long {
        val msg = ConversationEntity(
            sender = sender,
            content = content,
            toolName = toolName,
            toolArgs = toolArgs,
            toolResult = toolResult,
            imageUrl = imageUrl
        )
        return conversationDao.insertMessage(msg)
    }

    suspend fun clearHistory() {
        conversationDao.clearAll()
    }
}

class ReminderRepository(private val reminderDao: ReminderDao) {
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()

    suspend fun addReminder(title: String, dueTimestamp: Long, priority: String = "NORMAL"): Long {
        val reminder = ReminderEntity(
            title = title,
            dueTimestamp = dueTimestamp,
            priority = priority
        )
        return reminderDao.insertReminder(reminder)
    }

    suspend fun completeReminder(id: Long) {
        reminderDao.markCompleted(id)
    }

    suspend fun deleteReminder(id: Long) {
        reminderDao.deleteById(id)
    }
}

class StorageManager(
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val prefs: PreferencesManager
) {
    suspend fun clearLocalData() {
        memoryRepo.clearAll()
        conversationRepo.clearHistory()
    }
}
