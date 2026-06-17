package com.worktrax.app.lib

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

fun AdView.loadBannerAd() {
    loadAd(AdRequest.Builder().build())
}
