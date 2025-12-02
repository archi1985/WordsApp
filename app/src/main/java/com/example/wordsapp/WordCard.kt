package com.example.wordsapp

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.wordsapp.database.WordEntity
import com.example.wordsapp.viewmodel.WordViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

@Composable
fun WordCard(
    // the word that we show on this card
    currentWord: WordEntity,

    // function to speak a word (Text-to-Speech)
    speak: (String) -> Unit,

    // true = show translation, false = show only English word
    showTranslation: Boolean,
    // function to change showTranslation from outside (parent)
    setShowTranslation: (Boolean) -> Unit,

    // horizontal position of the card (for swipe animation)
    swipeOffset: Animatable<Float, *>,

    // transparency of the card (1f = visible, 0f = invisible)
    cardAlpha: Animatable<Float, *>,

    // size of the card (1f = normal size)
    cardScale: Animatable<Float, *>,

    // extra scale value passed from parent (for simple zoom effect)
    animatedScale: Float,

    // true when user is dragging the card
    isDragging: Boolean,
    // function to update isDragging from parent
    setIsDragging: (Boolean) -> Unit,

    // ViewModel to update word state (known / unknown, etc.)
    viewModel: WordViewModel,

    // index of current word in the learning list
    currentIndex: Int,
    // function to change currentIndex from parent
    setCurrentIndex: (Int) -> Unit,

    // list of all words that user is still learning
    learningWords: List<WordEntity>,

    // coroutine scope for animations and DB calls
    scope: CoroutineScope,

    // current color of the card
    cardColor: Color,
    // function to change cardColor from parent
    setCardColor: (Color) -> Unit,

    // color when swipe right (user knows the word)
    successColor: Color,
    // color when swipe left (user skips the word)
    skipColor: Color,
    // default neutral color
    neutralColor: Color
) {
    // convert 240.dp to pixels, to use in swipe logic
    val widthPx = LocalDensity.current.run { 240.dp.toPx() }

    // how far user must swipe to trigger "known" / "skip"
    val swipeThreshold = widthPx * 0.25f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            // move card left/right based on swipeOffset value
            .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
            .graphicsLayer {
                // change only scale of the card (zoom), not transparency here
                scaleX = animatedScale
                scaleY = animatedScale
            }
            // handle simple tap on the card
            .pointerInput(currentWord.id) {
                detectTapGestures(onTap = {
                    // when user taps card, show translation
                    setShowTranslation(true)
                    // and speak the English word
                    speak(currentWord.english)
                })
            }
            // handle drag (swipe) on the card
            .pointerInput(currentWord.id) {
                awaitPointerEventScope {
                    // endless loop to keep listening for touch events
                    while (true) {
                        // wait for the next touch event
                        val event = awaitPointerEvent()
                        // get first pointer (first finger)
                        val drag = event.changes.firstOrNull()

                        // if user is touching the screen
                        if (drag != null && drag.pressed) {
                            // when drag starts, show translation
                            setShowTranslation(true)
                            // tell parent that dragging started
                            setIsDragging(true)

                            // total horizontal movement of this drag
                            var totalDrag = 0f

                            // inner loop: while finger is still pressed
                            while (drag.pressed) {
                                // wait for next move event
                                val e2 = awaitPointerEvent()
                                val c = e2.changes.firstOrNull() ?: break

                                // dx = how much finger moved on X axis since last event
                                val dx = c.positionChange().x
                                totalDrag += dx

                                // change card color depending on how far we dragged
                                setCardColor(
                                    when {
                                        // drag to the right beyond threshold → success color
                                        totalDrag > swipeThreshold -> successColor
                                        // drag to the left beyond threshold → skip color
                                        totalDrag < -swipeThreshold -> skipColor
                                        // in the middle → neutral color
                                        else -> neutralColor
                                    }
                                )

                                // run animations and state updates in coroutine
                                scope.launch {
                                    // move card by dx without animation delay
                                    swipeOffset.snapTo(swipeOffset.value + dx)

                                    // make card more transparent when we drag more
                                    cardAlpha.snapTo(
                                        1f - (abs(swipeOffset.value) / widthPx)
                                    )

                                    // make card smaller when we drag more
                                    cardScale.snapTo(
                                        1f - (abs(swipeOffset.value) / (widthPx * 2))
                                    )
                                }

                                // mark this event as handled
                                c.consume()

                                // stop loop if finger is not pressed anymore
                                if (!c.pressed) break
                            }

                            // here user released the finger after dragging

                            // if drag distance is big enough and we have words to process
                            if (abs(totalDrag) > swipeThreshold && learningWords.isNotEmpty()) {
                                scope.launch {
                                    // reset color to neutral before exit animation
                                    setCardColor(neutralColor)

                                    // animate card flying out to left or right
                                    swipeOffset.animateTo(
                                        if (totalDrag > 0) widthPx * 1.5f
                                        else -widthPx * 1.5f,
                                        tween(150)
                                    )

                                    // fade card out (alpha to 0)
                                    cardAlpha.animateTo(0f, tween(100))
                                    // scale card down a bit
                                    cardScale.animateTo(0.7f, tween(100))

                                    // if swiped to the right → mark word as known
                                    if (totalDrag > 0) {
                                        viewModel.markWordAsKnown(currentWord.id)
                                    } else {
                                        // if swiped to the left → go to next word and "reset" in ViewModel
                                        if (learningWords.isNotEmpty()) {
                                            setCurrentIndex(
                                                (currentIndex + 1) % learningWords.size
                                            )
                                        }
                                        viewModel.resthere()
                                    }

                                    // move card to the other side (for “re-enter” animation)
                                    swipeOffset.snapTo(
                                        if (totalDrag > 0) -widthPx / 2
                                        else widthPx / 2
                                    )
                                    // half-transparent and slightly smaller before coming back
                                    cardAlpha.snapTo(0.4f)
                                    cardScale.snapTo(0.85f)

                                    // animate card back to center
                                    swipeOffset.animateTo(
                                        0f,
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
                                    // fade card to full opacity again
                                    cardAlpha.animateTo(1f, tween(150))
                                    // return card to full size
                                    cardScale.animateTo(1f, tween(150))
                                }
                            } else {
                                // drag was too small: just return card back to normal state
                                scope.launch {
                                    // move card back to center
                                    swipeOffset.animateTo(0f)
                                    // full opacity
                                    cardAlpha.animateTo(1f)
                                    // normal size
                                    cardScale.animateTo(1f)
                                }
                            }

                            // dragging finished
                            setIsDragging(false)
                        }
                    }
                }
            },
        // use current cardColor as background color of the card
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        // content inside the card (text)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (showTranslation)
                // when showTranslation = true → show English and Russian
                    "${currentWord.english}  -  ${currentWord.russian}"
                else
                // otherwise show only English word
                    currentWord.english,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
