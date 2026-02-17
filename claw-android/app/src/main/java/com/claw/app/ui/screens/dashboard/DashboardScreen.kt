package com.claw.app.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claw.app.data.remote.ConnectionState
import com.claw.app.domain.model.Instance
import com.claw.app.domain.model.InstanceStatus
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    onChannels: (String) -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gatewayConnection by viewModel.gatewayConnection.collectAsState()
    
    var showPairingDialog by remember { mutableStateOf<Instance?>(null) }
    
    if (uiState.pairingSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPairingSuccess() },
            title = { Text("Success") },
            text = { Text("Pairing approved successfully!") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPairingSuccess() }) {
                    Text("OK")
                }
            }
        )
    }

    showPairingDialog?.let { instance ->
        ApprovePairingDialog(
            onDismiss = { showPairingDialog = null },
            onConfirm = { code ->
                viewModel.approvePairing(instance.id, code)
                showPairingDialog = null
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claw") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ConnectionStatusCard(gatewayConnection)
                }
                
                item {
                    SubscriptionCard(uiState)
                }
                
                item {
                    Text(
                        "Your Instances",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                if (uiState.instances.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No instances yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.instances) { instance ->
                        InstanceCard(
                            instance,
                            onConnect = { viewModel.connectToGateway(instance) },
                            onChannels = { onChannels(instance.id) },
                            onPairing = { showPairingDialog = instance }
                        )
                    }
                }
                
                item {
                    UsageCard(uiState)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (connectionState) {
                    is ConnectionState.Connected -> Icons.Default.CheckCircle
                    is ConnectionState.Connecting,
                    is ConnectionState.WaitingForChallenge,
                    is ConnectionState.Authenticating -> Icons.Default.Sync
                    is ConnectionState.Error -> Icons.Default.Error
                    else -> Icons.Default.CloudOff
                },
                contentDescription = null,
                tint = when (connectionState) {
                    is ConnectionState.Connected -> Color(0xFF4CAF50)
                    is ConnectionState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Connecting -> "Connecting..."
                        is ConnectionState.WaitingForChallenge -> "Connecting..."
                        is ConnectionState.Authenticating -> "Authenticating..."
                        is ConnectionState.Error -> "Connection Error"
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (connectionState is ConnectionState.Error) {
                    Text(
                        text = connectionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(uiState: DashboardUiState) {
    val status = uiState.subscriptionStatus
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Subscription",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = { },
                    label = { Text(status?.subscription?.name ?: "FREE") }
                )
            }
            
            if (status != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                val usagePercent = if (status.limits.tokens > 0) {
                    (status.usage.tokens.toFloat() / status.limits.tokens * 100).coerceAtMost(100f)
                } else 0f
                
                Text(
                    "Token Usage This Month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { usagePercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${status.usage.tokens} / ${if (status.limits.tokens < 0) "Unlimited" else status.limits.tokens} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InstanceCard(
    instance: Instance, 
    onConnect: () -> Unit, 
    onChannels: () -> Unit = {},
    onPairing: () -> Unit = {}
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (instance.status) {
                            InstanceStatus.RUNNING -> Icons.Default.PlayArrow
                            InstanceStatus.STOPPED -> Icons.Default.Stop
                            InstanceStatus.STARTING -> Icons.Default.Sync
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = when (instance.status) {
                            InstanceStatus.RUNNING -> Color(0xFF4CAF50)
                            InstanceStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Port ${instance.dockerPort}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            instance.status.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (instance.status == InstanceStatus.RUNNING) {
                    FilledTonalButton(onClick = onConnect) {
                        Text("Connect")
                    }
                }
            }
            
            // Action row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onChannels,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Channels")
                }
                
                if (instance.status == InstanceStatus.RUNNING) {
                    OutlinedButton(
                        onClick = onPairing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pairing")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApprovePairingDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve Pairing") },
        text = {
            Column {
                Text("Enter the pairing code sent by your Telegram bot.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Pairing Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.isNotBlank()
            ) {
                Text("Approve")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun UsageCard(uiState: DashboardUiState) {
    val usage = uiState.usage
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Usage Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                UsageStat(
                    icon = Icons.Default.TextFields,
                    label = "Input",
                    value = "${usage?.totals?.inputTokens ?: 0}"
                )
                UsageStat(
                    icon = Icons.Default.AutoAwesome,
                    label = "Output",
                    value = "${usage?.totals?.outputTokens ?: 0}"
                )
                UsageStat(
                    icon = Icons.Default.AttachMoney,
                    label = "Cost",
                    value = "$${String.format("%.2f", usage?.totals?.totalCost ?: 0.0)}"
                )
            }
        }
    }
}

@Composable
private fun UsageStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
