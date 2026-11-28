package com.example.platform

import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.domain.model.AppCategory

object AppCategoryHelper {

    fun classify(appInfo: ApplicationInfo, label: String): AppCategory {
        val pkg = appInfo.packageName.lowercase()
        val name = label.lowercase()

        if (isBrowser(pkg, name)) return AppCategory.BROWSERS
        if (isSocial(pkg, name)) return AppCategory.SOCIAL
        if (isVideo(pkg, name)) return AppCategory.VIDEO
        if (isCommunication(pkg, name)) return AppCategory.COMMUNICATION
        if (isGame(pkg, name, appInfo)) return AppCategory.GAMES
        if (isProductivity(pkg, name)) return AppCategory.PRODUCTIVITY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return Api26CategoryHelper.mapCategory(appInfo)
        }
        return AppCategory.OTHER
    }

    private fun isBrowser(pkg: String, name: String): Boolean =
        pkg in BROWSER_PACKAGES ||
            pkg.contains("browser") ||
            name.contains("browser") ||
            pkg.contains("chrome") ||
            pkg.contains("firefox") ||
            pkg.contains("opera") ||
            pkg.contains("duckduckgo") ||
            pkg.contains("brave")

    private fun isSocial(pkg: String, name: String): Boolean =
        pkg in SOCIAL_PACKAGES ||
            pkg.contains("instagram") ||
            pkg.contains("facebook") ||
            pkg.contains("tiktok") ||
            pkg.contains("musically") ||
            pkg.contains("snapchat") ||
            pkg.contains("reddit") ||
            pkg.contains("twitter") ||
            pkg.contains("pinterest") ||
            pkg.contains("threads") ||
            name.contains("social")

    private fun isVideo(pkg: String, name: String): Boolean =
        pkg in VIDEO_PACKAGES ||
            pkg.contains("youtube") ||
            pkg.contains("netflix") ||
            pkg.contains("twitch") ||
            pkg.contains("vimeo") ||
            pkg.contains("disney") ||
            pkg.contains("hotstar") ||
            pkg.contains("primevideo") ||
            name.contains("video") ||
            name.contains("player")

    private fun isCommunication(pkg: String, name: String): Boolean =
        pkg in COMMUNICATION_PACKAGES ||
            pkg.contains("whatsapp") ||
            pkg.contains("telegram") ||
            pkg.contains("messenger") ||
            pkg.contains("discord") ||
            pkg.contains("slack") ||
            pkg.contains("signal") ||
            pkg.contains("viber") ||
            pkg.contains("teams") ||
            name.contains("messages") ||
            name.contains("mail") ||
            pkg.contains("gmail")

    private fun isGame(pkg: String, name: String, appInfo: ApplicationInfo): Boolean {
        @Suppress("DEPRECATION")
        val legacyGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        if (legacyGame) return true
        return pkg.contains("game") ||
            pkg.contains("supercell") ||
            pkg.contains("tencent.ig") ||
            pkg.contains("pubg") ||
            pkg.contains("minecraft") ||
            pkg.contains("roblox") ||
            pkg.contains("garena") ||
            name.contains("game")
    }

    private fun isProductivity(pkg: String, name: String): Boolean =
        pkg in PRODUCTIVITY_PACKAGES ||
            pkg.contains("notion") ||
            pkg.contains("docs") ||
            pkg.contains("sheets") ||
            pkg.contains("slides") ||
            pkg.contains("drive") ||
            pkg.contains("keep") ||
            pkg.contains("evernote") ||
            pkg.contains("todoist") ||
            pkg.contains("obsidian") ||
            pkg.contains("anki") ||
            pkg.contains("calendar") ||
            pkg.contains("calculator") ||
            pkg.contains("notes") ||
            name.contains("notes") ||
            name.contains("study") ||
            name.contains("dictionary") ||
            name.contains("office")

    @RequiresApi(Build.VERSION_CODES.O)
    private object Api26CategoryHelper {
        fun mapCategory(appInfo: ApplicationInfo): AppCategory = when (appInfo.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            else -> AppCategory.OTHER
        }
    }

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser"
    )

    private val SOCIAL_PACKAGES = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.pinterest",
        "com.instagram.barcelona"
    )

    private val VIDEO_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.netflix.mediaclient",
        "tv.twitch.android.app",
        "com.amazon.avod.thirdpartyclient",
        "in.startv.hotstar"
    )

    private val COMMUNICATION_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.facebook.orca",
        "com.discord",
        "com.google.android.gm",
        "com.Slack",
        "org.thoughtcrime.securesms",
        "com.microsoft.teams"
    )

    private val PRODUCTIVITY_PACKAGES = setOf(
        "notion.id",
        "com.google.android.keep",
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs",
        "com.google.android.apps.docs.editors.sheets",
        "com.google.android.calendar",
        "com.microsoft.office.word",
        "com.todoist",
        "md.obsidian",
        "com.ichi2.anki",
        "com.duolingo"
    )
}
