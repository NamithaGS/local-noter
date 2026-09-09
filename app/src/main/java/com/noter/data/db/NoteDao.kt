package com.noter.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE createdAt >= :startMillis AND createdAt < :endMillis ORDER BY createdAt ASC")
    suspend fun getNotesBetween(startMillis: Long, endMillis: Long): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE createdAt >= :startMillis AND createdAt < :endMillis " +
            "AND uploadedToDrive = 0 ORDER BY createdAt ASC"
    )
    suspend fun getUnuploadedNotesBetween(startMillis: Long, endMillis: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getNotesByIds(ids: List<String>): List<NoteEntity>

    @Query("UPDATE notes SET uploadedToDrive = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<String>)

    /** Notes already archived (pass 1) but not yet run through the summarize-and-file pass (pass 2). */
    @Query(
        "SELECT * FROM notes WHERE createdAt >= :startMillis AND createdAt < :endMillis " +
            "AND filedToSummary = 0 ORDER BY createdAt ASC"
    )
    suspend fun getUnsummarizedNotesBetween(startMillis: Long, endMillis: Long): List<NoteEntity>

    @Query("UPDATE notes SET filedToSummary = 1 WHERE id IN (:ids)")
    suspend fun markFiledToSummary(ids: List<String>)

    @Query("UPDATE notes SET manualTag = :tag WHERE id = :id")
    suspend fun updateTag(id: String, tag: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
