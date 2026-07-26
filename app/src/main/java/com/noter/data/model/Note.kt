package com.noter.data.model

data class Note(
    val id: String,
    val title: String,
    val transcriptPath: String,
    val audioPath: String,
    val summary: String?,
    val createdAt: Long,
    val duration: Int
)
