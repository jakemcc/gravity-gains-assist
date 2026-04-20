package com.jakemccrary.gravitygainsassist.website

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GripGainsWebSignInDataCleanerTest {
    @Test
    fun `clear cookies removes all WebView cookies and storage`() = runTest {
        val dataStore = FakeGripGainsWebSignInDataStore()
        val cleaner = GripGainsWebSignInDataCleaner(dataStore)

        cleaner.clearCookies()

        assertEquals(
            listOf("clearCookies", "flushCookies", "clearStorage"),
            dataStore.events,
        )
    }

    private class FakeGripGainsWebSignInDataStore : GripGainsWebSignInDataStore {
        val events = mutableListOf<String>()

        override suspend fun clearCookies() {
            events += "clearCookies"
        }

        override fun flushCookies() {
            events += "flushCookies"
        }

        override fun clearStorage() {
            events += "clearStorage"
        }
    }
}
