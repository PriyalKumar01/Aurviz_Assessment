# Aurviz Agent — Design System & Engineering Reference

This document serves as the single source of truth for the **Aurviz Agent** Android application's UI design system, color palette, logo assets, component styling, and architectural rules.

---

## 🎨 1. Dual Light & Dark Executive Theme

The app supports dynamic **Light & Dark mode toggling** via the Sun/Moon icon in the top-right header, persisted in `SecurePreferences`:

### Primary Palette (Dark Blue-Violet & Emerald)
- **Primary / Brand**: `#4361EE` (Deep Blue-Violet / Electric Indigo)
- **Primary Variant**: `#3F37C9` (Deep Royal Indigo)
- **Secondary / Accent**: `#10B981` (Emerald Green)
- **Tertiary / Neutral**: `#64748B` (Slate Gray)

### Dark Mode (Refined Charcoal Slate & Deep Indigo)
- **Background**: `#0B0F19` (Deep Charcoal Slate — avoids harsh neon blue saturation)
- **Surface / Cards**: `#111827` / `#1E293B`
- **Border**: `#283548`
- **User Message Bubble**: `#4361EE` (Executive Dark Blue-Violet, text `#FFFFFF`)
- **Agent Message Bubble**: `#132B22` (Subtle Dark Emerald / Mint tint, border `#1B4D3E`, text `#F8FAFC`)
- **Accent**: `#10B981` (Emerald Green)

### Light Mode (Clean, Crisp Executive)
- **Background**: `#F8FAFC` (Slate 50 — clean, crisp neutral off-white)
- **Surface / Cards**: `#FFFFFF` (Pure white)
- **Border**: `#E2E8F0` (Slate 200)
- **User Message Bubble**: `#3F37C9` (Deep Royal Indigo, text `#FFFFFF`)
- **Agent Message Bubble**: `#EBF7EE` (Soft Light Greenish Mint, border `#C8E6C9`, text `#0F172A`)
- **Accent**: `#059669` (Forest Emerald)

---

## 🖼️ 2. Logo Assets & Left-Aligned TopBar

- **TopBar Layout**:
  - **Left Side**:
    - High-resolution 38dp Aurviz logo mark (`aurviz_mark_transparent.png`).
    - App Title: **"Aurviz Agent"** in bold typography.
    - Subtitle: Pulsing green status dot + **"Personal AI Agent • Ready"** (Unprofessional brownish "AI" badge completely removed).
    - Tapping the TopBar brand navigates smoothly back to the **Home** dashboard.
  - **Right Side**:
    - Interactive **Light / Dark Mode Toggle Button** (Sun / Moon icon with haptic ripple).
- **Launcher Icons**:
  - Generated across all mipmap densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) for both square and round adaptive launcher icons (`#061B3A` background).
- **Assistant Message Avatar**:
  - Preceded by official Aurviz logo mark on every agent response.

---

## 🏠 3. Home Dashboard & First-Launch Onboarding

1. **Home Screen (`HomeScreen.kt`)**:
   - The app's default entry destination (`Screen.Home`).
   - Executive greeting banner ("Welcome back, Priyal!").
   - 4-card interactive feature grid (Autonomous Email Dispatcher, Real-World Web Automation, Cron & Routine Scheduling, Long-Term Memory Engine).
   - Quick action prompt chips ("Send email to colleague", "Check unread emails", "Schedule daily 9 AM summary").
   - Direct "Chat with Aurviz Agent" high-impact button.
2. **Onboarding Carousel (`OnboardingDialog.kt`)**:
   - Displays automatically on first APK launch.
   - 4-step wizard:
     - Step 1: Welcome & Overview
     - Step 2: Real-World Action Tools (Email, Call, WhatsApp, Web, Camera)
     - Step 3: Security & Privacy (On-device encrypted MasterKeys, zero key leaks)
     - Step 4: Profile Customization (Sets user name, email, phone number directly in SQLite + EncryptedSharedPreferences).

---

## 📱 4. Streamlined Vertical Timeline & Modern Dialogs

1. **Vertical Execution Plan Timeline (`ExecutionPlanCard.kt`)**:
   - Replaced cramped horizontal milestone bubbles with an elegant vertical timeline.
   - Each step features a distinct status icon (Pending, Running spinner, Success checkmark, Error cross), title, and execution latency.
   - Auto-collapses into a sleek 1-line badge when complete: `✓ Plan Completed (2 steps • 120ms) ▾`.
2. **Slim Integrated Input Pill (`AgentChatScreen.kt`)**:
   - Streamlined 44dp pill containing the Mic button, single-line text field, and Send button in one integrated container.
3. **8.dp Rounded Add Memory Dialog (`MemoryScreen.kt`)**:
   - Clean, professional 8.dp rounded corners (no overly rounded or invisible fields).
   - High-contrast, theme-aware text inputs for Light and Dark modes.
   - Dropdown category picker (`ExposedDropdownMenuBox`) replacing messy horizontal scroll chips.
4. **Interactive Settings & Permission Switches (`SettingsScreen.kt`)**:
   - Explicit "Save Profile" and "Reset Defaults" buttons with instant visual feedback.
   - Per-permission interactive switch toggles for `RECORD_AUDIO`, `CALL_PHONE`, `CAMERA`, `READ_CONTACTS`, and `POST_NOTIFICATIONS`.
   - Native permission request launcher for toggle ON, and OS App Settings dialog for toggle OFF.

---

## 🧠 5. Robust Natural Language Intent Architecture

The `MultiStepPlanner` fallback handles conversational variations without rigid constraints:

| User Input Type | Intent Routing | Action Taken |
| :--- | :--- | :--- |
| **Email with prefix/dictation** <br/>*(e.g. "hello send email to... and say...")* | `isEmailDispatchIntent()` | Extracts recipient email, subject, and dictated body into `send_email` with `action: "send"`. |
| **Specific email hello** <br/>*(e.g. "I just want to send a hello message to 230104043@gmaildu.ac.in")* | `isEmailDispatchIntent()` | Extracts `230104043@gmaildu.ac.in`, drafts polite check-in, prepares `send_email`. |
| **WhatsApp message to contact** <br/>*(e.g. "papa ko whatsapp par hello bhejdo")* | `isWhatsAppIntent()` | Resolves contact name via `ContactsContract`, drafts clean intent without unsolicited boilerplate. |
| **Phone call** <br/>*(e.g. "call 9876543210" or "call Papa")* | `isCallIntent()` | Resolves number/contact and dispatches `ACTION_CALL` or `ACTION_DIAL`. |
| **Scheduled email dispatch** <br/>*(e.g. "send an email to xyz@abc daily at 10 am")* | `isSchedulingInstruction()` | Registers recurring rule with `WorkManager`, then schedules `send_email(action: "send")`. |
| **Sender identity update** <br/>*(e.g. "set my email as priyalkumar06@gmail.com")* | `isIdentityEmailUpdate()` | Updates `SecurePreferences.userEmailAccount` and saves `primary_email` in SQLite. |
| **Pure greetings / Small talk** <br/>*(e.g. "hello", "hi", "how are you")* | `isPureGreeting()` | Responds with friendly conversational assistant greeting (never "No memories found"). |
