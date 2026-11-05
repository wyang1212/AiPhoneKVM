// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.atharok.btremote.automation.*
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.DeviceEntity
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.ThirdLibrary
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardLanguage
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.EnglishUSVirtualKeyboardLayout
import com.atharok.btremote.domain.entities.settings.AppearanceSettings
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.domain.entities.settings.ThemeEntity
import com.atharok.btremote.ui.components.*
import com.atharok.btremote.ui.screens.*
import com.atharok.btremote.ui.theme.BtRemoteTheme
import com.atharok.btremote.ui.views.*
import com.atharok.btremote.ui.views.keyboard.AdvancedKeyboardModalBottomSheet
import com.atharok.btremote.ui.views.keyboard.VirtualKeyboardModalBottomSheet
import com.atharok.btremote.ui.views.remote.MoreButtonsDialog
import com.atharok.btremote.ui.views.remote.TVChannelDialog
import com.atharok.btremote.update.AppUpdateDialog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.model.Statement
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

/**
 * Publication screenshots of real components with explicitly synthetic state.
 * This is NOT Bluetooth, camera, speech, model, server, or navigation acceptance.
 * All input-report callbacks are inert. Never run against the user's normal app.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PublicPageScreenshotTest {
    private val host = ShellActivityRule()
    @get:Rule val compose = AndroidComposeTestRule<TestRule, ComponentActivity>(host) { host.activity }
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var output: File
    private lateinit var runtime: AutomationRuntime
    private val failures = mutableListOf<String>()
    private var scene by mutableStateOf<@Composable () -> Unit>({})
    private var revision by mutableIntStateOf(0)
    private var dark by mutableStateOf(false)
    private val exampleAddress = "02:00:00:00:00:01"
    private val exampleName = "UI TEST · Example phone"
    private val noKeyboard: (ByteArray) -> Unit = {}
    private val noMouse: (MouseAction, Float, Float, Float) -> Unit = { _, _, _, _ -> }
    private val marker = "UI TEST · 示例状态 · 不代表物理设备或模型验收"

    /** Avoid ActivityScenario's blocking startActivitySync on vendor-restricted devices. */
    private inner class ShellActivityRule : TestRule {
        val activity: ComponentActivity
            get() = checkNotNull(resumed(ComponentActivity::class.java)) { "Audit host is not resumed" }

        fun <A : ComponentActivity> resumed(type: Class<A>): A? {
            var result: A? = null
            val read = Runnable {
                result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull { it.javaClass == type && it.packageName == context.packageName }
                    ?.let(type::cast)
            }
            if (Looper.myLooper() == Looper.getMainLooper()) read.run()
            else instrumentation.runOnMainSync(read)
            return result
        }

        fun <A : ComponentActivity> launch(type: Class<A>): A {
            check(context.packageName.matches(Regex("org\\.example\\.aiphonekvm\\.audit(?:\\.debug)?"))) {
                "Refusing shell activity launch outside the isolated audit application"
            }
            // No -W: observe this instrumented process's lifecycle with a finite deadline instead.
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${context.packageName}/${type.name}"
            )).use { it.readBytes() }
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (SystemClock.uptimeMillis() < deadline) {
                resumed(type)?.let { return it }
                SystemClock.sleep(50)
            }
            error("Audit activity did not resume within 15 seconds: ${type.simpleName}")
        }

        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                try {
                    launch(ComponentActivity::class.java)
                    base.evaluate()
                } finally {
                    resumed(ComponentActivity::class.java)?.let { current ->
                        instrumentation.runOnMainSync { current.finish() }
                    }
                }
            }
        }
    }

    @Before fun prepareIsolatedFixture() {
        check(context.packageName.matches(Regex("org\\.example\\.aiphonekvm\\.audit(?:\\.debug)?"))) {
            "Refusing public screenshots outside the isolated audit application"
        }
        check(BuildConfig.UPDATE_ORIGIN.isBlank() || URI(BuildConfig.UPDATE_ORIGIN).host.orEmpty().endsWith(".invalid")) {
            "The audit build must use a disabled or reserved-invalid update origin"
        }
        output = File(context.getExternalFilesDir("public-screenshots") ?: error("No isolated external storage"), "controller")
        if (testName.methodName.startsWith("a_")) output.deleteRecursively()
        output.mkdirs()
        failures.clear()
        context.getSharedPreferences("vision_private", Context.MODE_PRIVATE).edit().clear()
            .putString("url", "https://model.example.invalid/v1").putString("model", "example-vision-model").commit()
        context.getSharedPreferences("website_binding_private", Context.MODE_PRIVATE).edit().clear()
            .putString("origin", "https://controller.example.invalid").commit()
        runtime = AutomationRuntime.get(context)
        runtime.running.value = false
        runtime.goalLoop.state.value = GoalTaskState()
        runtime.camera.stop(notify = false)
        runtime.selectCamera(false)
        runtime.capture.lastFrame.value = null
        runtime.vision.baseUrl.value = "https://model.example.invalid/v1"
        runtime.vision.model.value = "example-vision-model"
        runtime.vision.configured.value = false
        runtime.vision.working.value = false
        runtime.vision.result.value = ""
        runtime.vision.suggestion.value = null
        runtime.logs.value = listOf("UI TEST: synthetic record; no command or model request was sent")
        runtime.store.save(AutomationConfig(
            token = "UI_TEST_NON_SECRET_NOT_FOR_A_SERVICE",
            captureToken = "UI_TEST_NON_SECRET_CAPTURE_CODE",
            deviceId = "UI_TEST_EXAMPLE_DEVICE", topicPrefix = "ui-test/"
        ))
    }

    @After fun finishFixture() {
        if (::runtime.isInitialized) {
            instrumentation.runOnMainSync {
                runtime.running.value = false
                runtime.goalLoop.state.value = GoalTaskState()
                runtime.vision.working.value = false
                runtime.vision.configured.value = false
                runtime.camera.stop(notify = false)
                runtime.capture.lastFrame.value = null
            }
        }
        if (::output.isInitialized) {
            File(output, "README.txt").writeText("""
                UI TEST SCREENSHOTS / 示例界面截图
                Every image and state in this folder is an isolated UI fixture.
                It does not prove Bluetooth delivery, model quality, camera optical control,
                speech recognition, real pairing, update installation, or website enrollment.
                All report senders are inert. Service and model endpoints are reserved-invalid.
                See manifest.json for captured/failed states and explicit limitations.
            """.trimIndent())
        }
        assertTrue("Some screenshot sections failed; inspect manifest.json: ${failures.joinToString()}", failures.isEmpty())
    }

    private fun orient(landscape: Boolean) {
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(10_000) {
            host.resumed(ComponentActivity::class.java)?.resources?.configuration?.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        // The configuration changes before the system's physical rotation animation ends.
        SystemClock.sleep(800)
        instrumentation.waitForIdleSync()
    }

    private fun installHost() {
        compose.setContent {
            BtRemoteTheme(AppearanceSettings(theme = if (dark) ThemeEntity.DARK else ThemeEntity.LIGHT,
                useDynamicColors = false, useFullScreen = false)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Text(marker, Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.fillMaxWidth().weight(1f)) { key(revision) { scene() } }
                    }
                }
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.runOnIdle { scene = content; revision++ }
        compose.waitForIdle()
    }

    private fun hideKeyboard() {
        compose.runOnUiThread {
            val activity = compose.activity
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken, 0)
        }
        instrumentation.waitForIdleSync()
    }

    private fun record(id: String, status: String, file: String? = null, note: String = "") {
        val manifestFile = File(output, "manifest.json")
        val manifest = if (manifestFile.exists()) JSONObject(manifestFile.readText()) else JSONObject().apply {
            put("schemaVersion", 1)
            put("fixtureOnly", true)
            put("classification", "UI TEST — synthetic, non-secret demonstration data")
            put("notPhysicalAcceptance", JSONArray(listOf("Bluetooth delivery", "camera optics", "voice recognition", "real model output", "server enrollment", "game-console compatibility")))
            put("screenshots", JSONArray())
        }
        manifest.getJSONArray("screenshots").put(JSONObject().apply {
            put("id", id); put("status", status); put("fixture", true)
            put("method", testName.methodName)
            if (file != null) put("file", file)
            if (note.isNotBlank()) put("note", note)
        })
        manifestFile.writeText(manifest.toString(2))
    }

    private fun capture(id: String, note: String = "") {
        hideKeyboard()
        compose.waitForIdle()
        SystemClock.sleep(180)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Android screenshot returned no bitmap" }
        val file = File(output, "$id-UI_TEST.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        record(id, "captured", file.name, note)
    }

    private fun section(id: String, block: () -> Unit) {
        try { block() }
        catch (error: Throwable) {
            failures += id
            record(id, "failed", note = error.javaClass.simpleName + ": " + error.message.orEmpty().take(180))
        }
    }

    /** Capture successive actual scroll positions rather than only the top of a long page. */
    private fun captureLong(id: String, limit: Int = 12) {
        capture("$id-01")
        for (page in 2..limit) {
            val candidates = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            // Dialog/bottom-sheet roots are registered after the underlying page.
            val node = candidates.lastOrNull { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) } ?: return
            val before = node.config[SemanticsProperties.VerticalScrollAxisRange].value()
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            if (before >= range.maxValue()) return
            val match = SemanticsMatcher("scrolling node ${node.id}") { it.id == node.id }
            compose.onNode(match).performTouchInput { swipeUp() }
            compose.waitForIdle()
            val after = compose.onNode(match).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            if (after <= before + 0.001f) return
            capture("$id-${page.toString().padStart(2, '0')}")
        }
        record(id, "coverage_limit", note = "Scroll screenshot limit reached; review whether additional content remains")
    }

    private fun clickText(text: String, substring: Boolean = false, scroll: Boolean = false) {
        val node = compose.onAllNodesWithText(text, substring = substring).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun fixtureDevice() = DeviceEntity(exampleName, exampleAddress, BluetoothClass.Device.Major.PHONE)

    @Composable private fun FixtureHeader() {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BluetoothPairingCard("UI TEST Controller", true, DeviceHidConnectionState(),
                BluetoothPairingState(false, false, null) {})
            OutlinedButton(onClick = {}, Modifier.fillMaxWidth()) { Text("搜索新设备") }
            Text("点选已配对设备后返回首页，再选择功能开始控制", style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable private fun FixtureDevicePage(populated: Boolean) {
        StatelessDevicesSelectionScreen(
            snackbarHostState = remember { SnackbarHostState() }, isBluetoothServiceRunning = true,
            connectDevice = {}, favoriteDevices = emptyList(),
            nonFavoriteDevices = if (populated) listOf(fixtureDevice()) else emptyList(),
            saveFavoriteDevice = {}, autoConnectDeviceAddress = "", onDeviceToAutoConnectChanged = {},
            onDeviceToUnpairChanged = {}, topBarActions = {
                TopBarActions(navigateToDeviceDiscoveryScreen = {}, navigateToDistantDevicePairScreen = {},
                    navigateToSettings = {}, onShowBluetoothAddressDialogChanged = {},
                    showHelpBottomSheet = false, onShowHelpBottomSheetChanged = {}, deviceManagement = true)
            }, overlayView = {},
            modeHeader = { FixtureHeader() }, selectedAddress = if (populated) exampleAddress else "", navigateUp = {}
        )
    }

    @Composable private fun FixtureScan(discovering: Boolean, populated: Boolean) {
        StatelessBluetoothScanningScreen(discovering, if (populated) listOf(fixtureDevice()) else emptyList(),
            DeviceHidConnectionState(), {}, {}, {}, {}, {}, true, false, {})
    }

    @Composable private fun FixtureTv(minimal: Boolean, mouse: Boolean = false) {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        StatelessRemoteScreen(landscape, ControlMode.ORIGINAL, {}, originalActions = {
                TopBarActions(navigateToSettings = {}, disconnectDevice = {},
                    navigationToggle = if (mouse) NavigationToggle.MOUSE else NavigationToggle.DIRECTION,
                    onNavigationToggleChanged = {}, useAdvancedKeyboardIntegrated = false,
                    isKeyboardVisible = false, onKeyboardVisibleChanged = {},
                    isMoreButtonsVisible = false, onMoreButtonsVisibleChanged = {},
                    isHelpBottomSheetVisible = false, onHelpBottomSheetVisibleChanged = {})
            }, remoteLayout = { RemoteLayout(minimal, false, {}, KeyboardLanguage.ENGLISH_US, noKeyboard, noKeyboard) },
            navigationLayout = { NavigationLayout(RemoteSettings(), noKeyboard, noKeyboard, noMouse,
                if (mouse) NavigationToggle.MOUSE else NavigationToggle.DIRECTION) }, overlayView = {})
    }

    @Test fun a_portraitPagesAndDialogs() {
        orient(false); installHost()
        section("C01-permissions") {
            show { BluetoothPermissionsScreen(emptyArray(), false, {}, {}) }; capture("C01-permissions")
        }
        section("C02-bluetooth-disabled") {
            show { ActivationView(stringResource(R.string.activation), AppIcons.BluetoothDisabled,
                stringResource(R.string.bluetooth_disabled_info), stringResource(R.string.bluetooth_disabled_message),
                AppIcons.Bluetooth, stringResource(R.string.bluetooth_enabled_button), {}, false, {}) }
            capture("C02-bluetooth-disabled", "Production ActivationView with Bluetooth-disabled fixture; no Bluetooth switch is touched")
        }
        section("C03-bluetooth-unsupported") { show { BluetoothNotSupportScreen() }; capture("C03-bluetooth-unsupported") }
        section("C04-home-empty") {
            show { ControlHomeScreen(null, "", true, {}, {}, {}, {}) }; capture("C04-home-empty")
        }
        section("C05-home-selected") {
            show { ControlHomeScreen(exampleName, exampleAddress, true, {}, {}, {}, {}, connectionStatus = "未连接") }
            capture("C05-home-selected")
            compose.onNodeWithContentDescription("编辑图标").performClick(); capture("C07-home-editing")
            compose.onNodeWithContentDescription("完成编辑").performClick()
            compose.onNodeWithContentDescription("帮助").performClick(); capture("C08-home-help")
        }
        section("C06-home-different-connection") {
            show { ControlHomeScreen(exampleName, exampleAddress, true, {}, {}, {}, {}, connectionStatus = "已连接：UI TEST Other host") }
            capture("C06-home-different-connection", "Two distinct synthetic identities, not a real connection")
        }
        section("C09-device-empty") { show { FixtureDevicePage(false) }; captureLong("C09-device-empty") }
        section("C10-device-paired") { show { FixtureDevicePage(true) }; captureLong("C10-device-paired") }
        section("C11-device-advanced-menu") {
            show { FixtureDevicePage(true) }
            compose.onNodeWithContentDescription("更多设备选项").performClick(); capture("C11-device-advanced-menu")
        }
        section("C13-device-item-menu") {
            show { FixtureDevicePage(true) }
            compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick(); capture("C13-device-item-menu")
        }
        section("C12-manual-address") { show { BluetoothAddressDialog({}, {}) }; capture("C12-manual-address") }
        section("C14-auto-connect") { show { AutoConnectDeviceDialog(exampleName, exampleAddress, "", {}, {}) }; capture("C14-auto-connect") }
        section("C15-unpair-confirmation") { show { UnpairDeviceDialog(exampleName, {}, {}) }; capture("C15-unpair-confirmation") }
        section("C16-device-scanning") { show { FixtureScan(true, false) }; capture("C16-device-scanning") }
        section("C16-device-search-empty") { show { FixtureScan(false, false) }; capture("C16-device-search-empty") }
        section("C16-device-search-results") { show { FixtureScan(false, true) }; capture("C16-device-search-results") }
        section("C17-pairing-guide") {
            show { DistantDevicePairScreen("UI TEST Controller", {}) }
            for (page in 1..5) {
                captureLong("C17-pairing-guide-$page", 3)
                if (page < 5) clickText(context.getString(R.string.next))
            }
        }
        section("C23-connecting") {
            show { LoadingDialog("UI TEST · 连接等待", "示例状态，不会建立真实连接", "取消", {}) }
            capture("C23-connecting")
        }
        section("C23-entry-error") {
            show { SimpleDialog("知道了", "选择设备", {}, {}, "暂时无法进入", "UI TEST：示例连接失败，不表示当前真机故障") }
            capture("C23-entry-error")
        }
        section("C24-settings-all-sections") { show { SettingsScreen({}, {}) }; captureLong("C24-settings") }
        for ((id, resource) in listOf("C28-theme" to R.string.theme,
            "C29-volume-up" to R.string.assign_action_volume_up_button,
            "C29-volume-down" to R.string.assign_action_volume_down_button,
            "C30-keyboard-language" to R.string.keyboard_language)) section(id) {
            show { SettingsScreen({}, {}) }
            clickText(context.getString(resource), scroll = true)
            captureLong(id, 6)
        }
        section("C31-libraries") { show { ThirdLibrariesScreen({}) }; captureLong("C31-libraries", 18) }
        section("C32-update") { show { AppUpdateDialog({}) }; capture("C32-update", "Reserved-invalid or disabled update source; no installation is attempted") }
        section("M01-compact-keyboard") {
            show { EnglishKeyboardPanel(noKeyboard, RemoteSettings(), noMouse, Modifier.fillMaxSize()) }
            capture("M01-compact-letters")
            compose.onNodeWithContentDescription("⇧").performClick(); capture("M02-compact-shift")
            compose.onNodeWithContentDescription("123").performClick(); capture("M03-compact-numbers")
            compose.onNodeWithContentDescription("#+=").performClick(); capture("M04-compact-symbols")
            compose.onNodeWithContentDescription("功能").performClick(); capture("M05-compact-functions")
        }
        section("M07-media") { show { MediaControlPanel(noKeyboard, Modifier.fillMaxSize()) }; captureLong("M07-media") }
        section("M08-mouse") { show { TouchpadControlPanel(RemoteSettings(), noMouse, Modifier.fillMaxSize()) }; capture("M08-mouse") }
        section("M09-tv-complete") { show { FixtureTv(false) }; capture("M09-tv-complete") }
        section("M09-tv-overflow") {
            show { FixtureTv(false) }
            compose.onNodeWithContentDescription(context.getString(R.string.more)).performClick(); capture("M09-tv-overflow")
        }
        section("M10-tv-minimal") { show { FixtureTv(true) }; capture("M10-tv-minimal") }
        section("M11-tv-mouse") { show { FixtureTv(false, true) }; capture("M11-tv-mouse-navigation") }
        section("M12-tv-standard-keyboard") {
            show { VirtualKeyboardModalBottomSheet(true, noKeyboard, { _, _ -> }, {}, EnglishUSVirtualKeyboardLayout()) }
            capture("M12-tv-standard-keyboard")
        }
        section("M13-tv-advanced-keyboard") {
            show { AdvancedKeyboardModalBottomSheet(KeyboardLanguage.ENGLISH_US, noKeyboard, {}) }
            capture("M13-tv-advanced-keyboard")
        }
        section("M14-tv-more-buttons") { show { MoreButtonsDialog(noKeyboard, noKeyboard, {}) }; captureLong("M14-tv-more-buttons", 5) }
        section("M15-tv-channels") { show { TVChannelDialog(noKeyboard, noKeyboard, {}) }; capture("M15-tv-channels") }
        section("M18-tv-help") { show { RemoteScreenHelpModalBottomSheet({}) }; captureLong("M18-tv-help", 10) }
        section("A01-task") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; captureLong("A01-task-default", 4)
            clickText("运行限制", substring = true, scroll = true); captureLong("A02-task-limits", 4)
            clickText("单步操作", scroll = true); captureLong("A04-single-step", 4)
        }
        section("A03-frame-preview") {
            val frame = fixtureFrame()
            compose.runOnIdle { runtime.capture.lastFrame.value = frame }
            show { AutomationScreen(runtime, {}, exampleAddress) }; captureLong("A03-fixture-preview", 4)
            compose.runOnIdle { runtime.capture.lastFrame.value = null }
        }
        for (status in listOf(GoalTaskStatus.RUNNING, GoalTaskStatus.STOPPING, GoalTaskStatus.COMPLETED,
            GoalTaskStatus.BLOCKED, GoalTaskStatus.FAILED, GoalTaskStatus.CANCELLED)) section("A05-task-${status.name.lowercase()}") {
            compose.runOnIdle {
                runtime.running.value = status.activeFixture()
                runtime.goalLoop.state.value = GoalTaskState(status, "UI TEST：示例任务", 2, 3,
                    summary = "UI TEST：用于核对布局的示例状态", message = "没有启动任务、调用模型或发送操作")
            }
            show { AutomationScreen(runtime, {}, exampleAddress) }
            captureLong("A05-task-${status.name.lowercase()}", 3)
            compose.runOnIdle { runtime.running.value = false; runtime.goalLoop.state.value = GoalTaskState() }
        }
        section("A08-companion-device-tab") {
            compose.runOnIdle { runtime.selectCamera(false) }
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("设备")
            captureLong("A08-companion-device-tab", 4)
            clickText("蓝牙鼠标高级校准", scroll = true); captureLong("A09-coordinate-calibration", 4)
        }
        section("A10-camera-closed") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("设备"); clickText("主控摄像头")
            captureLong("A10-camera-closed", 4)
        }
        section("A11-camera-fixture-and-reopen-regression") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("设备")
            if (compose.onAllNodesWithText("开启摄像头").fetchSemanticsNodes().isEmpty()) clickText("主控摄像头")
            val frame = fixtureFrame()
            compose.runOnIdle {
                runtime.camera.open.value = true
                runtime.camera.raw.value = CameraObservation.Raw(frame.bytes, frame.width, frame.height, 1, SystemClock.elapsedRealtime(), 0)
                runtime.camera.message.value = "UI TEST：合成测试屏幕，不是相机采集"
            }
            captureLong("A11-synthetic-camera-live", 3)
            clickText("框选屏幕", scroll = true)
            compose.onNodeWithContentDescription("相机屏幕框选").performScrollTo()
            compose.onNodeWithContentDescription("相机屏幕框选").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .2f, height * .5f)) }
            compose.onNodeWithContentDescription("相机屏幕框选").performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .8f, height * .5f)) }
            compose.onNodeWithText("已选2/4", substring = true).assertExists()
            capture("A12-synthetic-camera-two-corners", "Synthetic image and synthetic state; no CameraManager or HID sender was used")
            compose.runOnIdle { runtime.camera.open.value = false; runtime.camera.raw.value = null }
            compose.waitForIdle()
            compose.runOnIdle {
                runtime.camera.open.value = true
                runtime.camera.raw.value = CameraObservation.Raw(frame.bytes, frame.width, frame.height, 2, SystemClock.elapsedRealtime(), 0)
            }
            compose.onAllNodesWithText("已选2/4", substring = true).assertCountEquals(0)
            capture("A12-synthetic-camera-reopened", "Regression: previous two-corner draft must disappear after closing/reopening")
            compose.runOnIdle { runtime.camera.stop(notify = false); runtime.selectCamera(false) }
        }
        section("A14-settings-default") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("设置"); captureLong("A14-ai-settings", 8)
        }
        section("A16-mqtt-expanded") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("设置")
            clickText("MQTT高级设置", scroll = true); captureLong("A16-mqtt-expanded", 8)
        }
        section("A17-diagnostics-expanded") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("设置")
            clickText("调试与记录", scroll = true); captureLong("A17-diagnostics-expanded", 8)
        }
        section("A18-website-tab") {
            show { AutomationScreen(runtime, {}, exampleAddress) }; clickText("网站"); captureLong("A18-website-tab", 4)
        }
        section("D01-dark-home") {
            compose.runOnIdle { dark = true }
            show { ControlHomeScreen(exampleName, exampleAddress, true, {}, {}, {}, {}, connectionStatus = "未连接") }
            capture("D01-dark-home")
        }
        section("D02-dark-keyboard") {
            show { EnglishKeyboardPanel(noKeyboard, RemoteSettings(), noMouse, Modifier.fillMaxSize()) }; capture("D02-dark-keyboard")
        }
    }

    private fun GoalTaskStatus.activeFixture() = this == GoalTaskStatus.RUNNING || this == GoalTaskStatus.STOPPING

    private fun fixtureFrame(): CaptureHub.Frame {
        val bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(AndroidColor.rgb(232, 242, 255))
            drawText("UI TEST / SYNTHETIC SCREEN", 24f, 90f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(22, 50, 90); textSize = 30f })
            drawRect(40f, 150f, 600f, 260f, Paint().apply { color = AndroidColor.rgb(180, 220, 200) })
        }
        val bytes = ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); stream.toByteArray() }
        bitmap.recycle()
        return CaptureHub.Frame(bytes, 0, 0, 640, 320, source = "ui-test-fixture")
    }

    @Test fun b_landscapePages() {
        orient(true); installHost()
        section("C05-home-landscape") {
            show { ControlHomeScreen(exampleName, exampleAddress, true, {}, {}, {}, {}, connectionStatus = "未连接") }
            capture("C05-home-landscape", "Actual landscape Activity; no requested-orientation restoration claim is made")
            for (mode in HomeModuleOrder.defaults) {
                compose.onNodeWithTag("home-modules").performScrollToKey(mode.name)
                compose.onNodeWithContentDescription(mode.title).assertIsDisplayed()
            }
            capture("C05-home-landscape-after-scroll", "Every one of the eight module cards was individually scrolled into view and checked")
        }
        section("M06-landscape-keyboard") {
            show { EnglishKeyboardPanel(noKeyboard, RemoteSettings(), noMouse, Modifier.fillMaxSize()) }
            capture("M06-landscape-keyboard", "Actual audit Activity in landscape; report callbacks are inert")
        }
        section("M16-xbox") { show { GamepadControlPanel({ true }, Modifier.fillMaxSize()) }; capture("M16-xbox") }
        section("M17-fc") { show { GamepadControlPanel({ true }, Modifier.fillMaxSize(), fc = true) }; capture("M17-fc") }
        section("M09-tv-landscape") { show { FixtureTv(false) }; capture("M09-tv-landscape") }
        section("M13-advanced-landscape") {
            show { AdvancedKeyboardModalBottomSheet(KeyboardLanguage.ENGLISH_US, noKeyboard, {}) }; capture("M13-advanced-landscape")
        }
    }

    @Test fun c_websiteActivityDefault() {
        orient(false); installHost(); show { Text(marker) }
        section("A19-website-activity") {
            val activity = host.launch(WebsitePairingActivity::class.java)
            try {
                instrumentation.runOnMainSync {
                    (activity.window.decorView as ViewGroup).addView(TextView(activity).apply {
                        text = marker; textSize = 11f; setTextColor(AndroidColor.rgb(20, 50, 90))
                        setBackgroundColor(AndroidColor.rgb(235, 242, 255)); setPadding(8, 4, 8, 4)
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
                }
                instrumentation.waitForIdleSync(); SystemClock.sleep(350)
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                val file = File(output, "A19-website-activity-UI_TEST.png")
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
                record("A19-website-activity", "captured", file.name, "Actual website Activity using an invalid example origin; no scan/bind/network request")
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
            }
        }
        record("not-covered", "limitations", note = "System permission dialogs, real Bluetooth/pairing, voice service, camera optics/five-point calibration, model responses, website transaction states, update transfer/install, receiver helper and browser pages require separate execution; this suite is UI fixtures only")
    }
}
