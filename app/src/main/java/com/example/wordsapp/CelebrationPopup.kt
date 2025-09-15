package com.example.wordsapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
fun CelebrationPopup(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (show) {
        // This will automatically dismiss after 3 seconds
        LaunchedEffect(show) {
            if (show) {
                delay(2000) // 3 seconds timeout
                onDismiss()
            }
        }

        Dialog(
            onDismissRequest = onDismiss // Manual dismissal by clicking outside
        ) {
            Box(
                modifier = Modifier
                    .clickable { onDismiss() } // Manual dismissal by clicking anywhere
            ) {
                // Your GIF display code
                val context = LocalContext.current
                val imageLoader = ImageLoader.Builder(context)
                    .components { add(GifDecoder.Factory()) }
                    .build()

                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(R.raw.success)
                            .build(),
                        imageLoader = imageLoader
                    ),
                    contentDescription = "Celebration",
                    modifier = Modifier
                    .size(300.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
