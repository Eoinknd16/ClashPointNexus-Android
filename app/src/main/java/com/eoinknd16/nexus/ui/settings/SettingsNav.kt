package com.eoinknd16.nexus.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.eoinknd16.nexus.ui.theme.Nx

/**
 * How a settings screen is showing its [Section]s:
 *  - [Index]: an overview list of sections (title + current status), tap to open.
 *  - [Open]: one section's settings on their own page.
 *  - [Search]: every setting whose name matches, grouped by section.
 *  - [Inline]: old-style collapsible sections (used inside editors).
 */
sealed interface SectionMode {
    data object Index : SectionMode
    data class Open(val title: String) : SectionMode
    data class Search(val query: String) : SectionMode
    data object Inline : SectionMode
}

val LocalSectionMode = compositionLocalOf<SectionMode> { SectionMode.Inline }

/** Opens a section from the index. */
val LocalOpenSection = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Current search text for rows inside a section ("" = show everything). */
val LocalRowQuery = compositionLocalOf { "" }

/** Lets a section know one of its rows matched the search. */
val LocalRowMatched = staticCompositionLocalOf<() -> Unit> { {} }

/** True if a row with this name should show for the current search; tells its section if it matched. */
@Composable
fun rowVisible(title: String, subtitle: String? = null): Boolean {
    val q = LocalRowQuery.current.trim()
    if (q.isEmpty()) return true
    val hit = title.contains(q, ignoreCase = true) || (subtitle?.contains(q, ignoreCase = true) == true)
    if (hit) {
        val matched = LocalRowMatched.current
        SideEffect { matched() }
    }
    return hit
}

/** Small explanatory text that disappears while searching. */
@Composable
fun Hint(text: String) {
    if (LocalRowQuery.current.isNotBlank()) return
    Text(text, style = MaterialTheme.typography.labelSmall, color = Nx.Muted)
}
