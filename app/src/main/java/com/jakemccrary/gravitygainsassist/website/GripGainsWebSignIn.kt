package com.jakemccrary.gravitygainsassist.website

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URI

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
    private val navigationPolicy: GripGainsWebNavigationPolicy = GripGainsWebNavigationPolicy(),
) : GripGainsLoginWebViewFactory {
    override fun create(context: Context, onPageFinished: () -> Unit): WebView {
        cookieManager.setAcceptCookie(true)

        return WebView(context).apply {
            configureGripGainsLoginWebSettings(AndroidGripGainsLoginWebSettings(settings))
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    return !navigationPolicy.isAllowed(request?.url?.toString())
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished()
                }
            }
            if (navigationPolicy.isAllowed(initialUrl)) {
                loadUrl(initialUrl)
            }
        }
    }
}

internal interface GripGainsLoginWebSettings {
    var javaScriptEnabled: Boolean
    var domStorageEnabled: Boolean
    var allowFileAccess: Boolean
    var allowContentAccess: Boolean
    var allowFileAccessFromFileURLs: Boolean
    var allowUniversalAccessFromFileURLs: Boolean
}

internal fun configureGripGainsLoginWebSettings(settings: GripGainsLoginWebSettings) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
}

@Suppress("DEPRECATION")
private class AndroidGripGainsLoginWebSettings(
    private val settings: WebSettings,
) : GripGainsLoginWebSettings {
    override var javaScriptEnabled: Boolean
        get() = settings.javaScriptEnabled
        set(value) {
            settings.javaScriptEnabled = value
        }

    override var domStorageEnabled: Boolean
        get() = settings.domStorageEnabled
        set(value) {
            settings.domStorageEnabled = value
        }

    override var allowFileAccess: Boolean
        get() = settings.allowFileAccess
        set(value) {
            settings.allowFileAccess = value
        }

    override var allowContentAccess: Boolean
        get() = settings.allowContentAccess
        set(value) {
            settings.allowContentAccess = value
        }

    override var allowFileAccessFromFileURLs: Boolean
        get() = settings.allowFileAccessFromFileURLs
        set(value) {
            settings.allowFileAccessFromFileURLs = value
        }

    override var allowUniversalAccessFromFileURLs: Boolean
        get() = settings.allowUniversalAccessFromFileURLs
        set(value) {
            settings.allowUniversalAccessFromFileURLs = value
        }
}

class GripGainsWebNavigationPolicy(
    private val allowedRootHost: String = "gripgains.ca",
) {
    fun isAllowed(url: String?): Boolean {
        val uri = runCatching { URI(url.orEmpty()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false

        return scheme == "https" &&
            (host == allowedRootHost || host.endsWith(".$allowedRootHost"))
    }
}
