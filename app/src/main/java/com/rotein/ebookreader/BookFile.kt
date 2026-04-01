package com.rotein.ebookreader

data class BookFile(
    val name: String,
    val path: String,
    val extension: String,
    val size: Long,
    val metadata: BookMetadata? = null
)
