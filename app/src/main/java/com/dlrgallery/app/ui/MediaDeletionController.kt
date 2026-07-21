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

/**
 * Returns a callback that moves selected images to the Android system trash when available.
 * Android 10 confirms protected files one by one through RecoverableSecurityException.
 */
@Composable
fun rememberMediaDeleteRequester(
    onFinished: () -> Unit,
): (List<MediaImage>) -> Unit {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()

    var bulkRequestActive by remember { mutableStateOf(false) }
    var legacyQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var legacyPermissionUri by remember { mutableStateOf<Uri?>(null) }
    var legacyInProgress by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (bulkRequestActive) {
            bulkRequestActive = false
            if (result.resultCode == Activity.RESULT_OK) {
                onFinished()
            } else {
                Toast.makeText(context, "Перемещение в корзину отменено", Toast.LENGTH_SHORT).show()
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
                    error.message ?: "Не удалось удалить фотографию",
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
                error.message ?: "Не удалось удалить фотографию",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    return request@{ images: List<MediaImage> ->
        val uris = images.map(MediaImage::uri).distinct()
        if (uris.isEmpty()) return@request

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val request = MediaStore.createTrashRequest(resolver, uris, true)
                bulkRequestActive = true
                permissionLauncher.launch(
                    IntentSenderRequest.Builder(request.intentSender).build(),
                )
            } catch (error: Throwable) {
                bulkRequestActive = false
                Toast.makeText(
                    context,
                    error.message ?: "Не удалось открыть системную корзину",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            legacyQueue = uris
            legacyInProgress = true
        }
    }
}
