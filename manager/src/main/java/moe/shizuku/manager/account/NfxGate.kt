package moe.shizuku.manager.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.theme.ShizukuTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Composable
internal fun NfxGate(model: NfxSessionModel, tools: @Composable (() -> Unit) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val session = state.session
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, session?.token) {
        if (session == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                model.checkSession()
                delay(20_000)
            }
        }
    }

    if (session == null) {
        AccountSurface { LoginScreen(state, model::signIn) }
    } else {
        var showAccount by rememberSaveable(session.account.id) { mutableStateOf(session.account.isAdmin) }
        val savedTools = rememberSaveableStateHolder()
        if (!showAccount) {
            savedTools.SaveableStateProvider("tools") { tools { showAccount = true } }
        } else {
            BackHandler { showAccount = false }
            AccountSurface {
                if (session.account.isAdmin) {
                    AdminScreen(state, model) { showAccount = false }
                } else {
                    AccountScreen(session.account, model::logout) { showAccount = false }
                }
            }
        }
    }
}

@Composable
private fun AccountSurface(content: @Composable () -> Unit) {
    ShizukuTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.systemBarsPadding().imePadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                content()
            }
        }
    }
}

@Composable
private fun LoginScreen(state: NfxState, signIn: (String, String, Boolean) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = login.isNotBlank() && password.isNotEmpty() && !state.busy
    val send = { transfer: Boolean -> if (canSubmit) signIn(login, password, transfer) }

    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(30.dp))
        Image(painterResource(R.drawable.nfx_icon_mark), contentDescription = null,
            modifier = Modifier.size(86.dp).clip(RoundedCornerShape(22.dp)).background(colorResource(R.color.nfx_icon_background)))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.nfx_login_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = login, onValueChange = { login = it.take(40) }, enabled = !state.busy,
            label = { Text(stringResource(R.string.nfx_login_id)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(password, { password = it }, !state.busy) { send(false) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorText(state.error)
        state.deviceCode?.let { Text(stringResource(R.string.nfx_device_code, it), style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = { send(false) }, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.error == "TRANSFER_PENDING") R.string.nfx_check_transfer else R.string.nfx_sign_in))
        }
        if (state.error == "DEVICE_BOUND") {
            TextButton(onClick = { send(true) }, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nfx_request_transfer))
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, change: (String) -> Unit, enabled: Boolean, done: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { change(it.take(128)) }, enabled = enabled,
        label = { Text(stringResource(R.string.nfx_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { done() }), modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountScreen(account: NfxAccount, logout: () -> Unit, openTools: () -> Unit) {
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.nfx_account), style = MaterialTheme.typography.headlineMedium)
        Text(account.login, style = MaterialTheme.typography.titleLarge)
        Text(subscriptionLabel(account))
        account.deviceLabel?.let { Text(it) }
        account.deviceCode?.let { Text(stringResource(R.string.nfx_device_code, it)) }
        Button(onClick = openTools) { Text(stringResource(R.string.nfx_open_tools)) }
        TextButton(onClick = logout) { Text(stringResource(R.string.nfx_logout)) }
    }
}

