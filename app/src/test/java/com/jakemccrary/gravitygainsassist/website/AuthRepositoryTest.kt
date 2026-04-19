package com.jakemccrary.gravitygainsassist.website

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun `save token stores active session`() = runTest {
        val repository = DefaultAuthRepository(FakeSessionStore())

        repository.saveToken("  abc123  ")

        assertEquals(
            GripGainsSessionState.Status.TOKEN_PRESENT,
            repository.getSessionState().status,
        )
        assertEquals(
            GripGainsSession(
                token = "abc123",
                cookieHeader = "grip_gains_token=abc123",
            ),
            repository.getActiveSession(),
        )
    }

    @Test
    fun `save session stores token and cookie header`() = runTest {
        val repository = DefaultAuthRepository(FakeSessionStore())

        repository.saveSession(
            GripGainsSession(
                token = "jwt-token",
                cookieHeader = "csrftoken=abc; grip_gains_token=jwt-token",
            ),
        )

        assertEquals(
            GripGainsSession(
                token = "jwt-token",
                cookieHeader = "csrftoken=abc; grip_gains_token=jwt-token",
            ),
            repository.getActiveSession(),
        )
    }

    @Test
    fun `clear session removes stored token`() = runTest {
        val repository = DefaultAuthRepository(
            FakeSessionStore(
                StoredSessionRecord(
                    token = "abc123",
                    cookieHeader = "grip_gains_token=abc123",
                ),
            ),
        )

        repository.clearSession()

        assertEquals(GripGainsSessionState.Status.NO_TOKEN, repository.getSessionState().status)
        assertNull(repository.getActiveSession())
    }

    @Test
    fun `clear session clears web sign in cookies`() = runTest {
        val cookieCleaner = FakeGripGainsCookieCleaner()
        val repository = DefaultAuthRepository(
            sessionStore = FakeSessionStore(
                StoredSessionRecord(
                    token = "abc123",
                    cookieHeader = "grip_gains_token=abc123",
                ),
            ),
            cookieCleaner = cookieCleaner,
        )

        repository.clearSession()

        assertEquals(1, cookieCleaner.clearCount)
    }

    @Test
    fun `mark session invalid keeps token but removes active session`() = runTest {
        val repository = DefaultAuthRepository(
            FakeSessionStore(
                StoredSessionRecord(
                    token = "abc123",
                    cookieHeader = "grip_gains_token=abc123",
                ),
            ),
        )

        repository.markSessionInvalid()

        assertEquals(
            GripGainsSessionState.Status.INVALID_SESSION,
            repository.getSessionState().status,
        )
        assertNull(repository.getActiveSession())
    }

    @Test
    fun `mark session valid restores usable session`() = runTest {
        val repository = DefaultAuthRepository(
            FakeSessionStore(
                StoredSessionRecord(
                    token = "abc123",
                    cookieHeader = "grip_gains_token=abc123",
                    isInvalid = true,
                ),
            ),
        )

        repository.markSessionValid()

        assertEquals(
            GripGainsSessionState.Status.TOKEN_PRESENT,
            repository.getSessionState().status,
        )
        assertEquals(
            GripGainsSession(
                token = "abc123",
                cookieHeader = "grip_gains_token=abc123",
            ),
            repository.getActiveSession(),
        )
    }

    private class FakeSessionStore(
        initialRecord: StoredSessionRecord = StoredSessionRecord(),
    ) : SessionStore {
        private var record = initialRecord

        override fun read(): StoredSessionRecord = record

        override fun write(record: StoredSessionRecord) {
            this.record = record
        }

        override fun clear() {
            record = StoredSessionRecord()
        }
    }

    private class FakeGripGainsCookieCleaner : GripGainsCookieCleaner {
        var clearCount = 0
            private set

        override suspend fun clearCookies() {
            clearCount += 1
        }
    }
}
