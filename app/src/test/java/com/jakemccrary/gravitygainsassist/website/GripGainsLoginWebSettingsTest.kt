package com.jakemccrary.gravitygainsassist.website

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GripGainsLoginWebSettingsTest {
    @Test
    fun `configuration enables login requirements and disables local resource access`() {
        val settings = FakeGripGainsLoginWebSettings()

        configureGripGainsLoginWebSettings(settings)

        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.domStorageEnabled)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        assertFalse(settings.allowFileAccessFromFileURLs)
        assertFalse(settings.allowUniversalAccessFromFileURLs)
    }

    private class FakeGripGainsLoginWebSettings : GripGainsLoginWebSettings {
        override var javaScriptEnabled: Boolean = false
        override var domStorageEnabled: Boolean = false
        override var allowFileAccess: Boolean = true
        override var allowContentAccess: Boolean = true
        override var allowFileAccessFromFileURLs: Boolean = true
        override var allowUniversalAccessFromFileURLs: Boolean = true
    }
}
