package com.example.wordsapp.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
WordDao = the commands for the words table.
oom fills in the real SQL behind these functions.
 */

@Dao //marks this interface as a Data Access Object for Room
interface WordDao {
    @Query("SELECT * FROM words")
    //Flow - if the database changes, Room will automatically send a new list
    fun getAllWords(): Flow<List<WordEntity>>

    @Insert
    //suspend - runs in coroutine and above I do not need suspend because Flow is already the same
    suspend fun insert(word: WordEntity)

    @Update
    suspend fun update(word: WordEntity)

    //delete method
    @Query("DELETE FROM words WHERE id = :wordId")
    suspend fun delete(wordId: Int)

    @Query("UPDATE words SET isKnown = :isKnown WHERE id = :id")
    suspend fun updateIsKnown(id: Int, isKnown: Boolean)

}