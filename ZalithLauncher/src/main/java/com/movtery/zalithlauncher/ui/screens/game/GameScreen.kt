/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.ui.screens.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movtery.inputmap.keycodes.ControlEventKeycode
import com.movtery.inputmap.keycodes.OPEN_CHAT
import com.movtery.inputmap.keycodes.OPEN_CHAT_VALUE
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.bridge.CURSOR_DISABLED
import com.movtery.zalithlauncher.bridge.ZLBridgeStates
import com.movtery.zalithlauncher.bridge.ZLNativeInvoker
import com.movtery.zalithlauncher.game.input.LWJGLCharSender
import com.movtery.zalithlauncher.game.keycodes.mapToKeycode
import com.movtery.zalithlauncher.game.launch.handler.GameHandler
import com.movtery.zalithlauncher.game.sdl.SdlBridge
import com.movtery.zalithlauncher.game.sdl.SdlTextSender
import com.movtery.zalithlauncher.game.support.touch_controller.touchControllerInputModifier
import com.movtery.zalithlauncher.game.support.touch_controller.touchControllerTouchModifier
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.voxelium.VoxeliumGame
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.terracotta.Terracotta
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.rememberBoxSize
import com.movtery.zalithlauncher.ui.control.event.launcherEvent
import com.movtery.zalithlauncher.ui.control.event.lwjglEvent
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadKeyListener
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadOnActionListener
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadStickMovementListener
import com.movtery.zalithlauncher.ui.control.gamepad.SimpleGamepadCapture
import com.movtery.zalithlauncher.ui.control.gyroscope.GyroscopeReader
import com.movtery.zalithlauncher.ui.control.gyroscope.isGyroscopeAvailable
import com.movtery.zalithlauncher.ui.control.input.TextInputMode
import com.movtery.zalithlauncher.ui.screens.game.elements.ForceCloseOperation
import com.movtery.zalithlauncher.ui.screens.game.elements.GamepadModePromptDialog
import com.movtery.zalithlauncher.ui.screens.game.elements.LogBox
import com.movtery.zalithlauncher.ui.screens.game.elements.LogState
import com.movtery.zalithlauncher.ui.screens.game.multiplayer.TerracottaOperation
import com.movtery.zalithlauncher.ui.screens.game.multiplayer.rememberTerracottaViewModel
import com.movtery.zalithlauncher.ui.voxelium.game.RuntimeMenu
import com.movtery.zalithlauncher.ui.voxelium.game.TouchControls
import com.movtery.zalithlauncher.ui.voxelium.game.rememberTouchControlsState
import com.movtery.zalithlauncher.viewmodel.EventViewModel
import com.movtery.zalithlauncher.viewmodel.GamepadViewModel
import com.movtery.zalithlauncher.viewmodel.sendToast
import io.github.voxelium.core.controls.ControlLayoutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge
import kotlin.time.Duration.Companion.milliseconds

// Voxelium: the in-game controls are Voxelium's own (ui/voxelium/game/TouchControls.kt, after the
// iOS app's VXTouchControls.m). ZalithLauncher2's control layout layer, mouse layer, hotbar
// layer, menu ball, game menu and in-game layout editor are no longer shown here; gamepad,
// gyroscope, TouchController and log box are kept.

