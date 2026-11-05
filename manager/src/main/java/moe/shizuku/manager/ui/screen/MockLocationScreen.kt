package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.manage.MockCoordinates
import moe.shizuku.manager.manage.MockLocation

internal class MockLocationViewModel : ViewModel() {
    private val location = MockLocation()

    var latitude by mutableStateOf("")
    var longitude by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var outcome by mutableStateOf<MockLocation.Outcome?>(null)
        private set

    fun update(point: MockCoordinates?) {
        if (busy) return
        busy = true
        outcome = null
        viewModelScope.launch {
            try {
                outcome = location.update(point)
            } finally {
                busy = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MockLocationScreen(onBack: () -> Unit) {
    val model: MockLocationViewModel = viewModel()
    val point = MockCoordinates.parse(model.latitude, model.longitude)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.mock_location_title)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.mock_location_back))
                }
            },
        )
        Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.mock_location_description))
                CoordinateField(
                    label = stringResource(R.string.mock_location_latitude), value = model.latitude,
                    onValueChange = { model.latitude = it }, example = "-6.2", limit = 90,
                    enabled = !model.busy, imeAction = ImeAction.Next,
                )
                CoordinateField(
                    label = stringResource(R.string.mock_location_longitude), value = model.longitude,
                    onValueChange = { model.longitude = it }, example = "106.8", limit = 180,
                    enabled = !model.busy, imeAction = ImeAction.Done,
                )
                Button(
                    onClick = { point?.let(model::update) }, enabled = point != null && !model.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mock_location_apply)) }
                OutlinedButton(onClick = { model.update(null) }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.mock_location_restore))
                }
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.outcome?.let { LocationOutcome(it) }
            }
        }
    }
}

@Composable
private fun CoordinateField(
    label: String, value: String, onValueChange: (String) -> Unit, example: String,
    limit: Int, enabled: Boolean, imeAction: ImeAction,
) {
    val invalid = value.isNotBlank() && MockCoordinates.parseAxis(value, limit) == null
    OutlinedTextField(
        value = value, onValueChange = { if (it.length <= 48) onValueChange(it) },
        label = { Text(label) }, placeholder = { Text(example) },
        singleLine = true, enabled = enabled, isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(R.string.mock_location_range, -limit, limit)) }
        } else null,
        // Decimal keyboards can omit the minus key on some devices.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocationOutcome(outcome: MockLocation.Outcome) {
    val error = MaterialTheme.colorScheme.error
    when (val result = outcome.result) {
        MockLocation.Result.ServiceUnavailable, MockLocation.Result.AdbRequired ->
            Text(stringResource(R.string.mock_location_adb_required), color = error)
        MockLocation.Result.LocationDisabled -> Text(stringResource(R.string.mock_location_enable_location), color = error)
        MockLocation.Result.Restored -> Text(stringResource(R.string.mock_location_restored))
        is MockLocation.Result.Applied -> {
            if (result.unverified.isEmpty()) {
                Text(stringResource(R.string.mock_location_applied), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.mock_location_unverified, result.unverified.joinToString(", ")))
            }
            Text("${result.point.latitude}, ${result.point.longitude}", style = MaterialTheme.typography.bodyMedium)
        }
        is MockLocation.Result.Failed -> {
            Text(stringResource(R.string.mock_location_failed), color = error)
            Text(result.details, style = MaterialTheme.typography.bodySmall)
            if (result.providersMayRemain) Text(stringResource(R.string.mock_location_retry_restore), color = error)
        }
    }
    if (outcome.permissionRestoreFailed) Text(stringResource(R.string.mock_location_permission_restore_failed), color = error)
}
