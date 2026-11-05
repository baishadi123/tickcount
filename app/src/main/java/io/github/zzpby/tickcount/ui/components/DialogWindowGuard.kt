package io.github.zzpby.tickcount.ui.components

import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Keeps a full-screen dialog honest about the screen it is on.
 *
 * A dialog opened just after the activity has rotated back — the voyage
 * leaves the activity landscape, a letter is opened moments later — can be
 * measured against the configuration the rotation left behind, and laid out
 * at the dead orientation's size: the letter then arrives as a wide short box
 * in the middle of the screen, and never hears that the world turned back.
 *
 * This guard compares the dialog's laid-out size with the display as it
 * actually is, and when the ghost of the old orientation shows — content
 * wider or taller than the screen itself — hands the window to the system
 * whole, to be clamped and measured again. The comparison is cheap and quiet
 * when all is well: a window already inside the display is never touched.
 */
fun Modifier.dialogWindowGuard(): Modifier = composed {
    val view = LocalView.current
    onGloballyPositioned { coords ->
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@onGloballyPositioned

        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        window.windowManager.defaultDisplay.getRealMetrics(real)

        val offTheScreen = coords.size.width > real.widthPixels || coords.size.height > real.heightPixels
        if (offTheScreen) {
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }
    }
}
