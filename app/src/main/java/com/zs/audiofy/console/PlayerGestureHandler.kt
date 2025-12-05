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
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zs.audiofy.common.SystemFacade
import com.zs.audiofy.common.compose.LocalSystemFacade
import com.zs.compose.theme.text.LocalTextStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode as PointerNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode as CLCMN
import com.zs.audiofy.console.RouteConsole as C

private const val TAG = "PlayerGestureHandler"

/**
 *  The current screen brightness of this activity's window.
 *
 *  This value ranges from 0.0f to 1.0f, where 0.0f is the darkest and 1.0f is the brightest.
 *  A value of -1.0f indicates that the system's default brightness is being used.
 *
 *  Note: Setting this value directly modifies the window's layout attributes.
 *  Changes may not be immediately reflected if the system is managing the brightness
 *  automatically (e.g., under automatic brightness control). In such cases, the actual
 *  brightness may be clamped or overridden by the system.
 *
 *  @see android.view.WindowManager.LayoutParams.screenBrightness
 */
private var SystemFacade.brightness: Float
    get() = (this as? Activity)?.window?.attributes?.screenBrightness ?: 1f
    set(value) {
        val window = (this as? Activity)?.window ?: return
        val attr = window.attributes
        // -1f means use system brightness.
        if (value == -1f)
            attr.screenBrightness = value
        else
        //  Confine value between 0 and 1.
            attr.screenBrightness = value.coerceIn(0f, 1f)
        window.attributes = attr
    }

/**
 * Represents the volume of the music stream, providing a normalized view (0.0 to 1.0)
 * over the system's integer volume range.
 *
 * The volume is represented as a float between 0.0 and 1.0, where 0.0 is muted and 1.0
 * is the maximum volume.  This property maps between the normalized float
 * representation and the underlying integer volume levels of the Android system's
 * `AudioManager.STREAM_MUSIC` stream.
 *
 * **Important Considerations:**
 *
 * -   **Integer Mapping:** The Android system manages volume using integer levels.
 * This property converts the 0.0-1.0 float range to the nearest integer
 * volume level when setting the volume. This means that very small
 * adjustments in the float value might not result in a change in the actual
 * system volume.
 * -   **Granularity:** The number of discrete volume levels is determined by
 * `getStreamMaxVolume(AudioManager.STREAM_MUSIC)`.  A larger maximum volume
 * means finer-grained control.
 * -   **Clamping:** When setting the volume, the provided float value is clamped
 * to the range 0.0 to 1.0 to ensure it's within valid bounds.
 *
 * @property volume Gets or sets the normalized volume of the music stream (0.0 to 1.0).
 * -   **Get:** Retrieves the current music stream volume and normalizes it to a
 * float between 0.0 and 1.0 by dividing by the maximum stream volume.
 * -   **Set:** Sets the music stream volume.  The provided float value is
 * first clamped to the 0.0-1.0 range, then converted to the corresponding
 * integer volume level.
 */
