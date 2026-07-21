package com.dlrgallery.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class MediaAccess {
    None,
    Partial,
    Full,
}

fun currentMediaAccess(context: Context): MediaAccess {
    fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.Full
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
            else -> MediaAccess.None
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            if (granted(Manifest.permission.READ_MEDIA_IMAGES)) MediaAccess.Full else MediaAccess.None
        }
        else -> {
            if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.Full else MediaAccess.None
        }
    }
}

fun mediaPermissionsForCurrentVersion(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
    )
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
