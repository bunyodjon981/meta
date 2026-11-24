package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LogLevel
import com.example.data.model.TerminalLogLine
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalHeader
import com.example.ui.theme.TerminalTagBuild
import com.example.ui.theme.TerminalTagError
import com.example.ui.theme.TerminalTagGit
import com.example.ui.theme.TerminalTagInfo
import com.example.ui.theme.TerminalTagSuccess
import com.example.ui.theme.TerminalTagWarn
import com.example.ui.theme.TerminalTextDefault
import com.example.ui.theme.TerminalTextMuted

@Composable
fun RealTimeTerminal(
    logs: List<TerminalLogLine>,
    onClearLogs: () -> Unit,
    onCopiedFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TerminalBackground)
            .border(1.dp, ObsidianOutline, RoundedCornerShape(18.dp))
            .testTag("realtime_terminal")
    ) {
        // Terminal Top Chrome Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalHeader)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = TerminalTagSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.terminal_title),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TerminalTextDefault,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val fullText = logs.joinToString("\n") {
                            "[${it.timestamp}] [${it.level.tag}] ${it.message}"
                        }
                        clipboard.setText(AnnotatedString(fullText))
                        onCopiedFeedback()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("copy_terminal_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Terminal loglarini nusxalash",
                        tint = TerminalTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("clear_terminal_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Terminalni tozalash",
                        tint = TerminalTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Terminal Scrollable Output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp, max = 280.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(logs, key = { it.id }) { line ->
                val tagColor = when (line.level) {
                    LogLevel.INFO -> TerminalTagInfo
                    LogLevel.GIT -> TerminalTagGit
                    LogLevel.BUILD -> TerminalTagBuild
                    LogLevel.SUCCESS -> TerminalTagSuccess
                    LogLevel.WARN -> TerminalTagWarn
                    LogLevel.ERROR -> TerminalTagError
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TerminalTextMuted)) {
                            append("${line.timestamp} ")
                        }
                        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold)) {
                            append("[${line.level.tag}] ")
                        }
                        withStyle(
                            SpanStyle(
                                color = if (line.level == LogLevel.ERROR) TerminalTagError else TerminalTextDefault
                            )
                        ) {
                            append(line.message)
                        }
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}
