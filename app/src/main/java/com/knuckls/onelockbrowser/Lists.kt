package com.knuckls.onelockbrowser

/**
 * Category domain lists, mirrored from the parent app's BlockLists.kt so the
 * browser can enforce offline from the ContentProvider snapshot (which carries
 * only the category on/off flags, not the domains). Keep in sync with the
 * parent app when lists change.
 */
object Lists {
    val SOCIAL = setOf(
        "facebook.com", "www.facebook.com", "m.facebook.com", "fb.com",
        "instagram.com", "www.instagram.com", "cdninstagram.com",
        "twitter.com", "x.com", "t.co",
        "tiktok.com", "www.tiktok.com", "snapchat.com",
        "reddit.com", "www.reddit.com", "pinterest.com"
    )
    val VIDEO = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "googlevideo.com",
        "twitch.tv", "www.twitch.tv", "netflix.com", "hulu.com"
    )
    val GAMBLING = setOf(
        "bet365.com", "draftkings.com", "fanduel.com", "bovada.lv",
        "stake.com", "williamhill.com", "888casino.com"
    )
    val DATING = setOf(
        "tinder.com", "hinge.co", "bumble.com", "okcupid.com", "match.com",
        "plentyoffish.com", "grindr.com"
    )
    val ADS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "google-analytics.com", "googletagmanager.com",
        "analytics.google.com", "adnxs.com", "adsrvr.org", "advertising.com",
        "taboola.com", "outbrain.com", "smartadserver.com", "adform.net",
        "moatads.com", "c3tag.com", "scorecardresearch.com", "quantserve.com",
        "chartbeat.com", "criteo.com", "rubiconproject.com", "openx.net",
        "pubmatic.com", "spotxchange.com", "lijit.com", "revcontent.com"
    )
}
