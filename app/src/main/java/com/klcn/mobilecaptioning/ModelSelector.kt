package com.klcn.mobilecaptioning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class ModelGroup {
    Main,
    Experimental,
}

/**
 * Shared model selector used by Capture, Models and Settings.
 *
 * Keeping one selector prevents the selected model and the detail screen from
 * drifting apart. A coming-soon entry is visible for roadmap transparency but
 * can never be persisted or sent to the inference engine.
 */
@Composable
internal fun GroupedModelSelector(
    selectedModel: CaptionModelMode,
    enabled: Boolean,
    language: AppLanguage,
    onModelSelected: (CaptionModelMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showSelectedSummary: Boolean = true,
) {
    val vietnamese = language == AppLanguage.Vietnamese
    val selectedGroup = if (selectedModel in CaptionModelMode.experimentalModes) {
        ModelGroup.Experimental
    } else {
        ModelGroup.Main
    }
    var expandedGroup by remember(selectedModel) {
        mutableStateOf<ModelGroup?>(selectedGroup)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!compact) {
                Text(
                    text = if (vietnamese) {
                        "Chọn mô hình sinh chú thích"
                    } else {
                        "Select a captioning model"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (vietnamese) {
                        "Mở một nhóm, sau đó chọn mô hình. Thông số bên dưới tự cập nhật theo lựa chọn."
                    } else {
                        "Open a group and select a model. The details below update automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ModelGroupHeader(
                title = if (vietnamese) "MÔ HÌNH CHÍNH" else "PRIMARY MODELS",
                expanded = expandedGroup == ModelGroup.Main,
                enabled = enabled,
                onClick = {
                    expandedGroup = if (expandedGroup == ModelGroup.Main) {
                        null
                    } else {
                        ModelGroup.Main
                    }
                },
            )
            if (expandedGroup == ModelGroup.Main) {
                ModelGroupOptions(
                    models = CaptionModelMode.mainModes,
                    selectedModel = selectedModel,
                    enabled = enabled,
                    vietnamese = vietnamese,
                    onModelSelected = onModelSelected,
                    compact = compact,
                )
            }

            ModelGroupHeader(
                title = if (vietnamese) "MÔ HÌNH THỬ NGHIỆM" else "EXPERIMENTAL MODELS",
                expanded = expandedGroup == ModelGroup.Experimental,
                enabled = enabled,
                onClick = {
                    expandedGroup = if (expandedGroup == ModelGroup.Experimental) {
                        null
                    } else {
                        ModelGroup.Experimental
                    }
                },
            )
            if (expandedGroup == ModelGroup.Experimental) {
                ModelGroupOptions(
                    models = CaptionModelMode.experimentalModes,
                    selectedModel = selectedModel,
                    enabled = enabled,
                    vietnamese = vietnamese,
                    onModelSelected = onModelSelected,
                    compact = compact,
                )
            }

            if (showSelectedSummary) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectedModelSummary(
                    model = selectedModel,
                    vietnamese = vietnamese,
                )
            }
        }
    }
}

@Composable
private fun ModelGroupHeader(
    title: String,
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = if (expanded) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (expanded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelGroupOptions(
    models: List<CaptionModelMode>,
    selectedModel: CaptionModelMode,
    enabled: Boolean,
    vietnamese: Boolean,
    onModelSelected: (CaptionModelMode) -> Unit,
    compact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
            val optionEnabled = enabled && !model.comingSoon
            val selected = model == selectedModel
            val shape = RoundedCornerShape(14.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (optionEnabled) 1f else 0.62f)
                    .clickable(
                        enabled = optionEnabled,
                        onClick = { onModelSelected(model) },
                    ),
                shape = shape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = if (compact) 8.dp else 10.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = optionEnabled,
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${model.kdMethod(vietnamese)} · ${model.datasetName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (model.comingSoon) {
                        Text(
                            text = "COMING SOON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (selected) {
                        Text(
                            text = if (vietnamese) "ĐANG DÙNG" else "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedModelSummary(
    model: CaptionModelMode,
    vietnamese: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = if (vietnamese) "Mô hình đang dùng" else "Active model",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${model.studentName} · ${model.runtimePrecision}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val metric = model.validationCiderD?.let {
            "CIDEr-D ${"%.2f".format(it * 100.0)}"
        }
        val size = model.studentInt8PackageMb?.let {
            "${"%.2f".format(it)} MB"
        }
        listOfNotNull(metric, size)
            .takeIf { it.isNotEmpty() }
            ?.let { values ->
                Text(
                    text = values.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
    }
}
