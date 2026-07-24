/*
 *  Copyright (c) 2025 Zakir Sheikh
 *
 *  Created by Zakir Sheikh on 30=09-2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.zs.audiofy.console


import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zs.audiofy.common.AppConfig
import com.zs.compose.theme.ContentAlpha
import com.zs.compose.theme.text.LocalTextStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode as PointerNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode as CLCMN
import com.zs.audiofy.console.RouteConsole as C

private const val TAG = "PlayerGestureHandler"


fun Modifier.handlePlayerGestures(
    viewState: ConsoleViewState,
): Modifier = this then PlayerGestureHandlerElement(viewState)

private class PlayerGestureHandlerElement(
    val viewState: ConsoleViewState
) : ModifierNodeElement<PlayerGestureHandlerNode>() {

    override fun create(): PlayerGestureHandlerNode =
        PlayerGestureHandlerNode(viewState)

    override fun update(node: PlayerGestureHandlerNode) {

    }


    override fun InspectorInfo.inspectableProperties() {
        name = "playerGestureDectector"
        properties["state"] = viewState
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerGestureHandlerElement

        if (viewState != other.viewState) return false

        return true
    }

    override fun hashCode(): Int {
        return viewState.hashCode()
    }
}

private class PlayerGestureHandlerNode(
    val viewState: ConsoleViewState,
) : DelegatingNode(), PointerInputModifierNode, DrawModifierNode, CLCMN, KeyInputModifierNode {
    // TODO: Future enhancements to consider:
    //    1. Implement a distinct gesture for adjusting video scale (e.g., pinch-to-zoom).
    //    2. Improve drag scaling calculation, perhaps by using the dimensions of the drag area.
    //    3. Refine `onTap` behavior: toggle visibility only when playing; otherwise, ensure the controller is visible.
    //    4. Add support for focus and key events for devices like TVs.
    //    5. Handle left/right gestures according to the current layout direction (LTR/RTL).
    val detector = PointerNode {
        this@PlayerGestureHandlerNode.size = size
        //
        coroutineScope.launch {
            detectTapGesture()
        }
    }

    /**
     * Returns true if [offset] lies within the safe zone of this [PointerInputScope].
     *
     * The safe zone excludes a 30.dp margin from each edge.
     */
    fun PointerInputScope.isGestureInSafeZone(offset: Offset): Boolean{
        val safeInset = 30.dp.toPx()
        return  !(offset.x < safeInset ||
                offset.x > size.width - safeInset ||
                offset.y < safeInset ||
                offset.y > size.height - safeInset)
    }

    // some lateint properties
    lateinit var textMeasurer: TextMeasurer
    lateinit var style: TextStyle

    var size = IntSize.Zero
    var message: TextLayoutResult? = null
        set(value) {
            field = value
            invalidateDraw()
        }

    // Returns if controller is in locked state.
    val isLocked
        get() = viewState.visibility >= C.VISIBLE_NONE_LOCKED && viewState.visibility <= C.VISIBLE_LOCKED_LOCK

    override val shouldAutoInvalidate: Boolean get() = false
    override fun onCancelPointerInput() = detector.onCancelPointerInput()
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) = detector.onPointerEvent(pointerEvent, pass, bounds)

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Currently this Modifier is always focusable and enabled.
        // We only handle "activation" keys on KeyDown to simulate a click/press action.
        // Keys supported: Enter, Center (D‑pad), NumPadEnter, and Spacebar.
        // This mirrors the behavior found in Modifier.clickable.
        // In the future, other event types or accessibility actions may be added.
        val isEnterKey = (
                event.key == Key.Enter ||
                        event.key == Key.DirectionCenter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.Spacebar
                )

        if (event.type == KeyDown && isEnterKey) {
            // Toggle visibility when the activation key is pressed.
            toggleVisibility()
            return true // Event consumed
        }

        // Return false if the event was not handled.
        return false
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    // A simple fun that autohides the message after 3 seconds.
    var messageAutohideJob: Job? = null
    fun emit(text: String, autoHide: Boolean = true) {
        messageAutohideJob?.cancel()
        message = textMeasurer.measure(text, style)
        if (autoHide)
            messageAutohideJob = coroutineScope.launch {
                delay(2.5.seconds)
                message = null
            }
    }

    // Toggles controller visibility.
    fun toggleVisibility() {
        // show hide controller
        viewState.emit(
            newVisibility = when (viewState.visibility) {
                C.VISIBLE_NONE_LOCKED -> C.VISIBLE_LOCKED_LOCK
                C.VISIBLE_LOCKED_LOCK -> C.VISIBLE_NONE_LOCKED
                C.VISIBLE -> C.VISIBLE_NONE
                C.VISIBLE_LOCKED_SEEK -> C.VISIBLE_LOCKED_SEEK
                else -> C.VISIBLE
            }
        )
    }

    @SuppressLint("SuspiciousCompositionLocalModifierRead")
    override fun onAttach() {
        super.onAttach()
        delegate(detector)
        val fontFamilyResolver = currentValueOf(LocalFontFamilyResolver)
        val density = currentValueOf(LocalDensity)
        val layoutDirection = currentValueOf(LocalLayoutDirection)
        style = currentValueOf(LocalTextStyle).copy(shadow = textShadow, color = Color.White)
        textMeasurer = TextMeasurer(fontFamilyResolver, density, layoutDirection, 8)
    }

    val textShadow = Shadow(offset = Offset(5f, 5f), blurRadius = 8.0f)
    override fun ContentDrawScope.draw() {
        drawContent()
        val msg = message ?: return

        // Measure text size
        val textWidth = msg.size.width
        val textHeight = msg.size.height

        // Position text horizontally centered
        val centerX = (size.width - textWidth) / 2f
        val topY = 40.dp.toPx()

        // Padding
        val vPadding = 2.dp.toPx()
        val hPadding = 12.dp.toPx()

        // Rect bounds
        val rectLeft = centerX - hPadding
        val rectTop = topY - vPadding
        val rectRight = centerX + textWidth + hPadding
        val rectBottom = topY + textHeight + vPadding

        val rectSize = Size(rectRight - rectLeft, rectBottom - rectTop)
        val rectTopLeft = Offset(rectLeft, rectTop)

        // Corner radius = half of rect height → pill shape
        val cornerRadius = CornerRadius(rectSize.height / 2f, rectSize.height / 2f)

        // Background rounded rect (20% alpha white)
        drawRoundRect(
            color = Color.White.copy(alpha = ContentAlpha.indication),
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = cornerRadius
        )

        // Border (stroke) around rect
        drawRoundRect(
            color = Color.White,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = Dp.Hairline.toPx())
        )

        // Text in pure white
        drawText(
            msg,
            topLeft = Offset(centerX, topY),
            color = Color.White
        )
    }

    override fun onDetach() {
        undelegate(detector)
        super.onDetach()
    }


    // Handles a single tap: toggles player control visibility.
    fun onTap() {
        Log.d(TAG, "onTap")
        toggleVisibility()
    }

    // Backing field to store the original playback speed before a long press.
    var speed: Float = 1f
    fun onLongPress(released: Boolean) {
        // This feature is still in preview mode; hence this
        if (!AppConfig.isLabsModeOn)
            return
        Log.d(TAG, "onLongPress: $released")
        // Hide the player controls if they are visible
        if (viewState.visibility != C.VISIBLE_NONE)
            viewState.emit(C.VISIBLE_NONE)
        // When the long press starts
        if (!released) {
            // Store the current playback speed
            speed = viewState.playbackSpeed
            // Double the playback speed
            viewState.playbackSpeed = 2 * speed
            // Display ">> 2x" message without auto-hiding
            emit("⏭ 2x", false)
        } else { // When the long press is released
            // Restore the original playback speed
            viewState.playbackSpeed = speed
            emit("⏭ 1x", true)
        }
    }

    suspend fun PointerInputScope.detectTapGesture() {
        awaitEachGesture {
            // Wait for the first pointer down event (finger touches screen) and consume it
            // so it isn't propagated further down the gesture chain.
            val down = awaitFirstDown().also { it.consume() }
            // don't proceed if not in safe zone
            if (!isGestureInSafeZone(down.position))
                return@awaitEachGesture
            // If the screen is locked, any tap should only toggle the lock icon visibility.
            if (isLocked) {
                toggleVisibility()
                return@awaitEachGesture
            }
            // Launch a coroutine to detect a long press.
            // If the user holds down longer than the system-defined timeout,
            // trigger the onLongPress action.
            val longPressJob = coroutineScope.launch {
                delay(viewConfiguration.longPressTimeoutMillis) // Wait for long press threshold
                Log.d(TAG, "onLongPressHold: ${down.position}") // Debug log for long press position
                onLongPress(false)                              // Invoke long press callback
            }

            // Wait for the pointer to be lifted (finger up) or gesture cancellation.
            // Consume the "up" event so it doesn't propagate further.
            val up = waitForUpOrCancellation()?.also { it.consume() }
            longPressJob.cancel()  // Cancel the long press job since the finger has been lifted or gesture ended.

            // If gesture was cancelled (e.g., another finger interrupted), exit early.
            if (up == null) // cancelled
                return@awaitEachGesture

            when {
                // Long press completed: If the pointer was held down longer than the timeout.
                up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis -> {
                    onLongPress(true); Log.d(TAG, "onLongClick: ")
                }

                // Single tap: toggle visibility.
                else -> {
                    Log.d(TAG, "onTap: ")
                    onTap()
                }
            }
        }
    }
}
