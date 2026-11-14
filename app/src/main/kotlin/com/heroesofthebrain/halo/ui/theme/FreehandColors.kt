package com.heroesofthebrain.halo.ui.theme

import androidx.compose.ui.graphics.Color
import com.heroesofthebrain.halo.detect.GestureType

/**
 * Freehand Brand Color Tokens — Source of Truth: logo/kit/BRAND.md (§3 & §9)
 *
 * Warm ramp: Ember → Flame → Pupil
 * Proportions in UI: Neutrals 80% · Flame 12% · Pupil 5% · Ember 3%
 */
object FreehandColors {
    // Brand Warm Ramp (Dark Mode)
    val Ember = Color(0xFFE5483A)         // External bars: strong accent, head turns
    val Flame = Color(0xFFFF7A45)         // Internal bars: PRIMARY UI color, clench
    val Pupil = Color(0xFFFFC247)         // Center circle: LIVE signal, active, gesture detected, blinks

    // Brand Light Mode values (available for dual-mode)
    val EmberLight = Color(0xFFC93328)
    val FlameLight = Color(0xFFE35A26)
    val PupilLight = Color(0xFFEA9A0E)
    val Rust = Color(0xFFB8461F)          // Primary on light

    // Neutrals (Warm dark greys, never cool/blueish)
    val Ink900 = Color(0xFF16120F)        // Background
    val Ink850 = Color(0xFF1B1613)        // Deep card / elevated background
    val Ink800 = Color(0xFF201A16)        // Surface (cards, sheets)
    val Ink700 = Color(0xFF2A231E)        // Surface-2 (chips, highlighted items, inputs)
    val Ink650 = Color(0xFF332B25)        // Surface-3 (hover, active items)
    val Ink600 = Color(0xFF3D342D)        // Outline / dividers (decorative)
    val Ink500 = Color(0xFF52463D)        // Stronger outline

    // Typography Neutrals
    val Cream = Color(0xFFF7EFE6)         // Text Primary (16.4:1 contrast on Ink900 - AAA)
    val Sand = Color(0xFFB8ACA0)          // Text Secondary (8.4:1 contrast on Ink900 - AA)
    val SandMuted = Color(0xFF7D7166)     // Text Tertiary (hints, placeholders)

    // Neutrals Light (for reference / fallback)
    val Paper = Color(0xFFFAF6F1)
    val PaperDim = Color(0xFFF1EAE2)
    val Linen = Color(0xFFDDD2C6)
    val InkText = Color(0xFF1E1814)
    val Taupe = Color(0xFF6B5F55)

    // Semantic Status Colors
    val Success = Color(0xFF5CC98A)       // Connected, calibration ok, signal strong
    val Warning = Pupil                   // Weak signal, low battery (= Pupil #FFC247)
    val Error = Color(0xFFFF6B6B)         // Signal lost, disconnect, error
    val SuccessLight = Color(0xFF1E7F4F)
    val ErrorLight = Color(0xFFC62828)

    // Gesture Semantic Color Mapping (§3.5)
    // - Blinks: Pupil (#FFC247)
    // - Clench: Flame (#FF7A45)
    // - Head movements: Ember (#E5483A)
    fun forGesture(gesture: GestureType?): Color = when (gesture) {
        GestureType.DOUBLE_BLINK,
        GestureType.LONG_BLINK -> Pupil
        GestureType.CLENCH -> Flame
        GestureType.TURN_LEFT,
        GestureType.TURN_RIGHT,
        GestureType.TURN_UP,
        GestureType.TURN_DOWN -> Ember
        null -> SandMuted
    }
}
