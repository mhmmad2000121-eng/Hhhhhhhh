package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class WorkspaceNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Personal",
    val isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        val CATEGORIES = listOf("Personal", "Work", "Finance", "Ideas", "Security")
    }
}

@IgnoreExtraProperties
data class VaultItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val itemType: String = "SECRET",
    val secretNote: String = "",
    val localFileName: String = "",
    val mimeType: String = "text/plain",
    val sizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val TYPE_IMAGE = "IMAGE"
        const val TYPE_SECRET = "SECRET"
        const val TYPE_DOCUMENT = "DOCUMENT"
    }
}
