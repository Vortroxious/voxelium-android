/*
 * Voxelium for Android
 * Copyright (C) 2026 Voxelium contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.voxelium.game

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.utils.currentGameDisplayLayout
import io.github.voxelium.core.controls.ControlButton
import io.github.voxelium.core.controls.ControlKeys
import io.github.voxelium.core.controls.ControlLayout
import io.github.voxelium.core.controls.ControlLayoutStore
import io.github.voxelium.core.controls.ControlSpecial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Voxelium's on-screen controls and touch gestures for the game, after the iOS app's
// App/Voxelium/Native/VXTouchControls.m (zalithios d602cd1). Buttons and joystick come from
// VoxeliumCore's ControlLayout (controls/layout.json, the same file format as iOS), sizes in dp
// where iOS uses points. Input goes to the game through CallbackBridge in GLFW terms.

/** Where the controls send input, in GLFW terms (VXInputSink). */
internal interface GameInputSink {
    fun key(code: Int, pressed: Boolean)
    fun mouseButton(button: Int, pressed: Boolean)
    /** A press, then the release a frame later, so a game that polls the button still sees it. */
    fun click(button: Int)
    /** Pointer position in menus, in screen pixels. */
    fun cursorAt(screen: Offset)
    /** Mouse look while the game has captured the cursor: a delta in screen pixels. */
    fun cursorDelta(dx: Float, dy: Float)
    fun scroll(dx: Double, dy: Double)
}

/** [GameInputSink] on CallbackBridge; [toGame] maps screen pixels to the game's framebuffer. */
internal class BridgeInputSink(private val toGame: (Offset) -> Offset) : GameInputSink {
    override fun key(code: Int, pressed: Boolean) {
        CallbackBridge.sendKeyPress(code, CallbackBridge.getCurrentMods(), pressed)
        CallbackBridge.setModifiers(code, pressed)
    }

    override fun mouseButton(button: Int, pressed: Boolean) = CallbackBridge.sendMouseButton(button, pressed)

    override fun click(button: Int) = CallbackBridge.putMouseEvent(button)

    override fun cursorAt(screen: Offset) {
        val p = toGame(screen)
        CallbackBridge.sendCursorPos(p.x, p.y)
    }

    override fun cursorDelta(dx: Float, dy: Float) = CallbackBridge.sendCursorDelta(dx, dy)

    override fun scroll(dx: Double, dy: Double) = CallbackBridge.sendScroll(dx, dy)
}

private const val MOUSE_LEFT = 0
private const val MOUSE_RIGHT = 1
private const val MOUSE_LAST = 7

/** As VXTouchControls.m: a hold this long without moving breaks (left button); shorter is a tap. */
private const val LONG_PRESS_MS = 350L
/** Movement (dp) that turns a tap into a drag. */
private const val TAP_SLOP_DP = 12f

/** A layout button as the overlay uses it (VXControlButton). */
@Stable
internal class LiveButton(val spec: ControlButton) {
    /** Keys checked as iOS does when loading a file: 1…GLFW_KEY_LAST. */
    val keys: List<Int> = spec.keys.filter { it in 1..ControlKeys.LAST }
    val mouse: Int = spec.mouse.takeIf { it in 0..MOUSE_LAST } ?: -1
    /** Drawer items show in game and in menus whenever the drawer is open (iOS). */
    val inGame: Boolean = spec.showInGame || spec.inDrawer
    val inMenu: Boolean = spec.showInMenu || spec.inDrawer
    var pressedLook by mutableStateOf(false)
    var latched = false
}

/**
 * The overlay's state and its touch handling (VXControlsOverlay and VXTouchView). Everything
 * runs on the main thread.
 */
