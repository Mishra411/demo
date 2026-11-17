/*
 * LotteryRoutes.kt
 * Connects the waiting list screens to WaitingListController: collects the
 * live data, runs actions in a coroutine and shows their result.
 * Known issues: "now" is read when the screen is shown; the screen does not
 * refresh by itself when registration opens or closes.
 */

package com.example.code.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.code.controller.LotteryController
import com.example.code.controller.WaitingListController
import com.example.code.data.LotteryCreationRepository
import com.example.code.data.JoinResult
import com.example.code.model.Campsite
import com.example.code.model.Lottery
import com.example.code.model.WaitingListEntry
import com.example.code.ui.entrant.LotterySection
import com.example.code.ui.owner.OwnerLotteriesScreen
import com.example.code.ui.owner.OwnerWaitingListScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Lottery card on the entrant's campsite details screen.
 *
 * @param lottery the campsite's lottery, or null.
 * @param deviceId this entrant's ID.
 * @param controller waiting list controller.
 * @param drawnContent extra content shown after the draw; receives this
 * entrant's waiting list entry.
 * @param entrantName this entrant's profile name, shown to the owner on the waiting list.
 */
@Composable
fun EntrantLotteryCard(
    lottery: Lottery?,
    deviceId: String,
    controller: WaitingListController,
    drawnContent: @Composable (WaitingListEntry?) -> Unit = {},
    entrantName: String = "",
) {
    val scope = rememberCoroutineScope()
    val entries by remember(lottery?.id) {
        lottery?.let { controller.waitingList(it.id) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Re-read the clock every second so the Join button appears as soon as
    // registration opens, without leaving the screen.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    LotterySection(
        lottery = lottery,
        myEntry = entries.firstOrNull { it.entrantId == deviceId },
        now = now,
        busy = busy,
        statusMessage = message,
        onJoin = {
            val target = lottery ?: return@LotterySection
            busy = true
            scope.launch {
                message = try {
                    when (controller.join(target, deviceId, entrantName)) {
                        JoinResult.JOINED -> "You joined the waiting list."
                        JoinResult.ALREADY_JOINED -> "You are already on the waiting list."
                        JoinResult.FULL -> "Sorry, the waiting list is full."
                        JoinResult.CLOSED -> "Registration is not open."
                    }
                } catch (e: Exception) {
                    "Could not join: ${e.message}"
                }
                busy = false
            }
        },
        onLeave = {
            val target = lottery ?: return@LotterySection
            busy = true
            scope.launch {
                message = try {
                    if (controller.leave(target, deviceId)) {
                        "You left the waiting list."
                    } else {
                        "You are not on the waiting list."
                    }
                } catch (e: Exception) {
                    "Could not leave: ${e.message}"
                }
                busy = false
            }
        },
        drawnContent = drawnContent,
    )
}

/**
 * Owner's Lotteries tab.
 *
 * @param lotteries all lotteries.
 * @param deviceId this owner's ID.
 * @param onOpenWaitingList called with the lottery to open.
 * @param onEdit called with an open lottery to update (US 02.04.06).
 * @param onOpenSent opens the notifications this owner sent (US 02.10.02).
 * @param campsites all campsites, to offer this owner's free campsites for shared lotteries.
 * @param repository saves a campsite added to a shared lottery (US 02.04.08); null hides that section.
 */
@Composable
fun OwnerLotteriesRoute(
    lotteries: List<Lottery>,
    deviceId: String,
    onOpenWaitingList: (Lottery) -> Unit,
    onEdit: ((Lottery) -> Unit)? = null,
    onOpenSent: (() -> Unit)? = null,
    campsites: List<Campsite> = emptyList(),
    repository: LotteryCreationRepository? = null,
) {
    val scope = rememberCoroutineScope()
    var joinMessage by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    // A campsite can only be in one open lottery at a time.
    val busyCampsites = lotteries.filter { it.status == Lottery.STATUS_OPEN }
        .flatMap { lottery -> LotteryController.campsitesOf(lottery).map { it.first } }
        .toSet()
    OwnerLotteriesScreen(
        lotteries = WaitingListController.lotteriesOfOwner(lotteries, deviceId),
        onOpenWaitingList = onOpenWaitingList,
        onEdit = onEdit,
        onOpenSent = onOpenSent,
        ownerId = deviceId,
        joinable = lotteries.filter { LotteryController.canJoinShared(it, deviceId, now) },
        myCampsites = campsites.filter { it.ownerId == deviceId && it.id !in busyCampsites },
        onJoin = repository?.let { repo ->
            { lottery, campsite ->
                scope.launch {
                    joinMessage = try {
                        LotteryController.joinSharedLottery(repo, lottery, campsite, deviceId)
                        "${campsite.name} was added to the shared lottery."
                    } catch (e: IllegalArgumentException) {
                        e.message
                    } catch (e: Exception) {
                        "Could not add the campsite: ${e.message}"
                    }
                }
            }
        },
        joinMessage = joinMessage,
    )
}

/**
 * Owner's waiting list screen for one lottery.
 *
 * @param lottery the lottery.
 * @param deviceId this owner's ID, used as the sender.
 * @param controller waiting list controller.
 * @param onBack called when the owner goes back.
 */
@Composable
fun OwnerWaitingListRoute(
    lottery: Lottery,
    deviceId: String,
    controller: WaitingListController,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val entries by remember(lottery.id) { controller.waitingList(lottery.id) }
        .collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }

    OwnerWaitingListScreen(
        lottery = lottery,
        entries = entries,
        statusMessage = message,
        onNotify = { text ->
            scope.launch {
                message = try {
                    val sent = controller.notifyWaitingList(lottery, deviceId, entries, text)
                    "Notification sent to $sent entrants."
                } catch (e: Exception) {
                    "Could not send: ${e.message}"
                }
            }
        },
        onBack = onBack,
    )
}
