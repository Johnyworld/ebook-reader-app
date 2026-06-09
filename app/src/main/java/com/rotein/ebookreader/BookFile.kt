package com.rotein.ebookreader

data class BookFile(
    val name: String,
    val path: String,
    val contentUri: String = "",  // SAF content URI 문자열
    val extension: String,
    val size: Long,
    val dateAdded: Long,      // MediaStore DATE_ADDED (초 단위)
    val dateModified: Long,   // MediaStore DATE_MODIFIED (초 단위) — 읽은 순 proxy
    val metadata: BookMetadata? = null
)
