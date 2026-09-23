/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.screens.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.BuildConfig
import org.connectbot.R
import org.connectbot.data.entity.ColorScheme
import org.connectbot.ui.common.getLocalizedColorSchemeDescription
import org.connectbot.ui.common.getLocalizedFontDisplayName
import org.connectbot.ui.common.InputFieldShape
import org.connectbot.ui.components.FontDownloadProgressDialog
import org.connectbot.ui.components.SaveEditorFab
import org.connectbot.util.LocalFontProvider
import org.connectbot.util.TerminalFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToColors: () -> Unit = {},
    viewModel: ProfileEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveError) {
        uiState.saveError?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    if (uiState.fontDownloadInProgress) {
        FontDownloadProgressDialog()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.profileId == -1L) {
                            stringResource(R.string.profile_editor_title_new)
                        } else {
                            stringResource(R.string.profile_editor_title_edit)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            SaveEditorFab(
                visible = !uiState.isLoading && uiState.hasUnsavedChanges && uiState.name.isNotBlank(),
                isSaving = uiState.isSaving,
                contentDescription = stringResource(R.string.profile_editor_save),
                onClick = { viewModel.save(onNavigateBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Profile Name
                OutlinedTextField(
                    shape = InputFieldShape,
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text(stringResource(R.string.profile_editor_name_label)) },
                    singleLine = true,
                    isError = uiState.saveError != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                ColorSchemeSelector(
                    colorSchemeId = uiState.colorSchemeId,
                    availableSchemes = uiState.availableColorSchemes,
                    onSelectColorScheme = { viewModel.updateColorSchemeId(it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )

                Spacer(modifier = Modifier.height(2.dp))

                FontFamilySelector(
                    fontFamily = uiState.fontFamily,
                    customFonts = uiState.customFonts,
                    localFonts = uiState.localFonts,
                    onSelectFontFamily = { viewModel.updateFontFamily(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                FontSizeSelector(
                    fontSize = uiState.fontSize,
                    onFontSizeChange = { viewModel.updateFontSize(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(2.dp))

                EmulationSelector(
                    emulation = uiState.emulation,
                    customTerminalTypes = uiState.customTerminalTypes,
                    onSelectEmulation = { viewModel.updateEmulation(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                DelKeySelector(
                    delKey = uiState.delKey,
                    onSelectDelKey = { viewModel.updateDelKey(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                InlineImagesSelector(
                    inlineImages = uiState.inlineImages,
                    onSelectInlineImages = viewModel::updateInlineImages,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                EncodingSelector(
                    encoding = uiState.encoding,
                    commonEncodings = viewModel.commonEncodings,
                    allEncodings = viewModel.allEncodings,
                    onSelectEncoding = { viewModel.updateEncoding(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                ForceSizeSelector(
                    enabled = uiState.forceSizeEnabled,
                    rows = uiState.forceSizeRows,
                    columns = uiState.forceSizeColumns,
                    onEnabledChange = { viewModel.updateForceSizeEnabled(it) },
                    onRowsChange = { viewModel.updateForceSizeRows(it) },
                    onColumnsChange = { viewModel.updateForceSizeColumns(it) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilySelector(
    fontFamily: String?,
    customFonts: List<String>,
    localFonts: List<Pair<String, String>>,
    onSelectFontFamily: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    // Build options: System Default + preset fonts (if available) + custom fonts + local fonts
    val presetOptions = if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
        TerminalFont.entries.map { font ->
            val displayName = getLocalizedFontDisplayName(font.name)
            displayName to font.name
        }
    } else {
        listOf(getLocalizedFontDisplayName(TerminalFont.SYSTEM_DEFAULT.name) to TerminalFont.SYSTEM_DEFAULT.name)
    }
    val customOptions = if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
        customFonts.map { it to TerminalFont.createCustomFontValue(it) }
    } else {
        emptyList()
    }
    val localOptions = localFonts.map { (displayName, fileName) ->
        displayName to LocalFontProvider.createLocalFontValue(fileName)
    }
    val allOptions = presetOptions + customOptions + localOptions

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        OutlinedTextField(
            shape = InputFieldShape,
            value = getLocalizedFontDisplayName(fontFamily),
            onValueChange = {},
            label = { Text(stringResource(R.string.profile_editor_font_family_title)) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = InputFieldShape,
        ) {
                allOptions.forEach { (displayName, value) ->
                    DropdownMenuItem(
                        text = { Text(displayName) },
                        onClick = {
                            onSelectFontFamily(value)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
    }
}

@Composable
private fun FontSizeSelector(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_editor_font_size_title, fontSize),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Slider(
            value = fontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.toInt()) },
            valueRange = 6f..30f,
            steps = 23,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmulationSelector(
    emulation: String,
    customTerminalTypes: List<String>,
    onSelectEmulation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val presetOptions = listOf(
        "xterm-256color",
        "xterm",
        "vt100",
        "vt102",
        "vt220",
        "ansi",
        "screen",
        "screen-256color",
        "linux",
        "dumb",
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        OutlinedTextField(
            shape = InputFieldShape,
            value = emulation,
            onValueChange = {},
            label = { Text(stringResource(R.string.pref_emulation_title)) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = InputFieldShape,
        ) {
                presetOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelectEmulation(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
                if (customTerminalTypes.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    customTerminalTypes.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = {
                                onSelectEmulation(option)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelKeySelector(
    delKey: String,
    onSelectDelKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("del", "backspace")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        OutlinedTextField(
            shape = InputFieldShape,
            value = delKey,
            onValueChange = {},
            label = { Text(stringResource(R.string.hostpref_delkey_title)) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = InputFieldShape,
        ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelectDelKey(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InlineImagesSelector(
    inlineImages: String,
    onSelectInlineImages: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = mapOf(
        "off" to stringResource(R.string.inline_images_off),
        "ask" to stringResource(R.string.inline_images_ask),
        "on" to stringResource(R.string.inline_images_on),
    )

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.profile_inline_images),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = options[inlineImages] ?: options.getValue("ask"),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelectInlineImages(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncodingSelector(
    encoding: String,
    commonEncodings: List<String>,
    allEncodings: List<String>,
    onSelectEncoding: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        OutlinedTextField(
            shape = InputFieldShape,
            value = encoding,
            onValueChange = {},
            label = { Text(stringResource(R.string.hostpref_encoding_title)) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = InputFieldShape,
        ) {
                commonEncodings.forEach { enc ->
                    DropdownMenuItem(
                        text = { Text(enc) },
                        onClick = {
                            onSelectEncoding(enc)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSchemeSelector(
    colorSchemeId: Long,
    availableSchemes: List<ColorScheme>,
    onSelectColorScheme: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                shape = InputFieldShape,
                value = availableSchemes.find { it.id == colorSchemeId }?.name ?: stringResource(R.string.colorscheme_default),
                onValueChange = {},
                label = { Text(stringResource(R.string.profile_editor_section_color_scheme)) },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = InputFieldShape,
            ) {
                availableSchemes.forEach { scheme ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(scheme.name)
                                val localizedDescription = getLocalizedColorSchemeDescription(scheme)
                                if (localizedDescription.isNotBlank()) {
                                    Text(
                                        text = localizedDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectColorScheme(scheme.id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForceSizeSelector(
    enabled: Boolean,
    rows: Int,
    columns: Int,
    onEnabledChange: (Boolean) -> Unit,
    onRowsChange: (Int) -> Unit,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 2.dp)) {
        Text(
            text = stringResource(R.string.profile_editor_force_size_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.profile_editor_force_size_enable),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    shape = InputFieldShape,
                    value = columns.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onColumnsChange(it) }
                    },
                    label = { Text(stringResource(R.string.profile_editor_force_size_columns)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedTextField(
                    shape = InputFieldShape,
                    value = rows.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { onRowsChange(it) }
                    },
                    label = { Text(stringResource(R.string.profile_editor_force_size_rows)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
