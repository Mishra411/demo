/*
 * AppNavHost.kt
 * Navigation shell: top bar, bottom bar and a NavHost for the active mode.
 * Each mode gets its own NavHost, while shared application data is kept in
 * controllers above the individual mode navigation stacks.
 * Lottery screens are wired in through LotteryRoutes.kt, reservation screens
 * through ReservationRoutes.kt, the remaining screens through ExtraRoutes.kt,
 * and AutomaticDraw.kt draws winners when registration ends.
 * AI use: the loading screen with Retry, the Services holder and the routes
 * for chat, maps, reviews, lottery updates, sent notifications,
 * accessibility, policy and the admin Images and Logs tabs were added with
 * Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 */

package com.example.code.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.code.User
import com.example.code.controller.CampsiteController
import com.example.code.controller.DrawController
import com.example.code.controller.Inbox
import com.example.code.controller.LotteryController
import com.example.code.controller.MessageController
import com.example.code.controller.ReservationController
import com.example.code.controller.ReviewController
import com.example.code.controller.UserController
import com.example.code.controller.WaitingListController
import com.example.code.data.FirestoreImageRepository
import com.example.code.data.FirestoreLotteryRepository
import com.example.code.data.FirestoreMessageRepository
import com.example.code.data.FirestoreNotificationRepository
import com.example.code.data.FirestoreReservationRepository
import com.example.code.data.FirestoreReviewRepository
import com.example.code.data.FirestoreUserRepository
import com.example.code.data.ImageRepository
import com.example.code.data.LotteryCreationRepository
import com.example.code.data.NotificationRepository
import com.example.code.model.AccessibilitySettings
import com.example.code.model.AppNotification
import com.example.code.model.Featured
import com.example.code.model.Lottery
import com.example.code.model.Reservation
import com.example.code.model.StoredImage
import com.example.code.model.ownedCampsites
import com.example.code.ui.admin.AdminCampsitesScreen
import com.example.code.ui.admin.AdminProfileRemovalRoute
import com.example.code.ui.common.AccessibilityScreen
import com.example.code.ui.common.LoadingScreen
import com.example.code.ui.common.OsmMap
import com.example.code.ui.common.PlaceholderScreen
import com.example.code.ui.common.PolicyScreen
import com.example.code.ui.entrant.CampsiteDetailsDestination
import com.example.code.ui.entrant.CampsiteScanRoute
import com.example.code.ui.entrant.ExploreScreen
import com.example.code.ui.owner.OwnerCampsiteDetailsDestination
import com.example.code.ui.owner.OwnerCampsitesScreen
import com.example.code.ui.owner.RegisterCampsiteScreen
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val OWNER_CAMPSITE_DETAILS_ROUTE = "owner/campsite/{campsiteId}"

private const val CAMPSITE_DETAILS_ROUTE =
    "entrant/campsite/{campsiteId}"

private const val ADMIN_CAMPSITE_DETAILS_ROUTE = "admin/campsite/{campsiteId}"

private const val OWNER_WAITING_LIST_ROUTE =
    "owner/lottery/{lotteryId}/waiting-list"

private const val OWNER_EDIT_LOTTERY_ROUTE = "owner/lottery/{lotteryId}/edit"

private const val OWNER_SENT_ROUTE = "owner/sent-notifications"

private const val ENTRANT_RESERVATION_MAP_ROUTE = "entrant/reservation-map"

/**
 * Controllers and repositories shared by every screen, so they are passed
 * around as one value.
 */
private class Services(
    val users: UserController,
    val campsites: CampsiteController,
    val waitingList: WaitingListController,
    val reservations: ReservationController,
    val lotteryCreation: LotteryCreationRepository,
    val notifications: NotificationRepository,
    val messages: MessageController,
    val reviews: ReviewController,
    val images: ImageRepository,
)

/**
 * Root composable of the app.
 *
 * Loads or creates the device-based user profile before showing the main app.
 *
 * @param controller keeps the active user mode.
 * @param campsiteController manages campsite data shared across modes.
 * @param waitingListController manages lotteries and waiting lists.
 * @param drawController draws winners when registration ends.
 * @param ownerId optional active user ID, shared with owner and lottery flows.
 * @param reservationController manages reservations.
 * @param lotteryCreationRepository saves owner-created lotteries in the shared collection.
 * @param accessibility the user's accessibility mode choices.
 * @param onAccessibilityChange saves new accessibility choices.
 */
