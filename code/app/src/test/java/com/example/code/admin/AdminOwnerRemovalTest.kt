/*
 * AdminOwnerRemovalTest.kt
 * Unit tests for removing a campsite owner who violates policy
 * (US 03.07.01) and for recognising administrators by their profile role.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed by Aalok Mishra.
 */

package com.example.code.admin

import com.example.code.model.Campsite
import com.example.code.ui.admin.ownerRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminOwnerRemovalTest {
    private class FakeProfiles : AdminProfileRepository {
        val removedOwners = mutableListOf<String>()
        var fail = false
        override fun observeProfiles(): Flow<List<AdminProfile>> = flowOf(emptyList())
        override suspend fun removeProfile(profileId: String): Boolean = false
        override suspend fun removeOwner(ownerId: String): Int {
            if (fail) error("offline")
            removedOwners += ownerId
            return 2
        }
    }

    private val repository = FakeProfiles()

    @Test
    fun adminRemovesAnOwner() = runBlocking {
        val controller = AdminProfileController(repository, { true }, "admin-device")
        assertEquals(AdminRemovalResult.REMOVED, controller.removeOwner("owner-device-7f3a"))
        assertEquals(listOf("owner-device-7f3a"), repository.removedOwners)
    }

    @Test
    fun nonAdminCannotRemoveAnOwner() = runBlocking {
        val controller = AdminProfileController(repository, { false }, "device-priya-01")
        assertEquals(AdminRemovalResult.DENIED, controller.removeOwner("owner-device-7f3a"))
        assertTrue(repository.removedOwners.isEmpty())
    }

    @Test
    fun adminCannotRemoveThemselvesOrAnInvalidId() = runBlocking {
        val controller = AdminProfileController(repository, { true }, "admin-device")
        assertEquals(AdminRemovalResult.INVALID, controller.removeOwner("admin-device"))
        assertEquals(AdminRemovalResult.INVALID, controller.removeOwner(""))
        assertEquals(AdminRemovalResult.INVALID, controller.removeOwner("users/other"))
        assertTrue(repository.removedOwners.isEmpty())
    }

    @Test
    fun storageFailureIsReported() = runBlocking {
        repository.fail = true
        val controller = AdminProfileController(repository, { true }, "admin-device")
        assertEquals(AdminRemovalResult.FAILED, controller.removeOwner("owner-device-7f3a"))
    }

    @Test
    fun adminRoleOnTheProfileGrantsAccess() = runBlocking {
        val byRole = ProfileRoleAdminAuthorization("admin-device", claim = { false }) { listOf("ENTRANT", "ADMIN") }
        val entrant = ProfileRoleAdminAuthorization("device-priya-01", claim = { false }) { listOf("ENTRANT") }
        val byClaim = ProfileRoleAdminAuthorization("device-x", claim = { true }) { emptyList() }
        val brokenClaim = ProfileRoleAdminAuthorization("admin-device", claim = { error("no token") }) { listOf("ADMIN") }
        val noDevice = ProfileRoleAdminAuthorization("", claim = { false }) { listOf("ADMIN") }
        assertTrue(byRole.isAdmin())
        assertFalse(entrant.isAdmin())
        assertTrue(byClaim.isAdmin())
        assertTrue(brokenClaim.isAdmin())
        assertFalse(noDevice.isAdmin())
    }

    @Test
    fun ownerListHasOwnersByRoleAndByCampsite() {
        val profiles = listOf(
            AdminProfile("device-priya-01", "Priya Shah", roles = setOf("ENTRANT")),
            AdminProfile("owner-device-7f3a", "Jasper Camps", roles = setOf("ENTRANT", "CAMPSITE_OWNER")),
            AdminProfile("device-new-host", "Avery Chen", roles = setOf("CAMPSITE_OWNER")),
        )
        val campsites = listOf(
            Campsite(id = "c1", ownerId = "owner-device-7f3a", name = "Jasper Riverside"),
            Campsite(id = "c2", ownerId = "owner-device-7f3a", name = "Banff Tunnel Mountain"),
            Campsite(id = "c3", ownerId = "deleted-owner", name = "Orphan Creek"),
            Campsite(id = "c4", ownerId = "", name = "Legacy Site"),
        )
        val rows = ownerRows(profiles, campsites)
        assertEquals(listOf("device-new-host", "deleted-owner", "owner-device-7f3a"), rows.map { it.profile.id })
        assertEquals(emptyList<String>(), rows[0].campsiteNames)
        assertEquals(listOf("Orphan Creek"), rows[1].campsiteNames)
        assertEquals(listOf("Banff Tunnel Mountain", "Jasper Riverside"), rows[2].campsiteNames)
    }
}
