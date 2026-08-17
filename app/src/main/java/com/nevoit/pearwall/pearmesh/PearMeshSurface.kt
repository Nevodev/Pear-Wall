package com.nevoit.pearwall.pearmesh

import androidx.compose.foundation.background
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nevoit.pearwall.pearmesh.gl.EglRenderThread
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun PearMeshSurface(
    state: PearMeshState,
    modifier: Modifier = Modifier,
) {
    val applicationContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestState = rememberUpdatedState(state)
    val renderingEnabled = remember { AtomicBoolean(false) }
    val renderThread = remember { AtomicReference<EglRenderThread?>(null) }

    DisposableEffect(lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        fun updateRenderingState() {
            val enabled = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            renderingEnabled.set(enabled)
            renderThread.get()?.setRenderingEnabled(enabled)
        }

        val observer = LifecycleEventObserver { _, _ -> updateRenderingState() }
        lifecycle.addObserver(observer)
        updateRenderingState()

        onDispose {
            lifecycle.removeObserver(observer)
            renderingEnabled.set(false)
            renderThread.get()?.setRenderingEnabled(false)
        }
    }

    Box(modifier = modifier.background(Color.Gray)) {
        AndroidEmbeddedExternalSurface(
            modifier = Modifier.matchParentSize(),
            isOpaque = true,
        ) {
            onSurface { surface, width, height ->
                val newRenderThread = EglRenderThread(
                    context = applicationContext,
                    surface = surface,
                    initialWidth = width,
                    initialHeight = height,
                    stateProvider = { latestState.value.snapshot() },
                    initiallyRenderingEnabled = renderingEnabled.get(),
                    statsListener = state::setRenderDebugInfo,
                )
                renderThread.getAndSet(newRenderThread)?.stopAndJoin()

                surface.onChanged { newWidth, newHeight ->
                    newRenderThread.resize(newWidth, newHeight)
                }
                surface.onDestroyed {
                    renderThread.compareAndSet(newRenderThread, null)
                    newRenderThread.stopAndJoin()
                }

                newRenderThread.start()
            }
        }
    }
}
