package com.jakemccrary.gravitygainsassist.website

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GripGainsWebSignInSessionCaptureTest {
    @Test
    fun `parser extracts token and preserves normalized cookie header`() {
        val parser = GripGainsSessionCookieParser()

        val session = parser.parse("csrftoken=abc; grip_gains_token=jwt-token ; theme=light")

        assertEquals(
            GripGainsSession(
                token = "jwt-token",
                cookieHeader = "csrftoken=abc; grip_gains_token=jwt-token; theme=light",
            ),
            session,
        )
    }

    @Test
    fun `parser returns null when token cookie is missing`() {
        val parser = GripGainsSessionCookieParser()

        val session = parser.parse("csrftoken=abc; theme=light")

        assertNull(session)
    }

    @Test
    fun `capture reads cookies from source and parses session`() {
        val capture = GripGainsWebSignInSessionCapture(
            cookieSource = FakeGripGainsCookieSource("grip_gains_token=jwt-token; theme=light"),
        )

        val session = capture.capture()

        assertEquals(
            GripGainsSession(
                token = "jwt-token",
                cookieHeader = "grip_gains_token=jwt-token; theme=light",
            ),
            session,
        )
    }

    private class FakeGripGainsCookieSource(
        private val cookieHeader: String?,
    ) : GripGainsCookieSource {
        override fun readCookieHeader(url: String): String? = cookieHeader
    }
}
