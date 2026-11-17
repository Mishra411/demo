/*
 * ExtraRoutes.kt
 * Connects the profile settings, reviews, conversations, map view, lottery
 * updates, sent notifications and the admin Images and Logs tabs to their
 * controllers and repositories: collects the live data, runs actions in a
 * coroutine and shows their result.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.code.User
import com.example.code.UserRole
import com.example.code.controller.LotteryController
import com.example.code.controller.MessageController
import com.example.code.controller.ReviewController
import com.example.code.controller.UserController
import com.example.code.data.ImageRepository
import com.example.code.data.LotteryCreationRepository
import com.example.code.data.NotificationRepository
import com.example.code.model.Campsite
import com.example.code.model.Featured
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.StoredImage
import com.example.code.ui.admin.AdminImagesScreen
import com.example.code.ui.admin.AdminLogsScreen
import com.example.code.ui.admin.adminImageItems
import com.example.code.ui.common.ConversationScreen
import com.example.code.ui.common.OsmMap
import com.example.code.ui.common.ProfileScreen
import com.example.code.ui.common.compressPhoto
import com.example.code.ui.common.formatDate
import com.example.code.ui.common.rememberLocationRequester
import com.example.code.ui.entrant.CampsiteRadiusMap
import com.example.code.ui.entrant.ReviewsSection
import com.example.code.ui.owner.CreateLotteryScreen
import com.example.code.ui.owner.SentNotificationsScreen
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Shrinks the photo at [uri], uploads it and returns the reference to
 * store on a campsite or profile.
 *
 * @param kind [StoredImage.KIND_CAMPSITE] or [StoredImage.KIND_PROFILE].
 * @param label what the photo shows, for the admin Images tab.
 */
suspend fun uploadPhoto(
    context: Context,
    uri: Uri,
    images: ImageRepository,
    uploaderId: String,
    kind: String,
    label: String,
): String {
    val data = compressPhoto(context, uri)
    val id = images.upload(
        StoredImage(
            uploaderId = uploaderId,
            kind = kind,
            data = data,
            label = label,
            createdAt = System.currentTimeMillis(),
        ),
    )
    return StoredImage.refFor(id)
}

/**
 * Profile screen with the user's settings.
 *
 * @param campsites all campsites, for the interest tags offered.
 * @param onOpenAccessibility opens accessibility mode.
 * @param onOpenPolicy opens the app policy.
 */
@Composable
fun ProfileRoute(
    user: User,
    mode: AppMode,
    availableModes: List<AppMode>,
    isSaving: Boolean,
    errorMessage: String?,
    userController: UserController,
    images: ImageRepository,
    campsites: List<Campsite>,
    onSwitchMode: (AppMode) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenPolicy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ProfileScreen(
        user = user,
        activeMode = mode,
        availableModes = availableModes,
        isSaving = isSaving,
        errorMessage = errorMessage,
        onSaveProfile = { name, email, phoneNumber ->
            scope.launch { userController.updateProfile(name = name, email = email, phoneNumber = phoneNumber) }
        },
        onSwitchMode = onSwitchMode,
        onSetNotifications = { enabled -> scope.launch { userController.setNotificationsEnabled(enabled) } },
        onSetHosting = { enabled -> scope.launch { userController.setHosting(enabled) } },
        // US 03.11.01: an administrator can also be an entrant with the same profile.
        onSetEntering = if (UserRole.ADMIN in user.roles) {
            { enabled -> scope.launch { userController.setEntering(enabled) } }
        } else {
            null
        },
        onPhotoPicked = { uri ->
            scope.launch {
                try {
                    val ref = uploadPhoto(context, uri, images, user.deviceId, StoredImage.KIND_PROFILE, user.name)
                    userController.setProfilePhoto(ref)
                } catch (_: Exception) {
                    // The profile keeps its old photo.
                }
            }
        },
        onRemovePhoto = {
            scope.launch {
                val old = user.profilePhotoUrl
                if (userController.setProfilePhoto(null)) {
                    // Also delete the uploaded file so it leaves the admin Images list.
                    StoredImage.idFromRef(old)?.let { id ->
                        try {
                            images.remove(id)
                        } catch (_: Exception) {
                            // The photo is already gone from the profile.
                        }
                    }
                }
            }
        },
        availableTags = Featured.allTags(campsites).take(12),
        onSetPreferredTags = { tags -> scope.launch { userController.setPreferredTags(tags) } },
        onOpenAccessibility = onOpenAccessibility,
        onOpenPolicy = onOpenPolicy,
        onDeleteProfile = { scope.launch { userController.deleteProfile() } },
    )
}

/**
 * Ratings and reviews of one campsite (US 01.13.01 and the advanced review system).
 *
 * @param canReview false for the campsite's owner, who can reply and react only,
 * and for entrants who have not stayed there yet (US 01.13.02).
 * @param notAllowedMessage shown instead of the review form when [canReview] is false.
 * @param admin true in admin mode: ratings and reviews can be removed (US 03.09.01, 03.10.01).
 */
