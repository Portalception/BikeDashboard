package com.astheral.bikedashboard.data

import android.content.Context
import com.astheral.bikedashboard.calc.DEFAULT_TRIP_KM
import com.astheral.bikedashboard.ui.theme.AppearanceSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The bikes in the app and which one is being shown. */
class Profiles private constructor(context: Context) {
    private val db = AppDatabase.get(context)
    private val dao = db.profileDao()
    private val fillUps = db.fillUpDao()
    private val settings = Settings.get(context)

    val all: Flow<List<Profile>> = dao.observeAll()

    /** Fill-ups per bike, for the switcher. */
    val counts: Flow<Map<Long, Int>> = dao.observeCounts().map { list -> list.associate { it.profileId to it.count } }

    /** The bike being shown: the one picked last, or the first if that one's gone. Null when there are none yet. */
    val active: Flow<Profile?> = combine(all, settings.activeProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.distinctUntilChanged()

    /** The active bike together with its fill-ups, oldest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeFillUps: Flow<Pair<Profile?, List<FillUp>>> = active.flatMapLatest { profile ->
        if (profile == null) flowOf(null to emptyList()) else fillUps.observeAll(profile.id).map { profile to it }
    }

    /**
     * The round trip into Malaysia taken off the savings, the same for every bike. The first time,
     * before it's ever been set, it starts from the trip that used to be kept on the active bike
     * (or the first one) and saves that, so the number carries over.
     */
    val tripKm: Flow<Double> = combine(settings.tripKm, active) { saved, profile ->
        saved ?: profile?.tripKm?.also(settings::setTripKm) ?: DEFAULT_TRIP_KM
    }.distinctUntilChanged()

    /** How the app looks for the bike being shown; each bike remembers its own. */
    val appearance: Flow<AppearanceSettings> =
        combine(active, settings.appearances) { profile, looks -> looks.of(profile?.id) }.distinctUntilChanged()

    /** The look to show before the bikes have loaded: the last bike picked, if any. */
    fun appearanceNow(): AppearanceSettings =
        settings.appearances.value.of(settings.activeProfileId.value.takeIf { it != Settings.NO_PROFILE })

    /** Changes the look of the bike being shown. */
    suspend fun updateAppearance(change: (AppearanceSettings) -> AppearanceSettings) {
        val id = activeNow()?.id ?: return
        settings.updateAppearance(id, change)
    }

    suspend fun activeNow(): Profile? = active.first()

    suspend fun get(id: Long): Profile? = dao.getAll().firstOrNull { it.id == id }

    /** Adds a bike and switches to it. It starts with the look of the bike that was showing. */
    suspend fun create(profile: Profile): Long {
        val look = settings.appearances.value.of(activeNow()?.id)
        val id = dao.insert(profile.copy(id = 0))
        settings.setAppearance(id, look)
        settings.setActiveProfile(id)
        return id
    }

    suspend fun update(profile: Profile) = dao.update(profile)

    fun switchTo(id: Long) = settings.setActiveProfile(id)

    /** Deletes the bike and its fill-ups. The app then shows the first bike left. */
    suspend fun delete(profile: Profile) {
        dao.deleteWithFillUps(profile)
        settings.forgetAppearance(profile.id)
    }

    companion object {
        @Volatile
        private var instance: Profiles? = null

        fun get(context: Context): Profiles =
            instance ?: synchronized(this) {
                instance ?: Profiles(context.applicationContext).also { instance = it }
            }
    }
}
