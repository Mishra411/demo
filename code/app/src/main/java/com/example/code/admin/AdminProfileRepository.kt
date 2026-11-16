/*
 * AdminProfileRepository.kt
 * Lists stored user profiles and removes a selected profile document after confirmation.
 * Known issues: production access requires the reviewed Firestore admin rules; this code deploys no rules.
 */
package com.example.code.admin

import com.example.code.data.awaitResult
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Minimal administrative view, compatible with users/{deviceId} without changing User.
 * @property id authoritative Firestore document ID, not a client-supplied field.
 * @property name display name, possibly blank for a newly recognized device.
 * @property email contact address, if provided.
 * @property phone phone number, if provided.
 * @property roles role names stored on the profile, e.g. "CAMPSITE_OWNER".
 */
data class AdminProfile(
    val id: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val roles: Set<String> = emptySet(),
)

/** Persistence boundary for profile removal; acceptance tests use an in-memory implementation. */
interface AdminProfileRepository {
    /** Emits profiles after changes; failures terminate the stream so the UI can offer retry. */
    fun observeProfiles(): Flow<List<AdminProfile>>
    /** Removes only [profileId]'s profile document; returns false if already absent. */
    suspend fun removeProfile(profileId: String): Boolean

    /**
     * Removes campsite owner [ownerId] for violating app policy (US 03.07.01):
     * their campsites, their open lotteries and their profile.
     * AI use: written with Claude (Anthropic), Nov 2026.
     *
     * @return number of campsites removed.
     */
    suspend fun removeOwner(ownerId: String): Int {
        throw UnsupportedOperationException("Removing owners is not supported here.")
    }
}

/** Firestore implementation; server rules must enforce the administrator custom claim. */
class FirestoreAdminProfileRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AdminProfileRepository {
    override fun observeProfiles(): Flow<List<AdminProfile>> = callbackFlow {
        val listener = db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val rows = snapshot?.documents.orEmpty().mapNotNull { doc ->
                val fields = doc.data ?: return@mapNotNull null
                if (fields["isActive"] == false || fields["deletedAt"] != null) return@mapNotNull null
                AdminProfile(
                    doc.id,
                    fields["name"] as? String ?: "",
                    fields["email"] as? String ?: "",
                    fields["phoneNumber"] as? String ?: "",
                    (fields["roles"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.id } })
            trySend(rows)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun removeOwner(ownerId: String): Int {
        val batch = db.batch()
        val campsites = db.collection("campsites").whereEqualTo("ownerId", ownerId).get().awaitResult().documents
        campsites.forEach { batch.delete(it.reference) }
        db.collection("lotteries").whereEqualTo("ownerId", ownerId).get().awaitResult().documents
            .filter { it.getString("status") != "DRAWN" }
            .forEach { batch.delete(it.reference) }
        batch.delete(db.collection("users").document(ownerId))
        batch.commit().awaitResult()
        return campsites.size
    }

    override suspend fun removeProfile(profileId: String): Boolean {
        val target = db.collection("users").document(profileId)
        return db.runTransaction { transaction ->
            if (!transaction.get(target).exists()) false
            else { transaction.delete(target); true }
        }.awaitResult()
    }
}