@Stable
internal class TouchControlsState(
    private val store: ControlLayoutStore,
    private val gameDir: File,
    private val scope: CoroutineScope,
    private val onRuntimeMenu: () -> Unit,
    private val onKeyboard: () -> Unit,
) {
    var sink: GameInputSink = BridgeInputSink { it }
    var view: View? = null

    var layout by mutableStateOf<ControlLayout?>(null)
        private set
    var buttons by mutableStateOf<List<LiveButton>>(emptyList())
        private set
    /** Layout opacity, applied to buttons and joystick. */
    var opacity by mutableFloatStateOf(1f)

    var inGame by mutableStateOf(false)
        private set
    var guiHidden by mutableStateOf(false)
    var drawerOpen by mutableStateOf(false)
    var virtualMouse by mutableStateOf(false)
    /** Hidden while a gamepad is in use, until the screen is touched again (ZalithLauncher2's rule). */
    var hiddenForGamepad by mutableStateOf(false)

    /** Screen in pixels and pixels per dp. */
    var screen by mutableStateOf(IntSize.Zero)
        private set
    var density = 1f
        private set

    /** Joystick knob offset from the stick's centre, in pixels. */
    var knob by mutableStateOf(Offset.Zero)
        private set
    /** Virtual mouse pointer, in screen pixels. */
    var virtualCursor by mutableStateOf(Offset.Unspecified)
        private set

    fun setScreen(size: IntSize, density: Float) {
        this.density = density
        if (size == screen) return
        screen = size
        if (virtualCursor == Offset.Unspecified) virtualCursor = Offset(size.width / 2f, size.height / 2f)
    }

    /** Loads the user's layout, or the default for this screen (blocking). */
    fun loadLayout(widthDp: Double, heightDp: Double): ControlLayout = store.effective(widthDp, heightDp)

    fun applyLayout(l: ControlLayout) {
        releaseAll()
        layout = l
        opacity = l.opacity.toFloat()
        buttons = l.buttons.map(::LiveButton)
    }

    /** Saves the layout with the current opacity (the runtime menu's slider). */
    fun saveOpacity() {
        val l = layout ?: return
        val clamped = opacity.toDouble().coerceIn(ControlLayout.OPACITY_RANGE)
        val updated = l.copy(opacity = clamped)
        layout = updated
        scope.launch(Dispatchers.IO) { runCatching { store.save(updated) } }
    }

    // MARK: geometry

    fun frame(b: LiveButton): Rect {
        val w = b.spec.width.toFloat() * density
        val h = b.spec.height.toFloat() * density
        val sw = screen.width.toFloat()
        val sh = screen.height.toFloat()
        // Keep every control on screen (Amethyst clamps the same way when loading).
        val x = (b.spec.x.toFloat() * sw).coerceAtMost(sw - w).coerceAtLeast(0f)
        val y = (b.spec.y.toFloat() * sh).coerceAtMost(sh - h).coerceAtLeast(0f)
        return Rect(x, y, x + w, y + h)
    }

    fun stickFrame(): Rect {
        val j = layout?.joystick ?: return Rect.Zero
        val s = j.size.toFloat() * density
        val sw = screen.width.toFloat()
        val sh = screen.height.toFloat()
        val x = (j.x.toFloat() * sw).coerceAtMost(sw - s).coerceAtLeast(0f)
        val y = (j.y.toFloat() * sh).coerceAtMost(sh - s).coerceAtLeast(0f)
        return Rect(x, y, x + s, y + s)
    }

    fun isShown(b: LiveButton): Boolean {
        if (hiddenForGamepad) return false
        var shown = if (inGame) b.inGame else b.inMenu
        if (b.spec.inDrawer) shown = shown && drawerOpen
        if (guiHidden && b.spec.special != ControlSpecial.HIDE_GUI) shown = false
        return shown
    }

    /** Joystick: in game only. */
    val stickShown: Boolean get() = inGame && !guiHidden && !hiddenForGamepad && layout != null
    val pointerShown: Boolean get() = virtualMouse && !inGame
    val gaugeShown: Boolean get() = !guiHidden && !drawerOpen && !hiddenForGamepad

    // MARK: game state

    fun updateInGame(value: Boolean) {
        if (inGame == value) return
        inGame = value
        if (!value) {
            // Leaving the game (menu, inventory, chat): release movement and latched keys
            // that can't be unlatched from a menu (iOS: Sprint).
            resetStick()
            for (b in buttons) {
                if (!b.inMenu || ControlKeys.LEFT_CONTROL in b.keys) releaseLatched(b)
            }
        }
    }

    /** Lifts everything: the app went to the background or the overlay went away. */
    fun releaseAll() {
        for (owner in owners.values.toList()) end(owner)
        owners.clear()
        resetStick()
        for (b in buttons) {
            releaseLatched(b)
            if (b.pressedLook && !b.spec.toggle && b.spec.special == ControlSpecial.NONE) {
                send(b, false)
                b.pressedLook = false
            }
        }
    }

    // MARK: buttons (VXControlButton)

    private fun send(b: LiveButton, pressed: Boolean) {
        for (k in b.keys) sink.key(k, pressed)
        if (b.mouse >= 0) sink.mouseButton(b.mouse, pressed)
    }

    private fun buttonDown(b: LiveButton) {
        haptic(light = true)
        if (b.spec.special != ControlSpecial.NONE) {
            special(b.spec.special)
            return
        }
        if (b.spec.toggle) {
            b.latched = !b.latched
            send(b, b.latched)
            b.pressedLook = b.latched
        } else {
            send(b, true)
            b.pressedLook = true
        }
    }

    private fun buttonUp(b: LiveButton) {
        if (b.spec.special != ControlSpecial.NONE || b.spec.toggle) return
        send(b, false)
        b.pressedLook = false
    }

    private fun releaseLatched(b: LiveButton) {
        if (b.latched) {
            b.latched = false
            send(b, false)
            b.pressedLook = false
        }
    }

    fun special(s: ControlSpecial) {
        when (s) {
            ControlSpecial.HIDE_GUI -> guiHidden = !guiHidden
            ControlSpecial.DRAWER -> drawerOpen = !drawerOpen
            ControlSpecial.KEYBOARD -> onKeyboard()
            ControlSpecial.VIRTUAL_MOUSE -> virtualMouse = !virtualMouse
            ControlSpecial.RUNTIME_MENU -> onRuntimeMenu()
            ControlSpecial.NONE -> {}
        }
    }

    // MARK: joystick (VXJoystick: relative, W/A/S/D)

    private var stickHeld = 0 // bit 0 W, 1 S, 2 A, 3 D
    private val stickKeys = intArrayOf(ControlKeys.W, ControlKeys.S, ControlKeys.A, ControlKeys.D)

    private fun applyStick(held: Int) {
        for (i in 0 until 4) {
            val was = (stickHeld shr i) and 1
            val now = (held shr i) and 1
            if (was != now) sink.key(stickKeys[i], now == 1)
        }
        stickHeld = held
    }

    private fun trackStick(p: Offset) {
        val f = stickFrame()
        val r = f.width / 2
        if (r <= 0f) return
        var dx = (p.x - f.center.x) / r
        var dy = (p.y - f.center.y) / r
        val len = hypot(dx, dy)
        if (len > 1) {
            dx /= len
            dy /= len
        }
        knob = Offset(dx * r * 0.6f, dy * r * 0.6f)
        var held = 0
        if (dy < -0.35f) held = held or 1
        if (dy > 0.35f) held = held or 2
        if (dx < -0.35f) held = held or 4
        if (dx > 0.35f) held = held or 8
        applyStick(held)
    }

    private fun resetStick() {
        applyStick(0)
        knob = Offset.Zero
    }

    // MARK: hotbar (vxGuiScale, hotbarSlotAt)

    private var guiScaleSetting = 0
    private var guiScaleReadAt = Long.MIN_VALUE / 2

    /**
     * The GUI scale Minecraft uses: options.txt "guiScale" (0 = auto), clamped to what the
     * framebuffer allows. Re-read at most every 2 s, since the player can change it in game.
     */
    private fun guiScale(fbW: Int, fbH: Int, now: Long): Int {
        var maxScale = 1
        while (maxScale < fbW && maxScale < fbH && fbW / (maxScale + 1) >= 320 && fbH / (maxScale + 1) >= 240) maxScale++
        if (now - guiScaleReadAt > 2000) {
            guiScaleReadAt = now
            guiScaleSetting = runCatching {
                File(gameDir, "options.txt").useLines { lines ->
                    lines.firstOrNull { it.startsWith("guiScale:") }?.substring(9)?.trim()?.toIntOrNull()
                }
            }.getOrNull() ?: 0
        }
        val setting = guiScaleSetting
        return if (setting in 1 until maxScale) setting else maxScale
    }

    /** Minecraft hotbar slot under a screen point, or -1. */
    fun hotbarSlotAt(p: Offset, now: Long): Int {
        val fbW = CallbackBridge.windowWidth
        val fbH = CallbackBridge.windowHeight
        if (fbW <= 0 || fbH <= 0) return -1
        val s = guiScale(fbW, fbH, now)
        val g = toGame(p)
        // The touch area reaches 6 GUI pixels above the drawn hotbar, so slots are easier to hit.
        val left = (fbW / s / 2.0 - 91) * s
        val top = (fbH / s - 28.0) * s
        if (g.y < top || g.x < left || g.x >= left + 182.0 * s) return -1
        val slot = ((g.x - left) / (20.0 * s)).toInt()
        return if (slot in 0..8) slot else -1
    }

    var toGame: (Offset) -> Offset = { it }

    private var hotbarSlot = -1

    private fun selectHotbarSlot(slot: Int) {
        if (slot !in 0..8) return
        hotbarSlot = slot
        haptic(light = true)
        sink.key(ControlKeys.KEY_1 + slot, true)
        sink.key(ControlKeys.KEY_1 + slot, false)
    }

    // MARK: touches (VXTouchView)

    private sealed interface Owner {
        data class Button(val button: LiveButton) : Owner
        data object Stick : Owner
        data object Hotbar : Owner
        data object Primary : Owner
        data object Secondary : Owner
        /** A second finger on a control that already has one, as UIKit's single-touch views. */
        data object Ignored : Owner
    }

    private val owners = mutableMapOf<PointerId, Owner>()

    private var lastPrimary = Offset.Zero
    private var startPrimary = Offset.Zero
    private var startTime = 0L
    private var primaryInGame = false
    private var primaryVirtual = false
    private var moved = false
    private var holding = false
    private var gestureGeneration = 0

    private fun hitControl(p: Offset): Owner? {
        if (stickShown && stickFrame().contains(p)) return Owner.Stick
        for (b in buttons.asReversed()) if (isShown(b) && frame(b).contains(p)) return Owner.Button(b)
        return null
    }

    fun down(id: PointerId, p: Offset, now: Long) {
        // Controls hidden for a gamepad come back on this touch, which doesn't press them.
        val wasHidden = hiddenForGamepad
        hiddenForGamepad = false
        when (val hit = if (wasHidden) null else hitControl(p)) {
            Owner.Stick -> {
                if (Owner.Stick in owners.values) {
                    owners[id] = Owner.Ignored
                } else {
                    owners[id] = Owner.Stick
                    haptic(light = true)
                    trackStick(p)
                }
                return
            }
            is Owner.Button -> {
                if (hit in owners.values) {
                    owners[id] = Owner.Ignored
                } else {
                    owners[id] = hit
                    buttonDown(hit.button)
                }
                return
            }
            else -> {}
        }
        // In game, a touch that lands on the hotbar selects that slot right away and owns the
        // hotbar until it lifts: sliding along it changes slot. It never looks, uses or breaks.
        val game = inGame
        if (Owner.Hotbar !in owners.values && game) {
            val slot = hotbarSlotAt(p, now)
            if (slot >= 0) {
                owners[id] = Owner.Hotbar
                hotbarSlot = -1
                selectHotbarSlot(slot)
                return
            }
        }
        if (Owner.Primary !in owners.values) {
            owners[id] = Owner.Primary
            lastPrimary = p
            startPrimary = p
            startTime = now
            primaryInGame = game
            primaryVirtual = !game && virtualMouse
            moved = false
            holding = false
            if (primaryInGame || primaryVirtual) {
                // Tap vs long press is decided later; a still, held finger presses the left button.
                val gen = ++gestureGeneration
                scope.launch {
                    delay(LONG_PRESS_MS)
                    if (gen != gestureGeneration || Owner.Primary !in owners.values || moved || holding) return@launch
                    holding = true
                    haptic(light = false)
                    sink.mouseButton(MOUSE_LEFT, true)
                }
            } else {
                sink.cursorAt(p)
                sink.mouseButton(MOUSE_LEFT, true)
            }
        } else if (Owner.Secondary !in owners.values && !primaryInGame && !primaryVirtual) {
            owners[id] = Owner.Secondary
            sink.mouseButton(MOUSE_RIGHT, true)
        } else {
            owners[id] = Owner.Ignored
        }
    }

    fun move(id: PointerId, p: Offset, now: Long) {
        when (owners[id]) {
            Owner.Stick -> trackStick(p)
            Owner.Hotbar -> {
                val slot = hotbarSlotAt(p, now)
                if (slot >= 0 && slot != hotbarSlot) selectHotbarSlot(slot)
            }
            Owner.Primary -> {
                val d = p - lastPrimary
                if ((p - startPrimary).getDistance() > TAP_SLOP_DP * density) moved = true
                if (inGame) {
                    // Captured cursor (mouse look): relative motion in screen pixels, as iOS
                    // sends points × UIScreen.scale, so the sensitivity slider feels the same.
                    if (d != Offset.Zero) sink.cursorDelta(d.x, d.y)
                } else if (primaryVirtual) {
                    val c = virtualCursor
                    virtualCursor = Offset(
                        (c.x + d.x).coerceIn(0f, screen.width.toFloat()),
                        (c.y + d.y).coerceIn(0f, screen.height.toFloat())
                    )
                    sink.cursorAt(virtualCursor)
                } else {
                    sink.cursorAt(p)
                }
                lastPrimary = p
            }
            else -> {}
        }
    }

    fun up(id: PointerId, now: Long) {
        val owner = owners.remove(id) ?: return
        end(owner, now)
    }

    private fun end(owner: Owner, now: Long = Long.MAX_VALUE) {
        when (owner) {
            is Owner.Button -> buttonUp(owner.button)
            Owner.Stick -> resetStick()
            Owner.Hotbar -> {}
            Owner.Primary -> {
                gestureGeneration++
                val tap = !moved && now - startTime < LONG_PRESS_MS
                if (holding) {
                    sink.mouseButton(MOUSE_LEFT, false)
                } else if (tap && primaryInGame) {
                    haptic(light = true)
                    sink.click(MOUSE_RIGHT)
                } else if (tap && primaryVirtual) {
                    haptic(light = true)
                    sink.click(MOUSE_LEFT)
                } else if (!primaryInGame && !primaryVirtual) {
                    sink.mouseButton(MOUSE_LEFT, false)
                }
                holding = false
            }
            Owner.Secondary -> sink.mouseButton(MOUSE_RIGHT, false)
            Owner.Ignored -> {}
        }
    }

    /** Every finger lifts (the system took the touches, e.g. a dialog opened). */
    fun cancelTouches() {
        for (owner in owners.values.toList()) end(owner)
        owners.clear()
    }

    // MARK: physical mouse (menus; in game the view captures the pointer)

    var mouseSeen by mutableStateOf(false)
        private set
    private var mouseButtons = 0

    fun mouseEvent(event: PointerEvent, p: Offset) {
        if (!mouseSeen) mouseSeen = true
        hiddenForGamepad = false
        if (event.type == PointerEventType.Scroll) {
            val d = event.changes.first().scrollDelta
            sink.scroll(-d.x.toDouble(), -d.y.toDouble())
            return
        }
        if (!inGame) sink.cursorAt(p)
        var now = 0
        if (event.buttons.isPrimaryPressed) now = now or 1
        if (event.buttons.isSecondaryPressed) now = now or 2
        if (event.buttons.isTertiaryPressed) now = now or 4
        val changed = now xor mouseButtons
        for (i in 0 until 3) if ((changed shr i) and 1 == 1) sink.mouseButton(i, (now shr i) and 1 == 1)
        mouseButtons = now
    }

    /** Captured mouse while in game: relative motion, buttons and wheel. */
    fun capturedMouse(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                var dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                var dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                for (i in 0 until event.historySize) {
                    dx += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                    dy += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
                }
                if (dx != 0f || dy != 0f) sink.cursorDelta(dx, dy)
            }
            MotionEvent.ACTION_SCROLL -> sink.scroll(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
            )
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = when (event.actionButton) {
                    MotionEvent.BUTTON_PRIMARY -> 0
                    MotionEvent.BUTTON_SECONDARY -> 1
                    MotionEvent.BUTTON_TERTIARY -> 2
                    else -> return false
                }
                val pressed = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                val bit = 1 shl button
                mouseButtons = if (pressed) mouseButtons or bit else mouseButtons and bit.inv()
                sink.mouseButton(button, pressed)
            }
            else -> return false
        }
        return true
    }

    // MARK: haptics (as Amethyst: light on button press / tap, medium on long press)

    private fun haptic(light: Boolean) {
        view?.performHapticFeedback(
            if (light) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.LONG_PRESS
        )
    }
}

