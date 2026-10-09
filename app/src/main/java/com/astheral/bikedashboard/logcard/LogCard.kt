package com.astheral.bikedashboard.logcard

import com.astheral.bikedashboard.data.BikeDetails
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** The dates a log card can carry, which are checked before they're used. */
enum class CardDate(val label: String) {
    REGISTRATION("Registration date"),
    COE_EXPIRY("COE expiry"),
    ROAD_TAX_EXPIRY("Road tax expiry"),
}

/**
 * What was read off an LTA Vehicle Log Card. Everything is optional: a field that's missing
 * or shown as "-" stays null. The owner's own details (name, NRIC, address …) are never read.
 * Dates that were there but couldn't be read are kept as text in [unreadable].
 */
data class LogCard(
    val plate: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val chassisNumber: String? = null,
    val engineNumber: String? = null,
    val iuNumber: String? = null,
    val registrationDate: LocalDate? = null,
    val manufactureYear: Int? = null,
    val engineCc: Int? = null,
    val primaryColour: String? = null,
    val secondaryColour: String? = null,
    val openMarketValue: Double? = null,
    val coePaid: Double? = null,
    val coeNumber: String? = null,
    val coeExpiry: LocalDate? = null,
    val roadTaxExpiry: LocalDate? = null,
    val unreadable: Map<CardDate, String> = emptyMap(),
) {
    /** True when nothing at all was found, i.e. the file probably isn't a log card. */
    val isEmpty: Boolean get() = this == LogCard()

    /** "Blue / White", "Blue", or null when the card has no colour. */
    val colour: String? get() = listOfNotNull(primaryColour, secondaryColour).joinToString(" / ").ifEmpty { null }

    fun date(which: CardDate): LocalDate? = when (which) {
        CardDate.REGISTRATION -> registrationDate
        CardDate.COE_EXPIRY -> coeExpiry
        CardDate.ROAD_TAX_EXPIRY -> roadTaxExpiry
    }

    /** This card with [which] set to [date] (typed in by hand), no longer marked unreadable. */
    fun withDate(which: CardDate, date: LocalDate?): LogCard {
        val c = copy(unreadable = unreadable - which)
        return when (which) {
            CardDate.REGISTRATION -> c.copy(registrationDate = date)
            CardDate.COE_EXPIRY -> c.copy(coeExpiry = date)
            CardDate.ROAD_TAX_EXPIRY -> c.copy(roadTaxExpiry = date)
        }
    }

    /** [details] with every value found on the card filled in; anything the card lacks is left as it was. */
    fun applyTo(details: BikeDetails): BikeDetails = details.copy(
        plate = plate ?: details.plate,
        brand = brand ?: details.brand,
        model = model ?: details.model,
        chassisNumber = chassisNumber ?: details.chassisNumber,
        engineNumber = engineNumber ?: details.engineNumber,
        registeredIu = iuNumber ?: details.registeredIu,
        registrationDate = registrationDate ?: details.registrationDate,
        manufactureYear = manufactureYear ?: details.manufactureYear,
        engineCc = engineCc ?: details.engineCc,
        colour = colour ?: details.colour,
        openMarketValue = openMarketValue ?: details.openMarketValue,
        coePaid = coePaid ?: details.coePaid,
        coeNumber = coeNumber ?: details.coeNumber,
        coeExpiry = coeExpiry ?: details.coeExpiry,
        roadTaxExpiry = roadTaxExpiry ?: details.roadTaxExpiry,
    )

    /** What will be filled in, as label → value, in the order the Bike details page shows them. */
    fun previewRows(): List<Pair<String, String>> = listOfNotNull(
        brand?.let { "Brand" to it },
        model?.let { "Model" to it },
        plate?.let { "Licence plate" to it },
        chassisNumber?.let { "Chassis number" to it },
        engineNumber?.let { "Engine number" to it },
        iuNumber?.let { "Registered IU" to it },
        registrationDate?.let { "Registration date" to it.format(SHOWN) },
        manufactureYear?.let { "Year of manufacture" to it.toString() },
        engineCc?.let { "Engine capacity" to "$it cc" },
        colour?.let { "Colour" to it },
        coeNumber?.let { "COE number" to it },
        coeExpiry?.let { "COE expiry" to it.format(SHOWN) },
        roadTaxExpiry?.let { "Road tax expiry" to it.format(SHOWN) },
        openMarketValue?.let { "Open market value" to money(it) },
        coePaid?.let { "COE paid" to money(it) },
    )
}

