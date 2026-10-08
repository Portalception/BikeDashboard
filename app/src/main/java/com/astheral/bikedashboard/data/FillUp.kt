package com.astheral.bikedashboard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class Country { SG, MY, MIXED }

/**
 * One trip to the pump. The tank is always filled to full.
 *
 * Money is stored in SGD. A fill-up is made of up to three parts:
 * - SG part: litres pumped and SGD paid in Singapore.
 * - MY part: litres pumped in Malaysia and their cost in SGD (pump charge ÷ rate).
 * - Unsplit part: only for old mixed fill-ups from the spreadsheet, which recorded
 *   one total for both countries.
 */
@Serializable
@Entity(tableName = "fill_ups", indices = [Index("date"), Index("profileId")])
data class FillUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The bike this fill-up belongs to. Fill-ups from before profiles existed belong to profile 1. */
    @ColumnInfo(defaultValue = "1") val profileId: Long = 1,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val country: Country,
    val odometerKm: Int,

    val sgLitres: Double? = null,
    val sgCostSgd: Double? = null,

    val myLitres: Double? = null,
    val myCostSgd: Double? = null,
    /** Ringgit paid up front. */
    val rmPaid: Double? = null,
    /** Exchange rate, in RM per 1 SGD. */
    val rmPerSgd: Double? = null,
    /** What the pump actually charged, in RM. */
    val rmCharged: Double? = null,

    val unsplitLitres: Double? = null,
    val unsplitCostSgd: Double? = null,

    /** Fuel-gauge bars left before filling. */
    val barsLeft: Int? = null,
    /** Km ridden after the fuel light came on, stored as a positive number. */
    val reserveKm: Double? = null,

    /** True when there was no special trip into Malaysia, so no trip cost is subtracted from savings. */
    val noSpecialTrip: Boolean = false,

    /** "Estimated Max" from the spreadsheet. Kept for export only, never shown. */
    val estimatedMax: Double? = null,
    /** Savings from the spreadsheet. Imported rows keep this instead of being recalculated. */
    val importedSavingsSgd: Double? = null,
)
