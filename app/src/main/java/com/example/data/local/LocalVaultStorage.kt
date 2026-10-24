package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImportedMediaFile(
    val localFileName: String,
    val displayTitle: String,
    val mimeType: String,
    val sizeBytes: Long
)

class LocalVaultStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val privateMediaDir: File
        get() = File(context.filesDir, "private_media").apply {
            if (!exists()) mkdirs()
        }

    fun resolveVaultFile(localFileName: String): File? {
        if (localFileName.isBlank()) return null
        val file = File(privateMediaDir, localFileName)
        return if (file.exists()) file else null
    }

    suspend fun importMediaUri(uri: Uri): Result<ImportedMediaFile> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val fileName = "vault_${UUID.randomUUID().toString().replace("-", "").take(12)}.vaultbin"
            val destFile = File(privateMediaDir, fileName)

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open selected media stream"))

            val sizeBytes = destFile.length()
            val defaultTitle = "Media ${fileName.substringAfter("vault_").substringBefore(".")}"
            Result.success(
                ImportedMediaFile(
                    localFileName = fileName,
                    displayTitle = defaultTitle,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportToGallery(localFileName: String, title: String, mimeType: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = resolveVaultFile(localFileName)
                    ?: return@withContext Result.failure(IllegalStateException("Local vault file not found on this device"))

                val resolver = context.contentResolver
                val extension = when {
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "vault_export" }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${safeTitle}_${System.currentTimeMillis()}.$extension")
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType.ifBlank { "image/jpeg" })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PrivateWorkspace")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val targetUri = resolver.insert(collectionUri, values)
                    ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore entry"))

                resolver.openOutputStream(targetUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val completeValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(targetUri, completeValues, null, null)
                }

                Result.success(targetUri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun deleteLocalFile(localFileName: String) {
        if (localFileName.isBlank()) return
        val file = File(privateMediaDir, localFileName)
        if (file.exists()) {
            file.delete()
        }
    }

    // --- WORKSPACE SESSION PIN LOCK ---

    fun getPinCode(userId: String): String {
        return prefs.getString("pin_$userId", "") ?: ""
    }

    fun setPinCode(userId: String, pin: String) {
        prefs.edit().putString("pin_$userId", pin.trim()).apply()
    }

    fun isAutoLockOnPauseEnabled(userId: String): Boolean {
        return prefs.getBoolean("autolock_$userId", true)
    }

    fun setAutoLockOnPauseEnabled(userId: String, enabled: Boolean) {
        prefs.edit().putBoolean("autolock_$userId", enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "workspace_security_prefs"
    }
}
