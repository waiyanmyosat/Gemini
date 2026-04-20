package com.gemini.ai

import android.app.Application

class GeminiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GeminiWebViewManager.warmUp(this)
    }
}
