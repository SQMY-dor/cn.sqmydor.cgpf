package cn.sqmydor.cgpf

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.libxposed.service.XposedService
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** Settings screen built with miuix: feature switches (saved instantly) and the proxy port. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            MiuixTheme(controller = controller) { SettingsScreen() }
        }
    }
}

private class Prefs(val p: SharedPreferences) {
    fun bool(key: String, def: Boolean) = runCatching { p.getBoolean(key, def) }.getOrDefault(def)
    fun port() = Config.sanitizePort(
        runCatching { p.getInt(Config.KEY_PROXY_PORT, Config.DEFAULT_PROXY_PORT) }
            .getOrDefault(Config.DEFAULT_PROXY_PORT)
    )
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var service by remember { mutableStateOf<XposedService?>(App.service()) }
    DisposableEffect(Unit) {
        val l = App.Listener { s -> context.mainExecutor.execute { service = s } }
        App.addListener(l)
        onDispose { App.removeListener(l) }
    }

    val prefs = remember(service) {
        service?.let { s -> runCatching { Prefs(s.getRemotePreferences(Config.GROUP)) }.getOrNull() }
    }
    val connected = prefs != null

    var probe by remember(prefs) {
        mutableStateOf(prefs?.bool(Config.KEY_PROBE_ENABLED, Config.DEFAULT_PROBE_ENABLED) ?: Config.DEFAULT_PROBE_ENABLED)
    }
    var hide by remember(prefs) {
        mutableStateOf(prefs?.bool(Config.KEY_HIDE_CN_FEATURES, Config.DEFAULT_HIDE_CN_FEATURES) ?: Config.DEFAULT_HIDE_CN_FEATURES)
    }
    var rkp by remember(prefs) {
        mutableStateOf(prefs?.bool(Config.KEY_RKP_RENEW, Config.DEFAULT_RKP_RENEW) ?: Config.DEFAULT_RKP_RENEW)
    }
    var rkpProxy by remember(prefs) {
        mutableStateOf(prefs?.bool(Config.KEY_RKP_PROXY, Config.DEFAULT_RKP_PROXY) ?: Config.DEFAULT_RKP_PROXY)
    }
    val portState = rememberTextFieldState(Config.DEFAULT_PROXY_PORT.toString())
    LaunchedEffect(prefs) {
        portState.setTextAndPlaceCursorAtEnd((prefs?.port() ?: Config.DEFAULT_PROXY_PORT).toString())
    }

    val errorFmt = stringResource(R.string.status_error)
    fun put(block: SharedPreferences.Editor.() -> Unit): Boolean = try {
        prefs!!.p.edit().apply(block).apply()
        true
    } catch (t: Throwable) {
        Toast.makeText(context, String.format(errorFmt, t.message.toString()), Toast.LENGTH_LONG).show()
        false
    }

    val savedMsg = stringResource(R.string.saved)
    val invalidMsg = stringResource(R.string.port_invalid)
    val statusText = service?.let {
        stringResource(R.string.status_active, it.frameworkName, it.frameworkVersion, it.apiVersion)
    } ?: stringResource(R.string.status_inactive)

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
        ) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 6.dp)) {
                    Text(text = statusText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            item {
                SmallTitle(text = stringResource(R.string.section_features))
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp), insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        checked = probe, enabled = connected,
                        title = stringResource(R.string.probe_fix),
                        summary = stringResource(R.string.probe_fix_desc),
                        onCheckedChange = { v -> if (put { putBoolean(Config.KEY_PROBE_ENABLED, v) }) probe = v },
                    )
                    SwitchPreference(
                        checked = hide, enabled = connected,
                        title = stringResource(R.string.hide_cn),
                        summary = stringResource(R.string.hide_cn_desc),
                        onCheckedChange = { v -> if (put { putBoolean(Config.KEY_HIDE_CN_FEATURES, v) }) hide = v },
                    )
                }
            }
            item {
                SmallTitle(text = stringResource(R.string.section_rkp))
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp), insideMargin = PaddingValues(0.dp)) {
                    SwitchPreference(
                        checked = rkp, enabled = connected,
                        title = stringResource(R.string.rkp_renew),
                        summary = stringResource(R.string.rkp_renew_desc),
                        onCheckedChange = { v -> if (put { putBoolean(Config.KEY_RKP_RENEW, v) }) rkp = v },
                    )
                    SwitchPreference(
                        checked = rkpProxy, enabled = connected && rkp,
                        title = stringResource(R.string.rkp_proxy),
                        summary = stringResource(R.string.rkp_proxy_desc),
                        onCheckedChange = { v -> if (put { putBoolean(Config.KEY_RKP_PROXY, v) }) rkpProxy = v },
                    )
                }
            }
            item {
                SmallTitle(text = stringResource(R.string.proxy_port))
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                    TextField(
                        state = portState,
                        modifier = Modifier.fillMaxWidth(),
                        label = Config.DEFAULT_PROXY_PORT.toString(),
                        enabled = connected,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        text = stringResource(R.string.proxy_port_desc),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.reset_default),
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            onClick = { portState.setTextAndPlaceCursorAtEnd(Config.DEFAULT_PROXY_PORT.toString()) },
                        )
                        TextButton(
                            text = stringResource(R.string.save),
                            modifier = Modifier.weight(1f),
                            enabled = connected,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                val port = portState.text.toString().trim().toIntOrNull() ?: -1
                                if (!Config.isValidPort(port)) {
                                    Toast.makeText(context, invalidMsg, Toast.LENGTH_SHORT).show()
                                } else if (put { putInt(Config.KEY_PROXY_PORT, port) }) {
                                    Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.apply_note),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.navigationBarsPadding().height(12.dp))
            }
        }
    }
}
