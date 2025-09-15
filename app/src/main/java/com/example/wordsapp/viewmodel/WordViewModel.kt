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




// Add this data class (best to put it above the ViewModel)
data class WordState(
    val words: List<WordEntity> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class WordViewModel(private val wordDao: WordDao) : ViewModel() {

    val successTracker = SuccessTracker()
    private val _state = MutableStateFlow(WordState())
    val state = _state.asStateFlow()

    private val translationService = TranslationService.create()

    init {
        viewModelScope.launch {
            wordDao.getAllWords().collect { wordsFromDb ->
                _state.value = _state.value.copy(
                    words = wordsFromDb,
                    currentIndex = 0
                )
            }
        }
    }

    private suspend fun translateWord(english: String): String {
        return try {
            val response = translationService.translate(english)
            response.responseData.translatedText
                .takeIf { it.isNotBlank() && it != "fuck" } // Skip bad defaults
                ?: response.matches?.firstOrNull()?.translation // Fallback to best match
                ?: " "
        } catch (e: Exception) {
            Log.e("Translation", "API Error: ${e.message}")
            " "
        }
    }

// ---------------------------------------------------------------------------
//  Overload of addWord that accepts a manual translation.
//  • Keeps your original addWord(String) working because of default param.
// ---------------------------------------------------------------------------
    fun addWord(
        english: String,
        russianManual: String = ""          // empty => auto-translate
    ) = viewModelScope.launch {
        val russian = if (russianManual.isBlank())
            translateWord(english)
        else
            russianManual

        val newWord = WordEntity(english = english, russian = russian)
        wordDao.insert(newWord)
    }

    fun markWordAsKnown(id: Int) {
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
// ---------------------------------------------------------------------------
//  QUICK one-shot translation for UI preview.
//  • Called from the Composable to pre-fill the Russian field.
// ---------------------------------------------------------------------------
    suspend fun quickTranslate(wordEn: String): String = translateWord(wordEn)

    //to show description

    suspend fun fetchDescription(word: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.dictionaryapi.dev/api/v2/entries/en/$word"
                val result = URL(url).readText()

                val jsonArray = JSONArray(result)
                val firstEntry = jsonArray.getJSONObject(0)
                val meanings = firstEntry.getJSONArray("meanings")
                val firstMeaning = meanings.getJSONObject(0)
                val definitions = firstMeaning.getJSONArray("definitions")
                val firstDefinition = definitions.getJSONObject(0)
                val definitionText = firstDefinition.getString("definition")

                definitionText  // return the definition string

            } catch (e: Exception) {
                "No description found."
            }
        }
    }


}

class WordViewModelFactory(private val wordDao: WordDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WordViewModel(wordDao) as T
    }
}