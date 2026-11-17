/*
 * LotteryController.kt
 * Validates and publishes owner lottery settings, checks registration and
 * waiting-list capacity, and supplies entrant display names. Models hold data;
 * DrawController handles drawing and repositories handle persistence.
 * Known issues: none.
 */

package com.example.code.controller

import com.example.code.data.LotteryCreationRepository
import com.example.code.model.Campsite
import com.example.code.model.Lottery
import com.example.code.model.WaitingListEntry

/** Rules for lotteries. */
object LotteryController {
    //The two below methods, publishLottery and validateCreation by GPT6-Sol "Based on the required project specifications provided and the existing code, I need help adding in code for US 2.04.01, 03,04, 05, and 07", 11-05-2026
    /**
     * Publishes settings for an owned campsite (US 02.04.01, .03, .04, .05, .07).
     * Identity comes from [campsite], never editable form fields. Draw results
     * remain the responsibility of DrawController and waiting-list entries.
     * Returns the saved Firestore document ID; failures propagate to the form.
     */
    suspend fun publishLottery(
        repository: LotteryCreationRepository,
        campsite: Campsite,
        ownerId: String,
        settings: Lottery,
    ): String {
        require(ownerId.isNotBlank() && campsite.ownerId == ownerId) {
            "You can only create lotteries for your own campsites."
        }
        val lottery = settings.copy(
            id = "",
            campsiteId = campsite.id,
            campsiteName = campsite.name,
            ownerId = campsite.ownerId,
            waitingListCount = 0,
            status = Lottery.STATUS_OPEN,
        )
        validateCreation(lottery)
        return repository.createLottery(lottery)
    }

    /** Shared form/persistence validation. Throws with a message for the owner. */
    fun validateCreation(lottery: Lottery) {
        require(lottery.campsiteId.isNotBlank() && '/' !in lottery.campsiteId) {
            "A saved campsite is required."
        }
        require(lottery.campsiteName.isNotBlank()) { "The campsite needs a name." }
        require(lottery.ownerId.isNotBlank()) { "An owner ID is required." }
        require(lottery.registrationStart < lottery.registrationEnd) {
            "Registration must start before it ends."
        }
        require(lottery.registrationEnd <= lottery.bookingStart) {
            "Registration must end on or before the booking period starts."
        }
        require(lottery.bookingStart < lottery.bookingEnd) {
            "The booking period must start before it ends."
        }
        require(lottery.winnerCount >= 1) { "Choose at least one winner." }
        require(lottery.waitlistCapacity == null || lottery.waitlistCapacity >= lottery.winnerCount) {
            "Waiting-list capacity must be at least the number of winners, or left blank."
        }
        // US 02.04.02: extra campsites must be named, distinct and not the main one.
        require(
            lottery.extraCampsiteOwnerIds.isEmpty() ||
                lottery.extraCampsiteOwnerIds.size == lottery.extraCampsiteIds.size,
        ) { "Every extra campsite needs its owner." }
        require(lottery.extraCampsiteIds.size == lottery.extraCampsiteNames.size) {
            "Each extra campsite needs a name."
        }
        require(
            lottery.campsiteId !in lottery.extraCampsiteIds &&
                lottery.extraCampsiteIds.toSet().size == lottery.extraCampsiteIds.size,
        ) {
            "Each campsite can only be listed once."
        }
    }

    /**
     * Checks new settings for an open lottery (US 02.04.06): it must still
     * be open, keep its campsite and owner, pass [validateCreation], and its
     * capacity must fit everyone who already joined.
     * AI use: this and the three functions below were written with Claude
     * (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
     */
    fun validateUpdate(current: Lottery, updated: Lottery) {
        require(current.status == Lottery.STATUS_OPEN) {
            "Winners were already drawn, so this lottery can no longer change."
        }
        require(current.ownerId == updated.ownerId && current.campsiteId == updated.campsiteId) {
            "You can only update your own lottery."
        }
        validateCreation(updated.copy(campsiteName = current.campsiteName))
        require(updated.waitlistCapacity == null || updated.waitlistCapacity >= current.waitingListCount) {
            "${current.waitingListCount} entrants already joined, so capacity must be at least ${current.waitingListCount}."
        }
    }