@Composable
internal fun rememberTouchControlsState(
    store: ControlLayoutStore,
    gameDir: File,
    onRuntimeMenu: () -> Unit,
    onKeyboard: () -> Unit,
): TouchControlsState {
    val scope = rememberCoroutineScope()
    val menu by rememberUpdatedState(onRuntimeMenu)
    val keyboard by rememberUpdatedState(onKeyboard)
    return remember(store.file, gameDir) {
        TouchControlsState(store, gameDir, scope, { menu() }, { keyboard() })
    }
}

/**
 * The on-screen controls and the gestures on the game outside them. [inGame] is whether the
 * game has captured the cursor (in the world, not in a menu).
 */
@Composable
internal fun TouchControls(
    state: TouchControlsState,
    inGame: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val view = LocalView.current
    val screen = state.screen

    DisposableEffect(view) {
        state.view = view
        onDispose {
            state.releaseAll()
            state.view = null
        }
    }

    LaunchedEffect(screen) {
        if (screen.width <= 0 || screen.height <= 0) return@LaunchedEffect
        state.toGame = { p -> currentGameDisplayLayout(screen).mapToGame(p) }
        state.sink = BridgeInputSink { p -> currentGameDisplayLayout(screen).mapToGame(p) }
        // Landscape layout in dp, the screen the game runs on.
        val w = (max(screen.width, screen.height) / density).toDouble()
        val h = (min(screen.width, screen.height) / density).toDouble()
        val layout = withContext(Dispatchers.IO) { state.loadLayout(w, h) }
        state.applyLayout(layout)
    }

    LaunchedEffect(inGame) { state.updateInGame(inGame) }

    // A physical mouse in game: capture it, as ZalithLauncher2 did, once one has been used.
    val captureMouse = inGame && state.mouseSeen
    DisposableEffect(view, captureMouse) {
        if (captureMouse) {
            view.setOnCapturedPointerListener { _, event -> state.capturedMouse(event) }
            if (view.hasWindowFocus()) view.requestPointerCapture()
        }
        onDispose {
            if (captureMouse) {
                view.setOnCapturedPointerListener(null)
                view.releasePointerCapture()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { state.setScreen(it, density) }
            .pointerInput(state) {
                awaitPointerEventScope {
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                if (change.type == PointerType.Mouse) {
                                    state.mouseEvent(event, change.position)
                                    break
                                }
                                val now = change.uptimeMillis
                                when {
                                    change.pressed && !change.previousPressed -> state.down(change.id, change.position, now)
                                    !change.pressed && change.previousPressed -> state.up(change.id, now)
                                    change.pressed && change.position != change.previousPosition ->
                                        state.move(change.id, change.position, now)
                                }
                            }
                        }
                    } finally {
                        state.cancelTouches()
                    }
                }
            }
    ) {
        if (state.layout == null || screen.width <= 0) return@Box
        val alpha = state.opacity.coerceIn(0.2f, 1f)
        for (b in state.buttons) {
            if (state.isShown(b)) ControlButtonView(state, b, alpha)
        }
        if (state.stickShown) JoystickView(state, alpha)
        if (state.pointerShown) {
            Image(
                painter = painterResource(R.drawable.img_mouse_pointer_arrow),
                contentDescription = null,
                modifier = Modifier
                    .offset { state.virtualCursor.round() }
                    .size(24.dp)
            )
        }
        if (state.gaugeShown) MemoryGaugeView(state)
    }
}

