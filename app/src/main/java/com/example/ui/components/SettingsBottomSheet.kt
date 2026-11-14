package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ClusterSettings
import com.example.data.model.LoadBalanceStrategies
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsBottomSheet(
    userEmail: String,
    userName: String,
    settings: ClusterSettings,
    onDismiss: () -> Unit,
    onSaveSettings: (ClusterSettings) -> Unit,
    onSignOut: () -> Unit
) {
    var strategy by remember(settings) { mutableStateOf(settings.loadBalanceStrategy) }
    var autoFailover by remember(settings) { mutableStateOf(settings.autoFailoverEnabled) }
    var instantAlerts by remember(settings) { mutableStateOf(settings.instantAlertsEnabled) }
    var heartbeatSec by remember(settings) { mutableFloatStateOf(settings.heartbeatIntervalSec.toFloat()) }
    var thresholdPct by remember(settings) { mutableFloatStateOf(settings.highLoadThresholdPct.toFloat()) }
    var permissionTip by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            instantAlerts = true
            permissionTip = "تم تفعيل إشعارات النظام الفورية."
        } else {
            permissionTip = "ستظل التنبيهات الفورية تظهر داخل التطبيق دون إشعارات النظام."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "إعدادات الكتلة والحساب السحابي",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName.ifBlank { "حساب Google الموحد" },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = userEmail.ifBlank { "متصل بمزامنة Firebase" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "استراتيجية توزيع الأعباء الحسابية",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LoadBalanceStrategies.all.forEach { option ->
                    FilterChip(
                        selected = strategy == option,
                        onClick = { strategy = option },
                        label = { Text(LoadBalanceStrategies.labelAr(option)) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("التحول التلقائي عند التوقف (Auto-Failover)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "نقل المهام تلقائياً للوكيل التالي إذا انقطع الوكيل النشط",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoFailover,
                    onCheckedChange = { autoFailover = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("التنبيهات الفورية وإشعارات الجهاز", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "تنبيه فوري عند التحول التلقائي أو تجاوز الحمل",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = instantAlerts,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            instantAlerts = enabled
                        }
                    }
                )
            }

            if (permissionTip != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = permissionTip!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                Text(
                    text = "فترة نبض الفحص (Heartbeat): ${heartbeatSec.roundToLong()} ثانية",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = heartbeatSec,
                    onValueChange = { heartbeatSec = it },
                    valueRange = 5f..120f
                )
            }

            Column {
                Text(
                    text = "عتبة تنبيه الحمل المرتفع: ${thresholdPct.roundToLong()}%",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = thresholdPct,
                    onValueChange = { thresholdPct = it },
                    valueRange = 50f..98f
                )
            }

            Button(
                onClick = {
                    onSaveSettings(
                        settings.copy(
                            loadBalanceStrategy = strategy,
                            autoFailoverEnabled = autoFailover,
                            instantAlertsEnabled = instantAlerts,
                            heartbeatIntervalSec = heartbeatSec.roundToLong(),
                            highLoadThresholdPct = thresholdPct.roundToLong()
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_cluster_settings_button")
            ) {
                Text("حفظ ومزامنة الإعدادات")
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تسجيل الخروج من Google")
            }
        }
    }
}
