package com.example.wordsapp

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.wordsapp.database.AppDatabase
import com.example.wordsapp.database.WordDao
import com.example.wordsapp.ui.theme.WordsAppTheme
import com.example.wordsapp.viewmodel.WordViewModel
import com.example.wordsapp.viewmodel.WordViewModelFactory
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "word-database"
        ).build()

        tts = TextToSpeech(this, this)

        setContent {
            WordsAppTheme {
                WordApp(
                    wordDao = db.wordDao(),
                    speak = { text -> speakWord(text) }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        Log.d("WordsAppTTS", "onInit status = $status")

        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            Log.d("WordsAppTTS", "setLanguage result = $result")

            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            Log.e("WordsAppTTS", "TTS init FAILED, status = $status")
            ttsReady = false
        }
    }

    private fun speakWord(text: String) {
        Log.d("WordsAppTTS", "speakWord('$text'), ttsReady=$ttsReady")

        val engine = tts ?: return
        if (!ttsReady) return
        if (text.isBlank()) return

        try {
            val result = engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "word-${text.hashCode()}"
            )
            Log.d("WordsAppTTS", "tts.speak() result = $result")
        } catch (e: Exception) {
            Log.e("WordsAppTTS", "tts.speak() exception: ${e.message}")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

// ---------- main screen ----------
@Composable
fun WordApp(wordDao: WordDao, speak: (String) -> Unit) {
    // get our ViewModel that talks to the database (via wordDao)
    val viewModel: WordViewModel = viewModel(factory = WordViewModelFactory(wordDao))

    // controls if we show a celebration screen
    var showCelebration by remember { mutableStateOf(false) }

    // take the current UI state from ViewModel (state is a flow!)
    // collectAsState() turns it into a compose state that updates the UI automatically
    // jsut to more easier access to the data.
    val state by viewModel.state.collectAsState()

    // A scope to run coroutines from inside composables
    // like not only work in composable, but always
    val scope = rememberCoroutineScope()

    // animation values for the card:

    val swipeOffset = remember { Animatable(0.1f) }
    val cardAlpha = remember { Animatable(0.8f) }
    val cardScale = remember { Animatable(0.95f) }

    // index of the current word in the learning list
    var currentIndex by remember { mutableStateOf(0) }

    // show or not translation (show only after user click on card)
    var showTranslation by remember { mutableStateOf(false) }

    // text for the description under the word
    var descriptionText by remember { mutableStateOf("Loading…") }

    // take only words that are not known yet.
    // learningWords is the list of words the user is still studying.
    val learningWords = state.words.filter { !it.isKnown }

    // safely get the current word by index
    // coerceAtMost(lastIndex) makes sure we never get index that not exist in list
    val currentWord = learningWords.getOrNull(currentIndex.coerceAtMost(learningWords.lastIndex))

    // index for bottom navigation  0 or 1, to know what to show if rotated etc.
    var selectedIndex by rememberSaveable { mutableStateOf(0) }

    // text fields for adding a new word
    var newWordEn by remember { mutableStateOf("") }
    var newWordRu by remember { mutableStateOf("") }

    // asccess to the software keyboard so we can hide it after adding a word
    val keyboard = LocalSoftwareKeyboardController.current

    // true when user is swiping the card right now
    var isDragging by remember { mutableStateOf(false) }

    // to make sure text to speach reads the word only the first time it appears,
    // not every recomposition.
    var hasSpokenFirstWord by remember { mutableStateOf(false) }

    // colors for swipe feedback:
    // successColor = I know this word
    // skipColor = I don’t know / skip
    // neutralColor = default card color
    val successColor = Color(0xFF4CAF50)
    val skipColor = Color(0xFFF44336)
    val neutralColor = Color.Gray

    // current card background color that changes depending on swipe direction
    var cardColor by remember { mutableStateOf(neutralColor) }

    LaunchedEffect(viewModel) {
        viewModel.successTracker.celebrationTrigger.collect {
            showCelebration = it
        }
    }

    LaunchedEffect(newWordEn) {
        newWordRu = if (newWordEn.isNotBlank()) {
            viewModel.translateWord(newWordEn.trim())
        } else ""
    }

    LaunchedEffect(currentWord?.id) {
        showTranslation = false
        descriptionText = currentWord?.let { viewModel.fetchDescription(it.english) } ?: ""

        // return card to normal state
        swipeOffset.snapTo(0f)
        cardAlpha.snapTo(1f)
        cardScale.snapTo(1f)
        cardColor = neutralColor
    }

    LaunchedEffect(currentWord?.id) {
        if (currentWord == null) return@LaunchedEffect
        if (!hasSpokenFirstWord) {
            hasSpokenFirstWord = true
        } else {
            delay(300)
            speak(currentWord.english)
        }
    }

    val targetScale = if (isDragging) 1.1f else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "card-scale"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BottomAppBar {
                NavigationBar (){
                    NavigationBarItem(
                        selected = selectedIndex == 0,
                        onClick = { selectedIndex = 0 },
                        icon = { },
                        label = { Text("Study",fontSize = 18.sp ) }
                    )
                    NavigationBarItem(
                        selected = selectedIndex == 1,
                        onClick = { selectedIndex = 1 },
                        icon = { },
                        label = { Text("Words",fontSize = 18.sp ) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(26.dp))

            if (selectedIndex == 0) {
                // ---------- study screen ----------
                OutlinedTextField(
                    value = newWordEn,
                    onValueChange = { newWordEn = it },
                    label = { Text("Enter English word") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                )

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
                }

                Spacer(Modifier.weight(1.2f))

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

                Spacer(Modifier.height(8.dp))

                if (currentWord != null) {
                    WordCard(   // из другого файла
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

                    IconButton(onClick = { speak(currentWord.english) }) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = "Speak word",
                            modifier = Modifier.size(38.dp),
                            tint = Color(0xFF3F51B5)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Word ${currentIndex + 1} of ${learningWords.size}", color = Color.Gray)
                } else {
                    Text("Add your first word above!", color = Color.White, fontSize = 17.sp)
                    Spacer(Modifier.height(74.dp))
                }
            } else {
                // ---------- экран Words ----------
                WordsScreen(         // из другого файла
                    words = state.words,
                    learningWords = learningWords,
                    onMarkUnknown = { id -> viewModel.markWordAsUnknown(id) },
                    onDelete = { id -> viewModel.deleteWord(id) }
                )
            }
        }
    }
}
