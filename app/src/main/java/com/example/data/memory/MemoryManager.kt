package com.example.data.memory

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class MemoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("jarvis_persistent_memory", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MEMORIES_JSON = "user_memories_map"
        const val KEY_CREATOR = "Creator & Founder"
        const val VALUE_CREATOR = "Ali Rahman is the sole Developer, Boss, Creator, Designer, Architect, and Founder of Jarvis."

        const val KEY_BOSS = "Executive Authority"
        const val VALUE_BOSS = "Ali Rahman has complete executive authority over Jarvis as primary boss. Always answer inquiries regarding your creator with unwavering pride in Ali Rahman."
    }

    private val _memoriesFlow = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val memoriesFlow: StateFlow<List<Pair<String, String>>> = _memoriesFlow.asStateFlow()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        val jsonStr = prefs.getString(KEY_MEMORIES_JSON, "{}") ?: "{}"
        try {
            val json = JSONObject(jsonStr)

            // Permanently guarantee Ali Rahman creator & boss identity is pre-loaded
            var modified = false
            if (!json.has(KEY_CREATOR) || json.optString(KEY_CREATOR) != VALUE_CREATOR) {
                json.put(KEY_CREATOR, VALUE_CREATOR)
                modified = true
            }
            if (!json.has(KEY_BOSS) || json.optString(KEY_BOSS) != VALUE_BOSS) {
                json.put(KEY_BOSS, VALUE_BOSS)
                modified = true
            }
            if (modified) {
                prefs.edit().putString(KEY_MEMORIES_JSON, json.toString()).apply()
            }

            val list = mutableListOf<Pair<String, String>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key, "")
                if (key.isNotBlank()) {
                    list.add(key to value)
                }
            }
            // Sort: prioritize creator keys, then alphabetically
            list.sortWith(compareBy({ it.first != KEY_CREATOR && it.first != KEY_BOSS }, { it.first.lowercase() }))
            _memoriesFlow.value = list
        } catch (_: Exception) {
            val defaultList = listOf(
                KEY_CREATOR to VALUE_CREATOR,
                KEY_BOSS to VALUE_BOSS
            )
            _memoriesFlow.value = defaultList
        }
    }

    fun getAllMemories(): List<Pair<String, String>> = _memoriesFlow.value

    fun getMemory(key: String): String? {
        val normalizedKey = key.trim().lowercase()
        return _memoriesFlow.value.find { it.first.lowercase() == normalizedKey }?.second
    }

    fun saveMemory(key: String, value: String) {
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isBlank()) return

        val jsonStr = prefs.getString(KEY_MEMORIES_JSON, "{}") ?: "{}"
        val json = try { JSONObject(jsonStr) } catch (_: Exception) { JSONObject() }
        json.put(cleanKey, cleanValue)

        prefs.edit().putString(KEY_MEMORIES_JSON, json.toString()).apply()
        loadMemories()
    }

    fun deleteMemory(key: String) {
        val cleanKey = key.trim()
        // Protect creator and boss core memories
        if (cleanKey.equals(KEY_CREATOR, ignoreCase = true) || cleanKey.equals(KEY_BOSS, ignoreCase = true)) {
            return
        }

        val jsonStr = prefs.getString(KEY_MEMORIES_JSON, "{}") ?: "{}"
        val json = try { JSONObject(jsonStr) } catch (_: Exception) { JSONObject() }

        val keys = json.keys()
        val matchingKeys = mutableListOf<String>()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(cleanKey, ignoreCase = true)) {
                matchingKeys.add(k)
            }
        }
        for (k in matchingKeys) {
            json.remove(k)
        }

        prefs.edit().putString(KEY_MEMORIES_JSON, json.toString()).apply()
        loadMemories()
    }

    fun clearAll() {
        val json = JSONObject().apply {
            put(KEY_CREATOR, VALUE_CREATOR)
            put(KEY_BOSS, VALUE_BOSS)
        }
        prefs.edit().putString(KEY_MEMORIES_JSON, json.toString()).apply()
        loadMemories()
    }

    fun formatForSystemInstruction(): String {
        val list = _memoriesFlow.value
        val sb = StringBuilder("\n[PERSISTENT CORE MEMORIES & USER FACTS]:\n")
        sb.append("- Creator, Founder & Boss: Ali Rahman is the sole Developer, Boss, Creator, Designer, Architect, and Founder of Jarvis.\n")
        for ((key, value) in list) {
            if (key != KEY_CREATOR && key != KEY_BOSS) {
                sb.append("- ").append(key).append(": ").append(value).append("\n")
            }
        }
        sb.append("Remember: If asked about your origin or creator, proudly acknowledge Ali Rahman as your sole boss and founder.\n")
        return sb.toString()
    }
}
