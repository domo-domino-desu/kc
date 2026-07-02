package ddd.kc.data.repository

import kotlin.time.Clock

fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
