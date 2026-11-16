/*
 * UserSettingsTest.kt
 * Unit tests for profile settings: notification opt-out (US 01.10.01),
 * hosting campsites, profile photo, camping interests and deleting the
 * profile (US 01.08.01).
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.controller

import com.example.code.User
import com.example.code.UserRole
import com.example.code.data.UserRepository
import com.example.code.data.shouldDeliverNotification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {
    private class FakeUsers(var stored: User?) : UserRepository {
        var deleted = mutableListOf<String>()
        var failUpdates = false
        override suspend fun getUser(deviceId: String): User? = stored
        override suspend fun createUser(deviceId: String): User =
            User(deviceId = deviceId, roles = setOf(UserRole.ENTRANT)).also { stored = it }
        override suspend fun updateUser(user: User) {
            if (failUpdates) error("offline")
            stored = user
        }
        override suspend fun deleteUser(deviceId: String) {
            deleted += deviceId
            stored = null
        }
    }

    private val priya = User(
        deviceId = "device-priya-01",
        name = "Priya Shah",
        email = "priya@example.com",
        phoneNumber = "7805550101",
        roles = setOf(UserRole.ENTRANT),
    )
    private val repository = FakeUsers(priya)
    private val controller = UserController(repository)

    private fun loaded(): UserController = runBlocking {
        controller.loadOrCreateUser(priya.deviceId)
        controller
    }

    @Test
    fun entrantOptsOutOfNotifications() = runBlocking {
        assertTrue(loaded().setNotificationsEnabled(false))
        assertFalse(controller.currentUser.value!!.notificationsEnabled)
        assertFalse(repository.stored!!.notificationsEnabled)
        // The rest of the profile is untouched.
        assertEquals("Priya Shah", repository.stored!!.name)
        assertEquals("priya@example.com", repository.stored!!.email)
    }

    @Test
    fun optedOutEntrantsGetNoNotifications() {
        assertTrue(shouldDeliverNotification(true))
        assertFalse(shouldDeliverNotification(false))
    }

    @Test
    fun hostingAddsAndRemovesTheOwnerRole() = runBlocking {
        assertTrue(loaded().setHosting(true))
        assertEquals(setOf(UserRole.ENTRANT, UserRole.CAMPSITE_OWNER), repository.stored!!.roles)
        assertTrue(controller.setHosting(false))
        assertEquals(setOf(UserRole.ENTRANT), repository.stored!!.roles)
    }

    @Test
    fun hostingNeverRemovesTheAdminRole() = runBlocking {
        repository.stored = User(deviceId = "device-admin", roles = setOf(UserRole.ADMIN, UserRole.CAMPSITE_OWNER))
        controller.loadOrCreateUser("device-admin")
        controller.setHosting(false)
        assertEquals(setOf(UserRole.ADMIN), repository.stored!!.roles)
    }

    // US 03.11.01
    @Test
    fun adminCanAlsoBeAnEntrantAndAnOwner() = runBlocking {
        repository.stored = User(deviceId = "device-admin", roles = setOf(UserRole.ADMIN))
        controller.loadOrCreateUser("device-admin")
        assertTrue(controller.setEntering(true))
        assertTrue(controller.setHosting(true))
        assertEquals(setOf(UserRole.ADMIN, UserRole.ENTRANT, UserRole.CAMPSITE_OWNER), repository.stored!!.roles)
        controller.setEntering(false)
        assertEquals(setOf(UserRole.ADMIN, UserRole.CAMPSITE_OWNER), repository.stored!!.roles)
    }

    @Test
    fun aProfileAlwaysKeepsOneRole() = runBlocking {
        loaded().setEntering(false)
        assertEquals(setOf(UserRole.ENTRANT), repository.stored!!.roles)
    }

    @Test
    fun profilePhotoIsSavedAndRemoved() = runBlocking {
        assertTrue(loaded().setProfilePhoto("img:photo-1"))
        assertEquals("img:photo-1", repository.stored!!.profilePhotoUrl)
        controller.setProfilePhoto(null)
        assertNull(repository.stored!!.profilePhotoUrl)
    }

    @Test
    fun interestsAreSavedLowerCaseWithoutBlanks() = runBlocking {
        assertTrue(loaded().setPreferredTags(setOf(" River ", "HIKING", " ")))
        assertEquals(setOf("river", "hiking"), repository.stored!!.preferredTags)
    }

    @Test
    fun failedSaveKeepsTheOldSettingsAndReportsTheError() = runBlocking {
        loaded()
        repository.failUpdates = true
        assertFalse(controller.setNotificationsEnabled(false))
        assertTrue(controller.currentUser.value!!.notificationsEnabled)
        assertNotNull(controller.errorMessage.value)
    }

    @Test
    fun deletingTheProfileRemovesItAndStartsAFreshOne() = runBlocking {
        loaded().setHosting(true)
        assertTrue(controller.deleteProfile())
        assertEquals(listOf("device-priya-01"), repository.deleted)
        val fresh = controller.currentUser.value!!
        assertEquals("device-priya-01", fresh.deviceId)
        assertEquals("", fresh.name)
        assertEquals("", fresh.email)
        assertEquals(setOf(UserRole.ENTRANT), fresh.roles)
    }

    @Test
    fun settingsNeedALoadedProfile() = runBlocking {
        assertFalse(controller.setNotificationsEnabled(false))
        assertFalse(controller.deleteProfile())
    }
}
