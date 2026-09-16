package com.example.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.shared.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureRoutePickerBottomSheet(
    routes: List<Route>,
    onConfirm: (Route) -> Unit,
    onUseCurrent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStart by remember { mutableStateOf<String?>(null) }
    var selectedEnd by remember { mutableStateOf<String?>(null) }

    val parsed = routes.mapNotNull { r ->
        val parts = r.name.split("→", "->")
        if (parts.size == 2) Triple(r, parts[0].trim(), parts[1].trim()) else null
    }

    val starts = parsed.map { it.second }.distinct().toMutableList().apply { add(0, "Use Current Location") }
    val ends = parsed
        .filter { selectedStart == null || selectedStart == "Use Current Location" || it.second == selectedStart }
        .map { it.third }
        .distinct()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Select Route",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start", style = MaterialTheme.typography.labelMedium)
                            PureDropdownMenuBox(
                                options = starts,
                                selected = selectedStart,
                                placeholder = "Select start"
                            ) {
                                selectedStart = it
                                selectedEnd = null
                            }
                        }
                        IconButton(
                            onClick = {
                                val temp = selectedStart
                                selectedStart = selectedEnd
                                selectedEnd = temp
                            },
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Swap"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Destination", style = MaterialTheme.typography.labelMedium)
                            PureDropdownMenuBox(
                                options = ends,
                                selected = selectedEnd,
                                placeholder = "Select destination"
                            ) {
                                selectedEnd = it
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    enabled = selectedStart != null && selectedEnd != null,
                    onClick = {
                        if (selectedStart == "Use Current Location") {
                            onUseCurrent(selectedEnd!!)
                        } else {
                            val match = parsed.firstOrNull { it.second == selectedStart && it.third == selectedEnd }
                            if (match != null) onConfirm(match.first)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start Navigation")
                }
            }
        }
    }
}

@Composable
fun PureDropdownMenuBox(
    options: List<String>,
    selected: String?,
    placeholder: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val arrowRotation by animateFloatAsState(
        if (expanded) 180f else 0f, tween(180), label = "Location menu expansion"
    )

    val filtered = remember(options, query) {
        if (query.isBlank()) options
        else options.filter { it.contains(query, ignoreCase = true) }
    }

    Box {
        OutlinedButton(
            onClick = { expanded = true }, 
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(selected ?: placeholder, maxLines = 1, modifier = Modifier.weight(1f),
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(arrowRotation))
        }
        PureDropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                singleLine = true,
                placeholder = { Text("Search location...") },
                leadingIcon = { Text("🔍") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(4.dp))

            val currentItems = filtered.filter { it.equals("Use Current Location", ignoreCase = true) }
            val gateItems = filtered.filter {
                it.contains("gate", ignoreCase = true) && !it.equals("Use Current Location", ignoreCase = true)
            }.sorted()
            val blockItems = filtered.filter {
                it.matches(Regex("^[A-D][0-9].*")) || it.equals("DACA", ignoreCase = true)
            }.sorted()
            val facilityItems = filtered.filter {
                !it.equals("Use Current Location", ignoreCase = true) &&
                        !gateItems.contains(it) &&
                        !blockItems.contains(it)
            }.sorted()

            @Composable
            fun ItemRow(label: String, iconEmoji: String) {
                PureDropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Text(iconEmoji) },
                    onClick = {
                        onSelect(label)
                        expanded = false
                        query = ""
                    }
                )
            }

            if (currentItems.isNotEmpty()) {
                PureDropdownMenuItem(
                    text = { Text("📍 Current") },
                    enabled = false,
                    onClick = {}
                )
                currentItems.forEach { item ->
                    ItemRow(item, "📍")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            if (gateItems.isNotEmpty()) {
                PureDropdownMenuItem(
                    text = { Text("🏛 Gates") },
                    enabled = false,
                    onClick = {}
                )
                gateItems.forEach { item ->
                    ItemRow(item, "🚪")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            if (blockItems.isNotEmpty()) {
                PureDropdownMenuItem(
                    text = { Text("🏢 Academic Blocks") },
                    enabled = false,
                    onClick = {}
                )
                blockItems.forEach { item ->
                    ItemRow(item, "🏢")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            if (facilityItems.isNotEmpty()) {
                PureDropdownMenuItem(
                    text = { Text("🏫 Facilities") },
                    enabled = false,
                    onClick = {}
                )
                facilityItems.forEach { item ->
                    ItemRow(item, "🏫")
                }
            }
        }
    }
}
