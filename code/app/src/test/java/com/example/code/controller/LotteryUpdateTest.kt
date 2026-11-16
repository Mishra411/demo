/*
 * LotteryUpdateTest.kt
 * Unit tests for updating an open lottery (US 02.04.06) and for one
 * lottery covering several campsites (US 02.04.02).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.data.LotteryCreationRepository
import com.example.code.data.lotteryFromMap
import com.example.code.data.lotteryUpdateToMap
import com.example.code.data.newLotteryToMap
import com.example.code.fakes.TestData
import com.example.code.model.Campsite
import com.example.code.model.Lottery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryUpdateTest {
    private class FakeCreation : LotteryCreationRepository {
        var created: Lottery? = null
        var updated: Lottery? = null
        override suspend fun createLottery(lottery: Lottery): String {
            created = lottery
            return "lottery-new"
        }
        override suspend fun updateLottery(lottery: Lottery) {
            updated = lottery
        }
    }

    private val open = TestData.jasperLottery.copy(waitingListCount = 3)
    private val repository = FakeCreation()

    @Test
    fun ownerChangesDatesWinnersAndCapacity() = runBlocking {
        val settings = open.copy(
            registrationEnd = open.registrationEnd + 3_600_000,
            winnerCount = 3,
            waitlistCapacity = 10,
        )
        LotteryController.updateLottery(repository, open, TestData.OWNER_ID, settings)
        val saved = repository.updated!!
        assertEquals(open.registrationEnd + 3_600_000, saved.registrationEnd)
        assertEquals(3, saved.winnerCount)
        assertEquals(10, saved.waitlistCapacity)
        // Identity and the entrants who already joined never change.
        assertEquals(open.id, saved.id)
        assertEquals(open.campsiteId, saved.campsiteId)
        assertEquals(3, saved.waitingListCount)
    }

    @Test
    fun updateCannotRenameTheCampsiteOrMoveTheLottery() = runBlocking {
        val settings = open.copy(campsiteId = "camp-other", campsiteName = "Other", ownerId = "someone-else", id = "x")
        LotteryController.updateLottery(repository, open, TestData.OWNER_ID, settings)
        assertEquals(open.copy(), repository.updated)
    }

    @Test
    fun onlyTheOwnerCanUpdate() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { LotteryController.updateLottery(repository, open, "device-priya-01", open) }
        }
        assertNull(repository.updated)
    }

    @Test
    fun drawnLotteryCannotChange() {
        val drawn = open.copy(status = Lottery.STATUS_DRAWN)
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { LotteryController.updateLottery(repository, drawn, TestData.OWNER_ID, drawn) }
        }
        assertEquals("Winners were already drawn, so this lottery can no longer change.", error.message)
    }

    @Test
    fun capacityCannotDropBelowEntrantsAlreadyJoined() {
        // Three entrants joined; two winners.
        assertThrows(IllegalArgumentException::class.java) {
            LotteryController.validateUpdate(open, open.copy(waitlistCapacity = 2))
        }
        LotteryController.validateUpdate(open, open.copy(waitlistCapacity = 3))
        LotteryController.validateUpdate(open, open.copy(waitlistCapacity = null))
    }

    @Test
    fun updatedDatesMustStillBeValid() {
        assertThrows(IllegalArgumentException::class.java) {
            LotteryController.validateUpdate(open, open.copy(bookingStart = open.registrationEnd - 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LotteryController.validateUpdate(open, open.copy(winnerCount = 0))
        }
    }

    @Test
    fun lotteryListsItsMainCampsiteThenTheExtras() {
        val multi = open.copy(
            extraCampsiteIds = listOf("camp-banff-tunnel", "camp-elk-island"),
            extraCampsiteNames = listOf("Banff Tunnel Mountain", "Elk Island Astotin"),
        )
        assertEquals(
            listOf(
                "camp-jasper-riverside" to "Jasper Riverside",
                "camp-banff-tunnel" to "Banff Tunnel Mountain",
                "camp-elk-island" to "Elk Island Astotin",
            ),
            LotteryController.campsitesOf(multi),
        )
        assertEquals(listOf("camp-jasper-riverside" to "Jasper Riverside"), LotteryController.campsitesOf(open))
    }

    @Test
    fun extraCampsitesMustBeDistinctAndNamed() {
        fun rejected(ids: List<String>, names: List<String>) {
            assertThrows(IllegalArgumentException::class.java) {
                LotteryController.validateCreation(open.copy(extraCampsiteIds = ids, extraCampsiteNames = names))
            }
        }
        rejected(listOf("camp-jasper-riverside"), listOf("Jasper Riverside"))
        rejected(listOf("camp-banff-tunnel", "camp-banff-tunnel"), listOf("Banff", "Banff"))
        rejected(listOf("camp-banff-tunnel"), emptyList())
    }

    @Test
    fun publishingKeepsTheExtraCampsites() = runBlocking {
        val campsite = Campsite(id = "camp-jasper-riverside", ownerId = TestData.OWNER_ID, name = "Jasper Riverside")
        val settings = open.copy(
            extraCampsiteIds = listOf("camp-banff-tunnel"),
            extraCampsiteNames = listOf("Banff Tunnel Mountain"),
        )
        LotteryController.publishLottery(repository, campsite, TestData.OWNER_ID, settings)
        assertEquals(listOf("camp-banff-tunnel"), repository.created!!.extraCampsiteIds)
    }

    @Test
    fun multiCampsiteLotteryShowsOnEachOfItsCampsites() {
        val multi = open.copy(extraCampsiteIds = listOf("camp-banff-tunnel"), extraCampsiteNames = listOf("Banff Tunnel Mountain"))
        assertEquals(multi, WaitingListController.lotteryForCampsite(listOf(multi), "camp-banff-tunnel"))
        assertEquals(multi, WaitingListController.lotteryForCampsite(listOf(multi), "camp-jasper-riverside"))
        assertNull(WaitingListController.lotteryForCampsite(listOf(multi), "camp-elk-island"))
    }

    @Test
    fun extraCampsitesSurviveSavingAndLoading() {
        val multi = open.copy(extraCampsiteIds = listOf("camp-banff-tunnel"), extraCampsiteNames = listOf("Banff Tunnel Mountain"))
        val loaded = lotteryFromMap(multi.id, newLotteryToMap(multi))
        assertEquals(listOf("camp-banff-tunnel"), loaded.extraCampsiteIds)
        assertEquals(listOf("Banff Tunnel Mountain"), loaded.extraCampsiteNames)
        // A single-campsite lottery is stored without the extra fields.
        assertFalse(newLotteryToMap(open).containsKey("extraCampsiteIds"))
        assertTrue(lotteryFromMap(open.id, newLotteryToMap(open)).extraCampsiteIds.isEmpty())
    }

    @Test
    fun updateWritesOnlyTheEditableSettings() {
        val map = lotteryUpdateToMap(open.copy(waitlistCapacity = null))
        assertEquals(open.registrationEnd, map["registrationEnd"])
        assertEquals(open.winnerCount, (map["winnerCount"] as Number).toInt())
        assertTrue(map.containsKey("waitlistCapacity"))
        assertNull(map["waitlistCapacity"])
        assertFalse(map.containsKey("ownerId"))
        assertFalse(map.containsKey("waitingListCount"))
        assertFalse(map.containsKey("status"))
    }
}