@Composable
fun ReviewsRoute(
    campsiteId: String,
    deviceId: String,
    userName: String,
    controller: ReviewController,
    canReview: Boolean = true,
    notAllowedMessage: String? = null,
    admin: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val reviews by remember(campsiteId) {
        controller.reviews(campsiteId).catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run(done: String?, action: suspend () -> Unit) {
        busy = true
        scope.launch {
            message = try {
                action()
                done
            } catch (e: IllegalArgumentException) {
                e.message
            } catch (e: Exception) {
                "Could not save: ${e.message}"
            }
            busy = false
        }
    }

    ReviewsSection(
        threads = ReviewController.threads(reviews),
        myId = deviceId,
        busy = busy,
        message = message,
        onSubmit = { stars, text ->
            run("Thanks for your review.") { controller.submit(campsiteId, deviceId, userName, stars, text) }
        },
        onReply = { review, text -> run(null) { controller.reply(review, deviceId, userName, text) } },
        onReact = { review, key -> run(null) { controller.react(review, key, deviceId) } },
        canReview = canReview,
        notAllowedMessage = notAllowedMessage,
        onRemoveRating = if (admin) ({ review -> run("Rating removed.") { controller.removeRating(review) } }) else null,
        onRemoveReview = if (admin) ({ review -> run("Review removed.") { controller.removeReview(review) } }) else null,
    )
}

/**
 * Conversation between the owner and the guest of one reservation
 * (US 02.12.01 and the messaging system).
 */
@Composable
fun ConversationRoute(
    reservationId: String?,
    reservations: List<Reservation>,
    deviceId: String,
    userName: String,
    controller: MessageController,
    onBack: () -> Unit,
) {
    val reservation = reservations.firstOrNull { it.id == reservationId }
    if (reservation == null || (reservation.ownerId != deviceId && reservation.entrantId != deviceId)) {
        Text("Conversation not found")
        return
    }
    val scope = rememberCoroutineScope()
    val messages by remember(reservation.id) {
        controller.conversation(reservation).catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    var error by remember { mutableStateOf<String?>(null) }

    ConversationScreen(
        reservation = reservation,
        messages = messages,
        myId = deviceId,
        error = error,
        onSend = { text ->
            scope.launch {
                error = try {
                    controller.send(reservation, deviceId, userName, text)
                    null
                } catch (e: Exception) {
                    "Could not send: ${e.message}"
                }
            }
        },
        onBack = onBack,
    )
}

/**
 * Map view WOW feature on Explore: campsites within a chosen radius of the
 * device's location.
 *
 * @param campsites campsites after the entrant's search and filters.
 * @param onCampsiteClick opens a campsite.
 */
@Composable
fun ExploreMapRoute(campsites: List<Campsite>, onCampsiteClick: (Campsite) -> Unit) {
    var latitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var longitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var radius by rememberSaveable { mutableFloatStateOf(100f) }
    var locationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val requestLocation = rememberLocationRequester { found ->
        if (found == null) {
            locationMessage = "Location is off or not allowed, so every campsite is shown."
        } else {
            latitude = found.first
            longitude = found.second
            locationMessage = null
        }
    }
    val origin = latitude?.let { lat -> longitude?.let { lon -> lat to lon } }

    CampsiteRadiusMap(
        campsites = campsites,
        origin = origin,
        radiusKm = radius,
        onRadiusChange = { radius = it },
        locationMessage = locationMessage,
        onUseMyLocation = requestLocation,
        onCampsiteClick = onCampsiteClick,
        map = { points, center, radiusKm, onPointClick ->
            OsmMap(points, Modifier.fillMaxSize(), center = center, radiusKm = radiusKm, onPointClick = onPointClick)
        },
    )
}

/**
 * Form that updates an open lottery (US 02.04.06).
 *
 * @param lottery the lottery to update, or null if it no longer exists.
 * @param campsites all campsites, for the owner's other campsites.
 * @param onDone called after saving or cancelling.
 */
@Composable
fun EditLotteryRoute(
    lottery: Lottery?,
    campsites: List<Campsite>,
    deviceId: String,
    repository: LotteryCreationRepository,
    onDone: () -> Unit,
) {
    val campsite = campsites.firstOrNull { it.id == lottery?.campsiteId }
    if (lottery == null || campsite == null || lottery.ownerId != deviceId) {
        Text("Lottery not found")
        return
    }
    CreateLotteryScreen(
        campsite = campsite,
        onPublish = { settings ->
            LotteryController.updateLottery(repository, lottery, deviceId, settings)
            lottery.id
        },
        onPublished = onDone,
        onCancel = onDone,
        initial = lottery,
        otherCampsites = campsites.filter { it.ownerId == deviceId },
    )
}

/** Notifications the owner [deviceId] has sent (US 02.10.02). */
@Composable
fun SentNotificationsRoute(deviceId: String, repository: NotificationRepository, onBack: () -> Unit) {
    val sent by remember(deviceId) {
        repository.sentBy(deviceId).catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    SentNotificationsScreen(sent = sent, onBack = onBack)
}

/** Admin Images tab (US 03.06.01, 03.03.01). */
@Composable
fun AdminImagesRoute(campsites: List<Campsite>, images: ImageRepository) {
    val scope = rememberCoroutineScope()
    val uploaded by remember { images.observeAll().catch { emit(emptyList()) } }
        .collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AdminImagesScreen(
        items = adminImageItems(uploaded, campsites, ::formatDate),
        message = message,
        busy = busy,
        onRemove = { item ->
            busy = true
            scope.launch {
                message = try {
                    images.remove(item.storedId, item.campsiteId)
                    "Image removed."
                } catch (e: Exception) {
                    "Could not remove the image. Try again."
                }
                busy = false
            }
        },
    )
}

/** Admin Logs tab (US 03.08.01, 03.12.01). */
@Composable
fun AdminLogsRoute(notifications: NotificationRepository, messages: MessageController) {
    var error by remember { mutableStateOf<String?>(null) }
    val allNotifications by remember {
        notifications.observeAll().catch {
            error = "Could not load the logs. Administrator access is required."
            emit(emptyList())
        }
    }.collectAsState(initial = emptyList())
    val allMessages by remember {
        try {
            messages.all().catch { emit(emptyList()) }
        } catch (_: Exception) {
            flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())
    AdminLogsScreen(notifications = allNotifications, messages = allMessages, error = error)
}
