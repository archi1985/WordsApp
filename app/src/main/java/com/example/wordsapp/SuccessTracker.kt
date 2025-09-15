package com.example.wordsapp

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

class SuccessTracker {
    private var correctCount = mutableStateOf(0) // Make it observable
    private var lastCelebrationTime = 0L
    private val _celebrationTrigger = MutableStateFlow(false)
    val celebrationTrigger = _celebrationTrigger.asStateFlow()

    fun addCorrect() {
        correctCount.value++
        val currentTime = System.currentTimeMillis()
        val fiveMinutes = TimeUnit.MINUTES.toMillis(10)

        if (correctCount.value >= 10 /*&& (currentTime - lastCelebrationTime > fiveMinutes)*/) {
            _celebrationTrigger.value = true
            correctCount.value = 0
            lastCelebrationTime = currentTime
        }
    }

    fun resetCounter() {
        correctCount.value = 0
    }

    fun resetCelebration() {
        _celebrationTrigger.value = false
    }
}