private class GameViewModel(
    private val onChangeTextInputMode: (TextInputMode?) -> Unit
) : ViewModel() {
    /** Voxelium runtime menu shown */
    var runtimeMenuOpen by mutableStateOf(false)
    /** 强制关闭弹窗操作状态 */
    var forceCloseState by mutableStateOf<ForceCloseOperation>(ForceCloseOperation.None)

    /** 虚拟鼠标滚动事件处理 */
    val mouseScrollUpEvent = MouseScrollEvent(viewModelScope, 1.0)
    val mouseScrollDownEvent = MouseScrollEvent(viewModelScope, -1.0)

    /** 游戏内消息发送器 */
    val gameTextSender = GameTextSender(viewModelScope)

    /** 处理手柄映射的点击事件 */
    fun onKeyEvent(event: ClickEvent, pressed: Boolean) {
        val key = event.key
        when (event.type) {
            ClickEvent.Type.Key -> {
                lwjglEvent(
                    eventKey = key,
                    isMouse = key.startsWith("GLFW_MOUSE_", false),
                    isPressed = pressed
                )
            }
            ClickEvent.Type.LauncherEvent -> {
                launcherEvent(
                    eventKey = key,
                    isPressed = pressed,
                    onSwitchIME = { onChangeTextInputMode(null) },
                    onSwitchMenu = { switchMenu() },
                    onSingleScrollUp = { mouseScrollUpEvent.scrollSingle() },
                    onSingleScrollDown = { mouseScrollDownEvent.scrollSingle() },
                    onLongScrollUp = { mouseScrollUpEvent.scrollLongPress() },
                    onLongScrollUpCancel = { mouseScrollUpEvent.cancel() },
                    onLongScrollDown = { mouseScrollDownEvent.scrollLongPress() },
                    onLongScrollDownCancel = { mouseScrollDownEvent.cancel() }
                )
            }
            ClickEvent.Type.SendText -> {
                //游戏内文本发送事件
                if (pressed) {
                    val text = event.key
                    val inGame = ZLBridgeStates.cursorMode.value == CURSOR_DISABLED
                    gameTextSender.send(GameTextSender.Data(text, inGame))
                }
                return
            }
            else -> return
        }
    }

    /** Opens or closes the runtime menu */
    fun switchMenu() {
        runtimeMenuOpen = !runtimeMenuOpen
    }

    /**
     * 清除所有游戏状态
     */
    fun clearState() {
        mouseScrollUpEvent.cancel()
        mouseScrollDownEvent.cancel()
        gameTextSender.cancel()
        onChangeTextInputMode(TextInputMode.DISABLE)
    }

    override fun onCleared() {
        clearState()
    }
}

/**
 * 鼠标滚轮事件管理
 * @param offset 滚轮滚动距离
 */
private class MouseScrollEvent(
    private val scope: CoroutineScope,
    private val offset: Double
) {
    private var mouseScrollJob: Job? = null

    /**
     * 取消滚动事件，并重置状态
     */
    fun cancel() {
        mouseScrollJob?.cancel()
        mouseScrollJob = null
    }

    /**
     * 单击响应一次滚轮滚动事件
     */
    fun scrollSingle() {
        CallbackBridge.sendScroll(0.0, offset)
    }

    /**
     * 长按不间断触发滚轮滚动事件
     */
    fun scrollLongPress() {
        mouseScrollJob?.cancel()
        mouseScrollJob = scope.launch {
            while (true) {
                try {
                    ensureActive()
                    CallbackBridge.sendScroll(0.0, offset)
                    delay(50L.milliseconds)
                } catch (_: Exception) {
                    break
                }
            }
            mouseScrollJob = null
        }
    }
}

/**
 * 游戏内消息发送器
 */
private class GameTextSender(private val scope: CoroutineScope) {
    /**
     * @param text 要发送的文本
     * @param inGame 当前是否处于游戏内，如果在游戏中，则会尝试打开聊天栏
     */
    data class Data(
        val text: String,
        val inGame: Boolean
    )

    private var messageChannel: Channel<Data>? = null
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        messageChannel?.close()
        messageChannel = null
        job = null
    }

    /**
     * 尝试向游戏发送文本（排队发送）
     */
    fun send(data: Data) {
        if (job?.isActive != true || messageChannel == null) {
            job?.cancel()
            messageChannel?.close()

            messageChannel = Channel(Channel.UNLIMITED)
            job = scope.launch {
                messageChannel?.let { channel ->
                    for ((text, inGame) in channel) {
                        sendMessage(text, inGame)
                    }
                }
            }
        }

        messageChannel?.trySend(data)
    }

    private suspend fun sendMessage(text: String, inGame: Boolean) {
        withContext(Dispatchers.Main) {
            // 仅 SDL 渲染路径（MC 26.3+）经 SDL 通道提交；仅手柄子系统初始化 SDL 时游戏输入仍走 GLFW 桥
            val useSdl = SdlBridge.sdlEnabled && SdlBridge.isSdlRenderActive()

            fun sendText() {
                for (ch in text) {
                    if (useSdl) {
                        SdlTextSender.sendChar(ch)
                    } else {
                        LWJGLCharSender.sendChar(ch)
                    }
                }
            }

            if (inGame) {
                //根据options.txt中的配置，找到打开聊天栏的键
                //如果找不到，则忽略这次事件
                mapToKeycode(OPEN_CHAT, OPEN_CHAT_VALUE)?.let { openChat ->
                    if (useSdl) {
                        SdlTextSender.sendKey(openChat)
                        // 等待游戏侧激活文本输入通道，未激活就提交的文本会被 SDL 丢弃
                        if (SdlTextSender.awaitTextInputActive()) {
                            sendText()
                            delay(50L.milliseconds)
                            SdlTextSender.sendEnter()
                        }
                    } else {
                        CallbackBridge.sendKeyPress(openChat)
                        delay(50L.milliseconds)
                        sendText()
                        delay(50L.milliseconds)
                        LWJGLCharSender.sendEnter()
                    }
                }
            } else {
                //如果当前不在游戏内，则直接发送文本
                sendText()
            }
        }
    }
}


