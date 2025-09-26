package com.example.wordsapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.wordsapp.database.AppDatabase
import com.example.wordsapp.database.WordDao
import com.example.wordsapp.database.WordEntity
import com.example.wordsapp.ui.theme.WordsAppTheme
import com.example.wordsapp.viewmodel.WordViewModel
import com.example.wordsapp.viewmodel.WordViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

//---------- Main entry point of the app ----------
class MainActivity : ComponentActivity() {
    private lateinit var tts: TextToSpeech // tool that lets phone speak words

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create database (Room saves all words here)
        val db = Room.databaseBuilder(
            applicationContext, AppDatabase::class.java, "word-database"
        ).build()

        //Create Text-to-Speech engine
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US // set English voice
            }
        }

        //Show the Compose UI
        setContent {
            WordsAppTheme {
                // Pass database and speak function into the main screen
                WordApp(
                    wordDao = db.wordDao(),
                    speak = { text -> tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
                )
            }
        }
    }
//Cleanup
    override fun onDestroy() {
        // Stop and close Text-to-Speech when app closes
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}

//---------- Main screen of the app ----------
@Composable
fun WordApp(wordDao: WordDao, speak: (String) -> Unit) {
    // Connect ViewModel to database
    //Give me a WordViewModel.
    //If it already exists, reuse it.
    // If not, create it with this factory that knows how to pass the database.
    // Keep it alive even if the screen rotates.
    val viewModel: WordViewModel = viewModel(factory = WordViewModelFactory(wordDao))

    //All state variables that UI can react to
    //For state of Celebration
    var showCelebration by remember { mutableStateOf(false) }

    //This line subscribes my Composable to the ViewModels state
    //Whenever the ViewModel pushes new data, the UI automatically updates
    val state by viewModel.state.collectAsState()

    val scope = rememberCoroutineScope()

    // Animations for card
    val swipeOffset = remember { Animatable(0f) } // how far card is moved left/right
    val cardAlpha = remember { Animatable(1f) }   // card transparency
    val cardScale = remember { Animatable(1f) }   // card size

    var currentIndex by remember { mutableStateOf(0) }    // number of current word
    var showTranslation by remember { mutableStateOf(false) } // if Russian shown
    var descriptionText by remember { mutableStateOf("Loading…") } // explanation text
    val learningWords = state.words.filter { !it.isKnown } // only unknown words
    val currentWord = learningWords.getOrNull(currentIndex.coerceAtMost(learningWords.lastIndex))

    var showAllWordsDialog by remember { mutableStateOf(false) } // control dialog
    var newWordEn by remember { mutableStateOf("") } // input field English
    var newWordRu by remember { mutableStateOf("") } // input field Russian
    val keyboard = LocalSoftwareKeyboardController.current

    var isDragging by remember { mutableStateOf(false) } // true when swiping card
    var hasSpokenFirstWord by remember { mutableStateOf(false) } // avoid auto-speak first word

    // Card colors
    val successColor = Color(0xFF4CAF50) // green
    val skipColor = Color(0xFFF44336)    // red
    val neutralColor = Color(0xFFF8F9FA) // grey
    var cardColor by remember { mutableStateOf(neutralColor) }

    // Celebration (like confetti) comes from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.successTracker.celebrationTrigger.collect {
            showCelebration = it
        }
    }

    // Auto-translate: when user types English, get Russian translation
    LaunchedEffect(newWordEn) {
        newWordRu = if (newWordEn.isNotBlank()) {
            viewModel.translateWord(newWordEn.trim())
        } else ""
    }

    // Fetch description when word changes
    LaunchedEffect(currentWord?.id) {
        showTranslation = false
        descriptionText = currentWord?.let { viewModel.fetchDescription(it.english) } ?: ""
    }

    // Speak new word when it changes (not first one)
    LaunchedEffect(currentWord?.id) {
        if (currentWord == null) return@LaunchedEffect
        if (!hasSpokenFirstWord) {
            hasSpokenFirstWord = true
        } else {
            delay(300)
            speak(currentWord.english)
        }
    }

    // Animate card bigger when dragging
    val targetScale = if (isDragging) 1.1f else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "card-scale"
    )

    // ---------- Layout ----------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(26.dp))

        // Input field: English word
        OutlinedTextField(
            value = newWordEn,
            onValueChange = { newWordEn = it },
            label = { Text("Enter English word") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        )

        // Show Russian input only if English is not empty
        if (newWordEn.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newWordRu,
                onValueChange = { newWordRu = it },
                label = { Text("Translation") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Buttons row (Add / Show all words)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Button(
                onClick = {
                    if (newWordEn.isNotBlank()) {
                        viewModel.addWord(newWordEn.trim(), newWordRu.trim())
                        newWordEn = ""
                        newWordRu = ""
                        keyboard?.hide()
                    }
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
            ) { Text("Add new word") }

            Button(
                onClick = { showAllWordsDialog = true },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) { Text("Show all words") }
        }

        Spacer(Modifier.weight(1.2f))

        // Show description
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1.2f))

        // Celebration popup
        CelebrationPopup(
            show = showCelebration,
            onDismiss = {
                viewModel.successTracker.resetCelebration()
                showCelebration = false
            }
        )

        // If we have a word → show flashcard
        if (currentWord != null) {
            WordCard(
                currentWord = currentWord,
                speak = speak,
                showTranslation = showTranslation,
                setShowTranslation = { showTranslation = it },
                swipeOffset = swipeOffset,
                cardAlpha = cardAlpha,
                cardScale = cardScale,
                animatedScale = animatedScale,
                isDragging = isDragging,
                setIsDragging = { isDragging = it },
                viewModel = viewModel,
                currentIndex = currentIndex,
                setCurrentIndex = { currentIndex = it },
                learningWords = learningWords,
                scope = scope,
                cardColor = cardColor,
                setCardColor = { cardColor = it },
                successColor = successColor,
                skipColor = skipColor,
                neutralColor = neutralColor
            )

            Spacer(Modifier.height(15.dp))

            // Speak button
            IconButton(onClick = { speak(currentWord.english) }) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = "Speak word",
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFF3F51B5)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Word ${currentIndex + 1} of ${learningWords.size}", color = Color.Gray)
        } else {
            // If no words
            Text("Add your first word above!", color = Color.White, fontSize = 17.sp)
            Spacer(Modifier.height(74.dp))
        }
    }

    // Dialog with all words
    if (showAllWordsDialog) {
        AllWordsDialog(
            words = state.words,
            learningWords = learningWords,
            onDismiss = { showAllWordsDialog = false },
            onDelete = { id -> viewModel.deleteWord(id) },
            onMarkUnknown = { id -> viewModel.markWordAsUnknown(id) }
        )
    }
}

