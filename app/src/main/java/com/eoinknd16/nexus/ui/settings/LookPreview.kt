package com.eoinknd16.nexus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eoinknd16.nexus.look.FontChoice
import com.eoinknd16.nexus.look.LookState
import com.eoinknd16.nexus.look.family
import com.eoinknd16.nexus.ui.fx.GlowProgress
import com.eoinknd16.nexus.ui.fx.HoloTile
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.NexusBackdrop
import com.eoinknd16.nexus.ui.fx.NexusButton
import com.eoinknd16.nexus.ui.fx.holoPanel
import com.eoinknd16.nexus.ui.fx.nexusShape
import com.eoinknd16.nexus.ui.theme.Nx
import com.eoinknd16.nexus.ui.theme.hud

/**
 * A miniature home screen drawn with the live look -- backdrop, clock font,
 * panels, tiles, buttons, progress, tray -- so every change in Customise is
 * visible the moment you make it, without leaving the screen.
 */
@Composable
fun LookPreview(modifier: Modifier = Modifier) {
    val shape = nexusShape()
    val clockFont = LookState.look.fonts.clock.family()
    Box(modifier.fillMaxWidth().height(250.dp).clip(shape).border(1.dp, Nx.CyanDim, shape)) {
        NexusBackdrop {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("21", fontSize = 34.sp, fontFamily = clockFont, color = Nx.Text)
                            Text(":", fontSize = 30.sp, fontFamily = clockFont, color = Nx.Cyan)
                            Text("47", fontSize = 34.sp, fontFamily = clockFont, color = Nx.Text)
                        }
                        Text(hud("Thursday · 26 November"), style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WbSunny, null, tint = Nx.Amber, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(hud("9° · Partly cloudy"), style = MaterialTheme.typography.labelSmall, color = Nx.Text)
                        }
                    }
                    Column(Modifier.width(120.dp).holoPanel().padding(8.dp)) {
                        Text("Now playing", style = MaterialTheme.typography.labelSmall, color = Nx.Cyan)
                        Text("Severance", style = MaterialTheme.typography.bodyMedium, color = Nx.Text, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        GlowProgress(0.6f, Modifier.fillMaxWidth())
                    }
                }
                HudLabel("Command deck")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoloTile("TV", Icons.Filled.Movie, Modifier.weight(1f), sublabel = "Movies") {}
                    HoloTile("Remote", Icons.Filled.SettingsRemote, Modifier.weight(1f), sublabel = "PC", accent = Nx.Magenta) {}
                    HoloTile("Apps", Icons.Filled.Apps, Modifier.weight(1f), sublabel = "All", accent = Nx.Lime) {}
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NexusButton("Play", filled = true) {}
                    Spacer(Modifier.width(8.dp))
                    NexusButton("Sources") {}
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.holoPanel(glow = 0.1f).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Nx.Cyan, Nx.Magenta, Nx.Amber, Nx.Lime).forEach {
                            Box(Modifier.size(16.dp).clip(nexusShape(0.3f)).background(it.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
        }
    }
}

/** A font option drawn in itself, for pickers. */
@Composable
fun FontSample(choice: FontChoice) {
    Text("Aa 12:45", fontFamily = choice.family(), fontSize = 18.sp, color = Nx.Text)
}

/** Round colour dot, for pickers. */
@Composable
fun Swatch(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(18.dp).clip(CircleShape).background(color).border(1.dp, Nx.Text.copy(alpha = 0.3f), CircleShape))
}
