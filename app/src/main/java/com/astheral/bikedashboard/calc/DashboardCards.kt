package com.astheral.bikedashboard.calc

/** The number cards on the dashboard, in their default order. */
enum class DashCard {
    ECONOMY, KM_PER_DAY, COST_PER_DAY, COST_PER_KM, PRICE, SAVINGS;
}

/**
 * Which dashboard cards show, and in what order. [order] always holds every card exactly once;
 * [hidden] never holds all of them, so at least one card stays on the dashboard.
 */
data class CardLayout(
    val order: List<DashCard> = DashCard.entries.toList(),
    val hidden: Set<DashCard> = emptySet(),
) {
    /** The cards to show, in order. */
    val visible: List<DashCard> get() = order.filter { it !in hidden }

    /** Moves [card] to position [to] (clamped to the list). */
    fun move(card: DashCard, to: Int): CardLayout {
        val from = order.indexOf(card)
        if (from < 0) return this
        val target = to.coerceIn(0, order.lastIndex)
        if (target == from) return this
        val list = order.toMutableList()
        list.removeAt(from)
        list.add(target, card)
        return copy(order = list)
    }

    /** Shows or hides [card]; hiding the last visible card does nothing. */
    fun setShown(card: DashCard, shown: Boolean): CardLayout = when {
        shown -> copy(hidden = hidden - card)
        canHide(card) -> copy(hidden = hidden + card)
        else -> this
    }

    /** False for the only card still showing. */
    fun canHide(card: DashCard): Boolean = card in hidden || visible.size > 1

    /** For saving: the order, with "!" in front of hidden cards, e.g. "ECONOMY,!KM_PER_DAY,PRICE". */
    fun format(): String = order.joinToString(",") { if (it in hidden) "!${it.name}" else it.name }

    val isDefault: Boolean get() = this == CardLayout()

    companion object {
        /**
         * Reads [format]'s text. Unknown or repeated names are skipped, and cards it doesn't mention
         * (new ones from a later version) are added at the end, shown, in their default order.
         * If every card would be hidden, they're all shown.
         */
        fun parse(text: String?): CardLayout {
            if (text.isNullOrBlank()) return CardLayout()
            val order = mutableListOf<DashCard>()
            val hidden = mutableSetOf<DashCard>()
            text.split(',').forEach { raw ->
                val token = raw.trim()
                val off = token.startsWith("!")
                val card = DashCard.entries.firstOrNull { it.name == token.removePrefix("!") } ?: return@forEach
                if (card in order) return@forEach
                order += card
                if (off) hidden += card
            }
            DashCard.entries.filter { it !in order }.forEach { order += it }
            if (hidden.size >= order.size) hidden.clear()
            return CardLayout(order, hidden)
        }
    }
}
