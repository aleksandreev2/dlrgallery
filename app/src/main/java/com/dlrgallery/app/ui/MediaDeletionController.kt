package com.dlrgallery.app.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.net.Uri
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
import com.dlrgallery.app.data.StagedLocalTrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaTrashController(
    val moveToTrash: (List<MediaImage>) -> Unit,
    val restore: (List<MediaImage>) -> Unit,
    val deletePermanently: (List<MediaImage>) -> Unit,
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
    var legacyQueue by remember { mutableStateOf<List<MediaImage>>(emptyList()) }
    var legacyStage by remember { mutableStateOf<StagedLocalTrashEntry?>(null) }
    var legacyPermissionUri by remember { mutableStateOf<Uri?>(null) }
    var legacyInProgress by remember { mutableStateOf(false) }
    var legacyInitialCount by remember { mutableIntStateOf(0) }

    fun stopLegacyMove(message: String, discardStage: Boolean) {
        val stage = legacyStage
        legacyQueue = emptyList()
        legacyStage = null
        legacyPermissionUri = null
        legacyInProgress = false
        if (discardStage && stage != null) {
            scope.launch { localTrashRepository.discard(stage) }
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        onFinished()
    }

    suspend fun commitDeletedOriginal(stage: StagedLocalTrashEntry): Boolean = try {
        localTrashRepository.commit(stage)
        legacyStage = null
        legacyQueue = legacyQueue.drop(1)
        true
    } catch (error: Throwable) {
        stopLegacyMove(
            error.message ?: "Оригинал удалён, но завершение корзины прервалось. Копия сохранена.",
            discardStage = false,
        )
        false
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

        val uri = legacyPermissionUri
        val stage = legacyStage
        legacyPermissionUri = null
        if (result.resultCode != Activity.RESULT_OK || uri == null || stage == null) {
            stopLegacyMove("Перемещение в корзину отменено", discardStage = true)
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) { resolver.delete(uri, null, null) }
            } catch (error: Throwable) {
                stopLegacyMove(
                    error.message ?: "Не удалось удалить оригинал после подтверждения",
                    discardStage = true,
                )
                return@launch
            }
            commitDeletedOriginal(stage)
        }
    }

    LaunchedEffect(legacyQueue, legacyStage, legacyPermissionUri, legacyInProgress) {
        if (!legacyInProgress || legacyPermissionUri != null) return@LaunchedEffect

        val image = legacyQueue.firstOrNull()
        if (image == null) {
            legacyInProgress = false
            legacyStage = null
            val completed = legacyInitialCount.coerceAtLeast(0)
            legacyInitialCount = 0
            Toast.makeText(
                context,
                "Перемещено в корзину: ${formatLegacyFileCount(completed)}",
                Toast.LENGTH_SHORT,
            ).show()
            onFinished()
            return@LaunchedEffect
        }

        val stage = legacyStage
        if (stage == null) {
            try {
                legacyStage = localTrashRepository.stage(image)
            } catch (error: Throwable) {
                stopLegacyMove(
                    error.message ?: "Не удалось создать безопасную копию файла",
                    discardStage = false,
                )
            }
            return@LaunchedEffect
        }

        try {
            withContext(Dispatchers.IO) { resolver.delete(image.uri, null, null) }
        } catch (security: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                legacyPermissionUri = image.uri
                permissionLauncher.launch(
                    IntentSenderRequest.Builder(
                        security.userAction.actionIntent.intentSender,
                    ).build(),
                )
            } else {
                stopLegacyMove("Android не разрешил удалить оригинал", discardStage = true)
            }
            return@LaunchedEffect
        } catch (error: Throwable) {
            stopLegacyMove(
                error.message ?: "Не удалось удалить оригинал после резервного копирования",
                discardStage = true,
            )
            return@LaunchedEffect
        }

        commitDeletedOriginal(stage)
    }

    fun launchSystemAction(action: SystemMediaAction, images: List<MediaImage>) {
        val uris = images.map(MediaImage::uri).distinct()
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (action == SystemMediaAction.Trash) {
                if (legacyInProgress) {
                    Toast.makeText(context, "Дождитесь завершения текущей операции", Toast.LENGTH_SHORT).show()
                    return
                }
                legacyInitialCount = images.size
                legacyQueue = images.distinctBy(MediaImage::uri)
                legacyStage = null
                legacyPermissionUri = null
                legacyInProgress = true
            } else {
                Toast.makeText(
                    context,
                    "Для локальной корзины используйте вкладку «Корзина»",
                    Toast.LENGTH_SHORT,
                ).show()
            }
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

    return MediaTrashController(
        moveToTrash = { images -> launchSystemAction(SystemMediaAction.Trash, images) },
        restore = { images -> launchSystemAction(SystemMediaAction.Restore, images) },
        deletePermanently = { images ->
            launchSystemAction(SystemMediaAction.DeletePermanently, images)
        },
        isBusy = systemAction != null || legacyInProgress,
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

private fun formatLegacyFileCount(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "$count файл"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count файла"
    else -> "$count файлов"
}
