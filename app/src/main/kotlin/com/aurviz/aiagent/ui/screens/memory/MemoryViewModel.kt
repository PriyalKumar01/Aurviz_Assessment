package com.aurviz.aiagent.ui.screens.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurviz.aiagent.AurvizApp
import com.aurviz.aiagent.data.local.memory.MemoryCategory
import com.aurviz.aiagent.data.local.memory.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryRepository = (application as AurvizApp).memoryRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<MemoryCategory?>(null)
    val selectedCategory: StateFlow<MemoryCategory?> = _selectedCategory.asStateFlow()

    val filteredMemories: StateFlow<List<MemoryEntity>> = combine(
        memoryRepository.allMemories,
        _searchQuery,
        _selectedCategory
    ) { memories, query, category ->
        memories.filter { mem ->
            val matchesQuery = query.isBlank() ||
                    mem.key.contains(query, ignoreCase = true) ||
                    mem.content.contains(query, ignoreCase = true)

            val matchesCategory = category == null || mem.category == category

            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: MemoryCategory?) {
        _selectedCategory.value = category
    }

    fun addMemory(key: String, content: String, category: MemoryCategory, importance: Int) {
        viewModelScope.launch {
            memoryRepository.saveMemory(
                key = key,
                content = content,
                category = category,
                importance = importance
            )
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
        }
    }
}
