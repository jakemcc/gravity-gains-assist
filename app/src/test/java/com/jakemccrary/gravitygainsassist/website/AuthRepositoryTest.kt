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
        assertEquals(GripGainsSession("abc123"), repository.getActiveSession())
    }

    @Test
    fun `clear session removes stored token`() = runTest {
        val repository = DefaultAuthRepository(
            FakeSessionStore(StoredSessionRecord(token = "abc123")),
        )

        repository.clearSession()

        assertEquals(GripGainsSessionState.Status.NO_TOKEN, repository.getSessionState().status)
        assertNull(repository.getActiveSession())
    }

    @Test
    fun `mark session invalid keeps token but removes active session`() = runTest {
        val repository = DefaultAuthRepository(
            FakeSessionStore(StoredSessionRecord(token = "abc123")),
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
            FakeSessionStore(StoredSessionRecord(token = "abc123", isInvalid = true)),
        )

        repository.markSessionValid()

        assertEquals(
            GripGainsSessionState.Status.TOKEN_PRESENT,
            repository.getSessionState().status,
        )
        assertEquals(GripGainsSession("abc123"), repository.getActiveSession())
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
}
