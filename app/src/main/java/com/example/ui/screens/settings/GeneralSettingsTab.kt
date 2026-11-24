package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.ExpressiveSectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.generalSettingsItems(
    isDefaultLauncher: Boolean,
    segmentedColors: ListItemColors,
    onOpenDefaultHomeSettings: () -> Unit,
    onLaunchExportBackup: () -> Unit,
    onLaunchImportBackup: () -> Unit,
) {
    // Default Launcher
    item(key = "default_launcher") {
        SegmentedListItem(
            onClick = onOpenDefaultHomeSettings,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = segmentedColors,
            leadingContent = {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = null,
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(
                        if (isDefaultLauncher) R.string.settings_default_yes
                        else R.string.settings_default_no
                    ),
                    color = if (isDefaultLauncher) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                if (isDefaultLauncher) {
                    FilledTonalButton(
                        onClick = onOpenDefaultHomeSettings,
                        modifier = Modifier.testTag("set_default_launcher_button"),
                    ) {
                        Text(stringResource(R.string.settings_switch_default))
                    }
                } else {
                    Button(
                        onClick = onOpenDefaultHomeSettings,
                        modifier = Modifier.testTag("set_default_launcher_button"),
                    ) {
                        Text(stringResource(R.string.settings_switch_default))
                    }
                }
            },
            content = {
                Text(text = stringResource(R.string.settings_default_launcher))
            },
        )
    }

    // Backup & Restore Section
    item(key = "backup_section") {
        Column(modifier = Modifier.fillMaxWidth()) {
            ExpressiveSectionHeader(title = stringResource(R.string.settings_backup_section))
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedListItem(
                    onClick = onLaunchExportBackup,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    colors = segmentedColors,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_backup_export_desc))
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.testTag("settings_export_backup"),
                    content = {
                        Text(text = stringResource(R.string.settings_backup_export))
                    },
                )
                SegmentedListItem(
                    onClick = onLaunchImportBackup,
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    colors = segmentedColors,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_backup_import_desc))
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.testTag("settings_import_backup"),
                    content = {
                        Text(text = stringResource(R.string.settings_backup_import))
                    },
                )
            }
        }
    }
}
