/*
 * ImageRepository.kt
 * Uploads, lists and removes images (Firestore: images/{id}). Pictures are
 * compressed JPEGs stored as Base64 text, so the app needs no extra storage
 * service. Used by image upload, US 03.06.01 and US 03.03.01.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.data

import com.example.code.model.StoredImage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Access to uploaded images. */
interface ImageRepository {
    /** Saves [image] and returns its new ID. */
    suspend fun upload(image: StoredImage): String

    /** Loads the Base64 data of image [id], or null if it was removed. */
    suspend fun load(id: String): String?

    /** Every uploaded image, newest first (US 03.06.01). */
    fun observeAll(): Flow<List<StoredImage>>

    /**
     * Removes uploaded image [id] and clears every campsite and profile that
     * shows it (US 03.03.01). For a campsite image that is only a web link,
     * pass a blank [id] and the [campsiteId] to clear.
     */
    suspend fun remove(id: String, campsiteId: String? = null)
}

/** [ImageRepository] backed by Cloud Firestore. */
class FirestoreImageRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ImageRepository {

    private val images = db.collection(IMAGES)

    override suspend fun upload(image: StoredImage): String {
        val ref = images.document()
        ref.set(imageToMap(image)).awaitResult()
        return ref.id
    }

    override suspend fun load(id: String): String? =
        images.document(id).get().awaitResult().getString("data")

    override fun observeAll(): Flow<List<StoredImage>> = callbackFlow {
        val registration = images.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(
                snapshot?.documents.orEmpty()
                    .map { imageFromMap(it.id, it.data.orEmpty()) }
                    .sortedByDescending { it.createdAt },
            )
        }
        awaitClose { registration.remove() }
    }

    override suspend fun remove(id: String, campsiteId: String?) {
        val batch = db.batch()
        if (id.isNotBlank()) {
            val ref = StoredImage.refFor(id)
            db.collection("campsites").whereEqualTo("imageUrl", ref).get().awaitResult()
                .documents.forEach { batch.update(it.reference, "imageUrl", FieldValue.delete()) }
            db.collection("users").whereEqualTo("profilePhotoUrl", ref).get().awaitResult()
                .documents.forEach { batch.update(it.reference, "profilePhotoUrl", null) }
            batch.delete(images.document(id))
        }
        if (!campsiteId.isNullOrBlank()) {
            batch.update(db.collection("campsites").document(campsiteId), "imageUrl", FieldValue.delete())
        }
        batch.commit().awaitResult()
    }

    companion object {
        /** Name of the images collection. */
        const val IMAGES = "images"
    }
}

/** Converts [image] to the fields stored in Firestore. */
fun imageToMap(image: StoredImage): Map<String, Any> = mapOf(
    "uploaderId" to image.uploaderId,
    "kind" to image.kind,
    "data" to image.data,
    "label" to image.label,
    "createdAt" to image.createdAt,
)

/** Builds a [StoredImage] from Firestore document [data]. */
fun imageFromMap(id: String, data: Map<String, Any?>): StoredImage = StoredImage(
    id = id,
    uploaderId = data["uploaderId"] as? String ?: "",
    kind = data["kind"] as? String ?: StoredImage.KIND_CAMPSITE,
    data = data["data"] as? String ?: "",
    label = data["label"] as? String ?: "",
    createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
)
