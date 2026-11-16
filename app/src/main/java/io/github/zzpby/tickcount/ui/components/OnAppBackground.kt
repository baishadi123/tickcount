package io.github.zzpby.tickcount.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * The room goes quiet when the page does: calls [onBackground] once when the
 * app slips behind anything else — another app, the launcher, the screen
 * going off. Not on every pause: a dialog over this page is still this
 * page, and its sound is this page's own.
 */
@Composable
fun OnAppBackground(onBackground: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onBackground()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
        }
    }
}
