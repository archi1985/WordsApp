
package com.example.wordsapp.viewmodel
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wordsapp.SuccessTracker
import com.example.wordsapp.database.WordDao
import com.example.wordsapp.database.WordEntity
import com.example.wordsapp.service.TranslationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
/**
Author: Arkadii Zahorulko 2025
WordViewModel keeps track of all words (from DB) inside _state.
Gives a read-only version (state) to UI
Updates state automatically whenever DB changes
Also provides helpers like translationService and successTracker
 */

//holder for values
data class WordState(
    //holds all the words from the database
    val words: List<WordEntity> = emptyList(),
    //which word the user is currently on
    val currentIndex: Int = 0,
    //TO DO for future
    //val isLoading: Boolean = false,
    //val error: String? = null
)

class WordViewModel(private val wordDao: WordDao) : ViewModel() {
    //tracks when user has learned enough words
    val successTracker = SuccessTracker()

    //the internal state of  ViewModel
    private val _state = MutableStateFlow(WordState())

    //public state exposed to the UI (outside ViewModel immutable) - asStateFlow() make it only for read
    val state = _state.asStateFlow()

    //service that can auto-translate eng to rus
    private val translationService = TranslationService.create()

    //runs when ViewModel is created
    init {
        //launch a coroutine tied to the ViewModel lifecycle
        //runs background tasks safely
        viewModelScope.launch {
            //returns a Flow of words from the database
            //collect like - whenever the database emits new words, run this code
            wordDao.getAllWords().collect { wordsFromDb ->
                _state.value = _state.value.copy(
                    words = wordsFromDb,
                    currentIndex = 0
                )
            }
        }
    }
//suspend means it runs in a coroutine, lets Kotlin run it without freezing the UI
public suspend fun translateWord(english: String): String {
    return try {
        //ask the service for a translation
        val response = translationService.translate(english)

        //first, try the main translation
        val mainTranslation = response.responseData.translatedText

        if (mainTranslation.isNotBlank() && mainTranslation != "fuck") {
            //good result → return it
            mainTranslation
        } else {
            // If main is bad - try first alternative match
            val alternative = response.matches?.firstOrNull()?.translation
            if (!alternative.isNullOrBlank()) {
                alternative
            } else {
                // If still nothing - return just blank
                " "
            }
        }
    } catch (e: Exception) {
        //if API call fails completely - log and return blank
        Log.e("Translation", "API Error: ${e.message}")
        " "
    }
}

//this function can be used in two different ways (with or without manual translation)
    fun addWord(
        english: String,
    // empty - auto-translate
        russianManual: String = ""
        //start a coroutine tied to the ViewModel
    ) = viewModelScope.launch {
        val russian = if (russianManual.isBlank())
        //auto-translate if manual is blank
            translateWord(english)
        else
            russianManual

        val newWord = WordEntity(english = english, russian = russian)
        wordDao.insert(newWord)
    }

//this block ensures the database update happens safely without freezing the app
    fun markWordAsKnown(id: Int) {
        //start a coroutine tied to the ViewModel
        viewModelScope.launch {
            val word = state.value.words.find { it.id == id }
            word?.let {
                wordDao.update(it.copy(isKnown = true))
                successTracker.addCorrect()
            }
        }
    }

    fun deleteWord(wordId: Int) {
        viewModelScope.launch {
            wordDao.delete(wordId)
        }
    }

    fun resthere() = viewModelScope.launch {
        successTracker.resetCounter()
    }

    fun markWordAsUnknown(id: Int) = viewModelScope.launch {
        wordDao.updateIsKnown(id, false)
    }

//wrapper
    // suspend fun quickTranslate(wordEn: String): String = translateWord(wordEn)

    //to show description (looks up a word in an online dictionary API)
    suspend fun fetchDescription(word: String): String {
        //forces the code to run on an IO-optimized thread (for network and file operations)
        //and it returns the result of the last expression inside the block.
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.dictionaryapi.dev/api/v2/entries/en/$word"
                //download JSON from URL
                val result = URL(url).readText()
                //parse JSON response with JSONArray
                val jsonArray = JSONArray(result)
                //get definition from JSON structure
                val firstEntry = jsonArray.getJSONObject(0)
                val meanings = firstEntry.getJSONArray("meanings")
                val firstMeaning = meanings.getJSONObject(0)
                val definitions = firstMeaning.getJSONArray("definitions")
                val firstDefinition = definitions.getJSONObject(0)
                val definitionText = firstDefinition.getString("definition")
                // return the definition string
                definitionText
            } catch (e: Exception) {
                "No description found."
            }
        }
    }
}

// A factory class that knows how to create WordViewModel objects.
// Android needs this because WordViewModel requires "wordDao" in its constructor.
class WordViewModelFactory(
    private val wordDao: WordDao // we store the DAO (database access object)
) : ViewModelProvider.Factory {  // we "implement" the Factory interface

    // This method is called whenever someone asks Android for a ViewModel.
    // Android does not know how to create your custom WordViewModel,
    // so it calls this function and lets you decide how to build it.
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // We return a new WordViewModel, giving it the "wordDao" it needs.
        return WordViewModel(wordDao) as T
    }
}