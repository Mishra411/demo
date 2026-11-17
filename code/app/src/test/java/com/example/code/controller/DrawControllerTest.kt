/*
 * DrawControllerTest.kt
 * Unit tests for DrawController (US 02.05.01): drawing when registration
 * ends, marking winners, notifying them, and drawing only once.
 * Known issues: none.
 */

package com.example.code.controller

import com.example.code.data.LotteryRepository
import com.example.code.data.NotificationRepository
import com.example.code.fakes.FakeLotteryRepository
import com.example.code.fakes.FakeNotificationRepository
import com.example.code.fakes.TestData
import com.example.code.model.AppNotification
import com.example.code.model.Lottery
import com.example.code.model.WaitingListEntry
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DrawControllerTest {

    private val lotteries = FakeLotteryRepository(listOf(TestData.jasperLottery))
    private val notifications = FakeNotificationRepository()
    private var now = TestData.AFTER_REGISTRATION
    private val controller = DrawController(lotteries, notifications, { now }, Random(2026))

    private val lotteryId = TestData.jasperLottery.id

    @Before
    fun fillWaitingList() {
        lotteries.waitingLists.value = mapOf(
            lotteryId to listOf(TestData.priya, TestData.liam, TestData.sofia, TestData.noah),
        )
    }

    private fun lottery(): Lottery = lotteries.lotteries.value.getValue(lotteryId)

    @Test
    fun drawsTwoWinnersAfterRegistrationEnds() = runBlocking {
        val result = controller.drawIfDue(lottery())

        assertNotNull(result)
        assertEquals(2, result!!.winners.size)
        assertEquals(Lottery.STATUS_DRAWN, lottery().status)
        val statuses = lotteries.waitingLists.value.getValue(lotteryId).groupingBy { it.status }.eachCount()
        assertEquals(2, statuses[WaitingListEntry.STATUS_WON])
        assertEquals(2, statuses[WaitingListEntry.STATUS_NOT_SELECTED])
    }

    // US 01.03.02: entrants who were not drawn are told too.
    @Test
    fun notSelectedEntrantsAreNotified() = runBlocking {
        val result = controller.drawIfDue(lottery())!!

        val lost = notifications.sent.filter { it.type == AppNotification.TYPE_LOTTERY_LOST }
        assertEquals(result.notSelected.map { it.entrantId }.toSet(), lost.map { it.recipientId }.toSet())
        lost.forEach {
            assertEquals("Not selected for Jasper Riverside", it.title)
            assertEquals(lotteryId, it.lotteryId)
        }
    }

    @Test
    fun winnersAreNotified() = runBlocking {
        val result = controller.drawIfDue(lottery())!!

        val won = notifications.sent.filter { it.type == AppNotification.TYPE_LOTTERY_WON }
        assertEquals(result.winners.map { it.entrantId }, won.map { it.recipientId })
        won.forEach {
            assertEquals("You won Jasper Riverside!", it.title)
            assertEquals(lotteryId, it.lotteryId)
            assertEquals(now, it.createdAt)
        }
    }

    @Test
    fun nothingHappensDuringRegistration() = runBlocking {
        now = TestData.DURING_REGISTRATION

        assertNull(controller.drawIfDue(lottery()))
        assertEquals(Lottery.STATUS_OPEN, lottery().status)
        assertTrue(notifications.sent.isEmpty())
    }

    @Test
    fun lotteryIsDrawnOnlyOnce() = runBlocking {
        val open = lottery()
        controller.drawIfDue(open)
        val sentAfterFirst = notifications.sent.size

        // A second device still holding the OPEN copy tries again.
        assertNull(controller.drawIfDue(open))
        assertEquals(sentAfterFirst, notifications.sent.size)
    }

    @Test
    fun drawAllDueSkipsLotteriesStillOpen() = runBlocking {
        val later = TestData.banffLottery.copy(registrationEnd = TestData.AFTER_REGISTRATION + 86_400_000)
        lotteries.lotteries.value = lotteries.lotteries.value + (later.id to later)

        assertEquals(1, controller.drawAllDue(lotteries.lotteries.value.values.toList()))
        assertEquals(Lottery.STATUS_OPEN, lotteries.lotteries.value.getValue(later.id).status)
    }

    // Saving the draw refreshes the lottery list, which restarts the screen's
    // draw loop. The win and lose notifications must still be sent.
    // AI use: test written with Claude (Anthropic), Nov 2026.
    @Test
    fun notificationsAreSentEvenIfTheCallerIsCancelledDuringTheDraw() = runBlocking {
        lateinit var drawJob: Job
        val cancellingLotteries = object : LotteryRepository by lotteries {
            override suspend fun completeDraw(
                lotteryId: String,
                winnerIds: List<String>,
                notSelectedIds: List<String>,
                drawnAt: Long,
            ): Boolean {
                val saved = lotteries.completeDraw(lotteryId, winnerIds, notSelectedIds, drawnAt)
                drawJob.cancel()
                return saved
            }
        }
        val slowNotifications = object : NotificationRepository by notifications {
            override suspend fun sendAll(notifications: List<AppNotification>) {
                yield()
                this@DrawControllerTest.notifications.sendAll(notifications)
            }
        }
        val drawing = DrawController(cancellingLotteries, slowNotifications, { now }, Random(2026))

        drawJob = launch { drawing.drawIfDue(lottery()) }
        drawJob.join()

        assertEquals(Lottery.STATUS_DRAWN, lottery().status)
        assertEquals(4, notifications.sent.size)
    }
}