@Composable
fun AppNavHost(
    controller: ModeController = viewModel(),
    campsiteController: CampsiteController = viewModel(),
    waitingListController: WaitingListController = remember {
        WaitingListController(
            FirestoreLotteryRepository(),
            FirestoreNotificationRepository(),
        )
    },
    drawController: DrawController = remember {
        DrawController(
            FirestoreLotteryRepository(),
            FirestoreNotificationRepository(),
        )
    },
    reservationController: ReservationController = remember {
        ReservationController(FirestoreReservationRepository(), FirestoreNotificationRepository())
    },
    ownerId: String? = null,
    lotteryCreationRepository: LotteryCreationRepository =
        remember { FirestoreLotteryRepository() },
    accessibility: AccessibilitySettings = AccessibilitySettings(),
    onAccessibilityChange: (AccessibilitySettings) -> Unit = {},
) {
    val userController =
        remember {
            UserController(FirestoreUserRepository())
        }
    val services = remember {
        val notifications = FirestoreNotificationRepository()
        Services(
            users = userController,
            campsites = campsiteController,
            waitingList = waitingListController,
            reservations = reservationController,
            lotteryCreation = lotteryCreationRepository,
            notifications = notifications,
            messages = MessageController(FirestoreMessageRepository(), notifications),
            reviews = ReviewController(FirestoreReviewRepository()),
            images = FirestoreImageRepository(),
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val rawDeviceId = ownerId ?: rememberDeviceId()

    LaunchedEffect(rawDeviceId) {
        userController.loadOrCreateUser(rawDeviceId)
    }

    val currentUser by userController.currentUser.collectAsState()
    val userIsLoading by userController.isLoading.collectAsState()
    val userError by userController.errorMessage.collectAsState()
    val availableModes by controller.availableModes.collectAsState()

    LaunchedEffect(currentUser, currentUser?.roles) {
        currentUser?.let { user ->
            controller.setAvailableModes(
                modesForRoles(user.roles),
            )
        }
    }

    if (currentUser == null) {
        // Never leave the user on a blank screen: show progress, or the
        // error with a Retry button (for example when the device is offline).
        LoadingScreen(
            error = userError,
            onRetry = {
                coroutineScope.launch { userController.loadOrCreateUser(rawDeviceId) }
            },
        )
        return
    }

    val user = currentUser!!
    val deviceId = user.deviceId
    val mode by controller.activeMode.collectAsState()

    val lotterySnapshot by
    waitingListController.allLotteries.collectAsState(initial = null)

    val lotteries = lotterySnapshot.orEmpty()

    AutomaticDraw(
        lotteries = lotteries,
        controller = drawController,
    )

    key(mode) {
        ModeShell(
            mode = mode,
            availableModes = availableModes,
            onSwitchMode = { controller.switchTo(it) },
            user = user,
            userIsSaving = userIsLoading,
            userError = userError,
            services = services,
            lotteries = lotteries,
            lotteriesReady = lotterySnapshot != null,
            deviceId = deviceId,
            accessibility = accessibility,
            onAccessibilityChange = onAccessibilityChange,
        )
    }
}

/**
 * Scaffold and NavHost for one mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeShell(
    mode: AppMode,
    availableModes: List<AppMode>,
    onSwitchMode: (AppMode) -> Unit,
    user: User,
    userIsSaving: Boolean,
    userError: String?,
    services: Services,
    lotteries: List<Lottery>,
    lotteriesReady: Boolean,
    deviceId: String,
    accessibility: AccessibilitySettings,
    onAccessibilityChange: (AccessibilitySettings) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val campsiteController = services.campsites

    val campsites by campsiteController.campsites.collectAsState()
    val isLoading by campsiteController.isLoading.collectAsState()
    val loadError by campsiteController.errorMessage.collectAsState()

    val reservationSnapshot by
    services.reservations.allReservations.collectAsState(initial = null)

    val reservations = reservationSnapshot.orEmpty()

    // One device can be an entrant and an owner, so its notifications are
    // split between the entrant's Notifications tab and the owner's inbox.
    val allNotifications by remember(deviceId) {
        services.notifications.notificationsFor(deviceId).catch { emit(emptyList()) }
    }.collectAsState(initial = emptyList())
    val entrantNotifications = Inbox.forEntrant(allNotifications, reservations)
    val ownerInbox = Inbox.forOwner(allNotifications, reservations)
    val prefix = mode.name.lowercase()
    val openChat: (Reservation) -> Unit = { reservation ->
        navController.navigate("$prefix/chat/${Uri.encode(reservation.id)}") { launchSingleTop = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Symbol Camp", style = MaterialTheme.typography.titleLarge)
                        Text("${mode.label} mode", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    if (mode == AppMode.ADMIN) {
                        IconButton(
                            onClick = {
                                navController.navigate(
                                    Destination.ADMIN_PROFILE.route,
                                ) {
                                    launchSingleTop = true
                                }
                            },
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = "Profile",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomBar(
                tabs = Destination.tabsFor(mode),
                currentRoute = currentRoute,
                onTabSelected = {
                    navController.openTab(it, mode)
                },
                badges = mapOf(
                    Destination.ENTRANT_NOTIFICATIONS to Inbox.unread(entrantNotifications),
                    Destination.OWNER_RESERVATIONS to Inbox.unread(ownerInbox),
                ),
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.startFor(mode).route,
            modifier = Modifier.padding(padding),
        ) {
            Destination.entries
                .filter { it.mode == mode }
                .forEach { destination ->
                    composable(destination.route) {
                        DestinationContent(
                            destination = destination,
                            mode = mode,
                            availableModes = availableModes,
                            onSwitchMode = onSwitchMode,
                            user = user,
                            userIsSaving = userIsSaving,
                            userError = userError,
                            navController = navController,
                            services = services,
                            lotteries = lotteries,
                            reservations = reservations,
                            availabilityReady =
                                lotteriesReady &&
                                        reservationSnapshot != null,
                            deviceId = deviceId,
                            entrantNotifications = entrantNotifications,
                            ownerInbox = ownerInbox,
                            openChat = openChat,
                        )
                    }
                }

            sharedRoutes(
                prefix = prefix,
                navController = navController,
                services = services,
                reservations = reservations,
                user = user,
                accessibility = accessibility,
                onAccessibilityChange = onAccessibilityChange,
            )

            if (mode == AppMode.OWNER) {
                composable(
                    route = OWNER_CAMPSITE_DETAILS_ROUTE,
                    arguments =
                        listOf(
                            navArgument("campsiteId") {
                                type = NavType.StringType
                            },
                        ),
                ) { detailsEntry ->
                    val campsiteId = detailsEntry.arguments?.getString("campsiteId")
                    OwnerCampsiteDetailsDestination(
                        campsiteId = campsiteId,
                        ownerId = deviceId,
                        campsites = campsites,
                        isLoading = isLoading,
                        errorMessage = loadError,
                        onRetry = campsiteController::retryLoading,
                        onBack = { navController.popBackStack() },
                        onPublishLottery = { campsite, settings ->
                            LotteryController.publishLottery(
                                services.lotteryCreation,
                                campsite,
                                deviceId,
                                settings,
                            )
                        },
                        onEditCampsite = { edited, showError, done ->
                            campsiteController.updateCampsite(edited, onSuccess = done, onFailure = showError)
                        },
                        onUploadPhoto = { uri ->
                            uploadPhoto(context, uri, services.images, deviceId, StoredImage.KIND_CAMPSITE, "")
                        },
                        lotteries = lotteries,
                        reservations = reservations,
                        map = { point -> OsmMap(listOf(point), Modifier.fillMaxSize()) },
                        reviewsContent = {
                            if (campsiteId != null) {
                                ReviewsRoute(campsiteId, deviceId, user.name, services.reviews, canReview = false)
                            }
                        },
                    )
                }
            }

            if (mode == AppMode.ENTRANT) {
                composable(
                    route = CAMPSITE_DETAILS_ROUTE,
                    arguments =
                        listOf(
                            navArgument("campsiteId") {
                                type = NavType.StringType
                            },
                        ),
                ) { detailsEntry ->
                    val campsiteId =
                        detailsEntry.arguments
                            ?.getString("campsiteId")

                    val lottery =
                        WaitingListController.lotteryForCampsite(
                            lotteries,
                            campsiteId.orEmpty(),
                        )

                    CampsiteDetailsDestination(
                        campsiteId = campsiteId,
                        campsites = campsites,
                        isLoading = isLoading,
                        errorMessage = loadError,
                        onRetry = campsiteController::retryLoading,
                        onBack = { navController.popBackStack() },
                        lotteries = lotteries,
                        reservations = reservations,
                        map = { point -> OsmMap(listOf(point), Modifier.fillMaxSize()) },
                        reviewsContent = {
                            if (campsiteId != null) {
                                ReviewsRoute(
                                    campsiteId = campsiteId,
                                    deviceId = deviceId,
                                    userName = user.name,
                                    controller = services.reviews,
                                    // Owners cannot rate their own campsite.
                                    canReview = campsites.firstOrNull { it.id == campsiteId }?.ownerId != deviceId,
                                )
                            }
                        },
                        lotteryContent = {
                            EntrantLotteryCard(
                                lottery = lottery,
                                deviceId = deviceId,
                                controller = services.waitingList,
                                entrantName = user.name,
                                drawnContent = { myEntry ->
                                    if (lottery != null) {
                                        WinnerReservationContent(
                                            lottery = lottery,
                                            myEntry = myEntry,
                                            reservations = reservations,
                                            controller = services.reservations,
                                            campsites = campsites,
                                            entrantName = user.name,
                                        )
                                    }
                                },
                            )
                        },
                    )
                }

                composable(ENTRANT_RESERVATION_MAP_ROUTE) {
                    ReservationMapRoute(
                        reservations = reservations,
                        campsites = campsites,
                        deviceId = deviceId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            if (mode == AppMode.ADMIN) {
                composable(
                    route = ADMIN_CAMPSITE_DETAILS_ROUTE,
                    arguments =
                        listOf(
                            navArgument("campsiteId") {
                                type = NavType.StringType
                            },
                        ),
                ) { detailsEntry ->
                    CampsiteDetailsDestination(
                        campsiteId =
                            detailsEntry.arguments
                                ?.getString("campsiteId"),
                        campsites = campsites,
                        isLoading = isLoading,
                        errorMessage = loadError,
                        onRetry = campsiteController::retryLoading,
                        onBack = { navController.popBackStack() },
                        lotteries = lotteries,
                        reservations = reservations,
                        map = { point -> OsmMap(listOf(point), Modifier.fillMaxSize()) },
                    )
                }
            }

            if (mode == AppMode.OWNER) {
                composable(
                    route = OWNER_WAITING_LIST_ROUTE,
                    arguments =
                        listOf(
                            navArgument("lotteryId") {
                                type = NavType.StringType
                            },
                        ),
                ) { entry ->
                    val lotteryId =
                        entry.arguments?.getString("lotteryId")

                    val lottery =
                        lotteries.firstOrNull {
                            it.id == lotteryId
                        }

                    if (lottery == null) {
                        Text("Lottery not found")
                    } else {
                        OwnerWaitingListRoute(
                            lottery = lottery,
                            deviceId = deviceId,
                            controller = services.waitingList,
                            onBack = {
                                navController.popBackStack()
                            },
                        )
                    }
                }

                composable(
                    route = OWNER_EDIT_LOTTERY_ROUTE,
                    arguments = listOf(navArgument("lotteryId") { type = NavType.StringType }),
                ) { entry ->
                    val lotteryId = entry.arguments?.getString("lotteryId")
                    // Remember the lottery as it was when the form opened, so
                    // a live update does not reset what the owner is typing.
                    val lottery = remember(lotteryId) { lotteries.firstOrNull { it.id == lotteryId } }
                    EditLotteryRoute(
                        lottery = lottery,
                        campsites = campsites,
                        deviceId = deviceId,
                        repository = services.lotteryCreation,
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(OWNER_SENT_ROUTE) {
                    SentNotificationsRoute(
                        deviceId = deviceId,
                        repository = services.notifications,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/**
 * Routes every mode has: the conversation about a reservation,
 * accessibility mode and the app policy.
 */
private fun NavGraphBuilder.sharedRoutes(
    prefix: String,
    navController: NavHostController,
    services: Services,
    reservations: List<Reservation>,
    user: User,
    accessibility: AccessibilitySettings,
    onAccessibilityChange: (AccessibilitySettings) -> Unit,
) {
    composable(
        route = "$prefix/chat/{reservationId}",
        arguments = listOf(navArgument("reservationId") { type = NavType.StringType }),
    ) { entry ->
        ConversationRoute(
            reservationId = entry.arguments?.getString("reservationId"),
            reservations = reservations,
            deviceId = user.deviceId,
            userName = user.name,
            controller = services.messages,
            onBack = { navController.popBackStack() },
        )
    }
    composable("$prefix/accessibility") {
        AccessibilityScreen(
            settings = accessibility,
            onChange = onAccessibilityChange,
            onBack = { navController.popBackStack() },
        )
    }
    composable("$prefix/policy") {
        PolicyScreen(onBack = { navController.popBackStack() })
    }
}

/**
 * Shows the screen for [destination].
 */
@Composable
private fun DestinationContent(
    destination: Destination,
    mode: AppMode,
    availableModes: List<AppMode>,
    onSwitchMode: (AppMode) -> Unit,
    user: User,
    userIsSaving: Boolean,
    userError: String?,
    navController: NavHostController,
    services: Services,
    lotteries: List<Lottery>,
    reservations: List<Reservation>,
    availabilityReady: Boolean,
    deviceId: String,
    entrantNotifications: List<AppNotification>,
    ownerInbox: List<AppNotification>,
    openChat: (Reservation) -> Unit,
) {
    val campsiteController = services.campsites
    val campsites by campsiteController.campsites.collectAsState()

    val isLoading by campsiteController.isLoading.collectAsState()
    val loadError by campsiteController.errorMessage.collectAsState()
    val context = LocalContext.current
    val prefix = mode.name.lowercase()

    if (
        deviceId.isBlank() &&
        destination in
        listOf(
            Destination.OWNER_CAMPSITES,
            Destination.OWNER_REGISTER_CAMPSITE,
        )
    ) {
        Text(
            "Your device identity is unavailable. Please reopen the app.",
        )
        return
    }

    when (destination) {
        Destination.ENTRANT_EXPLORE -> {
            val openCampsite: (com.example.code.model.Campsite) -> Unit = { campsite ->
                navController.navigate(
                    "entrant/campsite/${Uri.encode(campsite.id)}",
                )
            }
            val now = System.currentTimeMillis()
            val openLotteryCampsites = lotteries
                .filter { LotteryController.isRegistrationOpen(it, now) }
                .flatMap { lottery -> LotteryController.campsitesOf(lottery).map { it.first } }
                .toSet()
            ExploreScreen(
                campsites = campsites,
                lotteries = lotteries,
                reservations = reservations,
                availabilityReady = availabilityReady,
                isLoading = isLoading,
                errorMessage = loadError,
                onRetry = campsiteController::retryLoading,
                onCampsiteClick = openCampsite,
                featured = Featured.pick(campsites, user.preferredTags, openLotteryCampsites),
                mapContent = { shown -> ExploreMapRoute(shown, openCampsite) },
            )
        }

        Destination.ENTRANT_SCAN ->
            CampsiteScanRoute(
                onOpenCampsite = { campsiteId ->
                    navController.navigate(
                        "entrant/campsite/${Uri.encode(campsiteId)}",
                    ) {
                        launchSingleTop = true
                    }
                },
            )

        Destination.OWNER_CAMPSITES ->
            OwnerCampsitesScreen(
                campsites =
                    ownedCampsites(
                        campsites,
                        deviceId,
                    ),
                isLoading = isLoading,
                errorMessage = loadError,
                onRetry = campsiteController::retryLoading,
                onCampsiteClick = { campsite ->
                    navController.navigate(
                        "owner/campsite/${Uri.encode(campsite.id)}",
                    )
                },
                onRegisterCampsite = {
                    navController.navigate(
                        Destination.OWNER_REGISTER_CAMPSITE.route,
                    )
                },
            )

        Destination.OWNER_REGISTER_CAMPSITE ->
            RegisterCampsiteScreen(
                onRegister = { campsite, showError ->
                    campsiteController.addCampsite(
                        campsite.copy(
                            ownerId = deviceId,
                        ),
                        onSuccess = {
                            navController.popBackStack(
                                Destination.OWNER_REGISTER_CAMPSITE.route,
                                inclusive = true,
                            )
                        },
                        onFailure = { message ->
                            showError(message)
                        },
                    )
                },
                onBack = {
                    navController.popBackStack()
                },
                onUploadPhoto = { uri ->
                    uploadPhoto(context, uri, services.images, deviceId, StoredImage.KIND_CAMPSITE, "")
                },
            )

        Destination.OWNER_LOTTERIES ->
            OwnerLotteriesRoute(
                lotteries = lotteries,
                deviceId = deviceId,
                onOpenWaitingList = { lottery ->
                    navController.navigate(
                        "owner/lottery/${lottery.id}/waiting-list",
                    )
                },
                onEdit = { lottery -> navController.navigate("owner/lottery/${lottery.id}/edit") },
                onOpenSent = { navController.navigate(OWNER_SENT_ROUTE) { launchSingleTop = true } },
            )

        Destination.ENTRANT_NOTIFICATIONS ->
            EntrantNotificationsRoute(
                notifications = entrantNotifications,
                repository = services.notifications,
                onOpenReservations = {
                    navController.navigate(
                        Destination.ENTRANT_RESERVATIONS.route,
                    ) {
                        launchSingleTop = true
                    }
                },
                onOpenMessage = { notification ->
                    reservations.firstOrNull { it.id == notification.reservationId }?.let(openChat)
                },
            )
        Destination.ENTRANT_RESERVATIONS ->
            EntrantReservationsRoute(
                reservations = reservations,
                deviceId = deviceId,
                controller = services.reservations,
                onMessage = openChat,
                onOpenMap = {
                    navController.navigate(ENTRANT_RESERVATION_MAP_ROUTE) { launchSingleTop = true }
                },
            )

        Destination.OWNER_RESERVATIONS ->
            OwnerReservationsRoute(
                reservations = reservations,
                deviceId = deviceId,
                controller = services.reservations,
                inbox = ownerInbox,
                onMessage = openChat,
                notifications = services.notifications,
            )

        Destination.ADMIN_PROFILES ->
            AdminProfileRemovalRoute(
                currentProfileId = deviceId,
                campsites = campsites,
            )

        Destination.ADMIN_CAMPSITES ->
            AdminCampsitesScreen(
                campsites = campsites,
                isLoading = isLoading,
                errorMessage = loadError,
                onRetry = campsiteController::retryLoading,
                onCampsiteClick = { campsite ->
                    navController.navigate(
                        "admin/campsite/${Uri.encode(campsite.id)}",
                    )
                },
                onRemoveCampsite = { campsite, showError ->
                    campsiteController.removeCampsite(
                        campsite,
                        onSuccess = {},
                        onFailure = { message ->
                            showError(message)
                        },
                    )
                },
            )

        Destination.ADMIN_IMAGES ->
            AdminImagesRoute(campsites = campsites, images = services.images)

        Destination.ADMIN_LOGS ->
            AdminLogsRoute(notifications = services.notifications, messages = services.messages)

        Destination.ENTRANT_PROFILE,
        Destination.OWNER_PROFILE,
        Destination.ADMIN_PROFILE ->
            ProfileRoute(
                user = user,
                mode = mode,
                availableModes = availableModes,
                isSaving = userIsSaving,
                errorMessage = userError,
                userController = services.users,
                images = services.images,
                campsites = campsites,
                onSwitchMode = onSwitchMode,
                onOpenAccessibility = { navController.navigate("$prefix/accessibility") { launchSingleTop = true } },
                onOpenPolicy = { navController.navigate("$prefix/policy") { launchSingleTop = true } },
            )
    }
}

/**
 * Opens [tab] of [mode] without stacking tabs on top of each other.
 */
private fun NavHostController.openTab(
    tab: Destination,
    mode: AppMode,
) {
    navigate(tab.route) {
        popUpTo(
            Destination.startFor(mode).route,
        ) {
            saveState = true
        }

        launchSingleTop = true
        restoreState = true
    }
}