private fun Offset.round() = IntOffset(x.roundToInt(), y.roundToInt())

private val IdleColor = Color(0x4D000000) // Amethyst bgColor 0x4d000000
private val PressedColor = Color.White.copy(alpha = 0.35f)

@Composable
private fun ControlButtonView(state: TouchControlsState, b: LiveButton, alpha: Float) {
    val f = state.frame(b)
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = 50) // cornerRadius 100%
    Box(
        modifier = Modifier
            .offset { f.topLeft.round() }
            .size(with(density) { f.width.toDp() }, with(density) { f.height.toDp() })
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(if (b.pressedLook) PressedColor else IdleColor)
            .then(if (b.spec.special == ControlSpecial.DRAWER) Modifier.border(1.5.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = b.spec.name,
            style = TextStyle(color = Color.White, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 14.sp, stepSize = 0.5.sp)
        )
    }
}

@Composable
private fun JoystickView(state: TouchControlsState, alpha: Float) {
    val f = state.stickFrame()
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { f.topLeft.round() }
            .size(with(density) { f.width.toDp() })
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(IdleColor)
            .border(1.dp, IdleColor, CircleShape), // strokeColor 1291845632
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { state.knob.round() }
                .size(72.dp)
                .clip(CircleShape)
                .background(PressedColor)
        )
    }
}

