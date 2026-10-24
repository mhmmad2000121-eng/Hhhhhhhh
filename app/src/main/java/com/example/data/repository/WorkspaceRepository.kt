package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.VaultItem
import com.example.data.model.WorkspaceNote
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WorkspaceRepository(private val db: FirebaseFirestore) {

    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth
        get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- WORKSPACE NOTES ---

    fun observeNotes(userId: String = auth.currentUser?.uid ?: ""): Flow<List<WorkspaceNote>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated" }
        val path = "users/$targetUid/notes"
        emitAll(
            db.collection("users").document(targetUid).collection("notes")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(WorkspaceNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getNotes(userId: String = requireUserId()): Result<List<WorkspaceNote>> {
        val path = "users/$userId/notes"
        return try {
            val snapshot = db.collection("users").document(userId).collection("notes")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val list = snapshot.documents.mapNotNull { doc ->
                doc.toObject(WorkspaceNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getNoteById(noteId: String, targetUserId: String = requireUserId()): Result<WorkspaceNote?> {
        val path = "users/$targetUserId/notes/$noteId"
        return try {
            val doc = db.collection("users").document(targetUserId)
                .collection("notes").document(noteId)
                .get()
                .await()
            Result.success(doc.toObject(WorkspaceNote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        title: String,
        content: String,
        category: String = "Personal",
        isPinned: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val notesRef = db.collection("users").document(uid).collection("notes")
        val docRef = notesRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120),
            "content" to content.take(10000),
            "category" to if (category in WorkspaceNote.CATEGORIES) category else "Personal",
            "isPinned" to isPinned,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(note: WorkspaceNote): Result<Unit> {
        val uid = requireUserId()
        require(note.id.isNotBlank()) { "Note ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("notes").document(note.id)
        val updates = mapOf(
            "title" to note.title.trim().take(120),
            "content" to note.content.take(10000),
            "category" to if (note.category in WorkspaceNote.CATEGORIES) note.category else "Personal",
            "isPinned" to note.isPinned,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- VAULT ITEMS ---

    fun observeVaultItems(userId: String = auth.currentUser?.uid ?: ""): Flow<List<VaultItem>> = flow {
        val targetUid = userId.ifBlank { auth.currentUser?.uid ?: "unauthenticated" }
        val path = "users/$targetUid/vault_items"
        emitAll(
            db.collection("users").document(targetUid).collection("vault_items")
                .whereEqualTo("userId", targetUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(VaultItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                }
                .catch { error ->
                    if (error is Exception) {
                        handleFirestoreError(error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createVaultItem(
        title: String,
        itemType: String,
        secretNote: String = "",
        localFileName: String = "",
        mimeType: String = "text/plain",
        sizeBytes: Long = 0L
    ): Result<String> {
        val uid = requireUserId()
        val itemsRef = db.collection("users").document(uid).collection("vault_items")
        val docRef = itemsRef.document()
        val safeType = if (itemType in listOf(VaultItem.TYPE_IMAGE, VaultItem.TYPE_SECRET, VaultItem.TYPE_DOCUMENT)) {
            itemType
        } else {
            VaultItem.TYPE_SECRET
        }
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().take(120),
            "itemType" to safeType,
            "secretNote" to secretNote.take(5000),
            "localFileName" to localFileName.take(255),
            "mimeType" to mimeType.ifBlank { "text/plain" }.take(100),
            "sizeBytes" to sizeBytes.coerceIn(0L, 1073741824L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vault_items").document(itemId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
