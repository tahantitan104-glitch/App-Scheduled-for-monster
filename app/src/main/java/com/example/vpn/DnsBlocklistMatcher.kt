package com.example.vpn

import com.example.domain.model.ContentBlockerConfig

object DnsBlocklistMatcher {

    val SHORT_VIDEO_DOMAINS: Set<String> = setOf(
        "tiktok.com",
        "tiktokv.com",
        "tiktokcdn.com",
        "musical.ly",
        "kwai.com",
        "likee.video",
        "snapchat.com",
        "triller.co",
        "sharechat.com",
        "mojapp.in"
    )

    val SOCIAL_DOMAINS: Set<String> = setOf(
        "facebook.com",
        "fb.com",
        "fbcdn.net",
        "instagram.com",
        "cdninstagram.com",
        "threads.net",
        "twitter.com",
        "x.com",
        "twimg.com",
        "reddit.com",
        "redd.it",
        "redditmedia.com",
        "tumblr.com",
        "pinterest.com",
        "discord.com"
    )

    val ADULT_DOMAINS: Set<String> = setOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "redtube.com",
        "youporn.com",
        "onlyfans.com",
        "chaturbate.com"
    )

    fun normalizeDomain(raw: String): String {
        return raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .trim('.')
    }

    fun matchesDomainOrSubdomain(host: String, blockedDomain: String): Boolean {
        val cleanHost = normalizeDomain(host)
        val cleanBlocked = normalizeDomain(blockedDomain)
        if (cleanHost.isEmpty() || cleanBlocked.isEmpty()) return false
        return cleanHost == cleanBlocked || cleanHost.endsWith(".$cleanBlocked")
    }

    fun effectiveBlockedDomains(config: ContentBlockerConfig): Set<String> {
        val set = LinkedHashSet<String>(config.blockedDomains.size + 32)
        config.blockedDomains.forEach { raw ->
            val norm = normalizeDomain(raw)
            if (norm.contains('.')) set.add(norm)
        }
        if (config.blockShortVideoDomains) set.addAll(SHORT_VIDEO_DOMAINS)
        if (config.blockSocialDomains) set.addAll(SOCIAL_DOMAINS)
        if (config.blockAdultDomains) set.addAll(ADULT_DOMAINS)
        return set
    }

    fun shouldBlockDomain(
        domain: String,
        config: ContentBlockerConfig,
        isFocusLockActive: Boolean
    ): Boolean {
        if (!config.enabled || !config.vpnDnsBlockerEnabled) return false
        if (config.onlyDuringFocusLock && !isFocusLockActive) return false

        val cleanDomain = normalizeDomain(domain)
        if (cleanDomain.isEmpty()) return false

        // Match exact or parent suffix efficiently by walking DNS labels upward
        val blocklist = effectiveBlockedDomains(config)
        if (blocklist.isEmpty()) return false

        var candidate = cleanDomain
        while (candidate.isNotEmpty()) {
            if (blocklist.contains(candidate)) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || dot >= candidate.lastIndex) break
            candidate = candidate.substring(dot + 1)
        }
        return false
    }

    fun shouldVpnBeActive(
        config: ContentBlockerConfig,
        isFocusLockActive: Boolean
    ): Boolean {
        if (!config.enabled || !config.vpnDnsBlockerEnabled) return false
        if (config.onlyDuringFocusLock && !isFocusLockActive) return false
        return config.blockedDomains.isNotEmpty() ||
            config.blockShortVideoDomains ||
            config.blockSocialDomains ||
            config.blockAdultDomains
    }
}
