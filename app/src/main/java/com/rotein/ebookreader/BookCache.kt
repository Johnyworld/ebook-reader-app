package com.rotein.ebookreader

object BookCache {
    @Volatile var books: List<BookFile>? = null
}
