package com.ayakasir.app.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "")
    )

    // Responsive sizing
    val buttonHeight = if (isCompact) 56.dp else 64.dp
    val buttonSpacing = if (isCompact) 6.dp else 8.dp
    val fontSize = if (isCompact) 20.sp else 24.sp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(buttonSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.CenterHorizontally)
            ) {
                row.forEach { digit ->
                    when {
                        digit.isNotEmpty() -> {
                            FilledTonalButton(
                                onClick = { onDigitClick(digit) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(buttonHeight),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = digit,
                                    fontSize = fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        row.indexOf(digit) == 0 -> {
                            // Empty space left of 0
                            IconButton(
                                onClick = { },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(buttonHeight),
                                enabled = false
                            ) { }
                        }
                        else -> {
                            // Backspace right of 0
                            IconButton(
                                onClick = onBackspace,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(buttonHeight)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Hapus"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
