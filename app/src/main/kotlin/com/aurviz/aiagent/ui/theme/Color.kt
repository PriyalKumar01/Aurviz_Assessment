package com.aurviz.aiagent.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Aurviz Brand Design System
// Dual Light & Dark Executive Themes
// ==========================================

// --- DARK PALETTE (Refined Charcoal Slate, not harsh neon blue) ---
val DarkBackground = Color(0xFF0B0F19)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkBorder = Color(0xFF283548)

val DarkUserBubble = Color(0xFF1E3A8A)       // Deep executive dark blue
val DarkAgentBubble = Color(0xFF132B22)      // Subtle dark emerald tint
val DarkAgentBubbleBorder = Color(0xFF1B4D3E)

val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)

// --- LIGHT PALETTE (Clean, Crisp, Off-white & Mint Green) ---
val LightBackground = Color(0xFFF8FAFC)      // Soft clean slate-50
val LightSurface = Color(0xFFFFFFFF)         // Crisp pure white
val LightSurfaceVariant = Color(0xFFF1F5F9)  // Slate-100
val LightBorder = Color(0xFFE2E8F0)          // Slate-200

val LightUserBubble = Color(0xFF1E3A8A)      // Deep professional dark blue (text is white)
val LightAgentBubble = Color(0xFFEBF7EE)     // Soft light greenish mint (requested by user)
val LightAgentBubbleBorder = Color(0xFFC8E6C9)

val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)

// Shared Accents (Professional Dark Blue-Violet, Emerald & Slate Gray)
val AurvizBlueViolet = Color(0xFF4361EE)     // Professional Dark Blue-Violet / Royal Indigo
val AurvizIndigo = Color(0xFF3F37C9)         // Deep rich indigo
val AurvizSlate = Color(0xFF64748B)          // Cool slate gray
val AurvizGold = Color(0xFFF59E0B)           // Warm amber accent
val AurvizGoldGlow = Color(0xFFFBBF24)
val AurvizSuccess = Color(0xFF10B981)        // Crisp emerald green
val AurvizError = Color(0xFFEF4444)

// Backward compatible aliases
val AurvizNavyDark = DarkBackground
val AurvizNavySurface = DarkSurface
val AurvizNavyElevated = DarkSurfaceVariant
val AurvizNavyBorder = DarkBorder
val AurvizPrimaryBlue = AurvizBlueViolet
val AurvizAccentGold = AurvizBlueViolet
val AurvizTextPrimary = DarkTextPrimary
val AurvizTextSecondary = DarkTextSecondary
