package io.github.zzpby.tickcount.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * The gallery-read permission, by its era: the fine-grained pair on 13 and
 * up, the storage one below. Nothing here runs at launch — the request goes
 * out the moment a referenced picture wants reading and the permission is
 * not standing, and the answer comes back to [onResult].
 */
fun hasGalleryRead(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(context, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun galleryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/**
 * A request shaped as a function: call it with what should happen once the
 * permission's state is known. The system dialog may or may not appear —
 * a standing permission answers immediately.
 */
@Composable
fun rememberGalleryReadRequest(): ((onResult: (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    // The launcher is built before the caller's answer exists, so the answer
    // waits in a holder until the system comes back.
    var waiting by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        waiting?.invoke(grants.values.any { it })
        waiting = null
    }
    return { onResult ->
        if (hasGalleryRead(context)) {
            onResult(true)
        } else {
            waiting = onResult
            launcher.launch(galleryPermissions())
        }
    }
}
