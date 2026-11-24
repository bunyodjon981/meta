package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.BuildSessionState
import com.example.data.model.TerminalLogLine
import com.example.domain.ApkStorageHelper
import com.example.domain.GitHubBuildCoordinator
import com.example.ui.components.AnimatedBuildStatusCard
import com.example.ui.components.RealTimeTerminal
import com.example.ui.theme.ForgeCyan
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeEmeraldContainer
import com.example.ui.theme.ForgeError
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.viewmodel.CreateApkUiState

@Composable
fun CreateApkScreen(
    uiState: CreateApkUiState,
    sessionState: BuildSessionState,
    logs: List<TerminalLogLine>,
    onUsernameChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onRepoNameChange: (String) -> Unit,
    onPrivateRepoChange: (Boolean) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onZipSelected: (Uri) -> Unit,
    onStartBuild: () -> Unit,
    onCancelBuild: () -> Unit,
    onClearLogs: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSaveReadyApkToUri: (localPath: String, targetUri: Uri, apkId: Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showToken by rememberSaveable { mutableStateOf(false) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onZipSelected(uri)
        }
    }

    val saveApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { targetUri ->
        val localPath = sessionState.latestReadyApkPath
        if (targetUri != null && localPath != null) {
            onSaveReadyApkToUri(localPath, targetUri, sessionState.latestReadyApkId)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onNotificationsToggle(true)
            onShowMessage("Bildirishnomalar yoqildi")
        } else {
            onNotificationsToggle(false)
            onShowMessage("Bildirishnoma ruxsati berilmadi — fonda bildirishnomasiz ishlaydi")
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Pipeline Banner + Notification Switch
            item {
                HeroHeaderCard(
                    notificationsEnabled = uiState.notificationsEnabled,
                    onToggleNotifications = { desired ->
                        if (desired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onNotificationsToggle(true)
                            } else {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            onNotificationsToggle(desired)
                        }
                    }
                )
            }

            // 2. GitHub Credentials & Repo Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "GitHub ulanish va Repository",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Topmost: GitHub Username
                        OutlinedTextField(
                            value = uiState.githubUsername,
                            onValueChange = onUsernameChange,
                            enabled = !sessionState.phase.isRunning,
                            label = { Text(stringResource(R.string.github_username_label)) },
                            placeholder = { Text(stringResource(R.string.github_username_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = ForgeCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            colors = forgeTextFieldColors(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_username_input")
                        )

                        // Second: GitHub Token
                        OutlinedTextField(
                            value = uiState.githubToken,
                            onValueChange = onTokenChange,
                            enabled = !sessionState.phase.isRunning,
                            label = { Text(stringResource(R.string.github_token_label)) },
                            placeholder = { Text(stringResource(R.string.github_token_hint)) },
                            supportingText = {
                                Text(
                                    text = "Classic token uchun 'repo' va 'workflow' ruxsatlarini belgilang.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = ForgeEmerald
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showToken = !showToken },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showToken) "Tokenni yashirish" else "Tokenni ko'rsatish"
                                    )
                                }
                            },
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            colors = forgeTextFieldColors(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_token_input")
                        )

                        // Third: Repository Name
                        OutlinedTextField(
                            value = uiState.repoName,
                            onValueChange = onRepoNameChange,
                            enabled = !sessionState.phase.isRunning,
                            label = { Text(stringResource(R.string.repo_name_label)) },
                            placeholder = { Text(stringResource(R.string.repo_name_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Source,
                                    contentDescription = null,
                                    tint = ForgeCyan
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = forgeTextFieldColors(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("repo_name_input")
                        )

                        // Private / Public switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.private_repo_label),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = uiState.isPrivateRepo,
                                onCheckedChange = onPrivateRepoChange,
                                enabled = !sessionState.phase.isRunning,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = ForgeEmerald
                                ),
                                modifier = Modifier.testTag("private_repo_switch")
                            )
                        }
                    }
                }
            }

            // 3. ZIP File Selection & Upload Action Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                zipPickerLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !sessionState.phase.isRunning && !uiState.isInspectingZip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("pick_zip_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, ForgeCyan.copy(alpha = 0.7f))
                        ) {
                            if (uiState.isInspectingZip) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = ForgeCyan
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("ZIP tekshirilmoqda...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.FolderZip,
                                    contentDescription = null,
                                    tint = ForgeCyan
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.zipSummary == null) {
                                        stringResource(R.string.pick_zip_button)
                                    } else {
                                        stringResource(R.string.change_zip_button)
                                    },
                                    color = ForgeCyan
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.zipSummary != null) {
                            uiState.zipSummary?.let { summary ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(ForgeEmeraldContainer.copy(alpha = 0.32f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = ForgeEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = summary.sourceFileName,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = ForgeEmerald
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Ilova: ${summary.detectedAppName} • ${summary.fileCount} fayl • ${GitHubBuildCoordinator.formatBytes(summary.totalSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = ForgeCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (summary.hasExistingWorkflow) {
                                                "Mavjud GitHub Actions workflow ishlatiladi"
                                            } else {
                                                "GitHub Actions APK workflow avtomatik qo'shiladi"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ForgeCyan
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.zipInspectError != null) {
                            Text(
                                text = uiState.zipInspectError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = ForgeError
                            )
                        }

                        // Primary Build / Cancel Button
                        if (sessionState.phase.isRunning) {
                            OutlinedButton(
                                onClick = onCancelBuild,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("cancel_build_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, ForgeError)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = ForgeError
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.cancel_build_button),
                                    color = ForgeError,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Button(
                                onClick = onStartBuild,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("start_build_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ForgeEmerald,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RocketLaunch,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.start_build_button),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 4. Animated Status Text & Progress (directly below Upload button)
            item {
                AnimatedBuildStatusCard(
                    sessionState = sessionState,
                    onDownloadReadyApk = {
                        val name = sessionState.latestReadyApkName ?: "app-debug.apk"
                        saveApkLauncher.launch(name)
                    },
                    onShareReadyApk = {
                        val path = sessionState.latestReadyApkPath ?: return@AnimatedBuildStatusCard
                        val name = sessionState.latestReadyApkName ?: "app-debug.apk"
                        val intent = ApkStorageHelper.buildShareApkIntent(context, path, name)
                        if (intent != null) {
                            context.startActivity(Intent.createChooser(intent, "APK ni ulashish"))
                        } else {
                            onShowMessage("APK fayli topilmadi.")
                        }
                    },
                    onOpenWorkflowUrl = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                )
            }

            // 5. Real-Time Terminal (directly below Animated Status)
            item {
                RealTimeTerminal(
                    logs = logs,
                    onClearLogs = onClearLogs,
                    onCopiedFeedback = { onShowMessage("Terminal loglari nusxalandi") }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HeroHeaderCard(
    notificationsEnabled: Boolean,
    onToggleNotifications: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ObsidianOutline),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_forge_banner),
                contentDescription = "APK Forge Cloud Pipeline Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianBackground.copy(alpha = 0.45f),
                                ObsidianBackground.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.hero_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = ForgeCyan
                        )
                        Text(
                            text = stringResource(R.string.hero_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }

                // Notification On/Off Toggle Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface.copy(alpha = 0.88f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (notificationsEnabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsOff
                            },
                            contentDescription = null,
                            tint = if (notificationsEnabled) ForgeEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.notifications_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = if (notificationsEnabled) {
                                    stringResource(R.string.notifications_on) + " — fonda bildirishnoma chiqadi"
                                } else {
                                    stringResource(R.string.notifications_off) + " — bildirishnomasiz ishlaydi"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = ForgeEmerald
                        ),
                        modifier = Modifier.testTag("notifications_toggle_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun forgeTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ForgeEmerald,
    unfocusedBorderColor = ObsidianOutline,
    focusedLabelColor = ForgeEmerald,
    cursorColor = ForgeEmerald,
    focusedContainerColor = ObsidianSurfaceVariant.copy(alpha = 0.55f),
    unfocusedContainerColor = ObsidianSurfaceVariant.copy(alpha = 0.35f)
)
