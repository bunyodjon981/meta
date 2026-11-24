package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.CreateApkScreen
import com.example.ui.screens.MyApksScreen
import com.example.ui.theme.ApkForgeTheme
import com.example.ui.theme.ForgeEmerald
import com.example.ui.theme.ForgeEmeraldContainer
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.ApkForgeViewModel

const val ROUTE_CREATE_APK = "create_apk"
const val ROUTE_MY_APKS = "my_apks"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApkForgeTheme {
                ApkForgeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkForgeApp(
    viewModel: ApkForgeViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_CREATE_APK

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by viewModel.buildSessionState.collectAsStateWithLifecycle()
    val logs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    val builtApks by viewModel.builtApks.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = ObsidianSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_CREATE_APK,
                            onClick = { navigateTo(ROUTE_CREATE_APK) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_CREATE_APK) {
                                        Icons.Rounded.Build
                                    } else {
                                        Icons.Outlined.Build
                                    },
                                    contentDescription = stringResource(R.string.nav_create_apk)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_create_apk)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ForgeEmerald,
                                selectedTextColor = ForgeEmerald,
                                indicatorColor = ForgeEmeraldContainer
                            ),
                            modifier = Modifier.testTag("nav_create_apk_tab")
                        )

                        NavigationBarItem(
                            selected = currentRoute == ROUTE_MY_APKS,
                            onClick = { navigateTo(ROUTE_MY_APKS) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (builtApks.isNotEmpty()) {
                                            Badge(
                                                containerColor = ForgeEmerald,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(builtApks.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_MY_APKS) {
                                            Icons.Rounded.Apps
                                        } else {
                                            Icons.Outlined.Apps
                                        },
                                        contentDescription = stringResource(R.string.nav_my_apks)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_my_apks)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ForgeEmerald,
                                selectedTextColor = ForgeEmerald,
                                indicatorColor = ForgeEmeraldContainer
                            ),
                            modifier = Modifier.testTag("nav_my_apks_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(containerColor = ObsidianSurface) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_CREATE_APK,
                            onClick = { navigateTo(ROUTE_CREATE_APK) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_CREATE_APK) {
                                        Icons.Rounded.Build
                                    } else {
                                        Icons.Outlined.Build
                                    },
                                    contentDescription = stringResource(R.string.nav_create_apk)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_create_apk)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ForgeEmerald,
                                selectedTextColor = ForgeEmerald,
                                indicatorColor = ForgeEmeraldContainer
                            )
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_MY_APKS,
                            onClick = { navigateTo(ROUTE_MY_APKS) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (builtApks.isNotEmpty()) {
                                            Badge(
                                                containerColor = ForgeEmerald,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text(builtApks.size.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentRoute == ROUTE_MY_APKS) {
                                            Icons.Rounded.Apps
                                        } else {
                                            Icons.Outlined.Apps
                                        },
                                        contentDescription = stringResource(R.string.nav_my_apks)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_my_apks)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ForgeEmerald,
                                selectedTextColor = ForgeEmerald,
                                indicatorColor = ForgeEmeraldContainer
                            )
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CREATE_APK,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_CREATE_APK) {
                        CreateApkScreen(
                            uiState = uiState,
                            sessionState = sessionState,
                            logs = logs,
                            onUsernameChange = viewModel::onUsernameChange,
                            onTokenChange = viewModel::onTokenChange,
                            onRepoNameChange = viewModel::onRepoNameChange,
                            onPrivateRepoChange = viewModel::onPrivateRepoChange,
                            onNotificationsToggle = viewModel::onNotificationsToggle,
                            onZipSelected = viewModel::onZipSelected,
                            onStartBuild = viewModel::onStartBuild,
                            onCancelBuild = viewModel::onCancelBuild,
                            onClearLogs = viewModel::onClearLogs,
                            onShowMessage = viewModel::showMessage,
                            onSaveReadyApkToUri = viewModel::exportApkToUserUri
                        )
                    }

                    composable(ROUTE_MY_APKS) {
                        MyApksScreen(
                            builtApks = builtApks,
                            onGoToCreateTab = { navigateTo(ROUTE_CREATE_APK) },
                            onSaveApkToUri = viewModel::exportApkToUserUri,
                            onSaveToDownloads = viewModel::saveApkToPublicDownloads,
                            onDeleteApk = viewModel::deleteBuiltApk,
                            onShowMessage = viewModel::showMessage
                        )
                    }
                }
            }
        }
    }
}
