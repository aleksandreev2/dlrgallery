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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dlrgallery.app.data.MediaImage
import com.dlrgallery.app.data.MediaOrganizationRepository
import kotlinx.coroutines.launch


data class MediaOrganizationController(
    val isBusy: Boolean,
    val moveToAlbum: (List<MediaImage>, String) -> Unit,
    val copyToAlbum: (List<MediaImage>, String) -> Unit,
)

private data class PendingMove(
    val items: List<MediaImage>,
    val targetRelativePath: String,
)

@Composable
fun rememberMediaOrganizationController(
    onFinished: () -> Unit,
): MediaOrganizationController {
    val context = LocalContext.current
    val repository = remember(context) {
        MediaOrganizationRepository(context.contentResolver)
    }
    val scope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<PendingMove?>(null) }
    var legacyQueue by remember { mutableStateOf<List<MediaImage>>(emptyList()) }
    var legacyTargetPath by remember { mutableStateOf("") }
    var legacyPermissionItem by remember { mutableStateOf<MediaImage?>(null) }

    fun finishOperation(message: String) {
        isBusy = false
        pendingMove = null
        legacyQueue = emptyList()
        legacyPermissionItem = null
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onFinished()
    }

    fun failOperation(error: Throwable) {
        isBusy = false
        pendingMove = null
        legacyQueue = emptyList()
        legacyPermissionItem = null
        Toast.makeText(
            context,
            error.message ?: "Не удалось выполнить операцию с альбомом",
            Toast.LENGTH_LONG,
        ).show()
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val legacyItem = legacyPermissionItem
        if (legacyItem != null) {
            legacyPermissionItem = null
            if (result.resultCode != Activity.RESULT_OK) {
                isBusy = false
                legacyQueue = emptyList()
                Toast.makeText(context, "Перемещение отменено", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                try {
                    repository.moveSingleToRelativePath(legacyItem, legacyTargetPath)
                    legacyQueue = legacyQueue.drop(1)
                } catch (error: Throwable) {
                    failOperation(error)
                }
            }
            return@rememberLauncherForActivityResult
        }

        val move = pendingMove
        if (move == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            isBusy = false
            pendingMove = null
            Toast.makeText(context, "Перемещение отменено", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            try {
                val moved = repository.moveToRelativePath(move.items, move.targetRelativePath)
                finishOperation(formatOperationResult(moved, "перемещено"))
            } catch (error: Throwable) {
                failOperation(error)
            }
        }
    }

    LaunchedEffect(legacyQueue, legacyPermissionItem, isBusy) {
        if (!isBusy || legacyPermissionItem != null || Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            return@LaunchedEffect
        }
        val item = legacyQueue.firstOrNull()
        if (item == null) {
            finishOperation("Файлы перемещены")
            return@LaunchedEffect
        }

        try {
            repository.moveSingleToRelativePath(item, legacyTargetPath)
            legacyQueue = legacyQueue.drop(1)
        } catch (security: RecoverableSecurityException) {
            legacyPermissionItem = item
            writePermissionLauncher.launch(
                IntentSenderRequest.Builder(
                    security.userAction.actionIntent.intentSender,
                ).build(),
            )
        } catch (error: Throwable) {
            failOperation(error)
        }
    }

    fun move(items: List<MediaImage>, targetRelativePath: String) {
        val uniqueItems = items.distinctBy(MediaImage::uri)
        if (uniqueItems.isEmpty()) return
        if (isBusy) {
            Toast.makeText(context, "Дождитесь завершения текущей операции", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(
                context,
                "Управление альбомами доступно с Android 10",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        isBusy = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                pendingMove = PendingMove(uniqueItems, targetRelativePath)
                val request = MediaStore.createWriteRequest(
                    context.contentResolver,
                    uniqueItems.map(MediaImage::uri),
                )
                writePermissionLauncher.launch(
                    IntentSenderRequest.Builder(request.intentSender).build(),
                )
            } catch (error: Throwable) {
                failOperation(error)
            }
        } else {
            legacyTargetPath = targetRelativePath
            legacyQueue = uniqueItems
        }
    }

    fun copy(items: List<MediaImage>, targetRelativePath: String) {
        val uniqueItems = items.distinctBy(MediaImage::uri)
        if (uniqueItems.isEmpty()) return
        if (isBusy) {
            Toast.makeText(context, "Дождитесь завершения текущей операции", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(
                context,
                "Управление альбомами доступно с Android 10",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        isBusy = true
        scope.launch {
            try {
                val copied = repository.copyToRelativePath(uniqueItems, targetRelativePath)
                finishOperation(formatOperationResult(copied, "скопировано"))
            } catch (error: Throwable) {
                failOperation(error)
            }
        }
    }

    return MediaOrganizationController(
        isBusy = isBusy,
        moveToAlbum = ::move,
        copyToAlbum = ::copy,
    )
}

private fun formatOperationResult(count: Int, action: String): String = when {
    count % 10 == 1 && count % 100 != 11 -> "$action $count файл"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$action $count файла"
    else -> "$action $count файлов"
}
