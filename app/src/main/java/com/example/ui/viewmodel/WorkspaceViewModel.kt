package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.VaultItem
import com.example.data.model.WorkspaceNote
import com.example.data.repository.WorkspaceRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class WorkspaceViewModel(
    private val repository: WorkspaceRepository,
    private val currentUserId: String
) : ViewModel() {

    val notesState: StateFlow<UiState<List<WorkspaceNote>>> = repository.observeNotes(currentUserId)
        .map<List<WorkspaceNote>, UiState<List<WorkspaceNote>>> { list ->
            val sorted = list.sortedWith(
                compareByDescending<WorkspaceNote> { it.isPinned }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
            UiState.Success(sorted)
        }
        .catch { error ->
            Log.w(TAG, "Error observing notes", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load notes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val vaultItemsState: StateFlow<UiState<List<VaultItem>>> = repository.observeVaultItems(currentUserId)
        .map<List<VaultItem>, UiState<List<VaultItem>>> { list ->
            val sorted = list.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            UiState.Success(sorted)
        }
        .catch { error ->
            Log.w(TAG, "Error observing vault items", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load vault items"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatusMessage(message: String) {
        _statusMessage.value = message
    }

    fun saveNote(
        existingNote: WorkspaceNote?,
        title: String,
        content: String,
        category: String,
        isPinned: Boolean
    ) {
        if (title.isBlank()) {
            _statusMessage.value = "Note title cannot be empty"
            return
        }
        viewModelScope.launch {
            val result = if (existingNote == null || existingNote.id.isBlank()) {
                repository.createNote(
                    title = title,
                    content = content,
                    category = category,
                    isPinned = isPinned
                ).map { }
            } else {
                repository.updateNote(
                    existingNote.copy(
                        title = title,
                        content = content,
                        category = category,
                        isPinned = isPinned
                    )
                )
            }
            result.onSuccess {
                _statusMessage.value = if (existingNote == null) "Note saved to cloud workspace" else "Note updated"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to save note"
            }
        }
    }

    fun toggleNotePinned(note: WorkspaceNote) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to update pin state"
                }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
                .onSuccess { _statusMessage.value = "Note deleted" }
                .onFailure { err -> _statusMessage.value = err.localizedMessage ?: "Failed to delete note" }
        }
    }

    fun addVaultSecret(title: String, secretNote: String) {
        if (title.isBlank()) {
            _statusMessage.value = "Title is required"
            return
        }
        viewModelScope.launch {
            repository.createVaultItem(
                title = title,
                itemType = VaultItem.TYPE_SECRET,
                secretNote = secretNote,
                localFileName = "",
                mimeType = "text/plain",
                sizeBytes = secretNote.toByteArray().size.toLong()
            ).onSuccess {
                _statusMessage.value = "Secret added to vault"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to save secret"
            }
        }
    }

    fun recordImportedMedia(
        title: String,
        secretNote: String,
        localFileName: String,
        mimeType: String,
        sizeBytes: Long
    ) {
        viewModelScope.launch {
            repository.createVaultItem(
                title = title.ifBlank { "Private Media" },
                itemType = VaultItem.TYPE_IMAGE,
                secretNote = secretNote,
                localFileName = localFileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes
            ).onSuccess {
                _statusMessage.value = "Media secured in vault & synced"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to sync media record"
            }
        }
    }

    fun deleteVaultItem(item: VaultItem, onLocalCleanup: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteVaultItem(item.id)
                .onSuccess {
                    if (item.localFileName.isNotBlank()) {
                        onLocalCleanup(item.localFileName)
                    }
                    _statusMessage.value = "Vault item removed"
                }
                .onFailure { err ->
                    _statusMessage.value = err.localizedMessage ?: "Failed to delete vault item"
                }
        }
    }

    private companion object {
        const val TAG = "WorkspaceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
