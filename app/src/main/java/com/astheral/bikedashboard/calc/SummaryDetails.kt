package com.astheral.bikedashboard.calc

/** The small lines under the big number on the page after adding a fill-up, in the order they show. */
enum class SummaryDetail(val shownByDefault: Boolean) {
    COST_LITRES(true),
    PRICE(true),
    DISTANCE(true),
    ECONOMY(true),
    COST_PER_KM(false),
    DAYS(false),
    ODOMETER(false),
    SAVINGS_30_DAYS(false),
}

/**
 * Which detail lines the fill-up summary shows. The same for every bike.
 * Saved as every option in order, hidden ones with "!" in front, e.g. "COST_LITRES,!DAYS".
 */
data class SummaryDetails(val shown: Set<SummaryDetail> = SummaryDetail.entries.filter { it.shownByDefault }.toSet()) {
    /** The lines to show, in their fixed order. */
    val visible: List<SummaryDetail> get() = SummaryDetail.entries.filter { it in shown }

    fun setShown(detail: SummaryDetail, on: Boolean): SummaryDetails = copy(shown = if (on) shown + detail else shown - detail)

    fun format(): String = SummaryDetail.entries.joinToString(",") { if (it in shown) it.name else "!${it.name}" }

    companion object {
        /**
         * Reads [format]'s text. Unknown names are skipped, and options it doesn't mention (new ones
         * from a later version) take their default. Nothing saved means the defaults.
         */
        fun parse(text: String?): SummaryDetails {
            if (text.isNullOrBlank()) return SummaryDetails()
            val on = mutableSetOf<SummaryDetail>()
            val mentioned = mutableSetOf<SummaryDetail>()
            text.split(',').forEach { raw ->
                val token = raw.trim()
                val detail = SummaryDetail.entries.firstOrNull { it.name == token.removePrefix("!") } ?: return@forEach
                if (!mentioned.add(detail)) return@forEach
                if (!token.startsWith("!")) on += detail
            }
            SummaryDetail.entries.filter { it !in mentioned && it.shownByDefault }.forEach { on += it }
            return SummaryDetails(on)
        }
    }
}
