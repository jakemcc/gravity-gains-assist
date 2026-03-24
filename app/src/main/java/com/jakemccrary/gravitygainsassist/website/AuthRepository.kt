package com.jakemccrary.gravitygainsassist.website

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AuthRepository {
    val sessionState: StateFlow<GripGainsSessionState>

    fun getSessionState(): GripGainsSessionState

    fun getActiveSession(): GripGainsSession?

    suspend fun saveSession(session: GripGainsSession)

    suspend fun saveToken(token: String)

    suspend fun clearSession()

    suspend fun markSessionInvalid()

    suspend fun markSessionValid()
}

class DefaultAuthRepository(
    private val sessionStore: SessionStore,
) : AuthRepository {
    private val mutableSessionState = MutableStateFlow(sessionStore.read().toSessionState())

    override val sessionState: StateFlow<GripGainsSessionState> = mutableSessionState.asStateFlow()

    override fun getSessionState(): GripGainsSessionState = mutableSessionState.value

    override fun getActiveSession(): GripGainsSession? {
        val record = sessionStore.read()
        return if (record.token.isNullOrBlank() || record.isInvalid) {
            null
        } else {
            GripGainsSession(
                token = record.token,
                cookieHeader = record.cookieHeader ?: defaultGripGainsCookieHeader(record.token),
            )
        }
    }

    override suspend fun saveSession(session: GripGainsSession) {
        val normalizedToken = session.token.trim()
        val normalizedCookieHeader = session.cookieHeader.trim()
        if (normalizedToken.isBlank() || normalizedCookieHeader.isBlank()) {
            clearSession()
            return
        }

        sessionStore.write(
            StoredSessionRecord(
                token = normalizedToken,
                cookieHeader = normalizedCookieHeader,
                isInvalid = false,
            ),
        )
        mutableSessionState.value = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT)
    }

    override suspend fun saveToken(token: String) {
        val normalizedToken = token.trim()
        if (normalizedToken.isBlank()) {
            clearSession()
            return
        }

        saveSession(
            GripGainsSession(
                token = normalizedToken,
                cookieHeader = defaultGripGainsCookieHeader(normalizedToken),
            ),
        )
    }

    override suspend fun clearSession() {
        sessionStore.clear()
        mutableSessionState.value = GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN)
    }

    override suspend fun markSessionInvalid() {
        val record = sessionStore.read()
        if (record.token.isNullOrBlank()) {
            mutableSessionState.value = GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN)
            return
        }

        sessionStore.write(record.copy(isInvalid = true))
        mutableSessionState.value =
            GripGainsSessionState(GripGainsSessionState.Status.INVALID_SESSION)
    }

    override suspend fun markSessionValid() {
        val record = sessionStore.read()
        if (record.token.isNullOrBlank()) {
            mutableSessionState.value = GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN)
            return
        }

        sessionStore.write(record.copy(isInvalid = false))
        mutableSessionState.value =
            GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT)
    }
}

private fun StoredSessionRecord.toSessionState(): GripGainsSessionState {
    return when {
        token.isNullOrBlank() -> GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN)
        isInvalid -> GripGainsSessionState(GripGainsSessionState.Status.INVALID_SESSION)
        else -> GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT)
    }
}
