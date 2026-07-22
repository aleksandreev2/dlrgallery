package com.dlrgallery.app.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dlrgallery.app.data.LocalTrashRepository
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.TrashItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaTrashController(
    val moveToTrash: (List<MediaImage>) -> Unit,
    val restore: (List<MediaImage>) -> Unit,
    val deletePermanently: (List<MediaImage>) -> Unit,
    val deleteLocalPermanently: (List<TrashItem>) -> Unit,
    val isBusy: Boolean,
)

private enum class SystemMediaAction {
    Trash,
    Restore,
    DeletePermanently,
}

@Composable
fun rememberMediaTrashController(
    onFinished: () -> Unit,
): MediaTrashController {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val localTrashRepository = remember(context) { LocalTrashRepository(context) }
    val scope = rememberCoroutineScope()

    var systemAction by remember { mutableStateOf<SystemMediaAction?>(null) }
    var localMoveInProgress by remember { mutableStateOf(false) }
    var localDeleteInProgress by remember { mutableStateOf(false) }
    var localDeleteQueue by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var localPermissionItem by remember { mutableStateOf<TrashItem?>(null) }
    var localDeleteSuccessCount by remember { mutableIntStateOf(0) }
    var localDeleteFailedCount by remember { mutableIntStateOf(0) }

    fun finishLocalPermanentDelete(cancelled: Boolean = false) {
        val success = localDeleteSuccessCount
        val failed = localDeleteFailedCount
        localDeleteQueue = emptyList()
        localPermissionItem = null
        localDeleteInProgress = false
        localDeleteSuccessCount = 0
        localDeleteFailedCount = 0
        val message = when {
            cancelled && success == 0 -> "Окончательное удаление отменено"
            cancelled -> "Удалено навсегда: ${formatFileCount(success)}. Остальные файлы сохранены"
            failed == 0 -> "Удалено навсегда: ${formatFileCount(success)}"
            success == 0 -> "Не удалось удалить ${formatFileCount(failed)}"
            else -> "Удалено навсегда: ${formatFileCount(success)}, ошибок: $failed"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    suspend fun removeLocalBackup(item: TrashItem): Boolean {
        val id = item.localEntryId ?: return false
        val result = localTrashRepository.deletePermanently(listOf(id))
        return result.successCount == 1 && result.failedCount == 0
    }

    suspend fun originalIsGone(item: TrashItem): Boolean {
        val uri = item.localOriginalUri ?: return true
        return !localTrashRepository.isOriginalReadable(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = systemAction
        if (action != null) {
            systemAction = null
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(context, action.successMessage(), Toast.LENGTH_SHORT).show()
                onFinished()
            } else {
                Toast.makeText(context, action.cancelMessage(), Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }

        val item = localPermissionItem ?: return@rememberLauncherForActivityResult
        localPermissionItem = null
        scope.launch {
            val uri = item.localOriginalUri
            if (uri == null) {
                if (removeLocalBackup(item)) {
                    localDeleteSuccessCount += 1
                } else {
                    localDeleteFailedCount += 1
                }
                localDeleteQueue = localDeleteQueue.drop(1)
                return@launch
            }

            if (result.resultCode != Activity.RESULT_OK) {
                // Some MIUI builds perform the deletion but still return RESULT_CANCELED.
                if (originalIsGone(item)) {
                    if (removeLocalBackup(item)) {
                        localDeleteSuccessCount += 1
                    } else {
                        localDeleteFailedCount += 1
                    }
                    localDeleteQueue = localDeleteQueue.drop(1)
                } else {
                    finishLocalPermanentDelete(cancelled = true)
                }
                return@launch
            }

            val deleted = runCatching {
                withContext(Dispatchers.IO) { resolver.delete(uri, null, null) }
                originalIsGone(item)
            }.getOrDefault(false)

            if (deleted && removeLocalBackup(item)) {
                localDeleteSuccessCount += 1
            } else {
                localDeleteFailedCount += 1
            }
            localDeleteQueue = localDeleteQueue.drop(1)
        }
    }

    LaunchedEffect(localDeleteQueue, localPermissionItem, localDeleteInProgress) {
        if (!localDeleteInProgress || localPermissionItem != null) return@LaunchedEffect
        val item = localDeleteQueue.firstOrNull()
        if (item == null) {
            finishLocalPermanentDelete()
            return@LaunchedEffect
        }

        val uri = item.localOriginalUri
        if (uri == null || originalIsGone(item)) {
            if (removeLocalBackup(item)) {
                localDeleteSuccessCount += 1
            } else {
                localDeleteFailedCount += 1
            }
            localDeleteQueue = localDeleteQueue.drop(1)
            return@LaunchedEffect
        }

        try {
            withContext(Dispatchers.IO) { resolver.delete(uri, null, null) }
            if (originalIsGone(item) && removeLocalBackup(item)) {
                localDeleteSuccessCount += 1
            } else {
                localDeleteFailedCount += 1
            }
            localDeleteQueue = localDeleteQueue.drop(1)
        } catch (security: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                localPermissionItem = item
                permissionLauncher.launch(
                    IntentSenderRequest.Builder(
                        security.userAction.actionIntent.intentSender,
                    ).build(),
                )
            } else {
                localDeleteFailedCount += 1
                localDeleteQueue = localDeleteQueue.drop(1)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            localDeleteFailedCount += 1
            localDeleteQueue = localDeleteQueue.drop(1)
        }
    }

    fun launchSystemAction(action: SystemMediaAction, images: List<MediaImage>) {
        val uris = images.map(MediaImage::uri).distinct()
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(
                context,
                "Для Android 10 используется локальная корзина DLR Gallery",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        try {
            val pendingIntent = when (action) {
                SystemMediaAction.Trash -> MediaStore.createTrashRequest(resolver, uris, true)
                SystemMediaAction.Restore -> MediaStore.createTrashRequest(resolver, uris, false)
                SystemMediaAction.DeletePermanently -> MediaStore.createDeleteRequest(resolver, uris)
            }
            systemAction = action
            permissionLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            )
        } catch (error: Throwable) {
            systemAction = null
            Toast.makeText(
                context,
                error.message ?: "Не удалось открыть системное подтверждение",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun moveToTrash(images: List<MediaImage>) {
        if (images.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            launchSystemAction(SystemMediaAction.Trash, images)
            return
        }
        if (localMoveInProgress || localDeleteInProgress) {
            Toast.makeText(context, "Дождитесь завершения текущей операции", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            localMoveInProgress = true
            val message = try {
                val result = localTrashRepository.moveToTrash(images)
                when {
                    result.failedCount == 0 -> "Перемещено в корзину: ${formatFileCount(result.successCount)}"
                    result.successCount == 0 -> "Не удалось переместить ${formatFileCount(result.failedCount)}"
                    else -> "Перемещено: ${formatFileCount(result.successCount)}, ошибок: ${result.failedCount}"
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                error.message ?: "Не удалось переместить файлы в корзину"
            }
            localMoveInProgress = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onFinished()
        }
    }

    fun deleteLocalPermanently(items: List<TrashItem>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || items.isEmpty()) return
        if (localMoveInProgress || localDeleteInProgress) {
            Toast.makeText(context, "Дождитесь завершения текущей операции", Toast.LENGTH_SHORT).show()
            return
        }
        localDeleteSuccessCount = 0
        localDeleteFailedCount = 0
        localDeleteQueue = items.distinctBy(TrashItem::key)
        localDeleteInProgress = true
    }

    return MediaTrashController(
        moveToTrash = ::moveToTrash,
        restore = { images -> launchSystemAction(SystemMediaAction.Restore, images) },
        deletePermanently = { images ->
            launchSystemAction(SystemMediaAction.DeletePermanently, images)
        },
        deleteLocalPermanently = ::deleteLocalPermanently,
        isBusy = systemAction != null || localMoveInProgress || localDeleteInProgress,
    )
}

@Composable
fun rememberMediaDeleteRequester(
    onFinished: () -> Unit,
): (List<MediaImage>) -> Unit = rememberMediaTrashController(onFinished).moveToTrash

private fun SystemMediaAction.successMessage(): String = when (this) {
    SystemMediaAction.Trash -> "Перемещено в корзину"
    SystemMediaAction.Restore -> "Файлы восстановлены"
    SystemMediaAction.DeletePermanently -> "Файлы удалены навсегда"
}

private fun SystemMediaAction.cancelMessage(): String = when (this) {
    SystemMediaAction.Trash -> "Перемещение в корзину отменено"
    SystemMediaAction.Restore -> "Восстановление отменено"
    SystemMediaAction.DeletePermanently -> "Удаление отменено"
}

private fun formatFileCount(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "$count файл"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count файла"
    else -> "$count файлов"
}
