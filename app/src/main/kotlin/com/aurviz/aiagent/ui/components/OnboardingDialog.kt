package com.aurviz.aiagent.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aurviz.aiagent.R
import com.aurviz.aiagent.ui.theme.AurvizBlueViolet
import com.aurviz.aiagent.ui.theme.AurvizSuccess

@Composable
fun FullScreenOnboarding(
    initialName: String = "User",
    initialEmail: String = "user.aurviz@example.com",
    initialPhone: String = "",
    onComplete: (name: String, email: String, phone: String) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf(initialName) }
    var userEmail by remember { mutableStateOf(initialEmail) }
    var userPhone by remember { mutableStateOf(initialPhone) }

    val totalSteps = 4

    // Infinite breathing glow animation for logo
    val infiniteTransition = rememberInfiniteTransition(label = "LogoAnimation")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    // Permission launcher for Slide 2
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsToRequest = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.any { it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF070B14),
                        Color(0xFF0F172A),
                        Color(0xFF0B0F19)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalSteps) {
                    val isActive = i == currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) AurvizBlueViolet
                                else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            // Slide Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "SlideTransition"
                ) { step ->
                    when (step) {
                        0 -> SlideWelcome(logoScale = logoScale)
                        1 -> SlideCapabilities()
                        2 -> SlidePermissions(
                            permissionsGranted = permissionsGranted,
                            onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) }
                        )
                        3 -> SlideProfileSetup(
                            userName = userName,
                            onNameChange = { userName = it },
                            userEmail = userEmail,
                            onEmailChange = { userEmail = it },
                            userPhone = userPhone,
                            onPhoneChange = { userPhone = it }
                        )
                    }
                }
            }

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        if (currentStep < totalSteps - 1) {
                            currentStep++
                        } else {
                            onComplete(
                                userName.trim().ifBlank { "User" },
                                userEmail.trim().ifBlank { "user.aurviz@example.com" },
                                userPhone.trim()
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AurvizBlueViolet),
                    modifier = Modifier.weight(if (currentStep > 0) 2f else 1f)
                ) {
                    Text(
                        text = if (currentStep == totalSteps - 1) "Get Started →" else "Continue →",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideWelcome(logoScale: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .scale(logoScale)
                .clip(CircleShape)
                .background(AurvizBlueViolet.copy(alpha = 0.15f))
                .border(2.dp, AurvizBlueViolet.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.aurviz_mark_transparent),
                contentDescription = "Aurviz Logo",
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "AURVIZ AGENT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Autonomous Personal AI Companion",
            style = MaterialTheme.typography.titleSmall,
            color = AurvizBlueViolet,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Execute multi-step tasks across email composition, web browser automation, phone calls, WhatsApp messages, and recurring cron routines.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SlideCapabilities() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Real-World Action Suite",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Your agent does not just converse; it autonomously controls tools to get work done:",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(4.dp))

        CapabilityHighlight(
            icon = Icons.Default.Email,
            title = "Autonomous Emailing",
            desc = "Context-aware leave requests, deliverables, and briefings with confirmation gates."
        )
        CapabilityHighlight(
            icon = Icons.Default.Language,
            title = "Web Automation Engine",
            desc = "Controlled browser opens target websites, extracts live facts, and navigates pages."
        )
        CapabilityHighlight(
            icon = Icons.Default.Alarm,
            title = "Cron-like Routines",
            desc = "WorkManager schedules recurring jobs surviving device reboots and low-battery states."
        )
        CapabilityHighlight(
            icon = Icons.Default.Phone,
            title = "Calls & WhatsApp",
            desc = "Resolves contacts from your phonebook and launches customized direct chats."
        )
    }
}

@Composable
private fun CapabilityHighlight(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AurvizBlueViolet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AurvizBlueViolet, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                Text(desc, color = Color(0xFF94A3B8), fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun SlidePermissions(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Device Permissions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "To allow the agent to listen to voice commands, resolve contacts, and run routines, please grant device permissions:",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(4.dp))

        PermissionBadge(icon = Icons.Default.Mic, title = "Microphone", desc = "Capture speech-to-text voice commands")
        PermissionBadge(icon = Icons.Default.Person, title = "Contacts", desc = "Resolve contact names for WhatsApp & calls")
        PermissionBadge(icon = Icons.Default.Phone, title = "Phone", desc = "Initiate telephone calls on request")
        PermissionBadge(icon = Icons.Default.CameraAlt, title = "Camera", desc = "Voice-activated camera launch")
        PermissionBadge(icon = Icons.Default.Notifications, title = "Notifications", desc = "Background task execution alerts")

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onRequestPermissions,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (permissionsGranted) AurvizSuccess else AurvizBlueViolet
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (permissionsGranted) Icons.Default.Check else Icons.Default.VerifiedUser,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (permissionsGranted) "Permissions Granted ✓" else "Grant Device Permissions",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AurvizBlueViolet, modifier = Modifier.size(18.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 12.5.sp)
            Text(desc, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SlideProfileSetup(
    userName: String,
    onNameChange: (String) -> Unit,
    userEmail: String,
    onEmailChange: (String) -> Unit,
    userPhone: String,
    onPhoneChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Personalize Your Agent",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Set how your AI assistant should address you and which email account to draft messages from:",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onNameChange,
            label = { Text("Your Preferred Name") },
            placeholder = { Text("e.g. User or Alex") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = AurvizBlueViolet) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = AurvizBlueViolet,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = userEmail,
            onValueChange = onEmailChange,
            label = { Text("Primary Email Address") },
            placeholder = { Text("user.aurviz@example.com") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = AurvizBlueViolet) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = AurvizBlueViolet,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = userPhone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number (Optional)") },
            placeholder = { Text("+91 98765 43210") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = AurvizSuccess) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = AurvizSuccess,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
