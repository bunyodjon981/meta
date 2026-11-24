package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BuildPhase
import com.example.data.model.BuildSessionState
import com.example.domain.GitHubBuildCoordinator
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeEmeraldContainer
import com.example.ui.theme.ForgeError
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurfaceVariant

@Composable
fun AnimatedBuildStatusCard(
    sessionState: BuildSessionState,
    onDownloadReadyApk: () -> Unit,
    onShareReadyApk: () -> Unit,
    onOpenWorkflowUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = sessionState.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "buildProgress"
    )

    val accentColor by animateColorAsState(
        targetValue = when (sessionState.phase) {
            BuildPhase.READY -> ForgeEmerald
            BuildPhase.FAILED -> ForgeError
            BuildPhase.IDLE -> ForgeCyan
            else -> ForgeAmber
        },
        label = "statusAccent"
    )

    val containerColor by animateColorAsState(
        targetValue = when (sessionState.phase) {
            BuildPhase.READY -> ForgeEmeraldContainer.copy(alpha = 0.45f)
            BuildPhase.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else -> ObsidianSurfaceVariant
        },
        label = "statusContainer"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("animated_status_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = 1.dp,
            color = if (sessionState.phase == BuildPhase.IDLE) ObsidianOutline else accentColor.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (sessionState.phase) {
                        BuildPhase.READY -> Icons.Rounded.CheckCircle
                        BuildPhase.FAILED -> Icons.Rounded.ErrorOutline
                        BuildPhase.IDLE -> Icons.Rounded.CloudDone
                        else -> Icons.Rounded.Sync
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(if (sessionState.phase.isRunning) pulseAlpha else 1f)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = sessionState.animatedStatusTitle,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn(tween(240))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(180)))
                        },
                        label = "animatedStatusTitle"
                    ) { titleText ->
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (sessionState.phase == BuildPhase.READY) {
                                    ForgeEmerald
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AnimatedContent(
                        targetState = sessionState.animatedStatusDetail,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                        label = "animatedStatusDetail"
                    ) { detailText ->
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (sessionState.phase.isRunning) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            if (sessionState.phase.isRunning) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = accentColor,
                    trackColor = ObsidianOutline,
                    strokeCap = StrokeCap.Round
                )
            }

            if (sessionState.phase == BuildPhase.READY && sessionState.latestReadyApkPath != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownloadReadyApk,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("download_ready_apk_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForgeEmerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.download_ready_apk),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onShareReadyApk,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("share_ready_apk_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ForgeEmerald.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.open_share_apk),
                            tint = ForgeEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "Hajmi: ${GitHubBuildCoordinator.formatBytes(sessionState.latestReadySizeBytes)} • 'Mening APKlarim' ga avtomatik qo'shildi",
                    style = MaterialTheme.typography.bodySmall,
                    color = ForgeEmerald
                )
            }

            val workflowUrl = sessionState.activeWorkflowUrl
            if (!workflowUrl.isNullOrBlank() &&
                (sessionState.phase.isRunning || sessionState.phase == BuildPhase.FAILED)
            ) {
                OutlinedButton(
                    onClick = { onOpenWorkflowUrl(workflowUrl) },
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GitHub Actions da ko'rish",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
