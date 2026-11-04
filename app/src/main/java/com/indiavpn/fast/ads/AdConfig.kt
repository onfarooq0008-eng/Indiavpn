package com.indiavpn.fast.ads

import com.indiavpn.fast.BuildConfig

/**
 * THE one place for the ad settings.
 *
 * Ads in this app (all served by the Yandex Mobile Ads SDK -- the only ad SDK in the app, no mediation):
 *  - NATIVE        : one card on the Home screen and one inside the Locations list.
 *  - INTERSTITIAL  : full-screen ad shown right after the user taps Disconnect, on every 3rd disconnect.
 *
 * Test ads: debug builds always use Yandex's public demo units. Release builds use the real units below,
 * unless USE_TEST_ADS is set to true for the release build type in app/build.gradle.
 */
object AdConfig {
    // ---- real Yandex ad units ----
    private const val NATIVE_AD_UNIT_ID = "R-M-20337113-1"
    private const val INTERSTITIAL_AD_UNIT_ID = "R-M-20337113-2"

    // ---- Yandex public demo units (always fill, earn nothing) ----
    private const val DEMO_NATIVE = "demo-native-content-yandex"
    private const val DEMO_INTERSTITIAL = "demo-interstitial-yandex"

    /** true => demo ads only. */
    val isTestMode: Boolean get() = BuildConfig.DEBUG || BuildConfig.USE_TEST_ADS

    val nativeAdUnitId: String get() = if (isTestMode) DEMO_NATIVE else NATIVE_AD_UNIT_ID
    val interstitialAdUnitId: String get() = if (isTestMode) DEMO_INTERSTITIAL else INTERSTITIAL_AD_UNIT_ID

    /** A native / interstitial ad that got "no fill" is requested again after this long (doubling), at most this many times. */
    const val RETRY_BASE_MS = 60_000L
    const val RETRY_MAX_TRIES = 5

    /** The interstitial is shown on every Nth user-initiated disconnect (3 = the 3rd, 6th, 9th...). */
    const val INTERSTITIAL_EVERY_N_DISCONNECTS = 3
}
