package com.example.wordsapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Room database
        val db = Room.databaseBuilder(
            applicationContext, AppDatabase::class.java, "word-database"
        ).build()
// Initialize TextToSpeech engine
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) tts.language = Locale.US
        }
// Set up Compose UI
        setContent {
            WordsAppTheme {
                WordApp(
                    wordDao = db.wordDao(), speak = { text ->
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    })
            }
        }
    }

    override fun onDestroy() {
        // Clean up TextToSpeech resources
        tts.stop();
        tts.shutdown();
        super.onDestroy()
    }
}

/**
 * Main composable that manages the app's state and UI
 */
@Composable
fun WordApp(
    wordDao: WordDao, speak: (String) -> Unit
) {
    /* ---------- State Management ---------- */

    // Initialize ViewModel with WordDao
    val viewModel: WordViewModel = viewModel(factory = WordViewModelFactory(wordDao))

    var showCelebration by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    // Coroutine scope for animations and async operations
    val scope = rememberCoroutineScope()
    // Animation state for card swipe
    val swipeOffset = remember { Animatable(0f) }
    // Current word index in learning list
    var currentIndex by remember { mutableStateOf(0) }
    // Whether to show translation on card
    var showTranslation by remember { mutableStateOf(false) }
    // Description text for current word
    var descriptionText by remember { mutableStateOf("Loading description…") }
    // Filter known words from state
    val learningWords = state.words.filter { !it.isKnown }
    // Get current word safely (handles index out of bounds)
    val currentWord = learningWords.getOrNull(currentIndex.coerceAtMost(learningWords.lastIndex))
    // Dialog control states
    var showAllWordsDialog by remember { mutableStateOf(false) }
    // New word input fields
    var newWordEn by remember { mutableStateOf("") }   // English text field
    var newWordRu by remember { mutableStateOf("") }   // Russian text field
    // Keyboard controller for hiding keyboard
    val keyboard = LocalSoftwareKeyboardController.current
// to new visualisation card appear/dissaper
    val cardAlpha = remember { Animatable(1f) }
    val cardScale = remember { Animatable(1f) }
    var isAnimatingTransition by remember { mutableStateOf(false) }

    // color states near your other animation states
    val successColor = Color(0xFF4CAF50)  // Green for "Know It"
    val skipColor = Color(0xFFF44336)     // Red for "Don't Know"
    val neutralColor = Color(0xFFF8F9FA)  // Default card color

// Track current swipe direction color
    var cardColor by remember { mutableStateOf(neutralColor) }


    // Track when to show celebration
    LaunchedEffect(viewModel) {
        viewModel.successTracker.celebrationTrigger.collect { shouldCelebrate ->
            showCelebration = shouldCelebrate
        }
    }

    /* ---------- Side Effects ---------- */

    /**
     * Auto-translation effect: When English text changes, fetch Russian translation
     */
    LaunchedEffect(newWordEn) {
        if (newWordEn.isNotBlank()) {
            // suspend fun quickTranslate() runs on IO thread; fills ru field
            newWordRu = viewModel.quickTranslate(newWordEn.trim())
        } else {
            newWordRu = ""              // clear when English cleared
        }
    }

    /**
     * Description fetch effect: When current word changes, fetch its description
     */
    LaunchedEffect(currentWord?.id) {
        showTranslation = false
        descriptionText = currentWord?.let { viewModel.fetchDescription(it.english) } ?: ""
    }
    // Track if user is currently dragging the card
    var isDragging by remember { mutableStateOf(false) }   // true only while finger down & moving

    // Track if first word has been spoken (to avoid speaking on initial load)
    var hasSpokenFirstWord by remember { mutableStateOf(false) }

    /**
     * Text-to-speech effect: Speak current word when it changes
     */
    LaunchedEffect(currentWord?.id) {
        if (currentWord == null) return@LaunchedEffect

        if (!hasSpokenFirstWord) {
            // Mark that first word has been loaded but DON'T speak it yet
            hasSpokenFirstWord = true
        } else {
            // Speak on subsequent word changes only
            delay(300)
            speak(currentWord.english)
        }
    }

    /* ---------- Animation Calculations ---------- */

    // Convert card width to pixels for swipe threshold calculations
    val widthPx = LocalDensity.current.run { 240.dp.toPx() }
    // Swipe threshold (25% of card width)
    val swipeThreshold = widthPx * 0.25f
    // Base card color
    val baseColor = Color(0xFFF8F9FA)

// Calculate tint alpha (0 to max 0.3f)

    val absOffset = kotlin.math.abs(swipeOffset.value)
    val isDecision = absOffset > swipeThreshold        // True when in the "zone"

// Tint alpha ramps from 0 → 0.3  until threshold, then snaps to 0.8 (solid)
    val tintAlpha = if (isDecision) 0.8f               // solid colour in zone
    else (absOffset / swipeThreshold) * 0.3f

    val tintColor = when {
        swipeOffset.value < 0f -> Color.Red.copy(alpha = tintAlpha)
        swipeOffset.value > 0f -> Color.Green.copy(alpha = tintAlpha)
        else -> Color.Transparent
    }

// Final blended background colour
    val backgroundColor = lerp(baseColor, tintColor, tintAlpha)
// Desired size while dragging (1f = normal, 1.10f = +10 %)
    val targetScale = if (isDragging) 1.10f else 1f          // make it bigger here

// Animate between 1f and 1.10f with a spring for a smooth “bounce-in/out”
    val animatedScale by animateFloatAsState(
        targetValue = targetScale, animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,           // smoothness
            dampingRatio = Spring.DampingRatioNoBouncy       // no overshoot
        ), label = "card-scale"
    )

    /* ---------- Main UI Layout ---------- */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Add-word field
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            value = newWordEn,
            onValueChange = { newWordEn = it },
            label = { Text("Enter English word") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
        )

        /*  Show second field **only** after user starts typing English  */
        if (newWordEn.isNotBlank()) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = newWordRu,
                onValueChange = { newWordRu = it },        // user can edit freely
                label = { Text("Translation (edit if needed)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Action buttons row
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp)  // space between children
        ) {
            // Add new word button
            Button(
                onClick = {
                    if (newWordEn.isNotBlank()) {
                        // save with user-edited (or auto) translation
                        viewModel.addWord(newWordEn.trim(), newWordRu.trim())
                        // clear fields for next entry
                        newWordEn = ""
                        newWordRu = ""
                        // hide soft keyboard
                        keyboard?.hide()
                    }
                },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
            ) {
                Text("Add new word")
            }
            // Show all words button
            Button(
                onClick = { showAllWordsDialog = true },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Show all words")
            }
        }

        Spacer(Modifier.weight(1.2f))

        // Word description text
        Spacer(Modifier.height(32.dp))
        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1.2f))


        CelebrationPopup(
            show = showCelebration,
            onDismiss = {
                viewModel.successTracker.resetCelebration()
                showCelebration = false
            }


        )

        /* ---- Learning Card Section ---- */
        if (currentWord != null) {
            val widthPx = LocalDensity.current.run { 240.dp.toPx() }
            val swipeThreshold = widthPx * .25f

            // Word card with tap + swipe
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .graphicsLayer {

                        // Fade effect based on swipe distance
                        alpha = 1f - (kotlin.math.min(
                            kotlin.math.abs(swipeOffset.value) / (swipeThreshold * 2f),
                            0.6f
                        ))

                        // Scale effect
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }


                    // Tap gesture to show translation
                    .pointerInput(currentWord.id) {
                        detectTapGestures(

                            onTap = {
                                showTranslation = true
                                speak(currentWord.english) // speak only on tap (finger down + up without move)
                            })
                    }
                    .pointerInput(currentWord.id) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val drag = event.changes.firstOrNull()

                                if (drag != null && drag.pressed) {
                                    showTranslation = true
                                    isDragging = true

                                    var totalDrag = 0f
                                    while (drag.pressed) {
                                        val e2 = awaitPointerEvent()
                                        val c = e2.changes.firstOrNull() ?: break
                                        val dx = c.positionChange().x
                                        totalDrag += dx


                                        //  color based on direction
                                        cardColor = when {
                                            totalDrag > swipeThreshold -> successColor
                                            totalDrag < -swipeThreshold -> skipColor
                                            else -> neutralColor
                                        }
                                        scope.launch {
                                            swipeOffset.snapTo(swipeOffset.value + dx)
                                            // Make card shrink/fade as it moves
                                            cardAlpha.snapTo(1f - (abs(swipeOffset.value) / widthPx))
                                            cardScale.snapTo(1f - (abs(swipeOffset.value) / (widthPx * 2)))
                                        }
                                        c.consume()
                                        if (!c.pressed) break
                                    }

                                    // Check swipe threshold
                                    if (abs(totalDrag) > swipeThreshold) {
                                        isAnimatingTransition = true
                                        scope.launch {
                                            // 1. Faster exit animation (changed from spring to tween)
                                            cardColor = neutralColor
                                            swipeOffset.animateTo(
                                                targetValue = if (totalDrag > 0) widthPx * 1.5f else -widthPx * 1.5f,
                                                animationSpec = tween(
                                                    durationMillis = 150,  // Faster exit (was 300+)
                                                    easing = LinearEasing
                                                )
                                            )
                                            cardAlpha.animateTo(0f, tween(100))
                                            cardScale.animateTo(0.7f, tween(100))

                                            // Update word index (no delay here)
                                            if (totalDrag > 0) viewModel.markWordAsKnown(currentWord.id)
                                            else {
                                                currentIndex =
                                                    (currentIndex + 1) % learningWords.size
                                                viewModel.resthere()
                                            }

                                            // 2. Faster entry animation
                                            swipeOffset.snapTo(if (totalDrag > 0) -widthPx / 2 else widthPx / 2) // Start closer to center
                                            cardAlpha.snapTo(0.4f) // Start less transparent
                                            cardScale.snapTo(0.85f) // Start larger

                                            // Animate new card in quickly
                                            swipeOffset.animateTo(
                                                0f,
                                                tween(
                                                    durationMillis = 200,  // Faster entry
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                            cardAlpha.animateTo(1f, tween(150))
                                            cardScale.animateTo(1f, tween(150))

                                            isAnimatingTransition = false
                                        }
                                    } else {
                                        scope.launch {
                                            // Return to center if not enough swipe
                                            swipeOffset.animateTo(0f)
                                            cardAlpha.animateTo(1f)
                                            cardScale.animateTo(1f)
                                        }
                                    }
                                    isDragging = false
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        alpha = cardAlpha.value
                        scaleX = cardScale.value
                        scaleY = cardScale.value

                    },
                colors = CardDefaults.cardColors(
                    containerColor = cardColor
                )

            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Display word (and translation if shown)
                    Text(
                        text = if (showTranslation) currentWord.english + "  -  " + currentWord.russian else currentWord.english,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Control buttons row
            Spacer(Modifier.height(15.dp))

            // Speak button
            IconButton(onClick = { speak(currentWord.english) }) {
                Icon(
                    imageVector = Icons.Rounded.VolumeUp,
                    contentDescription = "Speak word",
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFF3F51B5) // nice modern blue
                )
            }



            Spacer(Modifier.height(10.dp))
            Text("Word ${currentIndex + 1} of ${learningWords.size}", color = Color.Gray)
        } else {
            Text("Add your first word using the field above!", color = Color.White, fontSize = 17.sp)
            Spacer(Modifier.height(74.dp))
        }
    }
    /* ---- All Words Dialog ---- */
    if (showAllWordsDialog) {
        AllWordsDialog(
            words = state.words,
            learningWords = learningWords, // Pass your filtered list
            onDismiss = { showAllWordsDialog = false },
            onDelete = { id ->
                viewModel.deleteWord(id)
                // showAllWordsDialog = false
            },
            onMarkUnknown = { id ->
                viewModel.markWordAsUnknown(id)
                // showAllWordsDialog = false
            })
    }
}

