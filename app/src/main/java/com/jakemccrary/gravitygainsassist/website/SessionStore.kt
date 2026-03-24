package com.jakemccrary.gravitygainsassist.website

data class StoredSessionRecord(
    val token: String? = null,
    val cookieHeader: String? = null,
    val isInvalid: Boolean = false,
)

interface SessionStore {
    fun read(): StoredSessionRecord

    fun write(record: StoredSessionRecord)

    fun clear()
}