// ---------- Flashcard (tap or swipe) ----------
@Composable
fun WordCard(
    currentWord: WordEntity,
    speak: (String) -> Unit,
    showTranslation: Boolean,
    setShowTranslation: (Boolean) -> Unit,
    swipeOffset: Animatable<Float, *>,
    cardAlpha: Animatable<Float, *>,
    cardScale: Animatable<Float, *>,
    animatedScale: Float,
    isDragging: Boolean,
    setIsDragging: (Boolean) -> Unit,
    viewModel: WordViewModel,
    currentIndex: Int,
    setCurrentIndex: (Int) -> Unit,
    learningWords: List<WordEntity>,
    scope: CoroutineScope,
    cardColor: Color,
    setCardColor: (Color) -> Unit,
    successColor: Color,
    skipColor: Color,
    neutralColor: Color
) {
    val widthPx = LocalDensity.current.run { 240.dp.toPx() }
    val swipeThreshold = widthPx * 0.25f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
            .graphicsLayer {
                alpha = 1f - (abs(swipeOffset.value) / (swipeThreshold * 2f)).coerceAtMost(0.6f)
                scaleX = animatedScale
                scaleY = animatedScale
            }
            // Tap shows translation + speaks
            .pointerInput(currentWord.id) {
                detectTapGestures(onTap = {
                    setShowTranslation(true)
                    speak(currentWord.english)
                })
            }
            // Handle swipe gestures
            .pointerInput(currentWord.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val drag = event.changes.firstOrNull()

                        if (drag != null && drag.pressed) {
                            setShowTranslation(true)
                            setIsDragging(true)
                            var totalDrag = 0f
                            while (drag.pressed) {
                                val e2 = awaitPointerEvent()
                                val c = e2.changes.firstOrNull() ?: break
                                val dx = c.positionChange().x
                                totalDrag += dx

                                // Change color while dragging
                                setCardColor(
                                    when {
                                        totalDrag > swipeThreshold -> successColor
                                        totalDrag < -swipeThreshold -> skipColor
                                        else -> neutralColor
                                    }
                                )

                                // Move card
                                scope.launch {
                                    swipeOffset.snapTo(swipeOffset.value + dx)
                                    cardAlpha.snapTo(1f - (abs(swipeOffset.value) / widthPx))
                                    cardScale.snapTo(1f - (abs(swipeOffset.value) / (widthPx * 2)))
                                }
                                c.consume()
                                if (!c.pressed) break
                            }

                            // Decide after swipe
                            if (abs(totalDrag) > swipeThreshold) {
                                scope.launch {
                                    setCardColor(neutralColor)
                                    swipeOffset.animateTo(
                                        if (totalDrag > 0) widthPx * 1.5f else -widthPx * 1.5f,
                                        tween(150)
                                    )
                                    cardAlpha.animateTo(0f, tween(100))
                                    cardScale.animateTo(0.7f, tween(100))

                                    if (totalDrag > 0) {
                                        viewModel.markWordAsKnown(currentWord.id)
                                    } else {
                                        setCurrentIndex((currentIndex + 1) % learningWords.size)
                                        viewModel.resthere()
                                    }

                                    // Bring new card in
                                    swipeOffset.snapTo(if (totalDrag > 0) -widthPx / 2 else widthPx / 2)
                                    cardAlpha.snapTo(0.4f)
                                    cardScale.snapTo(0.85f)
                                    swipeOffset.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                                    cardAlpha.animateTo(1f, tween(150))
                                    cardScale.animateTo(1f, tween(150))
                                }
                            } else {
                                scope.launch {
                                    swipeOffset.animateTo(0f)
                                    cardAlpha.animateTo(1f)
                                    cardScale.animateTo(1f)
                                }
                            }
                            setIsDragging(false)
                        }
                    }
                }
            }
            .graphicsLayer {
                alpha = cardAlpha.value
                scaleX = cardScale.value
                scaleY = cardScale.value
            },
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (showTranslation)
                    "${currentWord.english}  -  ${currentWord.russian}"
                else currentWord.english,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------- Dialog with all words ----------
@Composable
fun AllWordsDialog(
    words: List<WordEntity>,
    learningWords: List<WordEntity>,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onMarkUnknown: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        shape = RoundedCornerShape(10.dp),
        title = { Text("All Words") },
        text = {
            Box(Modifier.heightIn(max = 600.dp)) {
                LazyColumn {
                    items(words) { word ->
                        val isInLearningSet = learningWords.any { it.id == word.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(12.dp)
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text("${word.english} - ${word.russian}", style = MaterialTheme.typography.bodyLarge)
                                if (isInLearningSet) {
                                    Text("In working set", color = Color(0xFF388E3C), style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    Button(
                                        onClick = { onMarkUnknown(word.id) },
                                        modifier = Modifier.height(35.dp),
                                        shape = RoundedCornerShape(5.dp),
                                    ) { Text("Add to Study") }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { onDelete(word.id) },
                                        modifier = Modifier.height(35.dp),
                                        shape = RoundedCornerShape(5.dp),
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp)
            ) { Text("Close") }
        }
    )
}
