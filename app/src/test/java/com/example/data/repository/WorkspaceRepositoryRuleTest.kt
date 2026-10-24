package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.VaultItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndQueryNote_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WorkspaceRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Confidential Q4 Plan",
                content = "Encrypted notes for executive review",
                category = "Work",
                isPinned = true
            )
        }
        assertTrue("Expected note creation to succeed", createResult.isSuccess)
        val noteId = createResult.getOrThrow()
        assertTrue(noteId.isNotEmpty())

        val notesResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getNotes(uid)
        }
        assertTrue(notesResult.isSuccess)
        assertTrue(notesResult.getOrThrow().any { it.id == noteId && it.title == "Confidential Q4 Plan" })
    }

    @Test
    fun observeNotes_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WorkspaceRepository(firestore)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createNote(
                title = "Realtime Sync Note",
                content = "Live update test",
                category = "Personal"
            ).getOrThrow()
        }

        val emittedNotes = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeNotes(uid).first { list -> list.any { it.id == noteId } }
        }
        assertTrue(emittedNotes.any { it.id == noteId })
    }

    @Test
    fun createVaultItem_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WorkspaceRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createVaultItem(
                title = "Recovery Backup Phrase",
                itemType = VaultItem.TYPE_SECRET,
                secretNote = "alpha bravo charlie delta",
                localFileName = "",
                mimeType = "text/plain",
                sizeBytes = 25L
            )
        }
        assertTrue(createResult.isSuccess)
        val itemId = createResult.getOrThrow()

        val emittedItems = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeVaultItems(uid).first { list -> list.any { it.id == itemId } }
        }
        assertTrue(emittedItems.any { it.id == itemId && it.title == "Recovery Backup Phrase" })
    }

    @Test
    fun getNoteById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WorkspaceRepository(firestore)
        val noteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createNote(
                title = "Alice Private Note",
                content = "Top secret",
                category = "Security"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = WorkspaceRepository(firestore)
        val crossUserResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getNoteById(noteId, targetUserId = aliceUid)
        }
        assertTrue("Expected cross-user read to fail", crossUserResult.isFailure)
        val firestoreEx = crossUserResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeNotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = WorkspaceRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeNotes("unauthenticated_user").first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Expected exception for unauthenticated listener", caughtException)
        val firestoreException = caughtException?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caughtException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@workspace.test"
        const val BOB_EMAIL = "bob@workspace.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
