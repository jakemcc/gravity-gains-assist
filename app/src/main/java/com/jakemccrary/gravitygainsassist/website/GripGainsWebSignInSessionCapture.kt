package com.jakemccrary.gravitygainsassist.website

object GripGainsUrls {
    const val baseUrl = "https://gripgains.ca"
    const val signInUrl = "$baseUrl/gravity-gains"
}

interface GripGainsCookieSource {
    fun readCookieHeader(url: String): String?
}

class GripGainsSessionCookieParser(
    private val tokenCookieName: String = GRIP_GAINS_TOKEN_COOKIE_NAME,
) {
    fun parse(cookieHeader: String?): GripGainsSession? {
        val normalizedCookies = cookieHeader
            .orEmpty()
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (normalizedCookies.isEmpty()) {
            return null
        }

        val token = normalizedCookies
            .asSequence()
            .mapNotNull(::parseCookie)
            .firstOrNull { (name, value) -> name == tokenCookieName && value.isNotBlank() }
            ?.second
            ?.removeSurrounding("\"")
            ?.trim()
            .orEmpty()
        if (token.isBlank()) {
            return null
        }

        return GripGainsSession(
            token = token,
            cookieHeader = normalizedCookies.joinToString("; "),
        )
    }

    private fun parseCookie(cookie: String): Pair<String, String>? {
        val separatorIndex = cookie.indexOf('=')
        if (separatorIndex <= 0) {
            return null
        }

        val name = cookie.substring(0, separatorIndex).trim()
        val value = cookie.substring(separatorIndex + 1).trim()
        if (name.isBlank()) {
            return null
        }

        return name to value
    }
}

class GripGainsWebSignInSessionCapture(
    private val cookieSource: GripGainsCookieSource,
    private val sessionCookieParser: GripGainsSessionCookieParser = GripGainsSessionCookieParser(),
    private val cookieUrl: String = GripGainsUrls.baseUrl,
) {
    fun capture(): GripGainsSession? {
        return sessionCookieParser.parse(cookieSource.readCookieHeader(cookieUrl))
    }
}
