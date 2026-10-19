package com.music.bitchord.desktop

import androidx.compose.foundation.lazy.LazyListState
import com.music.bitchord.ui.components.LocalShelfRowChrome
import com.music.bitchord.ui.components.ShelfRowChrome
import com.music.bitchord.ui.components.ShelfRow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateListOf
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.SourceQualityBadge
import com.music.bitchord.ui.components.sourceQualityBadge
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.trackColumnWidth
import com.music.bitchord.ui.replay.ReplayCreditCard
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.EntityType
import com.music.bitchord.data.model.SearchHistoryEntity
import com.music.bitchord.ui.LocalPullToRefreshEnabled
import com.music.bitchord.ui.screens.ExploreScreen
import com.music.bitchord.ui.screens.HomeScreen
import com.music.bitchord.ui.screens.CompactTrackRow
import com.music.bitchord.ui.screens.LibraryGridPage
import com.music.bitchord.ui.screens.LibraryLink
import com.music.bitchord.ui.screens.LibraryScreen
import com.music.bitchord.ui.screens.MoodGenrePlaylistsScreen
import com.music.bitchord.ui.screens.SearchScreen
import com.music.bitchord.data.NerdStats
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.QueueTimeline.asQueueEntry
import com.music.bitchord.data.listentogether.partyQueueIndexOf
import com.music.bitchord.data.listentogether.partyUpcomingAfter
import com.music.bitchord.ui.LyricsProviderState
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.ui.player.LyricsSidePanel
import com.music.bitchord.ui.player.NowPlayingScreen
import com.music.bitchord.ui.player.QueueSidePanel
import com.music.bitchord.ui.player.PlayerBack
import com.music.bitchord.ui.player.RepeatModes
import com.music.bitchord.ui.player.rememberMixPulse
import androidx.compose.runtime.SideEffect
import com.music.bitchord.data.model.QueueTier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bitchord.desktopapp.generated.resources.Res
import bitchord.desktopapp.generated.resources.logo
import bitchord.desktopapp.generated.resources.logo_mark
import bitchord.desktopapp.generated.resources.sf_pro_display_bold
import bitchord.desktopapp.generated.resources.sf_pro_display_heavy
import bitchord.desktopapp.generated.resources.sf_pro_display_medium
import bitchord.desktopapp.generated.resources.sf_pro_display_regular
import bitchord.desktopapp.generated.resources.sf_pro_display_semibold
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SubscriptionState
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isMatchMissing
import com.music.bitchord.data.model.isMatchPending
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.model.playingRowMatcher
import com.music.bitchord.data.model.isUnresolvedSpotify
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.sharedui.resources.Res as SharedRes
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.data.model.withoutRepeatsOf
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.LastPlayerScreen
import com.music.bitchord.data.settings.SmartAnalysis
import com.music.bitchord.data.settings.TrackAnalysisState
import com.music.bitchord.data.settings.TransitionWindow
import com.music.bitchord.ui.icons.BitChordIcons
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.Font as composeFont
import org.jetbrains.compose.resources.painterResource

/**
 * The margin every page keeps from the window's edge.
 *
 * Desktop-only pages share Library's edge inset so switching destinations does not move the page
 * content sideways.
 */
internal val DesktopPageGutter = PAGE_GUTTER

internal val DesktopBackground = Color(0xFF202020)
internal val DesktopSurface = Color(0xFF0D0D0F)
private val DesktopSurfaceRaised = Color(0xFF1C1C1E)
internal val DesktopGlass = Color(0x661C1C1E)
internal val DesktopGlassStrong = Color(0xCC0D0D0F)

/** The floating bars in the narrow layout. */
private val DesktopBarGlass = Color(0xB324242A)

/** The backdrop the floating bars blur. */
private val LocalDesktopHaze = staticCompositionLocalOf<HazeState?> { null }

/** How much a page's scrollable should keep clear of the floating bars. */
private val LocalDesktopBottomInset = staticCompositionLocalOf { 0.dp }

/** Android's own edge on these bars: `Color.White.copy(alpha = 0.10f)`. */
private val DesktopBarEdge = Color(0x1AFFFFFF)
internal val DesktopAccent = Color.White

/** Deleting, signing out, and anything that failed — the one place a colour survives, as on Android. */
internal val DesktopDestructive = Color(0xFFFF453A)

/** A chip the way Android draws a chosen segment: solid white with black on it, or nothing at all. */
@Composable
internal fun desktopChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = DesktopSecondary,
    selectedContainerColor = Color.White,
    selectedLabelColor = Color.Black,
)

/** Android's switch: a white track with a black knob on, a faint wash off. */
@Composable
internal fun desktopSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Color.White,
    checkedThumbColor = Color.Black,
    checkedBorderColor = Color.Transparent,
    uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
    uncheckedThumbColor = Color.White.copy(alpha = 0.35f),
    uncheckedBorderColor = Color.Transparent,
)
internal val DesktopSecondary = Color(0xFF8E8E93)
internal val DesktopDivider = Color(0xFF2C2C2E)
/**
 * The playback state as the window's root reads it: without what moves on its own — the playhead,
 * Automix's progress, whether a seek's audio has arrived — which those who show it read directly.
 */
private fun rootShape(state: DesktopPlaybackState) = state.copy(
    positionMs = 0L,
    positionSampledAtNanos = 0L,
    awaitingAudio = false,
    mixing = false,
    transitionWindow = null,
    smartAnalysis = com.music.bitchord.data.settings.SmartAnalysis(),
)

private const val STATS_SAMPLE_MS = 5_000L

/** How often the playhead is offered to the history tracker. */
private const val HISTORY_SAMPLE_MS = 5_000L

/** How long the search field is left alone before its typeahead is asked for. */
private const val SUGGESTION_DEBOUNCE_MS = 220L

/** How many tracks a station is built with when one is started explicitly. */
/** A search costs more than a completion, so the live rows wait for a longer pause. */
private const val TYPEAHEAD_MEDIA_DEBOUNCE_MS = 400L

/** How many playable rows the dropdown shows, matching Android's own limit. */
private const val TYPEAHEAD_MEDIA_LIMIT = 8

private const val INITIAL_RADIO_TRACKS = 24

/** An artist's top songs, capped so the release shelves are not buried. */
private const val MAX_ARTIST_SONGS = 20
private const val ARTIST_SONGS_PER_COLUMN = 4

/** The artist photograph's height. */
private val ARTIST_BANNER_HEIGHT = 340.dp

/** The library shelf the account's own playlists arrive on, and the one edits are spliced into. */
private const val PLAYLISTS_SHELF = "Playlists"
private const val STATS_MAX_DELTA_MS = 15_000L
private const val SCROBBLE_THRESHOLD_MS = 180_000L

/** Used for tracking restart of the current song when previous button is pressed. */
private const val BACK_RESTARTS_AFTER_MS = 10_000L

/** How long a manual skip takes to blend into the next track when Automix on skip is on. */
private const val SKIP_MIX_ON_SKIP_SECONDS = 15

/** How long the supplier waits for the server to echo an AutoPlay queue addition. */
private const val PARTY_AUTOPLAY_ECHO_TIMEOUT_MS = 5_000L

/** The phone gives one empty station response a delayed second chance. */
private const val AUTOPLAY_EMPTY_REFRESH_DELAY_MS = 2_000L

/** The phone's dark scheme, which the shared pages are drawn against. */
internal fun desktopColorScheme() = darkColorScheme(
    primary = DesktopAccent,
    // Black on the accent, now that the accent is white.
    onPrimary = Color.Black,
    primaryContainer = Color.White.copy(alpha = 0.16f),
    onPrimaryContainer = Color.White,
    background = DesktopBackground,
    onBackground = Color.White,
    surface = DesktopSurface,
    onSurface = Color.White,
    surfaceVariant = DesktopSurfaceRaised,
    onSurfaceVariant = DesktopSecondary,
    outline = Color(0xFF2C2C2E),
    outlineVariant = DesktopDivider,
    surfaceTint = DesktopAccent,
)

@Composable
internal fun desktopTypography(): Typography {
    val sfProDisplay = FontFamily(
        composeFont(Res.font.sf_pro_display_regular, FontWeight.W400),
        composeFont(Res.font.sf_pro_display_medium, FontWeight.W500),
        composeFont(Res.font.sf_pro_display_semibold, FontWeight.W600),
        composeFont(Res.font.sf_pro_display_bold, FontWeight.W700),
        composeFont(Res.font.sf_pro_display_heavy, FontWeight.W800),
    )
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = sfProDisplay),
        displaySmall = defaults.displaySmall.copy(fontFamily = sfProDisplay),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.7).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = sfProDisplay),
        titleLarge = defaults.titleLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = (-0.2).sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = sfProDisplay),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 16.sp),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W400, fontSize = 14.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = sfProDisplay),
        labelLarge = defaults.labelLarge.copy(fontFamily = sfProDisplay),
        labelMedium = defaults.labelMedium.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 12.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = sfProDisplay, fontWeight = FontWeight.W600, fontSize = 11.sp),
    )
}

private enum class DesktopDestination(val id: String, val label: String) {
    LISTEN_NOW("listen-now", "Home"),
    EXPLORE("explore", "Explore"),
    LIBRARY("library", "Library"),
    SEARCH("search", "Search"),
    HISTORY("history", "History"),
    DOWNLOADS("downloads", "Downloads"),
    LOCAL_MUSIC("local-music", "Local Music"),
    SETTINGS("settings", "Settings"),
}

private enum class DesktopRepeatMode {
    OFF,
    ALL,
    ONE,
    ;

    fun next(): DesktopRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    fun label(): String = when (this) {
        OFF -> "Off"
        ALL -> "All"
        ONE -> "One"
    }
}

@Composable
fun BitChordDesktopApp() {
    if (DesktopFrameProfiler.recompositions) androidx.compose.runtime.SideEffect { DesktopFrameProfiler.count("root") }
    // BITCHORD_PROFILE_ROOT_TICK=1, for measuring: the whole window recomposed twice a second.
    if (DesktopFrameProfiler.enabled && System.getenv("BITCHORD_PROFILE_ROOT_TICK") == "1") {
        val tick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(500); tick.intValue++ } }
        tick.intValue
    }
    if (DesktopFrameProfiler.recompositions) {
        val scope = androidx.compose.runtime.currentRecomposeScope
        androidx.compose.runtime.SideEffect { DesktopFrameProfiler.rootScope = scope }
    }
    if (DesktopFrameProfiler.recompositions) {
        val composition = androidx.compose.runtime.currentComposer.composition
        remember { DesktopFrameProfiler.observe(composition) }
    }
    val scope = rememberCoroutineScope()
    // Filled once the audio engine exists. Local playback helpers use this single hook so every
    // transport and queue gesture reaches Listen Together without duplicating protocol logic.
    val partySyncHolder = remember { arrayOfNulls<DesktopPartySync>(1) }
    val partyState by DesktopListenTogether.state.collectAsState()
    var personalQueueStash by remember { mutableStateOf<DesktopQueue?>(null) }
    var personalPositionStash by remember { mutableStateOf(0L) }
    var personalPlayingStash by remember { mutableStateOf(false) }
    val persistence = remember { DesktopPersistence() }
    // The offer of a new version at launch, the note that one waits to go in, and how the last
    // one went — see [DesktopUpdatePrompts].
    DesktopUpdatePrompts()
    var destination by remember { mutableStateOf(DesktopDestination.LISTEN_NOW) }
    var query by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var searchRows by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // What has been searched before, and what YouTube thinks is being typed.
    val searchHistory by DesktopSearchHistory.recent.collectAsState()
    // Whether anything has been searched for since the field was last emptied: until then the
    // page shows recent searches rather than a result set.
    var searchCommitted by remember { mutableStateOf(false) }
    var searchScrollReset by remember { mutableStateOf(0) }
    var searchFocusRequested by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    // Playable rows for the half-typed query, shown under the text completions.
    var searchTypeahead by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    // False while the query is being set *by* the app.
    var searchTyping by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var homeState by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    // Home as shown: the feed less the shelves the listener has taken off it.
    val hiddenHomeShelves by DesktopHomeShelves.hidden.collectAsState()
    val homeShelfOrder by DesktopHomeShelves.order.collectAsState()
    val visibleHomeState = remember(homeState, hiddenHomeShelves, homeShelfOrder) {
        (homeState as? UiState.Success)?.let { UiState.Success(DesktopHomeShelves.visible(it.data)) } ?: homeState
    }
    val waveShown by DesktopMyWave.shown.collectAsState()
    // The home feed is paged: `FEmusic_home` answers with a couple of shelves and a token, and
    // everything else arrives by following it.
    var homeContinuation by remember { mutableStateOf<String?>(null) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    // Recently Played has its own request and its own skeleton at the head of the page, as on the
    // phone; it is only asked for with an account to have played on.
    var homeRecentsLoading by remember { mutableStateOf(false) }
    // Held out here so a page keeps its place when another is visited and it is come back to.
    val homeListState = DesktopPrefetch.rememberListState()
    val exploreListState = rememberLazyListState()
    val exploreGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val moodGenreListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val homeWheel = rememberSmoothWheel(homeListState)
    // The list whichever other page is open scrolls by — see [DesktopPageScroll].
    val pageScroll = remember { DesktopPageScroll() }
    val exploreWheel = rememberSmoothWheel(exploreGridState)
    val moodGenreWheel = rememberSmoothWheel(moodGenreListState)
    val searchWheel = rememberSmoothWheel(searchListState)
    val libraryWheel = rememberSmoothWheel(libraryListState)
    val libraryGridWheel = rememberSmoothWheel(libraryGridState)
    /** A Library shelf's "Show all", open as a grid in place of the page. */
    var libraryShowAll by remember { mutableStateOf<HomeShelf?>(null) }
    /** What the Spotify grid is waiting on, or why it is empty; null once its playlists are in. */
    var spotifyLibraryStatus by remember { mutableStateOf<String?>(null) }
    var spotifyImportOpen by remember { mutableStateOf(false) }
    var waveSettingsOpen by remember { mutableStateOf(false) }
    var homeCustomizeOpen by remember { mutableStateOf(false) }
    var libraryCustomizeOpen by remember { mutableStateOf(false) }
    val libraryHidden by DesktopLibrarySections.hidden.collectAsState()
    /** Where the one back button in the top bar goes: every place visited, oldest first. */
    val navHistory = remember { mutableStateListOf<DesktopNavEntry>() }
    /** Set while [goBack] puts a place back, so that move is not itself recorded as a visit. */
    val navRestoring = remember { booleanArrayOf(false) }
    /** Opens a song's menu at the pointer; set by the pages' menu host once it is in place. */
    var pageMenu by remember { mutableStateOf<(Song) -> Unit>({}) }
    val homeSeenTitles = remember { HashSet<String>() }
    var homeGeneration by remember { mutableStateOf(0) }
    var homeIdentity by remember { mutableStateOf<String?>(null) }
    var exploreState by remember { mutableStateOf<UiState<List<MoodGenreSection>>>(UiState.Loading) }
    var selectedMoodGenre by remember { mutableStateOf<MoodGenre?>(null) }
    // Bumped by a retry so the loaders below re-run without the state they are keyed on having to
    // change to something and back again.
    var exploreReloads by remember { mutableStateOf(0) }
    var moodGenreReloads by remember { mutableStateOf(0) }
    var moodGenreShelves by remember { mutableStateOf<UiState<List<HomeShelf>>>(UiState.Loading) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    // The live queue: what is playing, what played before it, what is next.
    var liveQueue by remember {
        mutableStateOf(
            DesktopQueue.restored(
                persistence.queue(),
                persistence.string("queue_index", "0").toIntOrNull() ?: 0,
            ),
        )
    }
    // The order the queue was in before shuffle rearranged it, so the toggle can be undone.
    var preShuffleOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    // One party AutoPlay top-up at a time; see topUpPartyAutoplay.
    val partyAutoplayLock = remember { Mutex() }
    val queue = liveQueue.songs
    // What was played on this computer. The account's own history replaces it
    // while signed in — see [remoteHistory] — because that is what Android's
    // History screen is and what makes a phone and a desktop agree.
    var history by remember { mutableStateOf(persistence.history()) }
    var remoteHistory by remember { mutableStateOf<List<Song>>(emptyList()) }
    var likedIds by remember { mutableStateOf(persistence.likedIds()) }
    // In-flight tail of the Liked Music chain; replaced whenever the library is fetched again.
    var likedSyncJob by remember { mutableStateOf<Job?>(null) }
    var dislikedIds by remember { mutableStateOf(persistence.dislikedIds()) }
    val overlays = remember { DesktopOverlays() }
    var downloads by remember { mutableStateOf(persistence.downloads()) }
    var localSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val downloadQueue by DesktopDownloadQueue.active.collectAsState()
    val downloadInProgress = downloadQueue.keys
    var playlists by remember { mutableStateOf(persistence.playlists()) }
    var statsRevision by remember { mutableStateOf(0L) }
    var replayPeriod by remember { mutableStateOf(DesktopReplayPeriod.ALL_TIME) }
    val replayHolder = remember { DesktopAccounts.active()?.name.orEmpty() }
    var replaySummary by remember { mutableStateOf(DesktopListeningStats.summary()) }
    var statsCountedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    // A whole album's or playlist's tracks on their way into a playlist — its title, and them.
    var playlistBatch by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    // The open playlist's "shuffle the saved order", waiting for its confirmation.
    var shuffleOrderAsked by remember { mutableStateOf(false) }
    // A run of likes into Liked Music under way: how many of how many.
    var likeProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // One busy flag and one message for every playlist edit: only one of them can be in flight,
    // because each is driven by a dialog that is modal.
    var playlistBusy by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    // The album and artist pages the playing track belongs to, looked up while
    // the player is open. Whatever started a track knew its title and artwork
    // but rarely its ids — a home-feed tile carries neither — so without this
    // the credit under the title is not a link at all. Android does the same
    // thing, and for the same reason; see its `links` in MainActivity.
    var trackLinks by remember { mutableStateOf<Song?>(null) }
    // Settings is a modal over whatever page is open, the way Music puts its own preferences in a
    // sheet rather than a navigation destination.
    var autoplay by remember { mutableStateOf(persistence.boolean("autoplay", true)) }
    var dontRepeatSuggestions by remember {
        mutableStateOf(persistence.boolean(KEY_DONT_REPEAT_SUGGESTIONS, false))
    }
    /** Everything AutoPlay has played or offered this run — see [KEY_DONT_REPEAT_SUGGESTIONS]. */
    val sessionSongHistory = remember { mutableListOf<Song>() }
    var filterNonMusicAudio by remember {
        mutableStateOf(persistence.boolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, false))
    }
    var localMusicRevision by remember { mutableStateOf(0) }
    // The track the queued station was built around, so a top-up is asked for once per song rather
    // than once per recomposition.
    var autoplaySeed by remember { mutableStateOf<String?>(null) }
    // The jam's answer to a heart or a thumbs-down: a fresh tail for the queue, and for a
    // thumbs-down on what is playing, the next track now. Counters, taken up further down where
    // the queue's own functions are.
    var jamRefill by remember { mutableStateOf(0) }
    var jamSkip by remember { mutableStateOf(0) }
    var autoplayJob by remember { mutableStateOf<Job?>(null) }
    var automix by remember { mutableStateOf(persistence.boolean("automix", false)) }
    // A manual skip blends into the already-prepared next track over ~15s instead of cutting.
    var automixOnSkip by remember { mutableStateOf(persistence.boolean("automix_on_skip", false)) }
    // Apple-style Automix phrase length in bars: 0 = adaptive (default), 8 or 16 = fixed.
    var automixPhraseBars by remember {
        mutableStateOf(persistence.string("automix_phrase_bars", "0").toIntOrNull()?.let { if (it == 8 || it == 16) it else 0 } ?: 0)
    }
    var automixPerformance by remember {
        mutableStateOf(
            runCatching { AutomixPerformanceMode.valueOf(persistence.string("automix_performance", "BALANCED")) }
                .getOrDefault(AutomixPerformanceMode.BALANCED),
        )
    }
    var shuffle by remember { mutableStateOf(persistence.boolean("shuffle", false)) }
    var repeatMode by remember {
        mutableStateOf(
            runCatching { DesktopRepeatMode.valueOf(persistence.string("repeat_mode", "OFF")) }
                .getOrDefault(DesktopRepeatMode.OFF),
        )
    }
    var playbackSpeed by remember { mutableStateOf(persistence.string("playback_speed", "1.0").toFloatOrNull() ?: 1.0f) }
    var volume by remember { mutableStateOf(persistence.string("volume", "1.0").toFloatOrNull()?.coerceIn(0.0f, 1.0f) ?: 1.0f) }
    var crossfadeSeconds by remember { mutableStateOf(persistence.string("crossfade_seconds", "0").toIntOrNull()?.coerceIn(0, 12) ?: 0) }
    var downloadQuality by remember { mutableStateOf(persistence.string("download_quality", "LOSSLESS")) }
    var trayIconEnabled by remember { mutableStateOf(persistence.boolean("tray_icon", true)) }
    var closeToTray by remember { mutableStateOf(persistence.boolean("close_to_tray", true)) }
    // The three the FFmpeg engine unlocked: none of them could exist while JavaFX owned the decode,
    // because none of them can be done without the samples themselves.
    var spatialAudio by remember { mutableStateOf(persistence.boolean("spatial_audio", false)) }
    var dolbyAtmos by remember { mutableStateOf(persistence.boolean("dolby_atmos", true)) }
    var skipSilence by remember { mutableStateOf(persistence.boolean("skip_silence", false)) }
    var outputPrecision by remember { mutableStateOf(persistence.string("output_precision", "PCM_16")) }
    // The blob backdrop the mesh replaced, kept as an opt-out.
    var legacyMeshGradient by remember { mutableStateOf(persistence.boolean("legacy_mesh_gradient", false)) }
    var animatedCanvas by remember { mutableStateOf(persistence.boolean("animated_canvas", true)) }
    var spotifyCanvasCookie by remember { mutableStateOf(DesktopSpotifyToken.cookie()) }
    // Held here rather than by the Integrations page, so leaving the page does not close the
    // sign-in window.
    var spotifySignInJob by remember { mutableStateOf<Job?>(null) }
    var spotifySignInError by remember { mutableStateOf<String?>(null) }
    var showNerdStats by remember { mutableStateOf(persistence.boolean("show_nerd_stats", false)) }
    var syncedLyrics by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, true))
    }
    var lyricsBlur by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_LYRICS_BLUR, true))
    }
    var prioritizeSyllables by remember {
        mutableStateOf(persistence.boolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false))
    }
    var lyricsOrder by remember { mutableStateOf(persistence.lyricsSourceOrder()) }
    var lyricsOn by remember { mutableStateOf(persistence.lyricsEnabledSources()) }
    var lyricsDefault by remember { mutableStateOf(persistence.lyricsDefaultSource()) }
    /** Bumped to look the playing track's lyrics up again after the provider settings change. */
    var lyricsRefresh by remember { mutableStateOf(0) }
    var fullBleedArtwork by remember { mutableStateOf(persistence.boolean("full_bleed_artwork", false)) }
    var accounts by remember { mutableStateOf(DesktopAccounts.accounts()) }
    var activeAccountId by remember { mutableStateOf(DesktopAccounts.activeAccountId()) }
    var activeProfileId by remember { mutableStateOf(DesktopAccounts.activeProfileId()) }
    var signInBusy by remember { mutableStateOf<String?>(null) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var webSignInJob by remember { mutableStateOf<Job?>(null) }
    val activeAccount = accounts.firstOrNull { it.accountId == activeAccountId } ?: accounts.firstOrNull()
    // The account's own library, fetched once a session is in force.
    var libraryState by remember { mutableStateOf<UiState<LibraryPage>>(UiState.Loading) }
    // Whether YouTube is actually accepting the session, which is not the same as having an account
    // saved.
    // Taken as signed in from launch when an account is saved, so the sidebar shows it rather than
    // a sign-in button for the moment the session takes to come back; settled once it has.
    var youtubeSignedIn by remember { mutableStateOf(DesktopAccounts.activeAccountId() != null) }
    // Whether the saved session has been put back into force (or found gone) since launch.
    var sessionSettled by remember { mutableStateOf(false) }
    // Set by an edit that the library feed has not caught up with yet.
    var libraryStale by remember { mutableStateOf(false) }
    // Bumped when the session changes underneath, so the loader below re-runs without anything it
    // is keyed on having to change to something and back.
    var sessionRevision by remember { mutableStateOf(0) }
    // Read off the library rather than fetched again: the Playlists shelf is the same feed the
    // picker wants, minus the entries no edit can be aimed at.
    val accountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.let(DesktopSearchClient::userPlaylists)
            .orEmpty()
    }
    // The add-to-playlist dialog intentionally keeps only playlists owned by the account. The
    // sidebar is a library browser instead, so it shows the complete paged shelf: owned, saved,
    // and YouTube's built-in playlists such as Liked Music.
    val sidebarAccountPlaylists = remember(libraryState) {
        (libraryState as? UiState.Success)?.data?.shelves
            ?.firstOrNull { it.title == PLAYLISTS_SHELF }
            ?.items
            ?.filter { it.browseId != null }
            .orEmpty()
    }

    fun reloadLibrary() {
        libraryState = UiState.Loading
        sessionRevision++
    }

    /** Edits the Playlists shelf in place, without re-fetching it. */
    fun editPlaylistShelf(edit: (List<ShelfItem>) -> List<ShelfItem>) {
        val page = (libraryState as? UiState.Success)?.data ?: return
        val index = page.shelves.indexOfFirst { it.title == PLAYLISTS_SHELF }
        val shelves = if (index >= 0) {
            page.shelves.toMutableList().apply {
                this[index] = this[index].copy(items = edit(this[index].items))
            }
        } else {
            // A fresh account has no Playlists shelf at all, and it is exactly the account most
            // likely to be making its first one.
            val items = edit(emptyList())
            if (items.isEmpty()) return
            listOf(HomeShelf(PLAYLISTS_SHELF, items)) + page.shelves
        }
        libraryState = UiState.Success(page.copy(shelves = shelves.toList()))
    }

    // Whoever was signed in last is signed in again, before anything asks YouTube for something
    // that depends on it.
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { DesktopAccounts.activate() }
        } finally {
            sessionSettled = true
            sessionRevision++
        }
    }
    var audioQuality by remember { mutableStateOf(persistence.audioQuality()) }
    var sourceConfigs by remember { mutableStateOf(persistence.sourceConfigs()) }
    var sourceStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val youtubeSourceEnabled = sourceConfigs.any {
        it.kind == DesktopSourceKind.YOUTUBE && it.enabled
    }
    val sleepTimerMinutes by DesktopSleepTimer.minutes.collectAsState()
    val sleepAfterTrack by DesktopSleepTimer.afterTrack.collectAsState()
    var sleepTimerTick by remember { mutableStateOf(0L) }
    LaunchedEffect(sleepTimerMinutes) {
        while (sleepTimerMinutes != null && currentCoroutineContext().isActive) {
            delay(1_000L)
            sleepTimerTick++
        }
    }
    val sleepRemainingMs = remember(sleepTimerMinutes, sleepTimerTick) {
        DesktopSleepTimer.remainingMs()
    }
    var openedCollection by remember { mutableStateOf<DesktopCollection?>(null) }
    var collectionLoadingMore by remember { mutableStateOf(false) }
    var collectionError by remember { mutableStateOf<String?>(null) }
    var collectionReloads by remember { mutableStateOf(0) }
    // The artist page is its own destination rather than a collection with a different header.
    var openedArtist by remember { mutableStateOf<DesktopArtistTarget?>(null) }
    var artistState by remember { mutableStateOf<UiState<ArtistPage>>(UiState.Loading) }
    var artistReloads by remember { mutableStateOf(0) }

    fun canonicalSong(song: Song): Song {
        val homeSong = (homeState as? UiState.Success)?.data.orEmpty()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filter { it.videoId == song.videoId }
            .map(ShelfItem::toSong)
            .firstOrNull()
        return if (homeSong != null && song.artist.isUnknownArtist()) {
            song.copy(
                artist = homeSong.artist,
                durationText = song.durationText ?: homeSong.durationText,
                thumbnailUrl = song.thumbnailUrl ?: homeSong.thumbnailUrl,
            )
        } else {
            song
        }
    }

    lateinit var startSong: (Song, Boolean) -> Unit
    lateinit var playbackEngine: DesktopPlaybackEngine

    fun saveQueue() {
        // A party queue is temporary. The phone keeps the listener's own queue aside and so does
        // desktop; never let a shared running order overwrite the queue restored after leaving or
        // after a process restart.
        if (DesktopListenTogether.state.value.inParty) return
        persistence.saveQueue(liveQueue.songs)
        persistence.saveString("queue_index", liveQueue.index.toString())
    }

    /** Opens whatever the queue is currently pointing at. */
    fun playCurrent(startPlaying: Boolean = true) {
        val song = liveQueue.current ?: return
        selectedSong = song
        history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
        saveQueue()
        persistence.saveHistory(history)
        playbackEngine.load(song, startPlaying)
        partySyncHolder[0]?.onLocalIntent(song.videoId)
        scope.launch { DesktopScrobbling.updateNowPlaying(song) }
    }

    fun partyTrackChangeBlocked(): Boolean {
        if (!DesktopListenTogether.state.value.controlsLocked) return false
        DesktopPlayerHost.showMessage("Only the party host can change playback")
        return true
    }

    /**
     * The phone's party queue for a track picked here: that track, then what the party's members
     * queued by hand. Never the album around it, and never the last track's AutoPlay — the party
     * shares one running order, and the phone builds it exactly this way.
     */
    fun partyPlaybackQueue(tapped: Song): List<Song> {
        val party = DesktopListenTogether.state.value
        val upcoming = partyUpcomingAfter(party.queue, party.playback, party.playback.track?.videoId)
        return QueueTimeline.buildPartyPlaybackQueue(
            tapped,
            tapped.queueSource(),
            DesktopQueue.adopt(liveQueue.upcoming, upcoming.map { it.toDesktopSong() }),
        )
    }

    /** A song played on its own — from a search row, a shelf card, history. */
    fun playSong(song: Song, startPlaying: Boolean = true, source: DesktopQueueSource? = null) {
        if (partyTrackChangeBlocked()) return
        val tapped = canonicalSong(song).withSource(source)
        val songs = if (DesktopListenTogether.state.value.inParty) {
            partyPlaybackQueue(tapped)
        } else {
            // The phone's one-off queue: the song, then whatever the listener had queued by hand.
            QueueTimeline.buildOneOffQueue(liveQueue.songs, liveQueue.index, tapped, tapped.queueSource())
        }
        liveQueue = DesktopQueue(songs, index = 0)
        preShuffleOrder = emptyList()
        playCurrent(startPlaying)
    }

    /** Opens the Spotify sign-in window for an `sp_dc`, or closes the one already open. */
    fun signInToSpotify() {
        spotifySignInJob?.let {
            it.cancel()
            return
        }
        spotifySignInError = null
        spotifySignInJob = scope.launch {
            try {
                val cookie = DesktopWebSignIn.captureSpotify()
                DesktopSpotifyToken.setCookie(cookie)
                spotifyCanvasCookie = cookie
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                DesktopTrackLog.log("sign-in: Spotify failed: ${failure.message}")
                spotifySignInError = failure.message ?: "Could not complete the Spotify sign-in."
            } finally {
                spotifySignInJob = null
            }
        }
    }

    /** Takes a captured cookie all the way to a saved account. */
    suspend fun signIn(cookie: String, label: String, sourceProfile: String? = null) {
        signInError = null
        signInBusy = label
        try {
            val read = withContext(Dispatchers.IO) { DesktopYouTubeSession.readSessionScope(cookie) }
            if (read.isFailure) {
                signInError = "Could not reach YouTube Music. Check the connection and try again."
                return
            }
            val scope = read.getOrNull()
            if (scope == null) {
                signInError = "$label is not signed in to YouTube Music."
                return
            }
            DesktopYouTubeAuth.adopt(scope)
            // Asked with the session in force, so they describe the account that was just captured
            // rather than nobody in particular.
            val details = DesktopSearchClient.accountMenu().getOrNull()?.let(DesktopAccountParser::account)
            val channels = DesktopSearchClient.accountsList().getOrNull()
                ?.let(DesktopAccountParser::channels)
                .orEmpty()
            // Off the UI thread: saving reaches the platform keyring over D-Bus, and a blocking
            // call to another process is not something to do on the thread that is drawing.
            withContext(Dispatchers.IO) { DesktopAccounts.save(scope.cookie, scope, details, channels, sourceProfile) }
            accounts = withContext(Dispatchers.IO) { DesktopAccounts.accounts() }
            activeAccountId = DesktopAccounts.activeAccountId()
            activeProfileId = DesktopAccounts.activeProfileId()
            overlays.signIn = false
            overlays.accounts = false
        } finally {
            signInBusy = null
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, source: DesktopQueueSource? = null) {
        // A Spotify playlist's rows still being matched to YouTube Music, or with no match, have
        // nothing to play: they stay out of the queue, and the pick moves to the next playable row.
        val resolved = songs.withIndex().filterNot { it.value.isUnresolvedSpotify }
        if (resolved.isEmpty()) return
        if (partyTrackChangeBlocked()) return
        val playable = resolved.map { canonicalSong(it.value).withSource(source) }
        // Past the end still means the last row, as the clamp here always did.
        val at = resolved.indexOfFirst { it.index >= startIndex }.takeIf { it >= 0 } ?: resolved.lastIndex
        if (DesktopListenTogether.state.value.inParty) {
            liveQueue = DesktopQueue(partyPlaybackQueue(playable[at]), index = 0)
            preShuffleOrder = emptyList()
            playCurrent()
            return
        }
        // The phone's context queue: the album up to the pick, the pick, what the listener had
        // queued by hand, then the rest of the album.
        val built = QueueTimeline.buildContextQueue(
            currentTimeline = liveQueue.songs,
            currentIndex = liveQueue.index,
            newContextSongs = playable,
            selectedIndex = at,
            contextSource = playable[at].queueSource(),
        )
        liveQueue = if (shuffle) {
            DesktopQueue.shuffledStartingAt(built.timeline, built.startIndex)
        } else {
            // Keep the complete queue so Previous can navigate to the tracks before the selected one.
            DesktopQueue.startingAt(built.timeline, built.startIndex)
        }
        preShuffleOrder = if (shuffle) built.timeline.map(DesktopQueue::orderKey) else emptyList()
        playCurrent()
    }

    /** Moves within the queue, dropping whatever a forward jump passed over. */
    fun playQueueIndex(target: Int) {
        if (target !in liveQueue.songs.indices) return
        if (partyTrackChangeBlocked()) return
        liveQueue = liveQueue.jumpTo(target)
        playCurrent()
    }

    fun cycleSleepTimer() {
        when {
            sleepAfterTrack -> DesktopSleepTimer.cancel()
            sleepTimerMinutes == null -> DesktopSleepTimer.start(DesktopSleepTimer.presets.first())
            sleepTimerMinutes == DesktopSleepTimer.presets.last() -> DesktopSleepTimer.startAfterTrack()
            else -> {
                val current = DesktopSleepTimer.presets.indexOf(sleepTimerMinutes)
                DesktopSleepTimer.start(DesktopSleepTimer.presets.getOrElse(current + 1) { DesktopSleepTimer.presets.last() })
            }
        }
    }
    startSong = ::playSong

    /**
     * Changes a collection wherever it is held — the page open now and any copy kept for Back —
     * so an edit shows at once rather than after a reload, and Back does not bring the old list.
     */
    fun editCollections(matches: (DesktopCollection) -> Boolean, change: (DesktopCollection) -> DesktopCollection) {
        openedCollection?.takeIf { !it.loading && matches(it) }?.let { openedCollection = change(it) }
        for (i in navHistory.indices) {
            val entry = navHistory[i]
            val held = entry.collection?.takeIf { !it.loading && matches(it) } ?: continue
            navHistory[i] = entry.copy(collection = change(held))
        }
    }

    /** Applies a rating locally and carries it to the account. */
    fun rate(song: Song, status: LikeStatus) {
        val wasLiked = song.videoId in likedIds
        // Liked Music follows the heart straight away: a track un-liked leaves the list on
        // screen, one liked comes in at the top, as YouTube Music lists it.
        if (status == LikeStatus.LIKE && !wasLiked) {
            editCollections({ it.playlistId == LIKED_PLAYLIST_ID }) { page ->
                page.copy(songs = listOf(song.copy(setVideoId = null)) + page.songs.filterNot { it.videoId == song.videoId })
            }
        } else if (status != LikeStatus.LIKE && wasLiked) {
            editCollections({ it.playlistId == LIKED_PLAYLIST_ID }) { page ->
                page.copy(songs = page.songs.filterNot { it.videoId == song.videoId })
            }
        }
        (libraryState as? UiState.Success)?.data?.let { page ->
            val liked = if (status == LikeStatus.LIKE) {
                listOf(song) + page.likedSongs.filterNot { it.videoId == song.videoId }
            } else {
                page.likedSongs.filterNot { it.videoId == song.videoId }
            }
            if (liked != page.likedSongs) libraryState = UiState.Success(page.copy(likedSongs = liked))
        }
        // In a jam the rating also steers it, straight away: a thumbs-down takes the artist out of
        // what is lined up (and the track off, if it is the one playing); a heart grows the next
        // stretch from this track.
        val jamming = liveQueue.current?.radioName == DesktopMyWave.radioName
        if (jamming && (status != LikeStatus.INDIFFERENT || wasLiked)) {
            DesktopMyWave.onRated(song, status)
            val upcoming = liveQueue.songs.drop(liveQueue.index + 1)
            val kept = when (status) {
                LikeStatus.DISLIKE -> upcoming.filterNot { it.radioName == DesktopMyWave.radioName && DesktopMyWave.unwanted(it) }
                // Two already lined up stay — they may be loading — and the rest is re-grown.
                LikeStatus.LIKE -> upcoming.filterIndexed { i, it -> i < JAM_KEEP_AHEAD || it.radioName != DesktopMyWave.radioName || it.queueTier != QueueTier.AUTOPLAY }
                else -> upcoming
            }
            if (kept.size != upcoming.size) {
                liveQueue = liveQueue.copy(songs = liveQueue.songs.take(liveQueue.index + 1) + kept)
                saveQueue()
            }
            if (status != LikeStatus.INDIFFERENT) jamRefill++
            if (status == LikeStatus.DISLIKE && song.videoId == selectedSong?.videoId) jamSkip++
        }
        likedIds = if (status == LikeStatus.LIKE) likedIds + song.videoId else likedIds - song.videoId
        dislikedIds = if (status == LikeStatus.DISLIKE) {
            dislikedIds + song.videoId
        } else {
            dislikedIds - song.videoId
        }
        persistence.saveLikedIds(likedIds)
        persistence.saveDislikedIds(dislikedIds)
        scope.launch {
            DesktopSearchClient.rate(song.videoId, status)
                .onFailure { DesktopTrackLog.log("youtube: rating ${song.videoId} failed: ${it.message}") }
        }
    }

    fun toggleLike(song: Song) =
        rate(song, if (song.videoId in likedIds) LikeStatus.INDIFFERENT else LikeStatus.LIKE)

    fun toggleDislike(song: Song) =
        rate(song, if (song.videoId in dislikedIds) LikeStatus.INDIFFERENT else LikeStatus.DISLIKE)

    /** The origin the live queue is carrying, for anything appended to it. */
    fun currentQueueSource(): DesktopQueueSource? = liveQueue.current?.playbackSource?.let {
        DesktopQueueSource(
            it,
            liveQueue.current?.playbackSourceType ?: PlaybackSourceType.QUEUE,
            liveQueue.current?.playbackSourceId,
        )
    }

    /**
     * Queues a track by hand, into the listener's own section — "Play next" at its head, "Add to
     * queue" at its end, above the album and AutoPlay either way. In a party, held to the same
     * twenty-five upcoming songs the phone and the server allow.
     */
    fun enqueue(song: Song, next: Boolean) {
        val party = DesktopListenTogether.state.value
        if (party.controlsLocked) return
        if (party.inParty && liveQueue.upcoming.size >= DesktopPartySync.MAX_PARTY_UPCOMING_QUEUE) {
            DesktopPlayerHost.showMessage(
                "Queue is full (maximum ${DesktopPartySync.MAX_PARTY_UPCOMING_QUEUE} songs in party)",
            )
            return
        }
        val queued = canonicalSong(song).copy(radioName = liveQueue.current?.radioName)
            .withSource(currentQueueSource())
        liveQueue = liveQueue.enqueue(queued, playNext = next)
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    /** Slots a track in right after the one playing. */
    fun playNext(song: Song) = enqueue(song, next = true)

    /** Puts a track at the end of what the listener queued — not the end of the queue. */
    fun addToQueue(song: Song) = enqueue(song, next = false)

    // Whether My Wave is being put together, for the card's spinner.
    var waveStarting by remember { mutableStateOf(false) }

    /** Plays My Wave — see [DesktopMyWave] — from the account's likes and what was played lately. */
    fun startWave(from: Song? = null) {
        if (waveStarting || partyTrackChangeBlocked()) return
        waveStarting = true
        scope.launch {
            try {
                val liked = (libraryState as? UiState.Success)?.data?.likedSongs.orEmpty()
                val remote = if (youtubeSignedIn) DesktopSearchClient.history().getOrNull().orEmpty() else emptyList()
                // Signed out there is no account to read: what was played here, and failing that
                // the feed's own picks.
                val played = (remote + history).distinctBy { it.videoId }.ifEmpty {
                    (homeState as? UiState.Success)?.data.orEmpty()
                        .flatMap { it.items }.filter { it.videoId != null }.map { it.toSong() }
                }
                // Grown from one playlist, when the jam is set to: the account's or one kept here.
                val fromPlaylist = if (DesktopMyWave.seeds.value == DesktopWaveSeeds.PLAYLIST) {
                    val id = DesktopMyWave.playlist.value?.first
                    when {
                        id == null -> emptyList()
                        id.startsWith(LOCAL_PLAYLIST_PREFIX) -> playlists.firstOrNull { it.id == id.removePrefix(LOCAL_PLAYLIST_PREFIX) }?.songs.orEmpty()
                        else -> DesktopSearchClient.browse(id).getOrNull()?.songs.orEmpty()
                    }
                } else {
                    emptyList()
                }
                val batch = if (from != null) {
                    // A jam from one track: that track first, then what grows from it.
                    val seed = canonicalSong(from)
                    listOf(seed) + DesktopMyWave.startFrom(seed, liked, played).filterNot { it.videoId == seed.videoId }
                } else {
                    DesktopMyWave.start(liked, played, fromPlaylist)
                }
                if (batch.isEmpty()) {
                    DesktopTrackLog.log("wave: nothing to start a wave from")
                    return@launch
                }
                if (partyTrackChangeBlocked()) return@launch
                val station = batch.mapIndexed { i, song ->
                    if (i == 0) canonicalSong(song).asQueueEntry(QueueTier.CONTEXT).copy(radioName = DesktopMyWave.radioName) else song
                }
                liveQueue = DesktopQueue(station, 0)
                preShuffleOrder = emptyList()
                playCurrent()
                DesktopTrackLog.log("wave: started with ${station.size} tracks")
            } finally {
                waveStarting = false
            }
        }
    }

    /** Starts the station YouTube Music builds around one track. */
    fun startRadio(song: Song) {
        if (partyTrackChangeBlocked()) return
        val seed = canonicalSong(song).copy(radioName = song.title)
        scope.launch {
            // AutoPlay's section behind the seed, as the phone queues a station — which is also
            // what lets a party share it: a context tail is never published.
            val related = DesktopAutoplay.tracksFor(listOf(seed), seed, INITIAL_RADIO_TRACKS)
                .getOrNull()
                .orEmpty()
            if (related.isEmpty()) {
                DesktopTrackLog.log("radio: nothing to build a station on for '${song.title}'")
                return@launch
            }
            if (partyTrackChangeBlocked()) return@launch
            if (liveQueue.current?.videoId == seed.videoId) {
                liveQueue = DesktopQueue(listOf(liveQueue.current!!) + related, index = 0)
                saveQueue()
                partySyncHolder[0]?.onLocalIntent()
            } else {
                val station = listOf(seed.asQueueEntry(QueueTier.CONTEXT)) + related
                liveQueue = if (shuffle) DesktopQueue.shuffledStartingAt(station, 0) else DesktopQueue(station, 0)
                preShuffleOrder = if (shuffle) station.map(DesktopQueue::orderKey) else emptyList()
                playCurrent()
            }
            DesktopTrackLog.log("radio: started a station on '${song.title}' with ${related.size} tracks")
        }
    }

    /**
     * Opens an album or playlist in place of whatever page was open. The page it replaces is kept
     * in the history, not underneath: an artist page left standing under an album would be drawn
     * over it, which is how an album opened from an artist once went nowhere.
     */
    fun showCollection(collection: DesktopCollection) {
        openedArtist = null
        overlays.replay = false
        overlays.settingsPage = null
        collectionError = null
        openedCollection = collection
    }

    /**
     * Opens an album or playlist by id straight away, with a loader where the tracks go, the way an
     * artist's page opens — rather than leaving the click with nothing to show until YouTube answers.
     * [fallback] is what the card that was clicked already knew.
     */
    fun openCollection(browseId: String, fallback: BrowseItem? = null) {
        val current = openedCollection
        if (current?.browseId == browseId && !current.loading) {
            showCollection(current)
            return
        }
        showCollection(
            DesktopCollection(
                browseId = browseId,
                title = fallback?.title.orEmpty(),
                subtitle = fallback?.subtitle.orEmpty(),
                thumbnailUrl = fallback?.thumbnailUrl,
                type = fallback?.type?.takeIf { it != BrowseType.OTHER } ?: browseTypeOf(browseId),
                songs = emptyList(),
                loading = true,
            ),
        )
        collectionReloads++
    }

    // Keyed on the id, so a second page opened before the first answered cancels the first rather
    // than racing it; and a loading page restored by Back fetches again.
    LaunchedEffect(openedCollection?.browseId, openedCollection?.loading, collectionReloads) {
        val pending = openedCollection
            ?.takeIf { it.loading && !it.browseId.startsWith(SPOTIFY_PAGE_PREFIX) }
            ?: return@LaunchedEffect
        collectionError = null
        DesktopSearchClient.browse(
            browseId = pending.browseId,
            fallback = BrowseItem(
                browseId = pending.browseId,
                title = pending.title,
                subtitle = pending.subtitle,
                thumbnailUrl = pending.thumbnailUrl,
                type = pending.type,
            ),
        ).fold(
            onSuccess = { loaded ->
                if (openedCollection?.browseId == pending.browseId) openedCollection = loaded
            },
            onFailure = { collectionError = it.message ?: "Could not open ${pending.title.ifBlank { "this page" }}" },
        )
    }

    // A Spotify playlist: its tracks, then each one's YouTube Music match. Keyed like the browse
    // above, so opening another page cancels it, and Back to a page left half matched (or Retry
    // on one that failed) reads it again.
    LaunchedEffect(openedCollection?.browseId, collectionReloads) {
        val page = openedCollection?.takeIf { it.browseId.startsWith(SPOTIFY_PAGE_PREFIX) }
            ?: return@LaunchedEffect
        if (!page.loading && page.songs.none { it.isMatchPending }) return@LaunchedEffect
        collectionError = null
        fun update(change: (DesktopCollection) -> DesktopCollection) {
            openedCollection?.takeIf { it.browseId == page.browseId }?.let { openedCollection = change(it) }
        }
        runCatching { loadSpotifyPlaylist(page.browseId.removePrefix(SPOTIFY_PAGE_PREFIX), ::update) }
            .onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                DesktopTrackLog.log("spotify: playlist ${page.title} failed: ${failure.message}")
                collectionError = failure.message ?: "Could not open ${page.title}"
                // The error page stands in for the list only while there is no list.
                update { if (it.songs.isEmpty()) it.copy(loading = true) else it }
            }
    }

    fun openAlbum(browseId: String) = openCollection(browseId)

    /** Copies the track's YouTube Music link. */
    fun shareSong(song: Song) {
        DesktopExternalLinks.copy("https://music.youtube.com/watch?v=${song.videoId}")
        DesktopTrackLog.log("copied a link to '${song.title}'")
    }

    /**
     * The link YouTube Music's own overflow shares for a release, built from the browse id rather
     * than fetched — nothing about it depends on the tracks or the account. Albums and playlists
     * only: an artist's is a channel link, not a release.
     */
    fun shareCollection(collection: DesktopCollection) {
        val id = collection.browseId
        val url = when (collection.type) {
            BrowseType.PLAYLIST -> "https://music.youtube.com/playlist?list=${id.removePrefix("VL")}"
            BrowseType.ALBUM -> "https://music.youtube.com/browse/$id"
            else -> return
        }
        DesktopExternalLinks.copy(url)
        DesktopTrackLog.log("copied a link to '${collection.title}'")
    }

    fun downloadSong(song: Song) {
        // A second tap on something already queued or running calls it off.
        if (song.videoId in downloadInProgress) {
            DesktopDownloadQueue.cancel(song.videoId)
            return
        }
        if (song.videoId in downloads.map(Song::videoId) || song.localPath != null) return
        DesktopDownloadQueue.enqueue(song)
    }

    /** Deletes the file a download saved and forgets the record. */
    fun removeDownload(song: Song) {
        scope.launch {
            withContext(Dispatchers.IO) { DesktopDownloadManager.delete(song) }
            downloads = downloads.filterNot { it.videoId == song.videoId }
            persistence.saveDownloads(downloads)
        }
    }

    /** Everything on a page, in the order it is listed. */
    fun downloadAll(songs: List<Song>) {
        val have = downloads.map(Song::videoId).toSet()
        DesktopDownloadQueue.enqueueAll(
            songs.filterNot { it.videoId in have || it.localPath != null || it.isUnresolvedSpotify },
        )
    }

    /** Keeps a station queued ahead of whatever is playing. */

    /**
     * How many AutoPlay tracks the party already has waiting after [videoId], or null outside a
     * party or while its queue does not hold that track yet.
     */
    fun partyAutoplayWaiting(party: DesktopListenTogether.State, videoId: String): Int? {
        if (!party.inParty) return null
        if (partyQueueIndexOf(party.queue, party.playback, videoId) < 0) return null
        return partyUpcomingAfter(party.queue, party.playback, videoId).count { it.fromAutoplay }
    }

    /**
     * Sends [suggestions] to the party and waits for them to come back; the phone's
     * `topUpPartyAutoplay`.
     *
     * Serialised, and the send-and-wait not cancellable, because a track change cancels the load in
     * flight and starts another: the second used to read the party before the first batch had
     * echoed, find nothing waiting, and send the same station again — every suggestion twice, on
     * every device. Holding the lock until the echo means the next top-up reads a queue that
     * already has these in it, and filters against it.
     */
    suspend fun topUpPartyAutoplay(currentId: String, suggestions: List<Song>): Boolean =
        partyAutoplayLock.withLock {
            withContext(NonCancellable) {
                val party = DesktopListenTogether.state.value
                val waiting = partyUpcomingAfter(party.queue, party.playback, currentId)
                val waitingIds = waiting.mapTo(HashSet()) { it.videoId }
                val room = MAX_QUEUED_AUTOPLAY - waiting.count { it.fromAutoplay }
                val fresh = suggestions.filterNot { it.videoId in waitingIds }.take(room.coerceAtLeast(0))
                if (fresh.isEmpty()) return@withContext true
                DesktopListenTogether.queueAdd(fresh.map { it.toPartyTrack(0L) })
                val added = fresh.first().videoId
                withTimeoutOrNull(PARTY_AUTOPLAY_ECHO_TIMEOUT_MS) {
                    DesktopListenTogether.state.first { state -> state.queue.items.any { it.videoId == added } }
                } != null
            }
        }

    fun loadAutoplaySongs(playFirst: Boolean = false) {
        val current = selectedSong ?: return
        val party = DesktopListenTogether.state.value
        if (!DesktopAutoplay.enabled(party, autoplay) ||
            repeatMode == DesktopRepeatMode.ALL ||
            (party.inParty && DesktopAutoplay.supplierId(party) != party.you?.memberId)
        ) return

        if (dontRepeatSuggestions) sessionSongHistory += current

        // In a party the server's queue is the one being topped up, and this computer's copy of it
        // lags by however long reconcile is held off. Counted from the copy, a batch that had
        // already landed read as missing and the same station went out again.
        val queued = partyAutoplayWaiting(party, current.videoId)
            ?: liveQueue.songs.drop(liveQueue.index + 1).count { it.fromAutoplay }
        val needed = MAX_QUEUED_AUTOPLAY - queued
        if (needed <= 0 && !playFirst) return
        // One request per seed.
        if (!playFirst && autoplaySeed == current.videoId) return
        autoplaySeed = current.videoId

        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            var remaining = needed.coerceAtLeast(1)
            var emptyRefreshesRemaining = 1
            while (isActive) {
                DesktopTrackLog.log("autoplay: building a station from '${current.title}'")
                val at = liveQueue.songs.size
                // What the queue holds — the party's too — plus everything this session has
                // already offered.
                val partyQueue = DesktopListenTogether.state.value
                    .takeIf { it.inParty }?.queue?.items.orEmpty().map { it.toDesktopSong() }
                val suggestions = DesktopAutoplay.tracksFor(
                    existing = if (dontRepeatSuggestions) {
                        liveQueue.songs + partyQueue + sessionSongHistory
                    } else {
                        liveQueue.songs + partyQueue
                    },
                    seedSong = current,
                    limit = remaining,
                ).getOrElse { failure ->
                    DesktopTrackLog.log("autoplay: could not build a station: ${failure.message}")
                    emptyList()
                }
                // The listener may have moved on while the station was being fetched; appending
                // then would attach it to the wrong seed.
                val latestParty = DesktopListenTogether.state.value
                if (selectedSong?.videoId != current.videoId ||
                    !DesktopAutoplay.enabled(latestParty, autoplay) ||
                    (latestParty.inParty && DesktopAutoplay.supplierId(latestParty) != latestParty.you?.memberId)
                ) return@launch
                if (suggestions.isEmpty()) {
                    DesktopTrackLog.log("autoplay: no station came back for '${current.title}'")
                    // Match the phone's bounded recovery: retry once after a short delay, but only
                    // while the current track genuinely has no tail to play next.
                    if (liveQueue.index < 0 || liveQueue.index != liveQueue.songs.lastIndex) return@launch
                    if (emptyRefreshesRemaining-- <= 0) return@launch
                    delay(AUTOPLAY_EMPTY_REFRESH_DELAY_MS)
                    remaining = MAX_QUEUED_AUTOPLAY
                    continue
                }
                // The station carries on from what was playing, so it keeps that queue's origin —
                // Android holds the source on every queue item, not just the ones picked by hand.
                val inherited = current.playbackSource?.let {
                    DesktopQueueSource(it, current.playbackSourceType ?: PlaybackSourceType.QUEUE, current.playbackSourceId)
                }
                val queuedSuggestions = suggestions.map { it.withSource(inherited) }
                if (latestParty.inParty) {
                    // Match the phone: the server owns the party queue. Do not mutate this one
                    // device first; queueAdd is echoed back and all members apply it atomically.
                    val landed = topUpPartyAutoplay(current.videoId, queuedSuggestions)
                    if (!landed) {
                        autoplaySeed = null
                        DesktopTrackLog.log("autoplay: party refused the queue top-up; retry is armed")
                        return@launch
                    }
                    if (playFirst) DesktopListenTogether.next()
                } else {
                    liveQueue = liveQueue.append(queuedSuggestions)
                    saveQueue()
                    // The mix continues from where it was added rather than starting a queue of
                    // its own.
                    if (playFirst) playQueueIndex(at)
                }
                if (dontRepeatSuggestions) sessionSongHistory += suggestions
                DesktopTrackLog.log(
                    "autoplay: queued ${suggestions.size} after '${current.title}'",
                )
                return@launch
            }
        }
    }

    /**
     * Turning AutoPlay on or off, and what that does to the queue.
     *
     * Android runs one path for this — `toggleAutoplayFromNotification` — whether the switch was
     * thrown in the player or anywhere else, and it does more than set a flag. Off drops the
     * suggestions it had already queued, because switching it off is the listener saying they do
     * not want them. On clears the seed first, so the station is fetched again for the track
     * already playing rather than being refused as one this seed has been loaded for.
     */
    fun setAutoplay(enabled: Boolean) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        autoplay = enabled
        persistence.saveBoolean("autoplay", enabled)
        autoplayJob?.cancel()
        autoplayJob = null
        autoplaySeed = null
        if (DesktopListenTogether.state.value.inParty) {
            // As on the phone, the server owns the effective setting and queue while connected.
            // Keep the personal preference for after leaving, but do not trim or top up this one
            // device ahead of the server's state broadcast.
            DesktopListenTogether.setAutoplay(enabled)
            return
        }
        if (enabled) {
            loadAutoplaySongs()
        } else {
            val trimmed = liveQueue.withoutAutoplay()
            if (trimmed !== liveQueue) {
                liveQueue = trimmed
                saveQueue()
            }
        }
    }

    fun playNext() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        // Skipped in its first seconds, in a jam: the jam takes that as a word on the artist.
        selectedSong?.takeIf { it.radioName == DesktopMyWave.radioName }?.let { song ->
            val state = playbackEngine.state.value
            if (state.positionMs in 1 until JAM_EARLY_SKIP_MS && state.durationMs > JAM_EARLY_SKIP_MS * 2) DesktopMyWave.onSkipped(song)
        }
        // Automix on skip: when enabled and the next deck is already prepared, blend into it over a
        // short crossfade instead of cutting. The blend's own hand-off advances the queue, so a
        // successful mix returns here without touching liveQueue; anything else falls through to the
        // ordinary skip below.
        if (automixOnSkip && repeatMode != DesktopRepeatMode.ONE) {
            val currentId = selectedSong?.videoId
            val upcoming = currentId?.let {
                liveQueue.followingFor(it, repeatAll = repeatMode == DesktopRepeatMode.ALL)
            }
            if (upcoming != null && playbackEngine.beginSkipMix(upcoming.videoId, SKIP_MIX_ON_SKIP_SECONDS)) {
                return
            }
        }
        when {
            liveQueue.hasNext -> {
                liveQueue = liveQueue.next()
                playCurrent()
            }
            repeatMode == DesktopRepeatMode.ALL && liveQueue.songs.isNotEmpty() -> {
                liveQueue = liveQueue.copy(index = 0)
                playCurrent()
            }
            DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay) ->
                loadAutoplaySongs(playFirst = true)
        }
    }

    LaunchedEffect(jamRefill) {
        if (jamRefill == 0) return@LaunchedEffect
        // A new tail, grown with what was just said: the one-request-per-seed guard let go.
        autoplaySeed = null
        loadAutoplaySongs()
    }
    LaunchedEffect(jamSkip) {
        if (jamSkip > 0) playNext()
    }

    fun playPrevious() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val positionMs = playbackEngine.state.value.positionMs

        // After 10 seconds, Previous restarts the current song.
        if (positionMs > BACK_RESTARTS_AFTER_MS) {
            playbackEngine.seekTo(0L)
            partySyncHolder[0]?.onLocalIntent()
            return
        }

        // Within 10 seconds, Previous goes to the previous song in queue.
        if (!liveQueue.hasPrevious) return
        liveQueue = liveQueue.previous()
        playCurrent()
    }

    /**
     * Shuffle rearranges the queue rather than switching playback to a hidden random order, so what
     * the queue shows stays what plays.
     */
    fun setShuffle(enabled: Boolean) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        shuffle = enabled
        persistence.saveBoolean("shuffle", enabled)
        liveQueue = if (enabled) {
            preShuffleOrder = liveQueue.orderKeys()
            liveQueue.shuffledAhead()
        } else {
            liveQueue.inOrderOf(preShuffleOrder).also { preShuffleOrder = emptyList() }
        }
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    fun closePlaylistDialogs() {
        overlays.playlistDialog = false
        playlistTarget = null
        playlistBatch = null
        shuffleOrderAsked = false
        overlays.rename = false
        overlays.delete = false
        overlays.reorder = false
        playlistError = null
        playlistBusy = false
    }

    /** Creates a playlist — on the account when there is one, otherwise here. */
    fun createPlaylist(title: String, privacy: PlaylistPrivacy) {
        val name = title.trim()
        if (name.isBlank()) return
        val seeds = playlistBatch?.second ?: listOfNotNull(playlistTarget)
        val seed = seeds.firstOrNull()
        // A track from another source cannot go in an account playlist, so the playlist made to
        // hold it is one kept here.
        if (!DesktopYouTubeAuth.isSignedIn || (seeds.isNotEmpty() && seeds.none { DesktopSearchClient.isVideoId(it.videoId) })) {
            playlists = playlists + DesktopPlaylist(title = name, songs = seeds.distinctBy(Song::videoId))
            persistence.savePlaylists(playlists)
            closePlaylistDialogs()
            return
        }
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.createPlaylist(
                name,
                privacy,
                seeds.map(Song::videoId).filter(DesktopSearchClient::isVideoId).distinct(),
            )
                .onSuccess { playlistId ->
                    closePlaylistDialogs()
                    editPlaylistShelf { items ->
                        val card = ShelfItem(
                            title = name,
                            // Only what this request itself establishes: an unseeded playlist gets
                            // a card of just its name rather than a guess at what the feed will
                            // call it.
                            subtitle = if (seeds.isNotEmpty()) "${seeds.size} songs" else "",
                            thumbnailUrl = seed?.thumbnailUrl,
                            videoId = null,
                            browseId = "VL$playlistId",
                        )
                        // Leads the shelf because it is the newest, which is the order the feed
                        // itself comes in.
                        listOf(card) + items.filterNot { it.browseId == card.browseId }
                    }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not create the playlist"
                }
        }
    }

    fun openPlaylist(playlist: DesktopPlaylist) {
        showCollection(
            DesktopCollection(
                browseId = playlist.id,
                title = playlist.title,
                subtitle = "Playlist • ${playlist.songs.size} songs",
                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                type = BrowseType.PLAYLIST,
                songs = playlist.songs,
            ),
        )
    }

    /**
     * Keeps an imported Spotify playlist where the phone does: on the account when signed in,
     * otherwise on this computer, which is also the fallback if the account refuses it. Returns a
     * note only for that fallback.
     */
    suspend fun importSpotifyPlaylist(title: String, songs: List<Song>): String? {
        val signedIn = DesktopYouTubeAuth.isSignedIn
        if (signedIn) {
            val created = DesktopSearchClient.createPlaylist(title, PlaylistPrivacy.PRIVATE, songs.map(Song::videoId))
            created.getOrNull()?.let { playlistId ->
                editPlaylistShelf { items ->
                    val card = ShelfItem(
                        title = title,
                        subtitle = "${songs.size} songs",
                        thumbnailUrl = songs.firstOrNull()?.thumbnailUrl,
                        videoId = null,
                        browseId = "VL$playlistId",
                    )
                    listOf(card) + items.filterNot { it.browseId == card.browseId }
                }
                libraryStale = true
                openCollection("VL$playlistId")
                return null
            }
            DesktopTrackLog.log("spotify import: account playlist failed: ${created.exceptionOrNull()?.message}")
        }
        val playlist = DesktopPlaylist(title = title, songs = songs)
        playlists = playlists + playlist
        persistence.savePlaylists(playlists)
        openPlaylist(playlist)
        return if (signedIn) {
            DesktopStrings.format(
                "spotify_import_local_fallback",
                title,
                fallback = "Couldn't create it on YouTube Music, so '%1\$s' was saved on this device instead.",
            )
        } else {
            null
        }
    }

    fun addToPlaylist(playlist: DesktopPlaylist) {
        val adding = playlistBatch?.second ?: listOfNotNull(playlistTarget)
        if (adding.isEmpty()) return
        playlists = playlists.map { current ->
            if (current.id == playlist.id) current.copy(songs = (current.songs + adding).distinctBy(Song::videoId)) else current
        }
        playlistBatch = null
        persistence.savePlaylists(playlists)
        playlistTarget = null
    }

    /**
     * Every track of [songs] that [playlist] does not have yet, added in batches YouTube takes in
     * one go — the "merge" of one list into another. What was already there is left as it is
     * rather than added twice.
     */
    fun addAllToAccountPlaylist(playlist: UserPlaylist, songs: List<Song>) {
        playlistBusy = true
        playlistError = null
        scope.launch {
            val result = runCatching {
                val present = DesktopSearchClient.playlistEntries("VL${playlist.playlistId}")
                    .getOrDefault(emptyList())
                    .mapTo(HashSet(), Song::videoId)
                val adding = songs
                    .filter { DesktopSearchClient.isVideoId(it.videoId) && it.videoId !in present }
                    .distinctBy(Song::videoId)
                val entries = HashMap<String, String>()
                adding.chunked(PLAYLIST_ADD_BATCH).forEach { chunk ->
                    entries += DesktopSearchClient.addToPlaylist(playlist.playlistId, chunk.map(Song::videoId)).getOrThrow()
                }
                adding.map { it.copy(setVideoId = entries[it.videoId]) }
            }
            result.onSuccess { added ->
                DesktopTrackLog.log("playlist: ${added.size} of ${songs.size} added to ${playlist.title}")
                editCollections({ it.playlistId == playlist.playlistId }) { page ->
                    if (page.continuation != null) page else page.copy(songs = page.songs + added)
                }
                libraryStale = true
                closePlaylistDialogs()
            }.onFailure {
                DesktopTrackLog.log("playlist: adding ${songs.size} to ${playlist.title} failed: ${it.message}")
                playlistBusy = false
                playlistError = it.message ?: "Could not add to ${playlist.title}"
            }
        }
    }

    fun addToAccountPlaylist(playlist: UserPlaylist) {
        playlistBatch?.let { (_, songs) ->
            addAllToAccountPlaylist(playlist, songs)
            return
        }
        val song = playlistTarget ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.addToPlaylist(playlist.playlistId, listOf(song.videoId))
                .onSuccess { added ->
                    // At the end of the playlist on screen (or kept for Back), with the entry id
                    // it was given, so it can be taken out again or moved without a reload.
                    editCollections({ it.playlistId == playlist.playlistId }) { page ->
                        if (page.continuation != null || page.songs.any { it.videoId == song.videoId }) {
                            page
                        } else {
                            page.copy(songs = page.songs + song.copy(setVideoId = added[song.videoId]))
                        }
                    }
                    libraryStale = true
                    closePlaylistDialogs()
                }
                .onFailure {
                    DesktopTrackLog.log("playlist: could not add ${song.videoId}: ${it.message}")
                    playlistBusy = false
                    playlistError = it.message ?: "Could not add to ${playlist.title}"
                }
        }
    }

    /**
     * Into Liked Music, which YouTube fills only with likes: every track of the batch (or the one
     * being added) that is not liked yet is liked, one after another so YouTube takes them all,
     * shown on the page at once and counted in the dialog as they go.
     */
    fun addToLiked() {
        val songs = (playlistBatch?.second ?: listOfNotNull(playlistTarget))
            .filter { DesktopSearchClient.isVideoId(it.videoId) && it.videoId !in likedIds }
            .distinctBy(Song::videoId)
        if (songs.isEmpty()) {
            closePlaylistDialogs()
            return
        }
        playlistBusy = true
        playlistError = null
        likeProgress = 0 to songs.size
        // On the page straight away; the account follows request by request.
        likedIds = likedIds + songs.map(Song::videoId)
        dislikedIds = dislikedIds - songs.mapTo(HashSet(), Song::videoId)
        persistence.saveLikedIds(likedIds)
        persistence.saveDislikedIds(dislikedIds)
        editCollections({ it.playlistId == LIKED_PLAYLIST_ID }) { page ->
            page.copy(songs = songs.reversed().map { it.copy(setVideoId = null) } + page.songs.filterNot { old -> songs.any { it.videoId == old.videoId } })
        }
        (libraryState as? UiState.Success)?.data?.let { page ->
            libraryState = UiState.Success(page.copy(likedSongs = songs.reversed() + page.likedSongs.filterNot { old -> songs.any { it.videoId == old.videoId } }))
        }
        scope.launch {
            var failed = 0
            songs.forEachIndexed { i, song ->
                DesktopSearchClient.rate(song.videoId, LikeStatus.LIKE).onFailure {
                    failed++
                    DesktopTrackLog.log("liked: ${song.videoId} failed: ${it.message}")
                }
                likeProgress = (i + 1) to songs.size
                if (songs.size > 1) delay(LIKE_RUN_SPACING_MS)
            }
            DesktopTrackLog.log("liked: ${songs.size - failed} of ${songs.size} added to Liked Music")
            likeProgress = null
            libraryStale = true
            if (failed == 0) {
                closePlaylistDialogs()
            } else {
                playlistBusy = false
                playlistError = DesktopStrings["d_liking_failed", "%1\$d of %2\$d could not be liked"].format(failed, songs.size)
            }
        }
    }

    /** The open album's or playlist's every track — all its pages, not just those shown. */
    suspend fun allSongsOf(collection: DesktopCollection): List<Song> =
        if (collection.continuation != null && collection.type == BrowseType.PLAYLIST) {
            DesktopSearchClient.playlistEntries(collection.browseId).getOrDefault(collection.songs)
        } else {
            collection.songs
        }

    /** Opens the playlist picker for everything in [collection]. */
    fun addCollectionToPlaylist(collection: DesktopCollection) {
        scope.launch {
            playlistBusy = true
            val songs = allSongsOf(collection)
            playlistBusy = false
            if (songs.isNotEmpty()) playlistBatch = collection.title to songs
        }
    }

    /** The open playlist's own order, shuffled and saved to the account. */
    fun shuffleOpenPlaylistOrder() {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.playlistEntries(collection.browseId)
                .mapCatching { entries ->
                    val shuffled = entries.shuffled()
                    DesktopSearchClient.reorderPlaylist(
                        collection.playlistId,
                        current = entries.mapNotNull { it.setVideoId },
                        target = shuffled.mapNotNull { it.setVideoId },
                    ).getOrThrow()
                    shuffled
                }
                .onSuccess { shuffled ->
                    editCollections({ it.browseId == collection.browseId }) { it.copy(songs = shuffled, continuation = null) }
                    libraryStale = true
                    closePlaylistDialogs()
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not shuffle the playlist"
                }
        }
    }

    playbackEngine = remember {
        DesktopPlaybackEngine(
            onEnded = {
                selectedSong?.let(DesktopScrobbling::onPlaybackEnded)
                selectedSong?.takeIf { it.radioName == DesktopMyWave.radioName }?.let(DesktopMyWave::onFinished)
                if (DesktopSleepTimer.afterTrack.value) {
                    DesktopSleepTimer.cancel()
                } else if (repeatMode == DesktopRepeatMode.ONE) {
                    // Replayed in place. [startSong] makes the track a queue of one, which threw
                    // the rest of the queue away the first time it repeated.
                    val song = selectedSong
                    if (song != null && liveQueue.current?.videoId == song.videoId) {
                        playCurrent()
                    } else {
                        song?.let { startSong(it, true) }
                    }
                } else {
                    playNext()
                }
            },
            onCrossfaded = { song ->
                scope.launch {
                    // The queue moves with the audio. Only the selection used to, which left the
                    // queue on the track that had just finished — so Next "advanced" onto the song
                    // already playing, and every later skip landed one behind.
                    liveQueue = liveQueue.afterHandoffTo(song.videoId)
                    saveQueue()
                    selectedSong = song
                    history = (listOf(song) + history.filterNot { it.videoId == song.videoId }).take(50)
                    persistence.saveHistory(history)
                    DesktopScrobbling.updateNowPlaying(song)
                    partySyncHolder[0]?.onLocalIntent()
                }
            },
        )
    }
    val partySync = remember(playbackEngine) {
        DesktopPartySync(
            scope = scope,
            engine = playbackEngine,
            playTrack = { shared, at ->
                // The phone's PartySync.load: the party's running order, standing on its track —
                // so the queue on screen moves with the song rather than keeping the old one as
                // "now playing" and the new one as the first thing still to come.
                val songs = DesktopQueue.adopt(liveQueue.songs, shared.map { it.toDesktopSong() })
                liveQueue = DesktopQueue(songs, at.coerceIn(songs.indices)).trimmed()
                liveQueue.current?.let { song ->
                    selectedSong = song
                    playbackEngine.load(song, playWhenReady = false)
                }
            },
            localQueue = { liveQueue.songs to liveQueue.index },
            applyPartyUpcoming = { upcoming ->
                val aligned = liveQueue.withPartyUpcoming(upcoming.map { it.toDesktopSong() })
                if (aligned !== liveQueue) liveQueue = aligned
            },
            applyPartyAutoplay = { _ ->
                // The party setting overrides the personal preference only while connected. The
                // phone deliberately does not persist it or delete queue rows on this device.
                autoplayJob?.cancel()
                autoplayJob = null
                autoplaySeed = null
            },
            onEnteredParty = {
                if (personalQueueStash == null) {
                    personalQueueStash = liveQueue
                    personalPositionStash = playbackEngine.state.value.positionMs
                    personalPlayingStash = playbackEngine.state.value.isPlaying
                }
            },
            onLeftParty = {
                personalQueueStash?.let { stashed ->
                    liveQueue = stashed
                    selectedSong = stashed.current
                    stashed.current?.let {
                        playbackEngine.load(it, personalPlayingStash, personalPositionStash)
                    }
                    saveQueue()
                }
                personalQueueStash = null
            },
        )
    }
    DisposableEffect(partySync) {
        partySyncHolder[0] = partySync
        partySync.start()
        onDispose {
            partySync.stop()
            if (partySyncHolder[0] === partySync) partySyncHolder[0] = null
        }
    }
    fun togglePlayPauseFromUser() {
        if (partySync.handleLockedPlayPause()) return
        playbackEngine.togglePlayPause()
        partySync.onLocalIntent()
    }
    fun playFromUser() {
        if (DesktopListenTogether.state.value.controlsLocked) {
            if (!playbackEngine.state.value.isPlaying) partySync.handleLockedPlayPause()
            return
        }
        playbackEngine.play()
        partySync.onLocalIntent()
    }
    fun pauseFromUser() {
        if (DesktopListenTogether.state.value.controlsLocked) {
            if (playbackEngine.state.value.isPlaying) partySync.handleLockedPlayPause()
            return
        }
        playbackEngine.pause()
        partySync.onLocalIntent()
    }
    val mprisController = remember(playbackEngine) {
        DesktopMprisController(
            onPlay = ::playFromUser,
            onPause = ::pauseFromUser,
            onPlayPause = ::togglePlayPauseFromUser,
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onShuffleChanged = { enabled ->
                shuffle = enabled
                persistence.saveBoolean("shuffle", enabled)
            },
            onLoopStatusChanged = { status ->
                repeatMode = when (status) {
                    "Track" -> DesktopRepeatMode.ONE
                    "Playlist" -> DesktopRepeatMode.ALL
                    else -> DesktopRepeatMode.OFF
                }
                persistence.saveString("repeat_mode", repeatMode.name)
            },
            onRateChanged = { value ->
                playbackSpeed = value.toFloat().coerceIn(0.25f, 3.0f)
                persistence.saveString("playback_speed", playbackSpeed.toString())
            },
            onVolumeChanged = { value ->
                volume = value.toFloat().coerceIn(0.0f, 1.0f)
                persistence.saveString("volume", volume.toString())
            },
            onSeek = { positionMs ->
                if (!DesktopListenTogether.state.value.controlsLocked) {
                    playbackEngine.seekTo(positionMs)
                    partySync.onLocalIntent()
                }
            },
        )
    }
    DisposableEffect(mprisController) {
        mprisController.start()
        onDispose { mprisController.stop() }
    }
    // The Windows half of the same idea. Linux has MPRIS above; on Windows this
    // is what the media keys and the volume flyout's card talk to.
    DisposableEffect(playbackEngine) {
        DesktopWindowsMedia.start(
            DesktopWindowsMedia.Controller(
                onPlay = ::playFromUser,
                onPause = ::pauseFromUser,
                onNext = ::playNext,
                onPrevious = ::playPrevious,
                onStop = ::pauseFromUser,
                onSeek = { positionMs ->
                    if (!DesktopListenTogether.state.value.controlsLocked) {
                        playbackEngine.seekTo(positionMs)
                        partySync.onLocalIntent()
                    }
                },
            ),
        )
        onDispose { DesktopWindowsMedia.stop() }
    }

    LaunchedEffect(playbackSpeed) { playbackEngine.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(volume) { playbackEngine.setVolume(volume) }
    LaunchedEffect(audioQuality) { playbackEngine.setAudioQuality(audioQuality.name) }
    LaunchedEffect(automixPerformance) {
        playbackEngine.setAutomixPerformance(automixPerformance)
    }
    // Every one of the seven settings renders the same curve, so they are collected together
    // rather than destructured — seven sources of one tuning is seven chances to read them in the
    // wrong order.
    val eqEnabled by DesktopEqualizerSettings.enabled.collectAsState()
    val eqMode by DesktopEqualizerSettings.mode.collectAsState()
    val eqToneX by DesktopEqualizerSettings.toneX.collectAsState()
    val eqToneY by DesktopEqualizerSettings.toneY.collectAsState()
    val eqFocused by DesktopEqualizerSettings.focused.collectAsState()
    val eqBalance by DesktopEqualizerSettings.balance.collectAsState()
    val eqBands by DesktopEqualizerSettings.bands.collectAsState()
    LaunchedEffect(eqEnabled, eqMode, eqToneX, eqToneY, eqFocused, eqBalance, eqBands) {
        playbackEngine.setEqualizer(eqEnabled, DesktopEqualizerSettings.curve(), eqBalance)
    }
    LaunchedEffect(spatialAudio, skipSilence, outputPrecision) {
        playbackEngine.setSpatialAudio(spatialAudio)
        playbackEngine.setSkipSilence(skipSilence)
        playbackEngine.setOutputPrecision(outputPrecision)
    }
    // Not while listening together, exactly as on the phone: a blend starts the next track early,
    // by a length this computer decides from its own copy of the audio, so every member would begin
    // the next song at a different moment and be dragged back by a correcting seek. The transition a
    // party shares is the plain one. The settings themselves are left alone and come back after.
    val inParty = partyState.inParty
    LaunchedEffect(automix, automixOnSkip, automixPhraseBars, crossfadeSeconds, inParty, audioQuality, selectedSong?.videoId, queue, liveQueue.index, shuffle, repeatMode) {
        playbackEngine.setAutomixEnabled(automix && !inParty)
        playbackEngine.setAutomixPhraseBars(if (automix && !inParty) automixPhraseBars else 0)
        playbackEngine.setCrossfadeSeconds(if (inParty) 0 else crossfadeSeconds)
        // A party mixes only with the plain transition, so a skip there cuts like it always did.
        playbackEngine.setSkipMixEnabled(automixOnSkip && !inParty)
        // Exactly what Next would play, so a crossfade can never blend into anything else. Shuffle
        // is already the queue's order; picking "the first track that is not this one" under it
        // blended into a song from history whenever the current one was not at the top.
        // Repeat-one prepares nothing: the track ends and [onEnded] starts it again, rather than
        // blending into the next one.
        val next = selectedSong
            ?.takeIf { repeatMode != DesktopRepeatMode.ONE }
            ?.let { liveQueue.followingFor(it.videoId, repeatAll = repeatMode == DesktopRepeatMode.ALL) }
        playbackEngine.prepareNext(next)
    }
    // Topped up on every track change, the way Android does it, rather than only once the queue has
    // run dry.
    LaunchedEffect(selectedSong?.videoId, autoplay, repeatMode) {
        loadAutoplaySongs()
    }
    val partyAutoplaySupplier = DesktopAutoplay.supplierId(partyState)
    LaunchedEffect(
        partyState.code,
        partyState.playback.autoplayEnabled,
        partyAutoplaySupplier,
    ) {
        if (partyState.inParty &&
            partyState.playback.autoplayEnabled &&
            partyAutoplaySupplier == partyState.you?.memberId
        ) {
            autoplayJob?.cancel()
            autoplayJob = null
            autoplaySeed = null
            loadAutoplaySongs()
        }
    }
    LaunchedEffect(liveQueue.songs.size, liveQueue.index, partyState.queue.seq) {
        val currentId = selectedSong?.videoId
        val shouldRefresh = DesktopAutoplay.queueNeedsRefresh(
            enabled = DesktopAutoplay.enabled(partyState, autoplay),
            repeatAll = repeatMode == DesktopRepeatMode.ALL,
            currentIndex = liveQueue.index,
            itemCount = liveQueue.songs.size,
            loadInProgress = autoplayJob?.isActive == true,
        )
        // Only re-arm a seed that has already been attempted. The track-change effect above owns
        // the initial load; this effect owns later local or server queue edits that expose a tail.
        if (shouldRefresh && currentId != null && autoplaySeed == currentId) {
            autoplayJob = null
            autoplaySeed = null
            loadAutoplaySongs()
        }
    }
    LaunchedEffect(Unit) {
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            if (DesktopSleepTimer.isExpired()) {
                DesktopSleepTimer.cancel()
                playbackEngine.pause()
            }
        }
    }
    // The engine's state without the playhead, for everything at this level: the playhead moves
    // many times a second, and read here it recomposed the whole window with it — the sidebar,
    // the top bar, the page under the player and the player itself, a dozen times a second for
    // nothing. What draws the position reads it where it draws it (see PlaybackPosition).
    // Nor what Automix reports as it goes: its analysis moves on several times a second while a
    // track is worked through, and read here, each step recomposed the whole window — the disc
    // and the page under it caught on every one. It goes to the player's settings directly below.
    val playbackShape = remember(playbackEngine) {
        playbackEngine.state
            .map(::rootShape)
            .distinctUntilChanged()
    }
    val playback by playbackShape.collectAsState(rootShape(playbackEngine.state.value))
    LaunchedEffect(playbackEngine) {
        playbackEngine.state.collect {
            DesktopPlayerSettings.smartAnalysis.value = it.smartAnalysis
            DesktopPlayerSettings.smartMixInProgress.value = it.mixing
            DesktopPlayerSettings.smartTransitionWindow.value = it.transitionWindow
        }
    }

    // The shared player reads the playhead off this one object, and only where it
    // draws it — see PlaybackPosition — so a tick never recomposes the player.
    val playerPosition = remember { PlaybackPosition() }
    LaunchedEffect(playbackEngine) {
        playbackEngine.state.collect {
            // The audio thread's own timestamp, not this collector's: it runs on the UI thread and
            // gets to a reading as late as the UI is busy.
            playerPosition.report(it.positionMs, it.positionSampledAtNanos)
            playerPosition.seeks = it.seeks
            playerPosition.advancing = !it.awaitingAudio
        }
    }

    // The other version of what is playing — the music video of a song, or the song of a video —
    // for the disc player's Song | Video switch. Looked for only while that player is open, so a
    // queue played from the main window costs YouTube no extra searches.
    val playerStyle by DesktopStyleSettings.playerStyle.collectAsState()
    var versionAlternate by remember { mutableStateOf<Song?>(null) }
    var versionSearching by remember { mutableStateOf(false) }
    // Each song and its video, both ways round, once found or switched between: the switch back
    // needs no search, and cannot come up empty — it did, and the switch then vanished.
    val versionPairs = remember { androidx.compose.runtime.mutableStateMapOf<String, Song>() }
    // Tracks whose version was picked by hand, which the start-version setting then leaves alone.
    val versionChosen = remember { mutableSetOf<String>() }
    // A track found to be the wrong version to start as, for the switch further down to take up.
    var startVersionDue by remember { mutableStateOf<String?>(null) }

    /**
     * [song] with its song version's sleeve when it is a music video and that version is known: the
     * home card and the bars showed a blurry video frame where the cover belongs.
     */
    fun withSleeve(song: Song): Song {
        if (!song.looksLikeVideo()) return song
        val sleeve = versionPairs[song.videoId]?.takeIf { !it.looksLikeVideo() }?.thumbnailUrl ?: return song
        return song.copy(thumbnailUrl = sleeve)
    }
    // Once a track, and from the moment the player is wanted — the pointer on the way into it —
    // not from when it opens: the answer came a second into the opening, and the deck making
    // room for the switch then cost the slide a long frame. Kept when the player is put away.
    val startVersion by DesktopStyleSettings.startVersion.collectAsState()
    LaunchedEffect(selectedSong?.videoId, playerStyle, startVersion) {
        versionAlternate = null
        versionSearching = false
        val current = selectedSong ?: return@LaunchedEffect
        // Whether this track is the version tracks are to start as — if not, the other is looked
        // for straight away, whichever player is in use, and put in its place.
        val wrongVersion = current.videoId !in versionChosen && when (startVersion) {
            DesktopStartVersion.SONG -> current.looksLikeVideo()
            DesktopStartVersion.VIDEO -> !current.looksLikeVideo()
        }
        if (playerStyle != DesktopPlayerStyle.DISC && !wrongVersion) return@LaunchedEffect
        // A video at once — its sleeve is wanted on the home card and the bars too; a song only
        // once the player is.
        if (!current.looksLikeVideo() && !wrongVersion) snapshotFlow { overlays.nowPlaying || DesktopPlayerWarmup.wanted }.first { it }
        if (current.localPath != null || current.localUri != null || !DesktopSearchClient.isVideoId(current.videoId)) {
            return@LaunchedEffect
        }
        versionPairs[current.videoId]?.let {
            versionAlternate = it
            if (wrongVersion) startVersionDue = current.videoId
            return@LaunchedEffect
        }
        versionSearching = true
        val found = withContext(Dispatchers.IO) {
            runCatching {
                if (current.looksLikeVideo()) {
                    YtMusicRepository.resolveAudio(current.copy(isVideo = true))
                        .takeIf { it.videoId != current.videoId }
                        ?.copy(isVideo = false, isVideoOrigin = false)
                } else {
                    YtMusicRepository.resolveVideo(current)
                }
            }.getOrNull()
        }
        if (found != null) {
            versionPairs[current.videoId] = found
            versionPairs[found.videoId] = current
        }
        versionAlternate = found
        versionSearching = false
        if (wrongVersion && found != null) startVersionDue = current.videoId
    }

    /** Swaps the playing track for its other version, in place in the queue, at the same moment. */
    fun switchVersion(byHand: Boolean = true) {
        val current = selectedSong ?: return
        val target = versionAlternate ?: return
        if (partyTrackChangeBlocked()) return
        val at = playbackEngine.state.value.positionMs
        val replacement = target.copy(
            // Whichever way round, the other one: a video for a song, a song for a video.
            isVideo = !current.looksLikeVideo(),
            isVideoOrigin = !current.looksLikeVideo(),
            // Still the same place in the queue: the jam or station it came from goes with it —
            // without, switching a jam track to its clip read as the jam having stopped.
            radioName = current.radioName,
            queueTier = current.queueTier,
            playbackSource = target.playbackSource ?: current.playbackSource,
            playbackSourceId = target.playbackSourceId ?: current.playbackSourceId,
            playbackSourceType = target.playbackSourceType ?: current.playbackSourceType,
        )
        liveQueue = liveQueue.copy(
            songs = liveQueue.songs.toMutableList().also { songs ->
                if (liveQueue.index in songs.indices) songs[liveQueue.index] = replacement
            },
        )
        selectedSong = replacement
        versionPairs[replacement.videoId] = current
        versionPairs[current.videoId] = replacement
        versionAlternate = current
        // Switched by hand: the start-version setting leaves this one as it was put, both ways.
        if (byHand) {
            versionChosen += current.videoId
            versionChosen += replacement.videoId
        }
        saveQueue()
        playbackEngine.load(replacement, playWhenReady = true, startAtMs = at)
    }

    /**
     * [current] for the version tracks start as, while it is still just starting — never a track
     * that has been playing a while, and never one switched by hand.
     */
    fun switchToStartVersion(current: Song) {
        if (selectedSong?.videoId != current.videoId) return
        if (playbackEngine.state.value.positionMs > START_VERSION_WINDOW_MS) return
        switchVersion(byHand = false)
    }
    LaunchedEffect(startVersionDue) {
        val due = startVersionDue ?: return@LaunchedEffect
        startVersionDue = null
        selectedSong?.takeIf { it.videoId == due }?.let(::switchToStartVersion)
    }

    // Lyrics and motion artwork follow whatever is *playing*, not whatever page happens to be open.
    // This is where Android keeps it — `MainActivity`, keyed on the player's own track — so the
    // lookup starts the moment a track does and the panel is already populated by the time anyone
    // opens the player. Run from the player page instead, nothing was fetched until it was opened,
    // and a track half a minute in then showed a skeleton and arrived mid-song.
    var lyrics by remember { mutableStateOf<DesktopLyrics?>(null) }
    // The provider picked for this track in the player's lyrics drawer, if any.
    var lyricsOnly by remember(selectedSong?.videoId) { mutableStateOf<String?>(null) }
    // A title and artist typed into the song menu's lyrics search, overriding the
    // track's own credits for the lookup — for a cover or a mislabelled upload.
    var lyricsQuery by remember(selectedSong?.videoId) { mutableStateOf<Pair<String, String>?>(null) }
    // Bumped on every search so re-running the same query still refetches.
    var lyricsSearchTick by remember(selectedSong?.videoId) { mutableStateOf(0) }
    // What each provider's most recent race turned up, so the song menu's
    // provider drawer can say "Found" or "Not found" per source, not just name
    // the winner. It is keyed on the track and is only ever added to, never
    // cleared mid-track: a repeat search a cache answers straight away fires no
    // callbacks, so clearing on start would blank the very states it should hold.
    val lyricsProviderResults = remember(selectedSong?.videoId) {
        mutableStateOf<Map<LyricsSource, LyricsProviderState>>(emptyMap())
    }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var canvas by remember { mutableStateOf<DesktopCanvasArtwork?>(null) }

    // The length the lyrics are matched on, taken once per track. A better rendition swapped in
    // mid-song reports its own, slightly different length — or none, for a manifest — and keyed
    // on the live duration, every such upgrade threw away the lyrics on screen and fetched them
    // all over again. Only the playing track's own report counts: right after a skip, [playback]
    // still describes the track before.
    var lyricsLengthMs by remember(selectedSong?.videoId) { mutableStateOf(0L) }
    val playingLengthMs = playback.durationMs.takeIf { playback.song?.videoId == selectedSong?.videoId } ?: 0L
    LaunchedEffect(selectedSong?.videoId, playingLengthMs > 0L) {
        if (lyricsLengthMs <= 0L && playingLengthMs > 0L) lyricsLengthMs = playingLengthMs
    }

    // Keyed on the duration too: it lands a beat after the track, and a database match needs it,
    // so looking up against a length of zero would settle on the wrong recording.
    LaunchedEffect(
        selectedSong?.videoId,
        lyricsLengthMs,
        syncedLyrics,
        prioritizeSyllables,
        lyricsOn,
        lyricsOrder,
        lyricsOnly,
        lyricsRefresh,
        lyricsQuery,
        lyricsSearchTick,
    ) {
        val current = selectedSong
        lyrics = null
        lyricsError = null
        if (current == null) {
            lyricsLoading = false
            return@LaunchedEffect
        }
        if (!syncedLyrics) {
            lyricsError = "Synced lyrics are switched off"
            lyricsLoading = false
            return@LaunchedEffect
        }
        // A length is what a database match is made on, and it lands a beat after the track. Looking
        // up without one would settle on whichever recording shares the name — so this waits, the
        // same way Android's `loadLyrics` turns the call away until a duration exists.
        val length = lyricsLengthMs.takeIf { it > 0L } ?: current.durationMillis()
        if (length <= 0L) {
            lyricsLoading = true
            return@LaunchedEffect
        }
        lyricsLoading = true
        // The manual search asks the same providers under the typed words rather
        // than the track's own credits; a provider switch keeps that query.
        val probe = lyricsQuery?.let { (title, artist) -> current.copy(title = title, artist = artist) } ?: current
        DesktopLyricsClient.lookup(
            probe,
            length,
            only = lyricsOnly,
            // Each provider reports itself as the race runs; the callback is on
            // the IO thread, so the map is updated back on the composition's own.
            onSourceResult = { source, found ->
                scope.launch {
                    lyricsProviderResults.value += source to
                        if (found) LyricsProviderState.FOUND else LyricsProviderState.NOT_FOUND
                }
            },
        ).fold(
            onSuccess = { lyrics = it },
            onFailure = { lyricsError = it.message ?: "Lyrics unavailable" },
        )
        lyricsLoading = false
    }

    // A manual ask from the song menu's lyrics drawer, for a track whose automatic
    // lookup came up empty and whose real title or artist the listener knows
    // better than the tag. It just sets the query and nudges the effect above,
    // which owns the loading state and the result — so the drawer's provider
    // list updates through the same path as the automatic lookup.
    fun searchLyrics(title: String, artist: String) {
        lyricsQuery = title to artist
        lyricsSearchTick++
    }

    // The song menu's lyrics offset nudges the whole synced lyric against the clock. It is a
    // per-track adjustment, so a new track starts back at zero; the desktop owns the reset so the
    // phone's sheet, which carries the offset across tracks, is left alone.
    val lyricsOffsetMs by DesktopPlayerSettings.lyricsOffsetMs.collectAsState()
    LaunchedEffect(selectedSong?.videoId) {
        DesktopPlayerSettings.setLyricsOffsetMs(0)
    }

    LaunchedEffect(selectedSong?.videoId, animatedCanvas) {
        val current = selectedSong
        if (current == null || !animatedCanvas) {
            canvas = null
            return@LaunchedEffect
        }
        // Paint what is already known before waiting on anything: re-opening the player on a track
        // resolved a minute ago should not go dark on its way back to the same clip.
        canvas = DesktopCanvasClient.cached(current)
        canvas = withContext(Dispatchers.IO) { DesktopCanvasClient.lookup(current) }
    }

    LaunchedEffect(
        playback.song?.videoId,
        playback.isPlaying,
        playback.durationMs,
        playback.volume,
        playback.error,
        shuffle,
        repeatMode,
        playbackSpeed,
    ) {
        mprisController.update(
            playback = playback,
            shuffle = shuffle,
            loopStatus = when (repeatMode) {
                DesktopRepeatMode.OFF -> "None"
                DesktopRepeatMode.ALL -> "Playlist"
                DesktopRepeatMode.ONE -> "Track"
            },
            rate = playbackSpeed.toDouble(),
        )
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs) {
        DesktopScrobbling.onPlaybackStateChanged(playback)
        DesktopWindowsMedia.publish(playback)
    }
    // Refetched on opening the page rather than cached for the session: the
    // point of it is that something played elsewhere shows up here.
    LaunchedEffect(destination, youtubeSignedIn, sessionRevision) {
        if (destination != DesktopDestination.HISTORY || !youtubeSignedIn) return@LaunchedEffect
        DesktopSearchClient.history().onSuccess { remoteHistory = it }
    }
    LaunchedEffect(selectedSong?.videoId) {
        trackLinks = null
        val current = selectedSong ?: return@LaunchedEffect
        // Only once the player is wanted, so playing from the mini player costs nothing, and
        // only when something is actually missing. Fetched as the pointer heads for the player
        // rather than as it opens, and kept for the track: arriving mid-slide, the links had the
        // words laid out again during the opening — on every opening.
        if (current.artistId != null && current.albumId != null) return@LaunchedEffect
        snapshotFlow { overlays.nowPlaying || DesktopPlayerWarmup.wanted }.first { it }
        trackLinks = DesktopSearchClient.trackLinks(current.videoId).getOrNull()
    }
    // Keyed on the same things, plus the rate: Discord counts the bar down on
    // its own clock, so only a change to what it was told is worth another push.
    LaunchedEffect(playback.song?.videoId, playback.isPlaying, playback.durationMs, playbackSpeed, playback.streamFormat) {
        DesktopDiscordRpc.onPlaybackStateChanged(playback, playbackSpeed)
    }
    // The account's own history on YouTube Music, which is what the home feed is built out of.
    DisposableEffect(playback.song?.videoId) {
        val previous = playbackEngine.state.value.positionMs / 1_000
        onDispose { DesktopPlaybackTracker.onTrackChanged(previous) }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        DesktopPlaybackTracker.onPlaying(songId)
        while (currentCoroutineContext().isActive) {
            delay(HISTORY_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (snapshot.song?.videoId != songId) break
            DesktopPlaybackTracker.onProgress(songId, snapshot.positionMs / 1_000)
        }
    }
    LaunchedEffect(playback.song?.videoId, playback.isPlaying) {
        val songId = playback.song?.videoId ?: return@LaunchedEffect
        if (!playback.isPlaying) return@LaunchedEffect
        var previousPosition = playbackEngine.state.value.positionMs
        var countedAsPlay = songId in statsCountedSongs
        while (currentCoroutineContext().isActive) {
            delay(STATS_SAMPLE_MS)
            val snapshot = playbackEngine.state.value
            if (!snapshot.isPlaying || snapshot.song?.videoId != songId) break
            val position = snapshot.positionMs
            val delta = (position - previousPosition).takeIf { it in 1L..STATS_MAX_DELTA_MS } ?: 0L
            if (delta > 0L) {
                val threshold = snapshot.durationMs.takeIf { it > 0L }?.let { minOf(it / 2L, SCROBBLE_THRESHOLD_MS) }
                    ?: SCROBBLE_THRESHOLD_MS
                val reachedThreshold = !countedAsPlay && position >= threshold
                DesktopListeningStats.record(snapshot.song, delta, reachedThreshold)
                if (reachedThreshold) {
                    countedAsPlay = true
                    statsCountedSongs = statsCountedSongs + songId
                }
                statsRevision++
            }
            previousPosition = position
        }
    }
    // Followed here rather than keyed on: a key is read by the window's root, and the counter moves
    // every five seconds while a track plays — each a recomposition of the whole window, and a
    // dropped frame of the disc or the page with it.
    LaunchedEffect(Unit) {
        snapshotFlow { statsRevision }.drop(1).collect {
            replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
        }
    }
    DisposableEffect(playbackEngine) {
        onDispose { playbackEngine.release() }
    }

    /** Opens an artist's page, from a card, a search row or the player's credit. */
    fun openArtist(browseId: String, name: String) {
        val target = DesktopArtistTarget(browseId, name)
        if (openedArtist == target && artistState is UiState.Success) return
        openedCollection = null
        overlays.replay = false
        overlays.settingsPage = null
        openedArtist = target
        artistState = UiState.Loading
        artistReloads++
    }

    /**
     * The "…" menu for one row, wherever it is drawn.
     *
     * Built once so a row on Downloads or Local Music offers what the player's own "…" offers —
     * Android opens the same `SongActionsSheet` from every one of them.
     */
    fun songActionsFor(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onStartJam = { song -> startWave(from = song) },
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        onOpenAlbum = ::openAlbum,
        onOpenArtist = { id -> openArtist(id, song.artist) },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    /** The "…" a song row hangs off, built the same way on every page that offers one. */
    @Composable
    fun songMenu(song: Song) = DesktopSongMenuAnchor(
        song = song,
        liked = song.videoId in likedIds,
        actions = songActionsFor(song),
        onToggleLike = { toggleLike(song) },
        onRevertToOriginal = null,
        onUpgradeQuality = null,
    )

    /** Replay counts artists by name, so the page has to be found from the name alone. */
    fun openArtistByName(name: String) {
        scope.launch {
            val match = DesktopSearchClient.search(name, SearchFilter.ARTISTS)
                .getOrDefault(emptyList())
                .filterIsInstance<SearchResult.Browse>()
                .map { it.item }
                .firstOrNull { it.browseId.isNotBlank() }
                ?: return@launch
            openArtist(match.browseId, match.title)
        }
    }

    LaunchedEffect(openedArtist, artistReloads) {
        val target = openedArtist ?: return@LaunchedEffect
        if (artistState !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.artistPage(target.browseId).fold(
            onSuccess = { artistState = UiState.Success(it) },
            onFailure = { artistState = UiState.Error(it.message ?: "Could not open ${target.name}") },
        )
    }

    fun toggleSubscription(subscription: SubscriptionState) {
        val target = openedArtist ?: return
        val next = !subscription.subscribed
        // Painted before the network answers and put back if it refuses.
        artistState = (artistState as? UiState.Success)?.let { current ->
            UiState.Success(current.data.copy(subscription = subscription.copy(subscribed = next)))
        } ?: artistState
        scope.launch {
            DesktopSearchClient.setSubscribed(subscription.channelId, next).onFailure { failure ->
                DesktopTrackLog.log("subscription: ${target.name} failed: ${failure.message}")
                artistState = (artistState as? UiState.Success)?.let { current ->
                    UiState.Success(current.data.copy(subscription = subscription))
                } ?: artistState
            }
        }
    }

    /** A search hit or a recent search that is a page: an artist's own, or a collection's. */
    fun openBrowseItem(item: BrowseItem) {
        if (item.type == BrowseType.ARTIST) {
            openArtist(item.browseId, item.title)
        } else {
            openCollection(item.browseId, item)
        }
    }

    fun openShelfItem(item: ShelfItem, shelfTitle: String? = null) {
        val videoId = item.videoId
        val browseId = item.browseId
        when {
            // The track playing now: its card pauses and resumes it, not starts it over.
            videoId != null && videoId == selectedSong?.videoId -> togglePlayPauseFromUser()
            videoId != null -> {
                val source = shelfTitle?.let { DesktopQueueSource(it, PlaybackSourceType.HOME) }
                playSong(item.toSong(), source = source)
            }
            // An artist is a page of its own, not a list of tracks with a photograph on top.
            browseTypeOf(browseId.orEmpty()) == BrowseType.ARTIST ->
                openArtist(browseId!!, item.title)
            browseId != null -> openCollection(
                browseId = browseId,
                fallback = BrowseItem(
                    browseId = browseId,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = BrowseType.OTHER,
                ),
            )
        }
    }

    fun openMenu(song: Song) = pageMenu(song)

    /** What the phone records for a search hit: the thing picked, not the words typed to find it. */
    fun recordSongSearch(song: Song) = DesktopSearchHistory.record(
        SearchHistoryEntity(
            id = song.videoId,
            title = song.title,
            subtitle = song.artist,
            artworkUrl = song.thumbnailUrl,
            entityType = EntityType.TRACK,
        ),
    )

    fun recordBrowseSearch(item: BrowseItem) = DesktopSearchHistory.record(
        SearchHistoryEntity(
            id = item.browseId,
            title = item.title,
            subtitle = item.subtitle,
            artworkUrl = item.thumbnailUrl,
            entityType = when (item.type) {
                BrowseType.ALBUM -> EntityType.ALBUM
                BrowseType.ARTIST -> EntityType.ARTIST
                BrowseType.PLAYLIST -> EntityType.PLAYLIST
                else -> EntityType.TRACK
            },
        ),
    )

    /** Typing, from the page's field or the sidebar's: the typeahead follows, and an empty field goes back to recents. */
    fun editQuery(text: String) {
        query = text
        searchTyping = true
        if (text.isBlank()) {
            searchCommitted = false
            searchRows = emptyList()
            searchError = null
        }
    }

    fun search() {
        if (query.isBlank() || searchLoading) return
        overlays.settingsPage = null
        destination = DesktopDestination.SEARCH
        searchTyping = false
        searchSuggestions = emptyList()
        // Submitting is picking the first track the typeahead offered, as on the phone — or, with
        // none, the first the results bring back.
        val typedHit = searchTypeahead.firstNotNullOfOrNull { row ->
            when (row) {
                is SearchResult.TopTrack -> row.song
                is SearchResult.Track -> row.song
                else -> null
            }
        }
        typedHit?.let(::recordSongSearch)
        searchCommitted = true
        searchScrollReset++
        searchLoading = true
        searchError = null
        scope.launch {
            try {
                DesktopMusicSources.search(query, searchFilter).fold(
                    onSuccess = { rows ->
                        searchRows = rows
                        if (typedHit == null) {
                            rows.firstNotNullOfOrNull { row ->
                                when (row) {
                                    is SearchResult.TopTrack -> row.song
                                    is SearchResult.Track -> row.song
                                    else -> null
                                }
                            }?.let(::recordSongSearch)
                        }
                    },
                    onFailure = { searchError = it.message ?: "Search failed" },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                searchError = failure.message ?: "Search failed"
            } finally {
                // OBS and other desktop capture tools can briefly interrupt focus/coroutines.
                searchLoading = false
            }
        }
    }

    /** The typeahead, one request behind the keystrokes rather than one per. */
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(SUGGESTION_DEBOUNCE_MS)
        DesktopSearchClient.searchSuggestions(query)
            .onSuccess { if (searchTyping) searchSuggestions = it }
            .onFailure { searchSuggestions = emptyList() }
    }

    // Its own pass, on its own delay: a search costs far more than a completion, so it waits for a
    // longer pause in the typing rather than riding the same one.
    LaunchedEffect(query, searchTyping) {
        if (!searchTyping || query.isBlank()) {
            searchTypeahead = emptyList()
            return@LaunchedEffect
        }
        delay(TYPEAHEAD_MEDIA_DEBOUNCE_MS)
        DesktopSearchClient.searchTypeahead(query)
            .onSuccess { if (searchTyping) searchTypeahead = it.take(TYPEAHEAD_MEDIA_LIMIT) }
            .onFailure { searchTypeahead = emptyList() }
    }

    fun runSearch(term: String) {
        query = term
        searchTyping = false
        searchSuggestions = emptyList()
        searchTypeahead = emptyList()
        search()
    }

    fun selectDestination(next: DesktopDestination) {
        if (next == DesktopDestination.LIBRARY && libraryStale) {
            libraryStale = false
            reloadLibrary()
        }
        openedArtist = null
        openedCollection = null
        overlays.replay = false
        overlays.settingsPage = null
        selectedMoodGenre = null
        libraryShowAll = null
        if (next == DesktopDestination.SEARCH) searchFocusRequested = true
        destination = next
    }

    // Settings' search and scroll outlive a visit to one of its pages, and start over each time
    // Settings itself is opened.
    var settingsSession by remember { mutableStateOf(0) }
    var settingsQuery by remember(settingsSession) { mutableStateOf("") }
    val settingsListState = remember(settingsSession) { LazyListState() }
    val settingsWheel = rememberSmoothWheel(settingsListState)

    /** Settings in place of the page — from the sidebar, the account switcher or the tray. */
    fun openSettings() {
        overlays.nowPlaying = false
        if (overlays.settingsPage == null) settingsSession++
        overlays.settingsPage = DesktopSettingsPage.MAIN
    }

    /** The Library's folder rows: the two folders this computer has, and Spotify once connected. */
    val libraryLinks = remember(spotifyCanvasCookie.isNotBlank()) {
        listOfNotNull(
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["downloads", "Downloads"],
                    subtitle = DesktopStrings["downloaded_songs", "Downloaded songs"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = LOCAL_DOWNLOADS_ID,
                ),
                icon = Icons.Rounded.Download,
            ),
            LibraryLink(
                item = ShelfItem(
                    title = DesktopStrings["local_music", "Local Music"],
                    subtitle = DesktopStrings["d_audio_files_on_this_computer", "Audio files on this computer"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = LOCAL_MUSIC_ID,
                ),
                icon = Icons.Rounded.Folder,
            ),
            // As on the phone, only while signed in to Spotify in Account & integrations.
            LibraryLink(
                item = ShelfItem(
                    title = SPOTIFY_SHELF,
                    subtitle = DesktopStrings["spotify_library_subtitle", "Your playlists and Liked Songs"],
                    thumbnailUrl = null,
                    videoId = null,
                    browseId = SPOTIFY_LIBRARY_ID,
                ),
                icon = Icons.Rounded.Cloud,
                logo = SharedRes.drawable.spotify_logo,
            ).takeIf { spotifyCanvasCookie.isNotBlank() },
        )
    }

    /** The Spotify account's playlists, as a Library grid; Liked Songs first, as on the phone. */
    fun openSpotifyLibrary() {
        libraryShowAll = HomeShelf(SPOTIFY_SHELF, emptyList())
        spotifyLibraryStatus = "Loading your Spotify playlists…"
        scope.launch {
            runCatching { SpotifyLibrary.playlists() }
                .onSuccess { found ->
                    spotifyLibraryStatus = null
                    if (libraryShowAll?.title == SPOTIFY_SHELF) {
                        libraryShowAll = HomeShelf(SPOTIFY_SHELF, found.map { it.toShelfItem() })
                    }
                }
                .onFailure {
                    DesktopTrackLog.log("spotify: playlists failed: ${it.message}")
                    spotifyLibraryStatus = "Could not load your Spotify playlists: ${it.message}"
                }
        }
    }

    /**
     * The Library's "On device" shelf: the playlists kept on this computer rather than on the
     * account — the desktop's counterpart of the phone's downloaded releases, and like them
     * reachable from nowhere else.
     */
    val libraryDeviceItems = remember(playlists) {
        playlists.map { playlist ->
            ShelfItem(
                title = playlist.title,
                subtitle = "Playlist • ${playlist.songs.size} songs",
                thumbnailUrl = playlist.songs.firstOrNull()?.thumbnailUrl,
                videoId = null,
                browseId = LOCAL_PLAYLIST_PREFIX + playlist.id,
            )
        }
    }

    /** A Library card: a folder is a page of this app's, a local playlist is opened from here. */
    fun openLibraryItem(item: ShelfItem) {
        val browseId = item.browseId.orEmpty()
        when {
            browseId == LOCAL_DOWNLOADS_ID -> selectDestination(DesktopDestination.DOWNLOADS)
            browseId == LOCAL_MUSIC_ID -> selectDestination(DesktopDestination.LOCAL_MUSIC)
            browseId == SPOTIFY_LIBRARY_ID -> openSpotifyLibrary()
            // Spotify's tracks fill in below, as they are read and matched.
            browseId.startsWith(SPOTIFY_PAGE_PREFIX) -> showCollection(
                DesktopCollection(
                    browseId = browseId,
                    title = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    type = BrowseType.PLAYLIST,
                    songs = emptyList(),
                    loading = true,
                ),
            )
            browseId.startsWith(LOCAL_PLAYLIST_PREFIX) ->
                playlists.firstOrNull { it.id == browseId.removePrefix(LOCAL_PLAYLIST_PREFIX) }?.let(::openPlaylist)
            else -> openShelfItem(item)
        }
    }

    /**
     * Adds shelves to the page as they arrive, skipping any heading already on it and any shelf
     * that repeats one's releases — the phone's `publishHomeShelves`. Recently Played is
     * [prepend]ed and replaces any stale copy the core feed carried.
     */
    fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (homeState as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            val replacing = shelves.map { it.title.lowercase() }.toSet()
            homeSeenTitles.addAll(replacing)
            homeState = UiState.Success(shelves + existing.filterNot { it.title.lowercase() in replacing })
            return
        }
        val added = shelves.withoutRepeatsOf(existing)
            .filter { it.items.isNotEmpty() && homeSeenTitles.add(it.title.lowercase()) }
        if (added.isNotEmpty()) homeState = UiState.Success(existing + added)
    }

    /**
     * The phone's Home: the core feed, the account's own Recently Played at its head, and the
     * supplementary browse feeds, all asked for at once and laid down as each lands. Signed out
     * there is no Recently Played to ask for, which is also how the phone has it.
     */
    fun loadHome() {
        if (!youtubeSourceEnabled) {
            homeState = UiState.Error("YouTube Music is disabled")
            return
        }
        val generation = ++homeGeneration
        val signedIn = DesktopYouTubeAuth.isSignedIn
        homeIdentity = "$signedIn/${DesktopAccounts.activeAccountId()}"
        homeState = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        homeLoadingMore = false
        homeRecentsLoading = signedIn
        scope.launch {
            launch {
                DesktopSearchClient.home().fold(
                    onSuccess = { feed ->
                        if (generation != homeGeneration) return@fold
                        homeContinuation = feed.continuation
                        publishHomeShelves(feed.shelves)
                    },
                    onFailure = {
                        if (generation == homeGeneration && homeState !is UiState.Success) {
                            homeState = UiState.Error(it.message ?: "Could not load Home")
                        }
                    },
                )
            }
            if (signedIn) {
                launch {
                    YtMusicRepository.homeRecentlyPlayed()
                        .onSuccess { shelf ->
                            if (generation != homeGeneration) return@onSuccess
                            homeRecentsLoading = false
                            shelf?.let { publishHomeShelves(listOf(it), prepend = true) }
                        }
                        .onFailure { if (generation == homeGeneration) homeRecentsLoading = false }
                }
            }
            DesktopSearchClient.HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                launch {
                    DesktopSearchClient.homeSupplement(browseId).onSuccess { shelves ->
                        if (generation == homeGeneration) publishHomeShelves(shelves)
                    }
                }
            }
        }
    }

    /** The next page of the core feed, as the list nears its end; a no-op once it has run dry. */
    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (homeLoadingMore || homeState !is UiState.Success) return
        val generation = homeGeneration
        homeLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreHome(token).fold(
                onSuccess = { feed ->
                    if (generation != homeGeneration) return@fold
                    val before = homeSeenTitles.size
                    publishHomeShelves(feed.shelves)
                    // A page with nothing new is the feed looping back on itself, not running dry.
                    homeContinuation = feed.continuation.takeIf { homeSeenTitles.size > before }
                },
                onFailure = { homeContinuation = null },
            )
            homeLoadingMore = false
        }
    }

    // The tray icon speaks StatusNotifierItem rather than going through AWT, which only implements
    // the XEmbed tray a Wayland session does not have.
    val tray = remember {
        DesktopStatusNotifierController(
            // Clicking the icon is how the window comes back once it has been closed to the tray,
            // so it has to raise the window before it does anything inside it.
            onActivate = {
                DesktopWindowVisibility.show()
                overlays.nowPlaying = true
            },
            onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
        )
    }
    DisposableEffect(tray, trayIconEnabled) {
        if (trayIconEnabled) tray.start()
        onDispose { tray.stop() }
    }
    // Deliberately not a key on the effect above.
    DisposableEffect(trayIconEnabled, closeToTray) {
        // The listener's choice, with the tray as its precondition: hiding with no tray to come
        // back from leaves an audible process and no window.
        DesktopWindowVisibility.keepRunningWhenClosed = trayIconEnabled && closeToTray
        onDispose { DesktopWindowVisibility.keepRunningWhenClosed = false }
    }
    LaunchedEffect(tray, trayIconEnabled) {
        if (!trayIconEnabled) return@LaunchedEffect
        // The logo travels to the tray as pixels: a themed icon name would be whatever the user's
        // theme draws for a generic music player.
        runCatching { Res.readBytes("drawable/logo.svg") }.onSuccess(tray::setIcon)
    }
    LaunchedEffect(tray) {
        DesktopTrayMenu.bind(
            onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onOpenPlayer = {
                DesktopWindowVisibility.show()
                overlays.nowPlaying = true
            },
            onOpenSettings = {
                DesktopWindowVisibility.show()
                openSettings()
            },
            onQuit = {
                // Native work first: the audio thread and the media controls are inside FFmpeg,
                // WASAPI and WinRT, and exitProcess would unload those libraries under them.
                playbackEngine.shutdown()
                DesktopWindowsMedia.stop()
                exitProcess(0)
            },
        )
    }
    LaunchedEffect(selectedSong, playback.isPlaying) {
        val title = selectedSong?.let { "${it.title} — ${it.artist}" }
        tray.update(title = title, isPlaying = playback.isPlaying)
        DesktopTrayMenu.artworkUrl = selectedSong?.thumbnailUrl?.let { it.artworkAt(160) ?: it }
        DesktopTrayMenu.publish(title = title, playing = playback.isPlaying)
    }

    LaunchedEffect(Unit) {
        // Unpacks the analyser and its models on first run.
        DesktopAnalysisRuntime.ensureStarted()
        DesktopHorizontalScroll.install()
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        // A download record is a claim about a folder this app does not own, so it can outlive the
        // file it names. Checked once at startup rather than trusted: a stale entry showed the
        // track as downloaded, refused to fetch it again, and then would not play.
        val onDisk = withContext(Dispatchers.IO) { DesktopDownloadManager.verified(downloads) }
        if (onDisk.size != downloads.size) {
            DesktopTrackLog.log(
                "downloads: ${downloads.size - onDisk.size} recorded file(s) are gone; forgetting them",
            )
            downloads = onDisk
            persistence.saveDownloads(onDisk)
        }
        // Not before the saved session is back: started at once, the feed came as a guest's and
        // was then thrown away and fetched again for the account. A session slow to come back
        // (renewed from the browser) is not waited on for long; the reload below follows it.
        kotlinx.coroutines.withTimeoutOrNull(SESSION_WAIT_MS) { snapshotFlow { sessionSettled }.first { it } }
        loadHome()
    }

    // The feed is the account's: signing in, out or across to another channel is a different Home,
    // and Recently Played only exists with an account behind it.
    LaunchedEffect(youtubeSignedIn, activeAccountId) {
        val identity = "${DesktopYouTubeAuth.isSignedIn}/${DesktopAccounts.activeAccountId()}"
        if (homeIdentity != null && identity != homeIdentity) loadHome()
    }

    // The mark on a downloaded row, for the shared pages.
    LaunchedEffect(downloads) {
        DesktopAppUiHost.downloaded.value = downloads.mapTo(HashSet(), Song::videoId)
    }

    // What reached disk goes into the library's own record, which is what the Downloads page and
    // every row's tick read from.
    LaunchedEffect(Unit) {
        DesktopDownloadQueue.finished.collect { saved ->
            downloads = (listOf(saved) + downloads.filterNot { it.videoId == saved.videoId }).take(200)
            persistence.saveDownloads(downloads)
        }
    }

    // The charts are period-filtered at read time, so a different period is a different read.
    LaunchedEffect(replayPeriod) {
        replaySummary = withContext(Dispatchers.IO) { DesktopListeningStats.summary(replayPeriod) }
    }

    // Re-scanned when the filter or the folder changes, never on first composition.
    LaunchedEffect(localMusicRevision) {
        if (localMusicRevision == 0) return@LaunchedEffect
        localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        DesktopLocalMusicWatcher.restart()
    }

    // A track dropped into the folder appears without a relaunch.
    LaunchedEffect(Unit) {
        DesktopLocalMusicWatcher.restart()
        DesktopLocalMusicWatcher.changes.collect {
            localSongs = withContext(Dispatchers.IO) { DesktopLocalMusic.scan() }
        }
    }

    /** Bumped when a fresh Explore lands, to start resolving the covers it is missing. */
    var exploreGeneration by remember { mutableStateOf(0) }

    /**
     * Explore: what was saved last time at once, then — if it is stale, incomplete or asked for —
     * the live one, keeping every cover already known.
     */
    suspend fun loadExplore(force: Boolean = false) {
        if (!youtubeSourceEnabled) {
            exploreState = UiState.Error("YouTube Music is disabled")
            return
        }
        val language = DesktopStrings.resolvedTag()
        val saved = withContext(Dispatchers.IO) { DesktopExploreCache.load(language) }
        if (saved != null && exploreState !is UiState.Success) exploreState = UiState.Success(saved.sections)
        val complete = saved != null && saved.sections.all { section -> section.items.all { it.thumbnailUrl != null } }
        if (!force && saved != null && complete &&
            System.currentTimeMillis() - saved.savedAtMs < DesktopExploreCache.FRESH_MS
        ) {
            return
        }
        if (saved == null) exploreState = UiState.Loading
        DesktopSearchClient.moodAndGenres().fold(
            onSuccess = { sections ->
                if (sections.isEmpty()) {
                    if (saved == null) exploreState = UiState.Error("Nothing to explore right now")
                } else {
                    val known = (exploreState as? UiState.Success)?.data ?: saved?.sections
                    val merged = DesktopExploreCache.carryCovers(sections, known)
                    exploreState = UiState.Success(merged)
                    withContext(Dispatchers.IO) { DesktopExploreCache.save(language, merged) }
                    exploreGeneration++
                }
            },
            onFailure = {
                // A saved Explore on screen beats an error over it.
                if (saved == null) exploreState = UiState.Error(it.message ?: "Could not load Explore")
            },
        )
    }

    LaunchedEffect(destination, exploreReloads) {
        if (destination == DesktopDestination.EXPLORE &&
            (exploreState is UiState.Loading || exploreReloads > 0)
        ) {
            loadExplore(force = exploreReloads > 0)
        }
    }
    // Ready before it is asked for: read from disk, or fetched quietly, a few seconds after launch.
    LaunchedEffect(Unit) {
        delay(EXPLORE_PREFETCH_DELAY_MS)
        if (exploreState is UiState.Loading) loadExplore()
    }

    // Category buttons carry no artwork of their own, so each is resolved from the shelves it
    // opens. Keyed on the load, not on the state: keyed on the state, every cover that arrived
    // restarted this and cancelled every request still in flight — which is why Explore used to
    // take so long to fill. Arrivals are applied in batches, not one recomposition per cover.
    LaunchedEffect(exploreGeneration) {
        val sections = (exploreState as? UiState.Success)?.data ?: return@LaunchedEffect
        val missing = sections.flatMap(MoodGenreSection::items)
            .filter { it.thumbnailUrl == null }
            .distinctBy { DesktopExploreCache.key(it) }
        if (missing.isEmpty()) return@LaunchedEffect
        val found = java.util.concurrent.ConcurrentHashMap<String, String>()
        val limiter = Semaphore(EXPLORE_ARTWORK_CONCURRENCY)
        fun apply() {
            if (found.isEmpty()) return
            val current = (exploreState as? UiState.Success)?.data ?: return
            exploreState = UiState.Success(
                current.map { section ->
                    section.copy(
                        items = section.items.map { entry ->
                            found[DesktopExploreCache.key(entry)]?.let { entry.copy(thumbnailUrl = it) } ?: entry
                        },
                    )
                },
            )
        }
        coroutineScope {
            val ticker = launch {
                while (true) {
                    delay(EXPLORE_BATCH_MS)
                    apply()
                }
            }
            missing.map { item ->
                launch {
                    limiter.withPermit {
                        DesktopSearchClient.moodGenreArtwork(item.browseId, item.params).getOrNull()
                    }?.let { found[DesktopExploreCache.key(item)] = it }
                }
            }.forEach { it.join() }
            ticker.cancel()
        }
        apply()
        val done = (exploreState as? UiState.Success)?.data ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { DesktopExploreCache.save(DesktopStrings.resolvedTag(), done) }
    }

    /** The next page of the open collection, appended in place. */
    fun loadMoreCollectionSongs() {
        val collection = openedCollection ?: return
        val token = collection.continuation ?: return
        if (collectionLoadingMore) return
        collectionLoadingMore = true
        scope.launch {
            DesktopSearchClient.moreCollectionSongs(token, collection.songs.firstOrNull()?.artist)
                .onSuccess { (songs, next) ->
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        openedCollection = current.copy(
                            songs = (current.songs + songs).distinctBy(Song::videoId),
                            continuation = next,
                        )
                    }
                }
                .onFailure {
                    DesktopTrackLog.log("collection: no more rows for ${collection.browseId}: ${it.message}")
                    val current = openedCollection
                    if (current != null && current.browseId == collection.browseId) {
                        // Dropped rather than retried on scroll for ever: the trailing row is what
                        // asks, and leaving the token in place would have it ask again on every
                        // frame.
                        openedCollection = current.copy(continuation = null)
                    }
                }
            collectionLoadingMore = false
        }
    }

    // The whole list, not its first hundred: the pages after the first are fetched one after
    // another as soon as it opens, so the count, the length and the list are all of it before
    // anyone scrolls — each finished page asks for the next.
    LaunchedEffect(openedCollection?.browseId, openedCollection?.continuation, collectionLoadingMore) {
        val page = openedCollection ?: return@LaunchedEffect
        if (page.loading || page.continuation == null || collectionLoadingMore) return@LaunchedEffect
        loadMoreCollectionSongs()
    }

    fun renameOpenPlaylist(title: String) {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.renamePlaylist(collection.playlistId, title)
                .onSuccess {
                    // Renamed in place as well as re-fetched: the page that ordered it is still on
                    // screen showing the old name, and the library reload behind it takes a moment.
                    openedCollection = openedCollection
                        ?.takeIf { it.browseId == collection.browseId }
                        ?.copy(title = title)
                        ?: openedCollection
                    editPlaylistShelf { items ->
                        items.map { if (it.browseId == collection.browseId) it.copy(title = title) else it }
                    }
                    closePlaylistDialogs()
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not rename the playlist"
                }
        }
    }

    fun deleteOpenPlaylist() {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.deletePlaylist(collection.playlistId)
                .onSuccess {
                    closePlaylistDialogs()
                    // Its page is the one open, and a deleted playlist has nothing left to show.
                    if (openedCollection?.browseId == collection.browseId) openedCollection = null
                    editPlaylistShelf { items -> items.filterNot { it.browseId == collection.browseId } }
                    libraryStale = true
                }
                .onFailure {
                    playlistBusy = false
                    playlistError = it.message ?: "Could not delete the playlist"
                }
        }
    }

    /** Takes one row back out of the playlist it is being read in. */
    fun removeFromOpenPlaylist(song: Song) {
        val collection = openedCollection ?: return
        val setVideoId = song.setVideoId ?: return
        scope.launch {
            DesktopSearchClient.removeFromPlaylist(
                collection.playlistId,
                listOf(setVideoId to song.videoId),
            ).onSuccess {
                editCollections({ it.browseId == collection.browseId }) { page ->
                    page.copy(songs = page.songs.filterNot { it.setVideoId == setVideoId })
                }
                libraryStale = true
            }.onFailure {
                DesktopTrackLog.log("playlist: could not remove ${song.videoId}: ${it.message}")
            }
        }
    }

    /**
     * Sends a new order for the playlist being read. [original] and [reordered] are the same whole
     * list of entries — as it was and as the user left it — since a reorder is sent as moves relative
     * to neighbours and needs every entry to move against.
     */
    fun reorderOpenPlaylist(original: List<Song>, reordered: List<Song>) {
        val collection = openedCollection ?: return
        playlistBusy = true
        playlistError = null
        scope.launch {
            DesktopSearchClient.reorderPlaylist(
                collection.playlistId,
                current = original.mapNotNull { it.setVideoId },
                target = reordered.mapNotNull { it.setVideoId },
            ).onSuccess {
                val current = openedCollection
                if (current != null && current.browseId == collection.browseId) {
                    // The whole list arrived to be reordered, so the page now shows all of it and has
                    // no further page to ask for.
                    openedCollection = current.copy(songs = reordered, continuation = null)
                }
                closePlaylistDialogs()
                libraryStale = true
            }.onFailure {
                playlistBusy = false
                playlistError = it.message ?: "Could not reorder the playlist"
            }
        }
    }

    /**
     * The account's library, re-read whenever the session behind it changes — signing in, switching
     * channel, signing out.
     */
    LaunchedEffect(activeAccountId, activeProfileId, sessionRevision) {
        // Nothing to read until the saved session is back; the bump that follows runs this again.
        if (!sessionSettled) return@LaunchedEffect
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
        if (!DesktopYouTubeAuth.isSignedIn) {
            libraryState = UiState.Success(LibraryPage(emptyList(), emptyList(), emptyList()))
            return@LaunchedEffect
        }
        libraryState = UiState.Loading
        DesktopSearchClient.library().fold(
            onSuccess = { page ->
                // The continuation is a job to run, not page state — it is consumed below.
                libraryState = UiState.Success(page.copy(likedContinuation = null))
                DesktopTrackLog.log(
                    "library: ${page.likedSongs.size} liked, ${page.librarySongs.size} added, " +
                        "shelves ${page.shelves.joinToString { "${it.title}=${it.items.size}" }}",
                )
                val fromAccount = page.likedSongs.mapTo(HashSet(), Song::videoId)
                if (!likedIds.containsAll(fromAccount)) {
                    likedIds = likedIds + fromAccount
                    // Off the drawing thread: an account with a long Liked Music writes a few tens
                    // of kilobytes through the preference store, and that is a disk flush.
                    withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                }
                // Liked Music is published to the tab a page budget deep; the rest is followed here
                // for its ids alone, so a liked track past the budget still reads as liked.
                // Tied to the account it was fetched for: the liked set is one shared map, and a
                // sync still running after a switch would seed it with a stranger's likes.
                val syncingFor = activeAccountId
                page.likedContinuation?.let { token ->
                    likedSyncJob?.cancel()
                    likedSyncJob = scope.launch {
                        val extra = HashSet<String>()
                        DesktopSearchClient.syncLikedIds(token, onIds = { ids -> extra += ids })
                        if (syncingFor != DesktopAccounts.activeAccountId()) return@launch
                        if (!likedIds.containsAll(extra)) {
                            likedIds = likedIds + extra
                            withContext(Dispatchers.IO) { persistence.saveLikedIds(likedIds) }
                            DesktopTrackLog.log("library: liked sync added ${extra.size} ids past the first pages")
                        }
                    }
                }
            },
            onFailure = { libraryState = UiState.Error(it.message ?: "Could not load your library") },
        )
        // A session refused mid-flight is dropped by the request itself, so the answer to "are we
        // signed in" is only settled once the call is over.
        youtubeSignedIn = DesktopYouTubeAuth.isSignedIn
    }

    fun here() = DesktopNavEntry(
        destination = destination,
        artist = openedArtist,
        collection = openedCollection,
        mood = selectedMoodGenre,
        showAll = libraryShowAll,
        replay = overlays.replay,
        settings = overlays.settingsPage,
    )

    // Every move between places is a visit, however it was made — the sidebar, a card, a search
    // hit, the player's credits. The place left is what back returns to.
    LaunchedEffect(Unit) {
        var last: DesktopNavEntry? = null
        snapshotFlow { here() }.collect { now ->
            val before = last
            if (before != null && before.key != now.key) {
                if (navRestoring[0]) {
                    navRestoring[0] = false
                } else {
                    navHistory.add(before)
                    if (navHistory.size > NAV_HISTORY_LIMIT) navHistory.removeAt(0)
                }
            }
            // The newest copy of the same place, so an album that has paged in more tracks comes
            // back with them.
            last = now
        }
    }

    /** Back to the place before this one — the top bar's arrow, the mouse's back button, Alt+Left. */
    fun goBack() {
        val entry = navHistory.removeLastOrNull() ?: return
        if (entry.key == here().key) return
        navRestoring[0] = true
        destination = entry.destination
        overlays.replay = entry.replay
        overlays.settingsPage = entry.settings
        collectionError = null
        openedCollection = entry.collection
        libraryShowAll = entry.showAll
        if (entry.mood != selectedMoodGenre) {
            selectedMoodGenre = entry.mood
            moodGenreShelves = UiState.Loading
        }
        if (entry.artist != openedArtist) {
            openedArtist = entry.artist
            if (entry.artist != null) {
                artistState = UiState.Loading
                artistReloads++
            }
        }
    }

    fun openMoodGenre(item: MoodGenre) {
        selectedMoodGenre = item
        moodGenreShelves = UiState.Loading
    }

    LaunchedEffect(selectedMoodGenre, moodGenreReloads) {
        val item = selectedMoodGenre ?: return@LaunchedEffect
        if (moodGenreShelves !is UiState.Loading) return@LaunchedEffect
        DesktopSearchClient.moodGenreShelves(item.browseId, item.params).fold(
            onSuccess = { shelves ->
                moodGenreShelves = if (shelves.isEmpty()) {
                    UiState.Error("Nothing to explore here yet")
                } else {
                    UiState.Success(shelves)
                }
            },
            onFailure = { moodGenreShelves = UiState.Error(it.message ?: "Could not load this category") },
        )
    }

    // The menu the shared player's "…" opens.
    var playerMenuOpen by remember(selectedSong?.videoId) { mutableStateOf(false) }
    // The player's own overflow (volume, the lyrics/output/queue row, the lyric
    // footer), raised from the song menu's "More…" and drawn by the shared
    // player. Held here so the desktop song menu can open it.
    val playerOverflowOpen = remember(selectedSong?.videoId) { mutableStateOf(false) }
    // Requests the song menu sends the player (switch lyrics/queue, translate),
    // flipped here and consumed by it, and the player's state mirrored back so the
    // menu's rows say what is on screen. Not keyed to the track: the player's own
    // panel and translation state outlive a skip, so copies recreated per track
    // would contradict it until it next changed.
    val playerQueueToggle = remember { mutableStateOf(false) }
    val playerQueueShowing = remember { mutableStateOf(false) }
    val playerTranslateToggle = remember { mutableStateOf(false) }
    val playerShowingTranslation = remember { mutableStateOf(false) }
    // Where the player's "…" is, in window coordinates, so its menu hangs off it.
    val playerMenuAnchor = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    /** The song menu's verbs for [song], as the player sheet offers them. */
    fun playerSongActions(song: Song) = DesktopSongActions(
        signedIn = youtubeSignedIn,
        disliked = song.videoId in dislikedIds,
        downloaded = downloads.any { it.videoId == song.videoId },
        downloadInProgress = song.videoId in downloadInProgress,
        sleepTimerMinutes = sleepTimerMinutes,
        sleepAfterTrack = sleepAfterTrack,
        onToggleDislike = ::toggleDislike,
        onAddToPlaylist = { playlistTarget = it },
        onDownload = ::downloadSong,
        onRemoveDownload = ::removeDownload,
        onStartRadio = ::startRadio,
        onStartJam = { song -> startWave(from = song) },
        onPlayNext = ::playNext,
        onAddToQueue = ::addToQueue,
        // Both leave the player, the way opening a page from Android's sheet collapses it.
        onOpenAlbum = { id ->
            overlays.nowPlaying = false
            openAlbum(id)
        },
        onOpenArtist = { id ->
            overlays.nowPlaying = false
            openArtist(id, song.artist)
        },
        onSleepTimer = { minutes ->
            if (minutes == null) DesktopSleepTimer.cancel() else DesktopSleepTimer.start(minutes)
        },
        onSleepAfterTrack = { DesktopSleepTimer.startAfterTrack() },
        onShare = ::shareSong,
    )

    // What the shared player reads as settings, kept in step with the window's own.
    SideEffect {
        DesktopPlayerSettings.animatedCanvas.value = animatedCanvas
        DesktopPlayerSettings.fullBleedArtwork.value = fullBleedArtwork
        DesktopPlayerSettings.legacyMeshGradient.value = legacyMeshGradient
        DesktopPlayerSettings.lyricsBlur.value = lyricsBlur
        DesktopPlayerSettings.syncedLyrics.value = syncedLyrics
        DesktopPlayerSettings.showNerdStats.value = showNerdStats
        DesktopPlayerSettings.smartFadeEnabled.value = automix
        DesktopPlayerSettings.lyricsSourceOrder.value =
            DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn).mapNotNull(::lyricsSourceNamed)
        DesktopPlayerHost.volumeLevel.value = volume
        DesktopPlayerHost.onVolumeChange = {
            volume = it
            persistence.saveString("volume", it.toString())
        }
    }

    // The engine's measured stream, as the player's quality badge and stats line read it.
    LaunchedEffect(playback.streamFormat, playback.streamSourceId) {
        NerdStats.current.value = playback.streamFormat?.nerdSnapshot(playback.streamSourceId)
    }
    LaunchedEffect(playback.searchingBetter, selectedSong?.videoId) {
        NerdStats.racingLossless.value = selectedSong?.videoId
            ?.takeIf { playback.searchingBetter }
            ?.let(::setOf)
            .orEmpty()
    }
    LaunchedEffect(playback.streamFormat, playback.isPlaying) {
        DesktopPlayerHost.pipeline.value = playbackEngine.pipeline()
    }
    DesktopPlayerHost.pipelineDialog = { onDismiss ->
        DesktopAudioPipelineDialog(
            format = playback.streamFormat,
            sourceName = playback.streamSourceId?.let { id ->
                sourceConfigs.firstOrNull { it.id == id }?.displayName
            },
            pipeline = playbackEngine.pipeline(),
            onDismiss = onDismiss,
        )
    }

    // What the player and the lyrics column beside the page both read, worked out once for the two.
    val sharedLyrics = lyrics?.lines
    val sharedLyricsSource = remember(lyrics) { lyrics?.source?.let(::lyricsSourceNamed) }
    val sharedLyricsUnavailable = !lyricsLoading && sharedLyrics.isNullOrEmpty()
    val providerStates = remember(lyricsOrder, lyricsOn, sharedLyricsSource, lyricsLoading) {
        DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
            .mapNotNull(::lyricsSourceNamed)
            .associateWith { source ->
                when {
                    source == sharedLyricsSource -> LyricsProviderState.FOUND
                    lyricsProviderResults.value[source] != null -> lyricsProviderResults.value.getValue(source)
                    lyricsLoading -> LyricsProviderState.FETCHING
                    else -> LyricsProviderState.NOT_FETCHED
                }
            }
    }

    // Every provider the song menu's lyrics drawer lists — all of them, in the
    // saved order, not just the enabled ones — each carrying what its last race
    // turned up so the row can read "Found", "Not found" or "Searching…". The
    // winner is the one the sheet shows now.
    // Derived, and read only by the menu that shows it: each source's answer arrives on its own,
    // and read here every one of them recomposed the whole window.
    val menuLyricsProviders = remember(
        lyricsOrder,
        lyricsOn,
        lyricsOnly,
        lyricsLoading,
        lyrics?.source,
    ) { derivedStateOf {
        val currentLabel = lyrics?.source
        val asked = lyricsOnly?.let { listOf(it) } ?: DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn)
        lyricsOrder.map { label ->
            val source = lyricsSourceNamed(label)
            val state = when {
                label == currentLabel -> LyricsProviderState.FOUND
                source != null && lyricsProviderResults.value[source] != null -> lyricsProviderResults.value.getValue(source)
                lyricsLoading && label in asked -> LyricsProviderState.FETCHING
                else -> LyricsProviderState.NOT_FETCHED
            }
            DesktopLyricsProviderRow(label, state, label == currentLabel)
        }
    } }

    // The queue's edits, for the player's queue and the queue column alike.
    fun removeFromQueue(at: Int) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.removeAt(at)
        if (edited !== liveQueue) {
            liveQueue = edited
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    fun moveInQueue(from: Int, to: Int) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.move(from, to)
        if (edited !== liveQueue) {
            liveQueue = edited
            saveQueue()
            partySyncHolder[0]?.onLocalIntent()
        }
    }

    // Clears what the listener queued by hand, as the phone's Clear does; the album and AutoPlay
    // stay where they are.
    fun clearQueue() {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val edited = liveQueue.withoutUserQueue()
        if (edited === liveQueue) return
        liveQueue = edited
        saveQueue()
        partySyncHolder[0]?.onLocalIntent()
    }

    fun seekPlayer(target: Long) {
        if (DesktopListenTogether.state.value.controlsLocked) return
        val duration = playback.durationMs
        playbackEngine.seekTo(
            if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L),
        )
        partySyncHolder[0]?.onLocalIntent()
    }

    val nowPlayingPlacement by DesktopStyleSettings.nowPlayingPlacement.collectAsState()
    val animatedHome by DesktopStyleSettings.animatedHome.collectAsState()

    // Where the home card is, for the player that floats over the page to sit on it.
    val heroAnchor = remember { DesktopHeroAnchor() }
    val onHomeFeed = destination == DesktopDestination.LISTEN_NOW && overlays.settingsPage == null &&
        openedArtist == null && openedCollection == null

    // My Jam's row along the foot of the "now playing" card, while the jam has joined it.
    val jamFootRow = @Composable { rowModifier: Modifier ->
        val phase = com.music.bitchord.ui.rememberLoopingFraction(2_600)
        DesktopJamRow(
            starting = waveStarting,
            playing = true,
            paused = !playback.isPlaying,
            current = selectedSong,
            phase = { phase.value },
            onPlayPause = ::togglePlayPauseFromUser,
            onSkip = ::playNext,
            onTune = { waveSettingsOpen = true },
            modifier = rowModifier,
        )
    }
    // Whether My Jam has joined the "now playing" card, eased — held here, where it lasts however far
    // the feed is scrolled. A drop taken into a drop: a little overshoot in, a firm one out.
    val jamJoined = selectedSong?.radioName == DesktopMyWave.radioName && animatedHome && waveShown
    val jamJoin = androidx.compose.animation.core.animateFloatAsState(
        if (jamJoined) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = if (jamJoined) 0.62f else 0.8f, stiffness = 200f),
        label = "jamJoin",
    )
    LaunchedEffect(Unit) {
        snapshotFlow { jamJoin.value.coerceIn(0f, 1.15f) }.collect { DesktopJamJoin.amount = it }
    }
    val jamFoot = jamFootRow.takeIf { jamJoined || DesktopJamJoin.amount > 0.01f }

    /** The glass bar along the bottom of the window, for [current]. */
    @Composable
    fun nowPlayingBar(current: Song, compact: Boolean, framed: Boolean = true) {
        DesktopNowPlayingBar(
            framed = framed,
            song = withSleeve(current),
            isPlaying = playback.isPlaying,
            isLoading = playback.isLoading,
            position = playerPosition,
            durationMs = playback.durationMs,
            hasPrevious = liveQueue.hasPrevious,
            shuffle = shuffle,
            repeat = when (repeatMode) {
                DesktopRepeatMode.OFF -> 0
                DesktopRepeatMode.ALL -> 1
                DesktopRepeatMode.ONE -> 2
            },
            volume = playback.volume,
            lyricsOpen = overlays.sidePanel == DesktopSidePanel.LYRICS,
            queueOpen = overlays.sidePanel == DesktopSidePanel.QUEUE,
            compact = compact,
            onPlayPause = ::togglePlayPauseFromUser,
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onSeek = ::seekPlayer,
            onToggleShuffle = { setShuffle(!shuffle) },
            onCycleRepeat = {
                repeatMode = repeatMode.next()
                persistence.saveString("repeat_mode", repeatMode.name)
            },
            onVolumeChange = {
                volume = it
                persistence.saveString("volume", it.toString())
            },
            onOpenLyrics = { overlays.toggleSidePanel(DesktopSidePanel.LYRICS) },
            onOpenQueue = { overlays.toggleSidePanel(DesktopSidePanel.QUEUE) },
            onExpand = { overlays.nowPlaying = true },
        )
    }

    /** The home card's controls, the same the bar has. */
    @Composable
    fun heroTransport(current: Song) {
        DesktopHeroTransport(
            song = current,
            isPlaying = playback.isPlaying,
            isLoading = playback.isLoading,
            position = playerPosition,
            durationMs = playback.durationMs,
            shuffle = shuffle,
            repeat = when (repeatMode) {
                DesktopRepeatMode.OFF -> 0
                DesktopRepeatMode.ALL -> 1
                DesktopRepeatMode.ONE -> 2
            },
            volume = playback.volume,
            onPlayPause = ::togglePlayPauseFromUser,
            onNext = ::playNext,
            onPrevious = ::playPrevious,
            onSeek = ::seekPlayer,
            onToggleShuffle = { setShuffle(!shuffle) },
            onCycleRepeat = {
                repeatMode = repeatMode.next()
                persistence.saveString("repeat_mode", repeatMode.name)
            },
            onVolumeChange = {
                volume = it
                persistence.saveString("volume", it.toString())
            },
        )
    }

    /** The home card's words, like and queue for [current]. */
    fun heroExtras(current: Song) = DesktopHeroExtras(
        lyricLines = sharedLyrics,
        position = playerPosition,
        liked = current.videoId in likedIds,
        queueOpen = overlays.sidePanel == DesktopSidePanel.QUEUE,
        onToggleLike = { toggleLike(current) },
        onOpenQueue = { overlays.toggleSidePanel(DesktopSidePanel.QUEUE) },
    )

    // The warm-up behind the cloak — see [DesktopStartupWarmup]: the player opened and closed and
    // the home card turned into the bar and back, for real, before the window is first seen.
    LaunchedEffect(Unit) {
        val cloaked = kotlinx.coroutines.withTimeoutOrNull(1_500) { DesktopStartupWarmup.cloaked.first { it } } ?: return@LaunchedEffect
        if (!cloaked) return@LaunchedEffect
        // The app starts with no track picked even when it restores a queue, and the player only
        // opens with one: the queue's or the last one played stands in for the warm-up — shown,
        // never played — and is put back to nothing after.
        val pickedAtStart = selectedSong
        val standIn = pickedAtStart ?: liveQueue.current ?: history.firstOrNull()
        try {
            if (pickedAtStart == null && standIn != null) selectedSong = standIn
            // The window's first frames, and the page laid out.
            repeat(12) { androidx.compose.runtime.withFrameNanos { } }
            // Words too, word-timed, as a track's lyrics arrive: the card's and the player's lyric
            // lines, their buttons and their text set out, all for the first time here rather than
            // on the frame a real track's lyrics come in. Taken away again after.
            if (lyrics == null && selectedSong != null) lyrics = WARMUP_LYRICS
            val song = selectedSong
            // Every step only while still cloaked: should the guard have shown the window
            // already, the listener is not to watch the player open and close by itself.
            fun stillHidden() = DesktopStartupWarmup.cloaked.value
            if (song != null && overlays.settingsPage == null && stillHidden()) {
                // The card into the bar and back: the morph's own first frames.
                if (animatedHome && destination == DesktopDestination.LISTEN_NOW) {
                    runCatching {
                        homeListState.animateScrollBy(700f, tween(160))
                        homeListState.animateScrollBy(-700f, tween(160))
                        homeListState.scrollToItem(0)
                    }
                }
                // The player, opened and put away as a click would.
                if (stillHidden()) {
                    overlays.nowPlaying = true
                    delay(WARMUP_OPEN_MS)
                    overlays.nowPlaying = false
                    delay(WARMUP_CLOSE_MS)
                    DesktopPlayerWarmup.markPrimed()
                }
            } else {
                repeat(30) { androidx.compose.runtime.withFrameNanos { } }
            }
        } finally {
            if (lyrics === WARMUP_LYRICS) lyrics = null
            if (pickedAtStart == null && selectedSong == standIn) selectedSong = null
            DesktopStartupWarmup.finish()
        }
    }

    // Checks the playlist import and My Wave against the live services: BITCHORD_SELFTEST=1.
    if (System.getenv("BITCHORD_SELFTEST") == "1") {
        LaunchedEffect(Unit) {
            System.getenv("BITCHORD_SELFTEST_IMPORT").orEmpty().split('|').filter(String::isNotBlank).forEach { link ->
                runCatching { DesktopPlaylistImport.fetch(link) }
                    .onSuccess { (name, tracks) -> DesktopTrackLog.log("selftest import ok: ${DesktopPlaylistImport.detect(link)} '$name' ${tracks.size} tracks, first ${tracks.take(2)}") }
                    .onFailure { DesktopTrackLog.log("selftest import failed: ${DesktopPlaylistImport.detect(link)}: ${it.message}") }
            }
            snapshotFlow { sessionSettled }.first { it }
            delay(3_000)
            if (System.getenv("BITCHORD_SELFTEST_SCROLL") == "1") {
                snapshotFlow { homeState is UiState.Success }.first { it }
                delay(3_000)
                fun where(label: String) = DesktopTrackLog.log(
                    "selftest scroll $label: first=${homeListState.firstVisibleItemIndex} offset=${homeListState.firstVisibleItemScrollOffset} " +
                        "song=${selectedSong != null} signedIn=$youtubeSignedIn hero=${heroAnchor.present} item0=${homeListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size}",
                )
                // Real wheel events, through the window as a mouse sends them, so every handler
                // on the way — the page's, the card's, the scrollbar — has its say.
                val target = java.awt.Window.getWindows().filterIsInstance<androidx.compose.ui.awt.ComposeWindow>().firstOrNull()
                fun notch(down: Boolean) {
                    val window = target ?: return
                    val component = generateSequence(listOf<java.awt.Component>(window)) { level ->
                        level.flatMap { (it as? java.awt.Container)?.components?.toList().orEmpty() }.ifEmpty { null }
                    }.flatten().lastOrNull { it.isShowing && it.width > 400 && it.height > 400 } ?: window
                    javax.swing.SwingUtilities.invokeLater {
                        component.dispatchEvent(
                            java.awt.event.MouseWheelEvent(
                                component, java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                                (component.width * 0.55).toInt(), (component.height * 0.6).toInt(), 0, false,
                                java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, if (down) 1 else -1,
                            ),
                        )
                    }
                }
                where("start")
                repeat(14) { notch(true); delay(250); where("down ${it + 1}") }
                delay(3_000)
                where("rest")
                repeat(18) { notch(false); delay(250); where("up ${it + 1}") }
                DesktopTrackLog.log("selftest done")
                return@LaunchedEffect
            }
            if (System.getenv("BITCHORD_SELFTEST_JAM") == "1") {
                startWave()
                DesktopTrackLog.log("selftest jam started")
                return@LaunchedEffect
            }
            DesktopWaveTuning.entries.forEach { tuning ->
                DesktopMyWave.setTuning(tuning)
                val liked = (libraryState as? UiState.Success)?.data?.likedSongs.orEmpty()
                val batch = DesktopMyWave.start(liked, history)
                DesktopTrackLog.log("selftest wave $tuning: ${batch.size} tracks, e.g. ${batch.take(3).map { it.artist + " - " + it.title }}")
            }
            DesktopMyWave.setTuning(DesktopWaveTuning.ALL)
            DesktopTrackLog.log("selftest analysis library available: ${DesktopAnalysisRuntime.available}")
            DesktopTrackLog.log("selftest done")
        }
    }

    // A walk through the app's heaviest moments, timed frame by frame — see [DesktopFrameProfiler].
    if (DesktopFrameProfiler.enabled) {
        LaunchedEffect(Unit) {
            DesktopFrameProfiler.instrumentPresent()
            val hz = runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
            }.getOrDefault(60)
            // Switched off for this run only, to see what each costs: BITCHORD_PROFILE_OFF=glass,living
            val off = System.getenv("BITCHORD_PROFILE_OFF").orEmpty().split(',').map(String::trim).toSet()
            val glassBefore = DesktopStyleSettings.liquidGlass.value
            val livingBefore = DesktopStyleSettings.animatedHome.value
            if ("glass" in off) DesktopStyleSettings.setLiquidGlass(false)
            if ("living" in off) DesktopStyleSettings.setAnimatedHome(false)
            // The feed loaded and settled first, as a listener would find it.
            kotlinx.coroutines.withTimeoutOrNull(40_000) {
                androidx.compose.runtime.snapshotFlow { homeState is UiState.Success }.first { ready -> ready }
            }
            kotlinx.coroutines.delay(8_000)
            DesktopFrameProfiler.countIdleFrames("start-home", 3_000)
            // A track to show, picked and left paused: the player only opens with one.
            if (selectedSong == null) {
                (homeState as? UiState.Success)?.data?.flatMap { it.items }?.firstOrNull { it.videoId != null }
                    ?.let { playSong(it.toSong(), startPlaying = false) }
                kotlinx.coroutines.delay(4_000)
            }
            // BITCHORD_PROFILE_LOAD=1: what the app costs the rest of the computer while a track
            // plays — the processor, in whole cores, and frames drawn — on Home, with the player
            // open, and minimised. The volume is the profile's own, set to nothing beforehand.
            if (System.getenv("BITCHORD_PROFILE_LOAD") == "1") {
                // The packaged runtime has no jdk.management: the process's CPU time from ProcessHandle.
                fun cpuNanos(): Long = ProcessHandle.current().info().totalCpuDuration().map { it.toNanos() }.orElse(0L)
                val threads = java.lang.management.ManagementFactory.getThreadMXBean()
                fun threadTimes(): Map<String, Long> = threads.allThreadIds.toList().mapNotNull { id ->
                    threads.getThreadInfo(id)?.threadName?.let { it to threads.getThreadCpuTime(id) }
                }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
                suspend fun phase(label: String) {
                    kotlinx.coroutines.delay(4_000)
                    val threads0 = threadTimes()
                    val cpu0 = cpuNanos()
                    val wall0 = System.nanoTime()
                    val frames0 = DesktopPrefetch.framesDrawn
                    kotlinx.coroutines.delay(14_000)
                    DesktopFrameProfiler.countIdleFrames(label, 6_000)
                    val cores = (cpuNanos() - cpu0).toDouble() / (System.nanoTime() - wall0)
                    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null
                    DesktopTrackLog.log(
                        "load %-16s cpu=%.2f cores  frames=%d  playing=%s focused=%s".format(
                            label, cores, DesktopPrefetch.framesDrawn - frames0, playbackEngine.state.value.isPlaying, focused,
                        ),
                    )
                    // Each thread's share over the phase alone, in thousandths of a core.
                    val seconds = (System.nanoTime() - wall0) / 1e9
                    val busiest = threadTimes().map { (name, ns) -> name to (ns - (threads0[name] ?: 0L)) }.sortedByDescending { it.second }
                    DesktopTrackLog.log("load   threads: " + busiest.take(8).joinToString { "${it.first}=%.2f".format(it.second / 1e9 / seconds) })
                }
                if (!playbackEngine.state.value.isPlaying) togglePlayPauseFromUser()
                // BITCHORD_PROFILE_LOAD_PLAYER=1: the open player alone, quickly — for comparing
                // what its layers cost, one launch per layer switched off.
                if (System.getenv("BITCHORD_PROFILE_LOAD_PLAYER") == "1") {
                    overlays.nowPlaying = true
                    phase("player-only")
                    // And how even its frames are: gaps, misses, and the GPU's share of each.
                    DesktopFrameProfiler.measure("player-even", hz) { kotlinx.coroutines.delay(10_000) }
                    DesktopTrackLog.log("profile done")
                    return@LaunchedEffect
                }
                phase("home-playing")
                overlays.nowPlaying = true
                phase("player-playing")
                overlays.nowPlaying = false
                kotlinx.coroutines.delay(1_500)
                DesktopTrackLog.log("load minimise via frame: " + DesktopWindowsFrame.minimize())
                phase("minimised-playing")
                togglePlayPauseFromUser()
                phase("minimised-paused")
                DesktopTrackLog.log("profile done")
                return@LaunchedEffect
            }
            // BITCHORD_PROFILE_VIDEO=1: a music video, played silent with the player open — for
            // what playing one costs in memory, measured from outside.
            if (System.getenv("BITCHORD_PROFILE_VIDEO") == "1") {
                val clip = System.getenv("BITCHORD_PROFILE_VIDEO_ID")?.let { id ->
                    Song(videoId = id, title = "Profile clip", artist = "", thumbnailUrl = null, isVideo = true)
                }
                DesktopTrackLog.log("profile video clip=${clip?.videoId}")
                if (clip != null) {
                    volume = 0f
                    playSong(clip)
                    kotlinx.coroutines.delay(3_000)
                    DesktopTrackLog.log("profile video: ${selectedSong?.title} isVideo=${selectedSong?.isVideo}")
                    DesktopFrameProfiler.countIdleFrames("playing-home", 3_000)
                    overlays.nowPlaying = true
                    kotlinx.coroutines.delay(60_000)
                    overlays.nowPlaying = false
                    kotlinx.coroutines.delay(20_000)
                    togglePlayPauseFromUser()
                }
                DesktopTrackLog.log("profile done")
                return@LaunchedEffect
            }
            DesktopTrackLog.log("profile off=$off song=${selectedSong?.title} placement=${nowPlayingPlacement}")
            homeListState.scrollToItem(0)
            kotlinx.coroutines.delay(1_500)
            // BITCHORD_PROFILE_HOME_SCROLL=1: Home scrolled steadily up and down, and nothing else.
            if (System.getenv("BITCHORD_PROFILE_HOME_SCROLL") == "1") {
                DesktopTrackLog.log("profile cards composed before the scroll: ${com.music.bitchord.ui.screens.HomeDiagnostics.cardsComposed}")
                DesktopFrameProfiler.measure("home-scroll", hz) {
                    repeat(4) {
                        homeListState.animateScrollBy(2_400f, tween(1_000, easing = androidx.compose.animation.core.LinearEasing))
                        homeListState.animateScrollBy(-2_400f, tween(1_000, easing = androidx.compose.animation.core.LinearEasing))
                    }
                }
                DesktopTrackLog.log("profile done")
                return@LaunchedEffect
            }
            DesktopFrameProfiler.measure("home-still", hz) { kotlinx.coroutines.delay(2_000) }
            DesktopFrameProfiler.measure("morph-cold", hz) {
                homeListState.animateScrollBy(900f, tween(1_200))
                homeListState.animateScrollBy(-900f, tween(1_200))
            }
            DesktopFrameProfiler.measure("morph-warm", hz) {
                homeListState.animateScrollBy(900f, tween(1_200))
                homeListState.animateScrollBy(-900f, tween(1_200))
            }
            suspend fun cycle(label: String) {
                // The pointer reaches the bar a moment before the click, as it would.
                DesktopPlayerWarmup.request()
                if (System.getenv("BITCHORD_PROFILE_REPRIME") == "1") {
                    DesktopPlayerWarmup.primeAgain++
                    kotlinx.coroutines.delay(2_500)
                    DesktopPlayerWarmup.request()
                }
                kotlinx.coroutines.delay(400)
                DesktopFrameProfiler.measure("$label-open", hz) {
                    overlays.nowPlaying = true
                    kotlinx.coroutines.delay(1_200)
                }
                DesktopFrameProfiler.measure("$label-close", hz) {
                    overlays.nowPlaying = false
                    kotlinx.coroutines.delay(1_000)
                }
            }
            // Notches of a wheel, as a hand turns one: the morph led by the gliding scroll.
            suspend fun wheelNotches(down: Boolean) {
                repeat(8) {
                    homeWheel.add(if (down) 240f else -240f)
                    kotlinx.coroutines.delay(110)
                }
                kotlinx.coroutines.delay(500)
            }
            DesktopFrameProfiler.measure("wheel-top", hz) { wheelNotches(true); wheelNotches(false) }
            val placementBefore = DesktopStyleSettings.nowPlayingPlacement.value
            DesktopStyleSettings.setNowPlayingPlacement(DesktopNowPlayingPlacement.BOTTOM)
            kotlinx.coroutines.delay(1_500)
            DesktopFrameProfiler.measure("wheel-bottom", hz) { wheelNotches(true); wheelNotches(false) }
            DesktopStyleSettings.setNowPlayingPlacement(placementBefore)
            kotlinx.coroutines.delay(1_000)
            DesktopFrameProfiler.countIdleFrames("paused-home", 3_000)
            // BITCHORD_PROFILE_IDLETEST=1: does a still window make the next open stutter, and does
            // drawing for a moment before it put that right?
            if (System.getenv("BITCHORD_PROFILE_IDLETEST") == "1") {
                suspend fun openClose(label: String) {
                    DesktopFrameProfiler.measure(label, hz) {
                        overlays.nowPlaying = true
                        kotlinx.coroutines.delay(1_200)
                    }
                    overlays.nowPlaying = false
                    kotlinx.coroutines.delay(1_200)
                }
                repeat(3) {
                    kotlinx.coroutines.delay(20_000)
                    openClose("still-open")
                    kotlinx.coroutines.delay(20_000)
                    DesktopPlayerWarmup.request()
                    kotlinx.coroutines.delay(900)
                    openClose("ramped-open")
                }
                DesktopTrackLog.log("profile done")
                return@LaunchedEffect
            }
            if (System.getenv("BITCHORD_PROFILE_SHORT") == "1") {
                repeat(4) { cycle("short$it") }
                // As if minimised: what the memory trim hands back — see [DesktopMemoryTrim].
                DesktopMemoryTrim.simulateHidden()
                kotlinx.coroutines.delay(9_000)
                DesktopTrackLog.log("profile done")
                return@LaunchedEffect
            }
            cycle("cold")
            cycle("warm1")
            cycle("warm2")
            DesktopTrackLog.log("profile idling 75s")
            if ("keepwarm" in off) {
                // Frames drawn all through the wait: if the open after it is then smooth, what
                // made it slow was the hardware winding down while nothing moved.
                kotlinx.coroutines.withTimeoutOrNull(75_000) { while (true) androidx.compose.runtime.withFrameNanos { } }
            } else {
                kotlinx.coroutines.delay(75_000)
            }
            cycle("idle")
            DesktopFrameProfiler.measure("morph-idle", hz) {
                homeListState.animateScrollBy(900f, tween(1_200))
                homeListState.animateScrollBy(-900f, tween(1_200))
            }
            DesktopStyleSettings.setLiquidGlass(glassBefore)
            DesktopStyleSettings.setAnimatedHome(livingBefore)
            DesktopTrackLog.log("profile done")
        }
    }

    // The wheel over the home card's morph does it whole: a turn down while the card is still in
    // view carries the feed — in one eased stroke — to where the card has fully become the bar; a
    // turn up from there carries it back to the top. Never stopped part-way, where the morph would
    // follow the wheel notch by notch.
    val homeHeaderSize = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    homeWheel.snap = remember {
        snap@{ delta ->
            if (!animatedHome || selectedSong == null) return@snap null
            val first = homeListState.firstVisibleItemIndex
            val offset = homeListState.firstVisibleItemScrollOffset
            homeListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { homeHeaderSize.intValue = it.size }
            val header = homeHeaderSize.intValue
            if (header <= 0) return@snap null
            when {
                delta > 0f && first == 0 && header - offset > 1 -> (header - offset).toFloat()
                delta < 0f && first == 0 && offset > 0 -> -offset.toFloat()
                // Just past the header: back up to it in one stroke. The jam's row (item 1) is folded
                // into the card while the jam plays, so then the first shelf is the one under it.
                delta < 0f && offset == 0 && (first == 1 || (first == 2 && DesktopJamJoin.amount >= 0.99f)) -> -header.toFloat()
                else -> null
            }
        }
    }
    LaunchedEffect(Unit) { DesktopPlayerWarmup.expire() }
    LaunchedEffect(Unit) { DesktopPlayerWarmup.keepGpuAwake() }
    // Once per launch, a few seconds after there is something to show: the player is built out
    // of sight, so its code is compiled and its shaders made before the first time it is opened
    // rather than during it.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { selectedSong != null }.first { it }
        kotlinx.coroutines.delay(5_000)
        if (!overlays.nowPlaying) DesktopPlayerWarmup.request()
    }

    // With the bar at the bottom, the home card stands in for it while enough of the card shows.
    val heroCoversBar by remember { derivedStateOf { heroAnchor.present && heroAnchor.visible > 0.3f } }

    MaterialTheme(
        colorScheme = desktopColorScheme(),
        typography = desktopTypography(),
    ) {
        val ambientBackdrop by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
        val windowActions = LocalDesktopWindowActions.current
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalNowPlaying provides selectedSong,
        ) {
            // The track's pictures onto the GPU as they load, before the player or the card shows
            // them — see [DesktopTextureWarmer]. A pixel, under the window's own frame.
            DesktopTextureWarmer(selectedSong?.let(::withSleeve)?.thumbnailUrl)
            DesktopFrame(
                // The phone's page is black, not the near-black of its cards.
                containerColor = DesktopBackground,
                // Only Replay dresses itself unless ambient backdrop is on; everywhere else the chrome sits on the plain surface.
                backdrop = { transparentBase ->
                    val ambientArtworkUrl = replaySummary.songs.firstOrNull()?.song?.thumbnailUrl
                        .takeIf { overlays.replay }
                        ?: selectedSong?.thumbnailUrl.takeIf { ambientBackdrop }
                    DesktopPageBackdrop(
                        artworkUrl = ambientArtworkUrl,
                        transparentBase = transparentBase,
                        livingCoverUrl = selectedSong?.thumbnailUrl,
                        living = animatedHome,
                    )
                },
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.MediaPlayPause -> {
                            if (selectedSong != null) togglePlayPauseFromUser()
                            true
                        }
                        event.key == Key.MediaNext -> {
                            playNext()
                            true
                        }
                        event.key == Key.MediaPrevious -> {
                            playPrevious()
                            true
                        }
                        event.key == Key.F11 -> {
                            DesktopWindowMode.toggle()
                            true
                        }
                        event.key == Key.DirectionLeft && event.isAltPressed && !overlays.nowPlaying -> {
                            goBack()
                            true
                        }
                        DesktopPlatform.isMac && event.isMetaPressed && event.key == Key.Comma -> {
                            openSettings()
                            true
                        }
                        DesktopPlatform.isMac && event.isMetaPressed && event.key == Key.F -> {
                            searchFocusRequested = true
                            true
                        }
                        DesktopPlatform.isMac && event.isMetaPressed && event.key == Key.W -> {
                            windowActions?.close?.invoke()
                            true
                        }
                        DesktopPlatform.isMac && event.isMetaPressed && event.key == Key.M -> {
                            windowActions?.minimize?.invoke()
                            true
                        }
                        // One layer at a time, innermost first: the player's own side panel, then
                        // the player.
                        event.key == Key.Escape -> when {
                            // The player's own layers first — the lyrics, the queue, a
                            // drawer — in the order Android's back reaches them.
                            overlays.nowPlaying && PlayerBack.dispatch() -> true
                            overlays.nowPlaying -> {
                                overlays.nowPlaying = false
                                true
                            }
                            overlays.sidePanel != null -> {
                                overlays.sidePanel = null
                                true
                            }
                            // A page of Settings steps back the way the back button does, unless
                            // one of its prompts is up over it.
                            overlays.settingsPage != null &&
                                !overlays.lastfmLogin && !overlays.listenBrainzToken && !overlays.discordToken -> {
                                goBack()
                                true
                            }
                            else -> false
                        }
                        else -> false
                    }
                },
                topBar = { compact ->
                    // The live state here alone: its progress line is the one thing up here that
                    // follows the playhead.
                    val live by playbackEngine.state.collectAsState()
                    DesktopTopBar(
                        compact = compact,
                        song = selectedSong,
                        isPlaying = playback.isPlaying,
                        isLoading = playback.isLoading,
                        previousEnabled = liveQueue.hasPrevious ||
                            live.positionMs > BACK_RESTARTS_AFTER_MS,
                        progress = if (live.durationMs > 0L) {
                            live.positionMs.toFloat() / live.durationMs.toFloat()
                        } else {
                            0f
                        },
                        volume = playback.volume,
                        shuffle = shuffle,
                        repeatMode = repeatMode,
                        onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                        onPrevious = ::playPrevious,
                        onNext = ::playNext,
                        onShuffleChange = ::setShuffle,
                        onRepeatModeChange = {
                            repeatMode = it
                            persistence.saveString("repeat_mode", it.name)
                        },
                        onOpenNowPlaying = { overlays.nowPlaying = true },
                        onVolumeChange = {
                            volume = it
                            persistence.saveString("volume", it.toString())
                        },
                        onOpenAudioOutput = { overlays.audioOutput = true },
                        // A second click on the same button puts the column away, as in Apple Music.
                        onOpenLyrics = { overlays.toggleSidePanel(DesktopSidePanel.LYRICS) },
                        onOpenQueue = { overlays.toggleSidePanel(DesktopSidePanel.QUEUE) },
                        sidePanel = overlays.sidePanel,
                        accountAvatar = activeAccount?.profiles?.firstOrNull { it.profileId == activeProfileId }?.avatar?.takeIf { it.isNotBlank() }
                            ?: activeAccount?.avatar?.takeIf { it.isNotBlank() }
                            ?: activeAccount?.profiles?.firstOrNull()?.avatar?.takeIf { it.isNotBlank() },
                        onOpenAccounts = { DesktopTrackLog.log("accounts: opening the switcher"); overlays.accounts = true },
                        canGoBack = navHistory.isNotEmpty(),
                        onBack = ::goBack,
                        // The glass player, top or bottom, carries the transport now; an empty
                        // display with nothing playing was a player for show.
                        showTransport = false,
                        onOpenAllSettings = ::openSettings,
                    )
                },
                bottomPlayer = selectedSong
                    ?.takeIf { nowPlayingPlacement == DesktopNowPlayingPlacement.BOTTOM }
                    ?.let { current ->
                        {
                            nowPlayingBar(current, compact = false)
                        }
                    },
                bottomPlayerRoom = selectedSong != null && nowPlayingPlacement == DesktopNowPlayingPlacement.BOTTOM,
                // Out as far as the home card has gone under the top of the feed: the bar rises with
                // the scroll and sinks back with it, exactly in step, like the top player's morph.
                bottomReveal = {
                    if (heroAnchor.present) ((BOTTOM_REVEAL_FROM - heroAnchor.visible) / BOTTOM_REVEAL_FROM).coerceIn(0f, 1f) else 1f
                },
                topPlayer = selectedSong
                    ?.takeIf { nowPlayingPlacement == DesktopNowPlayingPlacement.TOP && DesktopFrameProfiler.on("topplayer") }
                    ?.let { current ->
                        {
                            DesktopMorphingPlayer(
                                anchor = heroAnchor,
                                song = withSleeve(current),
                                isPlaying = playback.isPlaying,
                                onOpenPlayer = { overlays.nowPlaying = true },
                                transport = { heroTransport(current) },
                                extras = heroExtras(current),
                                // A wheel turned over the card glides the feed it sits on.
                                wheel = homeWheel,
                                jamFoot = jamFoot,
                            ) { barModifier ->
                                Box(barModifier) { nowPlayingBar(current, compact = false, framed = false) }
                            }
                        }
                    },
                // The home feed's own header makes its room; every other page clears the pinned
                // bar, or at least stands off the top bar.
                topInset = when {
                    onHomeFeed -> 0.dp
                    nowPlayingPlacement == DesktopNowPlayingPlacement.TOP && selectedSong != null -> TOP_PLAYER_INSET
                    else -> PAGE_TOP_GAP
                },
                sidebar = {
                    DesktopSidebar(
                        destination = destination,
                        settingsOpen = overlays.settingsPage != null,
                        accountPlaylists = sidebarAccountPlaylists,
                        localPlaylists = playlists,
                        openedCollectionId = openedCollection?.browseId,
                        query = query,
                        onQueryChange = { text ->
                            editQuery(text)
                            if (destination != DesktopDestination.SEARCH || overlays.settingsPage != null) {
                                selectDestination(DesktopDestination.SEARCH)
                            }
                        },
                        onSearch = ::search,
                        onDestinationSelected = ::selectDestination,
                        onOpenAccountPlaylist = { openShelfItem(it, PLAYLISTS_SHELF) },
                        onOpenLocalPlaylist = ::openPlaylist,
                        onImportPlaylist = { spotifyImportOpen = true },
                        onOpenSettings = ::openSettings,
                        accountAvatar = activeAccount?.avatar
                            ?: activeAccount?.profiles?.firstOrNull()?.avatar,
                        accountName = activeAccount?.let { account ->
                            account.profiles.firstOrNull { it.profileId == activeProfileId }?.name?.takeIf(String::isNotBlank)
                                ?: account.name.takeIf(String::isNotBlank)
                        },
                        onOpenAccounts = {
                            if (youtubeSignedIn) overlays.accounts = true else overlays.signIn = true
                        },
                        focusSearch = searchFocusRequested,
                        onSearchFocused = { searchFocusRequested = false },
                    )
                },
                bottomBar = { compact ->
                    DesktopBottomChrome(
                        compact = compact,
                        destination = destination,
                        song = selectedSong,
                        isPlaying = playback.isPlaying,
                        onDestinationSelected = ::selectDestination,
                        onExpand = { overlays.nowPlaying = true },
                        onPlayPause = { if (selectedSong != null) togglePlayPauseFromUser() },
                        onNext = ::playNext,
                        nowPlaying = selectedSong
                            ?.takeIf { nowPlayingPlacement == DesktopNowPlayingPlacement.BOTTOM }
                            ?.let { current -> { nowPlayingBar(current, compact = true) } },
                    )
                },
                trailing = {
                    val current = selectedSong
                    DesktopSidePanelColumn(
                        panel = overlays.sidePanel.takeIf { current != null },
                        onClose = { overlays.sidePanel = null },
                    ) { which ->
                        if (current == null) return@DesktopSidePanelColumn
                        when (which) {
                            DesktopSidePanel.LYRICS -> LyricsSidePanel(
                                song = current,
                                isPlaying = playback.isPlaying,
                                position = playerPosition,
                                lyrics = sharedLyrics,
                                lyricsSource = sharedLyricsSource,
                                lyricsProviderStates = providerStates,
                                onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                                lyricsUnavailable = sharedLyricsUnavailable,
                                onSeek = ::seekPlayer,
                                modifier = Modifier.fillMaxSize(),
                            )
                            DesktopSidePanel.QUEUE -> QueueSidePanel(
                                queue = liveQueue.songs,
                                queueIndex = liveQueue.index,
                                autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                                onJumpTo = ::playQueueIndex,
                                onRemove = ::removeFromQueue,
                                onMove = ::moveInQueue,
                                onClear = ::clearQueue,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
                overlay = {
                    DesktopPlayerSheet(
                        visible = overlays.nowPlaying && selectedSong != null,
                        // Kept built, out of sight, for as long as there is a track: opening it is
                        // then only the slide, never the first frames of a whole new player too.
                        // The disc player only: it stands still while hidden. The classic one keeps
                        // its own animations going, so it is still built when asked for.
                        primeKey = selectedSong?.videoId,
                        prepare = selectedSong != null && DesktopFrameProfiler.on("prepare") &&
                            (playerStyle == DesktopPlayerStyle.DISC || DesktopPlayerWarmup.wanted),
                        onDismiss = {
                            DesktopWindowMode.exit()
                            overlays.nowPlaying = false
                        },
                        showCollapseButton = playerStyle == DesktopPlayerStyle.CLASSIC,
                    ) { windowWidth, windowHeight ->
                        val current = selectedSong ?: return@DesktopPlayerSheet
                        val playerSong = trackLinks?.takeIf { it.videoId == current.videoId }
                            ?.let { extra ->
                                current.copy(
                                    artistId = current.artistId ?: extra.artistId,
                                    // Same as on the phone: the watch-queue lookup
                                    // is what carries a channel per credited
                                    // artist, so it is what makes every name in
                                    // the credit line openable.
                                    artists = current.artists.ifEmpty { extra.artists },
                                    albumId = current.albumId ?: extra.albumId,
                                    albumName = current.albumName ?: extra.albumName,
                                )
                            }
                            ?: current
                        if (playerStyle == DesktopPlayerStyle.DISC) {
                            DesktopDiscPlayer(
                                // Marked as the video it is, so its clip and the switch read it right.
                                song = if (playerSong.looksLikeVideo() && !playerSong.isVideo) playerSong.copy(isVideo = true) else playerSong,
                                // A music video's own thumbnail is a frame of the video; the disc
                                // wants the song's sleeve, which its song version carries.
                                coverUrl = if (playerSong.looksLikeVideo()) {
                                    (versionPairs[playerSong.videoId] ?: versionAlternate)
                                        ?.takeIf { !it.looksLikeVideo() }?.thumbnailUrl
                                        ?: playerSong.thumbnailUrl
                                } else {
                                    playerSong.thumbnailUrl
                                },
                                isPlaying = playback.isPlaying,
                                isLoading = playback.isLoading,
                                position = playerPosition,
                                durationMs = playback.durationMs,
                                hasPrevious = liveQueue.hasPrevious,
                                hasNext = liveQueue.hasNext,
                                shuffle = shuffle,
                                repeat = when (repeatMode) {
                                    DesktopRepeatMode.OFF -> 0
                                    DesktopRepeatMode.ALL -> 1
                                    DesktopRepeatMode.ONE -> 2
                                },
                                liked = current.videoId in likedIds,
                                volume = playback.volume,
                                versionSwitch = DesktopVersionSwitch(
                                    alternate = versionAlternate,
                                    searching = versionSearching,
                                    onSwitch = ::switchVersion,
                                ),
                                onPlayPause = ::togglePlayPauseFromUser,
                                onNext = ::playNext,
                                onPrevious = ::playPrevious,
                                onSeek = ::seekPlayer,
                                onToggleShuffle = { setShuffle(!shuffle) },
                                onCycleRepeat = {
                                    repeatMode = when (repeatMode) {
                                        DesktopRepeatMode.OFF -> DesktopRepeatMode.ALL
                                        DesktopRepeatMode.ALL -> DesktopRepeatMode.ONE
                                        DesktopRepeatMode.ONE -> DesktopRepeatMode.OFF
                                    }
                                    persistence.saveString("repeat_mode", repeatMode.name)
                                },
                                onToggleLike = { toggleLike(current) },
                                onVolumeChange = {
                                    volume = it
                                    persistence.saveString("volume", it.toString())
                                },
                                onCollapse = {
                                    DesktopWindowMode.exit()
                                    overlays.nowPlaying = false
                                },
                                onOpenMenu = { playerMenuOpen = true },
                                onOpenArtist = {
                                    overlays.nowPlaying = false
                                    val id = playerSong.artistId
                                    if (id != null) {
                                        openArtist(id, playerSong.artist)
                                    } else {
                                        scope.launch {
                                            YtMusicRepository.findArtistPageId(playerSong.artist).getOrNull()
                                                ?.let { found -> openArtist(found, playerSong.artist) }
                                        }
                                    }
                                },
                                onOpenAlbum = playerSong.albumId?.let { id ->
                                    {
                                        overlays.nowPlaying = false
                                        openAlbum(id)
                                    }
                                },
                                lyricLines = sharedLyrics,
                                onOpenAudioOutput = { overlays.audioOutput = true },
                                onListenTogether = { overlays.listenTogether = true },
                                onOpenPlaybackSource = {
                                    val id = playerSong.playbackSourceId
                                    overlays.nowPlaying = false
                                    if (playerSong.playbackSourceType == PlaybackSourceType.BROWSE && id != null) {
                                        openAlbum(id)
                                    }
                                },
                                lyricsPanel = { panelModifier ->
                                    if (DesktopFrameProfiler.recompositions) androidx.compose.runtime.SideEffect { DesktopFrameProfiler.count("lyricsPanel") }
                                    LyricsSidePanel(
                                        song = current,
                                        isPlaying = playback.isPlaying,
                                        position = playerPosition,
                                        lyrics = sharedLyrics,
                                        lyricsSource = sharedLyricsSource,
                                        lyricsProviderStates = providerStates,
                                        onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                                        lyricsUnavailable = sharedLyricsUnavailable,
                                        onSeek = ::seekPlayer,
                                        modifier = panelModifier,
                                    )
                                },
                                queuePanel = { panelModifier ->
                                    if (DesktopFrameProfiler.recompositions) androidx.compose.runtime.SideEffect { DesktopFrameProfiler.count("queuePanel") }
                                    QueueSidePanel(
                                        queue = liveQueue.songs,
                                        queueIndex = liveQueue.index,
                                        autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                                        onJumpTo = ::playQueueIndex,
                                        onRemove = ::removeFromQueue,
                                        onMove = ::moveInQueue,
                                        onClear = ::clearQueue,
                                        modifier = panelModifier,
                                    )
                                },
                            )
                        } else NowPlayingScreen(
                            song = playerSong,
                            isPlaying = playback.isPlaying,
                            isLoading = playback.isLoading,
                            position = playerPosition,
                            durationMs = playback.durationMs,
                            audioVersionSwitching = false,
                            qualityUpgraded = false,
                            queue = liveQueue.songs,
                            queueIndex = liveQueue.index,
                            hasPrevious = liveQueue.hasPrevious,
                            hasNext = liveQueue.hasNext,
                            repeatMode = when (repeatMode) {
                                DesktopRepeatMode.OFF -> RepeatModes.OFF
                                DesktopRepeatMode.ONE -> RepeatModes.ONE
                                DesktopRepeatMode.ALL -> RepeatModes.ALL
                            },
                            shuffleEnabled = shuffle,
                            autoplayEnabled = DesktopAutoplay.enabled(partyState, autoplay),
                            signedIn = youtubeSignedIn,
                            accountName = activeAccount?.profiles?.firstOrNull { it.profileId == activeProfileId }?.name?.takeIf(String::isNotBlank)
                                ?: activeAccount?.name?.takeIf(String::isNotBlank),
                            likeStatus = when (current.videoId) {
                                in likedIds -> LikeStatus.LIKE
                                in dislikedIds -> LikeStatus.DISLIKE
                                else -> LikeStatus.INDIFFERENT
                            },
                            onToggleLike = { toggleLike(current) },
                            onPlayPause = ::togglePlayPauseFromUser,
                            onNext = ::playNext,
                            onPrevious = ::playPrevious,
                            onBlockedControl = {
                                DesktopPlayerHost.showMessage("Only the party host can change playback")
                            },
                            onSeek = ::seekPlayer,
                            onSeekFraction = { fraction ->
                                val duration = playbackEngine.state.value.durationMs
                                if (duration > 0) seekPlayer((fraction * duration).toLong())
                            },
                            onToggleShuffle = { setShuffle(!shuffle) },
                            // The phone's order: off, all, one.
                            onCycleRepeat = {
                                repeatMode = when (repeatMode) {
                                    DesktopRepeatMode.OFF -> DesktopRepeatMode.ALL
                                    DesktopRepeatMode.ALL -> DesktopRepeatMode.ONE
                                    DesktopRepeatMode.ONE -> DesktopRepeatMode.OFF
                                }
                                persistence.saveString("repeat_mode", repeatMode.name)
                            },
                            onToggleAutoplay = {
                                setAutoplay(!DesktopAutoplay.enabled(DesktopListenTogether.state.value, autoplay))
                            },
                            onJumpTo = ::playQueueIndex,
                            onRemoveFromQueue = ::removeFromQueue,
                            onMoveInQueue = ::moveInQueue,
                            onClearQueue = ::clearQueue,
                            onOpenMenu = { playerMenuOpen = true },
                            onOpenAlbum = { id ->
                                overlays.nowPlaying = false
                                openAlbum(id)
                            },
                            // The lead credit carries the track's own channel
                            // id and opens straight away; anyone else on the
                            // line has no id, so the page is found from the
                            // name — and only when the answer is that name
                            // exactly, so a fragment like "2115" opens nothing
                            // rather than White 2115.
                            onOpenArtist = { id, name ->
                                overlays.nowPlaying = false
                                if (id != null) {
                                    openArtist(id, name)
                                } else {
                                    scope.launch {
                                        YtMusicRepository.findArtistPageId(name).getOrNull()
                                            ?.let { found -> openArtist(found, name) }
                                    }
                                }
                            },
                            onOpenPlaybackSource = {
                                val id = playerSong.playbackSourceId
                                overlays.nowPlaying = false
                                if (playerSong.playbackSourceType == PlaybackSourceType.BROWSE && id != null) {
                                    openAlbum(id)
                                }
                            },
                            onListenTogether = {
                                overlays.nowPlaying = false
                                overlays.listenTogether = true
                            },
                            lyrics = sharedLyrics,
                            lyricsSource = sharedLyricsSource,
                            lyricsProviderStates = providerStates,
                            onSelectLyricsProvider = { source -> lyricsOnly = source.label },
                            lyricsUnavailable = sharedLyricsUnavailable,
                            lyricsOffsetOpen = false,
                            onDismissLyricsOffset = {},
                            windowWidth = windowWidth,
                            windowHeight = windowHeight,
                            playerOverflowOpen = playerOverflowOpen,
                            playerQueueToggle = playerQueueToggle,
                            playerQueueShowing = playerQueueShowing,
                            playerMenuAnchor = playerMenuAnchor,
                            playerTranslateToggle = playerTranslateToggle,
                            playerShowingTranslation = playerShowingTranslation,
                        )
                        // The sheet's own origin in the window, so the menu below can be
                        // placed on the "…" button the player reports in window terms.
                        val sheetOrigin = remember { mutableStateOf(Offset.Zero) }
                        Spacer(
                            Modifier
                                .matchParentSize()
                                .onGloballyPositioned { sheetOrigin.value = it.positionInWindow() },
                        )
                        if (playerMenuOpen) {
                            // Hung off the "…" itself, wherever the layout put it. Read in
                            // the offset block, so the sheet moving relays this out and
                            // recomposes nothing.
                            val anchorSize = playerMenuAnchor.value?.let { anchor ->
                                with(LocalDensity.current) { DpSize(anchor.width.toDp(), anchor.height.toDp()) }
                            } ?: DpSize(34.dp, 34.dp)
                            Box(
                                // The disc player reports no anchor for its "…": the menu goes over it by the
                                // same arithmetic the deck is laid out with. Every other player says where.
                                if (playerStyle == DesktopPlayerStyle.DISC && playerMenuAnchor.value == null) {
                                    // Over the deck's own "…", the right-hand end of its last row —
                                    // the same arithmetic the disc player lays the deck out with.
                                    val stageStart = windowWidth * (if (DesktopStyleSettings.showDisc.value) 0.40f else 0.44f)
                                    val stageWidth = windowWidth - stageStart - 40.dp
                                    val deckWidth = stageWidth.coerceAtMost(660.dp)
                                    val deckRight = stageStart + (stageWidth - deckWidth) / 2 + deckWidth
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = deckRight - 64.dp, y = (-96).dp)
                                        .size(34.dp)
                                } else {
                                    Modifier
                                        .offset {
                                            val anchor = playerMenuAnchor.value
                                                ?: return@offset IntOffset(
                                                    (windowWidth.roundToPx() - 34.dp.roundToPx()) / 2,
                                                    (windowHeight.roundToPx() - 34.dp.roundToPx()) / 2,
                                                )
                                            IntOffset(
                                                (anchor.left - sheetOrigin.value.x).roundToInt(),
                                                (anchor.top - sheetOrigin.value.y).roundToInt(),
                                            )
                                        }
                                        .size(anchorSize)
                                },
                            ) {
                                DesktopSongMenuFor(
                                    song = playerSong,
                                    liked = current.videoId in likedIds,
                                    actions = playerSongActions(current),
                                    onToggleLike = { toggleLike(current) },
                                    onRevertToOriginal = {
                                        DesktopOriginalVersion.pin(current.videoId)
                                        playbackEngine.reloadCurrent()
                                    }.takeIf {
                                        playback.streamSourceId != null &&
                                            playback.streamSourceId != "youtube" &&
                                            DesktopMusicSources.hasYouTubeOriginal(current) &&
                                            !DesktopOriginalVersion.isPinned(current.videoId)
                                    },
                                    onUpgradeQuality = {
                                        DesktopOriginalVersion.clear(current.videoId)
                                        playbackEngine.reloadCurrent(forceSourceRefresh = true)
                                    }.takeIf { DesktopOriginalVersion.isPinned(current.videoId) },
                                    onMore = {
                                        playerMenuOpen = false
                                        playerOverflowOpen.value = true
                                    },
                                    onSwitchLyricsQueue = {
                                        playerMenuOpen = false
                                        playerQueueToggle.value = true
                                    },
                                    queueShowing = playerQueueShowing.value,
                                    onTranslate = {
                                        playerMenuOpen = false
                                        playerTranslateToggle.value = true
                                    },
                                    showingTranslation = playerShowingTranslation.value,
                                    translateEnabled = !sharedLyrics.isNullOrEmpty(),
                                    lyricsProviders = menuLyricsProviders.value,
                                    onSelectLyricsProvider = { label -> lyricsOnly = label },
                                    onSearchLyrics = { title, artist -> searchLyrics(title, artist) },
                                    lyricsOffsetMs = lyricsOffsetMs,
                                    onSetLyricsOffset = { DesktopPlayerSettings.setLyricsOffsetMs(it) },
                                    onDismiss = { playerMenuOpen = false },
                                )
                            }
                        }
                        val message by DesktopPlayerHost.messages.collectAsState()
                        LaunchedEffect(message) {
                            if (message != null) {
                                delay(2_500)
                                DesktopPlayerHost.messages.value = null
                            }
                        }
                        message?.let { text ->
                            Text(
                                text = text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 36.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (overlays.accounts) {
                        DesktopAccountSelector(
                            accounts = accounts,
                            activeAccountId = activeAccountId,
                            activeProfileId = activeProfileId,
                            busy = signInBusy != null,
                            onSelect = { account, profile ->
                                activeAccountId = account.accountId
                                activeProfileId = profile.profileId
                                scope.launch {
                                    // Checked with Google before it is used: a login that has
                                    // expired sends the listener to sign in again rather than
                                    // putting a dead credential on every request.
                                    val live = withContext(Dispatchers.IO) {
                                        DesktopAccounts.choose(account.accountId, profile.profileId)
                                    }
                                    sessionRevision++
                                    if (live == null) {
                                        overlays.accounts = false
                                        signInError = DesktopStrings[
                                            "d_session_expired",
                                            "This account's session has expired. Sign in again.",
                                        ]
                                        overlays.signIn = true
                                    }
                                }
                            },
                            onAddAccount = { overlays.accounts = false; overlays.signIn = true },
                            onRemoveAccount = { account ->
                                scope.launch {
                                    accounts = withContext(Dispatchers.IO) {
                                        DesktopAccounts.remove(account.accountId)
                                        DesktopAccounts.activate()
                                        DesktopAccounts.accounts()
                                    }
                                    activeAccountId = DesktopAccounts.activeAccountId()
                                    activeProfileId = DesktopAccounts.activeProfileId()
                                    sessionRevision++
                                }
                            },
                            onOpenSettings = { overlays.accounts = false; openSettings() },
                            onDismiss = { overlays.accounts = false },
                        )
                    }

                    if (waveSettingsOpen) {
                        DesktopWaveSettingsDialog(
                            playlists = (
                                sidebarAccountPlaylists.mapNotNull { item -> item.browseId?.let { id -> id to item.title } } +
                                    playlists.map { (LOCAL_PLAYLIST_PREFIX + it.id) to it.title }
                                ).distinctBy { it.first },
                            onRestart = ::startWave,
                            onDismiss = { waveSettingsOpen = false },
                        )
                    }
                    if (libraryCustomizeOpen) {
                        DesktopLibraryCustomizeDialog(
                            links = libraryLinks.map { it.item.title },
                            shelves = (libraryState as? UiState.Success)?.data?.shelves?.map { it.title }.orEmpty(),
                            onDismiss = { libraryCustomizeOpen = false },
                        )
                    }
                    if (homeCustomizeOpen) {
                        DesktopHomeCustomizeDialog(
                            shelfTitles = (homeState as? UiState.Success)?.data.orEmpty().map { it.title },
                            onDismiss = { homeCustomizeOpen = false },
                        )
                    }

                    if (spotifyImportOpen) {
                        DesktopSpotifyImportDialog(
                            signedIn = youtubeSignedIn,
                            onImported = ::importSpotifyPlaylist,
                            onDismiss = { spotifyImportOpen = false },
                        )
                    }

                    if (overlays.signIn) {
                        DesktopSignInDialog(
                            busy = signInBusy,
                            error = signInError,
                            webSignIn = DesktopWebSignIn.available,
                            onWebSignIn = {
                                webSignInJob?.cancel()
                                webSignInJob = scope.launch {
                                    signInError = null
                                    signInBusy = WEB_SIGN_IN
                                    try {
                                        val cookie = DesktopWebSignIn.captureYouTube()
                                        signIn(cookie, WEB_SIGN_IN)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        DesktopTrackLog.log("sign-in: YouTube Music failed: ${failure.message}")
                                        signInError = failure.message ?: "Could not complete the sign-in."
                                    } finally {
                                        signInBusy = null
                                        webSignInJob = null
                                    }
                                }
                            },
                            onImport = { profile ->
                                scope.launch {
                                    when (val found = withContext(Dispatchers.IO) { DesktopBrowserCookies.read(profile) }) {
                                        is DesktopBrowserCookies.Result.Session ->
                            signIn(found.cookie, profile.label, profile.database.toString())
                                        DesktopBrowserCookies.Result.SignedOut ->
                                            signInError = "${profile.label} is not signed in."
                                        is DesktopBrowserCookies.Result.Unavailable ->
                                            signInError = "${profile.label}: ${found.reason}."
                                    }
                                }
                            },
                            onPaste = { pasted ->
                                scope.launch {
                                    if (!DesktopBrowserCookies.hasSigningSecret(pasted)) {
                                        signInError = "That cookie has no signing secret in it — copy the whole header."
                                    } else {
                                        signIn(pasted, "the cookie you pasted")
                                    }
                                }
                            },
                            onDismiss = {
                                webSignInJob?.cancel()
                                overlays.signIn = false
                                signInError = null
                            },
                        )
                    }
                    if (overlays.playlistDialog || playlistTarget != null || playlistBatch != null) {
                        DesktopPlaylistDialog(
                            song = playlistTarget,
                            batch = playlistBatch,
                            accountPlaylists = accountPlaylists,
                            localPlaylists = playlists,
                            signedIn = youtubeSignedIn,
                            canUseAccount = playlistBatch?.second?.any { DesktopSearchClient.isVideoId(it.videoId) }
                                ?: playlistTarget?.let { DesktopSearchClient.isVideoId(it.videoId) }
                                ?: true,
                            busy = playlistBusy,
                            error = playlistError,
                            onPickAccount = ::addToAccountPlaylist,
                            onPickLocal = ::addToPlaylist,
                            onPickLiked = ::addToLiked.takeIf {
                                youtubeSignedIn && (playlistBatch?.second ?: listOfNotNull(playlistTarget))
                                    .any { DesktopSearchClient.isVideoId(it.videoId) }
                            },
                            likeProgress = likeProgress,
                            onCreate = ::createPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (shuffleOrderAsked) {
                        AlertDialog(
                            onDismissRequest = ::closePlaylistDialogs,
                            title = { Text(DesktopStrings["d_shuffle_order_title", "Shuffle this playlist?"]) },
                            text = {
                                Text(
                                    playlistError ?: DesktopStrings["d_shuffle_order_body", "Its tracks are put in a random order, and the order is saved to the playlist itself."],
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = ::shuffleOpenPlaylistOrder, enabled = !playlistBusy) {
                                    Text(DesktopStrings["d_shuffle_order", "Shuffle order"])
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = ::closePlaylistDialogs) { Text(DesktopStrings["cancel", "Cancel"]) }
                            },
                        )
                    }
                    if (overlays.rename) {
                        DesktopRenamePlaylistDialog(
                            current = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onRename = ::renameOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    if (overlays.delete) {
                        DesktopDeletePlaylistDialog(
                            title = openedCollection?.title.orEmpty(),
                            busy = playlistBusy,
                            error = playlistError,
                            onDelete = ::deleteOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                        )
                    }
                    val reorderCollection = openedCollection?.takeIf { overlays.reorder }
                    if (reorderCollection != null) {
                        DesktopReorderPlaylistDialog(
                            browseId = reorderCollection.browseId,
                            busy = playlistBusy,
                            error = playlistError,
                            onSave = ::reorderOpenPlaylist,
                            onDismiss = ::closePlaylistDialogs,
                            songContextMenu = { song, onDismiss ->
                                DesktopSongMenuFor(
                                    song = song,
                                    liked = song.videoId in likedIds,
                                    actions = songActionsFor(song),
                                    onToggleLike = { toggleLike(song) },
                                    onRevertToOriginal = null,
                                    onUpgradeQuality = null,
                                    onDismiss = onDismiss,
                                )
                            },
                        )
                    }
                    if (overlays.downloadManager) {
                        DesktopDownloadManagerDialog(onDismiss = { overlays.downloadManager = false })
                    }
                    if (overlays.lastfmLogin) {
                        DesktopLastfmLoginDialog(onDismiss = { overlays.lastfmLogin = false })
                    }
                    if (overlays.listenBrainzToken) {
                        DesktopListenBrainzTokenDialog(onDismiss = { overlays.listenBrainzToken = false })
                    }
                    if (overlays.discordToken) {
                        DesktopDiscordTokenDialog(onDismiss = { overlays.discordToken = false })
                    }
                    if (overlays.listenTogether) {
                        DesktopListenTogetherDialog(
                            autoplayEnabled = autoplay,
                            onDismiss = { overlays.listenTogether = false },
                        )
                    }
                    if (overlays.audioOutput) {
                        DesktopAudioOutputDialog(onDismiss = { overlays.audioOutput = false })
                    }
                    if (overlays.pipeline) {
                        DesktopAudioPipelineDialog(
                            format = playback.streamFormat,
                            sourceName = playback.streamSourceId?.let { id ->
                                sourceConfigs.firstOrNull { it.id == id }?.displayName
                            },
                            pipeline = playbackEngine.pipeline(),
                            onDismiss = { overlays.pipeline = false },
                        )
                    }
                },
            ) { contentPadding ->
                // Desktop pages start at the same top edge as Library. Only preserve space for the
                // floating player/navigation bar at the bottom in compact windows.
                val sharedPagePadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                )
                CompositionLocalProvider(
                    LocalPullToRefreshEnabled provides false,
                    LocalShelfRowChrome provides DesktopShelfRowChrome,
                    com.music.bitchord.ui.components.LocalCardMotion provides animatedHome,
                    com.music.bitchord.ui.components.LocalCardPlayPause provides ::togglePlayPauseFromUser,
                ) {
                DesktopPointerMenuHost<Song>(
                    menu = { song, onDismiss ->
                        DesktopSongMenuFor(
                            song = song,
                            liked = song.videoId in likedIds,
                            actions = songActionsFor(song),
                            onToggleLike = { toggleLike(song) },
                            onRevertToOriginal = null,
                            onUpgradeQuality = null,
                            onDismiss = onDismiss,
                        )
                    },
                ) { openMenu ->
                pageMenu = openMenu
                // Each page arrives rather than appears: a short fade and rise when the page changes.
                val pageKey = listOf(destination, overlays.settingsPage, openedArtist?.name, openedCollection?.browseId, selectedMoodGenre?.title)
                val pageEnter = remember { Animatable(1f) }
                LaunchedEffect(pageKey) {
                    if (!animatedHome) return@LaunchedEffect
                    pageEnter.snapTo(0f)
                    pageEnter.animateTo(1f, tween(520, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
                }
                // The wheel glides on the page's own list — see [DesktopSmoothWheel] — for the pages
                // whose list is held here; the rest keep the stock wheel.
                // The list the open page scrolls by: held up here for the main pages, announced
                // by the page itself for the rest — see [DesktopPageScroll].
                val pageList: androidx.compose.foundation.gestures.ScrollableState? = when {
                    overlays.settingsPage == DesktopSettingsPage.MAIN -> settingsListState
                    overlays.settingsPage != null || overlays.replay || openedArtist != null || openedCollection != null -> null
                    destination == DesktopDestination.LISTEN_NOW -> homeListState
                    destination == DesktopDestination.EXPLORE && selectedMoodGenre != null -> moodGenreListState
                    destination == DesktopDestination.EXPLORE -> exploreGridState
                    destination == DesktopDestination.SEARCH -> searchListState
                    destination == DesktopDestination.LIBRARY && libraryShowAll != null -> libraryGridState
                    destination == DesktopDestination.LIBRARY -> libraryListState
                    else -> null
                } ?: pageScroll.state
                val announcedWheel = pageScroll.state?.let { rememberSmoothWheel(it) }
                val pageWheel = when {
                    overlays.settingsPage == DesktopSettingsPage.MAIN -> settingsWheel
                    overlays.settingsPage != null || overlays.replay || openedArtist != null || openedCollection != null -> null
                    destination == DesktopDestination.LISTEN_NOW -> homeWheel
                    destination == DesktopDestination.EXPLORE && selectedMoodGenre != null -> moodGenreWheel
                    destination == DesktopDestination.EXPLORE -> exploreWheel
                    destination == DesktopDestination.SEARCH -> searchWheel
                    destination == DesktopDestination.LIBRARY && libraryShowAll != null -> libraryGridWheel
                    destination == DesktopDestination.LIBRARY -> libraryWheel
                    else -> null
                }
                CompositionLocalProvider(LocalDesktopContextMenu provides openMenu, LocalDesktopPageScroll provides pageScroll) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .smoothWheel(pageWheel ?: announcedWheel)
                        .graphicsLayer {
                            alpha = 0.15f + pageEnter.value * 0.85f
                            translationY = (1f - pageEnter.value) * 22.dp.toPx()
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press && event.buttons.isBackPressed) goBack()
                                }
                            }
                        },
                ) {
                    when {
                        // Settings and its pages stand in for the page, held to the width the
                        // Settings card had. Its prompts are still cards, over the page.
                        overlays.settingsPage != null -> Box(
                            Modifier.fillMaxSize().padding(
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                        ) {
                            CompositionLocalProvider(LocalDesktopPanelIsPage provides true) {
                            when (overlays.settingsPage!!) {
                                DesktopSettingsPage.MAIN -> DesktopSettingsScreen(
                                    onCustomizeHome = { homeCustomizeOpen = true },
                                    query = settingsQuery,
                                    onQueryChange = { settingsQuery = it },
                                    listState = settingsListState,
                                    autoplay = autoplay,
                                    onAutoplayChange = ::setAutoplay,
                                    automix = automix,
                                    onAutomixChange = {
                                        automix = it
                                        persistence.saveBoolean("automix", it)
                                    },
                                    automixOnSkip = automixOnSkip,
                                    onAutomixOnSkipChange = {
                                        automixOnSkip = it
                                        persistence.saveBoolean("automix_on_skip", it)
                                    },
                                    automixPerformance = automixPerformance,
                                    onAutomixPerformanceChange = {
                                        automixPerformance = it
                                        persistence.saveString("automix_performance", it.name)
                                    },
                                    automixPhraseBars = automixPhraseBars,
                                    onAutomixPhraseBarsChange = {
                                        automixPhraseBars = it
                                        persistence.saveString("automix_phrase_bars", it.toString())
                                    },
                                    shuffle = shuffle,
                                    onShuffleChange = ::setShuffle,
                                    repeatMode = repeatMode,
                                    onRepeatModeChange = {
                                        repeatMode = it
                                        persistence.saveString("repeat_mode", it.name)
                                    },
                                    playbackSpeed = playbackSpeed,
                                    onPlaybackSpeedChange = {
                                        playbackSpeed = it
                                        persistence.saveString("playback_speed", it.toString())
                                    },
                                    crossfadeSeconds = crossfadeSeconds,
                                    onCrossfadeSecondsChange = {
                                        crossfadeSeconds = it
                                        persistence.saveString("crossfade_seconds", it.toString())
                                    },
                                    animatedCanvas = animatedCanvas,
                                    showNerdStats = showNerdStats,
                                    fullBleedArtwork = fullBleedArtwork,
                                    onAnimatedCanvasChange = {
                                        animatedCanvas = it
                                        persistence.saveBoolean("animated_canvas", it)
                                    },
                                    spotifyCanvasReady = spotifyCanvasCookie.isNotBlank(),
                                    onOpenSpotifyCanvasSetup = { overlays.settingsPage = DesktopSettingsPage.SPOTIFY_CANVAS },
                                    dontRepeatSuggestions = dontRepeatSuggestions,
                                    onDontRepeatSuggestionsChange = {
                                        dontRepeatSuggestions = it
                                        persistence.saveBoolean(KEY_DONT_REPEAT_SUGGESTIONS, it)
                                    },
                                    onChooseLocalMusicFolder = { onChosen ->
                                        DesktopLocalMusic.chooseFolder()?.let { chosen ->
                                            DesktopLocalMusic.setFolder(chosen)
                                            onChosen()
                                            localMusicRevision++
                                        }
                                    },
                                    onLocalMusicFolderChanged = { localMusicRevision++ },
                                    filterNonMusicAudio = filterNonMusicAudio,
                                    onFilterNonMusicAudioChange = {
                                        filterNonMusicAudio = it
                                        persistence.saveBoolean(DesktopLocalMusic.KEY_FILTER_NON_MUSIC_AUDIO, it)
                                        // The scan's result changes with it, so it has to be taken again.
                                        localMusicRevision++
                                    },
                                    syncedLyrics = syncedLyrics,
                                    onSyncedLyricsChange = {
                                        syncedLyrics = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_SYNCED_LYRICS, it)
                                    },
                                    lyricsBlur = lyricsBlur,
                                    onLyricsBlurChange = {
                                        lyricsBlur = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_LYRICS_BLUR, it)
                                    },
                                    enabledLyricsSources = DesktopLyricsClient.enabledSources(lyricsOrder, lyricsOn),
                                    onOpenLyricsSources = { overlays.settingsPage = DesktopSettingsPage.LYRICS_SOURCES },
                                    onOpenTranslationLanguage = { overlays.settingsPage = DesktopSettingsPage.TRANSLATION_LANGUAGE },
                                    onOpenEqualizer = { overlays.settingsPage = DesktopSettingsPage.EQUALIZER },
                                    onOpenAudioOutput = { overlays.settingsPage = DesktopSettingsPage.AUDIO_OUTPUT },
                                    onOpenListenTogether = { overlays.settingsPage = DesktopSettingsPage.LISTEN_TOGETHER },
                                    onShowNerdStatsChange = {
                                        showNerdStats = it
                                        persistence.saveBoolean("show_nerd_stats", it)
                                    },
                                    onFullBleedArtworkChange = {
                                        fullBleedArtwork = it
                                        persistence.saveBoolean("full_bleed_artwork", it)
                                    },
                                    legacyMeshGradient = legacyMeshGradient,
                                    onLegacyMeshGradientChange = {
                                        legacyMeshGradient = it
                                        persistence.saveBoolean("legacy_mesh_gradient", it)
                                    },
                                    spatialAudio = spatialAudio,
                                    onSpatialAudioChange = {
                                        spatialAudio = it
                                        persistence.saveBoolean("spatial_audio", it)
                                    },
                                    dolbyAtmos = dolbyAtmos,
                                    onDolbyAtmosChange = {
                                        dolbyAtmos = it
                                        DesktopAddonSettings.dolbyAtmosEnabled = it
                                        persistence.saveBoolean("dolby_atmos", it)
                                        DesktopAddonSource.clearCompletedTrackCalls()
                                    },
                                    skipSilence = skipSilence,
                                    onSkipSilenceChange = {
                                        skipSilence = it
                                        persistence.saveBoolean("skip_silence", it)
                                    },
                                    outputPrecision = outputPrecision,
                                    onOutputPrecisionChange = {
                                        outputPrecision = it
                                        persistence.saveString("output_precision", it)
                                    },
                                    outputSummary = playbackEngine.outputSummary(),
                                    trayIconEnabled = trayIconEnabled,
                                    closeToTray = closeToTray,
                                    onCloseToTrayChange = {
                                        closeToTray = it
                                        persistence.saveBoolean("close_to_tray", it)
                                    },
                                    onTrayIconChange = {
                                        trayIconEnabled = it
                                        persistence.saveBoolean("tray_icon", it)
                                    },
                                    downloadQuality = downloadQuality,
                                    onDownloadQualityChange = {
                                        downloadQuality = it
                                        persistence.saveString("download_quality", it)
                                    },
                                    audioQuality = audioQuality,
                                    onAudioQualityChange = {
                                        audioQuality = it
                                        persistence.saveAudioQuality(it)
                                    },
                                    sleepTimerMinutes = sleepTimerMinutes,
                                    sleepAfterTrack = sleepAfterTrack,
                                    sleepRemainingMs = sleepRemainingMs,
                                    onSleepTimerCycle = ::cycleSleepTimer,
                                    sourceConfigs = sourceConfigs,
                                    sourceStatus = sourceStatus,
                                    onSourceEnabledChange = { config, enabled ->
                                        val next = sourceConfigs.map {
                                            if (it.id == config.id && it.kind != DesktopSourceKind.YOUTUBE) {
                                                it.copy(enabled = enabled)
                                            } else {
                                                it
                                            }
                                        }
                                        persistence.saveSourceConfigs(next)
                                        sourceConfigs = persistence.sourceConfigs()
                                        DesktopModuleSource.reload()
                                    },
                                    onSaveSource = { saved ->
                                        // Replaced by id, so any number of addons can be configured.
                                        val without = sourceConfigs.filterNot {
                                            it.id == saved.id ||
                                                (saved.kind == DesktopSourceKind.CUSTOM_MODULE &&
                                                    it.kind == DesktopSourceKind.CUSTOM_MODULE)
                                        }
                                        persistence.saveSourceConfigs((without + saved).inSourceOrder())
                                        sourceConfigs = persistence.sourceConfigs()
                                        // Whatever was held for this entry describes a server that may no
                                        // longer be the one selected.
                                        DesktopAddonSource.forget(saved.id)
                                        DesktopModuleSource.reload()
                                    },
                                    onRemoveSource = { config ->
                                        if (config.isUserAdded) {
                                            persistence.saveSourceConfigs(sourceConfigs.filterNot { it.id == config.id })
                                            sourceConfigs = persistence.sourceConfigs()
                                            DesktopAddonSource.forget(config.id)
                                            DesktopModuleSource.reload()
                                        }
                                    },
                                    onMoveSource = { config, delta ->
                                        val next = moveUserSource(sourceConfigs, config.id, delta)
                                        persistence.saveSourceConfigs(next)
                                        sourceConfigs = persistence.sourceConfigs()
                                    },
                                    onTestSource = { candidate ->
                                        sourceStatus = sourceStatus + (candidate.id to "Checking source…")
                                        scope.launch {
                                            val health = if (candidate.kind == DesktopSourceKind.ADDON) {
                                                DesktopAddonSource.health(candidate)
                                            } else {
                                                DesktopModuleSource.health(candidate)
                                            }
                                            health.fold(
                                                onSuccess = { sourceStatus = sourceStatus + (candidate.id to it) },
                                                onFailure = {
                                                    sourceStatus = sourceStatus +
                                                        (candidate.id to (it.message ?: "Source unavailable"))
                                                },
                                            )
                                        }
                                    },
                                    onOpenIntegrations = { overlays.settingsPage = DesktopSettingsPage.INTEGRATIONS },
                                    onOpenLicenses = { overlays.settingsPage = DesktopSettingsPage.LICENSES },
                                )
                                DesktopSettingsPage.EQUALIZER -> DesktopEqualizerDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.AUDIO_OUTPUT -> DesktopAudioOutputDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.LISTEN_TOGETHER -> DesktopListenTogetherDialog(
                                    autoplayEnabled = autoplay,
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.LYRICS_SOURCES -> DesktopLyricsSourcesDialog(
                                    order = lyricsOrder,
                                    enabled = lyricsOn,
                                    prioritizeSyllables = prioritizeSyllables,
                                    onReorder = {
                                        lyricsOrder = it
                                        persistence.saveLyricsSourceOrder(it)
                                    },
                                    onToggle = { name ->
                                        lyricsOn = if (name in lyricsOn) lyricsOn - name else lyricsOn + name
                                        persistence.saveLyricsEnabledSources(lyricsOn)
                                    },
                                    onPrioritizeSyllables = {
                                        prioritizeSyllables = it
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, it)
                                    },
                                    onReset = {
                                        lyricsOrder = DesktopLyricsClient.sources.map { it.name }
                                        lyricsOn = lyricsOrder.toSet()
                                        prioritizeSyllables = false
                                        persistence.saveLyricsSourceOrder(lyricsOrder)
                                        persistence.saveLyricsEnabledSources(lyricsOn)
                                        persistence.saveBoolean(DesktopLyricsClient.KEY_PRIORITIZE_SYLLABLES, false)
                                        lyricsDefault = null
                                        persistence.saveLyricsDefaultSource(null)
                                    },
                                    onDismiss = ::goBack,
                                    defaultSource = lyricsDefault,
                                    onDefaultSource = { name ->
                                        lyricsDefault = name
                                        persistence.saveLyricsDefaultSource(name)
                                        // A default nobody may ask would be no default at all.
                                        if (name != null && name !in lyricsOn) {
                                            lyricsOn = lyricsOn + name
                                            persistence.saveLyricsEnabledSources(lyricsOn)
                                        }
                                        lyricsOnly = null
                                        lyricsRefresh++
                                    },
                                )
                                DesktopSettingsPage.TRANSLATION_LANGUAGE -> DesktopTranslationLanguageDialog(onDismiss = ::goBack)
                                DesktopSettingsPage.SPOTIFY_CANVAS -> DesktopSpotifyCanvasDialog(
                                    onDismiss = ::goBack,
                                    onSaved = { spotifyCanvasCookie = it },
                                )
                                DesktopSettingsPage.INTEGRATIONS -> DesktopIntegrationsDialog(
                                    song = playback.song,
                                    spotify = DesktopSpotifyConnection(
                                        connected = spotifyCanvasCookie.isNotBlank(),
                                        canSignIn = DesktopWebSignIn.available,
                                        signingIn = spotifySignInJob != null,
                                        error = spotifySignInError,
                                        onSignIn = ::signInToSpotify,
                                        onPasteCookie = { overlays.settingsPage = DesktopSettingsPage.SPOTIFY_CANVAS },
                                        onDisconnect = {
                                            DesktopSpotifyToken.setCookie("")
                                            spotifyCanvasCookie = ""
                                        },
                                    ),
                                    onOpenLastfm = { overlays.lastfmLogin = true },
                                    onOpenListenBrainz = { overlays.listenBrainzToken = true },
                                    onOpenDiscordToken = { overlays.discordToken = true },
                                    onDismiss = ::goBack,
                                )
                                DesktopSettingsPage.LICENSES -> DesktopLicensesPage(onDismiss = ::goBack)
                            }
                            }
                        }
                        overlays.replay -> DesktopReplayPage(
                            summary = replaySummary,
                            period = replayPeriod,
                            holder = replayHolder,
                            onPeriodChange = { replayPeriod = it },
                            onPlaySong = { playSong(it) },
                            onOpenArtist = ::openArtistByName,
                            contentPadding = contentPadding,
                        )
                        openedArtist != null -> DesktopArtistPage(
                            state = artistState,
                            fallbackName = openedArtist!!.name,
                            onRetry = {
                                artistState = UiState.Loading
                                artistReloads++
                            },
                            onPlaySongs = { songs, index ->
                                playSongs(songs, index, openedArtist?.let { artistSource(it) })
                            },
                            onShuffle = { songs ->
                                playSongs(songs, 0, openedArtist?.let { artistSource(it) })
                            },
                            onOpenMenu = { song -> openMenu(song) },
                            onShelfItemClick = ::openShelfItem,
                            // A channel subscription is the account's, so a guest is never shown
                            // the button.
                            onToggleSubscription = if (youtubeSignedIn) ::toggleSubscription else null,
                            contentPadding = contentPadding,
                        )
                        openedCollection?.loading == true -> DesktopPageScaffold(contentPadding) {
                            Box(Modifier.fillMaxSize()) {
                                val error = collectionError
                                if (error != null) {
                                    DesktopErrorPage(error) { collectionReloads++ }
                                } else {
                                    val title = openedCollection?.title.orEmpty()
                                    DesktopLoadingPage(if (title.isBlank()) "Loading…" else "Loading $title…")
                                }
                            }
                        }
                        openedCollection != null -> DesktopCollectionPage(
                            collection = openedCollection!!,
                            loadingMore = collectionLoadingMore,
                            onLoadMore = ::loadMoreCollectionSongs,
                            onRename = { overlays.rename = true }.takeIf { openedCollection?.owned == true },
                            onShare = openedCollection
                                ?.takeIf { it.type == BrowseType.ALBUM || it.type == BrowseType.PLAYLIST }
                                ?.let { collection -> { shareCollection(collection) } },
                            onDelete = { overlays.delete = true }.takeIf { openedCollection?.owned == true },
                            onReorder = { overlays.reorder = true }.takeIf {
                                openedCollection?.owned == true && openedCollection?.type == BrowseType.PLAYLIST
                            },
                            onShuffleOrder = { shuffleOrderAsked = true }.takeIf {
                                openedCollection?.owned == true && openedCollection?.type == BrowseType.PLAYLIST
                            },
                            onAddAllToPlaylist = { openedCollection?.let(::addCollectionToPlaylist); Unit }.takeIf {
                                // A Spotify list once its tracks are matched to YouTube's.
                                openedCollection?.songs?.isNotEmpty() == true &&
                                    openedCollection!!.songs.none { it.isMatchPending }
                            },
                            onRemoveFromPlaylist = ::removeFromOpenPlaylist
                                .takeIf { openedCollection?.owned == true },
                            likedIds = likedIds,
                            onPlaySongs = { songs, index ->
                                playSongs(songs, index, openedCollection?.let { collectionSource(it) })
                            },
                            onShuffle = { songs ->
                                playSongs(songs.shuffled(), 0, openedCollection?.let { collectionSource(it) })
                            },
                            onDownloadAll = ::downloadAll,
                            animatedCanvas = animatedCanvas,
                            onToggleLike = { song ->
                                toggleLike(song)
                            },
                            onAddToPlaylist = { playlistTarget = it },
                            onDownload = ::downloadSong,
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                        )
                        destination == DesktopDestination.LISTEN_NOW -> HomeScreen(
                            state = visibleHomeState,
                            listState = homeListState,
                            onItemClick = { item, shelfTitle -> openShelfItem(item, shelfTitle) },
                            onRetry = ::loadHome,
                            refreshing = false,
                            onRefresh = ::loadHome,
                            pullState = rememberPullToRefreshState(),
                            contentPadding = sharedPagePadding,
                            title = null,
                            signedIn = youtubeSignedIn,
                            // Signed out: straight to signing in, not to a list of logins that may have expired.
                            onSignIn = { if (youtubeSignedIn) overlays.accounts = true else overlays.signIn = true },
                            onItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                            onLoadMore = ::loadMoreHome,
                            loadingMore = homeLoadingMore,
                            recentlyPlayedLoading = homeRecentsLoading,
                            leadHero = false,
                            lead = if (waveShown) {
                                {
                                    val jamOn = selectedSong?.radioName == DesktopMyWave.radioName
                                    DesktopWaveCard(
                                        starting = waveStarting,
                                        playing = jamOn,
                                        paused = !playback.isPlaying,
                                        current = selectedSong?.takeIf { jamOn },
                                        // The sleeve the card above shows, so the two match — a video's
                                        // frame had the jam stay its colour after switching to the song.
                                        coverUrl = selectedSong?.let(::withSleeve)?.thumbnailUrl,
                                        onPlay = ::startWave,
                                        onPlayPause = ::togglePlayPauseFromUser,
                                        onSkip = ::playNext,
                                        onTune = { waveSettingsOpen = true },
                                        // One with the "now playing" card while the jam plays — when there
                                        // is that card above it.
                                        merged = jamOn && animatedHome && selectedSong != null,
                                        heroVisible = { if (heroAnchor.present) heroAnchor.visible else 1f },
                                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            header = if (animatedHome && DesktopFrameProfiler.on("header")) {
                                {
                                    val current = selectedSong?.let(::withSleeve)
                                    DesktopHomeHeader(
                                        song = current,
                                        heroAnchor = heroAnchor,
                                        onCustomize = { homeCustomizeOpen = true },
                                        // At the bottom, the card is the feed's own and scrolls with it; at
                                        // the top, the floating player sits on its place.
                                        inlineCard = if (current != null && nowPlayingPlacement == DesktopNowPlayingPlacement.BOTTOM) {
                                            { cardModifier ->
                                                DesktopHeroCard(
                                                    song = current,
                                                    isPlaying = playback.isPlaying,
                                                    onOpenPlayer = { overlays.nowPlaying = true },
                                                    transport = { heroTransport(current) },
                                                    extras = heroExtras(current),
                                                    modifier = cardModifier,
                                                    jamFoot = jamFoot,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                        destination == DesktopDestination.EXPLORE && selectedMoodGenre != null -> Column(
                            Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
                        ) {
                            MoodGenrePlaylistsScreen(
                                title = selectedMoodGenre!!.title,
                                state = moodGenreShelves,
                                listState = moodGenreListState,
                                onItemClick = { item -> openShelfItem(item, selectedMoodGenre?.title) },
                                onRetry = {
                                    moodGenreShelves = UiState.Loading
                                    moodGenreReloads++
                                },
                                contentPadding = PaddingValues(bottom = sharedPagePadding.calculateBottomPadding()),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        destination == DesktopDestination.EXPLORE -> DesktopExplorePage(
                            state = exploreState,
                            gridState = exploreGridState,
                            onCategoryClick = ::openMoodGenre,
                            onRetry = { exploreReloads++ },
                            contentPadding = sharedPagePadding,
                        )
                        destination == DesktopDestination.SEARCH -> SearchScreen(
                            query = query,
                            onQueryChange = ::editQuery,
                            filter = searchFilter,
                            // The playing row's indicator, as the phone passes it: the engine's
                            // live state, not the selection a track change publishes behind it.
                            currentSong = playback.song,
                            isPlaying = playback.isPlaying,
                            onFilterChange = {
                                searchFilter = it
                                if (query.isNotBlank()) search()
                            },
                            results = when {
                                searchLoading -> UiState.Loading
                                searchError != null -> UiState.Error(searchError!!)
                                searchCommitted -> UiState.Success(searchRows)
                                else -> null
                            },
                            loadingMore = false,
                            onLoadMore = {},
                            listState = searchListState,
                            scrollResetTrigger = searchScrollReset,
                            focusRequested = searchFocusRequested,
                            onFocusHandled = { searchFocusRequested = false },
                            onSongClick = { songs, index ->
                                songs.getOrNull(index)?.let { song ->
                                    recordSongSearch(song)
                                    playSong(song, source = DesktopQueueSource(DesktopStrings["search", "Search"], PlaybackSourceType.SEARCH))
                                }
                            },
                            onSongLongPress = ::openMenu,
                            onSongSwipe = { song ->
                                if (DesktopAppUiHost.swipeToPlayNext.value) playNext(song) else addToQueue(song)
                            },
                            onTopResultPlay = { song ->
                                recordSongSearch(song)
                                playSong(song, source = DesktopQueueSource(DesktopStrings["search", "Search"], PlaybackSourceType.SEARCH))
                            },
                            onTopResultPlaylist = { song ->
                                recordSongSearch(song)
                                playlistTarget = song
                            },
                            onBrowseClick = { item ->
                                recordBrowseSearch(item)
                                openBrowseItem(item)
                            },
                            history = searchHistory,
                            // The typed text leads the list, put there by the keystroke rather than
                            // taken from the response — the page drops it, as the phone's does.
                            suggestions = if (searchTyping && query.isNotBlank()) {
                                listOf(query) +
                                    searchSuggestions.filterNot { it.equals(query, ignoreCase = true) }
                            } else {
                                emptyList()
                            },
                            typeaheadResults = if (searchTyping && query.isNotBlank()) searchTypeahead else emptyList(),
                            onSubmit = ::search,
                            onSuggestionClick = ::runSearch,
                            onHistoryClick = { entity ->
                                // Going back to a recent does not record it again, as on the phone.
                                when (entity.entityType) {
                                    EntityType.TRACK -> playSong(
                                        Song(
                                            videoId = entity.id,
                                            title = entity.title,
                                            artist = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                        ),
                                        source = DesktopQueueSource(entity.title, PlaybackSourceType.SEARCH),
                                    )
                                    EntityType.ARTIST -> openArtist(entity.id, entity.title)
                                    EntityType.ALBUM, EntityType.PLAYLIST -> openBrowseItem(
                                        BrowseItem(
                                            browseId = entity.id,
                                            title = entity.title,
                                            subtitle = entity.subtitle,
                                            thumbnailUrl = entity.artworkUrl,
                                            type = if (entity.entityType == EntityType.ALBUM) BrowseType.ALBUM else BrowseType.PLAYLIST,
                                        ),
                                    )
                                }
                            },
                            onHistoryRemove = DesktopSearchHistory::remove,
                            onHistoryClear = DesktopSearchHistory::clear,
                            onTypeaheadLongPress = ::openMenu,
                            contentPadding = sharedPagePadding,
                            topPadding = contentPadding.calculateTopPadding(),
                            showField = false,
                        )
                        destination == DesktopDestination.LIBRARY && libraryShowAll != null -> Column(
                            Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
                        ) {
                            // The phone keeps the grid's sort beside it, in the bar above; here it sits
                            // at the end of the row the way back is on.
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
                                horizontalArrangement = Arrangement.End,
                            ) { DesktopLibrarySortMenu() }
                            spotifyLibraryStatus?.takeIf { libraryShowAll?.title == SPOTIFY_SHELF }?.let { status ->
                                Text(
                                    status,
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 12.dp),
                                )
                            }
                            LibraryGridPage(
                                shelf = libraryShowAll!!,
                                gridState = libraryGridState,
                                onItemClick = ::openLibraryItem,
                                onItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                                contentPadding = PaddingValues(bottom = sharedPagePadding.calculateBottomPadding()),
                                modifier = Modifier.weight(1f),
                                onNewPlaylist = { overlays.playlistDialog = true }
                                    .takeIf { libraryShowAll?.title == PLAYLISTS_SHELF },
                            )
                        }
                        destination == DesktopDestination.LIBRARY -> LibraryScreen(
                            signedIn = youtubeSignedIn,
                            // Without the parts switched off in Customize Library.
                            state = libraryHidden.let { hidden ->
                                val page = (libraryState as? UiState.Success)?.data
                                if (page == null || hidden.isEmpty()) {
                                    libraryState
                                } else {
                                    UiState.Success(page.copy(shelves = page.shelves.filterNot { DesktopLibrarySections.isHidden(hidden, it.title) }))
                                }
                            },
                            listState = libraryListState,
                            onShelfItemClick = ::openLibraryItem,
                            onShelfItemLongPress = { item -> if (item.videoId != null) openMenu(item.toSong()) },
                            onNewPlaylist = { overlays.playlistDialog = true },
                            onImportSpotifyPlaylist = { spotifyImportOpen = true },
                            onShowAll = { shelf -> libraryShowAll = shelf },
                            replay = {
                                // What the page shows, and where it is changed.
                                Row(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                                    DesktopCustomizePill(DesktopStrings["d_library_customize", "Customize Library"], onClick = { libraryCustomizeOpen = true })
                                }
                                // The phone's wallet of Replay cards, or nothing until there is
                                // listening to deal them from.
                                val cards = remember(replaySummary) { replaySummary.heroCards() }
                                if (!replaySummary.isEmpty && cards.isNotEmpty() && !DesktopLibrarySections.isHidden(libraryHidden, DesktopLibrarySections.REPLAY)) {
                                    ShelfRow(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(cards, key = { it.label }) { card ->
                                            ReplayCreditCard(
                                                label = card.label,
                                                value = card.value,
                                                detail = card.detail,
                                                artworkUrl = card.artworkUrl,
                                                holder = replayHolder,
                                                memberSince = replaySummary.memberSince(),
                                                onClick = { overlays.replay = true },
                                                modifier = Modifier.width(300.dp),
                                            )
                                        }
                                    }
                                }
                            },
                            // Signed out: straight to signing in, not to a list of logins that may have expired.
                            onSignIn = { if (youtubeSignedIn) overlays.accounts = true else overlays.signIn = true },
                            onRetry = ::reloadLibrary,
                            refreshing = false,
                            onRefresh = ::reloadLibrary,
                            pullState = rememberPullToRefreshState(),
                            contentPadding = sharedPagePadding,
                            links = libraryLinks.filterNot { DesktopLibrarySections.isHidden(libraryHidden, it.item.title) },
                            deviceItems = if (DesktopLibrarySections.isHidden(libraryHidden, DesktopLibrarySections.ON_DEVICE)) emptyList() else libraryDeviceItems,
                            showTitle = false,
                        )
                        destination == DesktopDestination.HISTORY -> DesktopHistoryPage(
                            // The account's history when there is one, and what
                            // this computer played when there is not.
                            history = remoteHistory.ifEmpty { history },
                            onSongClick = { playSong(it, source = DesktopQueueSource(DesktopStrings["history", "History"], PlaybackSourceType.HISTORY)) },
                            onDownload = ::downloadSong,
                            onAddToPlaylist = { playlistTarget = it },
                            downloadedIds = downloads.map(Song::videoId).toSet(),
                            downloadInProgress = downloadInProgress,
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                        destination == DesktopDestination.DOWNLOADS -> Box(Modifier.fillMaxSize()) {
                            DesktopDownloadsPage(
                                downloads = downloads,
                                onSongClick = { playSong(it, source = DesktopQueueSource(DesktopStrings["downloads", "Downloads"], PlaybackSourceType.BROWSE)) },
                                contentPadding = contentPadding,
                                menu = { song -> songMenu(song) },
                            )
                            // Only while there is a queue to look at.
                            if (downloadInProgress.isNotEmpty()) {
                                DesktopActionButton(
                                    "Queue · ${downloadInProgress.size}",
                                    BitChordIcons.Download,
                                    onClick = { overlays.downloadManager = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 28.dp, bottom = 28.dp + contentPadding.calculateBottomPadding()),
                                )
                            }
                        }
                        destination == DesktopDestination.LOCAL_MUSIC -> DesktopLocalMusicPage(
                            songs = localSongs,
                            onSongClick = { playSong(it, source = DesktopQueueSource(DesktopStrings["local_music", "Local Music"], PlaybackSourceType.BROWSE)) },
                            contentPadding = contentPadding,
                            menu = { song -> songMenu(song) },
                        )
                    }
                                    // The page's scrollbar, along its right edge, whichever page it is.
                    DesktopPageScrollbar(
                        pageList,
                        Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .padding(top = contentPadding.calculateTopPadding() + 6.dp, bottom = contentPadding.calculateBottomPadding() + 6.dp, end = 3.dp),
                    )
}
                }
                }

            }
        }
        }
    }

}

@Composable
private fun DesktopTopBar(
    compact: Boolean,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean,
    progress: Float,
    volume: Float,
    shuffle: Boolean,
    repeatMode: DesktopRepeatMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    /** Which of the two the column beside the page is showing, lit on its button. */
    sidePanel: DesktopSidePanel?,
    accountAvatar: String?,
    onOpenAccounts: () -> Unit,
    /** The app's one way back — every page's own arrow was folded into this. */
    canGoBack: Boolean,
    onBack: () -> Unit,
    /** False when the now-playing bar along the bottom carries the transport instead. */
    showTransport: Boolean = true,
    onOpenAllSettings: () -> Unit = {},
) {
    val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled
    // The same claim the player's scrubber runs through an Automix blend, but breathing slowly and
    // gently rather than strobing on the beat — the desktop's calm reading of a mix.
    val mixBlend = DesktopPlayerSettings.smartMixBlend.collectAsState()
    val reduceAnimation by DesktopPlayerSettings.reduceAnimation.collectAsState()
    val mixPulse = rememberMixPulse({ mixBlend.value }, enabled = !reduceAnimation, slow = true)
    val currentProgress by rememberUpdatedState(progress)

    // Apple Music uses one calm strip for both player controls and window furniture. The left
    // sidebar owns the traffic lights; the rest is a balanced transport / now-playing / utility
    // layout with deliberately smaller glyphs than the phone player.
    DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(64.dp)) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // The window's own buttons live at the head of the sidebar; a compact window has no
            // sidebar, so there they lead this bar instead.
            if (compact && inlineCaption && !DesktopCaptionOnRight) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .desktopWindowGlass(DesktopChromeEdge.BOTTOM)
                        .padding(start = if (DesktopPlatform.isMac) 16.dp else 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    DesktopWindowButtons()
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .desktopWindowGlass(DesktopChromeEdge.BOTTOM)
                    .padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The mark leads the player's own controls, now that the sidebar runs to the top.
                Image(
                    painter = painterResource(Res.drawable.logo_mark),
                    contentDescription = "BitChord",
                    modifier = Modifier.size(width = 28.dp, height = 18.dp),
                )
                Spacer(Modifier.width(14.dp))
                DesktopToolbarButton(onClick = onBack, enabled = canGoBack, size = 32.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        DesktopStrings["back", "Back"],
                        tint = if (canGoBack) Color.White else DesktopSecondary.copy(alpha = 0.40f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (!compact && showTransport) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DesktopToolbarButton(onClick = { onShuffleChange(!shuffle) }, size = 32.dp) {
                            Icon(
                                BitChordIcons.Shuffle,
                                DesktopStrings["shuffle", "Shuffle"],
                                tint = if (shuffle) DesktopAccent else DesktopSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DesktopToolbarButton(onClick = onPrevious, enabled = previousEnabled, size = 32.dp) {
                            Icon(
                                Icons.Rounded.FastRewind,
                                DesktopStrings["widget_previous", "Previous"],
                                tint = if (previousEnabled) Color.White else DesktopSecondary.copy(alpha = 0.40f),
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        DesktopToolbarButton(onClick = onPlayPause, size = 32.dp) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(23.dp),
                                )
                            }
                        }
                        DesktopToolbarButton(onClick = onNext, size = 32.dp) {
                            Icon(
                                Icons.Rounded.FastForward,
                                DesktopStrings["widget_next", "Next"],
                                tint = Color.White,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        DesktopToolbarButton(
                            onClick = { onRepeatModeChange(repeatMode.next()) },
                            size = 32.dp,
                        ) {
                            val tint = if (repeatMode != DesktopRepeatMode.OFF) DesktopAccent else DesktopSecondary
                            if (repeatMode == DesktopRepeatMode.ONE) {
                                Text(
                                    "1",
                                    color = tint,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.semantics { contentDescription = "Repeat one" },
                                )
                            } else {
                                Icon(
                                    BitChordIcons.Repeat,
                                    "Repeat ${repeatMode.label()}",
                                    tint = tint,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Box(
                        Modifier.weight(1f).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 300.dp, max = 520.dp)
                                .fillMaxWidth()
                                .height(46.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onOpenNowPlaying),
                            color = Color(0xFF323236),
                            tonalElevation = 0.dp,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (song == null) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            BitChordIcons.MusicNote,
                                            DesktopStrings["playback_channel_name", "Now playing"],
                                            tint = DesktopSecondary.copy(alpha = 0.65f),
                                            modifier = Modifier.size(21.dp),
                                        )
                                    }
                                } else {
                                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                        DesktopArtwork(
                                            song.thumbnailUrl,
                                            Modifier.size(44.dp).clip(RoundedCornerShape(3.dp)),
                                            px = ROW_ART_PX,
                                        )
                                        Column(
                                            Modifier.weight(1f).padding(start = 12.dp, end = 56.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                song.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                song.artist,
                                                color = DesktopSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    // Drawn rather than sized: through a blend the line covers
                                    // the full width and pulses on the beat, as the player's
                                    // scrubber does, and reading either in draw keeps the frame
                                    // clock from recomposing the whole bar.
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(start = 44.dp)
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .drawBehind {
                                                val base = currentProgress.coerceIn(0f, 1f)
                                                val fraction = base + (1f - base) * mixPulse.cover
                                                drawRect(
                                                    color = Color.White.copy(alpha = mixPulse.alpha(0.48f)),
                                                    size = Size(size.width * fraction, size.height),
                                                )
                                            },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // The player's everyday switches, in the middle of the strip.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (!compact) {
                            DesktopQuickSettings(
                                onOpenAudioOutput = onOpenAudioOutput,
                                onOpenAllSettings = onOpenAllSettings,
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The bottom bar has its own when it is the one in use.
                    if (showTransport) {
                        Icon(
                            Icons.Rounded.VolumeUp,
                            DesktopStrings["d_volume", "Volume"],
                            tint = DesktopSecondary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        DesktopThinSlider(
                            value = volume,
                            onValueChange = onVolumeChange,
                            idleHeight = 4.dp,
                            activeHeight = 8.dp,
                            modifier = Modifier.width(78.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    // Beside the quick switches when they are shown; otherwise here.
                    if (showTransport || compact) DesktopToolbarButton(onClick = onOpenAudioOutput, size = 32.dp) {
                        Icon(
                            Icons.Rounded.Headphones,
                            DesktopStrings["audio_output", "Audio output"],
                            tint = DesktopSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    // The words and the queue are the player's own now; kept for the old layout.
                    if (showTransport) DesktopToolbarButton(onClick = onOpenLyrics, size = 32.dp) {
                        Icon(
                            BitChordIcons.LyricsQuote,
                            DesktopStrings["lyrics", "Lyrics"],
                            tint = if (sidePanel == DesktopSidePanel.LYRICS) Color.White else DesktopSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    if (showTransport) DesktopToolbarButton(onClick = onOpenQueue, size = 32.dp) {
                        Icon(
                            BitChordIcons.Queue,
                            DesktopStrings["queue", "Queue"],
                            tint = if (sidePanel == DesktopSidePanel.QUEUE) Color.White else DesktopSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            // Windows' own place for them: the far right of the strip, flush with the top edge.
            if (inlineCaption && DesktopCaptionOnRight) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .desktopWindowGlass(DesktopChromeEdge.BOTTOM),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    DesktopWindowButtons()
                }
            }
            }
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                thickness = 1.dp,
                color = desktopChromeDivider(),
            )
        }
    }
}

@Composable
internal fun DesktopToolbarButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .desktopHoverWash()
            .clickable(
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DesktopSidebar(
    destination: DesktopDestination,
    settingsOpen: Boolean,
    accountPlaylists: List<ShelfItem>,
    localPlaylists: List<DesktopPlaylist>,
    openedCollectionId: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onOpenAccountPlaylist: (ShelfItem) -> Unit,
    onOpenLocalPlaylist: (DesktopPlaylist) -> Unit,
    onOpenSettings: () -> Unit,
    accountAvatar: String?,
    accountName: String?,
    onOpenAccounts: () -> Unit,
    /** Search was picked: the sidebar's box is the page's field, so it takes the focus. */
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    onImportPlaylist: () -> Unit,
) {
    if (DesktopFrameProfiler.recompositions) androidx.compose.runtime.SideEffect { DesktopFrameProfiler.count("sidebar") }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            runCatching { searchFocusRequester.requestFocus() }
            onSearchFocused()
        }
    }
    val windowInfo = LocalWindowInfo.current
    var searchFocused by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }

    LaunchedEffect(windowInfo.isWindowFocused) {
        if (!windowInfo.isWindowFocused) {
            restoreSearchFocus = searchFocused
        } else if (restoreSearchFocus) {
            // Let the desktop window finish its activation before asking Compose to put the native
            // text input target back in focus.
            yield()
            searchFocusRequester.requestFocus()
            restoreSearchFocus = false
        }
    }

    val titleBarEnabled by DesktopTitleBarSetting.enabled.collectAsState()
    val inlineCaption = DesktopPlatform.drawsOwnWindowFrame && !titleBarEnabled
    // Rows the listener has tucked away; keyed the same way the sidebar lists them.
    val hiddenSidebar by DesktopSidebarVisibility.hidden.collectAsState()
    Box(
        Modifier
            .width(220.dp)
            .fillMaxHeight()
            .desktopWindowGlass(DesktopChromeEdge.END, fade = 0.07f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // The sidebar runs to the top of the window, so its head is the window's caption: the
            // three buttons, and room to take hold of the window by.
            if (inlineCaption) {
                DesktopTitleBarDragArea(Modifier.fillMaxWidth().height(SIDEBAR_CAPTION_HEIGHT)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = if (DesktopPlatform.isMac) 16.dp else 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        // On Windows the buttons live at the other end of the window; the
                        // sidebar's head is just somewhere to take hold of it by.
                        if (!DesktopCaptionOnRight) {
                            DesktopWindowButtons()
                        } else {
                            Text(
                                "BitChord",
                                color = DesktopSecondary.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        Column(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = if (inlineCaption) 4.dp else 16.dp, bottom = 16.dp),
        ) {
            DesktopSearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                focusRequester = searchFocusRequester,
                onFocusChanged = { searchFocused = it },
            )
            Spacer(Modifier.height(20.dp))
            DesktopSidebarItem(BitChordIcons.Home, DesktopStrings["d_home", "Home"], destination == DesktopDestination.LISTEN_NOW) {
                onDestinationSelected(DesktopDestination.LISTEN_NOW)
            }
            DesktopSidebarItem(BitChordIcons.Explore, DesktopStrings["explore", "Explore"], destination == DesktopDestination.EXPLORE) {
                onDestinationSelected(DesktopDestination.EXPLORE)
            }
            DesktopSidebarItem(BitChordIcons.Library, DesktopStrings["library", "Library"], destination == DesktopDestination.LIBRARY) {
                onDestinationSelected(DesktopDestination.LIBRARY)
            }
            DesktopSidebarItem(BitChordIcons.Search, DesktopStrings["search", "Search"], destination == DesktopDestination.SEARCH) {
                onDestinationSelected(DesktopDestination.SEARCH)
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = desktopChromeDivider())
            Spacer(Modifier.height(12.dp))
            if (HISTORY_KEY !in hiddenSidebar) {
                DesktopSidebarItem(
                    BitChordIcons.Clock,
                    DesktopStrings["history", "History"],
                    destination == DesktopDestination.HISTORY,
                    onClick = { onDestinationSelected(DesktopDestination.HISTORY) },
                    onHide = { DesktopSidebarVisibility.hide(HISTORY_KEY, "History") },
                )
            }
            val queued by DesktopDownloadQueue.active.collectAsState()
            if (DOWNLOADS_KEY !in hiddenSidebar) {
                DesktopSidebarItem(
                    BitChordIcons.Download,
                    if (queued.isEmpty()) DesktopStrings["downloads", "Downloads"] else DesktopStrings["downloads", "Downloads"] + " · ${queued.size}",
                    destination == DesktopDestination.DOWNLOADS,
                    onClick = { onDestinationSelected(DesktopDestination.DOWNLOADS) },
                    onHide = { DesktopSidebarVisibility.hide(DOWNLOADS_KEY, "Downloads") },
                )
            }
            if (LOCAL_MUSIC_KEY !in hiddenSidebar) {
                DesktopSidebarItem(
                    BitChordIcons.Library,
                    DesktopStrings["local_music", "Local Music"],
                    destination == DesktopDestination.LOCAL_MUSIC,
                    onClick = { onDestinationSelected(DesktopDestination.LOCAL_MUSIC) },
                    onHide = { DesktopSidebarVisibility.hide(LOCAL_MUSIC_KEY, "Local Music") },
                )
            }
            // In sight, not only at the foot of the Library: the way in from other services.
            DesktopSidebarItem(Icons.Rounded.Add, DesktopStrings["playlist_import_title", "Import a playlist"], false, onClick = onImportPlaylist)
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = desktopChromeDivider())
            Spacer(Modifier.height(12.dp))
            val visibleAccount = accountPlaylists.filterNot { "account:${it.browseId}" in hiddenSidebar }
            val visibleLocal = localPlaylists.filterNot { "local:${it.id}" in hiddenSidebar }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(visibleAccount, key = { "account:${it.browseId}" }) { playlist ->
                    DesktopSidebarItem(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        playlist.title,
                        openedCollectionId == playlist.browseId,
                        onClick = { onOpenAccountPlaylist(playlist) },
                        onHide = {
                            DesktopSidebarVisibility.hide("account:${playlist.browseId}", playlist.title)
                        },
                    )
                }
                items(visibleLocal, key = { "local:${it.id}" }) { playlist ->
                    DesktopSidebarItem(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        playlist.title,
                        openedCollectionId == playlist.id,
                        onClick = { onOpenLocalPlaylist(playlist) },
                        onHide = { DesktopSidebarVisibility.hide("local:${playlist.id}", playlist.title) },
                    )
                }
                if (visibleAccount.isEmpty() && visibleLocal.isEmpty()) {
                    item {
                        Text(
                            DesktopStrings["d_playlists_appear_here", "Your playlists will appear here"],
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            color = DesktopSecondary.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            HorizontalDivider(color = desktopChromeDivider())
            Spacer(Modifier.height(8.dp))
            if (hiddenSidebar.isNotEmpty()) {
                DesktopSidebarRestoreRow(hiddenSidebar)
            }
            // Who is signed in, at the foot of the column, the way YouTube keeps it.
            DesktopSidebarAccount(avatar = accountAvatar, name = accountName, onClick = onOpenAccounts)
            DesktopSidebarItem(Icons.Rounded.Settings, DesktopStrings["settings", "Settings"], settingsOpen) {
                onOpenSettings()
            }
        }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(desktopChromeDivider()),
        )
    }
}

/** The signed-in account as a row of the sidebar, made exactly like the rows around it. */
@Composable
private fun DesktopSidebarAccount(avatar: String?, name: String?, onClick: () -> Unit) {
    DesktopSidebarItem(
        icon = null,
        label = name ?: DesktopStrings["sign_in", "Sign in"],
        selected = false,
        leading = {
            if (avatar != null) {
                DesktopArtwork(
                    avatar,
                    Modifier.size(SIDEBAR_MARK).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    px = 64,
                )
            } else {
                Icon(Icons.Rounded.Person, null, tint = DesktopSecondary, modifier = Modifier.size(18.dp))
            }
        },
        onClick = onClick,
    )
}

/** The square each sidebar row's icon or picture sits in. */
private val SIDEBAR_MARK = 20.dp

@Composable
private fun DesktopSidebarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    selected: Boolean,
    /** Drawn in the icon's place — the account's picture — at the icon's size. */
    leading: (@Composable () -> Unit)? = null,
    /** When set, a right-click offers to hide the row — see [DesktopSidebarVisibility]. */
    onHide: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // A selection that glides in and a hover that nudges, rather than a state that snaps.
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by androidx.compose.animation.animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.13f)
            hovered -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        tween(220),
        label = "sidebarFill",
    )
    val nudge by animateFloatAsState(if (hovered && !selected) 1f else 0f, tween(180), label = "sidebarNudge")
    val iconScale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "sidebarIcon",
    )
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(fill)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .then(
                    if (onHide == null) Modifier
                    else Modifier.pointerInput(onHide) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    menuOpen = true
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
                )
                .padding(horizontal = 9.dp, vertical = 7.dp)
                .graphicsLayer { translationX = nudge * 3.dp.toPx() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Every row's leading mark in the same square, so icons, the avatar and the words after
            // them all line up down the column.
            Box(Modifier.size(SIDEBAR_MARK).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, contentAlignment = Alignment.Center) {
                if (leading != null) {
                    leading()
                } else if (icon != null) {
                    Icon(icon, label, tint = if (selected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = if (selected) Color.White else DesktopSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onHide != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(DesktopStrings["hide_from_sidebar", "Hide from sidebar"]) },
                    onClick = {
                        menuOpen = false
                        onHide()
                    },
                )
            }
        }
    }
}

/** The secondary destinations that can be tucked away, by the key they hide under. */
private const val HISTORY_KEY = "dest:HISTORY"
private const val DOWNLOADS_KEY = "dest:DOWNLOADS"
private const val LOCAL_MUSIC_KEY = "dest:LOCAL_MUSIC"

/**
 * The "N hidden" row that brings tucked-away rows back: a menu of everything
 * hidden, each restoring on its own, plus a "Show all". Only drawn while
 * something is hidden, so an ordinary sidebar never shows it.
 */
@Composable
private fun DesktopSidebarRestoreRow(hidden: Map<String, String>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .desktopHoverFill()
                .clip(RoundedCornerShape(7.dp))
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.RemoveCircleOutline,
                null,
                tint = DesktopSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                DesktopStrings.format("n_hidden", hidden.size, fallback = "%1\$d hidden"),
                color = DesktopSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            hidden.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        DesktopSidebarVisibility.show(key)
                        if (DesktopSidebarVisibility.hidden.value.isEmpty()) open = false
                    },
                )
            }
            if (hidden.size > 1) {
                DropdownMenuItem(
                    text = { Text(DesktopStrings["show_all", "Show all"]) },
                    onClick = {
                        DesktopSidebarVisibility.showAll()
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopBottomChrome(
    compact: Boolean,
    destination: DesktopDestination,
    song: Song?,
    isPlaying: Boolean,
    onDestinationSelected: (DesktopDestination) -> Unit,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    /** The glass now-playing bar, in place of the small pill, when that is the chosen look. */
    nowPlaying: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (compact && nowPlaying != null) {
            nowPlaying()
        } else if (compact && song != null) {
            DesktopMiniPlayer(
                song = song,
                isPlaying = isPlaying,
                onExpand = onExpand,
                onPlayPause = onPlayPause,
                onNext = onNext,
            )
        }
        DesktopFloatingNavigation(destination, onDestinationSelected)
    }
}

@Composable
private fun DesktopFloatingNavigation(
    destination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
) {
    val tabs = listOf(
        DesktopDestination.LISTEN_NOW to (BitChordIcons.Play to "Play"),
        DesktopDestination.EXPLORE to (BitChordIcons.Explore to "Explore"),
        DesktopDestination.LIBRARY to (BitChordIcons.Library to "Library"),
        DesktopDestination.SEARCH to (BitChordIcons.Search to "Search"),
    )
    val selected = tabs.firstOrNull { it.first == destination }?.first ?: DesktopDestination.LIBRARY
    val shape = RoundedCornerShape(percent = 50)
    Surface(
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .desktopFloatingGlass(shape)
            .clip(shape),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEach { (item, iconAndLabel) ->
                val isSelected = item == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) DesktopAccent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onDestinationSelected(item) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(iconAndLabel.first, iconAndLabel.second, tint = if (isSelected) DesktopAccent else DesktopSecondary, modifier = Modifier.size(20.dp))
                    Text(iconAndLabel.second, color = if (isSelected) Color.White else DesktopSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DesktopMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .desktopFloatingGlass(shape)
            .clip(shape)
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesktopArtwork(song.thumbnailUrl, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), px = ROW_ART_PX)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.FastForward, DesktopStrings["widget_next", "Next"], tint = Color.White)
        }
    }
}

@Composable
internal fun DesktopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    placeholder: String = DesktopStrings["search", "Search"],
) {
    val searchShape = RoundedCornerShape(7.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(searchShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), searchShape)
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(DesktopAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        BitChordIcons.Search,
                        contentDescription = null,
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(placeholder, color = DesktopSecondary, maxLines = 1)
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun DesktopFrame(
    containerColor: Color,
    modifier: Modifier = Modifier,
    /**
     * The open page's own colours, painted across the whole window rather than only the content
     * area, so the title bar, top bar and sidebar take their tint from the page they are framing
     * instead of staying a flat slab beside it.
     */
    /** Receives true when its solid base should be left clear for the system material. */
    backdrop: @Composable (transparentBase: Boolean) -> Unit,
    topBar: @Composable (Boolean) -> Unit,
    sidebar: @Composable () -> Unit,
    bottomBar: @Composable (Boolean) -> Unit,
    /** The column on the far side of the page — the lyrics or the queue, when one is open. */
    trailing: @Composable () -> Unit = {},
    /** The floating now-playing bar along the bottom of a full-width window, when there is one. */
    bottomPlayer: (@Composable () -> Unit)? = null,
    /**
     * How far [bottomPlayer] is out, 0 to 1, read while drawing — for a bar that follows the page
     * as it scrolls instead of popping in and out. Always out when null.
     */
    bottomReveal: (() -> Float)? = null,
    /** Whether pages keep room at their foot for [bottomPlayer], even while it is away for a moment. */
    bottomPlayerRoom: Boolean = bottomPlayer != null,
    /** The player over the top of the page — the home card that floats away; drawn over the page. */
    topPlayer: (@Composable () -> Unit)? = null,
    /** Room at the top of every page for [topPlayer]'s pinned bar. */
    topInset: Dp = 0.dp,
    overlay: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    if (DesktopFrameProfiler.recompositions) androidx.compose.runtime.SideEffect { DesktopFrameProfiler.count("frame") }
    // Held out here rather than inside the constraints box.
    val haze = remember { HazeState() }
    // A frame every half minute at least: Skiko's drawing thread is pooled, and a thread left
    // without work for a minute is retired — the next frame, the first of an animation more often
    // than not, then started on a new one. One quiet redraw now and then keeps it.
    val drawerKeepAlive = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val gpuHeat = androidx.compose.ui.graphics.rememberGraphicsLayer()
    LaunchedEffect(Unit) {
        while (true) {
            // The thread Skiko presents frames on, raised too — from inside its own pool.
            DesktopSchedulingBoost.skikoPresentDispatcher()?.let { present ->
                withContext(present) { DesktopSchedulingBoost.boostDrawingThread() }
            }
            kotlinx.coroutines.delay(30_000)
            drawerKeepAlive.intValue++
        }
    }
    // What the liquid-glass bars refract: the page, as it scrolls under them.
    val glassBackdrop = rememberDesktopGlassBackdrop()
    // With a system material active, the window stays clear under its chrome. The app-background
    // preference decides whether the page and side column also stay clear or retain their solid
    // gray ground.
    val material by DesktopWindowBackdrop.active.collectAsState()
    val appBackground by DesktopWindowBackdrop.appBackground.collectAsState()
    val glass = material != DesktopBackdrop.OFF
    val materialBehindApp = glass && appBackground
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val roundCorners = DesktopPlatform.isMac && !maximized
    CompositionLocalProvider(LocalDesktopHaze provides haze, LocalDesktopGlassBackdrop provides glassBackdrop) {
        Box(
            with(DesktopFrameProfiler) { modifier.profiledDraw() }
                // Raises the thread that draws each frame, whichever it is — see
                // [DesktopSchedulingBoost.boostDrawingThread] — and keeps it from being retired.
                .drawWithContent {
                    DesktopSchedulingBoost.boostDrawingThread()
                    drawerKeepAlive.intValue
                    DesktopPlayerWarmup.ramp.intValue
                    drawContent()
                    // While warming up for the player: real work for the GPU — a window-sized blur,
                    // all but invisible — so the driver brings its clocks up before the player
                    // opens rather than during the slide. A still window it lets drop to idle
                    // clocks, and the first frames of the player then came out at 90-odd a second.
                    if (DesktopPlayerWarmup.ramping) {
                        gpuHeat.renderEffect = androidx.compose.ui.graphics.BlurEffect(36f, 36f)
                        gpuHeat.alpha = 0.004f
                        gpuHeat.record(androidx.compose.ui.unit.IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                            drawRect(Brush.linearGradient(listOf(Color(0xFF223344), Color(0xFF443322))))
                        }
                        drawLayer(gpuHeat)
                    }
                }
                .fillMaxSize()
                .then(if (roundCorners) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier)
                .then(if (glass) Modifier else Modifier.background(containerColor)),
        ) {
            // Both sources of the same state: the chrome blurs the backdrop behind it, and the
            // floating bottom bar blurs the page scrolling under it.
            // Nothing under a player that fills the window is drawn — see [DesktopPlayerCover].
            val underPlayer = Modifier.drawWithContent { if (!DesktopPlayerCover.covering) drawContent() }
            if (!glass) Box(Modifier.fillMaxSize().then(underPlayer).hazeSource(haze)) { backdrop(false) }
            // Not the column itself: the player is drawn inside it, as its overlay.
            Column(Modifier.fillMaxSize()) {
                // Above everything, and outside the box the rest of the window is drawn in, because
                // that is what a title bar is.
                Box(underPlayer) { DesktopTitleBar() }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    BoxWithConstraints(Modifier.fillMaxSize().then(underPlayer)) {
                        val compact = maxWidth < 980.dp
                        // The sidebar runs the full height of the window, its head the window's
                        // caption; the player's bar and the page share the column beside it.
                        Row(Modifier.fillMaxSize()) {
                            if (!compact) sidebar()
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                            topBar(compact)
                            Row(Modifier.fillMaxWidth().weight(1f)) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(
                                            if (glass && !materialBehindApp) {
                                                Modifier.background(containerColor)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    Box(Modifier.fillMaxSize().hazeSource(haze).glassSource(glassBackdrop)) {
                                        if (glass) backdrop(materialBehindApp)
                                        content(
                                            PaddingValues(
                                                start = 0.dp,
                                                end = 0.dp,
                                                top = if (compact) 0.dp else topInset,
                                                // Room for the bar that floats over this, so the
                                                // last row can still be scrolled clear of it.
                                                bottom = when {
                                                    compact -> 128.dp
                                                    bottomPlayerRoom -> 108.dp
                                                    else -> 16.dp
                                                },
                                            ),
                                        )
                                    }
                                    if (compact) {
                                        Box(Modifier.align(Alignment.BottomCenter)) { bottomBar(true) }
                                    } else {
                                        // Rises from the bottom edge of the window when a track
                                        // starts, and sinks back into it when there is none.
                                        var lastBottom by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
                                        if (bottomPlayer != null) lastBottom = bottomPlayer
                                        DesktopScrollLinkedBar(
                                            visible = bottomPlayer != null,
                                            reveal = bottomReveal,
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        ) {
                                            Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { lastBottom?.invoke() }
                                        }
                                        topPlayer?.invoke()
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .then(
                                            if (glass && !materialBehindApp) {
                                                Modifier.background(containerColor)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) { trailing() }
                            }
                            }
                        }
                    }
                    overlay()
                }
            }
        }
    }
}

/**
 * What [DesktopFrame] paints behind the window.
 *
 * [artworkUrl] is the page's lead artwork when it has one, and the mesh built from it is laid under
 * ink so a page of text stays readable over it.
 */
@Composable
private fun DesktopPageBackdrop(
    artworkUrl: String?,
    transparentBase: Boolean,
    /** The playing cover, for the moving colour behind the page; null for none. */
    livingCoverUrl: String? = null,
    living: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (transparentBase) Modifier else Modifier.background(DesktopBackground)),
    ) {
        if (artworkUrl == null) {
            if (living) DesktopLivingBackdrop(livingCoverUrl, Modifier.fillMaxSize())
            return@Box
        }
        DesktopMesh(artworkUrl)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color.Black.copy(alpha = 0.72f),
                        Color.Black.copy(alpha = 0.88f),
                    ),
                ),
            ),
        )
    }
}

/** What the mesh is averaged from — coarse by design. */
/** Whether AutoPlay may offer a song this session has already played or suggested. */
internal const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"

internal const val KEY_LIBRARY_SORT = "library_sort"

private const val MESH_SOURCE_PX = 120



/** Enough to scroll through, short of turning the shelf into the history page. */
/** The Library's "On device" cards — the phone's ids for the two folders, so their cards draw alike. */
private const val LOCAL_DOWNLOADS_ID = "local:downloads"
private const val LOCAL_MUSIC_ID = "local:all"
private const val LOCAL_PLAYLIST_PREFIX = "desktop-playlist:"



/**
 * An artist: their picture, the numbers under it, their top songs, and the carousels of what they
 * have released.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopArtistPage(
    state: UiState<ArtistPage>,
    fallbackName: String,
    onRetry: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    /** The ⋮ on a top song, or a right-click on it. */
    onOpenMenu: (Song) -> Unit,
    onShelfItemClick: (ShelfItem, String?) -> Unit,
    /** Null for a guest: a channel subscription is the account's. */
    onToggleSubscription: ((SubscriptionState) -> Unit)?,
    contentPadding: PaddingValues,
) {
    DesktopPageScaffold(contentPadding) {
        when (state) {
            UiState.Loading -> Box(Modifier.fillMaxSize()) {
                DesktopLoadingPage("Loading $fallbackName…")
            }
            is UiState.Error -> Box(Modifier.fillMaxSize()) {
                DesktopErrorPage(state.message, onRetry)
            }
            is UiState.Success -> {
                val artist = state.data
                val name = artist.name?.takeIf(String::isNotBlank) ?: fallbackName
                val palette = rememberDesktopArtworkPalette(artist.thumbnailUrl)
                // Android caps the top-songs block and pages it sideways so the release shelves are
                // not buried under a hundred rows.
                val top = artist.songs.take(MAX_ARTIST_SONGS)
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = rememberPageListState(),
                        contentPadding = pagePadding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item(key = "artist-header") {
                            DesktopArtistBanner(artist.thumbnailUrl, name, palette)
                        }
                        if (artist.subscriberCountText != null || artist.monthlyListenerCount != null) {
                            item(key = "artist-stats") {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    // YouTube's own text already reads "1.2M subscribers" in full,
                                    // so only the number is kept and the label re-said in the app's
                                    // words.
                                    artist.subscriberCountText?.let {
                                        DesktopStatChip(Icons.Rounded.Person, "${it.substringBefore(' ')} subscribers")
                                    }
                                    artist.monthlyListenerCount?.let {
                                        DesktopStatChip(Icons.Rounded.GraphicEq, "${it.substringBefore(' ')} monthly listeners")
                                    }
                                }
                            }
                        }
                        item(key = "artist-actions") {
                            // The phone's artist controls: subscribing first, where saving sits on a
                            // release, then the labelled Play pill and Shuffle.
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = DesktopPageGutter, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val subscription = artist.subscription
                                if (subscription != null && onToggleSubscription != null) {
                                    DesktopCircleButton(
                                        if (subscription.subscribed) BitChordIcons.Check else BitChordIcons.Plus,
                                        if (subscription.subscribed) {
                                            DesktopStrings["unsubscribe", "Unsubscribe"]
                                        } else {
                                            DesktopStrings["subscribe", "Subscribe"]
                                        },
                                        onClick = { onToggleSubscription(subscription) },
                                    )
                                }
                                DesktopPlayPill(
                                    label = DesktopStrings["play", "Play"],
                                    onClick = { if (top.isNotEmpty()) onPlaySongs(top, 0) },
                                )
                                DesktopCircleButton(BitChordIcons.Shuffle, DesktopStrings["shuffle", "Shuffle"], onClick = { onShuffle(top) })
                            }
                        }
                        artist.description?.takeIf(String::isNotBlank)?.let { blurb ->
                            item(key = "artist-about") {
                                Column(Modifier.padding(top = 16.dp)) {
                                    SectionTitle(DesktopStrings["d_about_artist", "About artist"])
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        blurb,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = DesktopPageGutter),
                                    )
                                }
                            }
                        }
                        if (top.isNotEmpty()) {
                            // Paged sideways four to a column, the way Home lists Recents.
                            item(key = "artist-top-songs") {
                                Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                                    SectionTitle(DesktopStrings["top_songs", "Top songs"])
                                    Spacer(Modifier.height(8.dp))
                                    BoxWithConstraints {
                                        val columnWidth = trackColumnWidth(maxWidth)
                                        ShelfRow(
                                            contentPadding = PaddingValues(horizontal = DesktopPageGutter),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            items(top.withIndex().chunked(ARTIST_SONGS_PER_COLUMN)) { column ->
                                                Column(Modifier.width(columnWidth)) {
                                                    column.forEach { (index, song) ->
                                                        CompactTrackRow(
                                                            title = song.title,
                                                            subtitle = song.artist,
                                                            thumbnailUrl = song.thumbnailUrl,
                                                            onClick = { onPlaySongs(top, index) },
                                                            onLongPress = { onOpenMenu(song) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        artist.sections.forEach { shelf ->
                            item(key = "artist-shelf-${shelf.title}") {
                                Box(Modifier.padding(top = 18.dp)) {
                                    DesktopShelf(shelf, onItemClick = onShelfItemClick)
                                }
                            }
                        }
                        if (top.isEmpty() && artist.sections.isEmpty()) {
                            item {
                                DesktopEmptyPage(
                                    Icons.Rounded.Person,
                                    "Nothing to show for $name",
                                    "YouTube Music has no songs or releases on this page.",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The photograph, and the name across the foot of it. */
@Composable
private fun DesktopArtistBanner(url: String?, name: String, palette: DesktopArtworkPalette) {
    Box(Modifier.fillMaxWidth().height(ARTIST_BANNER_HEIGHT)) {
        DesktopArtwork(url, Modifier.matchParentSize(), px = HEADER_ART_PX)
        // Two washes rather than one.
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to palette.primary.copy(alpha = 0.18f),
                    1f to DesktopSurface,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.4f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            ),
        )
        Text(
            name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 18.dp),
        )
    }
}

@Composable
private fun DesktopStatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(DesktopGlass.copy(alpha = 0.55f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = DesktopSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/** The artist page that is open, and the name to bill it under until it loads. */
/** The page an artist's tracks were started from. */
private fun artistSource(target: DesktopArtistTarget) =
    DesktopQueueSource(target.name, PlaybackSourceType.BROWSE, target.browseId)

/** The album or playlist a queue was started from. */
private fun collectionSource(collection: DesktopCollection) =
    DesktopQueueSource(collection.title, PlaybackSourceType.BROWSE, collection.browseId)

/** Where a queue was started from, for the player's "Playing from" caption. */
internal data class DesktopQueueSource(
    val title: String,
    val type: PlaybackSourceType,
    val id: String? = null,
)

/**
 * Where this row says it was played from, as the shared queue rules take it — the phone's
 * `play()` makes the same one from the first row of what it was handed.
 */
private fun Song.queueSource(): QueueSource = QueueSource(
    title = playbackSource ?: albumName ?: "Queue",
    type = playbackSourceType ?: PlaybackSourceType.QUEUE,
    id = playbackSourceId,
)

/** Stamps [source] onto a row, leaving one that already names its origin alone. */
private fun Song.withSource(source: DesktopQueueSource?): Song = when {
    source == null || playbackSource != null -> this
    else -> copy(
        playbackSource = source.title,
        playbackSourceType = source.type,
        playbackSourceId = source.id,
    )
}

private data class DesktopArtistTarget(val browseId: String, val name: String)

/** One place the window has been: which page, and whatever was open on it. */
private data class DesktopNavEntry(
    val destination: DesktopDestination,
    val artist: DesktopArtistTarget?,
    val collection: DesktopCollection?,
    val mood: MoodGenre?,
    val showAll: HomeShelf?,
    val replay: Boolean,
    val settings: DesktopSettingsPage?,
) {
    /** What makes two entries the same place: an album that paged in more tracks has not moved. */
    val key: List<Any?>
        get() = listOf(destination, artist?.browseId, collection?.browseId, mood?.browseId, mood?.params, showAll?.title, replay, settings)
}

/** Deep enough for any way back anyone takes; the oldest fall off. */
private const val NAV_HISTORY_LIMIT = 50

/** The strip at the head of the sidebar that holds the window's buttons and can be dragged by. */
private val SIDEBAR_CAPTION_HEIGHT = 34.dp


@Composable
private fun DesktopHistoryPage(
    history: List<Song>,
    onSongClick: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopPageScaffold(contentPadding) {
        val isCurrentRow = rememberPlayingRowMatcher(history)
        LazyColumn(
            state = rememberPageListState(),
            contentPadding = pagePadding(start = DesktopPageGutter, end = DesktopPageGutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (history.isEmpty()) item { DesktopEmptyPage(BitChordIcons.Clock, "Nothing played yet", "Songs you play will show up here.") }
            else items(history, key = Song::videoId) {
                DesktopSongRow(
                    song = it,
                    isCurrent = isCurrentRow(it),
                    liked = false,
                    onClick = onSongClick,
                    onToggleLike = null,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = it.videoId in downloadedIds,
                    downloadInProgress = it.videoId in downloadInProgress,
                    menu = menu,
                )
            }
        }
    }
}

@Composable
private fun DesktopDownloadsPage(
    downloads: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    DesktopLocalMusicPage(
        songs = downloads,
        onSongClick = onSongClick,
        contentPadding = contentPadding,
        menu = menu,
        title = DesktopStrings["downloads", "Downloads"],
        subtitle = DesktopStrings["d_available_offline", "Available offline"],
        emptyIcon = BitChordIcons.Download,
        emptyTitle = DesktopStrings["d_no_downloads", "No downloads"],
        emptyDescription = DesktopStrings["d_downloaded_songs_will_appear_here", "Downloaded songs will appear here."],
        persistenceKey = "downloaded",
    )
}

@Composable
private fun DesktopLocalMusicPage(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    title: String = "Local Music",
    subtitle: String = "Audio files found in Music and Downloads",
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = BitChordIcons.MusicNote,
    emptyTitle: String = "No local music",
    emptyDescription: String = "Put audio files in your Music folder and reopen this page.",
    persistenceKey: String = "local",
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val persistence = remember { DesktopPersistence() }
    var selectedTab by remember(persistenceKey) { mutableStateOf(0) }
    var searchQuery by remember(persistenceKey) { mutableStateOf("") }
    var view by remember(persistenceKey) {
        mutableStateOf(
            if (persistence.string("${persistenceKey}_view", "LIST") == "GRID") {
                DesktopLibraryView.GRID
            } else {
                DesktopLibraryView.LIST
            },
        )
    }
    var sort by remember(persistenceKey) {
        mutableStateOf(
            runCatching {
                DesktopLibrarySort.valueOf(persistence.string("${persistenceKey}_sort", "TITLE_ASC"))
            }.getOrDefault(DesktopLibrarySort.TITLE_ASC),
        )
    }
    var drillDown by remember(persistenceKey) { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val filteredSongs = remember(songs, sort, searchQuery) {
        songs.sortedForDesktopLibrary(sort).filter { song ->
            searchQuery.isBlank() || song.title.contains(searchQuery, true) || song.artist.contains(searchQuery, true) ||
                song.albumName.orEmpty().contains(searchQuery, true)
        }
    }
    val artists = remember(filteredSongs) {
        filteredSongs.groupBy { it.artist.ifBlank { DesktopStrings["d_on_this_computer", "On This Computer"] } }
            .entries.sortedBy { it.key.lowercase() }
    }
    val albums = remember(filteredSongs) {
        filteredSongs.groupBy { it.albumName?.takeIf(String::isNotBlank) ?: DesktopStrings["d_unknown_album", "Unknown Album"] }
            .entries.sortedBy { it.key.lowercase() }
    }
    DesktopPageScaffold(contentPadding) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = DesktopPageGutter),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {},
                    modifier = Modifier.weight(1f),
                    placeholder = DesktopStrings["d_search_your_music", "Search your music"],
                )
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = {
                    sort = when (sort) {
                        DesktopLibrarySort.TITLE_ASC -> DesktopLibrarySort.TITLE_DESC
                        DesktopLibrarySort.TITLE_DESC -> DesktopLibrarySort.DATE_ADDED
                        DesktopLibrarySort.DATE_ADDED -> DesktopLibrarySort.DATE_MODIFIED
                        DesktopLibrarySort.DATE_MODIFIED -> DesktopLibrarySort.TITLE_ASC
                    }
                    persistence.saveString("${persistenceKey}_sort", sort.name)
                }) {
                    Text(sort.label())
                }
                IconButton(onClick = {
                    view = if (view == DesktopLibraryView.LIST) DesktopLibraryView.GRID else DesktopLibraryView.LIST
                    persistence.saveString("${persistenceKey}_view", view.name)
                }) {
                    Icon(if (view == DesktopLibraryView.LIST) BitChordIcons.GridView else BitChordIcons.ListView, DesktopStrings["d_change_view", "Change view"])
                }
            }
            Spacer(Modifier.height(10.dp))
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                listOf(DesktopStrings["songs", "Songs"], DesktopStrings["artists", "Artists"], DesktopStrings["albums", "Albums"]).forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (songs.isEmpty()) {
                DesktopEmptyPage(emptyIcon, emptyTitle, emptyDescription)
            } else if (drillDown != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { drillDown = null }) { Icon(Icons.Rounded.ArrowBack, DesktopStrings["back", "Back"]) }
                    Text(drillDown!!.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                DesktopLibrarySongContent(drillDown!!.second, view, onSongClick, menu)
            } else when (selectedTab) {
                0 -> DesktopLibrarySongContent(filteredSongs, view, onSongClick, menu)
                1 -> DesktopLibraryGroupingContent(artists, view, onGroupClick = { drillDown = it })
                else -> DesktopLibraryGroupingContent(albums, view, onGroupClick = { drillDown = it })
            }
        }
    }
}

private fun DesktopLibrarySort.label(): String = when (this) {
    DesktopLibrarySort.TITLE_ASC -> "A–Z"
    DesktopLibrarySort.TITLE_DESC -> "Z–A"
    DesktopLibrarySort.DATE_ADDED -> DesktopStrings["d_sort_added", "Added"]
    DesktopLibrarySort.DATE_MODIFIED -> DesktopStrings["d_sort_modified", "Modified"]
}

@Composable
private fun DesktopLibrarySongContent(
    songs: List<Song>,
    view: DesktopLibraryView,
    onSongClick: (Song) -> Unit,
    menu: (@Composable (Song) -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        DesktopEmptyPage(BitChordIcons.Search, "No matching music", "Try a different search.")
    } else if (view == DesktopLibraryView.GRID) {
        LazyVerticalGrid(
            state = rememberPageGridState(),
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(songs, key = Song::videoId) { song ->
                Column(Modifier.fillMaxWidth().clickable { onSongClick(song) }) {
                    DesktopArtwork(song.thumbnailUrl, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(7.dp))
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(song.artist, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        val isCurrentRow = rememberPlayingRowMatcher(songs)
        LazyColumn(
            state = rememberPageListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(songs, key = Song::videoId) { song ->
                DesktopSongRow(
                    song,
                    isCurrent = isCurrentRow(song),
                    liked = false,
                    onClick = onSongClick,
                    onToggleLike = null,
                    downloaded = song.localPath != null,
                    menu = menu,
                )
            }
        }
    }
}

@Composable
private fun DesktopLibraryGroupingContent(
    groups: List<Map.Entry<String, List<Song>>>,
    view: DesktopLibraryView,
    onGroupClick: (Pair<String, List<Song>>) -> Unit,
) {
    if (groups.isEmpty()) {
        DesktopEmptyPage(BitChordIcons.Search, "Nothing here yet", "Music will be grouped as it is added.")
    } else if (view == DesktopLibraryView.GRID) {
        LazyVerticalGrid(
            state = rememberPageGridState(),
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            gridItems(groups, key = { it.key }) { group ->
                Column(Modifier.fillMaxWidth().clickable { onGroupClick(group.key to group.value) }) {
                    DesktopArtwork(group.value.firstOrNull()?.thumbnailUrl, Modifier.fillMaxWidth().height(150.dp).clip(CircleShape))
                    Spacer(Modifier.height(7.dp))
                    Text(group.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${group.value.size} songs", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    } else {
        LazyColumn(
            state = rememberPageListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(groups, key = { it.key }) { group ->
                Row(
                    Modifier.fillMaxWidth().clickable { onGroupClick(group.key to group.value) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopArtwork(group.value.firstOrNull()?.thumbnailUrl, Modifier.size(54.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text("${group.value.size} songs", color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(BitChordIcons.Play, DesktopStrings["open", "Open"], tint = DesktopSecondary)
                }
            }
        }
    }
}

/** Mobile's section order, with desktop-only groups placed beside their closest mobile counterpart. */
private enum class DesktopSettingsSection {
    ACCOUNT,
    LISTEN_TOGETHER,
    SOURCES,
    AUDIO_QUALITY,
    DOWNLOADS,
    OUTPUT_PRECISION,
    AUDIO_OUTPUT,
    PLAYBACK,
    DESKTOP_PLAYBACK,
    APPEARANCE,
    LYRICS,
    LOCAL_MUSIC,
    STORAGE,
    MISCELLANEOUS,
    LANGUAGE,
    ADVANCED,
    UPDATES,
}

/**
 * Settings as a page of inset groups, the way Android presents it, held to the width the old card
 * had so the rows do not stretch across a wide window. Its query and scroll belong to the caller,
 * so they are where they were when back returns from one of the pages it opens.
 */
@Composable
private fun DesktopSettingsScreen(
    /** Opens the choice of what Home shows and in what order. */
    onCustomizeHome: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    autoplay: Boolean,
    onAutoplayChange: (Boolean) -> Unit,
    automix: Boolean,
    onAutomixChange: (Boolean) -> Unit,
    automixOnSkip: Boolean,
    onAutomixOnSkipChange: (Boolean) -> Unit,
    automixPerformance: AutomixPerformanceMode,
    onAutomixPerformanceChange: (AutomixPerformanceMode) -> Unit,
    /** Apple-style Automix phrase length in bars: 0 = adaptive, 8 or 16 = fixed. */
    automixPhraseBars: Int,
    onAutomixPhraseBarsChange: (Int) -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    repeatMode: DesktopRepeatMode,
    onRepeatModeChange: (DesktopRepeatMode) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    crossfadeSeconds: Int,
    onCrossfadeSecondsChange: (Int) -> Unit,
    animatedCanvas: Boolean,
    onAnimatedCanvasChange: (Boolean) -> Unit,
    spotifyCanvasReady: Boolean,
    onOpenSpotifyCanvasSetup: () -> Unit,
    dontRepeatSuggestions: Boolean,
    onDontRepeatSuggestionsChange: (Boolean) -> Unit,
    filterNonMusicAudio: Boolean,
    onFilterNonMusicAudioChange: (Boolean) -> Unit,
    onChooseLocalMusicFolder: (onChosen: () -> Unit) -> Unit,
    onLocalMusicFolderChanged: () -> Unit,
    syncedLyrics: Boolean,
    onSyncedLyricsChange: (Boolean) -> Unit,
    lyricsBlur: Boolean,
    onLyricsBlurChange: (Boolean) -> Unit,
    /** The sources that will actually be asked, in the order they are asked. */
    enabledLyricsSources: List<String>,
    onOpenLyricsSources: () -> Unit,
    onOpenTranslationLanguage: () -> Unit,
    onOpenEqualizer: () -> Unit,
    /** Opens the output-device picker. */
    onOpenAudioOutput: () -> Unit,
    /** Opens the listening-together party. */
    onOpenListenTogether: () -> Unit,
    showNerdStats: Boolean,
    onShowNerdStatsChange: (Boolean) -> Unit,
    fullBleedArtwork: Boolean,
    onFullBleedArtworkChange: (Boolean) -> Unit,
    legacyMeshGradient: Boolean,
    onLegacyMeshGradientChange: (Boolean) -> Unit,
    trayIconEnabled: Boolean,
    closeToTray: Boolean,
    onCloseToTrayChange: (Boolean) -> Unit,
    onTrayIconChange: (Boolean) -> Unit,
    spatialAudio: Boolean,
    onSpatialAudioChange: (Boolean) -> Unit,
    dolbyAtmos: Boolean,
    onDolbyAtmosChange: (Boolean) -> Unit,
    skipSilence: Boolean,
    onSkipSilenceChange: (Boolean) -> Unit,
    outputPrecision: String,
    onOutputPrecisionChange: (String) -> Unit,
    outputSummary: String,
    downloadQuality: String,
    onDownloadQualityChange: (String) -> Unit,
    audioQuality: DesktopAudioQuality,
    onAudioQualityChange: (DesktopAudioQuality) -> Unit,
    sleepTimerMinutes: Int?,
    sleepAfterTrack: Boolean,
    sleepRemainingMs: Long?,
    onSleepTimerCycle: () -> Unit,
    sourceConfigs: List<DesktopSourceConfig>,
    sourceStatus: Map<String, String>,
    onSourceEnabledChange: (DesktopSourceConfig, Boolean) -> Unit,
    onSaveSource: (DesktopSourceConfig) -> Unit,
    onRemoveSource: (DesktopSourceConfig) -> Unit,
    onMoveSource: (DesktopSourceConfig, Int) -> Unit,
    onTestSource: (DesktopSourceConfig) -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    var editingSource by remember { mutableStateOf<DesktopSourceConfig?>(null) }
    val sourceProbeKey = sourceConfigs
        .filter { it.kind.needsServer && it.isComplete }
        .joinToString { "${it.id}@${it.baseUrl}" }
    LaunchedEffect(sourceProbeKey) {
        sourceConfigs
            .filter { it.kind.needsServer && it.isComplete }
            .forEach(onTestSource)
    }
    DesktopSettingsPageFrame {
            Column(Modifier.fillMaxSize()) {
                val settingsQuery = query
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        DesktopStrings["settings", "Settings"],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    DesktopSearchField(
                        query = settingsQuery,
                        onQueryChange = onQueryChange,
                        onSearch = {},
                        placeholder = DesktopStrings["settings_search_hint", "Search settings"],
                        modifier = Modifier.padding(start = 24.dp).width(280.dp),
                    )
                }
                CompositionLocalProvider(LocalSettingsQuery provides settingsQuery.trim()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                ) {
            DesktopSettingsSection.entries.forEach { section ->
            if (section == DesktopSettingsSection.PLAYBACK) item {
                    SettingsGroup(DesktopStrings["playback", "Playback"]) {
                    if (!automix) {
                        if (settingsRowVisible(DesktopStrings["crossfade", "Crossfade"])) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(DesktopStrings["crossfade", "Crossfade"], style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s",
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            DesktopBareSlider(
                                value = crossfadeSeconds.toFloat(),
                                onValueChange = { onCrossfadeSecondsChange(it.toInt()) },
                                valueRange = 0f..12f,
                                steps = 11,
                            )
                        }
                        }
                    }
                    SettingsToggle(
                        DesktopStrings["automix", "Automix [BETA]"],
                        if (automix) {
                            DesktopStrings["d_automix_on", "Calculates timing and blends transitions from the tracks"]
                        } else {
                            DesktopStrings["d_automix_off", "Automatically calculates transition timing without a slider"]
                        },
                        automix,
                        onAutomixChange,
                    )
                    SettingsToggle(
                        DesktopStrings["automix_on_skip", "Automix on skip"],
                        DesktopStrings[
                            "automix_on_skip_subtitle",
                            "When you skip ahead, mix into the next song over ~15s instead of cutting",
                        ],
                        automixOnSkip,
                        onAutomixOnSkipChange,
                    )
                    if (automix) {
                        val phraseTitle = DesktopStrings["automix_phrase_length", "Automix phrase length"]
                        val phraseSubtitle =
                            DesktopStrings["automix_phrase_length_subtitle", "Blend over a fixed number of bars, beat-aligned"]
                        if (settingsRowVisible(phraseTitle, phraseSubtitle)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text(phraseTitle, fontWeight = FontWeight.Medium)
                                Text(
                                    phraseSubtitle,
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0, 8, 16).forEach { bars ->
                                        FilterChip(
                                            colors = desktopChipColors(),
                                            selected = automixPhraseBars == bars,
                                            onClick = { onAutomixPhraseBarsChange(bars) },
                                            label = { Text(phraseBarsLabel(bars)) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    phraseBarsDetail(automixPhraseBars),
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (settingsRowVisible(DesktopStrings["automix_performance", "Automix performance"], DesktopStrings["automix_performance_subtitle", "Sets how much CPU background analysis may use"])) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(DesktopStrings["automix_performance", "Automix performance"], fontWeight = FontWeight.Medium)
                            Text(
                                DesktopStrings["automix_performance_subtitle", "Sets how much CPU background analysis may use"],
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AutomixPerformanceMode.entries.forEach { mode ->
                                    FilterChip(
                                        colors = desktopChipColors(),
                                        selected = automixPerformance == mode,
                                        onClick = { onAutomixPerformanceChange(mode) },
                                        label = { Text(mode.label()) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                automixPerformance.detail(),
                                color = DesktopSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    val eqOn by DesktopEqualizerSettings.enabled.collectAsState()
                    SettingsToggle(
                        DesktopStrings["skip_silence", "Skip silence"],
                        DesktopStrings["d_shorten_long_gaps_rather_than_playing_them_out", "Shorten long gaps rather than playing them out"],
                        skipSilence,
                        onSkipSilenceChange,
                    )
                    SettingsToggle(
                        DesktopStrings["spatial_audio", "Spatial audio"],
                        DesktopStrings["spatial_audio_subtitle", "Widens stereo tracks for a more immersive feel"],
                        spatialAudio,
                        onSpatialAudioChange,
                    )
                    SettingsToggle(
                        DesktopStrings["dolby_atmos", "Dolby Atmos"],
                        if (DesktopCodecs.supportsDolbyAtmos) {
                            DesktopStrings["d_atmos_on", "Prefer an add-on's immersive mix and decode E-AC-3 with FFmpeg"]
                        } else {
                            DesktopStrings["dolby_atmos_unavailable", "This device can't play Atmos, so these songs play in their usual version"]
                        },
                        dolbyAtmos && DesktopCodecs.supportsDolbyAtmos,
                        { if (DesktopCodecs.supportsDolbyAtmos) onDolbyAtmosChange(it) },
                    )
                    SettingsNavigationRow(
                        title = DesktopStrings["equalizer", "Equalizer"],
                        subtitle = if (eqOn) {
                            DesktopStrings["equalizer_subtitle", "Tone, seven bands and balance"]
                        } else {
                            DesktopStrings["d_off", "Off"]
                        },
                        onClick = onOpenEqualizer,
                    )
                }
            }
            if (section == DesktopSettingsSection.DESKTOP_PLAYBACK) item {
                SettingsGroup(DesktopStrings["d_desktop_playback", "Desktop playback"]) {
                    SettingsToggle(
                        DesktopStrings["autoplay", "Autoplay"],
                        DesktopStrings["d_keep_the_music_going_with_similar_songs", "Keep the music going with similar songs"],
                        autoplay,
                        onAutoplayChange,
                    )
                    SettingsToggle(
                        DesktopStrings["shuffle", "Shuffle"],
                        DesktopStrings["d_mix_the_order_of_the_current_queue", "Mix the order of the current queue"],
                        shuffle,
                        onShuffleChange,
                    )
                    SettingsRow(
                        BitChordIcons.Repeat,
                        DesktopStrings["d_repeat", "Repeat"],
                        "${repeatMode.label()} · Tap to change",
                    ) { onRepeatModeChange(repeatMode.next()) }
                    SettingsRow(
                        Icons.Rounded.Tune,
                        DesktopStrings["d_playback_speed", "Playback speed"],
                        "${"%.2f".format(playbackSpeed)}×",
                    ) {
                        val next = when {
                            playbackSpeed < 0.76f -> 1.0f
                            playbackSpeed < 1.01f -> 1.25f
                            playbackSpeed < 1.26f -> 1.5f
                            playbackSpeed < 1.51f -> 2.0f
                            else -> 0.5f
                        }
                        onPlaybackSpeedChange(next)
                    }
                    SettingsRow(
                        Icons.Rounded.Bedtime,
                        DesktopStrings["d_sleep_timer", "Sleep timer"],
                        when {
                            sleepRemainingMs != null -> "${formatSleepTimer(sleepRemainingMs)} " + DesktopStrings["d_until_pause", "until playback pauses"]
                            sleepAfterTrack -> DesktopStrings["d_sleep_after_track", "Pausing when this song ends"]
                            else -> DesktopStrings["d_sleep_subtitle", "Pause playback after a while"]
                        },
                    ) { onSleepTimerCycle() }
                }
            }
            if (section == DesktopSettingsSection.LYRICS) item {
                SettingsGroup(DesktopStrings["open_lyrics", "Lyrics"]) {
                    SettingsToggle(
                        DesktopStrings["synced_lyrics", "Synced lyrics"],
                        DesktopStrings["synced_lyrics_subtitle", "Lights up the words on the player as they're sung"],
                        syncedLyrics,
                        onSyncedLyricsChange,
                    )
                    // Nothing to choose between while the feature is off, and the sources are
                    // third-party services reached on the listener's connection.
                    if (syncedLyrics) {
                        SettingsToggle(
                            DesktopStrings["d_blur_unfocused_lyrics", "Blur unfocused lyrics"],
                            DesktopStrings["d_keeps_the_spotlight_on_the_current_line", "Keeps the spotlight on the current line"],
                            lyricsBlur,
                            onLyricsBlurChange,
                        )
                        SettingsNavigationRow(
                            title = DesktopStrings["lyrics_sources", "Lyrics sources"],
                            subtitle = enabledLyricsSources
                                .joinToString(", ")
                                .ifEmpty { DesktopStrings["d_lyrics_none", "None. Lyrics will not be fetched."] },
                            onClick = onOpenLyricsSources,
                        )
                        val translationLanguage by DesktopTranslationSetting.language.collectAsState()
                        SettingsNavigationRow(
                            title = DesktopStrings["translation_language", "Translation language"],
                            subtitle = DesktopTranslationSetting.describe(translationLanguage),
                            onClick = onOpenTranslationLanguage,
                        )
                    }
                }
            }
            if (section == DesktopSettingsSection.LANGUAGE) item {
                SettingsGroup(DesktopStrings["language", "Language"]) {
                    var languageMenuOpen by remember { mutableStateOf(false) }
                    val chosen by DesktopStrings.language.collectAsState()
                    Box {
                        SettingsRow(
                            Icons.Rounded.Language,
                            DesktopStrings["app_language", "App language"],
                            DesktopStrings.languageLabel(),
                        ) { languageMenuOpen = true }
                        DropdownMenu(languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                            DesktopStrings.available.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.label) },
                                    trailingIcon = if (language.tag == chosen) {
                                        { Icon(BitChordIcons.Check, contentDescription = null) }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        DesktopStrings.setLanguage(language.tag)
                                        languageMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (section == DesktopSettingsSection.STORAGE) item {
                SettingsGroup(DesktopStrings["storage", "Storage"]) {
                    var limitMb by remember { mutableStateOf(DesktopMediaCache.limitMb()) }
                    var cleared by remember { mutableStateOf<String?>(null) }
                    if (settingsRowVisible(DesktopStrings["song_cache_limit", "Song cache limit"])) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(DesktopStrings["song_cache_limit", "Song cache limit"], style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (limitMb > DesktopMediaCache.WARNING_MB) {
                                DesktopStrings["d_cache_keeps_up_to", "Keeps up to"] + " ${formatCacheSize(limitMb)}. " +
                                    DesktopStrings["d_cache_warning", "This can take a noticeable share of your free space."]
                            } else {
                                DesktopStrings["d_cache_subtitle", "Keeps downloaded audio on disk for instant seeking and replays"]
                            },
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        DesktopBareSlider(
                            value = limitMb.toFloat(),
                            onValueChange = { limitMb = it.roundToInt() },
                            onValueChangeFinished = { DesktopMediaCache.setLimitMb(limitMb) },
                            valueRange = DesktopMediaCache.MIN_LIMIT_MB.toFloat()..
                                DesktopMediaCache.MAX_LIMIT_MB.toFloat(),
                            steps = 18,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(formatCacheSize(limitMb), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    }
                    SettingsRow(
                        Icons.Rounded.DeleteSweep,
                        DesktopStrings["d_clear_song_cache", "Clear song cache"],
                        cleared ?: DesktopStrings["d_clear_song_cache_sub", "Frees space used by downloaded audio"],
                    ) {
                        DesktopMediaCache.clear()
                        cleared = DesktopStrings["d_song_cache_cleared", "Song cache cleared"]
                    }
                    var imagesCleared by remember { mutableStateOf(false) }
                    SettingsRow(
                        Icons.Rounded.DeleteSweep,
                        DesktopStrings["d_clear_image_cache", "Clear image cache"],
                        if (imagesCleared) DesktopStrings["d_image_cache_cleared", "Image cache cleared"] else DesktopStrings["d_clear_image_cache_sub", "Frees space used by album artwork"],
                    ) {
                        DesktopArtworkCache.clear()
                        imagesCleared = true
                    }
                }
            }
            if (section == DesktopSettingsSection.LOCAL_MUSIC) item {
                SettingsGroup(DesktopStrings["local_music", "Local Music"]) {
                    var folderLabel by remember { mutableStateOf(DesktopLocalMusic.folderLabel()) }
                    SettingsRow(
                        Icons.Rounded.Folder,
                        DesktopStrings["d_local_music_folder", "Local music folder"],
                        folderLabel,
                    ) {
                        onChooseLocalMusicFolder { folderLabel = DesktopLocalMusic.folderLabel() }
                    }
                    if (DesktopLocalMusic.folder() != null) {
                        SettingsRow(
                            BitChordIcons.Library,
                            DesktopStrings["use_all_audio_folders", "Use all audio folders"],
                            DesktopStrings["use_all_audio_folders_subtitle", "Remove the folder limit and scan music across the device"],
                        ) {
                            DesktopLocalMusic.setFolder(null)
                            folderLabel = DesktopLocalMusic.folderLabel()
                            onLocalMusicFolderChanged()
                        }
                    }
                    SettingsToggle(
                        DesktopStrings["filter_non_music_audio", "Filter non-music audio"],
                        DesktopStrings[
                            "filter_non_music_audio_subtitle",
                            "Hides clips under 30 seconds, WAV files, voice notes, recordings and system sounds",
                        ],
                        filterNonMusicAudio,
                        onFilterNonMusicAudioChange,
                    )
                }
            }
            if (section == DesktopSettingsSection.APPEARANCE) item {
                SettingsGroup(DesktopStrings["d_customization", "Customization"]) {
                    val playerStyle by DesktopStyleSettings.playerStyle.collectAsState()
                    StyleChoiceRow(
                        DesktopStrings["d_player_style", "Player"],
                        DesktopStrings["d_player_style_subtitle", "The full-screen player that opens from the bar"],
                        listOf(
                            DesktopStrings["d_player_style_disc", "Disc"] to DesktopPlayerStyle.DISC,
                            DesktopStrings["d_player_style_classic", "Classic"] to DesktopPlayerStyle.CLASSIC,
                        ),
                        playerStyle,
                        DesktopStyleSettings::setPlayerStyle,
                    )
                    if (playerStyle == DesktopPlayerStyle.DISC) {
                        val showDisc by DesktopStyleSettings.showDisc.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_show_disc", "Spinning disc"],
                            DesktopStrings["d_show_disc_subtitle", "The CD beside the track. Off shows the artwork as a card instead"],
                            showDisc,
                            DesktopStyleSettings::setShowDisc,
                        )
                        val backdropText by DesktopStyleSettings.backdropText.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_backdrop_text", "Giant backdrop lettering"],
                            DesktopStrings["d_backdrop_text_subtitle", "The artist's name drifting huge and faint behind the player"],
                            backdropText,
                            DesktopStyleSettings::setBackdropText,
                        )
                        val visualizerStyle by DesktopStyleSettings.visualizerStyle.collectAsState()
                        StyleChoiceRow(
                            DesktopStrings["d_visualizer", "Visualiser"],
                            DesktopStrings["d_visualizer_subtitle", "What moves behind the track, following what you hear. Also cycled from the player"],
                            DesktopVisualizerStyle.entries.map { it.label() to it },
                            visualizerStyle,
                            DesktopStyleSettings::setVisualizerStyle,
                        )
                        val visualMode by DesktopStyleSettings.visualMode.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_play_music_videos", "Play music videos"],
                            DesktopStrings["d_play_music_videos_subtitle", "When a track is a video, show it in the player (muted, in sync). Off shows the artwork"],
                            visualMode == DesktopVisualMode.VIDEO,
                        ) { DesktopStyleSettings.setVisualMode(if (it) DesktopVisualMode.VIDEO else DesktopVisualMode.ARTWORK) }
                    }
                    val startVersion by DesktopStyleSettings.startVersion.collectAsState()
                    StyleChoiceRow(
                        DesktopStrings["d_start_version", "When a track starts"],
                        DesktopStrings["d_start_version_subtitle", "Which version plays when a track has both: the song, or its music video. Either can still be switched to in the player"],
                        listOf(
                            DesktopStrings["d_version_song", "Song"] to DesktopStartVersion.SONG,
                            DesktopStrings["d_version_video", "Video"] to DesktopStartVersion.VIDEO,
                        ),
                        startVersion,
                        DesktopStyleSettings::setStartVersion,
                    )
                    val placement by DesktopStyleSettings.nowPlayingPlacement.collectAsState()
                    StyleChoiceRow(
                        DesktopStrings["d_now_playing_placement", "Now playing in the main window"],
                        DesktopStrings["d_now_playing_placement_subtitle", "A glass bar along the bottom, with a seek bar — or the compact display in the top bar"],
                        listOf(
                            DesktopStrings["d_placement_bottom", "Bottom"] to DesktopNowPlayingPlacement.BOTTOM,
                            DesktopStrings["d_placement_top", "Top bar"] to DesktopNowPlayingPlacement.TOP,
                        ),
                        placement,
                        DesktopStyleSettings::setNowPlayingPlacement,
                    )
                    val liquidGlass by DesktopStyleSettings.liquidGlass.collectAsState()
                    SettingsToggle(
                        DesktopStrings["d_liquid_glass", "Liquid glass"],
                        DesktopStrings["d_liquid_glass_subtitle", "Floating bars bend and refract what is behind them, as on Android"],
                        liquidGlass,
                        DesktopStyleSettings::setLiquidGlass,
                    )
                    val barTinted by DesktopStyleSettings.barTinted.collectAsState()
                    SettingsToggle(
                        DesktopStrings["d_bar_tinted", "Player bar in the cover's colour"],
                        DesktopStrings["d_bar_tinted_subtitle", "The floating player bar, top or bottom, takes the playing cover's colour. Off, it is clear glass"],
                        barTinted,
                        DesktopStyleSettings::setBarTinted,
                    )
                    SettingsRow(
                        Icons.Rounded.Dashboard,
                        DesktopStrings["home_customize", "Customize Home"],
                        DesktopStrings["home_customize_subtitle", "Which shelves Home shows, in what order, and My Jam"],
                        onClick = onCustomizeHome,
                    )
                    val animatedHome by DesktopStyleSettings.animatedHome.collectAsState()
                    SettingsToggle(
                        DesktopStrings["d_animated_home", "Living main window"],
                        DesktopStrings["d_animated_home_subtitle", "Moving colour from what is playing, a greeting, and cards that lift and slide in"],
                        animatedHome,
                        DesktopStyleSettings::setAnimatedHome,
                    )
                }
                SettingsGroup(DesktopStrings["appearance", "Appearance"]) {
                    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
                    SettingsToggle(
                        DesktopStrings["reduce_dynamic_blur", "Reduce dynamic blur"],
                        DesktopStrings["reduce_dynamic_blur_subtitle", "Swaps frosted glass for solid fills across the app"],
                        reduceDynamicBlur,
                        DesktopAppearanceSettings::setReduceDynamicBlur,
                    )
                    // Window furniture belongs beside the visual settings it changes, rather than
                    // interrupting playback controls. These rows remain Windows-only.
                    if (DesktopPlatform.drawsOwnWindowFrame) {
                        val titleBar by DesktopTitleBarSetting.enabled.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_title_bar", "Title bar"],
                            DesktopStrings[
                                "d_a_slim_bar_above_the_toolbar",
                                "A slim bar above the toolbar with the window's own buttons. " +
                                    "Off, the window has no title bar at all.",
                            ],
                            titleBar,
                            DesktopTitleBarSetting::set,
                        )
                    }
                    if (DesktopWindowBackdrop.available) {
                        val backdropChoice by DesktopWindowBackdrop.selected.collectAsState()
                        val backdropActive by DesktopWindowBackdrop.active.collectAsState()
                        val materialTitle = DesktopStrings["d_window_material", "Window material"]
                        val materialSubtitle = DesktopStrings[
                            "d_window_material_subtitle",
                            "Sets the material for the sidebar and top bar",
                        ]
                        if (settingsRowVisible(materialTitle, materialSubtitle)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Text(materialTitle, fontWeight = FontWeight.Medium)
                                Text(materialSubtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DesktopBackdrop.entries.forEach { option ->
                                        FilterChip(
                                            colors = desktopChipColors(),
                                            selected = backdropChoice == option,
                                            onClick = { DesktopWindowBackdrop.set(option) },
                                            label = { Text(if (option == DesktopBackdrop.OFF) DesktopStrings["d_off", "Off"] else option.label) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    when {
                                        backdropChoice != DesktopBackdrop.OFF && backdropActive == DesktopBackdrop.OFF ->
                                            if (DesktopPlatform.isMac) "Native vibrancy could not be attached"
                                            else DesktopStrings["d_needs_win11", "Needs Windows 11 version 22H2 or later"]
                                        backdropChoice == DesktopBackdrop.MICA ->
                                            if (DesktopPlatform.isMac) "A subtle blur of your desktop wallpaper"
                                            else DesktopStrings["d_mica_desc", "A soft tint taken from your wallpaper"]
                                        backdropChoice == DesktopBackdrop.ACRYLIC ->
                                            DesktopStrings["d_acrylic_desc", "A frosted blur of whatever is behind the window"]
                                        else -> DesktopStrings["d_backdrop_off_desc", "Solid, as the rest of the app"]
                                    },
                                    color = DesktopSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        val appBackground by DesktopWindowBackdrop.appBackground.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_app_background", "App background"],
                            DesktopStrings[
                                "d_app_background_subtitle",
                                "Use the window material instead of a solid gray background",
                            ],
                            appBackground,
                            DesktopWindowBackdrop::setAppBackground,
                        )
                        val ambientBackdrop by DesktopAppearanceSettings.ambientBackdrop.collectAsState()
                        SettingsToggle(
                            DesktopStrings["d_ambient_backdrop", "Ambient artwork glow"],
                            DesktopStrings[
                                "d_ambient_backdrop_subtitle",
                                "Diffuse current song artwork colors under the window glass",
                            ],
                            ambientBackdrop,
                            DesktopAppearanceSettings::setAmbientBackdrop,
                        )
                    }
                    SettingsToggle(
                        DesktopStrings["full_screen_cover_art", "Full-screen cover art"],
                        DesktopStrings["full_screen_cover_art_subtitle", "Runs the cover to the edges of the player instead of a square sleeve"],
                        fullBleedArtwork,
                        onFullBleedArtworkChange,
                    )
                    SettingsToggle(
                        DesktopStrings["legacy_mesh_gradient", "Legacy mesh gradient"],
                        DesktopStrings["d_use_the_older_blob_backdrop_behind_the_player", "Use the older blob backdrop behind the player"],
                        legacyMeshGradient,
                        onLegacyMeshGradientChange,
                    )
                    SettingsToggle(
                        DesktopStrings["d_animated_canvas", "Animated canvas"],
                        DesktopStrings["d_show_motion_artwork_when_it_is_available", "Show motion artwork when it is available"],
                        animatedCanvas,
                        onAnimatedCanvasChange,
                    )
                    if (animatedCanvas) {
                        SettingsNavigationRow(
                            title = DesktopStrings["spotify_canvas_setup", "Spotify Canvas setup"],
                            subtitle = if (spotifyCanvasReady) {
                                DesktopStrings["d_connected", "Connected"]
                            } else {
                                DesktopStrings["d_canvas_setup_sub", "To use Spotify Canvas, provide your Spotify sp_dc cookie."]
                            },
                            onClick = onOpenSpotifyCanvasSetup,
                        )
                    }
                }
            }
            if (section == DesktopSettingsSection.MISCELLANEOUS) item {
                SettingsGroup(DesktopStrings["miscellaneous", "Miscellaneous"]) {
                    SettingsToggle(
                        DesktopStrings["d_dont_repeat_songs_in_current_session", "Don\u2019t repeat songs in current session"],
                        DesktopStrings["d_autoplay_wont_suggest_a_song_already_played_or_suggested", "AutoPlay won\u2019t suggest a song already played or suggested this session"],
                        dontRepeatSuggestions,
                        onDontRepeatSuggestionsChange,
                    )
                    SettingsToggle(
                        DesktopStrings["d_tray_icon", "Tray icon"],
                        DesktopStrings[
                            "d_show_bitchord_in_the_system_tray_with_playback_controls",
                            "Show BitChord in the system tray, with playback controls",
                        ],
                        trayIconEnabled,
                        onTrayIconChange,
                    )
                    if (trayIconEnabled) {
                        SettingsToggle(
                            DesktopStrings["d_keep_playing_when_closed", "Keep playing when closed"],
                            DesktopStrings[
                                "d_closing_the_window_leaves_bitchord_in_the_tray_instead_o",
                                "Closing the window leaves BitChord in the tray instead of quitting",
                            ],
                            closeToTray,
                            onCloseToTrayChange,
                        )
                    }
                    val hideVolumeBar by DesktopAppearanceSettings.hideVolumeBar.collectAsState()
                    SettingsToggle(
                        DesktopStrings["hide_volume_bar", "Hide volume bar"],
                        DesktopStrings["hide_volume_bar_subtitle", "Removes the volume slider from the main player"],
                        hideVolumeBar,
                        DesktopAppearanceSettings::setHideVolumeBar,
                    )
                }
            }
            if (section == DesktopSettingsSection.AUDIO_QUALITY) item {
                SettingsGroup(DesktopStrings["audio_quality", "Audio quality"]) {
                    if (settingsRowVisible(DesktopStrings["audio_quality", "Audio quality"])) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DesktopAudioQuality.entries.forEach { rung ->
                                FilterChip(
                                    colors = desktopChipColors(),
                                    selected = audioQuality == rung,
                                    onClick = { onAudioQualityChange(rung) },
                                    label = { Text(rung.label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // A rung is not a bitrate: it decides which kinds of source are allowed to
                        // answer, so say which.
                        Text(
                            audioQuality.detail,
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    }
                }
            }
            if (section == DesktopSettingsSection.LISTEN_TOGETHER) item {
                SettingsGroup(DesktopStrings["listen_together", "Listen together"]) {
                    SettingsNavigationRow(
                        DesktopStrings["listen_together", "Listen together"],
                        DesktopStrings["listen_together_create_subtitle", "Start a party and share the code."],
                    ) { onOpenListenTogether() }
                }
            }
            if (section == DesktopSettingsSection.AUDIO_OUTPUT) item {
                SettingsGroup(DesktopStrings["audio_output", "Audio output"]) {
                    SettingsNavigationRow(
                        DesktopStrings["pipeline_output_device", "Output device"],
                        DesktopAudioDevices.label(),
                    ) { onOpenAudioOutput() }
                }
            }
            if (section == DesktopSettingsSection.OUTPUT_PRECISION) item {
                SettingsGroup(DesktopStrings["d_output_precision", "Output precision"]) {
                    if (settingsRowVisible(DesktopStrings["d_output_precision", "Output precision"])) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PCM_16" to DesktopStrings["d_pcm16", "16-bit PCM"], "FLOAT_32" to DesktopStrings["d_float32", "32-bit float"]).forEach { (value, label) ->
                                FilterChip(
                                    colors = desktopChipColors(),
                                    selected = outputPrecision == value,
                                    onClick = { onOutputPrecisionChange(value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // What was asked for and what the device agreed to are not the same
                        // question, and a setting that silently did not take is worse than one that
                        // says so.
                        Text(
                            DesktopStrings["d_playing_at", "Playing at"] + " $outputSummary",
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    }
                }
            }
            if (section == DesktopSettingsSection.DOWNLOADS) item {
                SettingsGroup(DesktopStrings["download_channel_name", "Downloads"]) {
                    if (settingsRowVisible(DesktopStrings["download_channel_name", "Downloads"])) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("STANDARD" to DesktopStrings["d_quality_standard", "Standard"], "HIGH" to DesktopStrings["d_quality_high", "High"], "LOSSLESS" to DesktopStrings["d_quality_lossless", "Lossless"]).forEach { (value, label) ->
                            FilterChip(
                                colors = desktopChipColors(),
                                selected = downloadQuality == value,
                                onClick = { onDownloadQualityChange(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                    }
                }
            }
            if (section == DesktopSettingsSection.SOURCES) item {
                SettingsGroup(DesktopStrings["sources_order_header", "Sources · tried in this order"]) {
                    val sourcesHeading = DesktopStrings["sources_order_header", "Sources · tried in this order"]
                    val userAddedIds = sourceConfigs.inSourceOrder()
                        .filter(DesktopSourceConfig::isUserAdded)
                        .map(DesktopSourceConfig::id)
                    sourceConfigs.inSourceOrder().forEachIndexed { index, config ->
                        if (!settingsRowVisible(sourcesHeading, config.displayName)) return@forEachIndexed
                        DesktopSourceSettingsRow(
                            position = index + 1,
                            config = config,
                            status = sourceStatus[config.id],
                            skippedByQuality = config.enabled && !audioQuality.permits(config.kind),
                            onEdit = if (config.kind.needsServer) {
                                { editingSource = config }
                            } else {
                                null
                            },
                            onToggle = if (config.kind == DesktopSourceKind.YOUTUBE) {
                                null
                            } else {
                                { onSourceEnabledChange(config, it) }
                            },
                            onMoveUp = if (userAddedIds.indexOf(config.id) > 0) {
                                { onMoveSource(config, -1) }
                            } else null,
                            onMoveDown = if (userAddedIds.indexOf(config.id) in 0 until userAddedIds.lastIndex) {
                                { onMoveSource(config, 1) }
                            } else null,
                        )
                    }
                    // One entry point, and it creates an addon.
                    SettingsRow(
                        BitChordIcons.Plus,
                        DesktopStrings["d_add_source", "Add a source"],
                        DesktopSourceKind.ADDON.detail,
                    ) {
                        editingSource = DesktopSourceConfig(
                            id = UUID.randomUUID().toString(),
                            kind = DesktopSourceKind.ADDON,
                        )
                    }
                }
            }
            if (section == DesktopSettingsSection.ACCOUNT) item {
                // One row rather than the four groups this used to be. Every
                // integration option Android has lives behind it; inline, the
                // list was longer than the rest of Settings put together.
                SettingsGroup(DesktopStrings["d_account", "Account"]) {
                    SettingsRow(
                        Icons.Rounded.Share,
                        DesktopStrings["account_integrations", "Account & integrations"],
                        DesktopStrings["d_discord_rich_presence_last_fm_and_listenbrainz", "Discord rich presence, Last.fm and ListenBrainz"],
                        onOpenIntegrations,
                    )
                }
            }
            if (section == DesktopSettingsSection.ADVANCED) item {
                SettingsGroup(DesktopStrings["advanced_options", "Advanced options"]) {
                    val graphicsApi by DesktopGraphicsSettings.api.collectAsState()
                    if (DesktopPlatform.isWindows) StyleChoiceRow(
                        DesktopStrings["d_graphics_api", "Graphics"],
                        DesktopStrings["d_graphics_api_subtitle", "What the window is drawn through. Direct3D keeps frames on time best; OpenGL if Direct3D misbehaves on this computer. Applies after a restart"],
                        listOf(
                            "Direct3D" to DesktopGraphicsApi.DIRECT3D,
                            "OpenGL" to DesktopGraphicsApi.OPENGL,
                        ),
                        graphicsApi,
                        DesktopGraphicsSettings::setApi,
                    )
                    val bypass by DesktopNetworkSettings.bypass.collectAsState()
                    val normalize by DesktopLoudnessSettings.enabled.collectAsState()
                    SettingsToggle(
                        DesktopStrings["d_loudness", "Even out loudness"],
                        DesktopStrings["d_loudness_subtitle", "Brings every track to about the same volume, by YouTube's own measure of each: loud masters turned down, quiet ones up a little"],
                        normalize,
                        DesktopLoudnessSettings::setEnabled,
                    )
                    SettingsToggle(
                        DesktopStrings["d_dpi_bypass", "Get round YouTube blocking"],
                        DesktopStrings["d_dpi_bypass_subtitle", "Without a VPN, for networks that slow YouTube down (as in Russia): looks up addresses over encrypted DNS and splits the site's name in the first packet so the filter does not recognise it"],
                        bypass,
                        DesktopNetworkSettings::setBypass,
                    )
                    SettingsToggle(
                        DesktopStrings["show_nerd_stats", "Show stats for nerds"],
                        DesktopStrings["show_nerd_stats_subtitle", "Codec, bitrate and sample rate on the player"],
                        showNerdStats,
                        onShowNerdStatsChange,
                    )
                }
            }
            if (section == DesktopSettingsSection.UPDATES) item {
                SettingsGroup(DesktopStrings["d_updates", "Updates"]) {
                    DesktopUpdateSettingsRows()
                }
            }
            }
            item {
                DesktopSettingsFooter(onLicenses = onOpenLicenses)
            }
        }
        }
            }
    }

    editingSource?.let { config ->
        DesktopSourceEditorDialog(
            config = config,
            configuredSources = sourceConfigs,
            isNew = sourceConfigs.none { it.id == config.id },
            status = sourceStatus[config.id],
            onDismiss = { editingSource = null },
            onSave = {
                onSaveSource(it)
                editingSource = null
            },
            onRemove = {
                onRemoveSource(config)
                editingSource = null
            },
            onTest = onTestSource,
        )
    }
}

/** The line at the foot of the settings sheet, as Android has it. */
@Composable
private fun DesktopSettingsFooter(onLicenses: () -> Unit) {
    val version = remember { DesktopUpdateChecker.currentVersion }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = DesktopAccent, textDecoration = TextDecoration.Underline),
    )
    Text(
        text = buildAnnotatedString {
            append("BitChord $version  ")
            withLink(LinkAnnotation.Url("https://github.com/felixforestya-alt/BitChord2", linkStyles)) {
                append("GitHub")
            }
            append("  ")
            withLink(LinkAnnotation.Url("https://github.com/felixforestya-alt", linkStyles)) {
                append("Developer")
            }
            append("  ")
            withLink(LinkAnnotation.Url("https://discord.gg/pDdKfrdHY6", linkStyles)) {
                append("Discord")
            }
            append("  ")
            withLink(LinkAnnotation.Clickable("licenses", linkStyles) { onLicenses() }) {
                append("Licenses")
            }
            append("\n~YouTube Music Backend")
        },
        style = MaterialTheme.typography.labelSmall,
        color = DesktopSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
    )
}

/** Settings' word on updates: this version, a check on demand, and how new ones are taken. */
@Composable
private fun DesktopUpdateSettingsRows() {
    val scope = rememberCoroutineScope()
    val state by DesktopUpdateChecker.state.collectAsState()
    val autoUpdate by DesktopUpdateSettings.autoUpdate.collectAsState()
    var showOffer by remember { mutableStateOf(false) }
    val title = DesktopStrings["d_update_version", "Version %s"].format(DesktopUpdateChecker.currentVersion)
    val subtitle = when (val s = state) {
        DesktopUpdateChecker.UpdateState.Idle -> DesktopStrings["d_update_check_hint", "Look for a newer release on GitHub"]
        DesktopUpdateChecker.UpdateState.Checking -> DesktopStrings["d_update_checking", "Checking…"]
        DesktopUpdateChecker.UpdateState.UpToDate -> DesktopStrings["d_update_latest", "This is the latest version"]
        is DesktopUpdateChecker.UpdateState.Available -> DesktopStrings["d_update_new", "%s is out"].format(s.info.version)
        is DesktopUpdateChecker.UpdateState.Downloading -> DesktopStrings["d_update_downloading", "Downloading… %s"].format(sizeLine(s.downloadedBytes, s.totalBytes))
        is DesktopUpdateChecker.UpdateState.Preparing -> DesktopStrings["d_update_preparing", "Checking and unpacking…"]
        is DesktopUpdateChecker.UpdateState.Ready -> DesktopStrings["d_update_ready_short", "%s goes in when the app restarts"].format(s.info.version)
        is DesktopUpdateChecker.UpdateState.Error -> s.message
    }
    if (settingsRowVisible(title, subtitle)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is DesktopUpdateChecker.UpdateState.Error) MaterialTheme.colorScheme.error else DesktopSecondary,
                )
            }
            when (state) {
                is DesktopUpdateChecker.UpdateState.Available -> UpdateButton(DesktopStrings["d_update_install", "Update"]) { showOffer = true }
                is DesktopUpdateChecker.UpdateState.Ready -> UpdateButton(DesktopStrings["d_update_restart_now", "Restart now"]) { DesktopUpdateChecker.restartNow() }
                is DesktopUpdateChecker.UpdateState.Downloading, is DesktopUpdateChecker.UpdateState.Preparing, DesktopUpdateChecker.UpdateState.Checking ->
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else -> TextButton(onClick = {
                    scope.launch {
                        if (DesktopUpdateChecker.check(userAsked = true) != null) showOffer = true
                    }
                }) { Text(DesktopStrings["d_update_check", "Check for updates"], color = Color.White) }
            }
        }
        (state as? DesktopUpdateChecker.UpdateState.Downloading)?.let { downloading ->
            if (downloading.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { downloading.progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
    SettingsToggle(
        DesktopStrings["d_update_auto", "Update automatically"],
        if (DesktopUpdateChecker.canSelfUpdate) {
            DesktopStrings["d_update_auto_subtitle", "Download new versions in the background and install them when the app is closed. Off: offered at launch"]
        } else {
            DesktopStrings["d_update_auto_unavailable", "This copy cannot update itself (it is not the portable build, or its folder is not writable) — new versions are offered with a link"]
        },
        autoUpdate,
        DesktopUpdateSettings::setAutoUpdate,
    )
    if (showOffer) DesktopUpdateDialog(onDismiss = { showOffer = false })
}

/** Everything this build is assembled from, and the terms each part came under. A page of Settings. */
@Composable
private fun DesktopLicensesPage(onDismiss: () -> Unit) {
    DesktopSettingsPageFrame {
        Text(
            DesktopStrings["d_third_party_licenses", "Third-party licenses"],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        Text(
            DesktopStrings["d_bitchord_is_free_software_and_so_is_everything_it_is_bui", "BitChord is free software, and so is everything it is built on."],
            color = DesktopSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        LazyColumn(
            state = rememberPageListState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DesktopLicenses.groups.forEach { group ->
                item(key = group.title) {
                    SettingsGroup(group.title) {
                        group.entries.forEach { entry ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.name, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        entry.license,
                                        color = DesktopAccent,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        entry.note,
                                        color = DesktopSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    entry.url,
                                    color = DesktopSecondary.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopAccent, contentColor = Color.Black),
            ) { Text(DesktopStrings["done", "Done"]) }
        }
    }
}

@Composable
private fun DesktopSourceSettingsRow(
    position: Int,
    config: DesktopSourceConfig,
    status: String?,
    skippedByQuality: Boolean = false,
    onEdit: (() -> Unit)?,
    onToggle: ((Boolean) -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val enabledAlpha = if (config.enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = onEdit != null) { onEdit?.invoke() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            color = DesktopSecondary,
            modifier = Modifier.width(20.dp).alpha(enabledAlpha),
        )
        Icon(
            when (config.kind) {
                DesktopSourceKind.ADDON -> Icons.Rounded.Extension
                DesktopSourceKind.CUSTOM_MODULE, DesktopSourceKind.MODULE -> Icons.Rounded.Extension
                DesktopSourceKind.JIOSAAVN -> Icons.Rounded.GraphicEq
                DesktopSourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
            },
            contentDescription = null,
            tint = DesktopAccent,
            modifier = Modifier.size(21.dp).alpha(enabledAlpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(enabledAlpha)) {
            Text(config.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    // The ceiling in force, not the switch, is why this one is being passed over —
                    // worth saying, or the row reads as enabled and silently unused.
                    skippedByQuality -> "Skipped at this audio quality"
                    status != null -> status
                    !config.isComplete -> "Setup required"
                    config.kind.needsServer -> "Checking…"
                    else -> config.kind.detail
                } + if (!config.allowDownloads) {
                    " · " + DesktopStrings["source_downloads_off", "Streaming only"]
                } else {
                    ""
                },
                color = if (skippedByQuality) DesktopAccent else DesktopSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onMoveUp != null) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, "Move source earlier")
            }
        }
        if (onMoveDown != null) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Move source later")
            }
        }
        if (onToggle == null) {
            Text(DesktopStrings["always_on", "Always on"], color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Switch(checked = config.enabled, onCheckedChange = onToggle, colors = desktopSwitchColors())
        }
    }
}

@Composable
private fun DesktopSourceEditorDialog(
    config: DesktopSourceConfig,
    configuredSources: List<DesktopSourceConfig>,
    isNew: Boolean,
    status: String?,
    onDismiss: () -> Unit,
    onSave: (DesktopSourceConfig) -> Unit,
    onRemove: () -> Unit,
    onTest: (DesktopSourceConfig) -> Unit,
) {
    var label by remember(config.id) { mutableStateOf(config.label) }
    var baseUrl by remember(config.id) { mutableStateOf(config.baseUrl) }
    var checking by remember(config.id) { mutableStateOf(false) }
    var message by remember(config.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val candidate = config.copy(label = label.trim(), baseUrl = baseUrl.trim())

    /** Works out what is actually on the end of the URL before storing it. */
    fun identify(thenSave: Boolean) {
        if (candidate.baseUrl.isBlank() || checking) return
        checking = true
        message = null
        scope.launch {
            val outcome = DesktopSourceFormats.identify(candidate.baseUrl)
            checking = false
            outcome.fold(
                onSuccess = { detected ->
                    when (detected) {
                        is DesktopDetectedFormat.Addon -> {
                            val named = candidate.copy(
                                kind = DesktopSourceKind.ADDON,
                                baseUrl = detected.baseUrl,
                                label = candidate.label.ifBlank { detected.manifest.displayName },
                                allowDownloads = detected.manifest.downloadsAllowed,
                            )
                            val duplicate = configuredSources.duplicateOf(named.baseUrl, exceptId = named.id)
                            if (duplicate != null) {
                                message = "Already added as ${duplicate.displayName}"
                            } else {
                                message = "Addon · ${detected.manifest.displayName}"
                                if (thenSave) onSave(named)
                            }
                        }
                        is DesktopDetectedFormat.ModuleIndex -> {
                            val asIndex = candidate.copy(
                                kind = DesktopSourceKind.CUSTOM_MODULE,
                                baseUrl = detected.url.ifBlank { candidate.baseUrl },
                            )
                            message = "Module index · ${detected.moduleCount} modules"
                            if (thenSave) onSave(asIndex)
                        }
                        is DesktopDetectedFormat.Unsupported -> message = detected.reason
                    }
                },
                onFailure = { message = it.message ?: "Nothing answered at that address" },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a source" else config.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(DesktopStrings["d_addon_url", "Addon URL"]) },
                    placeholder = { Text("https://example.com/addon") },
                    singleLine = true,
                )
                Text(
                    DesktopStrings[
                        "d_an_addon_server",
                        "An addon server — BitChord asks it over plain HTTP and runs no code from it. " +
                            "A compatible module index also works and is stored as one.",
                    ],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(DesktopStrings["d_name_optional", "Name (optional)"]) },
                    placeholder = { Text(DesktopStrings["d_my_music_source", "My music source"]) },
                    singleLine = true,
                )
                (message ?: status)?.let {
                    Text(it, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onRemove) { Text(DesktopStrings["remove", "Remove"]) }
                TextButton(onClick = onDismiss) { Text(DesktopStrings["cancel", "Cancel"]) }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        onTest(candidate)
                        identify(thenSave = false)
                    },
                    enabled = candidate.baseUrl.isNotBlank() && !checking,
                ) {
                    Text(if (checking) "Checking…" else "Test")
                }
                TextButton(
                    onClick = { identify(thenSave = true) },
                    enabled = candidate.baseUrl.isNotBlank() && !checking,
                ) {
                    Text(DesktopStrings["save", "Save"])
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopCollectionPage(
    collection: DesktopCollection,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    /** The three edits only a playlist's owner is offered. */
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** Opens the drag-into-order sheet; only a playlist the account owns can be rearranged. */
    onReorder: (() -> Unit)?,
    /** Shuffles the playlist's own saved order; again only an owned playlist. */
    onShuffleOrder: (() -> Unit)?,
    /** Every track here into a playlist — another one, a new one, or one kept here. */
    onAddAllToPlaylist: (() -> Unit)?,
    /** Copies the release's own YouTube Music link; null for anything with no link to share. */
    onShare: (() -> Unit)?,
    onRemoveFromPlaylist: ((Song) -> Unit)?,
    likedIds: Set<String>,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onDownloadAll: (List<Song>) -> Unit,
    animatedCanvas: Boolean,
    onToggleLike: (Song) -> Unit,
    onDownload: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    downloadedIds: Set<String>,
    downloadInProgress: Set<String>,
    contentPadding: PaddingValues,
) {
    val songs = collection.songs
    // The phone's "search this list": a filter over the rows already loaded.
    var searching by remember(collection.browseId) { mutableStateOf(false) }
    var listQuery by remember(collection.browseId) { mutableStateOf("") }
    val shownSongs = remember(songs, listQuery) {
        val needle = listQuery.trim()
        songs.withIndex().filter { (_, song) ->
            needle.isBlank() || song.title.contains(needle, ignoreCase = true) ||
                song.artist.contains(needle, ignoreCase = true) ||
                song.albumName.orEmpty().contains(needle, ignoreCase = true)
        }
    }
    var moreOpen by remember(collection.browseId) { mutableStateOf(false) }
    val subtitleParts = collection.subtitle
        .split(" • ", " · ", " | ")
        .map(String::trim)
        .filter(String::isNotBlank)
    val credit = subtitleParts.firstOrNull {
        it.lowercase() !in setOf("album", "single", "ep", "playlist", "artist") &&
            !it.matches(Regex("\\d{4}")) &&
            !it.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?")) &&
            !it.matches(Regex("[0-9,.]+\\s*(?:songs?|tracks?)", RegexOption.IGNORE_CASE))
    }.orEmpty()
    val typeLabel = when (collection.type) {
        BrowseType.ALBUM -> "Album"
        BrowseType.PLAYLIST -> "Playlist"
        BrowseType.ARTIST -> "Artist"
        BrowseType.OTHER -> "Collection"
    }
    val metadata = buildList {
        add(typeLabel.uppercase())
        subtitleParts.firstOrNull { it.matches(Regex("\\d{4}")) }?.let(::add)
        if (songs.isNotEmpty()) add(trackCount(songs.size))
        val seconds = songs.sumOf { it.durationText.durationMillis() / 1_000L }
        if (seconds > 0) add(formatCollectionDuration(seconds))
    }.joinToString(" • ").uppercase()
    DesktopPageScaffold(contentPadding) {
        val isCurrentRow = rememberPlayingRowMatcher(songs)
        LazyColumn(
            state = rememberPageListState(),
            contentPadding = pagePadding(
                start = DesktopPageGutter,
                top = DesktopPageGutter,
                end = DesktopPageGutter,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The services hang motion artwork off the release, so a page for one asks for
                    // it directly rather than picking a track and hoping it sits on the right
                    // edition.
                    val albumCanvas by produceState<DesktopCanvasArtwork?>(null, collection.title, animatedCanvas) {
                        value = if (!animatedCanvas) {
                            null
                        } else {
                            withContext(Dispatchers.IO) {
                                DesktopCanvasClient.lookupAlbum(
                                    collection.title,
                                    songs.firstOrNull()?.artist.orEmpty(),
                                )
                            }
                        }
                    }
                    Box(Modifier.size(286.dp).clip(RoundedCornerShape(8.dp))) {
                        DesktopArtwork(
                            collection.thumbnailUrl ?: songs.firstOrNull()?.thumbnailUrl,
                            Modifier.fillMaxSize(),
                        )
                        albumCanvas?.let {
                            DesktopCanvasView(it.url, Modifier.fillMaxSize(), fallbackUrl = it.fallbackUrl)
                        }
                    }
                    Spacer(Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            typeLabel.uppercase(),
                            color = DesktopSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            collection.title,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (credit.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                credit,
                                color = DesktopAccent,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (metadata.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(metadata, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(24.dp))
                        // The phone's release controls, in its order: Shuffle, Play, search this
                        // list, and everything else behind the overflow — downloading a release is
                        // done once, finding a track on it is done while reading the page.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DesktopCircleButton(BitChordIcons.Shuffle, DesktopStrings["shuffle", "Shuffle"], onClick = { onShuffle(songs) })
                            DesktopPlayPill(
                                label = DesktopStrings["play", "Play"],
                                onClick = { if (songs.isNotEmpty()) onPlaySongs(songs, 0) },
                                iconOnly = true,
                            )
                            DesktopCircleButton(
                                if (searching) Icons.Rounded.Close else BitChordIcons.Search,
                                if (searching) DesktopStrings["close_search", "Close search"] else DesktopStrings["search_this_list", "Search this list"],
                                onClick = {
                                    searching = !searching
                                    if (!searching) listQuery = ""
                                },
                            )
                            Box {
                                DesktopCircleButton(Icons.Rounded.MoreHoriz, DesktopStrings["more", "More"], onClick = { moreOpen = true })
                                // The song menu's own panel and rows, so the two menus look like one app.
                                if (moreOpen) DesktopMenuSurface(onDismiss = { moreOpen = false }) {
                                    fun pick(action: () -> Unit) {
                                        moreOpen = false
                                        action()
                                    }
                                    DesktopMenuRow(BitChordIcons.Download, DesktopStrings["download_all", "Download all"]) { pick { onDownloadAll(songs) } }
                                    onShare?.let { share ->
                                        DesktopMenuRow(Icons.Rounded.Share, DesktopStrings["share", "Share"]) { pick(share) }
                                    }
                                    onAddAllToPlaylist?.let { addAll ->
                                        DesktopMenuRow(BitChordIcons.Plus, DesktopStrings["d_add_all_to_playlist", "Add all to playlist…"]) { pick(addAll) }
                                    }
                                    if (onRename != null || onReorder != null || onShuffleOrder != null || onDelete != null) DesktopMenuDivider()
                                    onRename?.let { rename ->
                                        DesktopMenuRow(Icons.Rounded.Edit, DesktopStrings["rename", "Rename"]) { pick(rename) }
                                    }
                                    onReorder?.let { reorder ->
                                        DesktopMenuRow(Icons.Rounded.DragHandle, DesktopStrings["reorder_playlist", "Edit order"]) { pick(reorder) }
                                    }
                                    onShuffleOrder?.let { shuffleOrder ->
                                        DesktopMenuRow(BitChordIcons.Shuffle, DesktopStrings["d_shuffle_order", "Shuffle order"]) { pick(shuffleOrder) }
                                    }
                                    onDelete?.let { delete ->
                                        DesktopMenuDivider()
                                        DesktopMenuRow(
                                            Icons.Rounded.Delete,
                                            DesktopStrings["delete", "Delete"],
                                            tint = MaterialTheme.colorScheme.error,
                                        ) { pick(delete) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (searching) {
                item(key = "collection-search") {
                    DesktopSearchField(
                        query = listQuery,
                        onQueryChange = { listQuery = it },
                        onSearch = {},
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        placeholder = DesktopStrings["search_this_list", "Search this list"],
                    )
                }
            }
            itemsIndexed(shownSongs, key = { _, (index, song) -> "${song.videoId}-$index" }) { position, (index, song) ->
                DesktopCollectionSongRow(
                    // Striped by place on screen, not track number, so a filtered list still
                    // alternates.
                    striped = position % 2 == 1,
                    song = song,
                    isCurrent = isCurrentRow(song),
                    collectionType = collection.type,
                    liked = song.videoId in likedIds,
                    onClick = { _ -> onPlaySongs(songs, index) },
                    onToggleLike = onToggleLike,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    downloaded = song.videoId in downloadedIds,
                    downloadInProgress = song.videoId in downloadInProgress,
                    number = index + 1,
                    onRemove = onRemoveFromPlaylist?.takeIf { song.setVideoId != null },
                )
            }
            if (collection.continuation != null) {
                // Reaching this row is what asks for the next page, the way the home feed pages.
                item(key = "collection-more") {
                    LaunchedEffect(songs.size) { onLoadMore() }
                    Box(
                        Modifier.fillMaxWidth().height(72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                color = DesktopAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The width of a collection row's action cluster, fixed so the duration column lines up down the
 * table whichever rows can be removed.
 */
private fun collectionActionsWidth(hasRemove: Boolean): Dp =
    if (hasRemove) 136.dp else 102.dp

@Composable
private fun DesktopCollectionSongRow(
    song: Song,
    /** From the list's [rememberPlayingRowMatcher]; null falls back to the name rule. */
    isCurrent: Boolean? = null,
    collectionType: BrowseType,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)?,
    onAddToPlaylist: ((Song) -> Unit)?,
    downloaded: Boolean,
    downloadInProgress: Boolean,
    number: Int,
    /** Every other row is shaded, so a wide row can be followed across to its buttons. */
    striped: Boolean,
    /** Takes this row out of the playlist being read. */
    onRemove: ((Song) -> Unit)? = null,
) {
    // A Spotify track still being matched to YouTube Music, or with no match: listed, but there is
    // nothing yet to play, like, save or remove.
    val unresolved = song.isUnresolvedSpotify
    val openContextMenu = LocalDesktopContextMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (song.isMatchMissing) 0.45f else 1f)
            .contextClick(if (unresolved) null else ({ openContextMenu(song) }))
            .clip(RoundedCornerShape(6.dp))
            .background(if (striped) Color.White.copy(alpha = 0.04f) else Color.Transparent)
            .desktopHoverFill(corner = 6.dp)
            .clickable(enabled = !unresolved) { onClick(song) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val nowPlaying = isCurrent ?: song.isNowPlaying()
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (song.isMatchPending) {
                CircularProgressIndicator(color = DesktopSecondary, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
            } else {
                Text(
                    number.toString(),
                    color = DesktopSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // No sleeve on an album's rows.
        if (collectionType != BrowseType.ALBUM) {
            DesktopArtwork(song.thumbnailUrl, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)), px = ROW_ART_PX)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            song.title,
            Modifier.weight(0.42f),
            color = if (nowPlaying) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        Text(
            song.artist,
            Modifier.weight(0.20f).padding(horizontal = 8.dp),
            color = DesktopSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Every row of an album is on that album; the column would only repeat the page's title.
        if (collectionType != BrowseType.ALBUM) {
            Text(
                song.albumName ?: "—",
                Modifier.weight(0.25f).padding(horizontal = 8.dp),
                color = DesktopSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            song.durationText ?: "—",
            Modifier.width(58.dp),
            color = DesktopSecondary,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.width(collectionActionsWidth(onRemove != null)),
            horizontalArrangement = Arrangement.End,
        ) {
            if (unresolved) return@Row
            onRemove?.let { remove ->
                IconButton(onClick = { remove(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        DesktopStrings["d_remove_from_playlist", "Remove from playlist"],
                        tint = DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onToggleLike?.let { onLike ->
                IconButton(onClick = { onLike(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        "Favorite",
                        tint = if (liked) DesktopAccent else DesktopSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            onDownload?.let { onSave ->
                IconButton(onClick = { if (!downloaded) onSave(song) }, modifier = Modifier.size(34.dp)) {
                    if (downloadInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = DesktopAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (downloaded) BitChordIcons.Download else BitChordIcons.Download,
                            if (downloaded) "Downloaded" else "Download",
                            tint = if (downloaded) DesktopAccent else DesktopSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            onAddToPlaylist?.let { onAdd ->
                IconButton(onClick = { onAdd(song) }, modifier = Modifier.size(34.dp)) {
                    Icon(BitChordIcons.Plus, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}


/** The track playing, for rows that want to say so. */
private val LocalNowPlaying = compositionLocalOf<Song?> { null }

/**
 * Opens the app's song menu where the pointer is, for the desktop's own rows.
 *
 * The pages the phone shares already hang right-click off this via their long-press call; a
 * desktop-only row has no such hook, so it reads this and calls it from a [contextClick]. The
 * pages that hold it provide it from the pointer host that plants the menu under the cursor.
 */
internal val LocalDesktopContextMenu = compositionLocalOf<(Song) -> Unit> { {} }
/** Whether this row is the track being played, for the now-playing highlight. */
@Composable
private fun Song.isNowPlaying(): Boolean = isSameTrackAs(LocalNowPlaying.current)

/** Which of [rows] is the track being played: the one that was played, not every namesake. */
@Composable
private fun rememberPlayingRowMatcher(rows: List<Song>): (Song) -> Boolean {
    val current = LocalNowPlaying.current
    return remember(rows, current) { playingRowMatcher(rows, current) }
}

/** The frosted pane the floating bars are made of. */
@Composable
internal fun Modifier.desktopBarGlass(shape: Shape): Modifier =
    desktopFrosted(DesktopBarGlass, tintAlpha = 0.55f).border(0.5.dp, DesktopBarEdge, shape)

/**
 * The floating pills — the compact window's tab bar and mini player — in liquid glass, the way the
 * Android build draws its bars, falling back to [desktopBarGlass] where the lens is unavailable.
 */
@Composable
internal fun Modifier.desktopFloatingGlass(shape: Shape): Modifier =
    desktopLiquidGlass(cornerRadius = 999.dp, tint = DesktopSurface.copy(alpha = 0.22f), blurRadius = 6.dp) { desktopBarGlass(shape) }

/** The edge of a chrome pane that meets the page, and so has to dissolve into it. */
internal enum class DesktopChromeEdge { NONE, BOTTOM, END }

/**
 * The same pane for the window's own chrome — square-edged, and a shade denser, because the sidebar
 * and top bar carry far more text than a floating bar does.
 *
 * [edge] names the side the page is on. The pane's tint is dissolved across the last [fade] of it so
 * the two meet in a gradient rather than on a line.
 */
@Composable
internal fun Modifier.desktopChromeGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier = desktopFrosted(
    opaque = DesktopSurface.copy(alpha = 0.88f),
    tintAlpha = 0.46f,
    edge = edge,
    fade = fade,
)

/**
 * The window's own chrome — title bar, top bar, sidebar. Over Windows 11's Mica or Acrylic
 * ([DesktopWindowBackdrop]) it is a light dark tint and nothing else, so DWM's material shows
 * through; the in-app blur would paint the page's backdrop over it. Otherwise the in-app glass.
 *
 * No dissolve over the material: it would fade the tint out into bare material right where the
 * opaque page begins, a lighter stripe along the seam rather than a softer one.
 */
@Composable
internal fun Modifier.desktopWindowGlass(
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0.2f,
): Modifier {
    val backdrop by DesktopWindowBackdrop.active.collectAsState()
    return if (backdrop == DesktopBackdrop.OFF) {
        desktopChromeGlass(edge, fade)
    } else {
        val base = background(Color.Black.copy(alpha = WINDOW_GLASS_TINT))
        if (DesktopPlatform.isMac && edge == DesktopChromeEdge.BOTTOM) {
            base.drawBehind {
                // Subtle liquid glass specular highlight along the top edge
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
        } else {
            base
        }
    }
}

/** Enough to keep white text readable over a bright wallpaper, little enough to let it through. */
private const val WINDOW_GLASS_TINT = 0.28f

/**
 * The separators in the window's chrome. Acrylic lets the wallpaper through bright, and the solid
 * dark divider cut across it as a black line; a faint white one reads as an edge in the glass
 * instead. Mica is dark enough for the usual one.
 */
@Composable
internal fun desktopChromeDivider(): Color {
    val backdrop by DesktopWindowBackdrop.active.collectAsState()
    return when {
        backdrop == DesktopBackdrop.ACRYLIC -> Color.White.copy(alpha = 0.14f)
        DesktopPlatform.isMac && backdrop != DesktopBackdrop.OFF -> Color.White.copy(alpha = 0.10f)
        else -> DesktopDivider
    }
}

@Composable
private fun Modifier.desktopFrosted(
    opaque: Color,
    tintAlpha: Float,
    edge: DesktopChromeEdge = DesktopChromeEdge.NONE,
    fade: Float = 0f,
): Modifier {
    val haze = LocalDesktopHaze.current
    val reduceDynamicBlur by DesktopAppearanceSettings.reduceDynamicBlur.collectAsState()
    if (haze == null || reduceDynamicBlur) {
        return this.background(edge.dissolve(opaque, fade) ?: SolidColor(opaque))
    }
    val dissolve = edge.dissolve(Color.Black, fade)
    return this.hazeEffect(state = haze) {
        // Spelled out rather than taken from a preset.
        blurEnabled = true
        backgroundColor = DesktopSurface
        blurRadius = 30.dp
        // Blurred from a copy a third the size, as the phone's panes are: at 30 dp nothing of the
        // detail lost shows, and blurring the page under the pane at the window's full 4K, every
        // frame it scrolled, took a frame past its refresh.
        inputScale = dev.chrisbanes.haze.HazeInputScale.Fixed(0.33f)
        // Dark enough that white text on the pane always wins, translucent enough that the
        // colour of what is behind it still comes through.
        tints = listOf(HazeTint(DesktopSurface.copy(alpha = tintAlpha)))
        noiseFactor = 0.04f
        // Assigned either way: the scope belongs to the node and outlives the draw, so a pane that
        // stops dissolving — the leading stretch of the top bar, when the window grows back out of
        // compact — would otherwise keep the mask it was given in the other layout.
        mask = dissolve
    }
}

/** The gradient that takes [colour] to nothing across the pane's last [fade]. */
private fun DesktopChromeEdge.dissolve(colour: Color, fade: Float): Brush? {
    if (this == DesktopChromeEdge.NONE || fade <= 0f) return null
    val stops = arrayOf(
        0f to colour,
        (1f - fade).coerceIn(0f, 1f) to colour,
        1f to Color.Transparent,
    )
    return if (this == DesktopChromeEdge.BOTTOM) {
        Brush.verticalGradient(*stops)
    } else {
        Brush.horizontalGradient(*stops)
    }
}

private fun AutomixPerformanceMode.label(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT -> DesktopStrings["d_automix_efficient", "Efficient"]
    AutomixPerformanceMode.BALANCED -> DesktopStrings["d_automix_balanced", "Balanced"]
    AutomixPerformanceMode.PERFORMANCE -> DesktopStrings["d_automix_performance_mode", "Performance"]
}

/** Android's description of each rung. */
private fun AutomixPerformanceMode.detail(): String = when (this) {
    AutomixPerformanceMode.EFFICIENT ->
        DesktopStrings["d_automix_efficient_detail", "1 thread · lowest heat and battery use · analysis may take longer"]
    AutomixPerformanceMode.BALANCED ->
        DesktopStrings["d_automix_balanced_detail", "2 threads · recommended balance of speed, heat and battery"]
    AutomixPerformanceMode.PERFORMANCE ->
        DesktopStrings["d_automix_performance_detail", "4 threads · fastest analysis · higher heat and battery use"]
}

/** Chip label for an Apple-style Automix phrase length, in bars; 0 is the adaptive default. */
private fun phraseBarsLabel(bars: Int): String = when (bars) {
    8 -> DesktopStrings["automix_phrase_8", "8 bars"]
    16 -> DesktopStrings["automix_phrase_16", "16 bars"]
    else -> DesktopStrings["automix_phrase_auto", "Auto"]
}

/** What the selected phrase length does, shown under the chips. */
private fun phraseBarsDetail(bars: Int): String = when (bars) {
    8 -> DesktopStrings[
        "automix_phrase_8_detail",
        "Mixes over exactly 8 bars, both edges on the beat grid",
    ]
    16 -> DesktopStrings[
        "automix_phrase_16_detail",
        "Mixes over exactly 16 bars, both edges on the beat grid",
    ]
    else -> DesktopStrings[
        "automix_phrase_auto_detail",
        "Chooses each transition's length from the two tracks",
    ]
}

/** Android's wording for the Automix line, so both players read alike. */
private fun TrackAnalysisState.label(): String = when (this) {
    TrackAnalysisState.ANALYSED -> "analysed"
    TrackAnalysisState.REFINING -> "analysed, refining…"
    TrackAnalysisState.ANALYSING -> "analysing…"
    TrackAnalysisState.WAITING -> "waiting"
    TrackAnalysisState.FAILED -> "failed"
}

@Composable
private fun DesktopShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem, String?) -> Unit,
    gutter: Dp = DesktopPageGutter,
) {
    Column {
        SectionTitle(shelf.title, shelf.subtitle, gutter)
        DesktopScrollableRow(gutter = gutter) {
            items(shelf.items, key = { it.videoId ?: it.browseId ?: it.title }) { item ->
                DesktopShelfCard(item) { onItemClick(it, shelf.title) }
            }
        }
    }
}

/**
 * A sideways row with the affordances a desktop needs to move it.
 *
 * A carousel longer than the window has no way to be scrolled with a mouse — the wheel belongs to
 * the page underneath — so this adds the three that work: dragging it, the pointer's horizontal
 * ticks while it is hovered, and a paging arrow at whichever end still has more.
 *
 * Shared rather than repeated: every shelf-shaped row in the app wants the same three.
 */
@Composable
internal fun DesktopScrollableRow(
    gutter: Dp = DesktopPageGutter,
    spacing: Dp = 14.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val rowState = com.music.bitchord.ui.components.rememberShelfRowState()
    DesktopShelfRowChrome.Wrap(rowState) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * What every sideways shelf is wrapped in on the desktop — the shared pages' rows too, through
 * [LocalShelfRowChrome]. A mouse cannot swipe a row, so it gets the arrows it pages with, a drag
 * to pull it by, and the horizontal wheel while the pointer is over it.
 */
internal object DesktopShelfRowChrome : ShelfRowChrome {
    @Composable
    override fun Wrap(state: LazyListState, row: @Composable () -> Unit) {
        val scope = rememberCoroutineScope()
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()

        /** One screenful, less a sliver so the card at the edge stays in view. */
        fun page(forward: Boolean) {
            val viewport = state.layoutInfo.let { it.viewportEndOffset - it.viewportStartOffset }
            val distance = (viewport - SHELF_PAGE_OVERLAP_PX).coerceAtLeast(SHELF_PAGE_OVERLAP_PX)
            scope.launch { state.animateScrollBy(if (forward) distance.toFloat() else -distance.toFloat()) }
        }

        // Whichever row the pointer is in takes the horizontal ticks; the page underneath keeps the
        // wheel, as it does everywhere else.
        LaunchedEffect(hovered) {
            if (!hovered) return@LaunchedEffect
            DesktopHorizontalScroll.ticks.collect { tick -> state.scrollBy(tick * SHELF_WHEEL_STEP) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .hoverable(interaction)
                // A row can be taken hold of and pulled, which is the first thing anyone tries.
                .draggable(
                    state = rememberDraggableState { delta -> scope.launch { state.scrollBy(-delta) } },
                    orientation = Orientation.Horizontal,
                ),
        ) {
            row()
            DesktopShelfArrow(
                alignment = Alignment.CenterStart,
                icon = Icons.Rounded.ChevronLeft,
                description = DesktopStrings["d_scroll_left", "Scroll left"],
                visible = hovered && state.canScrollBackward,
                onClick = { page(forward = false) },
            )
            DesktopShelfArrow(
                alignment = Alignment.CenterEnd,
                icon = BitChordIcons.ChevronRight,
                description = DesktopStrings["d_scroll_right", "Scroll right"],
                visible = hovered && state.canScrollForward,
                onClick = { page(forward = true) },
            )
        }
    }
}

/** How much of the outgoing card stays visible after a paged scroll. */
internal const val SHELF_PAGE_OVERLAP_PX = 120

/** How far one notch of a horizontal scroll moves a shelf. */
internal const val SHELF_WHEEL_STEP = 90f

@Composable
internal fun BoxScope.DesktopShelfArrow(
    alignment: Alignment,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(alignment).padding(horizontal = 6.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DesktopShelfCard(item: ShelfItem, onClick: (ShelfItem) -> Unit) {
    val width = 158.dp
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1f else 0f, tween(140), label = "shelfLift")
    Column(
        Modifier
            .width(width)
            // The whole card answers the pointer, artwork and captions together, so the two do not
            // move independently of each other. No stock highlight: it is a square over the
            // whole card, cover and captions alike, beside a rounded cover. The pointer is
            // answered by the cover alone, in its own shape — a lift, a shadow and a faint wash.
            .hoverable(interaction)
            .graphicsLayer {
                val grown = 1f + 0.035f * lift
                scaleX = grown
                scaleY = grown
            }
            .clickable(interactionSource = interaction, indication = null) { onClick(item) }
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        Box(
            Modifier
                .size(width)
                .shadow(18.dp * lift, shape)
                .clip(shape)
                .background(DesktopGlass)
                .border(1.dp, Color.White.copy(alpha = 0.12f + 0.12f * lift), shape),
        ) {
            DesktopArtwork(item.thumbnailUrl, Modifier.fillMaxSize())
            if (lift > 0.01f) Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.08f * lift)))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DesktopSongRow(
    song: Song,
    /** From the list's [rememberPlayingRowMatcher]; null falls back to the name rule. */
    isCurrent: Boolean? = null,
    liked: Boolean,
    onClick: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)?,
    onDownload: ((Song) -> Unit)? = null,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    downloaded: Boolean = false,
    downloadInProgress: Boolean = false,
    number: Int? = null,
    /** The row's own "…", when the page has a menu to give it. */
    menu: (@Composable (Song) -> Unit)? = null,
) {
    val openContextMenu = LocalDesktopContextMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .contextClick { openContextMenu(song) }
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopGlass.copy(alpha = 0.38f))
            .desktopHoverFill(corner = 10.dp)
            .clickable { onClick(song) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                "$number",
                Modifier.width(28.dp),
                color = DesktopSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(12.dp))
        }
        DesktopArtwork(
            song.thumbnailUrl,
            Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            px = ROW_ART_PX,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent ?: song.isNowPlaying()) Color.White else Color.White.copy(alpha = 0.75f),
            )
            Text(listOfNotNull(song.artist.takeIf(String::isNotBlank), song.durationText).joinToString(" · "), color = DesktopSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        song.sourceQualityBadge()?.let { quality ->
            SourceQualityBadge(
                quality = quality,
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (onToggleLike != null) {
            IconButton(onClick = { onToggleLike(song) }) { Icon(if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart, "Favorite", tint = if (liked) DesktopAccent else DesktopSecondary) }
        }
        if (onDownload != null) {
            if (downloadInProgress) {
                IconButton(onClick = { onDownload(song) }) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = DesktopAccent,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                IconButton(onClick = { if (!downloaded) onDownload(song) }) {
                    Icon(
                        imageVector = if (downloaded) BitChordIcons.Download else BitChordIcons.Download,
                        contentDescription = if (downloaded) "Downloaded" else "Download",
                        tint = if (downloaded) DesktopAccent else DesktopSecondary,
                    )
                }
            }
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = { onAddToPlaylist(song) }) {
                Icon(BitChordIcons.Plus, DesktopStrings["add_to_playlist", "Add to playlist"], tint = DesktopSecondary)
            }
        }
        // The same menu every other surface opens, so a row on a list page offers what a row on
        // the player does; the row itself plays.
        menu?.invoke(song)
    }
}

/** A page's box, inset from the shell's chrome. */
@Composable
private fun DesktopPageScaffold(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    val direction = LocalLayoutDirection.current
    Box(
        Modifier.fillMaxSize().padding(
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
            top = contentPadding.calculateTopPadding(),
        ),
    ) {
        CompositionLocalProvider(
            LocalDesktopBottomInset provides contentPadding.calculateBottomPadding(),
        ) {
            content()
        }
    }
}

/** A scrollable's own padding, plus room to clear whatever floats over the page. */
@Composable
private fun pagePadding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = start,
    top = top,
    end = end,
    bottom = bottom + LocalDesktopBottomInset.current,
)

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String = "",
    gutter: Dp = DesktopPageGutter,
    /** An action belonging to this heading, drawn at the far end of it. */
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}

@Composable
private fun DesktopLoadingPage(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = DesktopAccent)
            Spacer(Modifier.height(14.dp))
            Text(label, color = DesktopSecondary)
        }
    }
}

@Composable
private fun DesktopErrorPage(message: String, onRetry: () -> Unit) {
    DesktopEmptyPage(Icons.Rounded.Tune, "Something went wrong", message, onRetry, "Try again")
}

@Composable
private fun DesktopEmptyPage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, onAction: (() -> Unit)? = null, actionLabel: String? = null) {
    Column(Modifier.fillMaxWidth().padding(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = DesktopSecondary, textAlign = TextAlign.Center)
        if (onAction != null && actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** A titled block of settings rows. */
/** A setting with a handful of mutually exclusive choices, as chips. */
@Composable
private fun <T> StyleChoiceRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    if (!settingsRowVisible(title, subtitle)) return
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (label, value) ->
                FilterChip(
                    colors = desktopChipColors(),
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val query = LocalSettingsQuery.current
    // A group whose own name matches shows whole; otherwise only its matching rows do, and the
    // heading goes with them when none are left.
    val wholeGroup = query.isBlank() || title.contains(query, ignoreCase = true)
    val matched = remember(query) { androidx.compose.runtime.mutableStateListOf<String>() }
    val visible = wholeGroup || matched.isNotEmpty()
    // The gap belongs to the group, not to the list: as list spacing, every filtered-out group
    // still left its 18dp behind and the surviving ones sat under a band of empty space.
    Column(if (visible) Modifier.padding(bottom = 20.dp) else Modifier) {
        if (visible) {
            Text(
                title.uppercase(),
                color = DesktopSecondary,
                style = MaterialTheme.typography.labelSmall,
                // In line with the rows' own text, now that no card sets them in.
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        Column {
            CompositionLocalProvider(
                LocalSettingsQuery provides if (wholeGroup) "" else query,
                LocalSettingsMatches provides matched,
            ) {
                content()
            }
        }
    }
}

/** What the settings field is filtering on, or blank when it is empty. */
private val LocalSettingsQuery = compositionLocalOf { "" }

/** Where a row tells its group that it survived the filter, so an empty group can hide its name. */
private val LocalSettingsMatches = compositionLocalOf<MutableList<String>?> { null }

/**
 * Whether a row with this text belongs on screen, and registering it with its group when it does.
 * Rows call this first and return early when it is false.
 */
@Composable
private fun settingsRowVisible(title: String, subtitle: String = ""): Boolean {
    val query = LocalSettingsQuery.current
    if (query.isBlank()) return true
    val hit = title.contains(query, ignoreCase = true) || subtitle.contains(query, ignoreCase = true)
    val matches = LocalSettingsMatches.current
    DisposableEffect(hit, title, matches) {
        if (hit) matches?.add(title)
        onDispose { if (hit) matches?.remove(title) }
    }
    return hit
}

/** A settings row that opens something rather than toggling it. */
@Composable
private fun SettingsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    if (!settingsRowVisible(title, subtitle)) return
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = DesktopSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            BitChordIcons.ChevronRight,
            null,
            tint = DesktopSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A settings row whose control is a slider, with its value read out beside the title. */
/** Material's slider with the step ticks and end-stop dot taken off, as Android draws them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopBareSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.24f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                drawStopIndicator = null,
                drawTick = { _, _ -> },
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    if (!settingsRowVisible(title, subtitle)) return
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable { onCheckedChange(!checked) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        Spacer(Modifier.width(18.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = desktopSwitchColors())
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    if (!settingsRowVisible(title, subtitle)) return
    Row(
        Modifier
            .fillMaxWidth()
            .desktopRowClickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = DesktopAccent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = DesktopSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        if (onClick != null) {
            Spacer(Modifier.width(10.dp))
            Icon(BitChordIcons.ChevronRight, null, tint = DesktopSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DesktopActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(modifier.clip(CircleShape).background(DesktopAccent).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.Black, fontWeight = FontWeight.SemiBold)
    }
}

private fun SearchResult.key(): String = when (this) {
    is SearchResult.TopTrack -> "track:${song.videoId}"
    is SearchResult.Track -> "track:${song.videoId}"
    is SearchResult.Browse -> "browse:${item.browseId}"
}

/**
 * Whether [this] is a music video rather than a song: by its flag, or — for rows that come
 * unmarked, as a video search's do — by its picture being a video frame from YouTube's image host
 * rather than a sleeve from YouTube Music's.
 */
internal fun Song.looksLikeVideo(): Boolean = isVideo || thumbnailUrl?.contains("ytimg.com") == true

private fun ShelfItem.toSong(): Song {
    val parts = subtitle.split(" • ", " · ", " | ").filter(String::isNotBlank)
    // Android's InnertubeParser uses the first non-type/non-tally segment.
    val artist = parts.firstOrNull {
        !it.matches(SHELF_DURATION) &&
            it.lowercase() !in SHELF_TYPE_WORDS &&
            !it.matches(SHELF_TALLY)
    } ?: "Unknown Artist"
    return Song(
        videoId = videoId.orEmpty(),
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        durationText = parts.firstOrNull { it.matches(SHELF_DURATION) },
    )
}

private fun String.isUnknownArtist(): Boolean =
    trim().equals("unknown", ignoreCase = true) ||
        trim().equals("unknown artist", ignoreCase = true) ||
        trim().equals("unknown artist(s)", ignoreCase = true) ||
        isBlank()

private val SHELF_DURATION = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
private val SHELF_TALLY = Regex("[0-9,.]+\\s*(?:views?|likes?|songs?)", RegexOption.IGNORE_CASE)
private val SHELF_TYPE_WORDS = setOf("song", "video", "album", "ep", "single", "playlist", "mix")

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatCollectionDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60L
    return if (minutes < 60L) {
        DesktopStrings["d_duration_m", "%d min"].format(minutes)
    } else {
        DesktopStrings["d_duration_hm", "%1\$d hr %2\$d min"].format(minutes / 60L, minutes % 60L)
    }
}

/** "12 songs", in the app's language — with Russian's three plural forms where it has them. */
private fun trackCount(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val key = when {
        DesktopStrings.resolvedTag() != "ru" -> if (count == 1) "d_tracks_one" else "d_tracks_many"
        mod10 == 1 && mod100 != 11 -> "d_tracks_one"
        mod10 in 2..4 && mod100 !in 12..14 -> "d_tracks_few"
        else -> "d_tracks_many"
    }
    val fallback = if (count == 1) "%d song" else "%d songs"
    return DesktopStrings[key, fallback].format(count)
}

private fun formatSleepTimer(ms: Long): String = formatTime(ms)

/** Room at the top of a page for the pinned glass player when it lives at the top. */
internal val TOP_PLAYER_INSET = 100.dp

/** How much of the home card can still show when the bottom bar starts to rise. */
private const val BOTTOM_REVEAL_FROM = 0.6f

/** Between the top bar and a page, with no player pinned over it. */
internal val PAGE_TOP_GAP = 16.dp

/** How long Home waits at launch for the saved session before loading as a guest. */
private const val SESSION_WAIT_MS = 8_000L

/** The player's opening and its entrance, then its closing, as the cloaked warm-up plays them. */
private const val WARMUP_OPEN_MS = 950L

/** A few word-timed lines for the warm-up — see the warm-up in [BitChordDesktopApp]. */
private val WARMUP_LYRICS = DesktopLyrics(
    source = "warmup",
    lines = listOf(
        com.music.bitchord.data.lyrics.LyricLine(
            timeMs = 0,
            text = "La la la la",
            words = listOf(
                com.music.bitchord.data.lyrics.LyricWord(0, 400, "La "),
                com.music.bitchord.data.lyrics.LyricWord(400, 800, "la "),
                com.music.bitchord.data.lyrics.LyricWord(800, 1200, "la "),
                com.music.bitchord.data.lyrics.LyricWord(1200, 1600, "la"),
            ),
        ),
        com.music.bitchord.data.lyrics.LyricLine(timeMs = 1_600, text = "Ла ла ла"),
        com.music.bitchord.data.lyrics.LyricLine(timeMs = 3_200, text = "ラララ"),
    ),
)
private const val WARMUP_CLOSE_MS = 380L

/** Explore is read from disk, or fetched, this long after launch, so it is ready when opened. */
private const val EXPLORE_PREFETCH_DELAY_MS = 4_000L
private const val EXPLORE_ARTWORK_CONCURRENCY = 8
private const val EXPLORE_BATCH_MS = 250L

/** How far into a track it may still be swapped for the version tracks start as. */
private const val START_VERSION_WINDOW_MS = 8_000L

/** Liked Music, as a playlist id: the page a heart adds to and takes from. */
private const val LIKED_PLAYLIST_ID = "LM"

/** How many tracks go into a playlist in one request when a whole list is added. */
private const val PLAYLIST_ADD_BATCH = 100

/** Jam tracks left lined up when a heart re-grows the rest. */
private const val JAM_KEEP_AHEAD = 2

/** A skip this early in a jam track counts against its artist. */
private const val JAM_EARLY_SKIP_MS = 30_000L

/** Between the likes of a run into Liked Music, so YouTube takes every one. */
private const val LIKE_RUN_SPACING_MS = 120L
