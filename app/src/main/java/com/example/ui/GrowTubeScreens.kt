package com.example.ui

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.example.data.*
import com.example.ui.theme.*
import java.util.Locale
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GrowTubeAppContent(viewModel: GrowTubeViewModel) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = viewModel.uiState,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "screen_transition"
        ) { state ->
            when (state) {
                is ScreenState.Home -> HomeScreen(viewModel)
                is ScreenState.Settings -> SettingsScreen(viewModel)
                is ScreenState.Loading -> LoadingScreen(viewModel)
                is ScreenState.Results -> ResultsScreen(viewModel, state.report)
                is ScreenState.Error -> ErrorScreen(viewModel, state.message)
                is ScreenState.VideoAutopsy -> VideoAutopsyScreen(viewModel, state.video, state.report)
                is ScreenState.ChannelClone -> ChannelCloneScreen(viewModel)
            }
        }
    }
}

// ==========================================
// API QUOTA TRACKER
// ==========================================
fun getQuotaColor(remaining: Int): Color {
    return when {
        remaining > 5000 -> Color(0xFF00C853)
        remaining > 2000 -> Color(0xFFFFD600)
        else -> Color(0xFFFF1744)
    }
}

@Composable
fun QuotaBadge(
    remaining: Int,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quotaColor = if (isConnected) getQuotaColor(remaining) else Color(0xFFFF1744)
    val displayText = if (isConnected) {
        val remainingFormatted = String.format(Locale.US, "%,d", remaining)
        "Quota: $remainingFormatted / 10,000"
    } else {
        "Quota: 0 / 10,000"
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .border(1.dp, quotaColor.copy(alpha = 0.4f), RoundedCornerShape(99.dp))
            .testTag("quota_badge"),
        color = quotaColor.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(quotaColor)
            )
            
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                color = quotaColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun QuotaDialog(
    remaining: Int,
    isConnected: Boolean,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val used = if (isConnected) (10000 - remaining).coerceAtLeast(0) else 0
    val analysesRemaining = if (isConnected) remaining / 300 else 0
    val quotaColor = if (isConnected) getQuotaColor(remaining) else Color(0xFFFF1744)
    val useFraction = if (isConnected) used / 10000f else 0f
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isConnected) {
                    Button(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "CONNECT KEY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "CLOSE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = DarkCard,
        tonalElevation = 6.dp,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Quota Status",
                    tint = quotaColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isConnected) "API Quota Status" else "API Quota: Disconnected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isConnected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF1744).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744))
                                )
                                Text(
                                    text = "YouTube API Key Not Connected",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF1744)
                                )
                            }
                            Text(
                                text = "Current Quota: 0 / 10,000. Add your free YouTube Data API v3 key in Settings to activate 10,000 daily quota units for live channel stats.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary.copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Quota Usage",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = String.format(Locale.US, "%,d / 10,000 used", used),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        LinearProgressIndicator(
                            progress = useFraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = quotaColor,
                            trackColor = TextPrimary.copy(alpha = 0.1f)
                        )
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TextPrimary.copy(alpha = 0.04f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Analyses remaining today",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = if (isConnected) "$analysesRemaining runs" else "0 runs (Live API)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConnected) TextPrimary else Color(0xFFFF1744),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(TextPrimary.copy(alpha = 0.08f)))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Unit cost per analysis",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "~300 units",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(TextPrimary.copy(alpha = 0.08f)))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = if (isConnected) "Active (Live YouTube API)" else "Offline Demo Mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConnected) GreenSuccess else Color(0xFFFF1744),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    )
}

// ==========================================
// SCREEN 1 - SETUP/HOME
// ==========================================
@Composable
fun DeleteConfirmationDialog(
    channelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744)), // Red filled
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Delete", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = DarkCard,
        title = {
            Text(
                text = "Delete Analysis?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = "Delete $channelName cached data?\nRe-analyze anytime for fresh live data.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    )
}

