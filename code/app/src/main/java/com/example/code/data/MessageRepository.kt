/*
 * MessageRepository.kt
 * Saves and loads messages between a campsite owner and the entrant who
 * reserved (Firestore: messages/{id}). US 02.12.01, US 03.12.01 and the
 * messaging system WOW feature.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.data

import com.example.code.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Access to reservation messages. */
interface MessageRepository {
    /** Saves [message] and returns its new ID. */
    suspend fun send(message: Message): String

    /** Messages about [reservationId], oldest first. */
    fun conversation(reservationId: String): Flow<List<Message>>

    /** Every message, newest first, for the admin log (US 03.12.01). */
    fun observeAll(): Flow<List<Message>>
}

/** [MessageRepository] backed by Cloud Firestore. */
class FirestoreMessageRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : MessageRepository {

    private val messages = db.collection(MESSAGES)

    override suspend fun send(message: Message): String {
        val ref = messages.document()
        ref.set(messageToMap(message)).awaitResult()
        return ref.id
    }

    override fun conversation(reservationId: String): Flow<List<Message>> =
        observe(messages.whereEqualTo("reservationId", reservationId)) { list -> list.sortedBy { it.createdAt } }

    override fun observeAll(): Flow<List<Message>> =
        observe(messages) { list -> list.sortedByDescending { it.createdAt } }

    private fun observe(query: Query, order: (List<Message>) -> List<Message>): Flow<List<Message>> =
        callbackFlow {
            val registration = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(order(snapshot?.documents.orEmpty().map { messageFromMap(it.id, it.data.orEmpty()) }))
            }
            awaitClose { registration.remove() }
        }

    companion object {
        /** Name of the messages collection. */
        const val MESSAGES = "messages"
    }
}

/** Converts [message] to the fields stored in Firestore. */
fun messageToMap(message: Message): Map<String, Any> = mapOf(
    "reservationId" to message.reservationId,
    "campsiteName" to message.campsiteName,
    "senderId" to message.senderId,
    "senderName" to message.senderName,
    "recipientId" to message.recipientId,
    "fromOwner" to message.fromOwner,
    "text" to message.text,
    "createdAt" to message.createdAt,
)

/** Builds a [Message] from Firestore document [data]. */
fun messageFromMap(id: String, data: Map<String, Any?>): Message = Message(
    id = id,
    reservationId = data["reservationId"] as? String ?: "",
    campsiteName = data["campsiteName"] as? String ?: "",
    senderId = data["senderId"] as? String ?: "",
    senderName = data["senderName"] as? String ?: "",
    recipientId = data["recipientId"] as? String ?: "",
    fromOwner = data["fromOwner"] as? Boolean ?: false,
    text = data["text"] as? String ?: "",
    createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
)
