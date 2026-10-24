// Nova Modern: colours and type, matching the "Nova Redesign" design canvas
package com.archos.mediacenter.video.modern.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.archos.mediacenter.video.R

object Nova {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF121214)
    val Surface2 = Color(0xFF1A1A1D)
    val Text = Color(0xFFF4F1EA)
    val TextSoft = Color(0xFFCFCBC3)
    val Muted = Color(0xFFA3A09A)
    val Dim = Color(0xFF8A8780)
    val Line = Color(0x1FF4F1EA)
    val Accent = Color(0xFFE50914)
    val OnAccent = Color(0xFFFFFFFF)
    val Scrim = Color(0x8C000000)
}

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

object NovaType {
    val Hero = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.4).sp)
    val Title = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp)
    val Section = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp)
    val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val BodyStrong = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
    val Meta = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val Small = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val Tiny = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp)
    val Badge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.6.sp)
    val Button = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp)
    val Label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
}

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Nova.Accent,
        onPrimary = Nova.OnAccent,
        background = Nova.Black,
        onBackground = Nova.Text,
        surface = Nova.Black,
        onSurface = Nova.Text,
        surfaceVariant = Nova.Surface2,
        onSurfaceVariant = Nova.Muted,
        secondary = Nova.Text,
        onSecondary = Nova.Black,
    )
    val base = Typography()
    val typography = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
        bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
        bodySmall = base.bodySmall.copy(fontFamily = Manrope),
        labelLarge = base.labelLarge.copy(fontFamily = Manrope),
        labelMedium = base.labelMedium.copy(fontFamily = Manrope),
        titleMedium = base.titleMedium.copy(fontFamily = Manrope),
        titleLarge = base.titleLarge.copy(fontFamily = Manrope),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
