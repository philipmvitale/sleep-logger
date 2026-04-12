package com.noom.interview.fullstack.sleep.model

import java.time.ZoneId

data class User(
    val id: Long,
    val timeZone: ZoneId
)
