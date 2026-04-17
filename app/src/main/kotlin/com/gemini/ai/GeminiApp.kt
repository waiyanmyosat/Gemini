package com.gemini.ai

import android.app.Application

class GeminiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize WebView engine as early as possible
        GeminiWebViewManager.warmUp(this)
    }
}