/** The bike's saved details in the card's shape, so they can be shown the same way as a card's values. */
fun BikeDetails.asLogCard(): LogCard = LogCard(
    plate = plate, brand = brand, model = model, chassisNumber = chassisNumber, engineNumber = engineNumber,
    iuNumber = registeredIu, registrationDate = registrationDate, manufactureYear = manufactureYear, engineCc = engineCc,
    primaryColour = colour, openMarketValue = openMarketValue, coePaid = coePaid, coeNumber = coeNumber,
    coeExpiry = coeExpiry, roadTaxExpiry = roadTaxExpiry,
)

private val SHOWN = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun money(v: Double) = String.format(Locale.ENGLISH, "S$%,.2f", v)

/** Something on the card that doesn't look right, which the user is asked about before it's saved. */
data class LogCardIssue(val date: CardDate, val kind: Kind, val message: String) {
    enum class Kind { EXPIRED, UNREADABLE, IN_FUTURE }
}

/**
 * Sense checks on the dates: the COE or road tax already run out (an old copy of the card),
 * a registration date that hasn't happened yet, or a date that couldn't be read at all.
 */
fun LogCard.issues(today: LocalDate): List<LogCardIssue> = CardDate.entries.mapNotNull { which ->
    val raw = unreadable[which]
    val date = date(which)
    when {
        raw != null -> LogCardIssue(which, LogCardIssue.Kind.UNREADABLE, "The ${which.label.lowercase()} on the card (\"$raw\") couldn't be read.")
        date == null -> null
        which == CardDate.REGISTRATION && date.isAfter(today) ->
            LogCardIssue(which, LogCardIssue.Kind.IN_FUTURE, "The registration date on the card, ${date.format(SHOWN)}, is in the future.")
        which != CardDate.REGISTRATION && date.isBefore(today) ->
            LogCardIssue(which, LogCardIssue.Kind.EXPIRED, "The ${which.label} on the card, ${date.format(SHOWN)}, has already passed. The card may be an old copy.")
        else -> null
    }
}

/**
 * Reads the text of an LTA Vehicle Log Card PDF. The PDF's text comes out as a label line
 * ("COE Expiry Date:") followed by its value line ("03 Aug 2025"); some extractors put both on
 * one line instead, and a few labels share a line ("Engine No.: Chassis No.:") with their values
 * on the next line in the same order. All three layouts are understood.
 */
object LogCardParser {
    /** Every label on the card, so a value line is never mistaken for a label and vice versa. */
    private val LABELS = listOf(
        "Vehicle No.", "Make/ Model", "Vehicle Scheme", "Current Propellant", "Chassis No.", "Vehicle Type",
        "Owner Name:", "Owner ID Type:", "NRIC/Passport/Company Cert No.:", "Registered Address", "Mailing Address:",
        "Birth Date", "Previous Vehicle No.:", "Effective Date of Ownership:", "Original Registration Date:",
        "Registration Date:", "No. of Transfers:", "IU Label No.:", "OBU Label No.:", "Engine No.:",
        "Year of Manufacture:", "Primary Colour:", "Secondary Colour:", "Passenger Capacity:",
        "Engine Capacity / Power Rating :", "Maximum Power Output:", "Max Unladen Weight:", "Maximum Laden Weight:",
        "Vehicle Attachment 1:", "Vehicle Attachment 2:", "Vehicle Attachment 3:", "No. Of Axles:",
        "Open Market Value:", "Additional Registration Fee Rate:", "Actual ARF Paid:", "Vehicle Lifespan Expiry Date:",
        "OPC Cash Rebate Eligibility:", "QP during COE Bidding Exercise:", "COE No.:", "COE Expiry Date:",
        "COE Category:", "COE Registration Category:", "Actual QP Paid", "QP (Regn Cat):", "PARF Eligibility:",
        "PARF Eligibility Expiry Date:", "Minimum PARF Benefit:", "CO2 Emission:", "CO Emission:", "HC Emission:",
        "NOx Emission:", "PM Emission:", "Message:", "Road Tax Expiry Date:", "Road Tax Expiry:",
    ).map(::key).distinct().sortedByDescending { it.length }

