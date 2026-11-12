package com.aurviz.aiagent.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurviz.aiagent.AurvizApp
import com.aurviz.aiagent.R
import com.aurviz.aiagent.domain.models.AgentExecutionState
import com.aurviz.aiagent.ui.components.ActionConfirmationCard
import com.aurviz.aiagent.ui.components.AudioWaveformVisualizer
import com.aurviz.aiagent.ui.components.ChatMessageItem
import com.aurviz.aiagent.ui.components.ExecutionPlanCard
import com.aurviz.aiagent.ui.components.SkeletonLoaderCard
import com.aurviz.aiagent.ui.components.WebAutomationSheet
import com.aurviz.aiagent.ui.theme.AurvizGold
import com.aurviz.aiagent.ui.theme.AurvizSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    modifier: Modifier = Modifier,
    onNavigateHome: () -> Unit = {},
    viewModel: AgentChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as AurvizApp
    val isDarkMode by app.isDarkMode.collectAsStateWithLifecycle()

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val activePlan by viewModel.activePlan.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()

    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    var showHistoryMenu by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val samplePrompts = listOf(
        "Compose an email to team regarding project milestone review",
        "Every morning at 9 AM, check important emails and prepare summary",
        "Open college-study.netlify.app website",
        "Look up latest AI technology news and prepare summary"
    )

    // Pulsing animation for online agent dot
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val outlineColor = MaterialTheme.colorScheme.outline

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onNavigateHome)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.aurviz_mark_transparent),
                            contentDescription = "Aurviz Logo - Home",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                            Text(
                                text = "Aurviz Agent",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    lineHeight = 16.sp,
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (executionState) {
                                                is AgentExecutionState.Listening -> Color(0xFFEF4444)
                                                is AgentExecutionState.Thinking -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                                is AgentExecutionState.ExecutingPlan -> MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                                                is AgentExecutionState.AwaitingConfirmation -> Color(0xFFFF5722)
                                                else -> AurvizSuccess.copy(alpha = pulseAlpha)
                                            }
                                        )
                                )
                                Text(
                                    text = when (executionState) {
                                        is AgentExecutionState.Listening -> "Listening..."
                                        is AgentExecutionState.Thinking -> "Thinking..."
                                        is AgentExecutionState.ExecutingPlan -> "Executing plan..."
                                        is AgentExecutionState.AwaitingConfirmation -> "Confirmation required"
                                        else -> "Personal AI Agent • Ready"
                                    },
                                    fontSize = 11.sp,
                                    style = androidx.compose.ui.text.TextStyle(
                                        lineHeight = 11.sp,
                                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Light / Dark Mode Toggle
                    IconButton(onClick = { app.toggleDarkMode() }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Light/Dark Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 3-Dot Overflow Menu for New Chat & Chat History
                    Box {
                        IconButton(onClick = { showHistoryMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu and History",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showHistoryMenu,
                            onDismissRequest = { showHistoryMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Chat") },
                                onClick = {
                                    showHistoryMenu = false
                                    viewModel.startNewChat()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chat History (${chatSessions.size})") },
                                onClick = {
                                    showHistoryMenu = false
                                    showHistoryDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null, tint = AurvizSuccess)
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Chat Stream (Maximizes height: "screen lambi lagegi")
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onCopy = { viewModel.copyToClipboard(context, it) },
                        onEditResend = { viewModel.setInputForEdit(it) }
                    )
                }

                // Active Execution Plan Checklist (Sleek and auto-collapsing)
                if (activePlan != null) {
                    item {
                        val currentStep = when (val state = executionState) {
                            is AgentExecutionState.ExecutingPlan -> state.currentStepIndex
                            else -> 0
                        }
                        ExecutionPlanCard(
                            plan = activePlan!!,
                            currentStepIndex = currentStep
                        )
                    }
                }

                // Thinking Indicator (Rich Skeleton Loader Shimmer)
                if (executionState is AgentExecutionState.Thinking) {
                    item {
                        val state = executionState as AgentExecutionState.Thinking
                        SkeletonLoaderCard(statusMessage = state.statusMessage)
                    }
                }

                // Explicit Confirmation Gate
                if (executionState is AgentExecutionState.AwaitingConfirmation) {
                    item {
                        val state = executionState as AgentExecutionState.AwaitingConfirmation
                        ActionConfirmationCard(
                            action = state.action,
                            onConfirm = state.onConfirm,
                            onCancel = state.onCancel
                        )
                    }
                }
            }

            // Controlled Web Automation Preview Sheet
            WebAutomationSheet(
                state = browserState,
                onDismiss = { viewModel.dismissBrowserSheet() }
            )

            // Compact Sample Prompt Chips (Shown only when chat is fresh to keep screen tall)
            if (messages.size <= 3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    samplePrompts.forEach { prompt ->
                        AssistChip(
                            onClick = { viewModel.submitCommand(prompt) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AurvizGold,
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = prompt.take(32) + "...",
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            // Real-Time Audio Waveform Bar (Visible when recording voice)
            AnimatedVisibility(
                visible = isListening,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.labelSmall,
                            color = AurvizGold,
                            fontWeight = FontWeight.Bold
                        )
                        AudioWaveformVisualizer(
                            rmsLevel = rmsLevel,
                            isListening = isListening
                        )
                    }
                }
            }

            // Slim, sleek, integrated bottom input pill (Height ~44dp, "mota nahi, normal aur lamba dikhe")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Integrated Mic Button
                    IconButton(
                        onClick = { viewModel.toggleVoiceListening() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Command",
                            tint = if (isListening) Color(0xFFEF4444) else AurvizGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Integrated Slim Text Field
                    BasicTextField(
                        value = inputText,
                        onValueChange = { viewModel.onInputTextChanged(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        decorationBox = { innerTextField ->
                            if (inputText.isBlank()) {
                                Text(
                                    "Speak or type command...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    // Integrated Send Button
                    IconButton(
                        onClick = { viewModel.submitCommand() },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Chat History Dialog
        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = AurvizSuccess)
                        Text("Chat History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    if (chatSessions.isEmpty()) {
                        Text(
                            "No past chat sessions saved yet. Start a new chat to archive conversations.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        ) {
                            items(chatSessions) { session ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.restoreSession(session)
                                            showHistoryDialog = false
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = session.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${session.messages.size} messages • ${java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.timestamp))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistoryDialog = false }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.startNewChat()
                        showHistoryDialog = false
                    }) {
                        Text("Start New Chat", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
