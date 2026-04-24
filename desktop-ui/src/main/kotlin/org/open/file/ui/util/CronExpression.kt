package org.open.file.ui.util

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A parsed 5-field POSIX cron expression — purposely minimal so we own
 * the code and don't carry an external dependency for what's ultimately
 * a triggering helper.
 *
 * Field layout:
 *
 * ```
 *  ┌───────────── minute (0-59)
 *  │ ┌─────────── hour (0-23)
 *  │ │ ┌───────── day of month (1-31)
 *  │ │ │ ┌─────── month (1-12)
 *  │ │ │ │ ┌───── day of week (0-6, Sunday = 0)
 *  │ │ │ │ │
 *  * * * * *
 * ```
 *
 * Supported syntax per field:
 *  - `*`        every value
 *  - `N`        single value
 *  - `N-M`      inclusive range
 *  - `* / N`    step from the field's start
 *  - `N-M/step` stepped range
 *  - `A,B,C`    comma-separated list of any of the above
 *
 * Deliberately **not** supported (no use case yet + each adds rules you
 * have to document and test): `?`, `L`, `W`, `#`, literal month / day
 * names, or the seconds / year fields from the Quartz dialect. Users
 * who need those should swap in [cron-utils] here.
 *
 * Day-of-month and day-of-week semantics follow the POSIX rule: when
 * **both** are restricted, a minute fires if *either* matches (OR), not
 * both. When one is `*` it's treated as unrestricted and the other
 * becomes the sole constraint. This matches what `crond` does on
 * practically every Linux host.
 */