/** The app's memory and its share of what it may use, refreshed every 2 s. */
@Stable
internal class MemoryReading(val usedBytes: Long, val limitBytes: Long) {
    val share: Double get() = if (limitBytes > 0) usedBytes.toDouble() / limitBytes else 0.0
    val text: String get() = String.format("%.1f / %.1f GB", usedBytes / GIB, limitBytes / GIB)
    /** Orange from 80 %, red from 90 % of the limit (iOS). */
    val background: Color
        get() = when {
            share >= 0.9 -> Color(0.8f, 0.15f, 0.1f, 0.85f)
            share >= 0.8 -> Color(0.9f, 0.55f, 0.0f, 0.85f)
            else -> Color(0f, 0f, 0f, 0.4f)
        }

    private companion object {
        const val GIB = 1073741824.0
    }
}

@Composable
internal fun rememberMemoryReading(): MemoryReading? {
    val context = LocalView.current.context
    var reading by remember { mutableStateOf<MemoryReading?>(null) }
    LaunchedEffect(context) {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        while (true) {
            reading = withContext(Dispatchers.IO) { readMemory(am) }
            delay(2000)
        }
    }
    return reading
}

/**
 * The process's resident memory (the game's JVM runs in it) and how much more the system can
 * give before it starts killing apps; used + available is the limit, as iOS's footprint +
 * os_proc_available_memory.
 */
