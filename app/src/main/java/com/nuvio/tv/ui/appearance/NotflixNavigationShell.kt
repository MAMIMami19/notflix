package com.nuvio.tv.ui.appearance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text

data class NotflixDestination(val route: String, val title: String, val icon: ImageVector? = null)

/** Restyle 2: the active profile as shown by the top-bar circle. */
data class NotflixProfileChip(val name: String, val colorHex: String, val avatarImageUrl: String?)

@Composable
fun rememberNotflixNavigationShell(
    navigation: NotflixNavigationState,
    currentRoute: String?,
    selectedRoute: String?,
    rootRoutes: Set<String>,
    homeRoute: String,
    destinations: List<NotflixDestination>,
    contentFocusRequester: FocusRequester,
    profile: NotflixProfileChip?,
    onSwitchProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    onExit: () -> Unit,
    longPressBackHeld: MutableState<Boolean>
): PresentationShell {
    val root = currentRoute in rootRoutes
    SideEffect {
        navigation.selectedRoute = selectedRoute
        navigation.topOrder = destinations.map { it.route } + (if (profile != null) listOf("profile") else emptyList())
    }
    BackHandler(root) {
        if (longPressBackHeld.value) return@BackHandler
        if (navigation.topFocused) onExit() else navigation.focusTop()
    }
    return PresentationShell(
        rootModifier = Modifier.background(NotflixVisualSpec.Background).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) navigation.interacted = true
            if (event.key == Key.Back && root && event.nativeKeyEvent.isLongPress && event.type == KeyEventType.KeyDown) {
                longPressBackHeld.value = true
                navigation.focusTop()
                true
            } else if (event.key == Key.Back && longPressBackHeld.value) {
                if (event.type == KeyEventType.KeyUp) longPressBackHeld.value = false
                true
            } else false
        },
        contentModifier = (if (root && currentRoute != homeRoute) Modifier.padding(top = NotflixVisualSpec.NAV_HEIGHT.dp) else Modifier)
            .onFocusChanged { if (it.hasFocus) navigation.topFocused = false }
            // M29: only Home registered the content target, so Down from the bar was lost on Search/Library/Settings.
            // Other root screens: the content group is the target and returns to its last focused element.
            .then(if (root && currentRoute != homeRoute) Modifier.focusRequester(contentFocusRequester).focusRestorer() else Modifier)
            .focusGroup(),
        chrome = {
            if (root && !navigation.immersive) {
                Row(
                    Modifier.fillMaxWidth().height(NotflixVisualSpec.NAV_HEIGHT.dp)
                        .background(Brush.verticalGradient(listOf(NotflixVisualSpec.Background.copy(
                            alpha = if (currentRoute == homeRoute && navigation.homeAtTop) .84f else 1f),
                            NotflixVisualSpec.Background.copy(alpha = if (currentRoute == homeRoute && navigation.homeAtTop) .05f else 1f))))
                        .padding(horizontal = NotflixVisualSpec.GUTTER.dp, vertical = 4.dp)
                        .testTag("notflix-navigation"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotflixWordmark(height = 22.dp)
                    Spacer(Modifier.width(18.dp))
                    destinations.forEach { item ->
                        key(item.route) {
                            TopDestination(item.route, item.title, item.route == selectedRoute, navigation,
                                contentFocusRequester, currentRoute == homeRoute, { onNavigate(item.route) }, item.icon)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (profile != null) {
                        TopProfileCircle(profile, navigation, contentFocusRequester,
                            currentRoute == homeRoute, onSwitchProfile)
                    }
                }
            }
        }
    )
}

/**
 * Restyle 2, Netflix-style top-bar item. The ACTIVE tab is a white pill with
 * black text (an icon destination: a white circle with a black icon); a
 * focused inactive tab gets a white border and no fill change; inactive tabs
 * are plain text. The old accent underline is gone — the pill is the active
 * indicator now, like Netflix's own bar.
 */
@Composable
private fun TopDestination(
    route: String, title: String, active: Boolean, navigation: NotflixNavigationState,
    contentFocusRequester: FocusRequester, isHome: Boolean, onClick: () -> Unit,
    icon: ImageVector? = null
) {
    val requester = remember { FocusRequester() }
    val confirmSound = rememberNotflixConfirmSound()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(route, requester) {
        navigation.topRequesters[route] = requester
        onDispose { navigation.topRequesters.remove(route) }
    }
    val shape = if (icon != null) CircleShape else RoundedCornerShape(16.dp)
    Box(
        // Restyle 3.1: 150.dp truncated the French «Séries et émissions» to
        // «Séries et émis…» — 200.dp fits it (the bar still has room: logo +
        // 4 tabs + gear + profile ≈ 730.dp of 960.dp).
        Modifier.widthIn(max = 200.dp).focusRequester(requester).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) {
                if (navigation.topFocused || navigation.homeNeedsFocusRepair?.invoke() != true) {
                    navigation.topFocused = true
                    navigation.homeCancelFocus?.invoke()
                }
            }
        }.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                navigation.topFocused = false
                if (isHome) navigation.homeDown?.invoke() else runCatching { contentFocusRequester.requestFocus() }
                true
            } else if (event.type == KeyEventType.KeyDown && event.key in setOf(Key.DirectionLeft, Key.DirectionRight)) {
                navigation.topFocused = true
                navigation.homeCancelFocus?.invoke()
                val index = navigation.topOrder.indexOf(route)
                val direction = if ((event.key == Key.DirectionRight) != rtl) 1 else -1
                navigation.topOrder.getOrNull(index + direction)?.let { navigation.topRequesters[it]?.requestFocus() }
                true
            } else false
        }.clip(shape)
            .background(if (active) NotflixVisualSpec.Text else Color.Transparent, shape)
            .border(2.dp, if (focused && !active) NotflixVisualSpec.Text else Color.Transparent, shape)
            .clickable(role = Role.Tab, onClick = {
                confirmSound()
                navigation.topFocused = true
                navigation.homeCancelFocus?.invoke()
                onClick()
            })
            .padding(if (icon != null) PaddingValues(6.dp) else PaddingValues(horizontal = 12.dp, vertical = 5.dp))
            .semantics { selected = active }.testTag("notflix-destination-$route"),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = title,
                tint = if (active) NotflixVisualSpec.Background else NotflixVisualSpec.Text,
                modifier = Modifier.size(20.dp))
        } else {
            Text(title, color = if (active) NotflixVisualSpec.Background else if (focused) NotflixVisualSpec.Text else NotflixVisualSpec.Secondary,
                fontFamily = NotflixVisualSpec.BodyFont, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Restyle 2: the profile CIRCLE at the end of the top bar — the active
 * profile's avatar (photo, or initial on its color), Netflix-style. OK opens
 * the profile switcher. Same focus/key wiring as [TopDestination]; focus
 * shows the circle's white ring.
 */
@Composable
private fun TopProfileCircle(
    profile: NotflixProfileChip, navigation: NotflixNavigationState,
    contentFocusRequester: FocusRequester, isHome: Boolean, onClick: () -> Unit
) {
    val requester = remember { FocusRequester() }
    val confirmSound = rememberNotflixConfirmSound()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(requester) {
        navigation.topRequesters["profile"] = requester
        onDispose { navigation.topRequesters.remove("profile") }
    }
    Box(
        Modifier.focusRequester(requester).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) {
                if (navigation.topFocused || navigation.homeNeedsFocusRepair?.invoke() != true) {
                    navigation.topFocused = true
                    navigation.homeCancelFocus?.invoke()
                }
            }
        }.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                navigation.topFocused = false
                if (isHome) navigation.homeDown?.invoke() else runCatching { contentFocusRequester.requestFocus() }
                true
            } else if (event.type == KeyEventType.KeyDown && event.key in setOf(Key.DirectionLeft, Key.DirectionRight)) {
                navigation.topFocused = true
                navigation.homeCancelFocus?.invoke()
                val index = navigation.topOrder.indexOf("profile")
                val direction = if ((event.key == Key.DirectionRight) != rtl) 1 else -1
                navigation.topOrder.getOrNull(index + direction)?.let { navigation.topRequesters[it]?.requestFocus() }
                true
            } else false
        }.clickable(role = Role.Button, onClick = {
            confirmSound()
            navigation.topFocused = true
            navigation.homeCancelFocus?.invoke()
            onClick()
        }).testTag("notflix-destination-profile"),
        contentAlignment = Alignment.Center
    ) {
        ProfileAvatarCircle(
            name = profile.name,
            colorHex = profile.colorHex,
            size = 36.dp,
            isSelected = focused,
            avatarImageUrl = profile.avatarImageUrl
        )
    }
}
