/*
 * LogsAndImagesTest.kt
 * Unit tests for the pure helpers behind the owner's sent notifications
 * (US 02.10.02), the admin notification and message logs (US 03.08.01,
 * 03.12.01) and the admin image browser (US 03.06.01).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.ui

import com.example.code.data.imageFromMap
import com.example.code.data.imageToMap
import com.example.code.data.notificationFromMap
import com.example.code.data.notificationToMap
import com.example.code.model.AppNotification
import com.example.code.model.Campsite
import com.example.code.model.Message
import com.example.code.model.StoredImage
import com.example.code.ui.admin.adminImageItems
import com.example.code.ui.admin.messageLog
import com.example.code.ui.admin.notificationLog
import com.example.code.ui.admin.searchLog
import com.example.code.ui.owner.groupSent
import com.example.code.ui.owner.notificationTypeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogsAndImagesTest {
    private fun waitlistMessage(id: String, to: String, at: Long = 2_000, text: String = "Draw is on Friday.") =
        AppNotification(
            id = id, recipientId = to, senderId = "owner-device-7f3a", lotteryId = "lottery-jasper-nov",
            campsiteName = "Jasper Riverside", type = AppNotification.TYPE_WAITLIST_MESSAGE,
            title = "Jasper Riverside", message = text, createdAt = at,
        )

    private val won = AppNotification(
        id = "n-won", recipientId = "device-priya-01", senderId = "owner-device-7f3a",
        type = AppNotification.TYPE_LOTTERY_WON, title = "You won: Jasper Riverside",
        message = "Open the campsite to choose your dates and reserve.", createdAt = 3_000,
    )

    @Test
    fun oneMessageToManyEntrantsIsShownOnceWithItsRecipients() {
        val sent = listOf(
            waitlistMessage("n1", "device-priya-01"),
            waitlistMessage("n2", "device-liam-02"),
            waitlistMessage("n3", "device-sofia-03"),
            won,
        )
        val groups = groupSent(sent)
        assertEquals(listOf(1, 3), groups.map { it.recipients })
        // Newest first.
        assertEquals("n-won", groups.first().sample.id)
    }

    @Test
    fun differentTextOrTimeIsADifferentMessage() {
        val sent = listOf(
            waitlistMessage("n1", "device-priya-01"),
            waitlistMessage("n2", "device-priya-01", at = 5_000),
            waitlistMessage("n3", "device-priya-01", text = "Bring bear spray."),
        )
        assertEquals(3, groupSent(sent).size)
    }

    @Test
    fun everyNotificationTypeHasALabel() {
        assertEquals("Waiting list message", notificationTypeLabel(AppNotification.TYPE_WAITLIST_MESSAGE))
        assertEquals("Lottery result: not selected", notificationTypeLabel(AppNotification.TYPE_LOTTERY_LOST))
        assertEquals("Direct message", notificationTypeLabel(AppNotification.TYPE_DIRECT_MESSAGE))
        assertEquals("Notification", notificationTypeLabel("SOMETHING_NEW"))
    }

    @Test
    fun notificationLogIsNewestFirstWithSenderAndRecipient() {
        val log = notificationLog(listOf(waitlistMessage("n1", "device-priya-01"), won)) { "t$it" }
        assertEquals(listOf("n-n-won", "n-n1"), log.map { it.key })
        assertEquals("Lottery result: selected · You won: Jasper Riverside", log.first().title)
        assertEquals("From owner-de to device-p · t3000", log.first().meta)
    }

    @Test
    fun messageLogShowsDirectionAndText() {
        val message = Message(
            id = "m1", reservationId = "r1", campsiteName = "Jasper Riverside", senderId = "owner-device-7f3a",
            senderName = "Jasper Camps", recipientId = "device-priya-01", fromOwner = true,
            text = "Gate code is 4521.", createdAt = 9_000,
        )
        val entry = messageLog(listOf(message)) { "t$it" }.single()
        assertEquals("Owner to guest · Jasper Riverside", entry.title)
        assertEquals("Gate code is 4521.", entry.body)
        assertEquals("From Jasper Camps (owner-de) to device-p · t9000", entry.meta)
        assertEquals(
            "Guest to owner · Jasper Riverside",
            messageLog(listOf(message.copy(fromOwner = false))) { "" }.single().title,
        )
    }

    @Test
    fun searchMatchesEveryWordIgnoringCase() {
        val log = notificationLog(listOf(waitlistMessage("n1", "device-priya-01"), won)) { "" }
        assertEquals(listOf("n-n1"), searchLog(log, "FRIDAY jasper").map { it.key })
        assertEquals(2, searchLog(log, "  ").size)
        assertTrue(searchLog(log, "banff").isEmpty())
    }

    @Test
    fun adminSeesUploadedImagesThenCampsiteLinks() {
        val uploaded = listOf(
            StoredImage(id = "old", uploaderId = "owner-device-7f3a", kind = StoredImage.KIND_CAMPSITE, createdAt = 1_000),
            StoredImage(id = "new", uploaderId = "device-priya-01", kind = StoredImage.KIND_PROFILE, label = "Priya Shah", createdAt = 2_000),
        )
        val campsites = listOf(
            Campsite(id = "c1", name = "Jasper Riverside", imageUrl = "img:old"),
            Campsite(id = "c2", name = "Banff Tunnel Mountain", imageUrl = "https://example.com/banff.jpg"),
            Campsite(id = "c3", name = "No Photo"),
        )
        val items = adminImageItems(uploaded, campsites) { "day$it" }
        assertEquals(listOf("stored-new", "stored-old", "link-c2"), items.map { it.key })
        assertEquals("Profile photo: Priya Shah", items[0].title)
        assertEquals("Uploaded by device-p on day2000", items[0].detail)
        assertEquals("Campsite photo: Jasper Riverside", items[1].title)
        assertEquals("old", items[1].storedId)
        assertEquals("https://example.com/banff.jpg", items[2].ref)
        assertEquals("c2", items[2].campsiteId)
        assertEquals("", items[2].storedId)
    }

    @Test
    fun imageReferencesRoundTrip() {
        assertEquals("img:abc", StoredImage.refFor("abc"))
        assertEquals("abc", StoredImage.idFromRef("img:abc"))
        assertNull(StoredImage.idFromRef("https://example.com/a.jpg"))
        assertNull(StoredImage.idFromRef(null))
    }

    @Test
    fun storedImageAndNotificationSurviveSavingAndLoading() {
        val image = StoredImage(
            id = "abc", uploaderId = "owner-device-7f3a", kind = StoredImage.KIND_CAMPSITE,
            data = "QUJD", label = "Jasper Riverside", createdAt = 1_000,
        )
        assertEquals(image, imageFromMap("abc", imageToMap(image)))
        val read = won.copy(reservationId = "r1", read = true)
        assertEquals(read, notificationFromMap(read.id, notificationToMap(read)))
    }
}
