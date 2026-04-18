package com.jakemccrary.gravitygainsassist.website

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GripGainsWebNavigationPolicyTest {
    @Test
    fun `allows secure grip gains domain and subdomains`() {
        val policy = GripGainsWebNavigationPolicy()

        assertTrue(policy.isAllowed("https://gripgains.ca/gravity-gains"))
        assertTrue(policy.isAllowed("https://www.gripgains.ca/gravity-gains"))
        assertTrue(policy.isAllowed("https://auth.gripgains.ca/callback"))
    }

    @Test
    fun `blocks non grip gains destinations`() {
        val policy = GripGainsWebNavigationPolicy()

        assertFalse(policy.isAllowed("https://evilgripgains.ca/gravity-gains"))
        assertFalse(policy.isAllowed("https://gripgains.ca.evil.example/gravity-gains"))
        assertFalse(policy.isAllowed("https://example.com/"))
    }

    @Test
    fun `blocks insecure and malformed urls`() {
        val policy = GripGainsWebNavigationPolicy()

        assertFalse(policy.isAllowed("http://gripgains.ca/gravity-gains"))
        assertFalse(policy.isAllowed("javascript:alert(1)"))
        assertFalse(policy.isAllowed(null))
    }
}
