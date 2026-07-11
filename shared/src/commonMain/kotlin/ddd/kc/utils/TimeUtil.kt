package ddd.kc.utils

import kotlin.time.Clock

fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