private fun readMemory(am: android.app.ActivityManager?): MemoryReading? = runCatching {
    val pages = File("/proc/self/statm").readText().trim().split(Regex("\\s+"))[1].toLong()
    val used = pages * android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
    val info = android.app.ActivityManager.MemoryInfo()
    am?.getMemoryInfo(info)
    MemoryReading(used, used + max(0L, info.availMem))
}.getOrNull()

@Composable
private fun MemoryGaugeView(state: TouchControlsState) {
    val reading = rememberMemoryReading() ?: return
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Just under the More button (wherever the layout puts it).
    val anchor = state.buttons.firstOrNull { it.spec.special == ControlSpecial.DRAWER }?.let(state::frame)
        ?: with(density) { Rect(0f, 0f, 100.dp.toPx(), 40.dp.toPx()) }
    val gap = with(density) { 4.dp.toPx() }
    BasicText(
        text = reading.text,
        style = TextStyle(
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        ),
        maxLines = 1,
        modifier = Modifier
            .offset {
                val sw = state.screen.width.toFloat()
                val sh = state.screen.height.toFloat()
                IntOffset(
                    (anchor.center.x - size.width / 2f).coerceAtMost(sw - size.width).coerceAtLeast(0f).roundToInt(),
                    min(anchor.bottom + gap, sh - size.height).roundToInt()
                )
            }
            .onSizeChanged { size = it }
            .clip(RoundedCornerShape(8.dp))
            .background(reading.background)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
