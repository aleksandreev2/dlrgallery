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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dlrgallery.app.data.MediaImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class MediaTrashController(
    val moveToTrash: (List<MediaImage>) -> Unit,
    val restore: (List<MediaImage>) -> Unit,
    val deletePermanently: (List<MediaImage>) -> Unit,
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
    val scope = rememberCoroutineScope()

    var systemAction by remember { mutableStateOf<SystemMediaAction?>(null) }
    var legacyQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var legacyPermissionUri by remember { mutableStateOf<Uri?>(null) }
    var legacyInProgress by remember { mutableStateOf(false) }

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
        legacyPermissionUri = null
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            legacyQueue = emptyList()
            legacyInProgress = false
            Toast.makeText(context, "Удаление отменено", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    resolver.delete(uri, null, null)
                }
            } catch (error: Throwable) {
                Toast.makeText(
                    context,
                    error.message ?: "Не удалось удалить файл",
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                legacyQueue = legacyQueue.drop(1)
            }
        }
    }

    LaunchedEffect(legacyQueue, legacyPermissionUri, legacyInProgress) {
        if (!legacyInProgress || legacyPermissionUri != null) return@LaunchedEffect

        val uri = legacyQueue.firstOrNull()
        if (uri == null) {
            legacyInProgress = false
            Toast.makeText(context, "Файлы удалены", Toast.LENGTH_SHORT).show()
            onFinished()
            return@LaunchedEffect
        }

        try {
            withContext(Dispatchers.IO) {
                resolver.delete(uri, null, null)
            }
            legacyQueue = legacyQueue.drop(1)
        } catch (security: RecoverableSecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                legacyPermissionUri = uri
                permissionLauncher.launch(
                    IntentSenderRequest.Builder(
                        security.userAction.actionIntent.intentSender,
                    ).build(),
                )
            } else {
                legacyQueue = emptyList()
                legacyInProgress = false
                Toast.makeText(context, "Android не разрешил удалить файл", Toast.LENGTH_SHORT).show()
            }
        } catch (error: Throwable) {
            legacyQueue = emptyList()
            legacyInProgress = false
            Toast.makeText(
                context,
                error.message ?: "Не удалось удалить файл",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun launchSystemAction(action: SystemMediaAction, images: List<MediaImage>) {
        val uris = images.map(MediaImage::uri).distinct()
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (action == SystemMediaAction.Trash || action == SystemMediaAction.DeletePermanently) {
                Toast.makeText(
                    context,
                    "На Android 10 системной корзины нет — файлы будут удалены с устройства",
                    Toast.LENGTH_LONG,
                ).show()
                legacyQueue = uris
                legacyInProgress = true
            } else {
                Toast.makeText(context, "Восстановление доступно с Android 11", Toast.LENGTH_SHORT).show()
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
