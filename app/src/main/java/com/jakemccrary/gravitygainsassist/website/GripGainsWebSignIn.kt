package com.jakemccrary.gravitygainsassist.website

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

interface GripGainsLoginWebViewFactory {
    fun create(context: Context, onPageFinished: () -> Unit): WebView
}

class AndroidGripGainsCookieSource(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : GripGainsCookieSource, GripGainsCookieCleaner {
    override fun readCookieHeader(url: String): String? = cookieManager.getCookie(url)

    override fun clearCookies() {
        readCookieHeader(GripGainsUrls.baseUrl)
            .orEmpty()
            .split(";")
            .mapNotNull { cookie -> cookie.cookieName() }
            .forEach { cookieName ->
                cookieManager.setCookie(
                    GripGainsUrls.baseUrl,
                    "$cookieName=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
                )
            }
        cookieManager.flush()
    }
}

interface GripGainsCookieCleaner {
    fun clearCookies()
}

object NoOpGripGainsCookieCleaner : GripGainsCookieCleaner {
    override fun clearCookies() = Unit
}

private fun String.cookieName(): String? {
    val separatorIndex = indexOf('=')
    if (separatorIndex <= 0) {
        return null
    }

    return substring(0, separatorIndex).trim().ifBlank { null }
}

class AndroidGripGainsLoginWebViewFactory(
    private val initialUrl: String = GripGainsUrls.signInUrl,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : GripGainsLoginWebViewFactory {
    override fun create(context: Context, onPageFinished: () -> Unit): WebView {
        cookieManager.setAcceptCookie(true)

        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished()
                }
            }
            loadUrl(initialUrl)
        }
    }
}
