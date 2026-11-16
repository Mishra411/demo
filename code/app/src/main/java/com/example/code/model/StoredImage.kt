/*
 * StoredImage.kt
 * Model of an image uploaded in the app: a campsite promo photo or a profile
 * photo (Firestore: images/{id}). The picture is a compressed JPEG stored as
 * Base64 text, so no extra storage service is needed. Used by the image
 * upload feature and by US 03.06.01 and US 03.03.01 (admin browses and
 * removes images). Data only.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.model

/**
 * An uploaded image.
 *
 * @property id Firestore document ID; campsites and profiles refer to it as "img:{id}".
 * @property uploaderId device ID of the user who uploaded it.
 * @property kind [KIND_CAMPSITE] or [KIND_PROFILE].
 * @property data Base64 JPEG bytes.
 * @property label what the image shows, e.g. the campsite name.
 * @property createdAt upload time, in milliseconds since the epoch.
 */
data class StoredImage(
    val id: String = "",
    val uploaderId: String = "",
    val kind: String = KIND_CAMPSITE,
    val data: String = "",
    val label: String = "",
    val createdAt: Long = 0L,
) {
    companion object {
        /** Promo photo of a campsite. */
        const val KIND_CAMPSITE = "CAMPSITE"

        /** Photo on a user profile. */
        const val KIND_PROFILE = "PROFILE"

        /** Prefix used in Campsite.imageUrl and User.profilePhotoUrl for uploaded images. */
        const val REF_PREFIX = "img:"

        /** Returns the image ID in [ref] if it points at an uploaded image, else null. */
        fun idFromRef(ref: String?): String? =
            ref?.takeIf { it.startsWith(REF_PREFIX) }?.removePrefix(REF_PREFIX)?.takeIf { it.isNotBlank() }

        /** Returns the reference stored on a campsite or profile for image [id]. */
        fun refFor(id: String): String = REF_PREFIX + id
    }
}
