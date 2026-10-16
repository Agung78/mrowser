package net.mrowser.web

/**
 * Pure: how the address bar shows the current page's URL — the host bright, the rest dimmed,
 * and for https a lock in place of the scheme.
 *
 * [Parts.text] is what the field holds, so it must survive an unedited Enter: an https URL
 * drops its `https://` (UrlNormalizer puts exactly that back), any other scheme keeps it in
 * [Parts.lead]. The host is kept as written (no lowercasing), with an IPv6 literal's brackets;
 * userinfo stays in the lead and a port goes to the rest. `null` for anything that is not an
 * http(s) URL with a host — `about:blank`, `data:` — which the bar shows as plain text.
 */
object UrlDisplay {

    data class Parts(val secure: Boolean, val lead: String, val host: String, val rest: String) {
        val text: String get() = lead + host + rest
    }

    private val WEB_SCHEME = Regex("(?i)^(https?)://")

    fun of(url: String): Parts? {
        val scheme = WEB_SCHEME.find(url) ?: return null
        val secure = scheme.groupValues[1].equals("https", ignoreCase = true)
        val afterScheme = url.substring(scheme.range.last + 1)
        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd)
        val userInfo = authority.substring(0, authority.lastIndexOf('@') + 1)
        val hostPort = authority.substring(userInfo.length)
        val host = if (hostPort.startsWith("[")) {
            hostPort.substring(0, hostPort.indexOf(']').let { if (it < 0) hostPort.length else it + 1 })
        } else {
            hostPort.substringBefore(':')
        }
        if (host.isEmpty()) return null
        val lead = (if (secure) "" else scheme.value) + userInfo
        val rest = hostPort.substring(host.length) + afterScheme.substring(authorityEnd)
        return Parts(secure, lead, host, rest)
    }
}
