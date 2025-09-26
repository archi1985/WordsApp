package com.example.wordsapp.database
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 AppDatabase is the main entry point to local Room database
 It tells Room which table we have (WordEntity),
 and gives us access to the DAO (WordDao)
 */
// here is :: class reference
@Database(entities = [WordEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
}