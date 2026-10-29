package com.indiavpn.fast.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.indiavpn.fast.R
import com.indiavpn.fast.data.AppSettings
import com.indiavpn.fast.data.ServerCache

/** Blue India VPN splash (shield, name, skyline). Shown ~1.2 s while the server list starts loading. */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var openNext: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val hasAcceptedTerms = AppSettings(this).hasAcceptedTerms
        val next = if (hasAcceptedTerms) {
            // Kick off the server fetch right now, while the logo is on screen, so MainActivity opens
            // with a warm (or already-loading) list. Only once terms are accepted -- no network calls
            // before consent.
            ServerCache.warm(this)
            MainActivity::class.java
        } else {
            ConsentActivity::class.java
        }
        openNext = Runnable {
            startActivity(Intent(this, next))
            finish()
        }.also { handler.postDelayed(it, SPLASH_MS) }
    }

    override fun onDestroy() {
        openNext?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_MS = 1200L
    }
}
