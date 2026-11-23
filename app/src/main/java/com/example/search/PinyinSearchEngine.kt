package com.example.search

import android.icu.text.Transliterator
import android.os.Build
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Zero-dependency Chinese Pinyin, initials, and mixed-script search matcher powered by
 * Android's built-in ICU Transliterator (API 29+) with clean ASCII-folding fallback.
 *
 * Examples:
 * - "微信" -> matches "微信", "wx", "weix", "weixin"
 * - "支付宝" -> matches "支付宝", "zfb", "zhifb", "zhifubao"
 * - "QQ音乐" -> matches "qq", "qqyy", "qqyinyue", "yy"
 * - "高德地图" -> matches "gddt", "gaode", "dt"
 */
object PinyinSearchEngine {

    const val NO_MATCH = Int.MAX_VALUE
    private const val MAX_CACHE_SIZE = 4_000

    private data class PinyinKey(
        val lowerRaw: String,
        val fullPinyin: String,
        val initials: String,
        val mixedInitials: String,
        val syllables: List<String>,
    )

    private val cache = ConcurrentHashMap<String, PinyinKey>()

    private val hanToAscii: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                Transliterator.getInstance("Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
            }.getOrNull()
        } else {
            null
        }
    }

    fun prewarm(texts: Iterable<String>) {
        for (text in texts) {
            if (text.isNotBlank()) keyFor(text)
        }
    }

    fun matches(query: String, primary: String, secondary: String? = null): Boolean =
        matchScore(query, primary, secondary) != NO_MATCH

    /**
     * Computes a relevance score for [query] against [primary] (and optional [secondary]).
     * Lower score means higher relevance; returns [NO_MATCH] if nothing matches.
     */
    fun matchScore(
        query: String,
        primary: String,
        secondary: String? = null,
    ): Int {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return 0

        val qLower = trimmed.lowercase(Locale.ROOT)
        val qCompact = qLower.filterNot { it.isWhitespace() }
        val key = keyFor(primary)

        // 1. Exact raw / initials / full pinyin match
        if (key.lowerRaw == qLower) return 0
        if (qCompact.isNotEmpty() && (key.initials == qCompact || key.mixedInitials == qCompact)) return 1
        if (qCompact.isNotEmpty() && key.fullPinyin == qCompact) return 2

        // 2. Prefix matches on raw label, initials, mixed initials, or full pinyin
        if (key.lowerRaw.startsWith(qLower)) return 10
        if (qCompact.isNotEmpty() && key.initials.startsWith(qCompact)) return 12
        if (qCompact.isNotEmpty() && key.mixedInitials.startsWith(qCompact)) return 13
        if (qCompact.isNotEmpty() && key.fullPinyin.startsWith(qCompact)) return 14

        // 3. Syllable-prefix chain match from start (e.g. "zhifb" -> zhi + f + b, "weix" -> wei + x)
        if (qCompact.isNotEmpty() && matchesSyllableChain(key.syllables, qCompact, fromStartOnly = true)) {
            return 16
        }

        // 4. Substring match on raw label
        val rawIdx = key.lowerRaw.indexOf(qLower)
        if (rawIdx >= 0) return 30 + rawIdx.coerceAtMost(20)

        // 5. Substring match on initials / mixed initials / syllable chain / full pinyin
        if (qCompact.isNotEmpty()) {
            val initIdx = key.initials.indexOf(qCompact)
            if (initIdx >= 0) return 55 + initIdx.coerceAtMost(20)

            val mixedIdx = key.mixedInitials.indexOf(qCompact)
            if (mixedIdx >= 0) return 60 + mixedIdx.coerceAtMost(20)

            if (matchesSyllableChain(key.syllables, qCompact, fromStartOnly = false)) {
                return 68
            }

            val pyIdx = key.fullPinyin.indexOf(qCompact)
            if (pyIdx >= 0) return 75 + pyIdx.coerceAtMost(20)
        }

        // 6. Optional secondary text (e.g. package name, app label, phone digits)
        if (!secondary.isNullOrBlank()) {
            val secLower = secondary.lowercase(Locale.ROOT)
            if (secLower.contains(qLower)) return 110
            if (qCompact.isNotEmpty()) {
                val secKey = keyFor(secondary)
                if (secKey.initials.contains(qCompact) ||
                    secKey.mixedInitials.contains(qCompact) ||
                    secKey.fullPinyin.contains(qCompact)
                ) {
                    return 125
                }
            }
        }

        return NO_MATCH
    }

    private fun keyFor(raw: String): PinyinKey {
        cache[raw]?.let { return it }
        val built = buildKey(raw)
        if (cache.size >= MAX_CACHE_SIZE) {
            cache.clear()
        }
        cache[raw] = built
        return built
    }

    private fun buildKey(raw: String): PinyinKey {
        val lowerRaw = raw.lowercase(Locale.ROOT)
        val syllables = ArrayList<String>(raw.length.coerceAtMost(24))
        val fullBuilder = StringBuilder(raw.length * 2)
        val initialsBuilder = StringBuilder(raw.length)
        val mixedBuilder = StringBuilder(raw.length)

        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                isHanChar(ch) -> {
                    val py = transliterateHanChar(ch)
                    if (py.isNotEmpty()) {
                        syllables.add(py)
                        fullBuilder.append(py)
                        initialsBuilder.append(py[0])
                        mixedBuilder.append(py[0])
                    }
                    i += 1
                }

                ch.isLetterOrDigit() -> {
                    val start = i
                    while (i < raw.length && raw[i].isLetterOrDigit() && !isHanChar(raw[i])) {
                        i += 1
                    }
                    val token = foldAscii(raw.substring(start, i)).lowercase(Locale.ROOT)
                    if (token.isNotEmpty()) {
                        syllables.add(token)
                        fullBuilder.append(token)
                        initialsBuilder.append(token[0])
                        mixedBuilder.append(token)
                    }
                }

                else -> {
                    i += 1
                }
            }
        }

        return PinyinKey(
            lowerRaw = lowerRaw,
            fullPinyin = fullBuilder.toString(),
            initials = initialsBuilder.toString(),
            mixedInitials = mixedBuilder.toString(),
            syllables = syllables,
        )
    }

    private fun transliterateHanChar(ch: Char): String {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (hanToAscii as? Transliterator)?.transliterate(ch.toString())
        } else {
            null
        } ?: return ""
        return raw.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
    }

    private fun foldAscii(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val sb = StringBuilder(normalized.length)
        for (c in normalized) {
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun isHanChar(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x4E00..0x9FFF) ||
            (code in 0x3400..0x4DBF) ||
            (code in 0xF900..0xFAFF)
    }

    /**
     * Checks whether [query] can be matched by taking non-empty prefixes of consecutive [syllables].
     * Example: syllables = ["zhi", "fu", "bao"] matches "zfb", "zhif", "zhifb", "zhifubao".
     */
    private fun matchesSyllableChain(
        syllables: List<String>,
        query: String,
        fromStartOnly: Boolean,
    ): Boolean {
        if (syllables.isEmpty() || query.isEmpty()) return false
        val maxStart = if (fromStartOnly) 0 else syllables.lastIndex
        for (startIdx in 0..maxStart) {
            if (matchFrom(syllables, startIdx, query, 0)) return true
        }
        return false
    }

    private fun matchFrom(
        syllables: List<String>,
        sylIdx: Int,
        query: String,
        qPos: Int,
    ): Boolean {
        if (qPos >= query.length) return true
        if (sylIdx >= syllables.size) return false
        val syl = syllables[sylIdx]
        if (syl.isEmpty() || syl[0] != query[qPos]) return false

        val maxLen = minOf(syl.length, query.length - qPos)
        // Prefer consuming as much of the current syllable as matches, backtracking down to 1 char
        var matchedLen = 0
        while (matchedLen < maxLen && syl[matchedLen] == query[qPos + matchedLen]) {
            matchedLen += 1
        }
        for (take in matchedLen downTo 1) {
            if (matchFrom(syllables, sylIdx + 1, query, qPos + take)) {
                return true
            }
        }
        return false
    }
}