/* ---- All Words FUN ---- */
@Composable
fun AllWordsDialog(
    words: List<WordEntity>,
    learningWords: List<WordEntity>, // to see that card in working set
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onMarkUnknown: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, modifier = Modifier
            .fillMaxWidth(0.95f) // Takes 95% of screen width
            .padding(0.dp,0.dp),
        shape = RoundedCornerShape(10.dp),
        title = { Text("All Words") }, text = {
            Box(Modifier.padding(0.dp).heightIn(max = 600.dp)  ) {
                LazyColumn (    ){
                    items(words) { word ->
                        val isInLearningSet = learningWords.any { it.id == word.id }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(0.dp,4.dp),

                            elevation = CardDefaults.cardElevation(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {

                                Text(
                                    text = "${word.english}   -   " + "${word.russian}",
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                if (isInLearningSet) {
                                    Text(
                                        text = "In working set",
                                        color = Color(0xFF388E3C), // Green color
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }



                                Spacer(Modifier.height(6.dp))
                                //Text(text = "${word.russian}")
                                Row {
                                    Button(
                                        onClick = { onMarkUnknown(word.id) },

                                        modifier = Modifier
                                            .height(35.dp)
                                            .padding(end = 8.dp),
                                        shape = RoundedCornerShape(5.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 8.dp,
                                            vertical = 4.dp
                                        ),


                                    ) {
                                        Text(
                                            "Add to Study",
                                            //style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onDelete(word.id) },
                                        modifier = Modifier
                                            .height(35.dp)
                                            .padding(end = 8.dp),
                                        shape = RoundedCornerShape(5.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 4.dp, vertical = 0.dp
                                        ),
                                    ) {
                                        Text("Delete")
                                    }
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
                modifier = Modifier.padding(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        }

    )
}
