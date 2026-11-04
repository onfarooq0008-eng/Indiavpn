package com.indiavpn.fast.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.indiavpn.fast.data.AppSettings
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/**
 * The ad entry point: the disconnect interstitial (native ads live in [NativeAdSlot]). There is no banner. Yandex is
 * the only ad SDK in the app.
 *
 *   AdsManager.init(app)                    -- safe to call repeatedly; starts the SDK (after the Terms screen)
 *   AdsManager.showInterstitial(activity)   -- call right after a user-initiated disconnect; shows on every 3rd one (no-op if no ad is ready)
 *   AdsManager.whenReady(runnable)          -- runs once the SDK is up (used by native ad slots)
 *
 * The SDK's own automatic start is switched off in the manifest, so nothing from Yandex
 * runs before the user has accepted the Terms screen. Everything here runs on the main thread and nothing can throw into UI code.
 */
object AdsManager {

    private const val TAG = "FastVPN-Ads"

    private enum class State { NOT_STARTED, INITIALIZING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private var state = State.NOT_STARTED

    // ---- interstitial ----
    private var appContext: Application? = null
    private var interstitialLoader: InterstitialAdLoader? = null // strong reference, as Yandex requires
    private var interstitial: InterstitialAd? = null
    private var shownInterstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var interstitialRetries = 0

    // ---- "SDK is ready" callbacks ----
    private val readyListeners = mutableListOf<Runnable>()

    /** Runs [r] on the main thread once the SDK is initialised (now, if it already is). */
    fun whenReady(r: Runnable) {
        if (state == State.READY) {
            r.run()
        } else if (!readyListeners.contains(r)) {
            readyListeners.add(r)
        }
    }

    fun removeReadyListener(r: Runnable) {
        readyListeners.remove(r)
    }

    /** In debug / test-ads builds, tell the developer WHY an ad is missing (no logcat needed). Never shown to real users. */
    private fun debugToast(context: Context, message: String) {
        if (AdConfig.isTestMode) Toast.makeText(context, "Ads: $message", Toast.LENGTH_LONG).show()
    }

    // ============================ init ============================

    /** Starts the SDK (once). Callers only reach this after the Terms screen has been accepted. Never blocks: the SDK starts asynchronously. */
    fun init(app: Application) {
        if (state != State.NOT_STARTED) return
        state = State.INITIALIZING
        appContext = app
        try {
            // Tell Yandex what the user really agreed to (must be set BEFORE initialize(), on every launch): true if they
            // tapped "I Agree" on the Terms screen (which discloses the ads) or allowed ads in the older dialog; otherwise
            // false, so Yandex does not use their personal data. Ads are requested either way.
            YandexAds.setUserConsent(AppSettings(app).adsAccepted)
            YandexAds.initialize(app) {
                state = State.READY
                Log.i(TAG, "Yandex Ads initialized (test=${AdConfig.isTestMode})")
                loadInterstitial()
                readyListeners.toList().also { readyListeners.clear() }.forEach { it.run() }
            }
        } catch (t: Throwable) {
            state = State.FAILED
            Log.w(TAG, "Yandex Ads init crashed: ${t.javaClass.simpleName}")
            debugToast(app, "init crashed: ${t.javaClass.simpleName}")
        }
    }

    // ============================ interstitial ============================

    private fun loadInterstitial() {
        val app = appContext ?: return
        if (state != State.READY || interstitialLoading || interstitial != null) return
        interstitialLoading = true
        try {
            val loader = interstitialLoader ?: InterstitialAdLoader(app).also { interstitialLoader = it }
            loader.loadAd(
                AdRequest.Builder(AdConfig.interstitialAdUnitId).build(),
                object : InterstitialAdLoadListener {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        interstitialLoading = false
                        interstitialRetries = 0
                        interstitial = interstitialAd
                        Log.i(TAG, "interstitial loaded")
                    }

                    override fun onAdFailedToLoad(adRequestError: AdRequestError) {
                        interstitialLoading = false
                        Log.i(TAG, "interstitial failed: code=${adRequestError.code} msg=${adRequestError.description}")
                        // Never reload straight from this callback (Yandex discourages it): retry later, a few times.
                        if (interstitialRetries < AdConfig.RETRY_MAX_TRIES) {
                            val delay = AdConfig.RETRY_BASE_MS shl interstitialRetries
                            interstitialRetries++
                            main.postDelayed({ loadInterstitial() }, delay)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            interstitialLoading = false
            Log.w(TAG, "interstitial crashed: ${t.javaClass.simpleName}")
        }
    }

    /**
     * Call once per user-initiated disconnect. Shows the preloaded interstitial on every [AdConfig.INTERSTITIAL_EVERY_N_DISCONNECTS]th
     * disconnect (the 3rd, 6th, 9th...). If no ad is ready at that moment it is skipped, not queued. Never blocks, never throws.
     */
    fun showInterstitial(activity: Activity) {
        if (state != State.READY || activity.isFinishing || activity.isDestroyed) return
        val settings = AppSettings(activity)
        val count = settings.interstitialDisconnects + 1
        settings.interstitialDisconnects = count // counted even when no ad is shown
        if (count % AdConfig.INTERSTITIAL_EVERY_N_DISCONNECTS != 0) return
        val ad = interstitial
        if (ad == null) {
            loadInterstitial() // not ready yet: try to have one for next time
            return
        }
        try {
            interstitial = null
            shownInterstitial = ad
            ad.setAdEventListener(object : InterstitialAdEventListener {
                override fun onAdShown() {}

                override fun onAdFailedToShow(adError: AdError) {
                    Log.i(TAG, "interstitial failed to show: ${adError.description}")
                    finishInterstitial(ad)
                }

                override fun onAdDismissed() = finishInterstitial(ad)
                override fun onAdClicked() {}
                override fun onAdImpression(impressionData: ImpressionData?) {}
            })
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "interstitial show crashed: ${t.javaClass.simpleName}")
            finishInterstitial(ad)
        }
    }

    /** After an interstitial is dismissed (or failed to show): release it, as Yandex asks, and preload the next one. */
    private fun finishInterstitial(ad: InterstitialAd) {
        try { ad.setAdEventListener(null) } catch (_: Throwable) {}
        if (shownInterstitial === ad) shownInterstitial = null
        loadInterstitial()
    }
}