private var AudioManager.volume: Float
    get() {
        // Get the current volume of the music stream.
        val current = getStreamVolume(AudioManager.STREAM_MUSIC)
        // Get the maximum volume for the music stream.
        val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // Normalize the current volume to a float between 0.0 and 1.0.
        // Handle the case where maxVolume is 0 to avoid division by zero.
        return if (max > 0) current.toFloat() / max.toFloat() else 0f
    }
    set(value) {
        // Get the maximum volume for the music stream.
        val max = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // Convert the normalized volume (0.0-1.0) to an integer volume level.
        // Clamp the input value to the valid range of 0.0 to 1.0.
        val target = (value.coerceIn(0f, 1f) * max).roundToInt()
        // Log the volume change for debugging purposes.
        Log.d(TAG, "Setting stream volume to index: $target (raw value: $value, max: $max)")
        // Set the volume of the music stream.
        // The third parameter (0) is a flag that specifies whether to show a volume UI.
        setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }


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
) : DelegatingNode(), PointerInputModifierNode, DrawModifierNode, CLCMN {
    // some lateint properties
    lateinit var textMeasurer: TextMeasurer
    lateinit var style: TextStyle
    lateinit var manager: AudioManager
    lateinit var facade: SystemFacade

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
    ) =
        detector.onPointerEvent(pointerEvent, pass, bounds)


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

    var seekMediaJob: Job? = null
    fun seekBy(mills: Long) {
        seekMediaJob?.cancel()
        seekMediaJob = coroutineScope.launch {
            emit("${mills / 1000}s", true)
            delay(200)
            viewState.seekBy(mills)
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
        style = currentValueOf(LocalTextStyle).copy(
            shadow = textShadow,
            fontSize = 24.sp,
            color = Color.White
        )
        textMeasurer = TextMeasurer(fontFamilyResolver, density, layoutDirection, 8)
        facade = currentValueOf(LocalSystemFacade)
        manager = (facade as Activity).getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val textShadow = Shadow(offset = Offset(5f, 5f), blurRadius = 8.0f)
    override fun ContentDrawScope.draw() {
        drawContent()
        Log.d(TAG, "draw: invalidating")
        val msg = message ?: return
        // Canvas size comes from DrawScope
        val centerX = (size.width - (msg.size.width)) / 2f
        drawText(msg, topLeft = Offset(centerX, 70.dp.toPx()))
    }

    override fun onDetach() {
        undelegate(detector)
        facade.brightness = -1f // restore to auto.
        super.onDetach()
    }

    // Represents the horizontal drag acoss screen from left to right
    fun onHorizontalDrag(pct: Float) {
        Log.d(TAG, "onHorizontalDrag: $pct")
        val pct = -pct // reverse
        val amount = (60_000 * pct).roundToLong()
        seekBy(amount)
    }

    var brightness = 0f
    fun onLeftVerticalDrag(pct: Float) {
        Log.d(TAG, "onLeftVerticalDrag: $pct")
        // Brightness Control
        // -------------------
        val real = pct * 0.01f // scale down
        val new = (brightness + real)
        // Adjust brightness.  If the user drags downwards and the brightness is
        // already at its minimum (0f), allow it to go to -1f (automatic).
        brightness =
            if (new < 0f && real < 0f) -1f else new.coerceIn(0f, 1f)
        facade.brightness =
            brightness           // Set the system brightness.
        // Update the UI message to display the current brightness level.
        if (brightness == -1f)
            emit("Ⓐ Automatic")
        else
            emit("🔆 ${(brightness * 100).roundToInt()}%")
    }

    var volume = 1f
    fun onRightVerticalDrag(pct: Float) {
        Log.d(TAG, "onRightVerticalDrag: $pct")
        //  Volume Control
        //  ----------------
        // Calculate the new volume.
        if (volume == -1f) volume = manager.volume
        val real = pct * 0.01f // scale down
        volume = (volume + real).coerceIn(
            0f,
            1f
        ) // Keep volume within 0-1 range.
        manager.volume = volume                   // Set the system volume.
        // Update the UI message to display the current volume percentage.
        emit("""🔊 ${(volume * 100).roundToInt()}%""")
    }

    fun onTap(count: Int = Int.MAX_VALUE) {
        Log.d(TAG, "onTap: $count")
        if (count == Int.MAX_VALUE) {
            toggleVisibility()
            return
        }
        val mills = count * 10 * 1_000L
        seekBy(mills)
    }

    // Backing field to store the original playback speed before a long press.
    var speed: Float = 1f
    fun onLongPress(released: Boolean) {
        Log.d(TAG, "onLongPress: $released")
        // When the long press starts
        if (!released) {
            // Hide the player controls if they are visible
            if (viewState.visibility != C.VISIBLE_NONE)
                viewState.emit(C.VISIBLE_NONE)
            // Store the current playback speed
            speed = viewState.playbackSpeed
            // Double the playback speed
            viewState.playbackSpeed = 2 * speed
            // Display ">> 2x" message without auto-hiding
            emit(">> 2x", false)
        } else { // When the long press is released
            // Restore the original playback speed
            viewState.playbackSpeed = speed
            emit(">> 1x", true)
        }
    }

    // TODO: Future enhancements to consider:
    //    1. Implement a distinct gesture for adjusting video scale (e.g., pinch-to-zoom).
    //    2. Improve drag scaling calculation, perhaps by using the dimensions of the drag area.
    //    3. Refine `onTap` behavior: toggle visibility only when playing; otherwise, ensure the controller is visible.
    //    4. Add support for focus and key events for devices like TVs.
    //    5. Handle left/right gestures according to the current layout direction (LTR/RTL).

    val detector = PointerNode {
        this@PlayerGestureHandlerNode.size = size

        // Coroutine to handle tap and long press gestures.
        coroutineScope.launch {
            var delayJob: Job? = null
            var lastTapMills = 0L    // Tracks the timestamp of the last tap to detect multi-taps.
            var tapCount = 0
            awaitEachGesture {
                // If the screen is locked, any tap should just toggle the lock icon visibility.
                if (isLocked) {
                    toggleVisibility()
                    return@awaitEachGesture
                }

                // Wait for the first pointer down event and consume it.
                val down = awaitFirstDown().also { it.consume() }

                // Launch a job to detect a long press. If the user holds down for the timeout
                // duration, trigger the onLongPress start action.
                val longPressJob = coroutineScope.launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    Log.d(TAG, "onLongPressHold: ${down.position}")
                    onLongPress(false)
                }

                // Wait for the pointer to be lifted or for the gesture to be cancelled.
                val up = waitForUpOrCancellation()?.also { it.consume() } // consume release
                longPressJob.cancel()

                // If the gesture was cancelled (e.g., another pointer came down), do nothing.
                if (up == null) // cancelled
                    return@awaitEachGesture
                when {
                    // Long press completed: If the pointer was held down longer than the timeout.
                    up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis -> { onLongPress(true); Log.d(TAG, "onLongClick: ") }

                    // Multi-tap: If this tap occurred within the double-tap timeout of the previous tap.
                    up.uptimeMillis - lastTapMills <= viewConfiguration.doubleTapTimeoutMillis -> {
                        // This is a subsequent tap in a multi-tap sequence.
                        // Cancel any pending single tap action.
                        delayJob?.cancel()
                        ++tapCount
                        // Determine direction of seek based on tap location (left/right side of screen).
                        val times = if (down.position.x > size.width / 2) tapCount else -tapCount
                        onTap(times)
                    }

                    // Single tap: This is the first tap or a tap that occurred after the double-tap timeout.
                    else -> {
                        // Reset tap count for a new sequence.
                        tapCount = 1
                        // Cancel any previously scheduled single tap job.
                        delayJob?.cancel()
                        // Schedule a single tap action to run after the double-tap timeout.
                        // This gives the user a chance to perform another tap for a multi-tap gesture.
                        delayJob = coroutineScope.launch {
                            delay(viewConfiguration.doubleTapTimeoutMillis)
                            Log.d(TAG, "onTap: ")
                            onTap()
                        }
                    }
                }
                // Record the time of this tap for future multi-tap detection.
                lastTapMills = up.uptimeMillis
            }
        }

        // Coroutine to handle drag gestures for seeking, volume, and brightness control.
        coroutineScope.launch {
            var accumulated = 0f
            var mode = 0 // 0: undecided, 1: horizontal, 2: vertical-left, 3: vertical-right

            detectDragGestures(
                onDragStart = {
                    // If the screen is locked, a drag should just toggle the lock icon visibility.
                    if (isLocked) {
                        toggleVisibility()
                        return@detectDragGestures
                    }
                    // Hide the controls when a drag starts.
                    if (viewState.visibility != C.VISIBLE_NONE)
                        viewState.emit(C.VISIBLE_NONE)

                    // Reset drag state.
                    mode = 0
                    // Store initial volume and brightness to calculate changes relative to the start of the drag.
                    volume = manager.volume
                    brightness = facade.brightness
                    accumulated = 0f
                },
                onDrag = {change, (dx, dy) ->
                    if (isLocked)
                        return@detectDragGestures
                    val position = change.position
                    val (width, height) = size
                    // On the first drag event, determine if the gesture is primarily horizontal or vertical,
                    // and if vertical, whether it's on the left or right side of the screen.
                    if (mode == 0){
                        val vertical = abs(dy) > abs(dx)
                        val left = position.x < width / 2
                        mode = when {
                            !vertical -> 1
                            left -> 2
                            else -> 3
                        }
                    }

                    // Accumulate the drag distance (dx for horizontal, dy for vertical).
                    accumulated += if (mode == 1) dx else dy
                    // Dispatch to the appropriate handler based on the determined mode.
                    when(mode){
                        1 -> onHorizontalDrag((accumulated / width).coerceIn(-1f, 1f) * -1f) // Horizontal drag for seeking
                        2 -> onLeftVerticalDrag((accumulated / height).coerceIn(-1f, 1f) * -1f)
                        3 -> onRightVerticalDrag((accumulated / height).coerceIn(-1f, 1f) * -1f)
                    }
                    change.consume()
                }
            )
        }
    }
}
