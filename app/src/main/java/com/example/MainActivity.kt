package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.repository.MeshRepository
import com.example.firebase.configureAppCheck
import com.example.notifications.AlertNotificationHelper
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.screens.AgentsScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeveloperApiScreen
import com.example.ui.screens.LogsAndAlertsScreen
import com.example.ui.theme.NexusMeshTheme
import com.example.viewmodel.MeshViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class MeshTab(val label: String, val icon: ImageVector, val testTag: String) {
    DASHBOARD("لوحة التحكم", Icons.Default.Dashboard, "nav_dashboard"),
    AGENTS("الوكلاء", Icons.Default.Hub, "nav_agents"),
    LOGS("السجل", Icons.Default.Terminal, "nav_logs"),
    ANALYTICS("التقارير", Icons.Default.Analytics, "nav_analytics"),
    DEVELOPER("المطورون", Icons.Default.Code, "nav_developer")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check setup with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            NexusMeshTheme {
                NexusMeshRootApp()
            }
        }
    }
}

@Composable
private fun NexusMeshRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val meshViewModel: MeshViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        MeshViewModel(
                            repository = MeshRepository(db, auth),
                            currentUserId = user.uid,
                            currentUserEmail = user.email.orEmpty()
                        )
                    }
                }
            )
            MainCommandCenterScaffold(
                viewModel = meshViewModel,
                userEmail = user.email.orEmpty(),
                userName = user.displayName.orEmpty(),
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainCommandCenterScaffold(
    viewModel: MeshViewModel,
    userEmail: String,
    userName: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val agentsState by viewModel.agentsState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val alertsState by viewModel.alertsState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val reportsState by viewModel.reportsState.collectAsStateWithLifecycle()
    val liveExecution by viewModel.liveExecution.collectAsStateWithLifecycle()
    val isGeneratingReport by viewModel.isGeneratingReport.collectAsStateWithLifecycle()
    val webhookResult by viewModel.webhookResult.collectAsStateWithLifecycle()
    val isTestingWebhook by viewModel.isTestingWebhook.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MeshTab.DASHBOARD) }
    var logsInitialSubTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val unreadAlertsCount = (alertsState as? UiState.Success)?.data?.count { !it.isAcknowledged } ?: 0

    BackHandler(enabled = selectedTab != MeshTab.DASHBOARD) {
        selectedTab = MeshTab.DASHBOARD
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.notificationEvent.collect { event ->
            AlertNotificationHelper.showInstantAlert(
                context = context,
                title = event.title,
                body = event.body,
                notificationId = event.id
            )
            snackbarHostState.showSnackbar("${event.title}: ${event.body}")
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedTab == MeshTab.DASHBOARD) "NexusMesh AI" else selectedTab.label,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                logsInitialSubTab = 1
                                selectedTab = MeshTab.LOGS
                            },
                            modifier = Modifier.testTag("alerts_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadAlertsCount > 0) {
                                        Badge { Text(unreadAlertsCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "التنبيهات الفورية")
                            }
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "إعدادات الكتلة")
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MeshTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    if (tab == MeshTab.LOGS) logsInitialSubTab = 0
                                    selectedTab = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
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
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        MeshTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    if (tab == MeshTab.LOGS) logsInitialSubTab = 0
                                    selectedTab = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                when (selectedTab) {
                    MeshTab.DASHBOARD -> DashboardScreen(
                        agentsState = agentsState,
                        tasksState = tasksState,
                        settings = settings,
                        liveExecution = liveExecution,
                        onNavigateToAgents = { selectedTab = MeshTab.AGENTS },
                        onRunWorkload = { title, prompt, category, simFailover ->
                            viewModel.executeOrchestratedTask(title, prompt, category, simFailover)
                        },
                        onChangeStrategy = { strat ->
                            viewModel.updateClusterSettings(settings.copy(loadBalanceStrategy = strat))
                        }
                    )
                    MeshTab.AGENTS -> AgentsScreen(
                        agentsState = agentsState,
                        currentUserEmail = userEmail,
                        onAddAgent = { name, provider, email, region, model, role, priority, conc, autoFailover, endpoint ->
                            viewModel.addCloudAgent(
                                name, provider, email, region, model, role,
                                priority, conc, autoFailover, endpoint
                            )
                        },
                        onUpdatePriority = { agent, priority ->
                            viewModel.updateAgentPriority(agent, priority)
                        },
                        onUpdateConfig = { id, name, email, priority, conc, autoFailover, role, endpoint ->
                            viewModel.updateAgentConfig(id, name, email, priority, conc, autoFailover, role, endpoint)
                        },
                        onToggleStatus = { agent -> viewModel.toggleAgentStatus(agent) },
                        onDeleteAgent = { agent -> viewModel.deleteAgent(agent) }
                    )
                    MeshTab.LOGS -> LogsAndAlertsScreen(
                        logsState = logsState,
                        alertsState = alertsState,
                        initialTab = logsInitialSubTab,
                        onAcknowledgeAlert = { alert -> viewModel.acknowledgeAlert(alert) },
                        onDeleteAlert = { alert -> viewModel.deleteAlert(alert) }
                    )
                    MeshTab.ANALYTICS -> AnalyticsScreen(
                        agentsState = agentsState,
                        tasksState = tasksState,
                        logsState = logsState,
                        reportsState = reportsState,
                        isGeneratingReport = isGeneratingReport,
                        onGenerateReport = { period -> viewModel.generatePeriodicReport(period) }
                    )
                    MeshTab.DEVELOPER -> DeveloperApiScreen(
                        settings = settings,
                        isTestingWebhook = isTestingWebhook,
                        webhookResult = webhookResult,
                        onSaveWebhookUrl = { url ->
                            viewModel.updateClusterSettings(settings.copy(webhookUrl = url))
                        },
                        onRotateToken = { viewModel.rotateApiToken() },
                        onTestWebhook = { url, token -> viewModel.testWebhookIntegration(url, token) }
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            userEmail = userEmail,
            userName = userName,
            settings = settings,
            onDismiss = { showSettingsSheet = false },
            onSaveSettings = { updated -> viewModel.updateClusterSettings(updated) },
            onSignOut = {
                showSettingsSheet = false
                onSignOut()
            }
        )
    }
}
