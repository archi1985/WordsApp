package com.example.wordsapp.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val english: String,
    val russian: String, // Store multiple translations here, comma-separated, e.g. "дом, здание, жильё"
    val isKnown: Boolean = false
)