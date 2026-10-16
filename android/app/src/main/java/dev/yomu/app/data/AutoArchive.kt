package dev.yomu.app.data

/** Use Long arithmetic so large documents cannot overflow the percentage check. */
internal fun reachedArchiveThreshold(offset: Int, length: Int): Boolean =
    length > 0 && offset.coerceIn(0, length).toLong() * 100 >= length.toLong() * 98