class CronExpression private constructor(
    private val minute: FieldMatcher,
    private val hour: FieldMatcher,
    private val dayOfMonth: FieldMatcher,
    private val month: FieldMatcher,
    private val dayOfWeek: FieldMatcher,

    private val domRestricted: Boolean,
    private val dowRestricted: Boolean,
    /** Original text — kept for display / persistence round-trips. */
    val source: String,
) {

    /**
     * Find the first epoch-millisecond >= [fromEpochMs] (in [zone]) that
     * matches every field, or null if the scan hits the iteration cap
     * without a hit (impossible expressions like Feb 30 on a
     * non-wildcard month).
     *
     * We scan minute-by-minute rather than doing the field-by-field
     * forward-jump dance that Quartz uses: implementation cost is much
     * lower and the worst-case hot path is "daily at 00:00 on a
     * leap-year Feb 29" which wraps after ~2M iterations — still
     * sub-second on desktop hardware, and a scheduler tick is every
     * 30s, so we've got plenty of headroom.
     */
    fun nextAfter(fromEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        // Start at the *next* minute — the schedule shouldn't fire on
        // its own trigger time. Truncate to minute precision so we
        // match whole-minute cron fields cleanly.
        var candidate = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(fromEpochMs),
            zone,
        ).plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)

        // 4-year cap covers every cron edge case we support, including
        // Feb 29 + day-of-week combos that can take up to 4 years to
        // align. Past that, treat the expression as unmatchable rather
        // than spinning forever.
        val maxIter = 4 * 366 * 24 * 60
        var i = 0
        while (i < maxIter) {
            if (matches(candidate)) return candidate.toInstant().toEpochMilli()
            candidate = candidate.plusMinutes(1)
            i++
        }
        return null
    }

    private fun matches(t: ZonedDateTime): Boolean {
        if (!minute.matches(t.minute)) return false
        if (!hour.matches(t.hour)) return false
        if (!month.matches(t.monthValue)) return false
        // Day-of-week: Java's 1=Mon .. 7=Sun; cron is 0=Sun..6=Sat.
        // Translate so 0/7 both mean Sunday (some crontabs accept 7).
        val dowCron = t.dayOfWeek.value % 7
        val domOk = dayOfMonth.matches(t.dayOfMonth)
        val dowOk = dayOfWeek.matches(dowCron)
        return when {
            // Both restricted: POSIX OR rule.
            domRestricted && dowRestricted -> domOk || dowOk
            // Only dom restricted: dow must be *, so the minute fires
            // iff the day-of-month matches.
            domRestricted -> domOk
            dowRestricted -> dowOk
            // Neither restricted: every day matches.
            else -> true
        }
    }

    companion object {
        /**
         * Parse [text] into a [CronExpression]. Whitespace is
         * collapsed before splitting so "0   0 * * *" still parses.
         * Returns null if any field is malformed; the caller renders
         * that as a validation error in the UI.
         */
        fun parseOrNull(text: String): CronExpression? = runCatching { parse(text) }.getOrNull()

        fun parse(text: String): CronExpression {
            val tokens = text.trim().split(Regex("\\s+"))
            require(tokens.size == 5) {
                "Cron expression must have 5 fields (min hour dom month dow), got ${tokens.size}"
            }
            val minTok = tokens[0]
            val hourTok = tokens[1]
            val domTok = tokens[2]
            val monTok = tokens[3]
            val dowTok = tokens[4]
            return CronExpression(
                minute = FieldMatcher.build(minTok, 0, 59),
                hour = FieldMatcher.build(hourTok, 0, 23),
                dayOfMonth = FieldMatcher.build(domTok, 1, 31),
                month = FieldMatcher.build(monTok, 1, 12),
                // accept 7 as alias for Sunday; fold back to 0 so the
                // matcher's index space matches `dayOfWeek % 7` from
                // Java's Monday-based calendar.
                dayOfWeek = FieldMatcher.build(dowTok, 0, 7).copyWithSundayFold(),
                domRestricted = domTok.trim() != "*",
                dowRestricted = dowTok.trim() != "*",
                source = tokens.joinToString(" "),
            )
        }

        /** Convenience presets the UI can offer as one-click starters. */
        val PRESETS: List<Pair<String, String>> = listOf(
            "Every 15 min" to "*/15 * * * *",
            "Hourly" to "0 * * * *",
            "Daily at midnight" to "0 0 * * *",
            "Weekdays at 9am" to "0 9 * * 1-5",
            "Sundays at noon" to "0 12 * * 0",
        )
    }

    /**
     * Bitset-backed matcher for a single cron field. We materialise
     * every allowed integer up-front so `matches(n)` is O(1) — for
     * five fields bounded at 60 values each that's negligible memory
     * (~40 bytes total) and saves re-parsing on every tick.
     *
     * Plain class (not `data class`) deliberately: data class would
     * try to generate structural equals over the BooleanArray, which
     * uses identity comparison and triggers a compiler warning. We
     * never compare matchers, so skipping it sidesteps the issue.
     */
    private class FieldMatcher(
        private val allowed: BooleanArray,
        private val minValue: Int,
    ) {
        fun matches(value: Int): Boolean {
            val idx = value - minValue
            return idx in allowed.indices && allowed[idx]
        }

        /**
         * Day-of-week uses 0..7 internally to accept `7` as Sunday;
         * fold it back to 0 so downstream matching on the
         * (day-of-week mod 7) value from Java's Monday-based calendar
         * still lines up.
         */
        fun copyWithSundayFold(): FieldMatcher {
            if (allowed.size <= 7) return this
            val folded = BooleanArray(7)
            for (i in allowed.indices) {
                val cronVal = i + minValue
                folded[cronVal % 7] = folded[cronVal % 7] || allowed[i]
            }
            return FieldMatcher(folded, 0)
        }

        companion object {
            fun build(token: String, minAllowed: Int, maxAllowed: Int): FieldMatcher {
                val bits = BooleanArray(maxAllowed - minAllowed + 1)
                // Top-level comma-splitting — each piece is parsed
                // independently and OR'd into the final bitset.
                token.split(",").forEach { piece ->
                    applyPiece(piece.trim(), minAllowed, maxAllowed, bits)
                }
                return FieldMatcher(bits, minAllowed)
            }

            private fun applyPiece(
                piece: String,
                minAllowed: Int,
                maxAllowed: Int,
                bits: BooleanArray,
            ) {
                // Parse trailing step ("/N") if present — can attach to
                // either `*` or an explicit range. Split once and
                // validate the shape up front so the rest of the
                // function can assume a well-formed range/step pair.
                val slashParts = piece.split("/")
                require(slashParts.size in 1..2) { "Invalid step in '$piece'" }
                val rangePart = slashParts[0]
                val stepPart: Int? = slashParts.getOrNull(1)?.toIntOrNull()
                if (slashParts.size == 2) {
                    require(stepPart != null && stepPart > 0) { "Step must be a positive integer in '$piece'" }
                }
                val step = stepPart ?: 1

                val lo: Int
                val hi: Int
                when {
                    rangePart == "*" -> {
                        lo = minAllowed
                        hi = maxAllowed
                    }
                    rangePart.contains("-") -> {
                        val dashParts = rangePart.split("-")
                        require(dashParts.size == 2) { "Invalid range '$rangePart'" }
                        val a = dashParts[0].toInt()
                        val b = dashParts[1].toInt()
                        require(a in minAllowed..maxAllowed && b in minAllowed..maxAllowed && a <= b) {
                            "Invalid range '$rangePart' (allowed: $minAllowed-$maxAllowed)"
                        }
                        lo = a
                        hi = b
                    }
                    else -> {
                        val v = rangePart.toInt()
                        require(v in minAllowed..maxAllowed) { "Value $v out of range $minAllowed-$maxAllowed" }
                        // Bare number with a step means "start at N,
                        // go to max in strides of step" — that's what
                        // `5/10` classically does.
                        lo = v
                        hi = if (stepPart != null) maxAllowed else v
                    }
                }

                var i = lo
                while (i <= hi) {
                    bits[i - minAllowed] = true
                    i += step
                }
            }
        }
    }
}
