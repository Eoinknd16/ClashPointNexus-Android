package com.eoinknd16.nexus.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eoinknd16.nexus.media.CatalogItem
import com.eoinknd16.nexus.ui.fx.GlowProgress
import com.eoinknd16.nexus.ui.fx.HudLabel
import com.eoinknd16.nexus.ui.fx.nexusPress
import com.eoinknd16.nexus.ui.theme.Nx

val PosterShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

@Composable
fun PosterCard(
    item: CatalogItem,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    Column(modifier.width(width).nexusPress(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(PosterShape).background(Nx.Deep)) {
            AsyncImage(
                model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Title fallback for posterless items, under the image so a real poster covers it.
            if (item.poster == null) {
                Text(
                    item.name, color = Nx.Text, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color(0xAA05070D)),
                ),
            )
            item.imdbRating?.let {
                Text(
                    "★ $it", style = MaterialTheme.typography.labelSmall, color = Nx.Amber,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
        }
        if (progress != null) GlowProgress(progress, Modifier.fillMaxWidth().padding(top = 4.dp))
        Spacer(Modifier.height(6.dp))
        Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Nx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.year?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Nx.Muted, maxLines = 1) }
    }
}

@Composable
fun PosterRow(
    label: String,
    items: List<CatalogItem>,
    progressFor: (CatalogItem) -> Float? = { null },
    onItem: (CatalogItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HudLabel(label, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "${it.type}:${it.id}" }) { item ->
                PosterCard(item, progress = progressFor(item)) { onItem(item) }
            }
        }
    }
}