@Composable
private fun AdminScreen(state: NfxState, model: NfxSessionModel, openTools: () -> Unit) {
    var addUser by rememberSaveable { mutableStateOf(false) }
    var grantAccountId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedTransfer by remember { mutableStateOf<NfxTransfer?>(null) }
    val data = state.adminData
    LaunchedEffect(Unit) { model.refreshAdmin() }
    LazyColumn(
        Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.nfx_admin), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.nfx_admin_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addUser = true }, enabled = !state.busy) { Text(stringResource(R.string.nfx_create_user)) }
                    TextButton(onClick = model::refreshAdmin, enabled = !state.busy) { Text(stringResource(R.string.nfx_refresh)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = openTools, enabled = !state.busy) { Text(stringResource(R.string.nfx_open_tools)) }
                    TextButton(onClick = model::logout, enabled = !state.busy) { Text(stringResource(R.string.nfx_logout)) }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                ErrorText(state.error)
            }
        }
        if (data != null) {
            item { Text(stringResource(R.string.nfx_transfers), style = MaterialTheme.typography.titleMedium) }
            if (data.transfers.isEmpty()) item { Text(stringResource(R.string.nfx_no_transfers)) }
            items(data.transfers, key = { "transfer-${it.id}" }) { transfer ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(transfer.login, style = MaterialTheme.typography.titleMedium)
                        Text(transfer.deviceLabel)
                        Text(stringResource(R.string.nfx_device_code, transfer.deviceCode))
                        TextButton(onClick = { selectedTransfer = transfer }, enabled = !state.busy) {
                            Text(stringResource(R.string.nfx_review_transfer))
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Text(stringResource(R.string.nfx_users), modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            }
            if (data.users.isEmpty()) item { Text(stringResource(R.string.nfx_no_users)) }
            items(data.users, key = { "user-${it.id}" }) { user ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.login, style = MaterialTheme.typography.titleMedium)
                        Text(subscriptionLabel(user))
                        user.deviceLabel?.let { Text(it) }
                        TextButton(onClick = { grantAccountId = user.id }, enabled = !state.busy) {
                            Text(stringResource(R.string.nfx_add_subscription))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (addUser) CreateUserDialog({ addUser = false }) { login, password ->
        addUser = false
        model.createUser(login, password)
    }
    data?.users?.firstOrNull { it.id == grantAccountId }?.let { account ->
        SubscriptionDialog(account, state, { grantAccountId = null }) { days, requestId ->
            model.grant(account.id, days, requestId)
        }
    }
    selectedTransfer?.let { transfer ->
        AlertDialog(
            onDismissRequest = { selectedTransfer = null },
            title = { Text(stringResource(R.string.nfx_review_transfer)) },
            text = { Text(stringResource(R.string.nfx_transfer_confirm, transfer.login, transfer.deviceCode)) },
            confirmButton = {
                TextButton(onClick = { selectedTransfer = null; model.decide(transfer.id, true) }) {
                    Text(stringResource(R.string.nfx_approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedTransfer = null; model.decide(transfer.id, false) }) {
                    Text(stringResource(R.string.nfx_reject))
                }
            },
        )
    }
}

@Composable
private fun CreateUserDialog(close: () -> Unit, create: (String, String) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = login.trim().count(Char::isDigit) in 8..15 && password.length >= 8
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.nfx_create_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(login, { login = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.nfx_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                PasswordField(password, { password = it }, true) { if (valid) create(login, password) }
                Text(stringResource(R.string.nfx_password_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { create(login, password) }, enabled = valid) { Text(stringResource(R.string.nfx_create_user)) } },
        dismissButton = { TextButton(onClick = close) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun SubscriptionDialog(account: NfxAccount, state: NfxState, close: () -> Unit, grant: (Int, String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("30") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val days = input.toIntOrNull()?.takeIf { it in 1..3650 }
    LaunchedEffect(state.completedGrantId) { if (state.completedGrantId == requestId) close() }
    AlertDialog(
        onDismissRequest = { if (!state.busy) close() },
        title = { Text(stringResource(R.string.nfx_add_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(account.login)
                OutlinedTextField(input, { input = it.filter(Char::isDigit).take(4) }, singleLine = true, enabled = !attempted,
                    label = { Text(stringResource(R.string.nfx_days)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text(stringResource(R.string.nfx_subscription_hint), style = MaterialTheme.typography.bodySmall)
                if (attempted) ErrorText(state.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { days?.let { attempted = true; grant(it, requestId) } }, enabled = days != null && !state.busy) {
                Text(stringResource(R.string.nfx_add_subscription))
            }
        },
        dismissButton = { TextButton(onClick = close, enabled = !state.busy) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun subscriptionLabel(account: NfxAccount): String {
    val expiry = account.subscriptionUntil ?: return stringResource(R.string.nfx_no_subscription)
    val formatted = remember(expiry) {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        runCatching { parser.parse(expiry)?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) } }
            .getOrNull() ?: expiry
    }
    return stringResource(R.string.nfx_subscription_until, formatted)
}

@Composable
private fun ErrorText(code: String?) {
    if (code == null) return
    val message = when (code) {
        "DEVICE_BOUND" -> R.string.nfx_device_bound
        "TRANSFER_PENDING" -> R.string.nfx_transfer_pending
        "INVALID_CREDENTIALS" -> R.string.nfx_invalid_credentials
        "INVALID_INPUT" -> R.string.nfx_invalid_input
        "SUBSCRIPTION_EXPIRED" -> R.string.nfx_subscription_expired
        "SESSION_EXPIRED", "INVALID_DEVICE_PROOF" -> R.string.nfx_session_expired
        "ACCOUNT_EXISTS" -> R.string.nfx_account_exists
        "TRANSFER_NOT_FOUND" -> R.string.nfx_transfer_not_found
        "TRY_LATER" -> R.string.nfx_try_later
        "CONNECTION" -> R.string.nfx_connection_error
        else -> R.string.nfx_server_error
    }
    Text(stringResource(message), color = if (code == "TRANSFER_PENDING") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
}
