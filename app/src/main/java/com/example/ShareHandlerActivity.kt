package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import com.example.service.VideoDownloadService

class ShareHandlerActivity : Activity() {
    private val TAG = "ShareHandlerActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)
        finish()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    Log.d(TAG, "Received shared text: $sharedText")
                    val extractedUrl = extractUrlFromText(sharedText)
                    if (extractedUrl.isNotEmpty()) {
                        Toast.makeText(this, "Analyzing link in background...", Toast.LENGTH_SHORT).show()
                        startBackgroundDownload(extractedUrl)
                    } else {
                        Toast.makeText(this, "No valid URL found to download.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startBackgroundDownload(url: String) {
        try {
            val serviceIntent = Intent(this, VideoDownloadService::class.java).apply {
                action = "START_SHARE_FLOW"
                putExtra("SHARED_URL", url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VideoDownloadService for share flow", e)
            Toast.makeText(this, "Failed to start background download.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractUrlFromText(text: String): String {
        val parts = text.split(Regex("\\s+"))
        for (part in parts) {
            if (Patterns.WEB_URL.matcher(part).matches()) {
                return if (!part.startsWith("http://") && !part.startsWith("https://")) {
                    "https://$part"
                } else {
                    part
                }
            }
        }
        return ""
    }
}