    /**
     * Saves new [settings] for owner [ownerId]'s open [current] lottery (US 02.04.06).
     */
    suspend fun updateLottery(
        repository: LotteryCreationRepository,
        current: Lottery,
        ownerId: String,
        settings: Lottery,
    ) {
        require(ownerId.isNotBlank() && current.ownerId == ownerId) { "You can only update your own lottery." }
        val updated = current.copy(
            registrationStart = settings.registrationStart,
            registrationEnd = settings.registrationEnd,
            bookingStart = settings.bookingStart,
            bookingEnd = settings.bookingEnd,
            winnerCount = settings.winnerCount,
            waitlistCapacity = settings.waitlistCapacity,
            extraCampsiteIds = settings.extraCampsiteIds,
            extraCampsiteNames = settings.extraCampsiteNames,
            extraCampsiteOwnerIds = settings.extraCampsiteOwnerIds,
            shared = settings.shared,
        )
        validateUpdate(current, updated)
        repository.updateLottery(updated)
    }

    /**
     * Every campsite in [lottery] as (ID, name), main campsite first (US 02.04.02).
     */
    fun campsitesOf(lottery: Lottery): List<Pair<String, String>> =
        listOf(lottery.campsiteId to lottery.campsiteName) +
            lottery.extraCampsiteIds.zip(lottery.extraCampsiteNames)

    /**
     * Owner of [campsiteId] in [lottery]: the lottery's owner for its own
     * campsites, or the owner who added it to a shared lottery (US 02.04.08).
     * AI use: written with Claude (Anthropic), Nov 2026.
     */
    fun ownerOf(lottery: Lottery, campsiteId: String): String {
        val index = lottery.extraCampsiteIds.indexOf(campsiteId)
        return if (index < 0) lottery.ownerId else lottery.extraCampsiteOwnerIds.getOrNull(index) ?: lottery.ownerId
    }

    /** Everyone who has a campsite in [lottery]: its creator first. */
    fun ownersOf(lottery: Lottery): List<String> =
        (listOf(lottery.ownerId) + lottery.extraCampsiteIds.map { ownerOf(lottery, it) }).distinct()

    /**
     * True if campsite owner [ownerId] can add a campsite to [lottery] at
     * [now]: it is shared, not someone else's own lottery they are already
     * in, and registration has not ended (US 02.04.08).
     */
    fun canJoinShared(lottery: Lottery, ownerId: String, now: Long): Boolean =
        lottery.shared && lottery.status == Lottery.STATUS_OPEN && now < lottery.registrationEnd &&
            ownerId.isNotBlank() && ownerId !in ownersOf(lottery)

    /**
     * Returns [lottery] with [campsite] added by its owner [ownerId]
     * (US 02.04.08).
     *
     * @throws IllegalArgumentException with a message for the owner if it can't be added.
     */
    fun withSharedCampsite(lottery: Lottery, campsite: Campsite, ownerId: String, now: Long): Lottery {
        require(ownerId.isNotBlank() && campsite.ownerId == ownerId) { "You can only add your own campsite." }
        require(lottery.shared) { "This lottery is not open to other owners." }
        require(lottery.status == Lottery.STATUS_OPEN && now < lottery.registrationEnd) {
            "Registration for this lottery has ended."
        }
        require(campsitesOf(lottery).none { it.first == campsite.id }) { "This campsite is already in the lottery." }
        // Older lotteries have no owner list: their extras belong to the creator.
        val owners = lottery.extraCampsiteOwnerIds.ifEmpty { lottery.extraCampsiteIds.map { lottery.ownerId } }
        return lottery.copy(
            extraCampsiteIds = lottery.extraCampsiteIds + campsite.id,
            extraCampsiteNames = lottery.extraCampsiteNames + campsite.name,
            extraCampsiteOwnerIds = owners + ownerId,
        )
    }

    /**
     * Adds [campsite] to the shared [lottery] (US 02.04.08).
     *
     * @throws IllegalArgumentException with a message for the owner if it can't be added.
     */
    suspend fun joinSharedLottery(
        repository: LotteryCreationRepository,
        lottery: Lottery,
        campsite: Campsite,
        ownerId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        withSharedCampsite(lottery, campsite, ownerId, now)
        repository.addCampsiteToLottery(lottery.id, campsite, ownerId)
    }
    /**
     * Returns true if entrants can join or leave the waiting list of
     * [lottery] at [now]: it is not drawn and [now] is inside the
     * registration period.
     */
    fun isRegistrationOpen(lottery: Lottery, now: Long): Boolean =
        lottery.status == Lottery.STATUS_OPEN &&
                now in lottery.registrationStart until lottery.registrationEnd

    /** Returns true if the waiting list of [lottery] has room for one more entrant. */
    fun hasRoom(lottery: Lottery): Boolean =
        lottery.waitlistCapacity == null || lottery.waitingListCount < lottery.waitlistCapacity

    /**
     * Returns the name shown for [entry], falling back to a short device ID
     * when the entrant has no name.
     */
    fun entrantDisplayName(entry: WaitingListEntry): String =
        entry.entrantName.ifBlank { "Entrant ${entry.entrantId.take(6)}" }
}
