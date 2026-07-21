// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme

@Composable
fun StyleSheetScreen() {
    Scaffold(
        containerColor = StickyKeysTheme.colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(StickyKeysTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.lg)
        ) {
            Text(
                text = "Style Sheet",
                style = StickyKeysTheme.typography.titleLarge,
                color = StickyKeysTheme.colors.onBackground
            )

            // Colors
            Card(
                colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surface),
                shape = StickyKeysTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm)
                ) {
                    Text(
                        text = "Colors",
                        style = StickyKeysTheme.typography.titleMedium,
                        color = StickyKeysTheme.colors.onSurface
                    )
                    
                    ColorRow("Primary", StickyKeysTheme.colors.primary, StickyKeysTheme.colors.onPrimary)
                    ColorRow("Primary Variant", StickyKeysTheme.colors.primaryVariant, StickyKeysTheme.colors.onPrimary)
                    ColorRow("Secondary", StickyKeysTheme.colors.secondary, StickyKeysTheme.colors.onSecondary)
                    ColorRow("Background", StickyKeysTheme.colors.background, StickyKeysTheme.colors.onBackground)
                    ColorRow("Surface", StickyKeysTheme.colors.surface, StickyKeysTheme.colors.onSurface)
                    ColorRow("Surface Variant", StickyKeysTheme.colors.surfaceVariant, StickyKeysTheme.colors.onSurfaceVariant)
                    ColorRow("Error", StickyKeysTheme.colors.error, StickyKeysTheme.colors.onError)
                }
            }

            // Typography
            Card(
                colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surface),
                shape = StickyKeysTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(StickyKeysTheme.spacing.sm)
                ) {
                    Text(
                        text = "Typography",
                        style = StickyKeysTheme.typography.titleMedium,
                        color = StickyKeysTheme.colors.onSurface
                    )
                    
                    Text("Title Large", style = StickyKeysTheme.typography.titleLarge, color = StickyKeysTheme.colors.onSurface)
                    Text("Title Medium", style = StickyKeysTheme.typography.titleMedium, color = StickyKeysTheme.colors.onSurface)
                    Text("Body Large", style = StickyKeysTheme.typography.bodyLarge, color = StickyKeysTheme.colors.onSurface)
                    Text("Body Medium", style = StickyKeysTheme.typography.bodyMedium, color = StickyKeysTheme.colors.onSurface)
                    Text("Label Large", style = StickyKeysTheme.typography.labelLarge, color = StickyKeysTheme.colors.onSurface)
                    Text("Label Medium", style = StickyKeysTheme.typography.labelMedium, color = StickyKeysTheme.colors.onSurface)
                    Text("Keyboard Key", style = StickyKeysTheme.typography.keyboardKey, color = StickyKeysTheme.colors.onSurface)
                }
            }

            // Spacing & Shapes
            Card(
                colors = CardDefaults.cardColors(containerColor = StickyKeysTheme.colors.surface),
                shape = StickyKeysTheme.shapes.pill,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(StickyKeysTheme.spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pill Shape & Spacing LG",
                        style = StickyKeysTheme.typography.bodyMedium,
                        color = StickyKeysTheme.colors.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorRow(name: String, color: Color, onColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(color, StickyKeysTheme.shapes.small)
            .padding(horizontal = StickyKeysTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = StickyKeysTheme.typography.bodyLarge,
            color = onColor,
            fontWeight = FontWeight.Bold
        )
    }
}