@Composable
private fun rememberGameViewModel(
    version: Version,
    onChangeTextInputMode: (TextInputMode?) -> Unit
) = viewModel(
    key = version.toString()
) {
    GameViewModel(onChangeTextInputMode)
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun GameScreen(
    version: Version,
    gameHandler: GameHandler,
    showGameInfo: Boolean,
    onInfoBoxClose: () -> Unit,
    logState: LogState,
    onLogStateChange: (LogState) -> Unit,
    textInputMode: TextInputMode,
    isTouchProxyEnabled: Boolean,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    getAccountName: () -> String?,
    eventViewModel: EventViewModel,
    gamepadViewModel: GamepadViewModel,
) {
    val context = LocalContext.current
    val viewModel = rememberGameViewModel(version) { mode ->
        eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(mode))
    }
    val cursorMode by ZLBridgeStates.cursorMode.collectAsStateWithLifecycle()
    val isGrabbing = remember(cursorMode) {
        cursorMode == CURSOR_DISABLED
    }
    val terracottaViewModel = rememberTerracottaViewModel(
        keyTag = gameHandler.toString() + "_Terracotta",
        gameHandler = gameHandler,
        eventViewModel = eventViewModel,
        getUserName = getAccountName
    )

    //Voxelium 触控：布局来自 controls/layout.json（与 iOS 相同的格式）
    val controlsStore = remember { ControlLayoutStore(VoxeliumGame.paths()) }
    val gameDir = remember(version) { version.getGameDir() }
    val controls = rememberTouchControlsState(
        store = controlsStore,
        gameDir = gameDir,
        onRuntimeMenu = { viewModel.runtimeMenuOpen = true },
        onKeyboard = { eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(null)) }
    )

    LaunchedEffect(viewModel.runtimeMenuOpen) {
        //向VMActivity同步状态，打开运行菜单时，不会继续处理按键事件
        eventViewModel.sendEvent(EventViewModel.Event.Game.KeyHandle(!viewModel.runtimeMenuOpen))
    }

    ForceCloseOperation(
        operation = viewModel.forceCloseState,
        onChange = { viewModel.forceCloseState = it },
        onForceClose = {
            Terracotta.setWaiting(false)
            ZLNativeInvoker.jvmExit(0, false)
        },
        text = stringResource(R.string.game_menu_option_force_close_text)
    )

    TerracottaOperation(
        viewModel = terracottaViewModel,
        onShowToast = { text, duration ->
            eventViewModel.sendToast(text, duration)
        }
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenSize = rememberBoxSize()

        if (AllSettings.gamepadControl.state) {
            GamepadOnActionListener(
                gamepadViewModel = gamepadViewModel,
                onAction = {
                    controls.hiddenForGamepad = true
                }
            )
        }

        if (AllSettings.gamepadControl.state && gamepadViewModel.gamepadEngaged) {
            //手柄事件监听
            GamepadKeyListener(
                gamepadViewModel = gamepadViewModel,
                isGrabbing = isGrabbing,
                onKeyEvent = { events, pressed ->
                    events.forEach { event ->
                        viewModel.onKeyEvent(event, pressed)
                    }
                }
            )

            //手柄摇杆控制移动事件监听
            GamepadStickMovementListener(
                gamepadViewModel = gamepadViewModel,
                isGrabbing = isGrabbing,
                onKeyEvent = { event, pressed ->
                    viewModel.onKeyEvent(event, pressed)
                }
            )
        }

        //Voxelium 触控层：按键、摇杆、视角、放置/破坏、物品栏、菜单指针
        TouchControls(
            state = controls,
            inGame = isGrabbing,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isTouchProxyEnabled) {
                        Modifier
                            .touchControllerTouchModifier(screenSize = screenSize)
                            .touchControllerInputModifier(
                                screenSize = screenSize,
                                onInputAreaRectUpdated = onInputAreaRectUpdated,
                            )
                    } else Modifier
                )
        )

        //陀螺仪控制
        val isGyroscopeAvailable = remember(context) {
            isGyroscopeAvailable(context = context)
        }
        if (isGrabbing && isGyroscopeAvailable && AllSettings.gyroscopeControl.state) {
            GyroscopeReader(
                xEvent = { delta ->
                    CallbackBridge.sendCursorDelta(if (AllSettings.gyroscopeInvertX.state) -delta else delta, 0f)
                },
                yEvent = { delta ->
                    CallbackBridge.sendCursorDelta(0f, if (AllSettings.gyroscopeInvertY.state) delta else -delta)
                },
                sampleRate = AllSettings.gyroscopeSampleRate.state,
                smoothing = AllSettings.gyroscopeSmoothing.state,
                smoothingWindow = AllSettings.gyroscopeSmoothingWindow.state,
                sensitivity = AllSettings.gyroscopeSensitivity.state / 100f
            )
        }

        GameInfoBox(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(all = 16.dp),
            versionName = version.getVersionName(),
            versionInfo = version.getVersionInfo()?.getInfoString(),
            visible = showGameInfo,
            onClose = onInfoBoxClose
        )

        LogBox(
            enableLog = logState.value,
            onClose = {
                onLogStateChange(LogState.CLOSE)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (viewModel.runtimeMenuOpen) {
            RuntimeMenu(
                state = controls,
                logsShown = logState.value,
                onDismiss = { viewModel.runtimeMenuOpen = false },
                onKeyboard = { eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(null)) },
                onSwitchLogs = { onLogStateChange(logState.next()) },
                onForceClose = { viewModel.forceCloseState = ForceCloseOperation.Show }
            )
        }

        if (AllSettings.gamepadControl.state) {
            //手柄事件捕获层
            SimpleGamepadCapture(
                gamepadViewModel = gamepadViewModel
            )
        }

        //手柄输入模式选择询问
        GamepadModePromptDialog(
            visible = gamepadViewModel.modePromptVisible,
            onConfirm = { mode ->
                gamepadViewModel.confirmModePrompt(mode)
            }
        )
    }

    LaunchedEffect(Unit) {
        eventViewModel.events
            .filterIsInstance<EventViewModel.Event.Game>()
            .collect { event ->
                when (event) {
                    is EventViewModel.Event.Game.OnBack -> {
                        //按下返回键：发送 Escape（暂停 / 关闭界面）
                        val escape = ClickEvent(
                            type = ClickEvent.Type.Key,
                            key = ControlEventKeycode.GLFW_KEY_ESCAPE
                        )
                        viewModel.onKeyEvent(escape, true)
                        delay(10L.milliseconds)
                        viewModel.onKeyEvent(escape, false)
                    }
                    is EventViewModel.Event.Game.OnResume -> {
                        viewModel.clearState()
                        controls.releaseAll()
                    }
                    else -> { /*忽略*/ }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameInfoBox(
    versionName: String,
    versionInfo: String?,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        BackgroundCard(
            modifier = modifier,
            influencedByBackground = false,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 16.dp)
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    //提示信息
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.game_loading),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.game_loading_version_name, versionName),
                            style = MaterialTheme.typography.labelLarge
                        )
                        versionInfo?.let { info ->
                            Text(
                                text = stringResource(R.string.game_loading_version_info, info),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                IconButton(
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                    onClick = onClose
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.generic_close)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
private fun PreviewGameInfoBox() {
    MaterialExpressiveTheme {
        GameInfoBox(
            versionName = "1.21.11",
            versionInfo = "1.21.11",
            visible = true,
            onClose = {}
        )
    }
}
