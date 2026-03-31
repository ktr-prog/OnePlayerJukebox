package com.zs.audiofy.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zs.core.playback.SubtitleView

@Composable
fun Cues(
    provider: () -> List<Any>?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = {
            val wrapper = SubtitleView(it)
            val cues = provider()
            wrapper.setCues(cues)
            wrapper.raw
        },
        update = {view ->
           val wrapper =  SubtitleView(view)
            val cues = provider()
            wrapper.setCues(cues)
        }
    )
}