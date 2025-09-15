package com.example.wordsapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words")
    fun getAllWords(): Flow<List<WordEntity>>

    @Insert
    suspend fun insert(word: WordEntity)

    @Update
    suspend fun update(word: WordEntity)

    //delete method
    @Query("DELETE FROM words WHERE id = :wordId")
    suspend fun delete(wordId: Int)

    @Query("UPDATE words SET isKnown = :isKnown WHERE id = :id")
    suspend fun updateIsKnown(id: Int, isKnown: Boolean)

}