    /** Section headings, which are never values. */
    private val HEADINGS = listOf(
        "Vehicle Registration Details", "Owner's Details", "Registration Details", "Vehicle Specifications",
        "Additional Registration Fee (ARF) and COE Information", "PARF Rebate Details", "Vehicle Emissions Details",
    ).map(::key).toSet()

    private val DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)

    /** A label or line in a comparable form: lower case, single spaces, no colon, no spaces around "/". */
    private fun key(s: String): String = s.lowercase(Locale.ENGLISH)
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*/\\s*"), "/")
        .replace(Regex("\\s*:\\s*"), " ")
        .trim()

    /** The label [line] starts with (as a key) and the rest of the line, or null if it doesn't start with one. */
    private fun splitLabel(line: String): Pair<String, String>? {
        val k = key(line)
        val label = LABELS.firstOrNull { k == it || k.startsWith("$it ") } ?: return null
        return label to k.removePrefix(label).trim()
    }

    /** The labels [line] is made of, if it's nothing but labels; otherwise empty. */
    private fun allLabels(line: String): List<String> {
        val found = mutableListOf<String>()
        var rest = key(line)
        while (rest.isNotEmpty()) {
            val label = LABELS.firstOrNull { rest == it || rest.startsWith("$it ") } ?: return emptyList()
            found += label
            rest = rest.removePrefix(label).trim()
        }
        return found
    }

    private fun isLabelOrHeading(line: String) = key(line) in HEADINGS || splitLabel(line) != null

    /** Every label found in [text] with its value as written (original case), first one wins. */
    fun fields(text: String): Map<String, String> {
        val lines = text.lines().map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val out = LinkedHashMap<String, String>()
        fun put(label: String, value: String) {
            val v = value.trim().replace(Regex("\\s+"), " ")
            if (v.isNotEmpty() && label !in out) out[label] = v
        }
        for ((i, line) in lines.withIndex()) {
            val next = lines.getOrNull(i + 1)?.takeUnless(::isLabelOrHeading)
            val labels = allLabels(line)
            when {
                labels.size >= 2 -> {
                    // "Engine No.: Chassis No.:" then "E123 C456": pair them up if the counts match.
                    val parts = next?.split(Regex("\\s+")).orEmpty()
                    if (parts.size == labels.size) labels.zip(parts).forEach { (l, v) -> put(l, v) }
                }
                labels.size == 1 -> if (next != null) put(labels[0], next)
                else -> splitLabel(line)?.let { (label, _) ->
                    // "Vehicle No. SBA1234A": the value follows the label on the same line.
                    put(label, valueAfterLabel(line, label))
                }
            }
        }
        return out
    }

    /** The original-case text after [label] on [line]. */
    private fun valueAfterLabel(line: String, label: String): String {
        // Walk the line until its key matches the label, so the value keeps its own case.
        for (end in 1..line.length) {
            if (key(line.substring(0, end)) == label) {
                val rest = line.substring(end)
                if (rest.isEmpty() || rest[0].isWhitespace() || rest[0] == ':') return rest.trimStart(':', ' ', '\t').trim()
            }
        }
        return ""
    }

    fun parse(text: String): LogCard {
        val f = fields(text)
        fun get(label: String): String? = f[key(label)]?.takeUnless { it == "-" || it.equals("nil", ignoreCase = true) }
        val unreadable = mutableMapOf<CardDate, String>()
        fun date(which: CardDate, vararg labels: String): LocalDate? {
            val raw = labels.firstNotNullOfOrNull { get(it) } ?: return null
            return parseDate(raw) ?: run { unreadable[which] = raw; null }
        }

        val (brand, model) = get("Make/ Model")?.let(::splitMakeModel) ?: (null to null)
        return LogCard(
            plate = get("Vehicle No.")?.uppercase()?.replace(" ", ""),
            brand = brand,
            model = model,
            chassisNumber = get("Chassis No.:")?.uppercase(),
            engineNumber = get("Engine No.:")?.uppercase(),
            iuNumber = get("IU Label No.:"),
            registrationDate = date(CardDate.REGISTRATION, "Registration Date:", "Original Registration Date:"),
            manufactureYear = get("Year of Manufacture:")?.trim()?.toIntOrNull(),
            engineCc = get("Engine Capacity / Power Rating :")?.let { Regex("(\\d[\\d,]*)\\s*cc", RegexOption.IGNORE_CASE).find(it) }
                ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull(),
            primaryColour = get("Primary Colour:")?.let(::titleCase),
            secondaryColour = get("Secondary Colour:")?.let(::titleCase),
            openMarketValue = get("Open Market Value:")?.let(::money),
            coePaid = get("Actual QP Paid")?.let(::money),
            coeNumber = get("COE No.:")?.uppercase(),
            coeExpiry = date(CardDate.COE_EXPIRY, "COE Expiry Date:"),
            roadTaxExpiry = date(CardDate.ROAD_TAX_EXPIRY, "Road Tax Expiry Date:", "Road Tax Expiry:"),
            unreadable = unreadable,
        )
    }

    /** "03 Aug 2025" (also "3 AUG 2025", "03/08/2025" and "2025-08-03"), or null. */
    fun parseDate(raw: String): LocalDate? {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        val words = s.split(" ")
        if (words.size == 3) {
            val month = words[1].lowercase(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
            try { return LocalDate.parse("${words[0]} $month ${words[2]}", DATE) } catch (_: DateTimeParseException) { }
        }
        Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})").matchEntire(s)?.let { m ->
            val (d, mo, y) = m.destructured
            return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }
        return runCatching { LocalDate.parse(s) }.getOrNull()
    }

    /** "YAMAHA/YZF-R3 ABS" → ("Yamaha", "YZF-R3 ABS"). With no "/", it's all the model. */
    private fun splitMakeModel(s: String): Pair<String?, String?> {
        val slash = s.indexOf('/')
        if (slash < 0) return null to s.trim().ifEmpty { null }
        return titleCase(s.substring(0, slash)).ifEmpty { null } to s.substring(slash + 1).trim().ifEmpty { null }
    }

    /** "HARLEY-DAVIDSON" → "Harley-Davidson", "LIGHT BLUE" → "Light Blue"; makes like "KTM" and "BMW" stay in capitals. */
    private fun titleCase(s: String): String = s.trim().split(" ").filter { it.isNotEmpty() }.joinToString(" ") { word ->
        if (word.uppercase(Locale.ENGLISH) in KEEP_UPPER) word.uppercase(Locale.ENGLISH)
        else word.lowercase(Locale.ENGLISH).split("-").joinToString("-") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private val KEEP_UPPER = setOf("BMW", "KTM", "MV", "TVS", "SYM", "CF", "BYD", "MG", "AJS", "BSA", "CCM")

    private fun money(s: String): Double? = s.replace(Regex("[^0-9.]"), "").toDoubleOrNull()
}