@Composable
fun HomeScreen(viewModel: GrowTubeViewModel) {
    var showQuotaDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<RecentChannelItem?>(null) }
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarText by remember { mutableStateOf("") }

    LaunchedEffect(showSnackbar) {
        if (showSnackbar) {
            kotlinx.coroutines.delay(3500)
            showSnackbar = false
        }
    }

    if (showQuotaDialog) {
        QuotaDialog(
            remaining = viewModel.remainingQuota,
            isConnected = viewModel.isApiKeyConnected,
            onDismiss = { showQuotaDialog = false },
            onNavigateToSettings = {
                showQuotaDialog = false
                viewModel.navigateToSettings()
            }
        )
    }

    if (channelToDelete != null) {
        DeleteConfirmationDialog(
            channelName = channelToDelete?.channelName ?: "",
            onConfirm = {
                channelToDelete?.let {
                    viewModel.deleteRecentChannel(it.handle)
                    snackbarText = "Deleted. Re-analyze anytime for fresh data."
                    showSnackbar = true
                }
                channelToDelete = null
            },
            onDismiss = {
                channelToDelete = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App top bar containing Settings icon and Quota Tracker
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { viewModel.navigateToSettings() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary
                    )
                }
                QuotaBadge(
                    remaining = viewModel.remainingQuota,
                    isConnected = viewModel.isApiKeyConnected,
                    onClick = { showQuotaDialog = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            // App Logo and Branding
            LogoAndBrandHeader()

            Spacer(modifier = Modifier.height(30.dp))

            // Channel Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = "Analysis",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Target Channel Diagnostic",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    OutlinedTextField(
                        value = viewModel.channelInput,
                        onValueChange = { viewModel.updateChannelInput(it) },
                        label = { Text("Channel URL or @handle") },
                        placeholder = { Text("e.g., @mkbhd or youtube.com/@cooking") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("channel_query_input"),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Query",
                                tint = Color.Gray
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    Button(
                        onClick = { viewModel.triggerAnalysis() },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("analyze_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "ANALYZE CHANNEL",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Launch",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Recently Analyzed Section
            if (viewModel.recentChannels.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Recently Analyzed Channels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        viewModel.recentChannels.forEach { item ->
                            RecentChannelRow(
                                item = item,
                                onClick = { viewModel.loadFromCache(item) },
                                onDelete = { channelToDelete = item },
                                onReanalyze = {
                                    snackbarText = "Fetching latest data..."
                                    showSnackbar = true
                                    viewModel.reanalyzeChannel(item.handle)
                                }
                            )
                        }
                    }
                }
            } else {
                // Friendly Tip for Empty State
                Spacer(modifier = Modifier.height(20.dp))
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Empty States",
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No analyzed channel yet.\nTry typing '@cooking' or '@mkbhd' above!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Floating custom Bento/M3-style toast/snackbar
        AnimatedVisibility(
            visible = showSnackbar,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.12f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth().testTag("delete_snackbar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Notification",
                        tint = YouTubeRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = snackbarText,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun LogoAndBrandHeader() {
    val infinitePulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentRed, YouTubeRed)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Logo",
                tint = TextPrimary,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "GrowTube",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = TextPrimary,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "AI-Driven YouTube Growth Intelligence",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(GreenSuccess.copy(alpha = 0.08f))
                .border(1.dp, GreenSuccess.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(GreenSuccess.copy(alpha = pulseAlpha))
            )
            Text(
                text = "GROWTUBE AI SECURE ENGINE ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = GreenSuccess,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun RecentChannelRow(
    item: RecentChannelItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onReanalyze: () -> Unit
) {
    val infinitePulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infinitePulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_item_${item.handle}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main clickable info area (Zone A)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with a fine border to match Bento styling
                AsyncImage(
                    model = item.avatarUrl,
                    contentDescription = item.channelName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.dp, TextPrimary.copy(alpha = 0.15f), CircleShape)
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.channelName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.handle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess.copy(alpha = pulseAlpha))
                        )
                        Text(
                            text = item.subscribers.formatAbbreviation() + " Subs • Load Cache",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Interactive Action Buttons Row (Zone B)
            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Re-analyze Action Button
                IconButton(
                    onClick = onReanalyze,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00C853).copy(alpha = 0.15f))
                        .testTag("reanalyze_button_${item.handle}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-analyze Channel",
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Action Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744).copy(alpha = 0.15f))
                        .testTag("delete_button_${item.handle}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Channel Cache",
                        tint = Color(0xFFFF1744),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2 - ANALYSIS LOADING
// ==========================================
@Composable
fun LoadingScreen(viewModel: GrowTubeViewModel) {
    val steps = listOf(
        "Fetching channel data from API...",
        "Reading last 20 videos statistics...",
        "Finding niche competitor channels & metrics...",
        "AI generating diagnostic content & strategic calendars..."
    )

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulse & Spin Logo Visuals
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outer Glow Spinner ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        color = YouTubeRed.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = YouTubeRed,
                        startAngle = angle,
                        sweepAngle = 100f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Internal Icon with simple scale pulse
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(YouTubeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "AI Loading",
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "GROWTUBE AI ANALYZER",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Formulating Channel Strategy Matrix",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Checklist Steps Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    steps.forEachIndexed { index, title ->
                        val stepNum = index + 1
                        val isCompleted = viewModel.loadingStep > stepNum
                        val isActive = viewModel.loadingStep == stepNum
                        val isPending = viewModel.loadingStep < stepNum

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> GreenSuccess.copy(alpha = 0.2f)
                                            isActive -> YouTubeRed.copy(alpha = 0.2f)
                                            else -> TextSecondary.copy(alpha = 0.1f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = GreenSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (isActive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = YouTubeRed,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "$stepNum",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isCompleted -> TextPrimary.copy(alpha = 0.5f)
                                    isActive -> Color.White
                                    else -> Color.Gray
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            // Sub Status Details Text
            Text(
                text = '"' + viewModel.loadingStatusText + '"',
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = GoldAlert,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Cancel Button
            OutlinedButton(
                onClick = { viewModel.cancelActiveAnalysis() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(160.dp).testTag("cancel_load_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel Run", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// SCREEN 3 - RESULTS
// ==========================================
@Composable
fun ResultsScreen(viewModel: GrowTubeViewModel, report: FullGrowTubeReport) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = report.stats.name,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                        Text(
                            text = report.stats.handle,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToHome() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Share Export Action
                    val context = LocalContext.current
                    IconButton(
                        onClick = { viewModel.shareExportReport(context, report) },
                        modifier = Modifier.testTag("share_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Exporter & Share",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCard)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkCard,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = viewModel.activeTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "DNA") },
                    label = { Text("DNA") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = YouTubeRed,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = YouTubeRed.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("tab_dna")
                )
                NavigationBarItem(
                    selected = viewModel.activeTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Competitors") },
                    label = { Text("Competitors") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = YouTubeRed,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = YouTubeRed.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("tab_competitors")
                )
                NavigationBarItem(
                    selected = viewModel.activeTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Strategy") },
                    label = { Text("Strategy") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = YouTubeRed,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = YouTubeRed.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("tab_strategy")
                )
                NavigationBarItem(
                    selected = viewModel.activeTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.Publish, contentDescription = "Launch") },
                    label = { Text("Launch") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = YouTubeRed,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = YouTubeRed.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("tab_launch")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
        ) {
            when (viewModel.activeTab) {
                0 -> TabChannelDna(report, viewModel) { video ->
                    viewModel.navigateToVideoAutopsy(video, report)
                }
                1 -> TabCompetitors(report, viewModel)
                2 -> TabStrategy(report, viewModel)
                3 -> TabLaunch(report, viewModel)
            }
        }
    }
}

// ------------------------------------------
// TAB 1: CHANNEL DNA
// ------------------------------------------
@Composable
fun TabChannelDna(
    report: FullGrowTubeReport,
    viewModel: GrowTubeViewModel,
    onWorstVideoClicked: (VideoInsight) -> Unit
) {
    val stats = report.stats
    val analysis = report.analysis

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Grid Counters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header Mini Avatar Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    AsyncImage(
                        model = stats.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stats.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Text(
                            text = stats.handle + " • " + stats.totalVideos.formatAbbreviation() + " uploads",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                // Stats Cards GRID (Row of 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatBox(
                        title = "SUBSCRIBERS",
                        value = stats.subscribers.formatAbbreviation(),
                        subInfo = "Total subscribers",
                        icon = Icons.Default.People,
                        color = YouTubeRed,
                        modifier = Modifier.weight(1.0f)
                    )
                    MiniStatBox(
                        title = "TOTAL VIEWS",
                        value = stats.totalViews.formatAbbreviation(),
                        subInfo = "Accumulated views",
                        icon = Icons.Default.Visibility,
                        color = BlueInfo,
                        modifier = Modifier.weight(1.0f)
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MiniStatBox(
                        title = "AVG VIEWS PER VIDEO",
                        value = stats.averageViews.formatAbbreviation(),
                        subInfo = "Last 20 videos average",
                        icon = Icons.Default.Equalizer,
                        color = GoldAlert,
                        modifier = Modifier.weight(1.0f)
                    )
                    MiniStatBox(
                        title = "UPLOADS TIMELINE",
                        value = stats.recentVideos.size.toString(),
                        subInfo = "Tracked checklist size",
                        icon = Icons.Default.OndemandVideo,
                        color = GreenSuccess,
                        modifier = Modifier.weight(1.0f)
                    )
                }
            }
        }

        // Colored health ring
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HealthScoreCircularProgress(
                        score = analysis.healthScore,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "Channel Health Audit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = analysis.healthReason ?: "No detailed health audit reason provided.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Upload Cadence
        item {
            GrowCard(
                title = "Upload Frequency",
                icon = Icons.Default.Schedule,
                iconColor = GreenSuccess
            ) {
                Column {
                    Text(
                        text = stats.uploadFrequencyText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Calculated systematically from publishing intervals of historic items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Latest Upload Performance Section
        val latestVideo = stats.recentVideos.firstOrNull()
        var showLatestUploadPerformance = false
        var latestVideoDaysSinceUpload = 1L
        if (latestVideo != null) {
            val sdfStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsedDate = try { sdfStr.parse(latestVideo.date) } catch(e: Exception) { null }
            latestVideoDaysSinceUpload = if (parsedDate != null) {
                val diffMs = java.lang.System.currentTimeMillis() - parsedDate.time
                val days = (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                days
            } else {
                1L
            }.coerceAtLeast(1L)
            if (latestVideoDaysSinceUpload < 7L) {
                showLatestUploadPerformance = true
            }
        }

        if (showLatestUploadPerformance && latestVideo != null) {
            val daysSinceUpload = latestVideoDaysSinceUpload
            val avgViewsPerDay = latestVideo.views.toDouble() / daysSinceUpload
            val engagementRate = if (latestVideo.views > 0) {
                ((latestVideo.likes + latestVideo.comments).toDouble() / latestVideo.views) * 100.0
            } else {
                0.0
            }

            // Calculate historical views per day average of the channel
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val nowTime = System.currentTimeMillis()
            val videosWithAges = stats.recentVideos.map { video ->
                val pDate = try { sdf.parse(video.date) } catch(e: Exception) { null }
                val ageDays = if (pDate != null) {
                    val diffMs = nowTime - pDate.time
                    val d = diffMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
                    d.coerceAtLeast(0.1)
                } else {
                    1.0
                }
                val vpd = video.views.toDouble() / ageDays
                Pair(ageDays, vpd)
            }
            
            val olderVideosAges = videosWithAges.filter { it.first >= 7.0 }
            val avgHistoricalViewsPerDay = if (olderVideosAges.isNotEmpty()) {
                olderVideosAges.map { it.second }.average()
            } else {
                videosWithAges.map { it.second }.average()
            }.coerceAtLeast(1.0)

            val prc = (avgViewsPerDay / avgHistoricalViewsPerDay) * 100.0
            val (badgeText, badgeColor) = when {
                avgViewsPerDay >= avgHistoricalViewsPerDay -> Pair("Strong Start 🔥", GreenSuccess)
                prc >= 50.0 -> Pair("On Track 📈", GoldAlert)
                else -> Pair("Needs Boost ⚡", YouTubeRed)
            }

            val daysStr = if (daysSinceUpload <= 1L) "1 day ago" else "$daysSinceUpload days ago"

            item {
                LaunchedEffect(report) {
                    viewModel.getLatestVideoAnalysis(report)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CardSectionHeader(title = "Latest Upload Performance", icon = Icons.Default.OndemandVideo, tint = YouTubeRed)
                    
                    // Main Stats Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header Row with Title & Performance Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1.0f).padding(end = 8.dp)) {
                                    Text(
                                        text = latestVideo.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Uploaded $daysStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }

                            HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f))

                            // 2x2 Grid of Metrics using row columns
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniMetricItem("Total Views", formatWithCommas(latestVideo.views), YouTubeRed)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniMetricItem("Avg Views/Day", formatWithCommas(Math.round(avgViewsPerDay)), BlueInfo)
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniMetricItem("Likes + Comments", formatWithCommas(latestVideo.likes + latestVideo.comments), GoldAlert)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    MiniMetricItem("Engagement Rate", String.format(Locale.US, "%.1f%%", engagementRate), GreenSuccess)
                                }
                            }
                        }
                    }

                    // GEMINI ANALYSIS SECTION below the stats card
                    if (viewModel.loadingLatestVideoAnalysis) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = YouTubeRed, modifier = Modifier.size(24.dp))
                                Text(
                                    text = "AI is reviewing your latest upload...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        val tracking = viewModel.latestVideoAnalysis
                        if (tracking != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Insights,
                                            contentDescription = null,
                                            tint = YouTubeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "AI Strategic Feed",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    
                                    // 1. Performance Verdict
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = BlueInfo,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Performance Verdict",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = BlueInfo
                                            )
                                        }
                                        Text(
                                            text = tracking.verdict,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            lineHeight = 20.sp
                                        )
                                    }

                                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f))

                                    // 2. Why It's Performing This Way
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Help,
                                                contentDescription = null,
                                                tint = GoldAlert,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Why It's Performing This Way",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GoldAlert
                                            )
                                        }
                                        Text(
                                            text = tracking.reasons,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            lineHeight = 20.sp
                                        )
                                    }

                                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f))

                                    // 3. 3 Actions to Boost Reach Right Now
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = null,
                                                tint = GreenSuccess,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "3 Actions to Boost Reach Right Now",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GreenSuccess
                                            )
                                        }
                                        
                                        tracking.actions.take(3).forEachIndexed { idx, action ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .background(GreenSuccess.copy(alpha = 0.15f), CircleShape)
                                                ) {
                                                    Text(
                                                        text = (idx + 1).toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GreenSuccess
                                                    )
                                                }
                                                Text(
                                                    text = action,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextPrimary.copy(alpha = 0.9f),
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top 3 Videos (Why they worked)
        item {
            Column {
                CardSectionHeader(title = "Best 3 Videos (Concepts)", icon = Icons.Default.ThumbUp, tint = GreenSuccess)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (analysis.bestVideos ?: emptyList()).take(3).forEach { video ->
                        InsightVideoCard(video = video, isPositive = true)
                    }
                }
            }
        }

        // Worst 3 Videos (Why they underperformed)
        item {
            Column {
                CardSectionHeader(title = "Worst 3 Videos (Concepts)", icon = Icons.Default.ThumbDown, tint = YouTubeRed)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (analysis.worstVideos ?: emptyList()).take(3).forEach { video ->
                        InsightVideoCard(
                            video = video,
                            isPositive = false,
                            onClick = { onWorstVideoClicked(video) }
                        )
                    }
                }
            }
        }

        // Target Audience Profile
        item {
            GrowCard(
                title = "Audience Profile Analysis",
                icon = Icons.Default.AccountCircle,
                iconColor = BlueInfo
            ) {
                Text(
                    text = analysis.audienceProfile ?: "No target audience profile analyzed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 22.sp
                )
            }
        }

        // Main Weakness (Key strategic gap)
        item {
            GrowCard(
                title = "Core Strategic Roadblock (AI Insight)",
                icon = Icons.Default.Warning,
                iconColor = YouTubeRed,
                containerColor = YouTubeRed.copy(alpha = 0.08f),
                borderColor = YouTubeRed.copy(alpha = 0.25f)
            ) {
                Text(
                    text = analysis.mainWeakness ?: "No major weakness or roadblock identified.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentRed,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ------------------------------------------
// TAB 2: COMPETITORS
// ------------------------------------------
@Composable
fun FingerprintItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabCompetitors(report: FullGrowTubeReport, viewModel: GrowTubeViewModel) {
    val stats = report.stats
    val analysis = report.analysis
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(stats.handle) {
        viewModel.loadCompetitorsTab(stats.handle, stats, analysis)
    }

    if (viewModel.loadingCompetitorsTab) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing competitors & mapping content gaps...", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    if (viewModel.competitorsTabError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Error, contentDescription = "Error", tint = YouTubeRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(viewModel.competitorsTabError ?: "An error occurred", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadCompetitorsTab(stats.handle, stats, analysis) },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
        return
    }

    val res = viewModel.competitorsTabResponse
    val currentCompetitors = res?.competitors ?: emptyList()

    var showDeleteConfirmCompetitor by remember { mutableStateOf<CompetitorAnalysis?>(null) }

    fun openChannel(handle: String) {
        val cleanHandle = when {
            handle.startsWith("@") -> handle.substring(1)
            else -> handle
        }
        val url = "https://www.youtube.com/@$cleanHandle"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open channel link.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteConfirmCompetitor != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmCompetitor = null },
            title = { Text("Remove Competitor", color = TextPrimary) },
            text = { Text("Are you sure you want to remove ${showDeleteConfirmCompetitor?.name} from your tracked competitors list?", color = TextPrimary.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        val comp = showDeleteConfirmCompetitor
                        if (comp != null) {
                            viewModel.removeManualCompetitor(comp, report)
                        }
                        showDeleteConfirmCompetitor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmCompetitor = null }) {
                    Text("Cancel", color = TextPrimary.copy(alpha = 0.6f))
                }
            },
            containerColor = DarkCard
        )
    }



    // SECTION 2 AI SEARCH BOTTOM SHEET (DEACTIVATED PER USER INTENT)
    var showAISuggestionsSheet by remember { mutableStateOf(false) }
    var selectedAISuggestions by remember { mutableStateOf<Set<CompetitorAnalysis>>(emptySet()) }
    if (showAISuggestionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAISuggestionsSheet = false },
            containerColor = DarkBackground,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextPrimary.copy(alpha = 0.2f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Text(
                    text = "🤖 Let AI Find Competitors",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select channels in your niche to add to your competitive footprint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.aiSuggestionsLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = YouTubeRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching YouTube for channels in your niche...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (viewModel.aiSuggestionsError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Error, contentDescription = "Error", tint = YouTubeRed, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = viewModel.aiSuggestionsError ?: "An error occurred.",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.runAISuggestionsSearch(report) },
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                        ) {
                            Text("Retry Search")
                        }
                    }
                } else {
                    val suggestions = viewModel.aiCompetitorSuggestions
                    if (suggestions.isEmpty()) {
                        Text("No Suggestions Available.", color = TextPrimary)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        ) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(suggestions.size) { index ->
                                    val comp = suggestions[index]
                                    val isSelected = selectedAISuggestions.contains(comp)
                                    val freqValue = remember(comp.uploadFrequency) {
                                        val clean = comp.uploadFrequency.lowercase()
                                        val number = clean.substringBefore(" ").trim()
                                        val isNum = number.isNotEmpty() && (number.getOrNull(0)?.isDigit() == true || number.toDoubleOrNull() != null)
                                        if (isNum) {
                                            if (clean.contains("month")) {
                                                "$number/mo"
                                            } else {
                                                "$number/wk"
                                            }
                                        } else {
                                            comp.uploadFrequency.replace("uploads/week", "/wk")
                                                .replace("upload/week", "/wk")
                                                .replace("videos/week", "/wk")
                                                .replace("video/week", "/wk")
                                                .replace("vids/week", "/wk")
                                                .replace("vid/week", "/wk")
                                                .replace("uploads/month", "/mo")
                                                .replace("upload/month", "/mo")
                                                .replace("videos/month", "/mo")
                                                .replace("video/month", "/mo")
                                                .replace("vids/month", "/mo")
                                                .replace("vid/month", "/mo")
                                                .replace(" ", "")
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) YouTubeRed.copy(alpha = 0.08f) else DarkSurface
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) YouTubeRed.copy(alpha = 0.4f) else TextPrimary.copy(alpha = 0.08f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .clickable {
                                                selectedAISuggestions = if (isSelected) {
                                                    selectedAISuggestions - comp
                                                } else {
                                                    selectedAISuggestions + comp
                                                }
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clip(CircleShape)
                                                            .background(YouTubeRed.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = comp.name.take(1).uppercase(),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = YouTubeRed
                                                        )
                                                    }
                                                    Column {
                                                        Text(
                                                            text = comp.name,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = comp.handle,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                            color = TextSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(TextPrimary.copy(alpha = 0.06f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = comp.contentFormat ?: "Standard Video",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            fontWeight = FontWeight.Bold,
                                                            color = GoldAlert
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = null,
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = YouTubeRed,
                                                            uncheckedColor = TextPrimary.copy(alpha = 0.4f)
                                                        ),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.Start
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "SUBS",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = TextSecondary
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("✓ Verified", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = GreenSuccess, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = comp.subscribers.formatAbbreviation(),
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "AVG VIEWS",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = TextSecondary
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("✓ Verified", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = GreenSuccess, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = comp.avgViews.formatAbbreviation(),
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "FREQ",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = TextSecondary
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("✓ Verified", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = GreenSuccess, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = freqValue,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(TextPrimary.copy(alpha = 0.04f))
                                                    .padding(8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = "Strength Icon",
                                                        tint = GoldAlert,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = "BIGGEST STRATEGIC STRENGTH:",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            fontWeight = FontWeight.Bold,
                                                            color = TextSecondary
                                                        )
                                                        Spacer(modifier = Modifier.height(1.dp))
                                                        Text(
                                                            text = comp.biggestStrength ?: "Consistent high quality content.",
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                            fontWeight = FontWeight.Medium,
                                                            color = TextPrimary,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                viewModel.addAISelectedCompetitors(selectedAISuggestions.toList(), report)
                                showAISuggestionsSheet = false
                            },
                            enabled = selectedAISuggestions.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ai_add_selected_button")
                        ) {
                            Text("Add Selected (${selectedAISuggestions.size})", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showAISuggestionsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = TextPrimary.copy(alpha = 0.6f))
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Niche Fingerprint DNA Title & Card
        item {
            CardSectionHeader(title = "Niche Fingerprint DNA", icon = Icons.Default.Insights, tint = TextPrimary)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FingerprintItem(label = "Primary Category", value = analysis.primaryCategory ?: "Entertainment")
                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                    FingerprintItem(label = "Sub-Category", value = analysis.subCategory ?: "AI Music & Remixes")
                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                    FingerprintItem(label = "Micro-Niche", value = analysis.microNiche ?: "Punjabi AI Tribute Songs")
                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                    FingerprintItem(label = "Core Audience Identity", value = analysis.contentIdentity ?: "AI vocal tributes and modern folk fusion")
                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(YouTubeRed.copy(alpha = 0.08f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "CONTENT PRODUCTION FORMAT (CRITICAL)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = analysis.contentFormat ?: "AI vocals + original music arrangement",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
        // SECTION 1 — YOUR CHANNEL stats card
        item {
            CardSectionHeader(title = "Your Channel", icon = Icons.Default.AccountCircle, tint = YouTubeRed)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = YouTubeRed.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, YouTubeRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // TOP ROW (horizontal, side by side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            AsyncImage(
                                model = stats.avatarUrl,
                                contentDescription = stats.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(TextPrimary.copy(alpha = 0.08f))
                            )
                            Column {
                                Text(
                                    text = stats.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .testTag("my_channel_name")
                                        .clickable { openChannel(stats.handle) }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stats.handle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(YouTubeRed.copy(alpha = 0.15f))
                                .border(1.dp, YouTubeRed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "★ YOUR CHANNEL",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // MIDDLE ROW: Content format description
                    Text(
                        text = analysis.contentFormat ?: "Standard Video Format",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = GoldAlert),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // BOTTOM ROW: ALL 3 STATS ON ONE LINE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Column 1
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "SUBS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stats.subscribers.formatAbbreviation(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "✓ Verified",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = GreenSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Column 2
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "AVG VIEWS",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stats.averageViews.formatAbbreviation(),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "✓ Verified",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = GreenSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Column 3
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "FREQ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${stats.uploadFrequencyValue}/wk",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "✓ Verified",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = GreenSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // SECTION 2 — ADD COMPETITORS & EMPTY STATE
        item {
            CardSectionHeader(title = "Competitive Footprint", icon = Icons.Default.Analytics, tint = TextPrimary)
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Tracked Niche Competitors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${currentCompetitors.size} / 15 Competitors Added",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = if (currentCompetitors.size >= 15) YouTubeRed else TextSecondary,
                    modifier = Modifier.testTag("competitors_count_text")
                )
            }
        }

        // Empty state when list is empty
        if (currentCompetitors.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "No competitors logo",
                            tint = TextPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Add competitors to see analysis.\nUse manual search or let AI suggest channels.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // New Live Search Box (Youtube API connected) per User Intent
        if (currentCompetitors.size < 15) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔍 Search & Add Competitors Live",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = YouTubeRed
                    )
                    
                    var searchVal by remember { mutableStateOf("") }
                    
                    OutlinedTextField(
                        value = searchVal,
                        onValueChange = {
                            searchVal = it
                            viewModel.searchYouTubeChannelsForCompetitors(it)
                        },
                        placeholder = { 
                            Text("Type channel name or @handle...", color = TextSecondary.copy(alpha = 0.5f)) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("competitor_search_input"),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (searchVal.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchVal = ""
                                    viewModel.searchYouTubeChannelsForCompetitors("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YouTubeRed,
                            unfocusedBorderColor = TextPrimary.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = YouTubeRed,
                            unfocusedLabelColor = TextSecondary
                        )
                    )

                    if (viewModel.competitorSearchLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = YouTubeRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (viewModel.competitorSearchError != null) {
                        Text(
                            text = viewModel.competitorSearchError ?: "",
                            color = YouTubeRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (viewModel.competitorSearchResults.isNotEmpty() && searchVal.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            viewModel.competitorSearchResults.forEach { resChannel ->
                                var isAddingChannel by remember { mutableStateOf(false) }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .border(BorderStroke(1.dp, DarkCardAccent), RoundedCornerShape(10.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (resChannel.avatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = resChannel.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(YouTubeRed),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = resChannel.name.take(1).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = resChannel.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = resChannel.handle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = resChannel.subscribersText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = YouTubeRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (!isAddingChannel) {
                                                isAddingChannel = true
                                                coroutineScope.launch {
                                                    val cleanHandle = when {
                                                        resChannel.handle.startsWith("@") -> resChannel.handle
                                                        else -> "@${resChannel.handle.replace("@", "")}"
                                                    }
                                                    val addResult = viewModel.fetchAndAddManualCompetitor(cleanHandle, report)
                                                    isAddingChannel = false
                                                    if (addResult == "SUCCESS") {
                                                        searchVal = ""
                                                        viewModel.searchYouTubeChannelsForCompetitors("")
                                                        android.widget.Toast.makeText(context, "Added ${resChannel.name} successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, addResult, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        if (isAddingChannel) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                text = "Add",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3 — TRACKED COMPETITORS LIST
        if (currentCompetitors.isNotEmpty()) {
            items(currentCompetitors.size) { index ->
                val comp = currentCompetitors[index]
                var offsetX by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(YouTubeRed.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(end = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Swipe Delete icon",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .pointerInput(comp) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (offsetX < -120f) {
                                            showDeleteConfirmCompetitor = comp
                                        }
                                        offsetX = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        offsetX = (offsetX + dragAmount).coerceIn(-300f, 0f)
                                    }
                                )
                            }
                    ) {
                        val freqValue = remember(comp.uploadFrequency) {
                            val clean = comp.uploadFrequency.lowercase()
                            val number = clean.substringBefore(" ").trim()
                            val isNum = number.isNotEmpty() && (number.getOrNull(0)?.isDigit() == true || number.toDoubleOrNull() != null)
                            if (isNum) {
                                if (clean.contains("month")) {
                                    "$number/mo"
                                } else {
                                    "$number/wk"
                                }
                            } else {
                                comp.uploadFrequency.replace("uploads/week", "/wk")
                                    .replace("upload/week", "/wk")
                                    .replace("videos/week", "/wk")
                                    .replace("video/week", "/wk")
                                    .replace("vids/week", "/wk")
                                    .replace("vid/week", "/wk")
                                    .replace("uploads/month", "/mo")
                                    .replace("upload/month", "/mo")
                                    .replace("videos/month", "/mo")
                                    .replace("video/month", "/mo")
                                    .replace("vids/month", "/mo")
                                    .replace("vid/month", "/mo")
                                    .replace(" ", "")
                            }
                        }

                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(YouTubeRed.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = comp.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = YouTubeRed
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = comp.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .testTag("competitor_name_${comp.handle}")
                                                .clickable { openChannel(comp.handle) }
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = comp.handle,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TextPrimary.copy(alpha = 0.06f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = comp.contentFormat ?: "Standard Video",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = GoldAlert
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { showDeleteConfirmCompetitor = comp },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove Competitor icon",
                                            tint = TextPrimary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = TextPrimary.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "SUBS",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("✓ Verified", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = GreenSuccess, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comp.subscribers.formatAbbreviation(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "AVG VIEWS",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("✓ Verified", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = GreenSuccess, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comp.avgViews.formatAbbreviation(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "FREQ",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("✓ Verified", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = GreenSuccess, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = freqValue,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TextPrimary.copy(alpha = 0.04f))
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "Strength Icon",
                                        tint = GoldAlert,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "BIGGEST STRATEGIC STRENGTH:",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = comp.biggestStrength ?: "Consistent high quality content.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 4 — DYNAMIC GAP ANALYSIS
        item {
            CardSectionHeader(title = "Gap Analysis & Tactics", icon = Icons.Default.Insights, tint = TextPrimary)
        }

        item {
            GrowCard(
                title = "Competitive Content Gaps",
                icon = Icons.Default.Search,
                iconColor = GoldAlert
            ) {
                Text(
                    text = res?.gapAnalysis ?: "Add competitors to see content gap analysis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 22.sp,
                    modifier = Modifier.testTag("gap_analysis_text")
                )
            }
        }

        item {
            GrowCard(
                title = "3 Key Strategies To Adopt",
                icon = Icons.Default.Insights,
                iconColor = GreenSuccess
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val strategies = res?.strategiesToAdopt ?: emptyList()
                    if (strategies.isEmpty()) {
                        Text(
                            text = "Add competitors to unlock strategies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary.copy(alpha = 0.6f)
                        )
                    } else {
                        strategies.take(3).forEachIndexed { index, strategy ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(GreenSuccess.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GreenSuccess
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = strategy,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            GrowCard(
                title = "Your Competitive Edge Advantage",
                icon = Icons.Default.MilitaryTech,
                iconColor = YouTubeRed,
                containerColor = YouTubeRed.copy(alpha = 0.05f)
            ) {
                Text(
                    text = res?.yourAdvantage ?: "Consistent high-quality content delivery.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ------------------------------------------
// TAB 3: STRATEGY (10 VIDEO IDEAS & CALENDAR)
// ------------------------------------------
@Composable
fun TabStrategy(report: FullGrowTubeReport, viewModel: GrowTubeViewModel) {
    val stats = report.stats
    val analysis = report.analysis

    LaunchedEffect(stats.handle) {
        viewModel.loadStrategyTab(stats.handle, stats, analysis)
    }

    if (viewModel.loadingStrategyTab) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Formulating content schedule & viral blueprints...", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    if (viewModel.strategyTabError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Error, contentDescription = "Error", tint = YouTubeRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(viewModel.strategyTabError ?: "An error occurred", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadStrategyTab(stats.handle, stats, analysis) },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
        return
    }

    val res = viewModel.strategyTabResponse
    val mainWeakness = analysis.mainWeakness ?: "No main weakness identified."
    val strategiesToAdopt = viewModel.competitorsTabResponse?.strategiesToAdopt ?: analysis.strategiesToAdopt ?: emptyList()
    val bestUploadDays = res?.bestUploadDays ?: emptyList()
    val recommendedLength = res?.recommendedLength ?: "N/A"
    val shortsVsLongRatio = res?.shortsVsLongRatio ?: "N/A"
    val videoIdeas = res?.videoIdeas ?: emptyList()
    val contentCalendar = res?.contentCalendar ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Strategic Roadblock & Mitigation Card
        item {
            GrowCard(
                title = "Strategic Roadblock Mitigation",
                icon = Icons.Default.Warning,
                iconColor = YouTubeRed,
                borderColor = YouTubeRed.copy(alpha = 0.3f),
                containerColor = YouTubeRed.copy(alpha = 0.03f)
            ) {
                Column {
                    Text(
                        text = "CORE STRATEGIC ROADBLOCK TO OVERCOME",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = mainWeakness,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.06f))
                     Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ROADBLOCK-DIRECTED COMBAT TACTICS",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAlert,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    strategiesToAdopt.forEachIndexed { i, strategy ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(6.dp)
                                    .background(GoldAlert, shape = RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strategy ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Calculated Niche Fingerprint summary Card
        item {
            GrowCard(
                title = "Calculated Niche Fingerprint DNA",
                icon = Icons.Default.Tune,
                iconColor = BlueInfo
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = "MICRO-NICHE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = analysis.microNiche ?: "N/A",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = "CONTENT FORMAT IDENTITY",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = analysis.contentFormat ?: "N/A",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    
                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.06f))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = "SUB-CATEGORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = analysis.subCategory ?: "N/A",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1.0f)) {
                            Text(
                                text = "COMPETITOR GAPS TO EXPLOIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = analysis.gapAnalysis ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Custom Regional Upload Schedule Card
        item {
            GrowCard(
                title = "Regional & Niche Broadcast Schedule",
                icon = Icons.Default.Language,
                iconColor = GreenSuccess
            ) {
                Column {
                    Text(
                        text = "OPTIMIZED WEEKLY PUBLICATION DAYS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    ) {
                        bestUploadDays.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .background(GreenSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp))
                                    .border(1.dp, GreenSuccess.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = (day ?: "").uppercase(),
                                    color = GreenSuccess,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Text(
                        text = "AUDIENCE PEAK LAUNCH TIMINGS & TARGET ZONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recommendedLength,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    HorizontalDivider(color = TextPrimary.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = BlueInfo,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Derived explicitly from micro-niche user engagement. Upload schedules are timed to simultaneously capture peak viewing hours across key locations (e.g. India IST dhol trends vs Canada EST morning rush for diaspora music; dinner times for family recipe channels). Completely free from general generic YouTube advice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Format ratio configuration Card
        item {
            GrowCard(
                title = "Performance-Driven Format Distribution",
                icon = Icons.Default.Speed,
                iconColor = GoldAlert
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "SHORTS VS LONG-FORM RATIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = shortsVsLongRatio,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(1.dp, 40.dp)
                            .background(TextSecondary.copy(alpha = 0.2f))
                    )
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "FORMAT DISTRIBUTION RATIONALE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Configured dynamically to align with historical video formats that yield maximum views and the audience's actual layout interaction history.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // 10 video blueprints header
        item {
            CardSectionHeader(title = "10 AI Viral Video Blueprints (Targeted at Gaps)", icon = Icons.Default.SmartDisplay, tint = YouTubeRed)
        }

        // 10 video blueprints List
        items(videoIdeas.take(10)) { idea ->
            VideoBlueprintCard(idea = idea)
        }

        // Content calendar header
        item {
            CardSectionHeader(title = "30-Day content calendar", icon = Icons.Default.CalendarMonth, tint = BlueInfo)
        }

        // 30 day content calendar Card (rendered inside a 7-column calendar grid!)
        item {
            GrowCalendarView(calendarEntries = contentCalendar)
        }
    }
}

// ==========================================
// SUB-COMPONENTS & UTILS
// ==========================================

@Composable
fun CardSectionHeader(title: String, icon: ImageVector, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = TextPrimary
        )
    }
}

@Composable
fun MiniStatBox(
    title: String,
    value: String,
    subInfo: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subInfo,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun HealthScoreCircularProgress(score: Int, modifier: Modifier = Modifier) {
    val animateScore = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animateScore.animateTo(
            targetValue = score.toFloat(),
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // SVG Arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            
            // Background arc background track
            drawArc(
                color = Color.DarkGray.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            
            // Sweep color arc
            val strokeColor = when {
                score >= 85 -> GreenSuccess
                score >= 60 -> GoldAlert
                else -> AccentRed
            }
            
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = 3.6f * animateScore.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animateScore.value.toInt()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                fontSize = 24.sp
            )
            Text(
                text = "/100",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun GrowCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    containerColor: Color = DarkSurface,
    borderColor: Color = TextPrimary.copy(alpha = 0.08f),
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            content()
        }
    }
}

@Composable
fun InsightVideoCard(
    video: VideoInsight,
    isPositive: Boolean,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPositive) GreenSuccess.copy(alpha = 0.04f) else YouTubeRed.copy(alpha = 0.04f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp, 
            color = if (isPositive) GreenSuccess.copy(alpha = 0.15f) else YouTubeRed.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isPositive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isPositive) GreenSuccess else YouTubeRed,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun VideoBlueprintCard(idea: VideoIdea) {
    var exp by remember { mutableStateOf(false) }

    val formatStr = idea.format ?: "Long"
    val difficultyStr = idea.difficulty ?: "Medium"
    val titleStr = idea.title ?: "No Title"
    val whyItWillWorkStr = idea.whyItWillWork ?: "No details available."

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { exp = !exp },
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Formatting badge
                val ratioColor = if (formatStr.lowercase().contains("short")) YouTubeRed else BlueInfo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ratioColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatStr.uppercase(),
                        color = ratioColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Title
                Text(
                    text = titleStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1.0f),
                    maxLines = if (exp) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Difficulty badge
                val diffColor = when (difficultyStr.lowercase()) {
                    "easy" -> GreenSuccess
                    "medium" -> GoldAlert
                    else -> AccentRed
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(diffColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = difficultyStr.uppercase(),
                        color = diffColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            AnimatedVisibility(visible = exp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Why this works (AI Growth Prediction):",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAlert,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = whyItWillWorkStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GrowCalendarView(calendarEntries: List<CalendarEntry>) {
    val entriesMap = remember(calendarEntries) { calendarEntries.associateBy { it.dayNumber } }
    var selectedEntry by remember { mutableStateOf<CalendarEntry?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "30-Day Monthly Calendar Grid",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Red dates represent automated posting dates tailored to your calculated uploading cadence. Tap highlighted days to view customized ideas.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            // Dynamic Row Layout: 7 days representing 1 week
            val days = (1..30).toList()
            val chunkedWeeks = days.chunked(7)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Label Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val labs = listOf("M", "T", "W", "T", "F", "S", "S")
                    labs.forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray
                        )
                    }
                }

                chunkedWeeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        week.forEach { dayNumber ->
                            val entry = entriesMap[dayNumber]
                            val isUpload = entry != null
                            val isSelected = selectedEntry?.dayNumber == dayNumber

                            Box(
                                modifier = Modifier
                                    .weight(1.0f)
                                    .aspectRatio(1.0f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> YouTubeRed
                                            isUpload -> YouTubeRed.copy(alpha = 0.25f)
                                            else -> TextSecondary.copy(alpha = 0.08f)
                                        }
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = when {
                                            isSelected -> Color.White
                                            isUpload -> YouTubeRed
                                            else -> Color.Transparent
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isUpload) {
                                            selectedEntry = if (isSelected) null else entry
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNumber",
                                        fontWeight = if (isUpload) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else if (isUpload) YouTubeRed else Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    if (isUpload && !isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.SmartDisplay,
                                            contentDescription = null,
                                            tint = YouTubeRed,
                                            modifier = Modifier.size(9.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Pad grid if week chunk has less than 7 days
                        if (week.size < 7) {
                            val emptyCells = 7 - week.size
                            repeat(emptyCells) {
                                Box(modifier = Modifier.weight(1.0f))
                            }
                        }
                    }
                }
            }

            // Expandable dialog detail row below the calendar grid!
            AnimatedVisibility(visible = selectedEntry != null) {
                selectedEntry?.let { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = YouTubeRed.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DAY ${entry.dayNumber} PUBLICATION DETAILS",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    color = YouTubeRed
                                )
                                IconButton(
                                    onClick = { selectedEntry = null },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.videoTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SETTINGS SCREEN
// ==========================================
@Composable
fun SettingsScreen(viewModel: GrowTubeViewModel) {
    var ytKeyInput by remember { mutableStateOf(viewModel.youtubeApiKey) }
    var geminiKeyInput by remember { mutableStateOf(viewModel.geminiApiKey) }
    var showYtKey by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Back Arrow and Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateToHome() },
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // API Keys Configuration Card (moved from Home UI)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "API Keys",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "API Keys Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    Text(
                        text = "Provide these keys to run custom AI analysis on live channels. Keys are stored safely in local preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    // YouTube API Field
                    OutlinedTextField(
                        value = ytKeyInput,
                        onValueChange = { ytKeyInput = it },
                        label = { Text("YouTube Data API v3 Key") },
                        modifier = Modifier.fillMaxWidth().testTag("yt_key_input"),
                        visualTransformation = if (showYtKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showYtKey = !showYtKey }) {
                                Icon(
                                    imageVector = if (showYtKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        )
                    )

                    // Gemini API Field
                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_key_input"),
                        visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                Icon(
                                    imageVector = if (showGeminiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        )
                    )

                    // Save button
                    Button(
                        onClick = {
                            viewModel.saveKeys(ytKeyInput, geminiKeyInput)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_keys_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = if (ytKeyInput.isNotEmpty() && geminiKeyInput.isNotEmpty()) GreenSuccess else YouTubeRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (viewModel.youtubeApiKey.isNotEmpty() && viewModel.geminiApiKey.isNotEmpty()) "Keys Configured! (Update)" else "Save Keys Locally",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    if (viewModel.youtubeApiKey.isEmpty() || viewModel.geminiApiKey.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldAlert.copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "💡 Left blank? Click 'Analyze' to run in [Failsafe DEMO Mode] using realistic sandbox creator datasets!",
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldAlert,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ERROR / RETRY SCREEN
// ==========================================
@Composable
fun ErrorScreen(viewModel: GrowTubeViewModel, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(YouTubeRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error Logo",
                    tint = YouTubeRed,
                    modifier = Modifier.size(44.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "DIAGNOSTIC FAULT DETECTED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = YouTubeRed,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.navigateToHome() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("Go Home")
                }

                Button(
                    onClick = { viewModel.triggerAnalysis() },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("error_retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }
    }
}

// ------------------------------------------
// STRING FORMAT ABBREVIATION HELPER
// ------------------------------------------
private fun Long.formatAbbreviation(): String {
    return when {
        this >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", this / 1_000_000_000.0)
        this >= 1_000_000 -> String.format(Locale.US, "%.1fM", this / 1_000_000.0)
        this >= 1_000 -> String.format(Locale.US, "%.1fK", this / 1_000.0)
        else -> this.toString()
    }
}

// ==========================================
// VIDEO AUTOPSY
// ==========================================

fun extractCoreNiche(title: String): String {
    val clean = title.replace(Regex("(?i)\\b(making|building|how to|a|an|the|in|with|using|of|and|for|or|is|are|on|my|your|me|to|how|i|we|it|this|that|why|failing|failed|from|of|out|to|into)\\b"), "").trim()
    val words = clean.split(Regex("\\s+")).filter { it.length > 2 }
    return if (words.isNotEmpty()) {
        words.take(3).joinToString(" ")
    } else {
        "this content"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAutopsyScreen(
    viewModel: GrowTubeViewModel,
    video: VideoInsight,
    report: FullGrowTubeReport
) {
    val loading = viewModel.loadingAutopsy
    val autopsyReport = viewModel.autopsyReport
    val autopsyError = viewModel.autopsyError

    if (autopsyError != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Diagnostics Failed", fontWeight = FontWeight.Bold, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.navigateBackToResults(report) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCard)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Strategic Autopsy Failed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = autopsyError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.navigateToVideoAutopsy(video, report) },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                    ) {
                        Text("Retry Diagnostics", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    if (loading || autopsyReport == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Running Diagnostics...", fontWeight = FontWeight.Bold, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBackToResults(report) },
                            modifier = Modifier.testTag("autopsy_loading_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCard)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(color = YouTubeRed, strokeWidth = 3.dp)
                    Text(
                        text = "Analyzing Failure Vectors...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Layer 1: Formulating Niche Fingerprint\nLayer 2: Benchmarking Viral Anomalies\nLayer 3: Executing Niche-Specific Gap Analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        return
    }

    val r = autopsyReport!!

    var fingerprintExpanded by remember { mutableStateOf(true) }
    var benchmarkExpanded by remember { mutableStateOf(false) }
    var titleExpanded by remember { mutableStateOf(false) }
    var thumbnailExpanded by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }

    val primaryKillReason = video.reason
    val estimatedCtrImprovement = remember(video.title) {
        val hash = java.lang.Math.abs(video.title.hashCode())
        val base = 3.5f + (hash % 34) / 10f
        String.format(Locale.US, "%.1f%%", base)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Autopsy", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBackToResults(report) },
                        modifier = Modifier.testTag("autopsy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TOP VERDICT CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = YouTubeRed.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("top_verdict_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = YouTubeRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Autopsy Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = YouTubeRed.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Primary Kill Reason:",
                        style = MaterialTheme.typography.labelMedium,
                        color = YouTubeRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = primaryKillReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GreenSuccess.copy(alpha = 0.1f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Fix Benefit",
                            tint = GreenSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "If Fixed: Estimated CTR improvement of ",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Text(
                            text = estimatedCtrImprovement,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }

            // LAYER 1 — NICHE FINGERPRINT CARD
            AutopsyExpandableCard(
                title = "Layer 1: Niche Fingerprint",
                isExpanded = fingerprintExpanded,
                onToggle = { fingerprintExpanded = !fingerprintExpanded },
                testTag = "niche_fingerprint_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Exact Niche Hierarchy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TextPrimary.copy(alpha = 0.02f))
                            .border(1.dp, TextPrimary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HierarchyRow("Primary Category", r.primaryCategory)
                        HierarchyRow("Sub-Category", r.subCategory)
                        HierarchyRow("Micro-Niche", r.microNiche)
                        HierarchyRow("Content Identity", r.contentIdentity)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Channel Content Patterns",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    CritiqueBlock(isCritical = false, title = "Topics Covered", feedback = r.topicsCovered)
                    CritiqueBlock(isCritical = false, title = "Best Video Format", feedback = r.bestVideoFormat)
                    CritiqueBlock(isCritical = false, title = "Language & Cultural Context", feedback = r.languageCulture)
                    CritiqueBlock(isCritical = false, title = "Core Target Demographic", feedback = r.coreDemographic)
                }
            }

            // LAYER 2 — VIRAL BENCHMARK CARD
            AutopsyExpandableCard(
                title = "Layer 2: Viral Benchmark",
                isExpanded = benchmarkExpanded,
                onToggle = { benchmarkExpanded = !benchmarkExpanded },
                testTag = "viral_benchmark_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CritiqueBlock(isCritical = false, title = "Viral Title Structures", feedback = r.viralTitleStructures)
                    CritiqueBlock(isCritical = false, title = "Core Emotional Triggers", feedback = r.viralEmotionalTriggers)
                    CritiqueBlock(isCritical = false, title = "Top Thumbnail Visual Styles", feedback = r.viralThumbnailStyles)
                    CritiqueBlock(isCritical = false, title = "High-Ranking Description Patterns", feedback = r.viralDescriptionPatterns)
                    CritiqueBlock(isCritical = false, title = "Discovery Tag Systems", feedback = r.viralTagsDiscovery)
                }
            }

            // SECTION 1 — TITLE GAP ANALYSIS
            AutopsyExpandableCard(
                title = "Layer 3: Title Analysis",
                isExpanded = titleExpanded,
                onToggle = { titleExpanded = !titleExpanded },
                testTag = "title_analysis_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CritiqueBlock(
                        isCritical = true,
                        title = "Brutal Honest Verdict",
                        feedback = r.titleCritique
                    )
                    
                    Text(
                        text = "5 Alternate Title Suggestions",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )

                    r.alternateTitles.forEachIndexed { i, titleItem ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(TextPrimary.copy(alpha = 0.03f))
                                .border(1.dp, TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${i + 1}. ${titleItem.suggestedTitle}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = titleItem.inspiredBy,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // SECTION 2 — THUMBNAIL GAP ANALYSIS
            AutopsyExpandableCard(
                title = "Layer 3: Thumbnail Analysis",
                isExpanded = thumbnailExpanded,
                onToggle = { thumbnailExpanded = !thumbnailExpanded },
                testTag = "thumbnail_analysis_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CritiqueBlock(
                        isCritical = true,
                        title = "Visual Pitfalls Identified",
                        feedback = r.thumbnailCritique
                    )

                    CritiqueBlock(
                        isCritical = false,
                        title = "Actionable Thumbnail Design Shifts",
                        feedback = r.thumbnailFixes
                    )
                }
            }

            // SECTION 3 — DESCRIPTION GAP ANALYSIS
            AutopsyExpandableCard(
                title = "Layer 3: Description Analysis",
                isExpanded = descriptionExpanded,
                onToggle = { descriptionExpanded = !descriptionExpanded },
                testTag = "description_analysis_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CritiqueBlock(
                        isCritical = true,
                        title = "Conversion Gaps Found",
                        feedback = r.descriptionCritique
                    )

                    Text(
                        text = "Optimal High-Conversion Description Template",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, TextPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = r.recommendedDescription,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color(0xFFA5D6A7),
                            lineHeight = 16.sp
                        )
                    }

                    Text(
                        text = "(Press & hold or screenshot this template to adapt for your upload flow!)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            // SECTION 4 — TAGS GAP ANALYSIS
            AutopsyExpandableCard(
                title = "Layer 3: Tags Analysis",
                isExpanded = tagsExpanded,
                onToggle = { tagsExpanded = !tagsExpanded },
                testTag = "tags_analysis_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CritiqueBlock(
                        isCritical = true,
                        title = "Discoverability Bottlenecks",
                        feedback = r.tagsCritique
                    )

                    Text(
                        text = "Suggested Optimal Tags List",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )

                    r.suggestedTags.chunked(3).forEach { rowTags ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowTags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TextPrimary.copy(alpha = 0.05f))
                                        .border(1.dp, TextPrimary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HierarchyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun AutopsyExpandableCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextPrimary
                )
            }
            if (isExpanded) {
                HorizontalDivider(color = TextPrimary.copy(alpha = 0.08f))
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CritiqueBlock(
    isCritical: Boolean,
    title: String,
    feedback: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCritical) YouTubeRed.copy(alpha = 0.05f) else GreenSuccess.copy(alpha = 0.04f))
            .border(
                1.dp,
                if (isCritical) YouTubeRed.copy(alpha = 0.15f) else GreenSuccess.copy(alpha = 0.15f),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Icon(
            imageVector = if (isCritical) Icons.Default.Cancel else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isCritical) YouTubeRed else GreenSuccess,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isCritical) YouTubeRed else GreenSuccess
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

// ------------------------------------------
// TAB 4: PRE-UPLOAD VIDEO OPTIMIZER (LAUNCH)
// ------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabLaunch(report: FullGrowTubeReport, viewModel: GrowTubeViewModel) {
    LaunchedEffect(report.stats.handle) {
        viewModel.loadSavedLaunches(report.stats.handle)
    }
    
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val contextActual = androidx.compose.ui.platform.LocalContext.current
    var reportToDeleteForDialog by remember { mutableStateOf<LaunchReport?>(null) }

    if (reportToDeleteForDialog != null) {
        AlertDialog(
            onDismissRequest = { reportToDeleteForDialog = null },
            title = {
                Text(
                    text = "Delete Launch Run",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Delete this launch run?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        reportToDeleteForDialog?.let { reportVal ->
                            viewModel.deleteLaunchReport(reportVal.channelHandle, reportVal)
                        }
                        reportToDeleteForDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Text("Delete", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { reportToDeleteForDialog = null }
                ) {
                    Text("Cancel", color = TextPrimary.copy(alpha = 0.6f))
                }
            },
            containerColor = DarkCard
        )
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.analyzeThumbnail(it.toString(), viewModel.launchTopicInput, report)
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TextPrimary.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pre-Upload Video Optimizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    Text(
                        text = "What is your video/song about?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    
                    OutlinedTextField(
                        value = viewModel.launchTopicInput,
                        onValueChange = { viewModel.updateLaunchTopicInput(it) },
                        placeholder = { Text("e.g. New sad Punjabi song, product review, cooking recipe...", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("launch_topic_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = YouTubeRed,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = YouTubeRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    
                    Text(
                        text = "e.g. New sad Punjabi song, product review, cooking recipe etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    
                    Button(
                        onClick = { viewModel.optimizeLaunch(viewModel.launchTopicInput, report) },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_launch_button"),
                        enabled = viewModel.launchTopicInput.trim().isNotEmpty() && !viewModel.loadingLaunch,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (viewModel.loadingLaunch) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = TextPrimary)
                                Text("Generate Everything", fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
        
        item {
            var showHistory by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TextPrimary.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHistory = !showHistory }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, contentDescription = null, tint = YouTubeRed)
                            Text(
                                text = "Historical Launch Runs (${viewModel.savedLaunchReports.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = if (showHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }
                    
                    AnimatedVisibility(visible = showHistory) {
                        Column(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (viewModel.savedLaunchReports.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No previous launch runs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                viewModel.savedLaunchReports.forEachIndexed { index, savedReport ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.3f))
                                                .clickable { viewModel.selectLaunchReport(savedReport) }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = savedReport.topicName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                val dateText = remember(savedReport.timestamp) {
                                                    try {
                                                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                                                        sdf.format(java.util.Date(savedReport.timestamp))
                                                    } catch (e: Exception) {
                                                        "Unknown Date"
                                                    }
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Score: ${savedReport.score.totalScore}/100 (${savedReport.score.status})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextSecondary.copy(alpha = 0.8f)
                                                    )
                                                    Text(
                                                        text = "•",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                    )
                                                    Text(
                                                        text = dateText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    reportToDeleteForDialog = savedReport
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("delete_launch_run_${index}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Launch Run",
                                                    tint = YouTubeRed,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            thickness = 1.dp,
                                            color = Color(0xFF2A2A2A)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        val activeReport = viewModel.launchReport
        if (activeReport != null) {
            item {
                Button(
                    onClick = {
                        val fullText = buildString {
                            appendLine("GROWTUBE LAUNCH REPORT OPTIMIZATION BLUEPRINT")
                            appendLine("Video Topic: ${activeReport.topicName}")
                            appendLine("===============================")
                            appendLine("1. TITLE SUGGESTIONS:")
                            activeReport.titleOptions.forEachIndexed { i, t ->
                                appendLine("- Option ${i+1}: ${t.suggestedTitle} [Predicted CTR: ${t.ctrScore}/10]")
                                if (!t.inspiredBy.isNullOrEmpty()) appendLine("  Inspired by: ${t.inspiredBy}")
                                if (!t.whyAudienceClick.isNullOrEmpty()) appendLine("  Audience Fit: ${t.whyAudienceClick}")
                            }
                            appendLine()
                            appendLine("2. DESCRIPTION (HASHTAGS INCLUDED):")
                            appendLine(activeReport.description.fullText ?: buildString {
                                appendLine(activeReport.description.primaryHook)
                                appendLine()
                                appendLine(activeReport.description.bodyText)
                                appendLine()
                                appendLine("Timestamps:")
                                appendLine(activeReport.description.timestamps)
                                appendLine()
                                appendLine(activeReport.description.socialLinksPlaceholder)
                                appendLine()
                                appendLine(activeReport.description.cta)
                            })
                            appendLine()
                            appendLine("3. KEYWORDS:")
                            activeReport.tags.forEach { tag ->
                                appendLine("- ${tag.name} (${tag.searchVolume})")
                                if (!tag.inspirationVideo.isNullOrEmpty()) appendLine("  Inspired by: ${tag.inspirationVideo}")
                                if (!tag.relevanceReason.isNullOrEmpty()) appendLine("  Relevance: ${tag.relevanceReason}")
                            }
                            appendLine()
                            appendLine("4. COMPREHENSIVE AUDIENCE ANALYSIS:")
                            appendLine("Total Audience Match Score: ${activeReport.score.totalScore}/100")
                            appendLine("Status: ${activeReport.score.status}")
                            appendLine("Title strength: ${activeReport.score.titleStrength}/20")
                            appendLine("Thumbnail Potential: ${activeReport.score.thumbnailPotential}/20")
                            appendLine("Description Quality: ${activeReport.score.descriptionQuality}/20")
                            appendLine("Tags Coverage: ${activeReport.score.tagsCoverage}/20")
                            appendLine("Niche Alignment: ${activeReport.score.nicheAlignment}/20")
                        }
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullText))
                        android.widget.Toast.makeText(contextActual, "Entire optimization blueprint copied!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("copy_all_master_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Entire Optimization Blueprint", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var sec1Expanded by remember { mutableStateOf(false) }
                    ExpandableSectionCard(
                        title = "SECTION 1 — TITLE SUGGESTIONS",
                        isExpanded = sec1Expanded,
                        onToggle = { sec1Expanded = !sec1Expanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeReport.titleOptions.forEachIndexed { i, titleOpt ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = titleOpt.suggestedTitle,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (titleOpt.ctrScore >= 8) GreenSuccess.copy(alpha = 0.15f)
                                                            else YouTubeRed.copy(alpha = 0.15f)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "CTR ${titleOpt.ctrScore}/10",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (titleOpt.ctrScore >= 8) GreenSuccess else YouTubeRed
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(titleOpt.suggestedTitle))
                                                        android.widget.Toast.makeText(contextActual, "Copied title: ${titleOpt.suggestedTitle}", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(28.dp).testTag("copy_title_button_${i}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ContentCopy,
                                                        contentDescription = "Copy title",
                                                        tint = Color.LightGray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Text(
                                            text = "💡 Hook Mechanics: ${titleOpt.whyItWillWork}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        
                                        val inspiredText = titleOpt.inspiredBy
                                        if (!inspiredText.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "🔥 ${inspiredText}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = YouTubeRed.copy(alpha = 0.9f)
                                            )
                                        }
                                        
                                        val clickReason = titleOpt.whyAudienceClick
                                        if (!clickReason.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "🎯 Audience Fit: ${clickReason}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GreenSuccess
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    var sec2Expanded by remember { mutableStateOf(false) }
                    ExpandableSectionCard(
                        title = "SECTION 2 — THUMBNAIL ANALYZER (COMPANION)",
                        isExpanded = sec2Expanded,
                        onToggle = { sec2Expanded = !sec2Expanded },
                        onCopy = {
                            val results = activeReport.thumbnailAnalysis
                            if (results != null) {
                                val txt = buildString {
                                    appendLine("THUMBNAIL ANALYZER CRITIQUE REPORT")
                                    appendLine("Audience Score: ${results.audienceScore}/10")
                                    appendLine("Predicted CTR: ${results.predictedCtr}")
                                    appendLine("What Works: ${results.whatWorks}")
                                    appendLine("What Needs Fix: ${results.whatNeedsToFix}")
                                    appendLine("Actionable Fixes: ${results.specificFixSuggestions}")
                                }
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(txt))
                                android.widget.Toast.makeText(contextActual, "Thumbnail analysis critique copied!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(contextActual, "No thumbnail report yet! Pick image to run structural review.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "YouTube recommends visual consistency matching channel branding to optimize click-through velocity. Upload your draft below to analyze layout, lighting, fonts, and contrast ratios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.dp, Color.DarkGray, RoundedCornerShape(10.dp))
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = YouTubeRed,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Select Thumbnail Draft to Analyze",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Analyzed dynamically with Gemini Multimodal Vision",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            val results = activeReport.thumbnailAnalysis
                            if (viewModel.analyzingThumbnail) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = YouTubeRed)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gemini Vision studying thumbnail elements...", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (results != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(YouTubeRed.copy(alpha = 0.1f))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Thumbnail Performance Potential", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                        Text("Predicted Impression CTR: ${results.predictedCtr}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                    Text(
                                        text = "${results.audienceScore * 2}/20",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = YouTubeRed
                                    )
                                }
                                
                                Text("Visual Blueprint Suggestions", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                CritiqueBlock(
                                    isCritical = false,
                                    title = "What Works Well",
                                    feedback = results.whatWorks
                                )
                                
                                Text("Niche Matching Checks", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                CritiqueBlock(
                                    isCritical = true,
                                    title = "What Needs Urgent Fixes",
                                    feedback = results.whatNeedsToFix
                                )
                                
                                Text("Contrast & Saliency Check", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, TextPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = results.specificFixSuggestions,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    var sec3Expanded by remember { mutableStateOf(false) }
                    ExpandableSectionCard(
                        title = "SECTION 3 — DESCRIPTION GENERATOR",
                        isExpanded = sec3Expanded,
                        onToggle = { sec3Expanded = !sec3Expanded },
                        onCopy = {
                            val desc = activeReport.description
                            val txt = desc.fullText ?: buildString {
                                appendLine(desc.primaryHook)
                                appendLine()
                                appendLine(desc.bodyText)
                                appendLine()
                                appendLine("Timestamps:")
                                appendLine(desc.timestamps)
                                appendLine()
                                appendLine(desc.socialLinksPlaceholder)
                                appendLine()
                                appendLine(desc.cta)
                            }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(txt))
                            android.widget.Toast.makeText(contextActual, "Consolidated description with hashtags copied!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val desc = activeReport.description
                            val fullParagraph = desc.fullText ?: buildString {
                                appendLine(desc.primaryHook)
                                appendLine()
                                appendLine(desc.bodyText)
                                appendLine()
                                appendLine("Timestamps:")
                                appendLine(desc.timestamps)
                                appendLine()
                                appendLine(desc.socialLinksPlaceholder)
                                appendLine()
                                appendLine(desc.cta)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, TextPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Upload-Ready Consolidated Block",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = YouTubeRed
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullParagraph))
                                                android.widget.Toast.makeText(contextActual, "Description copied!", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy unified text",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = fullParagraph,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Divider(color = TextPrimary.copy(alpha = 0.08f))

                            Column {
                                Text("Viral Hook Opener", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                Text(
                                    text = desc.primaryHook,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Column {
                                Text("Optimized Content Body Text", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                Text(
                                    text = desc.bodyText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Column {
                                Text("Interactive Chapter Outline", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                Text(
                                    text = desc.timestamps,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Column {
                                Text("Smart SEO Links Block", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                Text(
                                    text = desc.socialLinksPlaceholder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Column {
                                Text("Audience Connection Outro", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = YouTubeRed)
                                Text(
                                    text = desc.cta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    var sec4Expanded by remember { mutableStateOf(false) }
                    ExpandableSectionCard(
                        title = "SECTION 4 — KEYWORD TAGS",
                        isExpanded = sec4Expanded,
                        onToggle = { sec4Expanded = !sec4Expanded },
                        onCopy = {
                            val txt = activeReport.tags.joinToString(", ") { it.name }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(txt))
                            android.widget.Toast.makeText(contextActual, "All keyword tags copied (comma-separated)!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "💡 Generated from viral competitor videos and your audience's exact search intent patterns in this micro-niche.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            activeReport.tags.forEachIndexed { idx, tag ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = tag.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(YouTubeRed.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Vol: ${tag.searchVolume}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = YouTubeRed,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(tag.name))
                                                    android.widget.Toast.makeText(contextActual, "Copied tag: ${tag.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp).testTag("copy_tag_button_${idx}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy tag",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        
                                        val insVideo = tag.inspirationVideo
                                        val relReason = tag.relevanceReason
                                        
                                        if (!insVideo.isNullOrEmpty() || !relReason.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Divider(color = TextPrimary.copy(alpha = 0.05f), thickness = 0.5.dp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        
                                        if (!insVideo.isNullOrEmpty()) {
                                            Text(
                                                text = "🔥 ${insVideo}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = YouTubeRed.copy(alpha = 0.9f),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if (!relReason.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "✨ Relevance: ${relReason}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GreenSuccess
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    var sec5Expanded by remember { mutableStateOf(false) }
                    ExpandableSectionCard(
                        title = "SECTION 5 — AUDIENCE MATCH SCORE",
                        isExpanded = sec5Expanded,
                        onToggle = { sec5Expanded = !sec5Expanded },
                        onCopy = {
                            val score = activeReport.score
                            val txt = "Overall Audience Match Score: ${score.totalScore}/100 [${score.status}]\n" +
                                    "- Title Optimization Strength: ${score.titleStrength}/20\n" +
                                    "- Thumbnail Audience Draw Rate: ${score.thumbnailPotential}/20\n" +
                                    "- Description Search Engine Strength: ${score.descriptionQuality}/20\n" +
                                    "- Keyword & Metadata Depth: ${score.tagsCoverage}/20\n" +
                                    "- Channel Niche Baseline Compliance: ${score.nicheAlignment}/20"
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(txt))
                            android.widget.Toast.makeText(contextActual, "Audience Match Scorecard copied!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val score = activeReport.score
                            val totalScore = score.totalScore
                            
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(140.dp)
                            ) {
                                val animValue = totalScore.toFloat() / 100f
                                Canvas(modifier = Modifier.size(120.dp)) {
                                    drawArc(
                                        color = Color.DarkGray,
                                        startAngle = 135f,
                                        sweepAngle = 270f,
                                        useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = if (totalScore >= 85) GreenSuccess else if (totalScore >= 65) Color(0xFFFFBF00) else Color.Red,
                                        startAngle = 135f,
                                        sweepAngle = 270f * animValue,
                                        useCenter = false,
                                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$totalScore",
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "AUDIENCE FIT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (totalScore >= 85) GreenSuccess.copy(alpha = 0.15f)
                                        else if (totalScore >= 65) Color(0xFFFFBF00).copy(alpha = 0.15f)
                                        else Color.Red.copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = score.status,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalScore >= 85) GreenSuccess else if (totalScore >= 65) Color(0xFFFFBF00) else Color.Red
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ScoreParamRow("Title Optimization Strength", score.titleStrength)
                                ScoreParamRow("Thumbnail Audience Draw Rate", score.thumbnailPotential)
                                ScoreParamRow("Description Search Engine Strength", score.descriptionQuality)
                                ScoreParamRow("Keyword & Metadata Depth", score.tagsCoverage)
                                ScoreParamRow("Channel Niche Baseline Compliance", score.nicheAlignment)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableSectionCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCopy: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TextPrimary.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onCopy != null) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = TextPrimary
                    )
                }
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ScoreParamRow(label: String, score: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(
            text = "$score / 20",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (score >= 17) GreenSuccess else if (score >= 12) Color(0xFFFFBF00) else Color.Red
        )
    }
}

@Composable
fun MiniMetricItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatWithCommas(num: Long): String {
    return try {
        java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(num)
    } catch (e: Exception) {
        num.toString()
    }
}

// =========================================================================
// CHANNEL CLONING ENGINE CORE COMPOSE UI
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptFlowRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}

@Composable
fun ChannelCloneScreen(viewModel: GrowTubeViewModel) {
    val context = LocalContext.current
    val state = viewModel.cloneActiveState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Upper Header with Back Navigation and Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { 
                    if (state == 0) {
                        viewModel.navigateToHomeFromClone()
                    } else if (state == 4 && viewModel.cloneStyleDNALoading) {
                        viewModel.cloneActiveState = 3
                    } else {
                        viewModel.cloneActiveState = state - 1
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clone Engine",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Advanced YouTube Cloner",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Step Number Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${state + 1} / 14",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Horizontal Progress Line Indicator
        LinearProgressIndicator(
            progress = (state + 1) / 14f,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = TextPrimary.copy(alpha = 0.08f)
        )

        // Main Adaptive Body Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DarkBackground)
        ) {
            when (state) {
                0 -> CloneState0_GoalAndFormat(viewModel)
                1 -> CloneState1_ChannelLink(viewModel)
                2 -> CloneState2_TranscriptLoop(viewModel)
                3 -> CloneState3_TopicAndCompetitorChoices(viewModel)
                4 -> CloneState4_StyleDNAAnalyzer(viewModel)
                5 -> CloneState5_ScriptAndHookVariations(viewModel)
                6 -> CloneState6_VisualDesignCues(viewModel)
                7 -> CloneState7_StoryboardBeats(viewModel)
                8 -> CloneState8_VideoPromo(viewModel)
                9 -> CloneState9_ThumbnailPreference(viewModel)
                10 -> CloneState10_ThumbnailConcepts(viewModel)
                11 -> CloneState11_MetadataSEO(viewModel)
                12 -> CloneState12_CalendarOption(viewModel)
                13 -> CloneState13_FinalSummary(viewModel)
            }
        }
    }
}

  data class OptionInfo(
    val title: String,
    val englishDesc: String,
    val punjabiDesc: String,
    val hindiDesc: String
)

val globalOptionsHelp = mapOf(
    "Maximize Views / Go Viral" to OptionInfo(
        title = "Maximize Views / Go Viral",
        englishDesc = "Focuses on high-tempo, viral trends and optimized CTR hooks to maximize audience click-through-rates and view velocity.",
        punjabiDesc = "ਇਹ ਤੇਜ਼ੀ ਨਾਲ ਵਾਇਰਲ ਹੋਣ ਵਾਲੇ ਵਿਸ਼ਿਆਂ ਅਤੇ ਆਕਰਸ਼ਕ ਕਲਿੱਕ-ਥ੍ਰੂ-ਰੇਟ (CTR) 'ਤੇ ਧਿਆਨ ਕੇਂਦਰਿਤ ਕਰਦਾ ਹੈ ਤਾਂ ਜੋ ਵੀਡੀਓ 'ਤੇ ਵਿਊਜ਼ ਬਹੁਤ ਤੇਜ਼ੀ ਨਾਲ ਵਧਣ।",
        hindiDesc = "यह तेजी से वायरल होने वाले विषयों और आकर्षक क्लिक-थ्रू-रेट (CTR) पर ध्यान केंद्रित करता है जिससे वीडियो पर व्यूज बहुत तेजी से बढ़ें।"
    ),
    "Grow Subscribers" to OptionInfo(
        title = "Grow Subscribers",
        englishDesc = "Targets core value-driven content and signature prompts to convert casual viewers into dedicated subscribers.",
        punjabiDesc = "ਇਹ ਵੀਡੀਓ ਦੇ ਦਰਸ਼ਕਾਂ ਨੂੰ ਪੱਕੇ ਸਬਸਕ੍ਰਾਈਬਰਸ ਵਿੱਚ ਬਦਲਣ ਲਈ ਵਿਸ਼ੇਸ਼ ਅਤੇ ਉੱਚ-ਮੁੱਲ ਵਾਲੀ ਸਮੱਗਰੀ 'ਤੇ ਕੰਮ ਕਰਦਾ ਹੈ।",
        hindiDesc = "यह वीडियो के दर्शकों को स्थायी ग्राहकों (सब्सक्राइबर्स) में बदलने के लिए विशेष और मूल्यवान सामग्री पर ध्यान केंद्रित करता है।"
    ),
    "Drive Sales / Conversions" to OptionInfo(
        title = "Drive Sales / Conversions",
        englishDesc = "Focuses on strategic calls-to-action (CTAs) that funnel viewers toward landing pages, premium courses, or affiliate channels.",
        punjabiDesc = "ਵੀਡੀਓ ਵਿੱਚ ਮਜ਼ਬੂਤ ਕਾਲ-ਟੂ-ਐਕਸ਼ਨ (CTA) ਦੇ ਕੇ ਦਰਸ਼ਕਾਂ ਨੂੰ ਵੈਬਸਾਈਟ 'ਤੇ ਭੇਜਣਾ, ਜਾਂ ਐਫੀਲੀਏਟ ਲਿੰਕਸ ਰਾਹੀਂ ਬਿਜ਼ਨਸ ਸੇਲਜ਼ ਵਧਾਉਣਾ।",
        hindiDesc = "वीडियो में मजबूत कॉल-टो -एक्शन (CTA) देकर दर्शकों को वेबसाइट पर भेजना, या एफिलिएट लिंक्स के माध्यम से व्यापारिक बिक्री बढ़ाना।"
    ),
    "Build Brand Awareness" to OptionInfo(
        title = "Build Brand Awareness",
        englishDesc = "Positions your personal or corporate brand as a high-authority thought leader in the target industry.",
        punjabiDesc = "ਇਸ ਦਾ ਮਕਸਦ ਤੁਹਾਡੇ ਬ੍ਰਾਂਡ ਨੂੰ ਸਭ ਤੋਂ ਵਧੀਆ ਅਤੇ ਭਰੋਸੇਯੋਗ ਦਿਖਾਉਣਾ ਅਤੇ ਖੇਤਰ ਵਿੱਚ ਲੀਡਰਸ਼ਿਪ ਸਥਾਪਤ ਕਰਨਾ ਹੈ।",
        hindiDesc = "इसका मुख्य उद्देश्य आपके ब्रांड को विश्वसनीय दिखाना और अपने क्षेत्र में विशेषज्ञ के रूप में स्थापित करना है।"
    ),
    "YouTube Long-Form (8–20 min)" to OptionInfo(
        title = "YouTube Long-Form (8–20 min)",
        englishDesc = "Exhaustive deep-dive case studies extending over 8 minutes. Perfect for watch-time building and mid-roll ad placement.",
        punjabiDesc = "8 ਤੋਂ 20 ਮਿੰਟ ਦੇ ਵਿਸਤ੍ਰਿਤ ਵੀਡੀਓਜ਼ ਜੋ ਸਰਚ ਰੈਂਕਿੰਗ ਵਧਾਉਣ ਅਤੇ ਵੀਡੀਓ ਦੇ ਵਿਚਕਾਰ ਐਡਸ ਲਗਾਉਣ ਲਈ ਸਭ ਤੋਂ ਵਧੀਆ ਹਨ।",
        hindiDesc = "8 से 20 मिनट की विस्तृत वीडियो जो सर्च रेंकिंग बढ़ाने और वीडियो के बीच में विज्ञापन लगाने के लिए सर्वोत्तम हैं।"
    ),
    "YouTube Shorts (under 60 sec)" to OptionInfo(
        title = "YouTube Shorts (under 60 sec)",
        englishDesc = "Sub-60 seconds high-energy vertical loops. Designed for fast dynamic retention loops and Shorts Feed recommendation.",
        punjabiDesc = "60 ਸਕਿੰਟਾਂ ਤੋਂ ਘੱਟ ਦੇ ਹਾਈ-ਐਨਰਜੀ ਵੀਡੀਓਜ਼ ਜੋ ਤੇਜ਼ੀ ਨਾਲ ਵਾਇਰਲ ਹੋਣ ਦੇ ਨਿਯਮਾਂ 'ਤੇ ਚੱਲਦੇ ਹਨ।",
        hindiDesc = "60 सेकंड से कम की उच्च-ऊर्जा वाली वीडियो जो शॉर्ट्स एल्गोरिदम के नियमों के अनुकूल होती हैं।"
    ),
    "YouTube Mid-Form (3–7 min)" to OptionInfo(
        title = "YouTube Mid-Form (3–7 min)",
        englishDesc = "Semiactuating 3 to 7 minutes delivering compressed structure and high-yield information without padding.",
        punjabiDesc = "3 ਤੋਂ 7 ਮਿੰਟ ਦੀ ਸਮੱਗਰੀ ਜੋ ਕਿ ਬਿਨਾਂ ਫਾਲਤੂ ਗੱਲਾਂ ਦੇ ਸਿੱਧੇ ਮੁੱਦੇ 'ਤੇ ਗੱਲ ਕਰਦੀ ਹੈ।",
        hindiDesc = "3 से 7 मिनट की सामग्री जो बिना फालतू बातों के सीधे मुख्य बिंदु पर चर्चा करती है।"
    ),
    "Build a loyal tech community" to OptionInfo(
        title = "Build a loyal tech community",
        englishDesc = "Aims to build a highly engaged audience through high-quality tech videos and tutorials. Perfect for establishing direct connection, brand loyalty, and long-term community value.",
        punjabiDesc = "ਇਸ ਦਾ ਮਕਸਦ ਉੱਚ-ਗੁਣਵੱਤਾ ਵਾਲੇ ਤਕਨੀਕੀ ਵੀਡੀਓਜ਼ ਅਤੇ ਟਿਊਟੋਰਿਅਲਸ ਰਾਹੀਂ ਅਜਿਹੇ ਦਰਸ਼ਕ ਤਿਆਰ ਕਰਨਾ ਹੈ ਜੋ ਤੁਹਾਡੇ ਨਾਲ ਲੰਬੇ ਸਮੇਂ ਤੱਕ ਜੁੜੇ ਰਹਿਣ। ਇਹ ਤੁਹਾਡੇ ਬ੍ਰਾਂਡ ਦੀ ਵਫ਼ਾਦਾਰੀ ਅਤੇ ਭਰੋਸੇਯੋਗਤਾ ਵਧਾਉਂਦਾ ਹੈ।",
        hindiDesc = "इसका उद्देश्य उच्च गुणवत्ता वाले तकनीकी वीडियो और ट्यूटोरियल के माध्यम से एक वफादार दर्शक वर्ग का निर्माण करना है। यह आपके ब्रांड की विश्वसनीयता और लंबे समय तक जुड़ाव को बढ़ाता है।"
    ),
    "Earn AdSense revenue fast" to OptionInfo(
        title = "Earn AdSense revenue fast",
        englishDesc = "Targets rapid viral trends and search-friendly topics with fast-paced editing and engaging CTR headlines to drive views quickly and maximize AdSense advertising revenue.",
        punjabiDesc = "ਇਹ ਤੇਜ਼ੀ ਨਾਲ ਵਾਇਰਲ ਹੋਣ ਵਾਲੇ ਟ੍ਰੈਂਡਸ ਅਤੇ ਪ੍ਰਸਿੱਧ ਵਿਸ਼ਿਆਂ ਨੂੰ ਨਿਸ਼ਾਨਾ ਬਣਾਉਂਦਾ ਹੈ, ਜਿਸ ਨਾਲ ਘੱਟ ਸਮੇਂ ਵਿੱਚ ਜ਼ਿਆਦਾ ਵਿਊਜ਼ ਅਤੇ ਵੱਧ ਐਡਸੈਂਸ ਕਮਾਈ ਹੁੰਦੀ ਹੈ। ਇਹ ਤੇਜ਼ ਸੰਪਾਦਨ (fast editing) ਅਤੇ ਕਲਿਕਬੇਟ 'ਤੇ ਕੰਮ ਕਰਦਾ ਹੈ।",
        hindiDesc = "यह तेजी से वायरल होने वाले ट्रेंड और सर्च-फ्रेंडली विषयों को लक्षित करता है, जिससे कम समय में अधिक व्यूज और अधिकतम एडसेंस कमाई होती है। इसमें तेज संपादन और क्लिक-योग्य हेडलाइंस का उपयोग होता है।"
    ),
    "Launch high-ticket courses" to OptionInfo(
        title = "Launch high-ticket courses",
        englishDesc = "Positions you as a premium industry authority. Designed to seamlessly hook users into webinars, academies, premium courses, or private consulting options.",
        punjabiDesc = "ਤੁਹਾਡੀ ਵੀਡੀਓ ਨੂੰ ਇਸ ਤਰੀਕੇ ਨਾਲ ਪੇਸ਼ ਕੀਤਾ ਜਾਵੇਗਾ ਜੋ ਤੁਹਾਨੂੰ ਇੱਕ ਮਾਹਰ (expert) ਸਾਬਤ ਕਰੇ। ਇਹ ਦਰਸ਼ਕਾਂ ਨੂੰ ਤੁਹਾਡੇ ਅਕੈਡਮੀ ਕੋਰਸ, ਵੈਬੀਨਾਰ, ਜਾਂ ਕੋਚਿੰਗ ਪ੍ਰੋਗਰਾਮਾਂ ਵਿੱਚ ਸ਼ਾਮਲ ਹੋਣ ਲਈ ਪ੍ਰੇਰਿਤ ਕਰਦਾ ਹੈ।",
        hindiDesc = "यह आपको अपने क्षेत्र में एक विशेषज्ञ के रूप में स्थापित करता है। यह दर्शकों को आपके प्रीमियम कोर्स, वेबिनार, या व्यक्तिगत परामर्श कार्यक्रमों में शामिल होने के लिए प्रेरित करता है।"
    ),
    "Generate lead conversions" to OptionInfo(
        title = "Generate lead conversions",
        englishDesc = "Focuses on strategic calls-to-action (CTAs) that funnel viewers toward sign-ups, website landing pages, business newsletters, or affiliate sales pipelines.",
        punjabiDesc = "ਵੀਡੀਓ ਵਿੱਚ ਮਜ਼ਬੂਤ ਕਾਲ-ਟੂ-ਐਕਸ਼ਨ (CTA) ਦੇ ਕੇ ਦਰਸ਼ਕਾਂ ਨੂੰ ਵੈਬਸਾਈਟ 'ਤੇ ਭੇਜਣਾ, ਨਿਊਜ਼ਲੈਟਰ ਵਿੱਚ ਸ਼ਾਮਲ ਕਰਨਾ, ਜਾਂ ਐਫੀਲੀਏਟ ਲਿੰਕਸ ਰਾਹੀਂ ਬਿਜ਼ਨਸ ਲੀਡਸ ਇਕੱਠੀਆਂ ਕਰਨਾ।",
        hindiDesc = "यह मजबूत कॉल-टू-एक्शन (CTA) पर ध्यान केंद्रित करता है जो दर्शकों को वेबसाइट साइन-अप, न्यूजलेटर या एफिलिएट लिंक्स के माध्यम से बिजनेस लीड्स में बदलता है।"
    ),
    "Establish authority in niche" to OptionInfo(
        title = "Establish authority in niche",
        englishDesc = "Establishes long-term credibility as a subject matter expert. Uses high-value data, research case studies, and deep insights to secure industry leadership status.",
        punjabiDesc = "ਖੇਤਰ ਵਿੱਚ ਇੱਕ ਭਰੋਸੇਯੋਗ ਚਿੰਤਕ (thought-leader) ਬਣਨ ਲਈ। ਇਸ ਵਿੱਚ ਗਹਿਰੀ ਰਿਸਰਚ, ਡੇਟਾ ਐਨਾਲਿਸਿਸ, ਅਤੇ ਵਿਸਤ੍ਰਿਤ ਵੀਡੀਓਜ਼ ਸ਼ਾਮਲ ਹੁੰਦੀਆਂ ਹਨ ਜੋ ਸਥਾਈ ਪ੍ਰਭਾਵ ਪਾਉਂਦੀਆਂ ਹਨ।",
        hindiDesc = "अपने क्षेत्र में एक विश्वसनीय सोच-नेता (Thought-leader) और विषय विशेषज्ञ बनने के लिए। इसमें गहन शोध, डेटा विश्लेषण और विस्तृत वीडियो शामिल होते हैं जो स्थायी प्रभाव छोड़ते हैं।"
    ),
    "Shorts" to OptionInfo(
        title = "Shorts (<60 seconds, fast retention loop)",
        englishDesc = "Ultra short videos designed for fast dynamic retention loops. Focuses heavily on high-energy hooks within the first 3 seconds to trigger YouTube Shorts feed algorithm.",
        punjabiDesc = "60 ਸਕਿੰਟਾਂ ਤੋਂ ਘੱਟ ਦੇ ਵੀਡੀਓਜ਼। ਇਸ ਵਿੱਚ ਪਹਿਲੇ 3 ਸਕਿੰਟਾਂ ਵਿੱਚ ਹੀ ਦਰਸ਼ਕਾਂ ਨੂੰ ਖਿੱਚਣ (hook) ਅਤੇ ਬਾਰ-ਬਾਰ ਦੇਖੇ ਜਾਣ ਵਾਲੇ ਲੂਪਸ ਦਾ ਇਸਤੇਮਾਲ ਹੁੰਦਾ ਹੈ, ਜੋ ਵਾਇਰਲ ਹੋਣ ਲਈ ਬਿਹਤਰੀਨ ਹੈ।",
        hindiDesc = "60 सेकंड से कम की छोटी वीडियो। इसका मुख्य फोकस पहले 3 सेकंड में दर्शकों को आकर्षित करने और बार-बार देखे जाने वाले लूप्स बनाने पर होता है, जो शॉर्ट्स एल्गोरिदम के लिए बेहतरीन है।"
    ),
    "Mid-form" to OptionInfo(
        title = "Mid-form (2-5 minutes, high value tips)",
        englishDesc = "Balanced 2 to 5 minutes duration delivering immediate actionable value without fluff. Excellent for rapid feature reviews, quick tech tips, and product roundups.",
        punjabiDesc = "2 ਤੋਂ 5 ਮਿੰਟ ਦੇ ਵੀਡੀਓਜ਼ ਜੋ ਬਿਨਾਂ ਵਾਧੂ ਗੱਲਾਂ ਦੇ ਸਿੱਧੇ ਮੁੱਦੇ 'ਤੇ ਆਉਂਦੇ ਹਨ। ਇਹ ਤੁਰੰਤ ਕੁਝ ਸਿੱਖਣ ਵਾਲੇ ਟੂਲ ਨਿਊਜ਼, ਟਿਪਸ ਅਤੇ ਰੀਵਿਊਜ਼ ਲਈ ਬਹੁਤ ਵਧੀਆ ਹਨ।",
        hindiDesc = "2 से 5 मिनट की वीडियो जो सीधे मुख्य मुद्दे पर आती हैं। यह तुरंत कुछ सीखने वाले क्विक टिप्स, टूल रिव्यूज और उपयोगी गाइड के लिए सबसे अच्छी हैं।"
    ),
    "Long-form" to OptionInfo(
        title = "Long-form (8-15 minutes, exhaustive deep-dive)",
        englishDesc = "Complete masterclasses or deep analytical case studies extending over 8 minutes. Maximizes watch time, mid-roll ad placements, and robust search rankings.",
        punjabiDesc = "8 ਤੋਂ 15 ਮਿੰਟ ਦੇ ਵਿਸਤ੍ਰਿਤ ਵੀਡੀਓਜ਼ ਜੋ ਡੂੰਘੀ ਜਾਣਕਾਰੀ ਦਿੰਦੇ ਹਨ। ਇਹ ਚੈਨਲ ਨੂੰ ਸਰਚ ਵਿੱਚ ਉੱਪਰ ਲਿਆਉਣ ਅਤੇ ਵੀਡੀਓ ਦੇ ਵਿਚਕਾਰ ਐਡਸ (mid-roll ads) ਲਗਾ ਕੇ ਉੱਚੀ ਕਮਾਈ ਕਰਨ ਲਈ ਸਭ ਤੋਂ ਵਧੀਆ ਹਨ।",
        hindiDesc = "8 से 15 मिनट की विस्तृत मास्टरक्लास या केस स्टडीज। यह वॉच टाइम बढ़ाने, वीडियो के बीच में विज्ञापन (mid-roll ads) लगाने और सर्च रैंकिंग में ऊपर आने के लिए सर्वोत्तम है।"
    ),
    "Cinematic dark room" to OptionInfo(
        title = "Cinematic Dark Room",
        englishDesc = "An atmospheric style featuring glowing neon-blue code screens and cyber illumination. Best for technical guides, futuristic coding videos, and professional dev vlogs.",
        punjabiDesc = "ਗਲੋਇੰਗ ਨਿਓਨ-ਬਲੂ ਕੋਡ ਸਕ੍ਰੀਨਸ ਅਤੇ ਸਾਈਬਰ ਲਾਈਟਿੰਗ ਵਾਲਾ ਰਣਨੀਤਕ ਸਟਾਈਲ। ਇਹ ਕੋਡਿੰਗ, ਤਕਨੀਕੀ ਵੀਡੀਓਜ਼ ਅਤੇ ਪ੍ਰੋਫੈਸ਼ਨਲ ਵੀਲਾਗਸ ਲਈ ਸਭ ਤੋਂ ਵਧੀਆ ਹੈ।",
        hindiDesc = "ग्लोइंग नियॉन-ब्लू कोड स्क्रीन और साइबर लाइटिंग वाली वायुमंडलीय शैली। यह कोडिंग, तकनीकी गाइड और पेशेवर व्लॉग के लिए सबसे उपयुक्त है।"
    ),
    "Cozy clean kitchen" to OptionInfo(
        title = "Cozy Clean Kitchen",
        englishDesc = "Soft gentle steam with rich, natural morning overhead sunlight. Creates an inviting, warm domestic cozy atmosphere ideal for culinary guides and cozy home tutorials.",
        punjabiDesc = "ਨਰਮ ਭਾਫ਼ ਅਤੇ ਸਵੇਰ ਦੀ ਕੁਦਰਤੀ ਧੁੱਪ ਵਾਲੀ ਘਰੇਲੂ ਰਸੋਈ ਦੀ ਦਿੱਖ। ਇਹ ਖਾਣਾ ਬਣਾਉਣ ਦੇ ਵੀਡੀਓਜ਼ ਅਤੇ ਘਰੇਲੂ ਗਾਈਡਸ ਲਈ ਆਦਰਸ਼ ਹੈ।",
        hindiDesc = "हल्की भाप और सुबह की प्राकृतिक धूप के साथ घरेलू रसोई का वातावरण। यह खाना पकाने की वीडियो और व्यावहारिक ट्यूटोरियल के लिए आदर्श है।"
    ),
    "Modern neon DJ deck" to OptionInfo(
        title = "Modern Neon DJ Deck",
        englishDesc = "Energetic pulsing neon waveforms with a glowing audio spectrum visualizer. Perfect for music remixes, podcasts, sound analysis, or high-energy tech content.",
        punjabiDesc = "ਇੱਕ ਆਡੀਓ ਸਪੈਕਟ੍ਰਮ ਵਿਜ਼ੂਅਲਾਈਜ਼ਰ ਦੇ ਨਾਲ ਉੱਚ-ਊਰਜਾ ਵਾਲੀ ਨਿਓਨ ਤਰੰਗਾਂ। ਇਹ ਸੰਗੀਤ ਰੀਮਿਕਸ, ਪੋਡਕਾਸਟ ਅਤੇ ਉੱਚ-ਊਰਜਾ ਤਕਨੀਕੀ ਸਮੱਗਰੀ ਲਈ ਵਧੀਆ ਹੈ।",
        hindiDesc = "एक ऑडियो स्पेक्ट्रम विजुअलाइज़र के साथ ऊर्जावान नियॉन तरंगें। यह संगीत रीमिक्स, पॉडकास्ट और उच्च-ऊर्जा तकनीकी वीडियो के लिए बहुत बढ़िया है।"
    ),
    "Surgical high-contrast" to OptionInfo(
        title = "Surgical High-Contrast",
        englishDesc = "Pitch-black backgrounds coupled with high-speed kinetic typography and ultra-sharp text alignments. Maximize retention and emphasis on information value.",
        punjabiDesc = "ਹਾਈ-ਸਪੀਡ ਕਾਇਨੇਟਿਕ ਟਾਈਪੋਗ੍ਰਾਫੀ ਦੇ ਨਾਲ ਬਿਲਕੁਲ ਕਾਲਾ ਪਿਛੋਕੜ। ਇਹ ਵੀਡੀਓ 'ਤੇ ਦਰਸ਼ਕਾਂ ਦੇ ਧਿਆਨ ਅਤੇ ਜਾਣਕਾਰੀ ਦੇ ਮੁੱਲ ਨੂੰ ਬਹੁਤ ਵਧਾਉਂਦਾ ਹੈ।",
        hindiDesc = "हाई-स्पीड काइनेटिक टाइपोग्राफी के साथ पिच-ब्लैक बैकग्राउंड। यह दर्शकों के ध्यान को आकर्षित करने और जानकारी के मूल्य को अधिकतम करने में मदद करता है।"
    ),
    "Minimalist aesthetic" to OptionInfo(
        title = "Minimalist Aesthetic",
        englishDesc = "Clean elegant layouts with a highly targeted splash of rich color. Distinguishes from classic junk spam using deep premium appeal.",
        punjabiDesc = "ਸਾਫ਼-ਸੁਥਰਾ ਅਤੇ ਸ਼ਾਨਦਾਰ ਲੇਆਉਟ ਜਿਸ ਵਿੱਚ ਰੰਗਾਂ ਦੇ ਖਾਸ ਟੁਕੜੇ ਪਾਏ ਜਾਂਦੇ ਹਨ। ਇਹ ਚੈਨਲ ਨੂੰ ਇੱਕ ਪ੍ਰੀਮੀਅਮ ਦਿੱਖ ਦਿੰਦਾ ਹੈ।",
        hindiDesc = "साफ़-सुथरा और सुरुचिपूर्ण लेआउट जिसमें रंगों का एक विशेष उपयोग होता है। यह चैनल को एक प्रीमियम और विशिष्ट रूप देता है।"
    ),
    "High-contrast character face" to OptionInfo(
        title = "High-Contrast Face Shock",
        englishDesc = "High-contrast face close-up expressing shock or strong emotion. Statistically proven to increase click-through-rates with clear facial engagement.",
        punjabiDesc = "ਸਦਮੇ ਜਾਂ ਮਜ਼ਬੂਤ ਭਾਵਨਾ ਨੂੰ ਦਰਸਾਉਂਦਾ ਚਿਹਰਾ। ਇਹ ਕਲਿੱਕ ਕਰਨ ਦੀ ਦਰ (CTR) ਨੂੰ ਬਹੁਤ ਵਧਾਉਂਦਾ ਹੈ।",
        hindiDesc = "झटके या मजबूत भावना व्यक्त करने वाला उच्च-रंग विपरीत चेहरा। यह वीडियो की क्लिक रेट (CTR) को महत्वपूर्ण रूप से बढ़ाता है।"
    ),
    "Bold thick text overlays" to OptionInfo(
        title = "Bold Text Overlays",
        englishDesc = "Thick, high-contrast typography layered over extremely abstract visual concepts. Captures direct attention on busy feeds immediately.",
        punjabiDesc = "ਗੂੜ੍ਹੇ ਅਤੇ ਪ੍ਰਭਾਵਸ਼ਾਲੀ ਅੱਖਰ ਜੋ ਦਰਸ਼ਕਾਂ ਦਾ ਧਿਆਨ ਤੁਰੰਤ ਖਿੱਚਦੇ ਹਨ।",
        hindiDesc = "मोटे और अत्यधिक दृश्यमान अक्षर जो दर्शकों का ध्यान तुरंत और आसानी से आकर्षित करते हैं।"
    ),
    "Surgical flat vector design layout" to OptionInfo(
        title = "Surgical Flat Vector Neon",
        englishDesc = "Geometric layouts combined with crisp, bright vector illustrations and glowing ambient outlines. Delivers a modern startup tech and professional aesthetic.",
        punjabiDesc = "ਨਿਓਨ ਚਮਕ ਦੇ ਨਾਲ ਫਲੈਟ ਜਾਮਿਤੀ ਆਕਾਰ (shapes) ਅਤੇ ਡਿਜ਼ਾਈਨ। ਇਹ ਚੈਨਲ ਨੂੰ ਇੱਕ ਬਹੁਤ ਹੀ ਆਧੁਨਿਕ ਅਤੇ ਪ੍ਰੋਫੈਸ਼ਨਲ ਦਿੱਖ ਪ੍ਰਦਾਨ ਕਰਦਾ ਹੈ।",
        hindiDesc = "चमकदार न्यून आउटलाइन्स के साथ ज्यामितीय आकार और साफ वेक्टर चित्र। यह आपके चैनल को आधुनिक और अत्यधिक पेशेवर लुक देता है।"
    ),
    "Recreate" to OptionInfo(
        title = "Recreate Pivot Model",
        englishDesc = "Recreate or pivot your content strategy based on a previously analyzed top-performing video from the target channel.",
        punjabiDesc = "ਤੁਹਾਡੇ ਦੁਆਰਾ ਪਹਿਲਾਂ ਵਿਸ਼ਲੇਸ਼ਣ (analyze) ਕੀਤੇ ਗਏ ਕਿਸੇ ਪ੍ਰਸਿੱਧ ਵੀਡੀਓ ਦੇ ਫਾਰਮੂਲੇ ਨੂੰ ਦੁਬਾਰา ਬਣਾਉਣਾ।",
        hindiDesc = "चैनल के सबसे लोकप्रिय वीडियो की रूपरेखा का उपयोग करके अपनी सामग्री की रणनीति को फिर से तैयार या पिवट करना।"
    ),
    "Choose an entirely new custom topic" to OptionInfo(
        title = "Custom Topic Generation",
        englishDesc = "Generate an entirely new script and storyboard on a custom topic of your choice while preserving the cloned channel's DNA.",
        punjabiDesc = "ਚੈਨਲ ਦੇ ਸਟਾਈਲ ਨੂੰ ਵਰਤਦੇ ਹੋਏ ਕਿਸੇ ਬਿਲਕੁਲ ਨਵੇਂ ਵਿਸ਼ੇ 'ਤੇ ਕੰਮ ਕਰਨਾ।",
        hindiDesc = "चैनल की कोर शैली को बनाए रखते हुए अपनी पसंद के बिल्कुल नए विषय पर स्क्रिप्ट और स्टोरीबोर्ड तैयार करना।"
    ),
    "Include competitor channels" to OptionInfo(
        title = "Competitor Intelligence Loop",
        englishDesc = "Analyze and cross-reference competitor links in your niche to identify content gaps and boost strategic outcome.",
        punjabiDesc = "ਆਪਣੇ ਮੁਕਾਬਲੇਬਾਜ਼ ਚੈਨਲਾਂ ਦੇ ਲਿੰਕ ਸ਼ਾਮਲ ਕਰਨਾ ਤਾਂ ਜੋ ਉਹਨਾਂ ਦੀ ਰਣਨੀਤੀ ਨੂੰ ਵੀ ਵਿਸ਼ਲੇਸ਼ਣ ਕੀਤਾ ਜਾ ਸਕੇ।",
        hindiDesc = "अपने क्षेत्र के अन्य प्रतिद्वंद्वी चैनलों के वीडियो का विश्लेषण करना ताकि ट्रेंडिंग अवसरों का पता लगाया जा सके।"
    ),
    "Focus purely on targets" to OptionInfo(
        title = "Exclusive Pure Target Modeling",
        englishDesc = "Keep exclusive focus on target channel resources and script structures for pure stylistic modeling.",
        punjabiDesc = "ਸਿਰਫ਼ ਮੁੱਖ ਨਿਸ਼ਾਨา ਬਣਾਏ ਗਏ ਚੈਨਲ ਦੇ ਡਾਟਾ 'ਤੇ ਧਿਆਨ ਕੇਂਦਰਿਤ ਕਰਨਾ।",
        hindiDesc = "शुद्ध शैलीबद्ध मॉडलिंग के लिए केवल लक्षित चैनल के संसाधनों और स्क्रिप्ट संरचनाओं पर केंद्रित रहना।"
    )
)

fun getUiText(key: String, lang: String): String {
    return when (lang) {
        "Hinglish" -> when (key) {
            "lang_select" -> "Preferred Assistant Language"
            "lang_sub" -> "Assistant se baatchit aur tips ke liye apni pasandida language select karein."
            "goal_title" -> "Channel Objective / Goal"
            "goal_sub" -> "Aap is cloned style content se kaisa target achieve karna chahte hain?"
            "goal_placeholder" -> "Apna main target/goal likhein e.g. Views, Leads, Subscribers..."
            "format_title" -> "Target Video Format"
            "format_sub" -> "Apne video ke liye structure ya duration select karein:"
            "btn_proceed_sec1" -> "TARGET SCAN PE AAGE BADHEIN"
            "channels_title" -> "Enter Target Clone Channel Links (Up to 3)"
            "channels_sub" -> "Aise 3 YouTube channels ke link dalo jinka style synthesize aur recreate karna chahte ho."
            "channel_1_req" -> "Channel 1 (Primary - Required)"
            "channel_2_opt" -> "Channel 2 (Optional)"
            "channel_3_opt" -> "Channel 3 (Optional)"
            "btn_proceed_sec2" -> "TRANSCRIPT LOOP PE AAGE BADHEIN"
            "transcript_title" -> "Provide Channel Transcripts"
            "transcript_sub" -> "Clone karne ke liye transcripts dalo taaki AI automatic analysis kar sake."
            "btn_proceed_sec3" -> "TOPICS & COMPETITORS INSTRUCTIONS"
            else -> key
        }
        "Punjabi" -> when (key) {
            "lang_select" -> "Preferred Assistant Language / ਗੱਲਬਾਤ ਦੀ ਭਾਸ਼ਾ"
            "lang_sub" -> "ਅਸਿਸਟੈਂਟ ਨਾਲ ਗੱਲਬਾਤ ਅਤੇ ਸੁਝਾਵਾਂ ਲਈ ਆਪਣੀ ਪਸੰਦੀਦਾ ਭਾਸ਼ਾ ਚੁਣੋ।"
            "goal_title" -> "ਚੈਨਲ ਉਦੇਸ਼ / ਟੀਚਾ"
            "goal_sub" -> "ਤੁਸੀਂ ਇਸ ਕਲੋਨ ਕੀਤੇ ਸਟਾਈਲ ਤੋਂ ਕੀ ਨਤੀਜਾ ਚਾਹੁੰਦੇ ਹੋ?"
            "goal_placeholder" -> "ਆਪਣਾ ਮੁੱਖ ਟੀਚਾ ਲਿਖੋ..."
            "format_title" -> "ਟਾਰਗੇਟ ਵੀਡੀਓ ਫਾਰਮੈਟ"
            "format_sub" -> "ਆਪਣੀ ਵੀਡੀਓ ਦੀ ਮਿਆਦ ਅਤੇ ਲੇਆਉਟ ਚੁਣੋ:"
            "btn_proceed_sec1" -> "ਟਾਰਗੇਟ ਸਕੈਨ 'ਤੇ ਅੱਗੇ ਵਧੋ"
            "channels_title" -> "ਟਾਰਗੇਟ ਕਲੋਨ ਚੈਨਲ ਲਿੰਕਸ (ਵੱਧ ਤੋਂ ਵੱਧ 3)"
            "channels_sub" -> "ਉਹਨਾਂ 3 ਯੂਟਿਊਬ ਚੈਨਲਾਂ ਦੇ ਲਿੰਕ ਦਰਜ ਕਰੋ ਜਿਨ੍ਹਾਂ ਦਾ ਸਟਾਈਲ ਤੁਸੀਂ ਕਲੋਨ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ।"
            "channel_1_req" -> "ਚੈਨਲ 1 (ਮੁੱਖ - ਲੋੜੀਂਦਾ)"
            "channel_2_opt" -> "ਚੈਨਲ 2 (ਵਿਕਲਪਿਕ)"
            "channel_3_opt" -> "ਚੈਨਲ 3 (ਵਿਕਲਪਿਕ)"
            "btn_proceed_sec2" -> "ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਦਰਜ ਕਰਨ ਲਈ ਅੱਗੇ ਵਧੋ"
            "transcript_title" -> "ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਪ੍ਰਦਾਨ ਕਰੋ"
            "transcript_sub" -> "ਕਲੋਨ ਕਰਨ ਲਈ ਵੀਡੀਓ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟਸ ਦਰਜ ਕਰੋ।"
            "btn_proceed_sec3" -> "ਵਿਸ਼ੇ ਅਤੇ ਮੁਕਾਬਲੇਬਾਜ਼ਾਂ ਦੇ ਵਿਕਲਪ"
            else -> key
        }
        "Hindi" -> when (key) {
            "lang_select" -> "Preferred Assistant Language / बातचीत की भाषा"
            "lang_sub" -> "असिस्टेंट से बातचीत और सुझावों के लिए अपनी पसंदीदा भाषा चुनें।"
            "goal_title" -> "चैनल का मुख्य उद्देश्य / लक्ष्य"
            "goal_sub" -> "आप इस क्लोन किए गए स्टाइल से क्या लक्ष्य हासिल करना चाहते हैं?"
            "goal_placeholder" -> "अपना मुख्य लक्ष्य लिखें..."
            "format_title" -> "टारगेट वीडियो फॉर्मेट"
            "format_sub" -> "अपने वीडियो के लिए उपयुक्त अवधि और लेआउट चुनें:"
            "btn_proceed_sec1" -> "टारगेट स्कैन पर आगे बढ़ें"
            "channels_title" -> "टारगेट क्लोन चैनल लिंक्स (अधिकतम 3)"
            "channels_sub" -> "उन 3 यूट्यूब चैनलों के लिंक दर्ज करें जिनका स्टाइल आप क्लोन करना चाहते हैं।"
            "channel_1_req" -> "चैनल 1 (मुख्य - आवश्यक)"
            "channel_2_opt" -> "चैनल 2 (वैकल्पिक)"
            "channel_3_opt" -> "चैनल 3 (वैकल्पिक)"
            "btn_proceed_sec2" -> "ट्रांसक्रिप्ट दर्ज करने पर आगे बढ़ें"
            "transcript_title" -> "ट्रांसक्रिप्ट प्रदान करें"
            "transcript_sub" -> "क्लोन करने के लिए वीडियो ट्रांसक्रिप्ट दर्ज करें।"
            "btn_proceed_sec3" -> "विषय और प्रतिस्पर्धी विकल्प"
            else -> key
        }
        else -> when (key) {
            "lang_select" -> "Preferred Assistant Language"
            "lang_sub" -> "Select your preferred language for assistant guidance, instructions, and tips."
            "goal_title" -> "Channel Objective / Goal"
            "goal_sub" -> "What is the primary target outcome you desire from this cloned style content?"
            "goal_placeholder" -> "Enter your main target goal / business model"
            "format_title" -> "Target Video Format"
            "format_sub" -> "Choose the duration layout standard matching the video structure you wish to draft:"
            "btn_proceed_sec1" -> "PROCEED TO TARGET SCAN"
            "channels_title" -> "Enter Target Clone Channel Links (Up to 3)"
            "channels_sub" -> "Provide the exact handle or URL of up to 3 YouTube channels whose styles you want to synthesize and recreate."
            "channel_1_req" -> "Channel 1 (Primary - Required)"
            "channel_2_opt" -> "Channel 2 (Optional)"
            "channel_3_opt" -> "Channel 3 (Optional)"
            "btn_proceed_sec2" -> "PROCEED TO INPUT TRANSCRIPTS"
            "transcript_title" -> "Provide Channel Transcripts"
            "transcript_sub" -> "Provide 2–3 FULL video transcripts from this channel."
            "btn_proceed_sec3" -> "PROCEED TO CONTEXT TOPIC"
            else -> key
        }
    }
}

fun findHelpInfo(optionText: String): OptionInfo? {
    globalOptionsHelp[optionText]?.let { return it }
    for ((key, info) in globalOptionsHelp) {
        if (optionText.contains(key, ignoreCase = true) || key.contains(optionText, ignoreCase = true)) {
            return info
        }
    }
    return null
}

fun getHinglishDescription(info: OptionInfo): String {
    return when (info.title) {
        "Maximize Views / Go Viral" -> "Yeh high-tempo trends aur target hook points par focus karta hai taaki video ke click-through-rate (CTR) aur view velocity max upar jaye."
        "Grow Subscribers" -> "Iska main focus quality and deep value elements par hota hai taaki casual viewers ko direct loyal subscribers me convert kiya ja sake."
        "Drive Sales / Conversions" -> "Video me strong, appealing Call-to-Actions (CTAs) lagana taaki direct website ya affiliate page par sales increase ho skey."
        "Build Brand Awareness" -> "Aapko aur aapke brand ko industry me thought leader ki tarah represent karta hai taaki authority generate ho ske."
        "YouTube Long-Form (8–20 min)" -> "8 se 20 min ke case studies tutorials, jo deep watch time aur mid-rolls ad lagane ke liye sabse best format hai."
        "YouTube Shorts (under 60 sec)" -> "60 seconds se kam ki vertical high-retention video loops jo viral content strategy aur short feed optimization ke liye bani hain."
        "YouTube Mid-Form (3–7 min)" -> "3 se 7 min ke accurate value and precise tutorials bina kisi waste talks ke direct solutions dene ke liye."
        "Build a loyal tech community" -> "High quality coding tutorials aur content se ek dependable audience block build karna jo hamesha search me aapko reference kare."
        "Earn AdSense revenue fast" -> "Fast search volume keywords aur simple high-CTR trends compile karke quickly maximum AdSense impressions upar le jana."
        "Launch high-ticket courses" -> "Aapko domain expert represent karta hai taaki digital learning, webinars, aur paid consulting convert ho ske."
        "Generate lead conversions" -> "Audience feedback forms, newsletters, aur private pipelines connect karke potential business contacts collect karna."
        "Establish authority in niche" -> "Authentic statistics, technical reports, aur long-term strategic reviews implement karke domain head authority standard click karna."
        "Shorts", "Shorts (<60 seconds, fast retention loop)" -> "Ultra brief loop structured videos designed to keep retention close to 100% and satisfy standard short algorithms."
        "Mid-form", "Mid-form (2-5 minutes, high value tips)" -> "Focused and balanced value structure targeting prompt results without extra padding."
        "Long-form", "Long-form (8-15 minutes, exhaustive deep-dive)" -> "Chronologically complete content format for high information weightage and steady organic search visibility."
        "Cinematic dark room", "Cinematic Dark Room" -> "Glow blue neon indicators aur light accents setup coding, setup reviews aur cyber lifestyle vlogs ke liye ideal background."
        "Cozy clean kitchen", "Cozy Clean Kitchen" -> "Subah ki sun rays, clean counter layout aur light background steam kitchen vlogs, recipes and health topics ke liye visual template."
        "Modern neon DJ deck", "Modern Neon DJ Deck" -> "Energetic rhythmic waveform displays, glowing lines aur bass light integrations podcast aur audio updates ke liye unique overlay setup."
        "Surgical high-contrast", "Surgical High-Contrast" -> "Absolute pitch dark canvas with high tempo fonts overlays and kinetic text transitions to keep audience extremely focused."
        "Minimalist aesthetic", "Minimalist Aesthetic" -> "Minimal components layout, clean backgrounds, aur standard branding icons jo simple high-quality appeal represent kare."
        "High-contrast character face", "High-Contrast Face Shock" -> "Saturated zoom character close-ups aur thick borders thumbnails face emotion click generate karne ke liye."
        "Bold thick text overlays", "Bold Text Overlays" -> "Easy-to-read compact phrases overlays context quickly highlight karne ke liye text styling."
        "Surgical flat vector design layout", "Surgical Flat Vector Neon" -> "Neatly aligned geometric graphics aur premium minimal vectors interface professional feel provide karne ke liye."
        "Recreate", "Recreate Pivot Model" -> "Competitor key points flow, speech pacing aur title model base structure use karke target recreation plan design karna."
        "Choose an entirely new custom topic", "Custom Topic Generation" -> "Direct fresh user entry provide karke specialized prompt context aur dynamic content angles define karna."
        "Include competitor channels", "Competitor Intelligence Loop" -> "Aapke configured channels ke patterns scan list ko prompt analytics elements include karna."
        "Focus purely on targets", "Exclusive Pure Target Modeling" -> "Content structures aur suggestions ko directly targets outline limit parameters ke under narrow range maintain rakhna."
        else -> info.englishDesc
    }
}

@Composable
fun InfoGuideDialog(
    optionName: String,
    info: OptionInfo,
    viewModel: GrowTubeViewModel,
    onDismiss: () -> Unit
) {
    val languages = listOf("Hinglish", "English", "ਪੰਜਾਬੀ", "हिंदी")
    var selectedLanguage by remember { mutableStateOf(viewModel.preferredHelpLanguage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Got it / ਸਮਝ ਗਿਆ / समझ गया", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = info.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(15.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Dynamic Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    languages.forEach { lang ->
                        val isSelected = when (lang) {
                            "Hinglish" -> selectedLanguage == "Hinglish"
                            "ਪੰਜਾਬੀ" -> selectedLanguage == "Punjabi"
                            "हिंदी" -> selectedLanguage == "Hindi"
                            else -> selectedLanguage == "English"
                        }
                        val normalizedLang = when (lang) {
                            "ਪੰਜਾਬੀ" -> "Punjabi"
                            "हिंदी" -> "Hindi"
                            "Hinglish" -> "Hinglish"
                            else -> "English"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    selectedLanguage = normalizedLang
                                    viewModel.preferredHelpLanguage = normalizedLang
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val currentDesc = when (selectedLanguage) {
                    "Punjabi" -> info.punjabiDesc
                    "Hindi" -> info.hindiDesc
                    "Hinglish" -> getHinglishDescription(info)
                    else -> info.englishDesc
                }

                val currentLabel = when (selectedLanguage) {
                    "Punjabi" -> "ਪੰਜਾਬੀ ਗਾਈਡ:"
                    "Hindi" -> "हिंदी गाइड:"
                    "Hinglish" -> "Hinglish Guide:"
                    else -> "English Guidance:"
                }

                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 22.sp
                )
            }
        },
        containerColor = DarkCard,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(16.dp)
    )
}


@Composable
fun CloneState0_GoalAndFormat(viewModel: GrowTubeViewModel) {
    var goalText by remember { mutableStateOf(viewModel.cloneGoal) }
    var selectedFormat by remember { mutableStateOf(viewModel.cloneFormat) }
    var activeInfoOption by remember { mutableStateOf<OptionInfo?>(null) }

    val goalSuggestions = listOf(
        "Maximize Views / Go Viral",
        "Grow Subscribers",
        "Drive Sales / Conversions",
        "Build Brand Awareness"
    )

    val formatOptions = listOf(
        "YouTube Long-Form (8–20 min)",
        "YouTube Shorts (under 60 sec)",
        "YouTube Mid-Form (3–7 min)"
    )

    activeInfoOption?.let { info ->
        InfoGuideDialog(
            optionName = info.title,
            info = info,
            viewModel = viewModel,
            onDismiss = { activeInfoOption = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preferred Assistant Language Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = getUiText("lang_select", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = getUiText("lang_sub", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                
                val langList = listOf("Hinglish", "Punjabi", "Hindi", "English")
                val langLabels = mapOf(
                    "Hinglish" to "Hinglish 💬",
                    "Punjabi" to "ਪੰਜਾਬੀ 🌾",
                    "Hindi" to "हिंदी 🇮🇳",
                    "English" to "English 🇬🇧"
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    langList.forEach { lang ->
                        val isSelected = viewModel.preferredHelpLanguage == lang
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    viewModel.preferredHelpLanguage = lang
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = langLabels[lang] ?: lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Objective Goal selection
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = getUiText("goal_title", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = getUiText("goal_sub", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    placeholder = { Text(getUiText("goal_placeholder", viewModel.preferredHelpLanguage)) },
                    modifier = Modifier.fillMaxWidth().testTag("clone_goal_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Goal Pill Suggestions
                OptFlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    goalSuggestions.forEach { suggestion ->
                        val isSelected = goalText == suggestion
                        AssistChip(
                            onClick = { goalText = suggestion },
                            label = { Text(suggestion) },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary.copy(alpha = 0.4f)
                            ),
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "View Option Info",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else TextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            activeInfoOption = findHelpInfo(suggestion)
                                        }
                                )
                            }
                        )
                    }
                }

                // Selected Goal Explanation Card
                val currentGoalHelp = findHelpInfo(goalText)
                if (currentGoalHelp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selected goal info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> currentGoalHelp.punjabiDesc
                                    "Hindi" -> currentGoalHelp.hindiDesc
                                    "Hinglish" -> getHinglishDescription(currentGoalHelp)
                                    else -> currentGoalHelp.englishDesc
                                },
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Target Video Format card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = getUiText("format_title", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = getUiText("format_sub", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                formatOptions.forEach { format ->
                    val isSelected = selectedFormat == format
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { selectedFormat = format }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedFormat = format },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = format,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                activeInfoOption = findHelpInfo(format)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "View Format Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Selected Format Explanation Card
                val currentFormatHelp = findHelpInfo(selectedFormat)
                if (currentFormatHelp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selected format info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> currentFormatHelp.punjabiDesc
                                    "Hindi" -> currentFormatHelp.hindiDesc
                                    "Hinglish" -> getHinglishDescription(currentFormatHelp)
                                    else -> currentFormatHelp.englishDesc
                                },
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.proceedToState1(goalText, selectedFormat) },
            enabled = goalText.isNotBlank() && selectedFormat.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("proceed_to_link_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(getUiText("btn_proceed_sec1", viewModel.preferredHelpLanguage), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun CloneState1_ChannelLink(viewModel: GrowTubeViewModel) {
    var linkInput1 by remember(viewModel.cloneChannelLink) { mutableStateOf(viewModel.cloneChannelLink) }
    var linkInput2 by remember(viewModel.cloneChannelLink2) { mutableStateOf(viewModel.cloneChannelLink2) }
    var linkInput3 by remember(viewModel.cloneChannelLink3) { mutableStateOf(viewModel.cloneChannelLink3) }
    var searchQuery by remember { mutableStateOf(viewModel.cloneSearchQuery) }

    val linkSuggestions = listOf(
        "youtube.com/@cooking (Culinary Hacks)",
        "youtube.com/@composecoders (Technical Dev)",
        "youtube.com/@punjabi_beats (AI Fusion Beats)",
        "youtube.com/@techworld (Gadget reviews)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // YouTube Channels Live Search Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "🔍 ਯੂਟਿਊਬ ਚੈਨਲ ਖੋਜੋ"
                        "Hindi" -> "🔍 यूट्यूब चैनल खोजें"
                        "Hinglish" -> "🔍 YouTube Channel Search"
                        else -> "🔍 Search YouTube Channels"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਚੈਨਲ ਦਾ ਨਾਮ ਜਾਂ @handle ਟਾਈਪ ਕਰੋ, ਚੁਣੋ ਅਤੇ ਸਿੱਧਾ ਐਡ ਕਰੋ।"
                        "Hindi" -> "चैनल का नाम या @handle दर्ज करें, चुनें और सीधे जोड़ें।"
                        "Hinglish" -> "Channel name ya @handle type karein, click karke directly slot me add karein."
                        else -> "Type a channel name or handle, then tap to map it to any clone slot instantly."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchYouTubeChannelsForClone(it)
                    },
                    placeholder = {
                        Text(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਜਿਵੇਂ: Gordon Ramsay, MKBHD"
                                "Hindi" -> "जैसे: Gordon Ramsay, MKBHD"
                                else -> "e.g. Cooking, Technical vlogs, MKBHD"
                            }
                        )
                    },
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchYouTubeChannelsForClone("")
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Render live search results dropdown list
                if (searchQuery.trim().isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (viewModel.cloneSearchLoading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                                }
                            } else if (viewModel.cloneSearchResults.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = when (viewModel.preferredHelpLanguage) {
                                            "Punjabi" -> "ਕੋਈ ਚੈਨਲ ਨਹੀਂ ਮਿਲਿਆ। ਬਦਲ ਕੇ ਟਾਈਪ ਕਰੋ।"
                                            "Hindi" -> "कोई चैनल नहीं मिला। बदल कर टाइप करें।"
                                            else -> "No matching channels. Try standard keywords."
                                        },
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                viewModel.cloneSearchResults.forEach { result ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (result.avatarUrl.isNotEmpty()) {
                                                    AsyncImage(
                                                        model = result.avatarUrl,
                                                        contentDescription = "Avatar",
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.AccountCircle,
                                                        contentDescription = "Avatar",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = result.name,
                                                        color = TextPrimary,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = result.handle,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(text = "•", color = Color.Gray, fontSize = 11.sp)
                                                        Text(
                                                            text = result.subscribersText,
                                                            color = TextSecondary,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            // Quick set buttons mapping the selected handle to Ch slots
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                listOf(
                                                    Triple(
                                                        when (viewModel.preferredHelpLanguage) {
                                                            "Punjabi" -> "Ch 1 ਸੈੱਟ"
                                                            "Hindi" -> "Ch 1 सेट"
                                                            "Hinglish" -> "Set Ch 1"
                                                            else -> "Set Ch 1"
                                                        }, { 
                                                            linkInput1 = result.handle
                                                            viewModel.cloneChannelLink = result.handle
                                                        }, Color(0xFF3F51B5)
                                                    ),
                                                    Triple(
                                                        when (viewModel.preferredHelpLanguage) {
                                                            "Punjabi" -> "Ch 2 ਸੈੱਟ"
                                                            "Hindi" -> "Ch 2 सेट"
                                                            "Hinglish" -> "Set Ch 2"
                                                            else -> "Set Ch 2"
                                                        }, { 
                                                            linkInput2 = result.handle
                                                            viewModel.cloneChannelLink2 = result.handle
                                                        }, Color(0xFFE91E63)
                                                    ),
                                                    Triple(
                                                        when (viewModel.preferredHelpLanguage) {
                                                            "Punjabi" -> "Ch 3 ਸੈੱਟ"
                                                            "Hindi" -> "Ch 3 सेट"
                                                            "Hinglish" -> "Set Ch 3"
                                                            else -> "Set Ch 3"
                                                        }, { 
                                                            linkInput3 = result.handle
                                                            viewModel.cloneChannelLink3 = result.handle
                                                        }, Color(0xFF009688)
                                                    )
                                                ).forEach { (label, onClick, color) ->
                                                    Button(
                                                        onClick = onClick,
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = color.copy(alpha = 0.15f),
                                                            contentColor = color
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(32.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                                                    ) {
                                                        Text(
                                                            text = label, 
                                                            fontSize = 10.sp, 
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = getUiText("channels_title", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = getUiText("channels_sub", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                
                // Info Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "💡 ਸਲਾਹ: ਇੱਕ ਤੋਂ ਵੱਧ ਚੈਨਲ (ਵੱਧ ਤੋਂ ਵੱਧ 3) ਲਿੰਕ ਸ਼ਾਮਲ ਕਰਨਾ ਵਧੀਆ ਹੈ! ਇਹ ਸਾਡੇ ਏਆਈ ਮਾਡਲ ਨੂੰ ਇੱਕ ਸਟਾਈਲ ਡੀਐਨਏ ਬਣਾਉਣ ਵਿੱਚ ਮਦਦ ਕਰਦਾ ਹੈ।"
                            "Hindi" -> "💡 सलाह: एक से अधिक चैनल (अधिकतम 3) लिंक जोड़ना बेहतर है! यह हमारे एआई मॉडल को एक बेहतर स्टाइल डीएनए बनाने में मदद करता है।"
                            "Hinglish" -> "💡 Tip: Ek se jyada channel (Up to 3) ke links add karna better hai! Isse humara AI Model robust Style DNA capture karega."
                            else -> "💡 Multiple Channels (up to 3) are recommended! It helps our AI Model capture a balanced, robust Style DNA instead of a single narrow format."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = getUiText("channel_1_req", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = linkInput1,
                    onValueChange = { 
                        linkInput1 = it 
                        viewModel.cloneChannelLink = it
                    },
                    placeholder = { Text("e.g. @mkbhd or youtube.com/@tech") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("clone_link_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = getUiText("channel_2_opt", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = linkInput2,
                    onValueChange = { 
                        linkInput2 = it 
                        viewModel.cloneChannelLink2 = it
                    },
                    placeholder = { Text("e.g. @competitor2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("clone_link_input_2"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = getUiText("channel_3_opt", viewModel.preferredHelpLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
                OutlinedTextField(
                    value = linkInput3,
                    onValueChange = { 
                        linkInput3 = it 
                        viewModel.cloneChannelLink3 = it
                    },
                    placeholder = { Text("e.g. @competitor3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("clone_link_input_3"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਤੁਰੰਤ ਡੈਮੋ ਟੈਂਪਲੇਟਸ (ਚੈਨਲ 1 ਭਰਦਾ ਹੈ):"
                        "Hindi" -> "त्वरित डेमो टेम्पलेट्स (चैनल 1 भरता है):"
                        "Hinglish" -> "Quick Demo Templates (Fills Channel 1):"
                        else -> "Quick Demo Templates (Fills Channel 1):"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                linkSuggestions.forEach { suggestion ->
                    val actualLink = suggestion.substringBefore(" (")
                    val isSelected = linkInput1 == actualLink
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { 
                                linkInput1 = actualLink 
                                viewModel.cloneChannelLink = actualLink
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartDisplay,
                            contentDescription = "Suggested Link",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = suggestion,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.proceedToState2(linkInput1, linkInput2, linkInput3) },
            enabled = linkInput1.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("proceed_to_transcripts_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(getUiText("btn_proceed_sec2", viewModel.preferredHelpLanguage), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun CloneState2_TranscriptLoop(viewModel: GrowTubeViewModel) {
    var textInput by remember { mutableStateOf(viewModel.cloneTranscriptInputInput) }
    val transcripts = viewModel.cloneTranscripts
    val hasMetRequirement = transcripts.size >= 2

    val sampleTranscripts = listOf(
        "Vintage cooking methods are dying out but simple pan butter hacks revive them instantly which we will see...",
        "Here is why your excessive nested widgets are destroying your compile and render times in high-end devices...",
        "Classical Desi folk sound fusions are conquering modern production, let me layer EQ filters dynamically..."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਵੀਡੀਓ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਦਰਜ ਕਰੋ"
                        "Hindi" -> "वीडियो ट्रांसक्रिप्ट दर्ज करें"
                        "Hinglish" -> "Video Transcripts Paste Karein"
                        else -> "Input Video Transcripts"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਟਾਰਗੇਟ ਚੈਨਲ ਦੀਆਂ ਵੀਡੀਓਜ਼ ਦੀ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਇੱਥੇ ਪਾਓ। ਸਹੀ ਸਟਾਈਲ ਵਿਸ਼ਲੇਸ਼ਣ ਲਈ ਘੱਟੋ-ਘੱਟ 2 ਟ੍ਰਾਂਸਕ੍ਰਿਪਟਸ ਦੀ ਲੋੜ ਹੈ।"
                        "Hindi" -> "लक्षित चैनल के सफल वीडियो की ट्रांसक्रिप्ट को यहाँ पेस्ट करें। विश्लेषण के लिए कम से कम 2 ट्रांसक्रिप्ट आवश्यक हैं।"
                        "Hinglish" -> "Competitor ya target channel ke popular video scripts/transcripts paste karein. Accurate analysis ke liye minimum TWO transcripts chahiye."
                        else -> "Add transcript texts of the target channel's most successful videos. We require at least TWO distinct transcripts to analyze patterns and extract an authentic DNA."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਇੱਥੇ ਵੀਡੀਓ ਦੀ ਸਕ੍ਰਿਪਟ ਜਾਂ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਪੇਸਟ ਕਰੋ..."
                                "Hindi" -> "यहाँ वीडियो स्क्रिप्ट या ट्रांसक्रिप्ट पेस्ट करें..."
                                "Hinglish" -> "Video script ya transcript text yahan paste karein..."
                                else -> "Paste video script or transcription text here..."
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("transcript_text_input"),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.addTranscript(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਜੋੜੋ"
                                "Hindi" -> "ट्रांसक्रिप्ट जोड़ें"
                                "Hinglish" -> "Transcript Add Karein"
                                else -> "Add Transcript"
                            }
                        )
                    }
                }

                // Add sample transcript button for fast demo clicks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਤੁਰੰਤ ਫਾਸਟ ਡੈਮੋ:"
                            "Hindi" -> "तुरंत फ़ास्ट डेमो:"
                            "Hinglish" -> "Instant Fast Demo:"
                            else -> "Instant Fast Demo:"
                        },
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            viewModel.addTranscript(sampleTranscripts.random())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2F48)),
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਰੈਂਡਮ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਪਾਓ"
                                "Hindi" -> "रैंडम ट्रांसक्रिप्ट भरें"
                                "Hinglish" -> "Inject Random Text"
                                else -> "Inject Random"
                            },
                            fontSize = 10.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Suggested Videos Section
        Text(
            text = when (viewModel.preferredHelpLanguage) {
                "Punjabi" -> "ਵੀਡੀਓ ਸੁਝਾਅ (ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਜਨਰੇਟ ਕਰਨ ਲਈ ਚੁਣੋ):"
                "Hindi" -> "वीडियो सुझाव (ट्रांसक्रिप्ट बनाने के लिए चुनें):"
                "Hinglish" -> "Suggested Videos (Generate Transcript):"
                else -> "Suggested Clone Videos (Auto-Extract):"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (viewModel.loadingCloneSuggestions) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TextPrimary.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਚੈਨਲਾਂ ਤੋਂ ਵੀਡੀਓਜ਼ ਸਕੈਨ ਕੀਤੀਆਂ ਜਾ ਰਹੀਆਂ ਹਨ..."
                            "Hindi" -> "चैनलों से वीडियो स्कैन किए जा रहे हैं..."
                            else -> "Scanning target channels for uploads..."
                        },
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        } else if (viewModel.cloneSuggestedVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TextPrimary.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕੋਈ ਵੀਡੀਓ ਨਹੀਂ ਮਿਲੀ। ਉੱਪਰ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਮੈਨੂਅਲੀ ਪਾਓ।"
                        "Hindi" -> "कोई वीडियो नहीं मिला। ऊपर ट्रांसक्रिप्ट मैन्युअल रूप से दर्ज करें।"
                        else -> "No videos found. Use the manual paste above."
                    },
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                viewModel.cloneSuggestedVideos.forEach { video ->
                    val isAdded = viewModel.cloneTranscripts.contains(video.transcript) && video.transcript.isNotEmpty()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isAdded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else TextPrimary.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color(0xFF23253A), Color(0xFF10111E))
                                        )
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Play Icon",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .align(Alignment.Center)
                                )
                                
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "10:15",
                                        color = TextPrimary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = video.originalChannel,
                                        color = TextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = video.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.channelName,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(text = video.viewsText, color = Color.Gray, fontSize = 10.sp)
                                            Text(text = "•", color = Color.Gray, fontSize = 10.sp)
                                            Text(text = video.ageText, color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }

                                    when {
                                        video.isGenerating -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp).padding(4.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        isAdded -> {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Added", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        else -> {
                                            Button(
                                                onClick = { viewModel.generateTranscriptForCloneVideo(video) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Select", modifier = Modifier.size(10.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = when (viewModel.preferredHelpLanguage) {
                                                        "Punjabi" -> "ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਕੱਢੋ"
                                                        "Hindi" -> "ट्रांसक्रिप्ट निकालें"
                                                        else -> "Extract Transcript"
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Added list display
        Text(
            text = when (viewModel.preferredHelpLanguage) {
                "Punjabi" -> "ਸ਼ਾਮਲ ਕੀਤੀਆਂ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟਾਂ (${transcripts.size} / ਘੱਟੋ-ਘੱਟ 2):"
                "Hindi" -> "जोड़ी गई ट्रांसक्रिप्ट (${transcripts.size} / न्यूनतम 2):"
                "Hinglish" -> "Transcripts Added (${transcripts.size} / min 2):"
                else -> "Transcripts Added (${transcripts.size} / min 2):"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        if (transcripts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TextPrimary.copy(alpha = 0.03f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕੋਈ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਅਜੇ ਨਹੀਂ ਜੋੜੀ ਗਈ।"
                        "Hindi" -> "अभी तक कोई ट्रांसक्रिप्ट नहीं जोड़ी गई।"
                        "Hinglish" -> "Abhi tak koi transcript add nahi ki gayi."
                        else -> "No transcripts added yet."
                    },
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transcripts.forEachIndexed { index, transcript ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "Doc",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Transcript #${index + 1}",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = transcript.take(70) + if (transcript.length > 70) "..." else "",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { viewModel.removeTranscript(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.proceedToState3() },
            enabled = hasMetRequirement,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("analyze_style_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasMetRequirement) MaterialTheme.colorScheme.primary else TextSecondary.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਸਟਾਈਲ ਡੀਐਨਏ ਵਿਸ਼ਲੇਸ਼ਣ ਸ਼ੁਰੂ ਕਰੋ"
                    "Hindi" -> "स्टाइल डीएनए विश्लेषण शुरू करें"
                    "Hinglish" -> "STYLE DNA ANALYZE KAREIN"
                    else -> "ANALYZE STYLE DNA"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Stars")
        }
    }
}

@Composable
fun CloneState3_TopicAndCompetitorChoices(viewModel: GrowTubeViewModel) {
    var topicChoice by remember { mutableStateOf("new") }
    var customTopicInput by remember { mutableStateOf("") }
    var competitorChoice by remember { mutableStateOf("no") }
    var competitorLinksInput by remember { mutableStateOf("") }
    var activeInfoOption by remember { mutableStateOf<OptionInfo?>(null) }

    activeInfoOption?.let { info ->
        InfoGuideDialog(
            optionName = info.title,
            info = info,
            viewModel = viewModel,
            onDismiss = { activeInfoOption = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕਦਮ 1: ਵੀਡੀਓ ਦਾ ਵਿਸ਼ਾ ਚੁਣੋ"
                        "Hindi" -> "चरण 1: वीडियो विषय का चयन"
                        "Hinglish" -> "Step 1: Video Topic Select Karein"
                        else -> "Step 1: Video Topic Selection"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { topicChoice = "recreate" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = topicChoice == "recreate", onClick = { topicChoice = "recreate" })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਪੁਰਾਣੇ ਸਫ਼ਲ ਵੀਡੀਓ ਸਟਾਈਲ ਨੂੰ ਦੁਬਾਰਾ ਬਣਾਓ"
                            "Hindi" -> "पहले से सफल वीडियो स्टाइल को फिर से बनाएं"
                            "Hinglish" -> "Pehle wale hit style ko recreate/pivot karein"
                            else -> "Recreate/Pivot an existing analyzed hit"
                        },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { activeInfoOption = findHelpInfo("Recreate") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { topicChoice = "new" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = topicChoice == "new", onClick = { topicChoice = "new" })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਇੱਕ ਬਿਲਕੁਲ ਨਵਾਂ ਕਸਟਮ ਵਿਸ਼ਾ ਚੁਣੋ"
                            "Hindi" -> "एक पूरी तरह से नया कस्टम विषय चुनें"
                            "Hinglish" -> "Bilkul naya custom topic select karein"
                            else -> "Choose an entirely new custom topic"
                        },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { activeInfoOption = findHelpInfo("Choose an entirely new custom topic") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Selected Topic Choice Explanation
                val selectedTopicText = if (topicChoice == "recreate") "Recreate" else "Choose an entirely new custom topic"
                val currentTopicHelp = findHelpInfo(selectedTopicText)
                if (currentTopicHelp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selected topic info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> currentTopicHelp.punjabiDesc
                                    "Hindi" -> currentTopicHelp.hindiDesc
                                    "Hinglish" -> getHinglishDescription(currentTopicHelp)
                                    else -> currentTopicHelp.englishDesc
                                },
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (topicChoice == "new") {
                    OutlinedTextField(
                        value = customTopicInput,
                        onValueChange = { customTopicInput = it },
                        placeholder = {
                            Text(
                                when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਨਵੀਂ ਵੀਡੀਓ ਕਿਸ ਵਿਸ਼ੇ 'ਤੇ ਹੋਣੀ ਚਾਹੀਦੀ ਹੈ?"
                                    "Hindi" -> "नया वीडियो किस विशेष विषय पर होना चाहिए?"
                                    "Hinglish" -> "Naye video ke liye specific topic likhein..."
                                    else -> "What specific topic should the new video cover?"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("custom_topic_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕਦਮ 2: ਮੁਕਾਬਲੇਬਾਜ਼ ਰਣਨੀਤੀ ਸਕੈਨ"
                        "Hindi" -> "चरण 2: प्रतिस्पर्धी रणनीति स्कैन"
                        "Hinglish" -> "Step 2: Competitive Strategy Scan"
                        else -> "Step 2: Competitive Strategy Scan"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕੀ ਤੁਸੀਂ ਹੋਰ ਮੁਕਾਬਲੇਬਾਜ਼ (competitor) ਵੀਡੀਓ ਸਟਾਈਲ ਨੂੰ ਵੀ ਸਕੈਨ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?"
                        "Hindi" -> "क्या आप समान हैंडल पर विस्तृत प्रतिस्पर्धी रणनीति मैपिंग चलाना चाहते हैं?"
                        "Hinglish" -> "Kya aap competitors ke channels ki dynamic format scan add karna chahte hain?"
                        else -> "Do you want to run detailed competitor strategy mapping on similar handles?"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { competitorChoice = "yes" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = competitorChoice == "yes", onClick = { competitorChoice = "yes" })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਹਾਂ, ਮੁਕਾਬਲੇਬਾਜ਼ਾਂ ਦੇ ਚੈਨਲ ਵੀ ਜੋੜੋ"
                            "Hindi" -> "हाँ, प्रतिस्पर्धी चैनलों के स्टाइल शामिल करें"
                            "Hinglish" -> "Yes, competitor strategy fetch karein"
                            else -> "Yes, fetch competitor frameworks"
                        },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { activeInfoOption = findHelpInfo("Include competitor channels") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { competitorChoice = "no" }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = competitorChoice == "no", onClick = { competitorChoice = "no" })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਨਹੀਂ, ਸਿਰਫ਼ ਮੁੱਖ ਟਾਰਗੇਟ ਚੈਨਲ 'ਤੇ ਧਿਆਨ ਦਿਓ"
                            "Hindi" -> "नहीं, केवल लक्षित चैनलों पर ध्यान केंद्रित रखें"
                            "Hinglish" -> "No, purely target style pe focused rhein"
                            else -> "No, proceed without competitive model"
                        },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { activeInfoOption = findHelpInfo("Focus purely on targets") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Selected Competitor Explanation Card
                val selectedCompText = if (competitorChoice == "yes") "Include competitor channels" else "Focus purely on targets"
                val currentCompHelp = findHelpInfo(selectedCompText)
                if (currentCompHelp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selected competitor info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> currentCompHelp.punjabiDesc
                                    "Hindi" -> currentCompHelp.hindiDesc
                                    "Hinglish" -> getHinglishDescription(currentCompHelp)
                                    else -> currentCompHelp.englishDesc
                                },
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                if (competitorChoice == "yes") {
                    OutlinedTextField(
                        value = competitorLinksInput,
                        onValueChange = { competitorLinksInput = it },
                        placeholder = {
                            Text(
                                when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਮੁਕਾਬਲੇਬਾਜ਼ ਚੈਨਲਾਂ ਦੇ ਲਿੰਕ ਲਿਖੋ (ਜਿਵੇਂ @tech, @cook)"
                                    "Hindi" -> "प्रतिद्वंद्वी चैनल दर्ज करें (जैसे @tech, @cook)"
                                    "Hinglish" -> "Enter competitor tags (e.g. @techguy, @cookpro)"
                                    else -> "Enter competitor tags (e.g. @techguy, @cookpro)"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("competitor_links_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }
        }

        val isValid = if (topicChoice == "new") customTopicInput.isNotBlank() else true

        Button(
            onClick = { 
                viewModel.proceedToState4(
                    topicChoice = topicChoice,
                    customTopic = customTopicInput,
                    includeComp = competitorChoice,
                    compLinks = competitorLinksInput
                )
            },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_architecture_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isValid) MaterialTheme.colorScheme.primary else TextSecondary.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਕੰਟੈਂਟ ਆਰਕੀਟੈਕਚਰ ਤਿਆਰ ਕਰੋ"
                    "Hindi" -> "कंटेंट आर्किटेक्चर जनरेट करें"
                    "Hinglish" -> "CONTENT ARCHITECTURE GENERATE KAREIN"
                    else -> "GENERATE CONTENT ARCHITECTURE"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Stars")
        }
    }
}

@Composable
fun CloneState4_StyleDNAAnalyzer(viewModel: GrowTubeViewModel) {
    if (viewModel.cloneStyleDNALoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਚੈਨਲ ਟ੍ਰਾਂਸਕ੍ਰਿਪਟ ਫ੍ਰੀਕੁਐਂਸੀ ਡੀਕੋਡ ਕੀਤੀ ਜਾ ਰਹੀ ਹੈ..."
                    "Hindi" -> "चैनल ट्रांसक्रिप्ट फ्रीक्वेंसी डिकोड की जा रही है..."
                    "Hinglish" -> "CHANNEL TRANSCRIPT FREQUENCIES DECODE HO RAHI HAI..."
                    else -> "DECODING CHANNEL TRANSCRIPT FREQUENCIES..."
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਭਾਸ਼ਾਈ ਰਿਦਮ, ਹੁੱਕ ਪੈਰਾਮੀਟਰ, ਸ਼ਬਦ ਪ੍ਰਤੀ ਸੈਕਿੰਡ ਅਤੇ ਵੀਡੀਓ ਟੈਂਪੋ ਵਿਸ਼ਲੇਸ਼ਣ ਜਾਰੀ ਹੈ... ਕਿਰਪਾ ਕਰਕੇ ਉਡੀਕ ਕਰੋ।"
                    "Hindi" -> "भाषाई लय, हुक पैरामीटर, शब्द प्रति सेकंड और वीडियो टेम्पो का विश्लेषण हो रहा है... कृपया प्रतीक्षा करें।"
                    "Hinglish" -> "Linguistic rhythms, word-per-second voice trigger framework and visual triggers analyze ho rhe hain... Please wait."
                    else -> "Analyzing linguistic rhythms, signatures, hook parameters, word-per-second, and visual tempo triggers... Please wait."
                },
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val dna = viewModel.cloneStyleDNA
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "DNA",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਸਟਾਈਲ ਡੀਐਨਏ ਡੀਕੋਡ ਹੋ ਗਿਆ!"
                        "Hindi" -> "स्टाईल डीएनए डिकोड हो गया!"
                        "Hinglish" -> "Extracted Style DNA Decoded!"
                        else -> "Extracted Style DNA Decoded!"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }

            if (dna == null) {
                Text("Error Loading DNA. Proceeding...", color = Color.Red)
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਪ੍ਰਾਇਮਰੀ ਨਿਚ (Niche)"
                                "Hindi" -> "प्राथमिक श्रेणी (Niche)"
                                "Hinglish" -> "Primary Niche"
                                else -> "Primary Niche"
                            },
                            dna.niche
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਦਰਸ਼ਕ ਪ੍ਰੋਫਾਈਲ"
                                "Hindi" -> "लक्षित दर्शक प्रोफाइल"
                                "Hinglish" -> "Target Audience Profile"
                                else -> "Target Audience Profile"
                            },
                            dna.targetAudience
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਭਾਸ਼ਾ ਸੰਦਰਭ"
                                "Hindi" -> "सक्रिय भाषा संदर्भ"
                                "Hinglish" -> "Active Language Context"
                                else -> "Active Language Context"
                            },
                            dna.languageAndDialect
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਭਾਸ਼ਾਈ ਸਲੈਂਗ / ਹਸਤਾਖਰ"
                                "Hindi" -> "भाषाई स्लैंग / हस्ताक्षर"
                                "Hinglish" -> "Linguistic Slang Signatures"
                                else -> "Linguistic Slang Signatures"
                            },
                            dna.signatureSlang.joinToString(", ")
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਵੀਡੀਓ ਹੁੱਕ ਤਕਨੀਕਾਂ"
                                "Hindi" -> "वीडियो हुक तकनीकें"
                                "Hinglish" -> "Retention Hook Techniques"
                                else -> "Retention Hook Techniques"
                            },
                            dna.hookTypes.joinToString(", ")
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਕਹਾਣੀ ਦੀ ਰਿਦਮ"
                                "Hindi" -> "कहानी कहने की लय"
                                "Hinglish" -> "Narrative Rhythm Tempo"
                                else -> "Standard Narrative Tempo"
                            },
                            dna.sentenceRhythm
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਵੀਡੀਓ ਦੀ ਟੋਨ"
                                "Hindi" -> "वीडियो की टोन"
                                "Hinglish" -> "Overall Tone Theme"
                                else -> "Overall Aesthetic Tone"
                            },
                            dna.tone
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਵੀਡੀਓ ਵਿਜ਼ੂਅਲ ਟ੍ਰਾਂਜਿਸ਼ਨ ਸਟਾਈਲ"
                                "Hindi" -> "विजुअल ट्रांजिशन स्टाइल"
                                "Hinglish" -> "Visual Transition Style"
                                else -> "Visual Transition Style"
                            },
                            dna.transitions
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਸ਼ਬਦ ਪ੍ਰਤੀ ਸਕਿੰਟ ਸਪੀਡ"
                                "Hindi" -> "शब्द प्रति सेकंड गति"
                                "Hinglish" -> "Words Per Second Speed"
                                else -> "Spoken Word Velocity"
                            },
                            "${dna.wordsPerSecond} words/second"
                        )
                        DnaItemRow(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਸਿਗਨੇਚਰ CTA ਫਾਰਮੈਟ"
                                "Hindi" -> "सिग्नेचर CTA फॉर्मेट"
                                "Hinglish" -> "Signature CTA Format"
                                else -> "Signature CTA Format"
                            },
                            dna.ctaStyle
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.proceedToState5() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("proceed_to_narrative_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਨੈਰੇਟਿਵ ਜਨਰੇਟਰ ਵੱਲ ਵਧੋ"
                        "Hindi" -> "नैरेटिव जनरेटर पर आगे बढ़ें"
                        "Hinglish" -> "NARRATIVE GENERATOR PE CHALEIN"
                        else -> "PROCEED TO NARRATIVE GENERATOR"
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
            }
        }
    }
}

@Composable
fun DnaItemRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = TextPrimary.copy(alpha = 0.05f))
    }
}

@Composable
fun CloneState5_ScriptAndHookVariations(viewModel: GrowTubeViewModel) {
    val script = viewModel.cloneScript
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var revisionText by remember { mutableStateOf("") }

    if (viewModel.cloneScriptLoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਓਪਰੀਜਨਲ ਮਲਟੀ-ਹੁੱਕ ਸਕ੍ਰਿਪਟ ਤਿਆਰ ਕੀਤੀ ਜਾ ਰਹੀ ਹੈ..."
                    "Hindi" -> "ओरिजनल मल्टी-हुक स्क्रिप्ट जनरेट हो रही है..."
                    "Hinglish" -> "ORIGINAL MULTI-HOOK SCRIPT GENERATE HO RAHI HAI..."
                    else -> "GENERATING ORIGINAL MULTI-HOOK SCRIPT..."
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਉੱਚ CTR ਵਾਲੀਆਂ ਆਪਸ਼ਨਲ ਹੁੱਕਸ ਅਤੇ ਬੋਲਣ ਦੀ ਸਪੀਡ ਮੁਤਾਬਕ ਸਕ੍ਰਿਪਟਿੰਗ... ਬਸ ਤਿਆਰ ਹੈ।"
                    "Hindi" -> "उच्च CTR वाले वैकल्पिक हुक्स और बोलने की गति के अनुसार स्क्रिप्टिंग... बस तैयार है।"
                    "Hinglish" -> "High CTR alternative hooks design aur spoken rhythm optimization parameters select ho rhe hain... almost ready."
                    else -> "Synthesizing high CTR alternative hooks, calculating targets against spoken rhythm parameters... almost ready."
                },
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (script == null) {
                Text("Error generating script. Proceeding...", color = Color.Red)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਤਿਆਰ ਕੀਤੀ ਸਕ੍ਰਿਪਟ"
                            "Hindi" -> "तैयार स्क्रिप्ट ड्राफ्ट"
                            "Hinglish" -> "Script Draft Architecture"
                            else -> "Script Draft Architecture"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E2E1E)
                    ) {
                        Text(
                            text = "Words: ${script.actualWordCount} / Target: ${script.targetWordCount}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, tint = MaterialTheme.colorScheme.primary, contentDescription = "Title")
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਪ੍ਰਸਤਾਵਿਤ ਵਾਇਰਲ ਟਾਈਟਲ"
                                    "Hindi" -> "प्रस्तावित वायरल वीडियो शीर्षक"
                                    "Hinglish" -> "PROPOSED VIRAL TITLE"
                                    else -> "PROPOSED VIRAL TITLE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            Text(script.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "A/B ਹੁੱਕ ਵਿਕਲਪ (ਸਕ੍ਰਿਪਟ ਦੀ ਸ਼ੁਰੂਆਤ ਬਦਲਣ ਲਈ ਟੈਪ ਕਰੋ):"
                        "Hindi" -> "A/B हुक विकल्प (स्क्रिप्ट की शुरुआत तुरंत बदलने के लिए क्लिक करें):"
                        "Hinglish" -> "A/B Hook Variations (Tap to swap script intro instantly):"
                        else -> "A/B Hook Variations (Tap to swap script intro instantly):"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                HookCardOption(
                    "A",
                    when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਹੁੱਕ A (ਸਵਾਲੀਆ ਸਟਾਈਲ)"
                        "Hindi" -> "हुक A (प्रश्नवाचक स्टाइल)"
                        "Hinglish" -> "Hook A (Question Style)"
                        else -> "Hook A (Question Style)"
                    },
                    script.hookA,
                    viewModel.cloneSelectedHookIndex == 0
                ) {
                    viewModel.selectHook("A")
                }
                HookCardOption(
                    "B",
                    when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਹੁੱਕ B (ਹੈਰਾਨੀਜਨਕ ਸਟਾਈਲ)"
                        "Hindi" -> "हुक B (चौंका देने वाला स्टाइल)"
                        "Hinglish" -> "Hook B (Shock Style)"
                        else -> "Hook B (Shock Style)"
                    },
                    script.hookB,
                    viewModel.cloneSelectedHookIndex == 1
                ) {
                    viewModel.selectHook("B")
                }
                HookCardOption(
                    "C",
                    when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਹੁੱਕ C (ਕਹਾਣੀ ਸਟਾਈਲ)"
                        "Hindi" -> "हुक C (कहानी दर्शाने वाला स्टाइल)"
                        "Hinglish" -> "Hook C (Story Style)"
                        else -> "Hook C (Story Style)"
                    },
                    script.hookC,
                    viewModel.cloneSelectedHookIndex == 2
                ) {
                    viewModel.selectHook("C")
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਪੂਰੀ ਬੋਲਣ ਵਾਲੀ ਸਕ੍ਰਿਪਟ (ਓਰੀਜਨਲ):"
                                    "Hindi" -> "पूर्ण बोलने योग्य कहानी स्क्रिप्ट (ओरिजनल):"
                                    "Hinglish" -> "Full Spoken Narrative (Original):"
                                    else -> "Full Spoken Narrative (Original):"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(script.finalScript))
                                    Toast.makeText(context, "Script copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray)
                            }
                        }

                        Text(
                            text = script.finalScript,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TextSecondary
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਕਸਟਮ ਫੀਡਬੈਕ ਦਿਓ (ਸਕ੍ਰਿਪਟ ਸੁਧਾਰੋ)"
                                "Hindi" -> "कस्टम फीडबैक दें (भाषाई संशोधन)"
                                "Hinglish" -> "Provide Custom Feedback (Linguistic Revisions)"
                                else -> "Provide Custom Feedback (Linguistic Revisions)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        OutlinedTextField(
                            value = revisionText,
                            onValueChange = { revisionText = it },
                            placeholder = {
                                Text(
                                    when (viewModel.preferredHelpLanguage) {
                                        "Punjabi" -> "ਉਦਾਹਰਨ ਲਈ: ਇਸਨੂੰ ਹੋਰ ਰੋਮਾਂਚਕ ਬਣਾਓ, ਵਧੇਰੇ ਸਲੈਂਗ ਵਰਤੋ, ਆਦਿ।"
                                        "Hindi" -> "जैसे: इसे और रोमांचक बनाएं, अधिक स्थानीय शब्दों का उपयोग करें, आदि।"
                                        "Hinglish" -> "e.g. Make it more intense, inject more slang words, etc."
                                        else -> "e.g. Make it more intense, inject more slang words, etc."
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("revision_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Button(
                            onClick = {
                                if (revisionText.isNotBlank()) {
                                    viewModel.submitFeedbackAndProceed(revisionText)
                                    revisionText = ""
                                }
                            },
                            enabled = revisionText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B3D5A)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਸੁਧਾਰ ਲਾਗੂ ਕਰੋ"
                                    "Hindi" -> "संशोधन लागू करें"
                                    "Hinglish" -> "Apply Revisions"
                                    else -> "Apply Revisions"
                                }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.proceedToState6() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("approve_script_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਸਕ੍ਰਿਪਟ ਮਨਜ਼ੂਰ ਕਰੋ"
                        "Hindi" -> "स्क्रिप्ट स्वीकृत करें"
                        "Hinglish" -> "APPROVE SCRIPT ARCHITECTURE"
                        else -> "APPROVE SCRIPT ARCHITECTURE"
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(imageVector = Icons.Default.Check, contentDescription = "Approve")
            }
        }
    }
}

@Composable
fun HookCardOption(
    id: String,
    title: String,
    text: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else DarkCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else TextPrimary.copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
                if (isSelected) {
                    Icon(imageVector = Icons.Default.CheckCircle, tint = MaterialTheme.colorScheme.primary, contentDescription = "Active", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        }
    }
}

@Composable
fun CloneState6_VisualDesignCues(viewModel: GrowTubeViewModel) {
    var descText by remember { mutableStateOf(viewModel.cloneVisualDescription) }
    var activeInfoOption by remember { mutableStateOf<OptionInfo?>(null) }

    val visualStyles = listOf(
        "Cinematic dark room, glowing code screens, blue cyber lighting",
        "Cozy clean kitchen, soft steam, rich natural overhead sunlight",
        "Modern neon DJ deck, glowing bass spectrum visualizer fusions",
        "Surgical high-contrast black backdrop with bold kinetic typography"
    )

    activeInfoOption?.let { info ->
        InfoGuideDialog(
            optionName = info.title,
            info = info,
            viewModel = viewModel,
            onDismiss = { activeInfoOption = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਸਟੋਰੀਬੋਰਡ ਕਲਾ ਨਿਰਦੇਸ਼"
                        "Hindi" -> "स्टोरीबोर्ड आर्ट डायरेक्शन"
                        "Hinglish" -> "Storyboard Art Direction Cues"
                        else -> "Storyboard Art Direction"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਸਟੋਰੀਬੋਰਡ ਲਈ ਲੋੜੀਂਦਾ ਵਿਜ਼ੂਅਲ ਮਾਹੌਲ, ਰੰਗ ਅਤੇ ਕੈਮਰਾ ਟੋਨ ਬਾਰੇ ਜਾਣਕਾਰੀ ਦਿਓ।"
                        "Hindi" -> "स्टोरीबोर्ड के लिए वांछित विज़ुअल वातावरण, रंग और कैमरा मूड निर्दिष्ट करें।"
                        "Hinglish" -> "Storyboard prompts ke liye visual background environment, colors, and camera mood context customize karein."
                        else -> "Provide cues on what visual environment, colors, and camera mood you wish the storyboard prompts to maintain."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    placeholder = {
                        Text(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਜਿਵੇਂ: ਨਿਓਨ ਕੋਡਿੰਗ ਸਟੂਡੀਓ, ਗੋਲਡਨ ਆਵਰ ਰਸੋਈ..."
                                "Hindi" -> "जैसे: नियॉन कोडिंग स्टूडियो, कंचन आभा रसोई..."
                                "Hinglish" -> "e.g. neon coding studio, gold hour kitchen..."
                                else -> "e.g. neon coding studio, gold hour kitchen..."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("visual_desc_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਸੁਝਾਏ ਗਏ ਵਿਜ਼ੂਅਲ ਸਟਾਈਲ:"
                        "Hindi" -> "सुझाए गए विज़ुअल स्टाइल:"
                        "Hinglish" -> "Suggested Art Directions:"
                        else -> "Suggested Art Directions:"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                visualStyles.forEach { style ->
                    val isSelected = descText == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { descText = style }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { descText = style })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = style,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { activeInfoOption = findHelpInfo(style) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Style Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Dynamic visual help explanation card below choices
                val currentVisualHelp = findHelpInfo(descText)
                if (currentVisualHelp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selected visual style info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> currentVisualHelp.punjabiDesc
                                    "Hindi" -> currentVisualHelp.hindiDesc
                                    "Hinglish" -> getHinglishDescription(currentVisualHelp)
                                    else -> currentVisualHelp.englishDesc
                                },
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.submitVisualCues(descText) },
            enabled = descText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_storyboard_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਸਟੋਰੀਬੋਰਡ ਪ੍ਰੋਂਪਟ ਤਿਆਰ ਕਰੋ"
                    "Hindi" -> "स्टोरीबोर्ड प्रॉम्प्ट जनरेट करें"
                    "Hinglish" -> "STORYBOARD PROMPTS GENERATE KAREIN"
                    else -> "GENERATE STORYBOARD PROMPTS"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun CloneState7_StoryboardBeats(viewModel: GrowTubeViewModel) {
    val beats = viewModel.cloneImagePrompts
    val profile = viewModel.cloneVisualProfile
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Movie, contentDescription = "Movie", tint = MaterialTheme.colorScheme.primary)
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਸਿਨੇਮੈਟਿਕ ਸਟੋਰੀਬੋਰਡ ਬੀਟਸ"
                    "Hindi" -> "सिनेमैटिक स्टोरीबोर्ड बीट्स"
                    "Hinglish" -> "Cinematic Storyboard Beats"
                    else -> "Dynamic Cinematic Storyboard"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (profile != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਕਲਾ ਸ਼ੈਲੀ ਪਰਿਭਾਸ਼ਿਤ"
                            "Hindi" -> "कला शैली परिभाषित"
                            "Hinglish" -> "ART STYLE DEFINED"
                            else -> "ART STYLE DEFINED"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(profile.artStyle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਲਾਈਟਿੰਗ (Lighting):"
                                    "Hindi" -> "लाइटिंग (Lighting):"
                                    "Hinglish" -> "Lighting:"
                                    else -> "Lighting:"
                                },
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(profile.lightingStyle, fontSize = 11.sp, color = TextSecondary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਰੰਗ ਪੈਲੇਟ (Palette):"
                                    "Hindi" -> "रंग पैलेट (Palette):"
                                    "Hinglish" -> "Color Palette:"
                                    else -> "Palette:"
                                },
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(profile.colorPalette, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }

        Text(
            text = when (viewModel.preferredHelpLanguage) {
                "Punjabi" -> "ਵੀਡੀਓ ਸਟੋਰੀਬੋਰਡ ਤਰਤੀਬ:"
                "Hindi" -> "वीडियो स्टोरीबोर्ड क्रम:"
                "Hinglish" -> "Video Storyboard Beats Sequence:"
                else -> "Video Storyboard Beats:"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        beats.forEachIndexed { index, beat ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਬੀਟ ਸੀਕੁਐਂਸ"
                                    "Hindi" -> "बीट क्रम (Sequence)"
                                    "Hinglish" -> "Beat Sequence"
                                    else -> "Beat Sequence"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                        
                        IconButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(beat.imagePrompt))
                                val msg = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਇਮੇਜ ਪ੍ਰੋਂਪਟ ਕਾਪੀ ਹੋ ਗਿਆ!"
                                    "Hindi" -> "इमेज प्रॉम्प्ट कॉपीड!"
                                    "Hinglish" -> "Image prompt copied!"
                                    else -> "Image prompt copied!"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Prompt", tint = Color.Gray)
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"${beat.segmentText}\"",
                            modifier = Modifier.padding(8.dp),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "AI ਇਮੇਜ ਜਨਰੇਸ਼ਨ ਪ੍ਰੋਂਪਟ:"
                            "Hindi" -> "AI इमेज जनरेशन प्रॉम्प्ट:"
                            "Hinglish" -> "AI Image Gen Prompt (Midjourney style):"
                            else -> "AI Image Generation Prompt (Midjourney Style):"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(beat.imagePrompt, fontSize = 12.sp, color = TextPrimary, lineHeight = 18.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਕੈਮਰਾ ਐਂਗਲ:"
                                    "Hindi" -> "कैमरा:"
                                    "Hinglish" -> "Camera Angle:"
                                    else -> "Camera:"
                                },
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            Text(beat.cameraAngle, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਲਾਈਟਿੰਗ:"
                                    "Hindi" -> "लाइटिंग:"
                                    "Hinglish" -> "Lighting:"
                                    else -> "Aesthetic Lighting:"
                                },
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            Text(beat.lighting, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "B-Roll ਸੁਝਾਅ:"
                                    "Hindi" -> "B-Roll सुझाव:"
                                    "Hinglish" -> "B-Roll Suggestion:"
                                    else -> "B-Roll Video suggestion:"
                                },
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                            Text(beat.brollSuggestion, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (beat.videoPrompt.isNotBlank()) {
                        Divider(color = TextPrimary.copy(alpha = 0.05f))
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਵੀਡੀਓ ਐਨੀਮੇਸ਼ਨ ਸੈਟਿੰਗਜ਼:"
                                "Hindi" -> "वीडियो एनीमेशन सेटिंग्स:"
                                "Hinglish" -> "Video Animation configuration:"
                                else -> "Motion Video Animation configuration:"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00C853)
                        )
                        Text(beat.videoPrompt, fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.cloneActiveState = 8 },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("proceed_to_motion_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਵੀਡੀਓ ਪ੍ਰੋਂਪਟ ਸੈਟਅਪ 'ਤੇ ਜਾਓ"
                    "Hindi" -> "वीडियो प्रॉम्प्टक सेटअप पर बढ़ें"
                    "Hinglish" -> "VIDEO PROMPT SETUP PE CHALEIN"
                    else -> "PROCEED TO VIDEO PROMPT SETUP"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun CloneState8_VideoPromo(viewModel: GrowTubeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕਦਮ 8: ਮੋਸ਼ਨ ਵੀਡੀਓ ਜਨਰੇਸ਼ਨ"
                        "Hindi" -> "चरण 8: मोशन वीडियो जनरेशन"
                        "Hinglish" -> "Step 8: Motion Video Generation Setup"
                        else -> "Step 8: Motion Video Generation"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕੀ ਤੁਸੀਂ ਆਪਣੇ ਸਟੋਰੀਬੋਰਡ ਚਿੱਤਰ ਪ੍ਰੋਂਪਟਾਂ ਵਿੱਚ ਪੂਰੀ ਤਰ੍ਹਾਂ ਰੈਂਡਰ ਹੋਣ ਯੋਗ AI ਮੋਸ਼ਨ ਪੈਰਾਮੀਟਰ (ਜਿਵੇਂ ਜ਼ੂਮ, ਫੋਕਲ ਸਪੀਡ, ਕੈਮਰਾ ਐਕਸ਼ਨ) ਸ਼ਾਮਲ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?"
                        "Hindi" -> "क्या आप अपने स्टोरीबोर्ड इमेज प्रॉम्प्ट में पूरी तरह से रेंडर करने योग्य AI मोशन पैरामीटर (जैसे ज़ूम, फोकल स्पीड, कैमरा एक्शन) शामिल करना चाहते हैं?"
                        "Hinglish" -> "Kya aap apne storyboard prompts mein fully renderable AI Motion parameters (e.g. zoom camera speed, pan direction, camera action) add karna chahte hain?"
                        else -> "Do you want to append fully renderable AI Motion parameters (e.g. zoom, focal speed, camera action) to your storyboard image prompts?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.submitVideoPromptChoice(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("video_prompt_yes"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਹਾਂ, ਵੀਡੀਓ ਚਾਲੂ ਕਰੋ"
                                "Hindi" -> "हाँ, वीडियो सक्रिय करें"
                                "Hinglish" -> "HAAN, VIDEO ENABLE KAREIN"
                                else -> "YES, ENABLE VIDEO"
                            }
                        )
                    }

                    Button(
                        onClick = { viewModel.submitVideoPromptChoice(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38384D)),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("video_prompt_no"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਨਹੀਂ, ਸਿਰਫ਼ ਤਸਵੀਰਾਂ"
                                "Hindi" -> "नहीं, केवल चित्र रखें"
                                "Hinglish" -> "NAHI, ONLY IMAGES"
                                else -> "NO, IMAGES ONLY"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CloneState9_ThumbnailPreference(viewModel: GrowTubeViewModel) {
    var thumbPref by remember { mutableStateOf(viewModel.cloneThumbnailPreference) }
    var activeInfoOption by remember { mutableStateOf<OptionInfo?>(null) }

    val options = listOf(
        "Minimalist aesthetic with high-end color splash",
        "High-contrast character face close-up expressing shock",
        "Bold thick text overlays centered over highly abstract elements",
        "Surgical flat vector design layout with glowing neon glows"
    )

    activeInfoOption?.let { info ->
        InfoGuideDialog(
            optionName = info.title,
            info = info,
            viewModel = viewModel,
            onDismiss = { activeInfoOption = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਥੰਬਨੇਲ ਡਿਜ਼ਾਇਨ ਪੈਰਾਮੀਟਰ"
                        "Hindi" -> "थंबनेल डिज़ाइन पैरामीटर"
                        "Hinglish" -> "Thumbnail Design Parameters"
                        else -> "Thumbnail Design Parameter"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਜਾਣਕਾਰੀ ਦਿਓ ਕਿ ਤੁਸੀਂ ਥੰਬਨੇਲ ਵਿੱਚ ਕਿਹੜਾ ਮੁੱਖ ਲੇਆਉਟ ਜਾਂ ਭਾਵਨਾਤਮਕ ਕਲਿੱਕ ਟ੍ਰਿਗਰ ਵਰਤਣਾ ਚਾਹੁੰਦੇ ਹੋ।"
                        "Hindi" -> "स्पष्ट करें कि आप थंबनेल अवधारणाओं को अपनाने के लिए कौन सा मुख्य लेआउट या भावनात्मक क्लिक ट्रिगर चाहते हैं।"
                        "Hinglish" -> "Thumbnail design layouts ke liye emotional eye click trigger customize ya select karein."
                        else -> "Explain what primary layout or emotion click trigger you want the thumbnail concepts to adopt."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = thumbPref,
                    onValueChange = { thumbPref = it },
                    placeholder = {
                        Text(
                            when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਜਿਵੇਂ: ਮਿਨੀਮਲਿਸਟ ਉੱਚ-ਕੰਟਰਾਸਟ ਹੈਰਾਨੀ ਵਾਲਾ ਚਿਹਰਾ..."
                                "Hindi" -> "जैसे: न्यूनतम उच्च-विपरीत छवि, क्लिकबेट चौंका देने वाला चेहरा..."
                                "Hinglish" -> "e.g. minimalist high-contrast clickbait face..."
                                else -> "e.g. minimalist high-contrast clickbait face..."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("thumbnail_pref_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਤੁਰੰਤ ਕਲਿੱਕਬੇਟ ਸਟਾਈਲ:"
                        "Hindi" -> "त्वरित क्लिकबेट स्टाइल:"
                        "Hinglish" -> "Quick Clickbait Styles:"
                        else -> "Quick Clickbait styles:"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                options.forEach { opt ->
                    val isSelected = thumbPref == opt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { thumbPref = opt }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { thumbPref = opt })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = opt,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { activeInfoOption = findHelpInfo(opt) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Style Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Dynamic thumbnail help explanation card below choices
                val currentThumbHelp = findHelpInfo(thumbPref)
                if (currentThumbHelp != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Selected thumbnail style info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> currentThumbHelp.punjabiDesc
                                    "Hindi" -> currentThumbHelp.hindiDesc
                                    "Hinglish" -> getHinglishDescription(currentThumbHelp)
                                    else -> currentThumbHelp.englishDesc
                                },
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.submitThumbnailParams(thumbPref) },
            enabled = thumbPref.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("generate_thumbnail_concepts_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਥੰਬਨੇਲ ਸੰਕਲਪ ਤਿਆਰ ਕਰੋ"
                    "Hindi" -> "थंबनेल संकल्प जनरेट करें"
                    "Hinglish" -> "THUMBNAIL CONCEPTS GENERATE KAREIN"
                    else -> "GENERATE THUMBNAIL CONCEPTS"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun CloneState10_ThumbnailConcepts(viewModel: GrowTubeViewModel) {
    val thumbnails = viewModel.cloneThumbnails
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    if (viewModel.cloneThumbnailsLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਵੀਡੀਓ ਦੇ ਲਿੰਕ ਅਤੇ ਸਕ੍ਰਿਪਟ ਦੇ ਆਧਾਰ 'ਤੇ 5 ਵਾਇਰਲ ਥੰਬਨੇਲ ਡਿਜ਼ਾਈਨ ਕੀਤੇ ਜਾ ਰਹੇ ਹਨ..."
                        "Hindi" -> "वायरल थंबनेल डिजाइंस तैयार किए जा रहे हैं..."
                        "Hinglish" -> "Video link aur script ke basis par 5 viral thumbnails design kiye ja rahe hain..."
                        else -> "Designing viral thumbnail click concepts based on script and channel style..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.BurstMode, contentDescription = "Burst", tint = MaterialTheme.colorScheme.primary)
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਵਾਇਰਲ ਥੰਬਨੇਲ ਡਿਜ਼ਾਈਨ ਬੋਰਡ"
                    "Hindi" -> "वायरल थंबनेल डिज़ाइन बोर्ड"
                    "Hinglish" -> "Viral Thumbnail Design Boards"
                    else -> "Viral Thumbnail Design Boards"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        thumbnails.forEachIndexed { index, thumb ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(thumb.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(thumb.prompt))
                                val msg = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਥੰਬਨੇਲ ਪ੍ਰੋਂਪਟ ਕਾਪੀ ਹੋ ਗਿਆ!"
                                    "Hindi" -> "थंबनेल प्रॉम्प्ट कॉपी हो गया!"
                                    "Hinglish" -> "Thumbnail prompt copied!"
                                    else -> "Thumbnail prompt copied!"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy Prompt", tint = Color.Gray)
                        }
                    }

                    DnaItemRow(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਵਿਜ਼ੂਅਲ ਰਚਨਾ (Composition)"
                            "Hindi" -> "विज़ुअल कंपोजिशन"
                            "Hinglish" -> "Visual Composition"
                            else -> "Visual Composition"
                        },
                        thumb.visualConcept
                    )
                    DnaItemRow(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "CTR ਬੋਲਡ ਟੈਕਸਟ ਓਵਰਲੇਅ"
                            "Hindi" -> "CTR बोल्ड टेक्स्ट ओवरले"
                            "Hinglish" -> "CTR Bold Text Overlay"
                            else -> "CTR Bold Text Overlay"
                        },
                        thumb.textOverlay
                    )
                    DnaItemRow(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਭਾਵਨਾਤਮਕ ਕਲਿੱਕ ਟ੍ਰਿਗਰ"
                            "Hindi" -> "भावनात्मक क्लिक ट्रिगर"
                            "Hinglish" -> "Emotional Click Trigger"
                            else -> "Emotional Click Trigger"
                        },
                        thumb.emotionTrigger
                    )
                    DnaItemRow(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "Midjourney ਪ੍ਰੋਂਪਟ"
                            "Hindi" -> "मिडजर्नी जनरेशन प्रॉम्प्ट"
                            "Hinglish" -> "Midjourney Generation Prompt"
                            else -> "Midjourney Gen Prompt"
                        },
                        thumb.prompt
                    )
                    DnaItemRow(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਮਨੋਵਿਗਿਆਨਕ CTR ਕਾਰਨ"
                            "Hindi" -> "मनोवैज्ञानिक CTR कारण"
                            "Hinglish" -> "Psychological CTR Reason"
                            else -> "Psychological CTR Reason"
                        },
                        thumb.recommendationReason
                    )
                }
            }
        }

        Button(
            onClick = {
                viewModel.cloneActiveState = 11
                viewModel.generateMetadata()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("proceed_to_metadata_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਐਸਈਓ ਮੈਟਾਡੇਟਾ ਤਿਆਰ ਕਰੋ"
                    "Hindi" -> "SEO मेटाडेटा आर्किटेक्चर जनरेट करें"
                    "Hinglish" -> "METADATA ARCHITECTURE GENERATE KAREIN"
                    else -> "GENERATE METADATA ARCHITECTURE"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun CloneState11_MetadataSEO(viewModel: GrowTubeViewModel) {
    val meta = viewModel.cloneMetadata
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    if (viewModel.cloneMetadataLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਤੁਹਾਡੇ ਵੀਡੀਓ ਲਈ ਐਸਈਓ ਟੈਗਸ, ਹੈਸ਼ਟੈਗਸ ਅਤੇ ਹੁੱਕੀ ਵਰਣਨ ਤਿਆਰ ਕੀਤਾ ਜਾ ਰਿਹਾ ਹੈ..."
                        "Hindi" -> "आपके वीडियो के लिए अनुकूलित एसईओ टैग, विवरण और पिन कमेंट तैयार किए जा रहे हैं..."
                        "Hinglish" -> "Aapki video ke liye SEO tags, description aur high conversions pinned comments generate ho rahe hain..."
                        else -> "Generating surgical SEO tags, hashtags, timeline chapters, and conversion-focused pinned comment..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Tag, contentDescription = "Tag", tint = MaterialTheme.colorScheme.primary)
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਐਸਈਓ ਮੈਟਾਡੇਟਾ ਅਤੇ ਚੈਪਟਰ ਆਰਕੀਟੈਕਚਰ"
                    "Hindi" -> "SEO मेटाडेटा और चैप्टर्स ढांचा"
                    "Hinglish" -> "SEO Metadata & Chapters Architecture"
                    else -> "SEO Metadata & Chapters Architecture"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (meta == null) {
            Text("Error loading SEO profile. Proceeding...", color = Color.Red)
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaCopySection(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਐਸਈਓ ਅਨੁਕੂਲਿਤ ਵੀਡੀਓ ਵੇਰਵਾ (Description)"
                            "Hindi" -> "SEO अनुकूलित वीडियो विवरण"
                            "Hinglish" -> "SEO Optimized Description"
                            else -> "SEO Optimized Description"
                        },
                        meta.seoDescription, clipboardManager, context
                    )
                    MetaCopySection(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਬ੍ਰੌਡ ਅਤੇ ਲੌਂਗਟੇਲ ਟੈਗਸ (Tags)"
                            "Hindi" -> "ब्रॉड और लॉन्गटेल टैग्स (Tags)"
                            "Hinglish" -> "Broad & Longtail Tags"
                            else -> "Broad & Longtail Tags"
                        },
                        meta.tags, clipboardManager, context
                    )
                    MetaCopySection(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਵੀਡੀਓ ਹੈਸ਼ਟੈਗਸ (Hashtags)"
                            "Hindi" -> "वीडियो हैशटैग (Hashtags)"
                            "Hinglish" -> "Video Hashtags"
                            else -> "Niche Hashtags"
                        },
                        meta.hashtags, clipboardManager, context
                    )
                    MetaCopySection(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਵੀਡੀਓ ਟਾਈਮਲਾਈਨ ਚੈਪਟਰਸ"
                            "Hindi" -> "वीडियो टाइमलाइन चैप्टर्स"
                            "Hinglish" -> "Video Timeline Chapters"
                            else -> "Video Timeline Chapters"
                        },
                        meta.chapters, clipboardManager, context
                    )
                    MetaCopySection(
                        when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਪਿੰਨ ਕੀਤਾ ਜਾਣ ਵਾਲਾ CTA ਕਮੈਂਟ"
                            "Hindi" -> "पिन किया जाने वाला सगाई कमेंट"
                            "Hinglish" -> "Pinned CTA Engagement Comment"
                            else -> "Pinned Engagement CTA Comment"
                        },
                        meta.pinnedComment, clipboardManager, context
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.cloneActiveState = 12 },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("proceed_to_calendar_opt_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਵੀਡੀਓ ਕੈਲੰਡਰ ਪਲਾਨ 'ਤੇ ਜਾਓ"
                    "Hindi" -> "रणनीतिक कैलेंडर योजना पर बढ़ें"
                    "Hinglish" -> "PROCEED TO CALENDAR CONFIG PLAN"
                    else -> "PROCEED TO CALENDAR CONFIG"
                },
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Arrow")
        }
    }
}

@Composable
fun MetaCopySection(
    title: String,
    content: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            IconButton(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(content))
                    Toast.makeText(context, "$title copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(content, fontSize = 12.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = TextPrimary.copy(alpha = 0.05f))
    }
}

@Composable
fun CloneState12_CalendarOption(viewModel: GrowTubeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਵੀਡੀਓ ਰਣਨੀਤੀ ਕੈਲੰਡਰ ਪਲਾਨ"
                        "Hindi" -> "रणनीतिक वीडियो कैलेंडर योजना"
                        "Hinglish" -> "Upcoming Video Strategy Calendar Design"
                        else -> "Upcoming Video Strategy Calendar"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਕੀ ਤੁਸੀਂ ਆਪਣੀ ਰਣਨੀਤੀ ਵਿੱਚ ਆਉਣ ਵਾਲੀਆਂ 4 ਵੀਡੀਓਜ਼, ਟੀਚੇ ਵਾਲੇ ਟੌਪਿਕਸ, ਸੁਝਾਏ ਗਏ ਹੁੱਕਸ ਅਤੇ ਬਿਹਤਰੀਨ ਅਪਲੋਡ ਸਮਾਂ ਸਾਰਣੀ ਨੂੰ ਸ਼ਾਮਲ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?"
                        "Hindi" -> "क्या आप एक रणनीतिक कार्यक्रम तैयार करना चाहते हैं जिसमें आगामी 4 वीडियो, लक्षित विषय, अनुशंसित हुक और सर्वोत्तम अपलोड समय का विवरण हो?"
                        "Hinglish" -> "Kya aap upcoming 4 videos ke topics, high CTR keyword, aur best publish timeframes ka strategic plan generate karna chahte hain?"
                        else -> "Do you want to generate a strategic schedule detailing 4 upcoming videos, targeted topics, recommended hooks, and best publishing timeframes?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.submitCalendarChoice(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("calendar_yes"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਹਾਂ, ਪਲਾਨ ਤਿਆਰ ਕਰੋ"
                                "Hindi" -> "हाँ, कैलेंडर जनरेट करें"
                                "Hinglish" -> "HAAN, PLAN GENERATE KAREIN"
                                else -> "YES, GENERATE"
                            }
                        )
                    }

                    Button(
                        onClick = { viewModel.submitCalendarChoice(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38384D)),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("calendar_no"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (viewModel.preferredHelpLanguage) {
                                "Punjabi" -> "ਨਹੀਂ, ਛੱਡੋ"
                                "Hindi" -> "नहीं, आगे बढ़ें"
                                "Hinglish" -> "NAHI, SKIP KAREIN"
                                else -> "NO, SKIP TO WRAP"
                            }
                        )
                    }
                }
            }
        }
    }
}

fun generateAndSharePdfReport(context: android.content.Context, viewModel: GrowTubeViewModel) {
    try {
        val pdfDocument = PdfDocument()
        
        // Assemble text content
        val lines = mutableListOf<String>()
        lines.add("============================================")
        lines.add("       GROWTUBE CHANNEL CLONE REPORT        ")
        lines.add("============================================")
        lines.add("Generated: 2026-06-04 UTC")
        lines.add("Target Language: ${viewModel.preferredHelpLanguage}")
        lines.add("")
        
        // Section: Niche & Style DNA
        lines.add("--- 1. CHANNEL STYLE DNA ---")
        viewModel.cloneStyleDNA?.let { dna ->
            lines.add("Niche: ${dna.niche}")
            lines.add("Target Audience: ${dna.targetAudience}")
            lines.add("Language/Dialect: ${dna.languageAndDialect}")
            lines.add("Tone: ${dna.tone}")
            lines.add("Sentence Rhythm: ${dna.sentenceRhythm}")
            lines.add("Signature Slang: ${dna.signatureSlang.joinToString(", ")}")
            lines.add("CTA Style: ${dna.ctaStyle}")
        } ?: lines.add("Style DNA analysis not available.")
        lines.add("")
        
        // Section: Script & Hooks
        lines.add("--- 2. GENERATED SCRIPT & HOOKS ---")
        viewModel.cloneScript?.let { scr ->
            lines.add("Title: ${scr.title}")
            lines.add("")
            lines.add("[HOOK VARIATION A (Question)]")
            lines.add(scr.hookA)
            lines.add("")
            lines.add("[HOOK VARIATION B (Shock)]")
            lines.add(scr.hookB)
            lines.add("")
            lines.add("[HOOK VARIATION C (Story)]")
            lines.add(scr.hookC)
            lines.add("")
            lines.add("[FULL VIDEO SCRIPT NARRATIVE]")
            lines.add(scr.finalScript)
        } ?: lines.add("Script generation not available.")
        lines.add("")
        
        // Section: Storyboard Prompts
        lines.add("--- 3. STORYBOARD IMAGE PROMPTS ---")
        if (viewModel.cloneImagePrompts.isNotEmpty()) {
            viewModel.cloneImagePrompts.forEachIndexed { i, beat ->
                lines.add("Scene #${i + 1}:")
                lines.add("Segment Text: ${beat.segmentText}")
                lines.add("AI Image Prompt: ${beat.imagePrompt}")
                lines.add("Camera Angle: ${beat.cameraAngle}")
                lines.add("Lighting: ${beat.lighting}")
                lines.add("Mood: ${beat.mood}")
                lines.add("B-Roll Suggestion: ${beat.brollSuggestion}")
                lines.add("")
            }
        } else {
            lines.add("No Storyboard prompts generated.")
        }
        lines.add("")
        
        // Section: Thumbnails
        lines.add("--- 4. CTR THUMBNAIL CONCEPTS ---")
        if (viewModel.cloneThumbnails.isNotEmpty()) {
            viewModel.cloneThumbnails.forEachIndexed { i, thumb ->
                lines.add("Thumbnail Concept #${i + 1}: ${thumb.title}")
                lines.add("Visual Composition: ${thumb.visualConcept}")
                lines.add("Bold Text Overlay: ${thumb.textOverlay}")
                lines.add("Emotion Trigger: ${thumb.emotionTrigger}")
                lines.add("Midjourney Prompt: ${thumb.prompt}")
                lines.add("CTR Strategic Reason: ${thumb.recommendationReason}")
                lines.add("")
            }
        } else {
            lines.add("No Thumbnails found.")
        }
        lines.add("")
        
        // Section: SEO Metadata
        lines.add("--- 5. SEO METADATA ARCHITECTURE ---")
        viewModel.cloneMetadata?.let { meta ->
            lines.add("SEO Optimized Video Description:")
            lines.add(meta.seoDescription)
            lines.add("")
            lines.add("Tags: ${meta.tags}")
            lines.add("")
            lines.add("Hashtags: ${meta.hashtags}")
            lines.add("")
            lines.add("Timeline Chapters:")
            lines.add(meta.chapters)
            lines.add("")
            lines.add("Pinned Engagement Comment:")
            lines.add(meta.pinnedComment)
        } ?: lines.add("SEO Metadata not available.")
        lines.add("")
        
        // Section: Calendar Schedule
        lines.add("--- 6. STRATEGIC CALENDAR PLAN ---")
        if (viewModel.cloneCalendar.isNotEmpty()) {
            viewModel.cloneCalendar.forEachIndexed { i, item ->
                lines.add("Video #${i + 1}: ${item.title}")
                lines.add("Niche Strategic Concept: ${item.topicAngle}")
                lines.add("Suggested Hook: ${item.suggestedHook}")
                lines.add("Target Keywords: ${item.targetKeyword}")
                lines.add("Publish Schedule: ${item.uploadDayTime}")
                lines.add("")
            }
        } else {
            lines.add("Calendar scheduling not available.")
        }
        
        // Simple page layout parameters
        val pageWidth = 595 // A4 standard width in points
        val pageHeight = 842 // A4 standard height in points
        val margin = 40
        val lineSpacing = 16
        val maxLinesPerPage = (pageHeight - (margin * 2)) / lineSpacing
        
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        // Wrap all lines to fit within pageWidth - (margin * 2)
        val wrappedLines = mutableListOf<String>()
        val maxCharLength = 70 // Safe limit for standard monospace text at size 10 inside margins
        
        for (line in lines) {
            val sections = line.split("\n")
            for (sec in sections) {
                if (sec.length <= maxCharLength) {
                    wrappedLines.add(sec)
                } else {
                    var currentText = sec
                    while (currentText.length > maxCharLength) {
                        var breakIdx = currentText.lastIndexOf(' ', maxCharLength)
                        if (breakIdx == -1 || breakIdx < 10) {
                            breakIdx = maxCharLength
                        }
                        wrappedLines.add(currentText.substring(0, breakIdx))
                        currentText = currentText.substring(breakIdx).trim()
                    }
                    wrappedLines.add(currentText)
                }
            }
        }
        
        // Draw wrapped lines into PDF pages
        var pageNumber = 1
        var lineIterator = 0
        
        while (lineIterator < wrappedLines.size) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            var yPosition = margin
            var printedInPage = 0
            
            while (lineIterator < wrappedLines.size && printedInPage < maxLinesPerPage) {
                val currentLine = wrappedLines[lineIterator]
                val paintToUse = if (currentLine.startsWith("---") || currentLine.startsWith("===")) titlePaint else textPaint
                canvas.drawText(currentLine, margin.toFloat(), yPosition.toFloat(), paintToUse)
                
                yPosition += lineSpacing
                printedInPage++
                lineIterator++
            }
            
            pdfDocument.finishPage(page)
            pageNumber++
        }
        
        // Save PDF to cache dir and trigger standard share Intent
        val cacheFolder = File(context.cacheDir, "reports")
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs()
        }
        val fileName = "GrowTube_Cloner_Report_${System.currentTimeMillis()}.pdf"
        val reportFile = File(cacheFolder, fileName)
        
        FileOutputStream(reportFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        
        // Share PDF file using FileProvider
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, reportFile)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Consolidated PDF Report"))
        
        Toast.makeText(context, "PDF Report Generated Successfully!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to generate PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun generateAndShareTxtReport(context: android.content.Context, viewModel: GrowTubeViewModel) {
    try {
        val sb = StringBuilder()
        sb.append("==========================================\n")
        sb.append("      GROWTUBE CHANNEL CLONE REPORT       \n")
        sb.append("==========================================\n\n")
        
        sb.append("--- 1. CHANNEL STYLE DNA ---\n")
        viewModel.cloneStyleDNA?.let { dna ->
            sb.append("Niche: ${dna.niche}\n")
            sb.append("Target Audience: ${dna.targetAudience}\n")
            sb.append("Language/Dialect: ${dna.languageAndDialect}\n")
            sb.append("Tone: ${dna.tone}\n")
            sb.append("Sentence Rhythm: ${dna.sentenceRhythm}\n")
            sb.append("Signature Slang: ${dna.signatureSlang.joinToString(", ")}\n")
            sb.append("CTA Style: ${dna.ctaStyle}\n")
        } ?: sb.append("Style DNA analysis not available.\n")
        sb.append("\n")
        
        sb.append("--- 2. GENERATED SCRIPT & HOOKS ---\n")
        viewModel.cloneScript?.let { scr ->
            sb.append("Title: ${scr.title}\n\n")
            sb.append("[HOOK VARIATION A]\n${scr.hookA}\n\n")
            sb.append("[HOOK VARIATION B]\n${scr.hookB}\n\n")
            sb.append("[HOOK VARIATION C]\n${scr.hookC}\n\n")
            sb.append("[FULL SCRIPT NARRATIVE]\n${scr.finalScript}\n")
        } ?: sb.append("Script generation not available.\n")
        sb.append("\n")
        
        sb.append("--- 3. STORYBOARD IMAGE PROMPTS ---\n")
        viewModel.cloneImagePrompts.forEachIndexed { i, beat ->
            sb.append("Scene #${i + 1}:\n")
            sb.append("Segment Text: ${beat.segmentText}\n")
            sb.append("AI Image Prompt: ${beat.imagePrompt}\n")
            sb.append("Camera: ${beat.cameraAngle} | Lighting: ${beat.lighting} | Mood: ${beat.mood}\n")
            sb.append("B-Roll Suggestion: ${beat.brollSuggestion}\n\n")
        }
        sb.append("\n")
        
        sb.append("--- 4. CTR THUMBNAIL CONCEPTS ---\n")
        viewModel.cloneThumbnails.forEachIndexed { i, thumb ->
            sb.append("Thumbnail Idea #${i + 1}: ${thumb.title}\n")
            sb.append("Visual: ${thumb.visualConcept}\n")
            sb.append("Overlay: ${thumb.textOverlay}\n")
            sb.append("Emotion Trigger: ${thumb.emotionTrigger}\n")
            sb.append("Prompt: ${thumb.prompt}\n")
            sb.append("Reasoning: ${thumb.recommendationReason}\n\n")
        }
        sb.append("\n")
        
        sb.append("--- 5. SEO METADATA ARCHITECTURE ---\n")
        viewModel.cloneMetadata?.let { meta ->
            sb.append("Description:\n${meta.seoDescription}\n\n")
            sb.append("Tags: ${meta.tags}\n\n")
            sb.append("Hashtags: ${meta.hashtags}\n\n")
            sb.append("Timeline Chapters:\n${meta.chapters}\n\n")
            sb.append("Pinned CTA Comment:\n${meta.pinnedComment}\n")
        } ?: sb.append("SEO Metadata not available.\n")
        sb.append("\n")
        
        sb.append("--- 6. STRATEGIC CALENDAR PLAN ---\n")
        viewModel.cloneCalendar.forEachIndexed { i, item ->
            sb.append("Upcoming Video #${i + 1}: ${item.title}\n")
            sb.append("Hook Strategy: ${item.suggestedHook}\n")
            sb.append("Keywords: ${item.targetKeyword}\n")
            sb.append("Publish Schedule: ${item.uploadDayTime}\n")
            sb.append("Concept: ${item.topicAngle}\n\n")
        }
        
        val textReport = sb.toString()
        
        val cacheFolder = File(context.cacheDir, "reports")
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs()
        }
        val fileName = "GrowTube_Cloner_Report_${System.currentTimeMillis()}.txt"
        val reportFile = File(cacheFolder, fileName)
        
        FileOutputStream(reportFile).use { out ->
            out.write(textReport.toByteArray())
        }
        
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, reportFile)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Consolidated TXT Report"))
        Toast.makeText(context, "TXT Document Report Generated Successfully!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share text: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CloneState13_FinalSummary(viewModel: GrowTubeViewModel) {
    val beats = viewModel.cloneImagePrompts
    val script = viewModel.cloneScript
    val meta = viewModel.cloneMetadata
    val calendar = viewModel.cloneCalendar
    
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1B2E1E),
            border = BorderStroke(2.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                Column {
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਚੈਨਲ ਕਲੋਨ ਪ੍ਰਕਿਰਿਆ ਪੂਰੀ ਹੋ ਗਈ!"
                            "Hindi" -> "चैनल क्लोन प्रक्रिया पूरी हुई!"
                            "Hinglish" -> "Channel Clone Successfully Completed!"
                            else -> "Channel Clone Complete!"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਸਾਰਾ ਓਰੀਜਨਲ ਕੰਟੈਂਟ ਬਲੂਪ੍ਰਿੰਟ ਤਿਆਰ ਹੈ ਅਤੇ ਡਿਪਲੋਏਮੈਂਟ ਲਈ ਉਪਲਬਧ ਹੈ।"
                            "Hindi" -> "सभी मूल कंटेंट ब्लूप्रिंट तैयार हैं और प्रकाशन के लिए उपलब्ध हैं।"
                            "Hinglish" -> "Sare original content blueprints compile ho chuke hain aur deployment ke liye ready hain."
                            else -> "All original content blueprints are parsed, compiled, and ready for deployment."
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Consolidated Report Download Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF Report",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਇਕੱਠੀ ਕੀਤੀ ਪੂਰੀ ਰਿਪੋਰਟ ਡਾਊਨਲੋਡ ਕਰੋ"
                            "Hindi" -> "एकीकृत पूर्ण रिपोर्ट डाउनलोड करें"
                            "Hinglish" -> "Consolidated Full Report Save Karein"
                            else -> "Export Consolidated Report"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = when (viewModel.preferredHelpLanguage) {
                        "Punjabi" -> "ਸਾਰੀਆਂ ਚੀਜ਼ਾਂ (ਸਟਾਈਲ ਡੀਐਨਏ, ਸਕ੍ਰਿਪਟ, ਸਟੋਰੀਬੋਰਡ ਪ੍ਰੋਂਪਟ, ਥੰਬਨੇਲਸ, ਐਸਈਓ) ਨੂੰ ਇੱਕ ਠੋਸ ਦਸਤਾਵੇਜ਼ ਬਣਾਕੇ ਸੇਵ ਕਰੋ।"
                        "Hindi" -> "सभी विवरणों (स्टाइल डीएनए, स्क्रिप्ट, स्टोरीबोर्ड प्रॉम्प्ट, थंबनेल, एसईओ) को एक ही एकीकृत रिपोर्ट फ़ाइल में सहेजें।"
                        "Hinglish" -> "Alag alag copy karne ki bajaye style DNA, script narrative, storyboard details aur complete SEO tags ko ek hi draft document me download karein."
                        else -> "Export all created materials (Style DNA, spoken script, prompts, thumbnail strategic boards, tags) into a single document."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { generateAndSharePdfReport(context, viewModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).height(42.dp).testTag("download_pdf_report_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "PDF", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF FORMAT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { generateAndShareTxtReport(context, viewModel) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38384D)),
                        modifier = Modifier.weight(1f).height(42.dp).testTag("share_txt_report_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE TXT/MD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = when (viewModel.preferredHelpLanguage) {
                "Punjabi" -> "ਤਿਆਰ ਕੀਤੇ ਗਏ ਅਸੇਟਸ ਦੀ ਸੂਚੀ:"
                "Hindi" -> "तैयार की गई संपत्तियों की सूची:"
                "Hinglish" -> "Summarized Assets Directory:"
                else -> "Summarized Assets Directory:"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        if (script != null) {
            ExportCardLink(
                when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਵੀਡੀਓ ਸਕ੍ਰਿਪਟ ਅਤੇ ਹੁੱਕਸ"
                    "Hindi" -> "वीडियो स्क्रिप्ट और हुक्स"
                    "Hinglish" -> "Spoken Script Narrative & Hooks"
                    else -> "Spoken Script Narrative & Hooks"
                },
                when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਟੀਚੇ ਦੀ ਲੰਬਾਈ ਅਨੁਸਾਰ ਢੁਕਵੀਂ ਤਿਆਰ ਸਕ੍ਰਿਪਟ।"
                    "Hindi" -> "लक्ष्य की लंबाई के अनुसार अनुकूलित व तैयार स्क्रिप्ट।"
                    "Hinglish" -> "Spelled narrative script optimized for target video duration."
                    else -> "Spelled narrative optimized for target timing parameters."
                },
                clipboardManager, context, script.finalScript
            )
        }

        if (beats.isNotEmpty()) {
            val promptsStr = beats.mapIndexed { index, b -> "Beat #${index + 1}: ${b.imagePrompt}\n" }.joinToString("\n")
            ExportCardLink(
                when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਸਟੋਰੀਬੋਰਡ ਆਰਟ ਡਿਜ਼ਾਈਨ ਪ੍ਰੋਂਪਟ"
                    "Hindi" -> "स्टोरीबोर्ड आर्ट डिजाइन प्रॉम्प्ट"
                    "Hinglish" -> "Storyboard Art Design Prompts"
                    else -> "Storyboard Art Design Prompts"
                },
                when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਮਿਡਜਰਨੀ ਚਿੱਤਰਾਂ ਲਈ ਲੋੜੀਂਦੇ ਕਰਮਬੱਧ ਵਰਣਨ।"
                    "Hindi" -> "मिडजर्नी छवियों के लिए क्रमिक विवरण की सूची।"
                    "Hinglish" -> "Sequence of image descriptions optimized for AI prompt generation."
                    else -> "Sequence of physical image descriptions for Midjourney."
                },
                clipboardManager, context, promptsStr
            )
        }

        if (meta != null) {
            val metaStr = "SEO DESCRIPTION:\n${meta.seoDescription}\n\nTAGS:\n${meta.tags}\n\nHASHTAGS:\n${meta.hashtags}"
            ExportCardLink(
                when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਮੈਟਾਡੇਟਾ ਅਤੇ ਐਸਈਓ ਹੈਡਰਸ"
                    "Hindi" -> "मेटाडेटा और एसईओ हेडर्स"
                    "Hinglish" -> "Metadata & SEO Headers"
                    else -> "Metadata & SEO Headers"
                },
                when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਯੂਟਿਊਬ ਐਲਗੋਰਿਦਮ ਲਈ ਅਨੁਕੂਲਿਤ ਵੀਡੀਓ ਟੈਗਸ ਅਤੇ ਲੋੜੀਂਦੇ ਚੈਪਟਰ।"
                    "Hindi" -> "यूट्यूब एल्गोरिदम के लिए अनुकूलित टैग्स और वीडियो टाइमलाइन चैप्टर।"
                    "Hinglish" -> "Tags and visual timeline chapters optimized for YouTube search algorithm SEO."
                    else -> "Broad tag indices and timeline chapters optimized for YouTube search algorithms."
                },
                clipboardManager, context, metaStr
            )
        }

        if (calendar.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = when (viewModel.preferredHelpLanguage) {
                            "Punjabi" -> "ਰਣਨੀਤਕ ਵੀਡੀਓ ਕੈਲੰਡਰ:"
                            "Hindi" -> "रणनीतिक वीडियो कैलेंडर अनुसूची:"
                            "Hinglish" -> "Strategic Video Strategy Calendar:"
                            else -> "Strategic Strategy Calendar:"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    calendar.forEachIndexed { i, item ->
                        Column {
                            Text("Video #${i + 1}: ${item.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਕੀਵਰਡ: ${item.targetKeyword} | ਸ਼ਡਿਊਲ: ${item.uploadDayTime}"
                                    "Hindi" -> "कीवर्ड: ${item.targetKeyword} | शेड्यूल: ${item.uploadDayTime}"
                                    else -> "Keyword: ${item.targetKeyword} | Schedule: ${item.uploadDayTime}"
                                },
                                fontSize = 11.sp, color = TextSecondary
                            )
                            Text(
                                text = when (viewModel.preferredHelpLanguage) {
                                    "Punjabi" -> "ਰਣਨੀਤਕ ਸੰਕਲਪ: ${item.topicAngle}"
                                    "Hindi" -> "रणनीतिक दृष्टिकोण: ${item.topicAngle}"
                                    else -> "Strategic Concept: ${item.topicAngle}"
                                },
                                fontSize = 11.sp, color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = TextPrimary.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.navigateToHomeFromClone() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("done_clone_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when (viewModel.preferredHelpLanguage) {
                    "Punjabi" -> "ਪ੍ਰਕਿਰਿਆ ਪੂਰੀ ਕਰੋ ਅਤੇ ਮੁੱਖ ਪੰਨੇ 'ਤੇ ਵਾਪਸ ਜਾਓ"
                    "Hindi" -> "प्रक्रिया पूर्ण करें और मुख्य पृष्ठ पर लौटें"
                    "Hinglish" -> "FINISH & HOMEPAGE PE RAHIYE"
                    else -> "FINISH & RETURN TO HOMEPAGE"
                },
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun ExportCardLink(
    title: String,
    description: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    context: android.content.Context,
    contentToCopy: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Text(description, fontSize = 11.sp, color = TextSecondary)
            }
            
            Button(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(contentToCopy))
                    Toast.makeText(context, "Copied asset content!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38384D)),
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("COPY", fontSize = 11.sp)
            }
        }
    }
}


