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
) : GripGainsCookieSource {
    override fun readCookieHeader(url: String): String? = cookieManager.getCookie(url)
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
