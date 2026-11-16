/*
 * UserController.kt
 * Loads the device-based user profile and manages editable profile information.
 * Supports device identification and profile updates.
 */

package com.example.code.controller

import com.example.code.User
import com.example.code.UserRole
import com.example.code.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls loading and updating the user profile associated with the current device.
 *
 * @property repository data source used to load, create, and update users.
 */
class UserController(
    private val repository: UserRepository,
) {

    private val _currentUser = MutableStateFlow<User?>(null)

    /** The currently loaded user, or null before loading succeeds. */
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    /** Whether a user profile is currently being loaded or saved. */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    /** Most recent user-loading or profile-saving error. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Loads the profile associated with [deviceId].
     *
     * If no existing profile is found, a new default Entrant profile is
     * created for the device.
     */
    suspend fun loadOrCreateUser(deviceId: String) {
        if (deviceId.isBlank()) {
            _errorMessage.value = "Device identity is unavailable."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        try {
            val existingUser = repository.getUser(deviceId)

            _currentUser.value =
                existingUser ?: repository.createUser(deviceId)
        } catch (exception: Exception) {
            _errorMessage.value =
                "Failed to load user profile: ${exception.message}"
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Updates the current user's name, email, and phone number.
     *
     * @return true when the profile is valid and saved successfully,
     * false otherwise.
     */
    suspend fun updateProfile(
        name: String,
        email: String,
        phoneNumber: String,
    ): Boolean {
        val currentUser = _currentUser.value ?: return false

        if (!isValidEmail(email)) {
            _errorMessage.value = "Please enter a valid email address."
            return false
        }

        _isLoading.value = true
        _errorMessage.value = null

        return try {
            val updatedUser =
                User(
                    deviceId = currentUser.deviceId,
                    name = name.trim(),
                    email = email.trim(),
                    phoneNumber = phoneNumber.trim(),
                    profilePhotoUrl = currentUser.profilePhotoUrl,
                    notificationsEnabled = currentUser.notificationsEnabled,
                    roles = currentUser.roles,
                    preferredTags = currentUser.preferredTags,
                )

            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser

            true
        } catch (exception: Exception) {
            _errorMessage.value =
                "Failed to save profile: ${exception.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Turns notifications on or off for the current user (US 01.10.01).
     *
     * @return true when saved.
     */
    suspend fun setNotificationsEnabled(enabled: Boolean): Boolean =
        save { it.updateNotificationPreference(enabled) }

    /**
     * Adds or removes the campsite owner role, so an entrant or admin can
     * also host campsites (US 03.11.01). Other roles are never changed here.
     *
     * @return true when saved.
     */
    suspend fun setHosting(enabled: Boolean): Boolean = setRole(UserRole.CAMPSITE_OWNER, enabled)

    /**
     * Adds or removes the entrant role, so an administrator can also browse
     * and enter lotteries with the same profile (US 03.11.01).
     *
     * @return true when saved.
     */
    suspend fun setEntering(enabled: Boolean): Boolean = setRole(UserRole.ENTRANT, enabled)

    // Adds or removes one role; a profile always keeps at least one role.
    private suspend fun setRole(role: UserRole, enabled: Boolean): Boolean {
        val user = _currentUser.value ?: return false
        val roles = if (enabled) user.roles + role else user.roles - role
        return save(roles.ifEmpty { setOf(UserRole.ENTRANT) }) {}
    }

    /** Sets the profile photo reference of the current user, or null to remove it. */
    suspend fun setProfilePhoto(ref: String?): Boolean =
        save { it.profilePhotoUrl = ref }

    /** Saves the campsite tags the user likes, used for the featured carousel. */
    suspend fun setPreferredTags(tags: Set<String>): Boolean =
        save { it.preferredTags = tags.map { tag -> tag.trim().lowercase() }.filter { tag -> tag.isNotEmpty() }.toSet() }

    /**
     * Deletes the current user's profile (US 01.08.01) and starts a fresh,
     * empty entrant profile for this device.
     *
     * @return true when deleted.
     */
    suspend fun deleteProfile(): Boolean {
        val user = _currentUser.value ?: return false
        _isLoading.value = true
        _errorMessage.value = null
        return try {
            repository.deleteUser(user.deviceId)
            _currentUser.value = repository.createUser(user.deviceId)
            true
        } catch (exception: Exception) {
            _errorMessage.value = "Failed to delete profile: ${exception.message}"
            false
        } finally {
            _isLoading.value = false
        }
    }

    // Saves a changed copy of the current user. AI use: written with Claude (Anthropic), Nov 2026.
    private suspend fun save(roles: Set<UserRole>? = null, change: (User) -> Unit): Boolean {
        val user = _currentUser.value ?: return false
        val updated = User(
            deviceId = user.deviceId,
            name = user.name,
            email = user.email,
            phoneNumber = user.phoneNumber,
            profilePhotoUrl = user.profilePhotoUrl,
            notificationsEnabled = user.notificationsEnabled,
            roles = roles ?: user.roles,
            preferredTags = user.preferredTags,
        ).also(change)
        _errorMessage.value = null
        return try {
            repository.updateUser(updated)
            _currentUser.value = updated
            true
        } catch (exception: Exception) {
            _errorMessage.value = "Failed to save profile: ${exception.message}"
            false
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val trimmedEmail = email.trim()

        return trimmedEmail.contains("@") &&
                trimmedEmail.substringAfter("@").contains(".")
    }
}