package com.worktrax.app.lib

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.worktrax.app.R

fun AdView.loadBannerAd() {
    loadAd(AdRequest.Builder().build())
}

private var cachedInterstitial: InterstitialAd? = null

fun loadInterstitial(activity: Activity, adUnitId: String, onReady: () -> Unit = {}, onFailed: () -> Unit = {}) {
    InterstitialAd.load(activity, adUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
            cachedInterstitial = ad
            onReady()
        }
        override fun onAdFailedToLoad(error: LoadAdError) {
            cachedInterstitial = null
            onFailed()
        }
    })
}

fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
    val ad = cachedInterstitial ?: run { onDismissed(); return }
    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            cachedInterstitial = null
            onDismissed()
        }
        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
            cachedInterstitial = null
            onDismissed()
        }
    }
    ad.show(activity)
}

fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    adView.findViewById<TextView>(R.id.ad_headline)?.let {
        adView.headlineView = it
        it.text = nativeAd.headline
    }
    val body = nativeAd.body
    adView.findViewById<TextView>(R.id.ad_body)?.let {
        adView.bodyView = it
        it.text = body
        it.visibility = if (body != null) View.VISIBLE else View.GONE
    }
    val callToAction = nativeAd.callToAction
    adView.findViewById<Button>(R.id.ad_call_to_action)?.let {
        adView.callToActionView = it
        it.text = callToAction
        it.visibility = if (callToAction != null) View.VISIBLE else View.GONE
    }
    val icon = nativeAd.icon
    adView.findViewById<ImageView>(R.id.ad_icon)?.let {
        adView.iconView = it
        if (icon != null) {
            it.setImageDrawable(icon.drawable)
            it.visibility = View.VISIBLE
        }
    }
    adView.setNativeAd(nativeAd)
}

fun loadNativeAd(activity: Activity, adUnitId: String, onLoaded: (NativeAd) -> Unit, onFailed: () -> Unit = {}) {
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad -> onLoaded(ad) }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) { onFailed() }
        })
        .withNativeAdOptions(NativeAdOptions.Builder().build())
        .build()
        .loadAd(AdRequest.Builder().build())
}
