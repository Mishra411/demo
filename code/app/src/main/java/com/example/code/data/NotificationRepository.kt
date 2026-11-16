/*
 * NotificationRepository.kt
 * Saves and loads in-app notifications in Cloud Firestore.
 * Notification delivery respects each user's notification preference.
 */

package com.example.code.data

import com.example.code.model.AppNotification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Access to persisted application notifications.
 */
interface NotificationRepository {

    /**
     * Saves notifications for recipients who have notifications enabled.
     */
    suspend fun sendAll(notifications: List<AppNotification>)

    /**
     * Observes notifications belonging to [recipientId].
     */
    fun notificationsFor(recipientId: String): Flow<List<AppNotification>>

    /** Observes notifications sent by [senderId], newest first (US 02.10.02). */
    fun sentBy(senderId: String): Flow<List<AppNotification>> = flowOf(emptyList())

    /** Observes every notification, newest first, for the admin log (US 03.08.01). */
    fun observeAll(): Flow<List<AppNotification>> = flowOf(emptyList())

    /** Marks notifications [ids] as read. */
    suspend fun markRead(ids: List<String>) {}
}

/**
 * [NotificationRepository] backed by Cloud Firestore.
 */
class FirestoreNotificationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : NotificationRepository {

    override suspend fun sendAll(notifications: List<AppNotification>) {
        if (notifications.isEmpty()) {
            return
        }

        val deliverableNotifications =
            notifications.filter { notification ->
                val user =
                    db.collection(USERS)
                        .document(notification.recipientId)
                        .get()
                        .awaitResult()

                shouldDeliverNotification(
                    user.getBoolean("notificationsEnabled"),
                )
            }

        if (deliverableNotifications.isEmpty()) {
            return
        }

        val collection = db.collection(NOTIFICATIONS)
        val batch = db.batch()

        deliverableNotifications.forEach { notification ->
            val ref = collection.document()

            batch.set(
                ref,
                notificationToMap(
                    notification.copy(id = ref.id),
                ),
            )
        }

        batch.commit().awaitResult()
    }

    override fun notificationsFor(
        recipientId: String,
    ): Flow<List<AppNotification>> =
        observe(db.collection(NOTIFICATIONS).whereEqualTo("recipientId", recipientId))

    // US 02.10.02: notifications this owner has sent.
    override fun sentBy(senderId: String): Flow<List<AppNotification>> =
        observe(db.collection(NOTIFICATIONS).whereEqualTo("senderId", senderId))

    // US 03.08.01: every notification, for the admin log.
    override fun observeAll(): Flow<List<AppNotification>> =
        observe(db.collection(NOTIFICATIONS))

    override suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = db.batch()
        ids.forEach { batch.update(db.collection(NOTIFICATIONS).document(it), "read", true) }
        batch.commit().awaitResult()
    }

    private fun observe(query: Query): Flow<List<AppNotification>> =
        callbackFlow {
            val registration =
                query.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }

                    trySend(
                        snapshot?.documents.orEmpty()
                            .map { document -> notificationFromMap(document.id, document.data.orEmpty()) }
                            .sortedByDescending { it.createdAt },
                    )
                }

            awaitClose {
                registration.remove()
            }
        }

    companion object {
        const val NOTIFICATIONS = "notifications"
        private const val USERS = "users"
    }
}

/**
 * Converts [notification] to the fields stored in Firestore.
 */
fun notificationToMap(
    notification: AppNotification,
): Map<String, Any> =
    mapOf(
        "id" to notification.id,
        "recipientId" to notification.recipientId,
        "senderId" to notification.senderId,
        "lotteryId" to notification.lotteryId,
        "campsiteName" to notification.campsiteName,
        "type" to notification.type,
        "title" to notification.title,
        "message" to notification.message,
        "createdAt" to notification.createdAt,
        "reservationId" to notification.reservationId,
        "read" to notification.read,
    )

/** Builds an [AppNotification] from Firestore document [data]. */
fun notificationFromMap(id: String, data: Map<String, Any?>): AppNotification =
    AppNotification(
        id = (data["id"] as? String)?.ifBlank { null } ?: id,
        recipientId = data["recipientId"] as? String ?: "",
        senderId = data["senderId"] as? String ?: "",
        lotteryId = data["lotteryId"] as? String ?: "",
        campsiteName = data["campsiteName"] as? String ?: "",
        type = data["type"] as? String ?: AppNotification.TYPE_WAITLIST_MESSAGE,
        title = data["title"] as? String ?: "",
        message = data["message"] as? String ?: "",
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
        reservationId = data["reservationId"] as? String ?: "",
        read = data["read"] as? Boolean ?: false,
    )

/**
 * Returns whether a notification should be delivered for the stored
 * preference (US 01.10.01). Missing preferences default to enabled.
 */
fun shouldDeliverNotification(
    notificationsEnabled: Boolean?,
): Boolean {
    return notificationsEnabled != false
}