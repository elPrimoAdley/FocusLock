package com.example.focuslock.ui.insights

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.BuildConfig
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.engine.RuleFormatterAndDetoxCalculator
import com.example.focuslock.engine.insights.InsightsCalculator
import com.example.focuslock.engine.insights.InsightsPeriod
import com.example.focuslock.engine.insights.MonthlyHeatmapGrid
import com.example.focuslock.engine.insights.StackedBarSegment
import com.example.focuslock.engine.insights.TopUsedAppItem
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.navigation.Destination
import com.example.focuslock.ui.navigation.TopSectionBar
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var appForCategorySheet by remember { mutableStateOf<TopUsedAppItem?>(null) }
    var editingAwakeStart by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = colors.nightAbyss,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.nightAbyss)
                .padding(innerPadding)
                .systemBarsPadding()
        ) {
            TopSectionBar(title = Destination.Insights.title, onOpenDrawer = onOpenDrawer)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 4.21 : Carte d'explication si la permission UsageStats n'est pas accordée
                if (!uiState.hasUsagePermission) {
                    item {
                        ElevatedCard {
                            Text(
                                text = "PERMISSION D'USAGE REQUISE",
                                style = typo.monoTag,
                                color = colors.softCoral
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Autorise l'accès aux données d'utilisation pour afficher ton histogramme 24h, ta Balance d'éveil et tes applications les plus utilisées.",
                                style = typo.bodySecondary,
                                color = colors.ink
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            MintPillButton(
                                text = "Ouvrir les réglages Accès aux données d'utilisation",
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Rafraîchir l'état de la permission",
                                style = typo.monoTag,
                                color = colors.electricAzureStart,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { viewModel.refreshPermissionCheck() }
                                    .padding(6.dp)
                            )
                        }
                    }
                }

                // ==================== 4.15 EN-TÊTE SEGMENTÉ & TOTAL GÉANT ====================
                val summary = uiState.summary
                if (summary != null) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Sélecteur segmenté Jour | Semaine | Tendance
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, CircleShape)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                InsightsPeriod.entries.forEach { period ->
                                    val selected = uiState.selectedPeriod == period
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(if (selected) colors.electricAzureEnd else Color.Transparent)
                                            .clickable { viewModel.selectPeriod(period) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = period.label,
                                            style = typo.bodySecondary,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) colors.nightAbyss else colors.mutedInk
                                        )
                                    }
                                }
                            }

                            // Navigation par date (< AUJOURD'HUI >)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.elevatedCard)
                                        .clickable { viewModel.navigateDate(-1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("‹", fontSize = 18.sp, color = colors.ink)
                                }

                                Text(
                                    text = summary.periodTitle,
                                    style = typo.monoTag,
                                    color = colors.electricAzureStart,
                                    modifier = Modifier.clickable { viewModel.resetToToday() }
                                )

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colors.elevatedCard)
                                        .clickable { viewModel.navigateDate(1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("›", fontSize = 18.sp, color = colors.ink)
                                }
                            }

                            // Temps d'écran géant ("1h 40m")
                            Text(
                                text = summary.formattedTotal,
                                style = typo.timerDisplay.copy(fontSize = 42.sp),
                                color = colors.ink
                            )
                            Text(
                                text = summary.trendDeltaText ?: "TEMPS D'ÉCRAN",
                                style = typo.monoTag,
                                color = colors.mutedInk
                            )
                        }
                    }

                    // ==================== 4.16 GRAPHIQUE EMPILÉ EN CANVAS ====================
                    item {
                        ElevatedCard {
                            StackedBarChart(
                                segments = summary.bars,
                                maxScaleMinutes = summary.maxBarScaleMinutes,
                                period = summary.period,
                                totalFormatted = summary.formattedTotal
                            )
                        }
                    }

                    // ==================== 4.17 APPLICATIONS LES PLUS UTILISÉES ====================
                    item {
                        Text(
                            text = "APPLICATIONS LES PLUS UTILISÉES",
                            style = typo.cardTitleSerif,
                            color = colors.ink
                        )
                    }

                    if (summary.topApps.isEmpty()) {
                        item {
                            ElevatedCard {
                                Text(
                                    text = "Aucune activité d'application enregistrée sur cette période.",
                                    style = typo.bodySecondary,
                                    color = colors.mutedInk
                                )
                            }
                        }
                    } else {
                        items(summary.topApps, key = { it.packageName }) { appItem ->
                            val label = uiState.appLabelsByPackage[appItem.packageName]
                                ?: appItem.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                            val icon by produceState<ImageBitmap?>(initialValue = null, key1 = appItem.packageName) {
                                value = viewModel.appsLoader.getAppIcon(appItem.packageName)
                            }

                            ElevatedCard(onClick = { appForCategorySheet = appItem }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(colors.deepSlate),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (icon != null) {
                                            Image(
                                                bitmap = icon!!,
                                                contentDescription = label,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        } else {
                                            Text(label.take(2).uppercase(), style = typo.monoTag, color = colors.ink)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = typo.bodyPrimary,
                                            color = colors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        InsightsCategoryPill(
                                            assignedCategory = appItem.assignedCategory,
                                            onClick = { appForCategorySheet = appItem }
                                        )
                                    }

                                    Text(
                                        text = InsightsCalculator.formatScreenTimeTotal(appItem.totalMinutes),
                                        style = typo.monoMetric,
                                        color = colors.ink
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 4.18 DISTRACTIONS ÉVITÉES & BALANCE ====================
                item {
                    ElevatedCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Distractions évitées",
                                    style = typo.bodyPrimary,
                                    color = colors.ink
                                )
                                Text(
                                    text = "Apparitions de l'écran de blocage aujourd'hui",
                                    style = typo.bodySecondary,
                                    color = colors.mutedInk
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.mintClarityGradient)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "🛡 ${uiState.distractionsPreventedToday}",
                                    style = typo.monoMetric,
                                    color = colors.mintOnButton
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = colors.steelLine)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Balance", style = typo.bodyPrimary, color = colors.ink)
                            Text(
                                text = "${uiState.summary?.balancePercentage ?: 0} % du temps d'éveil",
                                style = typo.monoMetric,
                                color = colors.electricAzureStart
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                    .clickable { editingAwakeStart = true }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Réveil : ${RuleFormatterAndDetoxCalculator.formatMinutesHHMM(uiState.awakeStartMinutes)}",
                                    style = typo.monoTag,
                                    color = colors.ink
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                    .clickable { editingAwakeStart = false }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Coucher : ${RuleFormatterAndDetoxCalculator.formatMinutesHHMM(uiState.awakeEndMinutes)}",
                                    style = typo.monoTag,
                                    color = colors.ink
                                )
                            }
                        }
                    }
                }

                // ==================== 4.19 HEATMAP MENSUELLE DES SÉRIES ====================
                val heatmap = uiState.heatmapGrid
                if (heatmap != null) {
                    item {
                        MonthlyHeatmapCard(
                            grid = heatmap,
                            onPrevMonth = { viewModel.navigateHeatmapMonth(-1) },
                            onNextMonth = { viewModel.navigateHeatmapMonth(1) }
                        )
                    }
                }

                // ==================== 4.20 JOURNAL LOCAL D'AUDIT ====================
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "JOURNAL D'AUDIT (90 JOURS)",
                            style = typo.monoTag,
                            color = colors.mutedInk
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isTodayFiltered = uiState.auditFilterDate != null
                            Text(
                                text = if (isTodayFiltered) "Voir tout" else "Filtrer sur ce jour",
                                style = typo.monoTag,
                                color = colors.electricAzureStart,
                                modifier = Modifier.clickable {
                                    viewModel.setAuditDateFilter(
                                        if (isTodayFiltered) null else uiState.selectedDate
                                    )
                                }
                            )
                        }
                    }
                }

                if (uiState.auditLogs.isEmpty()) {
                    item {
                        ElevatedCard {
                            Text(
                                text = "Aucune décision de blocage enregistrée sur cette sélection.",
                                style = typo.bodySecondary,
                                color = colors.mutedInk
                            )
                        }
                    }
                } else {
                    items(uiState.auditLogs, key = { it.id }) { item ->
                        ElevatedCard {
                            Text(
                                text = item.formattedLine,
                                style = typo.monoMetric.copy(fontSize = 12.sp),
                                color = colors.ink
                            )
                        }
                    }

                    if (uiState.canLoadMoreAuditLogs) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.loadMoreAuditLogs() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Charger 50 entrées supplémentaires", style = typo.monoTag, color = colors.electricAzureStart)
                            }
                        }
                    }
                }

                // 4.21 : Générateur de 30 jours de test (uniquement en BuildConfig.DEBUG)
                if (BuildConfig.DEBUG) {
                    item {
                        ElevatedCard {
                            Text("OUTIL DEBUG (ABSENT EN RELEASE)", style = typo.monoTag, color = colors.electricAzureStart)
                            Spacer(modifier = Modifier.height(8.dp))
                            MintPillButton(
                                text = "Générer 30 jours d'historique de test (4.21)",
                                onClick = { viewModel.seedThirtyDaysDebugData() }
                            )
                        }
                    }
                }
            }
        }
    }

    // 4.17 : Sheet de choix de catégorie pour une application
    val targetApp = appForCategorySheet
    if (targetApp != null) {
        val appLabel = uiState.appLabelsByPackage[targetApp.packageName] ?: targetApp.packageName
        var chosenCat by remember(targetApp) {
            mutableStateOf(targetApp.assignedCategory ?: AppCategory.DISTRACTING)
        }
        FocusModalSheet(
            title = "Classer « $appLabel »",
            saveButtonText = "Enregistrer la catégorie",
            onDismissRequest = { appForCategorySheet = null },
            onSaveClick = {
                viewModel.assignAppCategory(targetApp.packageName, chosenCat)
                appForCategorySheet = null
            }
        ) {
            ElevatedCard {
                Text("CHOISIS LA CATÉGORIE INSIGHTS", style = typo.monoTag, color = colors.mutedInk)
                Spacer(modifier = Modifier.height(10.dp))
                val choices = listOf(
                    AppCategory.DISTRACTING to ("Distrayante (Violet #A78BFA)" to colors.distractingViolet),
                    AppCategory.NEUTRAL to ("Neutre (Bleu #60A5FA)" to colors.neutralBlue),
                    AppCategory.PRODUCTIVE to ("Productive (Menthe #34D399)" to colors.productiveMint)
                )
                choices.forEach { (cat, pair) ->
                    val (label, dotColor) = pair
                    val selected = chosenCat == cat
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.deepSlate)
                            .border(
                                1.dp,
                                if (selected) dotColor else colors.steelLine,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { chosenCat = cat }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(label, style = typo.bodyPrimary, color = colors.ink)
                    }
                }
            }
        }
    }

    val isStartAwake = editingAwakeStart
    if (isStartAwake != null) {
        val currentMins = if (isStartAwake) uiState.awakeStartMinutes else uiState.awakeEndMinutes
        val pickerState = rememberTimePickerState(
            initialHour = currentMins / 60,
            initialMinute = currentMins % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingAwakeStart = null },
            containerColor = colors.deepSlate,
            title = {
                Text(
                    text = if (isStartAwake) "Heure de réveil" else "Heure de coucher",
                    style = typo.cardTitleSerif,
                    color = colors.ink
                )
            },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mins = pickerState.hour * 60 + pickerState.minute
                        if (isStartAwake) {
                            viewModel.updateAwakeWindow(mins, uiState.awakeEndMinutes)
                        } else {
                            viewModel.updateAwakeWindow(uiState.awakeStartMinutes, mins)
                        }
                        editingAwakeStart = null
                    }
                ) {
                    Text("Valider", color = colors.mintClarityStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAwakeStart = null }) {
                    Text("Annuler", color = colors.mutedInk)
                }
            }
        )
    }
}

// ==================== 4.16 COMPOSANT GÉNÉRIQUE STACKED BAR CHART EN CANVAS ====================

@Composable
fun StackedBarChart(
    segments: List<StackedBarSegment>,
    maxScaleMinutes: Float,
    period: InsightsPeriod,
    totalFormatted: String
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val safeMax = maxScaleMinutes.coerceAtLeast(1f)

    val talkBackDesc = remember(period, totalFormatted, segments) {
        "Graphique ${period.label} : temps d'écran $totalFormatted sur ${segments.size} barres."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = talkBackDesc }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(140.dp)
            ) {
                val count = segments.size.coerceAtLeast(1)
                val slotWidth = size.width / count.toFloat()
                val barWidth = (slotWidth * 0.56f).coerceIn(6.dp.toPx(), 24.dp.toPx())
                val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

                // Lignes horizontales de repère (100% et 50%)
                drawLine(
                    color = colors.steelLine.copy(alpha = 0.45f),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = colors.steelLine.copy(alpha = 0.45f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.dp.toPx()
                )

                segments.forEachIndexed { index, seg ->
                    val x = index * slotWidth + (slotWidth - barWidth) / 2f
                    // Rail vertical sombre en arrière-plan
                    drawRoundRect(
                        color = colors.deepSlate,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (seg.isAvailable && seg.totalValue > 0f) {
                        var currentBottomY = size.height

                        // 1. Productive (Menthe #34D399) en bas
                        if (seg.productiveValue > 0f) {
                            val h = (seg.productiveValue / safeMax).coerceIn(0f, 1f) * size.height
                            drawRoundRect(
                                color = colors.productiveMint,
                                topLeft = Offset(x, (currentBottomY - h).coerceAtLeast(0f)),
                                size = Size(barWidth, h),
                                cornerRadius = corner
                            )
                            currentBottomY -= h
                        }

                        // 2. Neutral (Bleu #60A5FA) au milieu
                        if (seg.neutralValue > 0f) {
                            val h = (seg.neutralValue / safeMax).coerceIn(0f, 1f) * size.height
                            drawRoundRect(
                                color = colors.neutralBlue,
                                topLeft = Offset(x, (currentBottomY - h).coerceAtLeast(0f)),
                                size = Size(barWidth, h),
                                cornerRadius = corner
                            )
                            currentBottomY -= h
                        }

                        // 3. Distracting (Violet #A78BFA) au sommet
                        if (seg.distractingValue > 0f) {
                            val h = (seg.distractingValue / safeMax).coerceIn(0f, 1f) * size.height
                            drawRoundRect(
                                color = colors.distractingViolet,
                                topLeft = Offset(x, (currentBottomY - h).coerceAtLeast(0f)),
                                size = Size(barWidth, h),
                                cornerRadius = corner
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Échelle verticale droite (1h / 30m / 0s pour la vue Jour)
            Column(
                modifier = Modifier.height(140.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (period == InsightsPeriod.DAY) "1h" else "${safeMax.toInt()}m",
                    style = typo.monoTag.copy(fontSize = 10.sp),
                    color = colors.mutedInk
                )
                Text(
                    text = if (period == InsightsPeriod.DAY) "30m" else "${(safeMax / 2f).toInt()}m",
                    style = typo.monoTag.copy(fontSize = 10.sp),
                    color = colors.mutedInk
                )
                Text(
                    text = "0s",
                    style = typo.monoTag.copy(fontSize = 10.sp),
                    color = colors.mutedInk
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Étiquettes horizontales (0, 6, 12, 18 ou L..D ou S-7..S0)
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelsToDisplay = if (period == InsightsPeriod.DAY) {
                listOf("0", "6", "12", "18")
            } else {
                segments.map { if (!it.isAvailable) "${it.label}*" else it.label }
            }
            labelsToDisplay.forEach { lbl ->
                Text(
                    text = lbl,
                    style = typo.monoTag.copy(fontSize = 10.sp),
                    color = colors.mutedInk
                )
            }
        }
    }
}

@Composable
private fun InsightsCategoryPill(
    assignedCategory: AppCategory?,
    onClick: () -> Unit
) {
    val colors = FocusTheme.colors
    val (label, dotColor) = when (assignedCategory) {
        AppCategory.DISTRACTING -> "Distracting" to colors.distractingViolet
        AppCategory.NEUTRAL -> "Neutral" to colors.neutralBlue
        AppCategory.PRODUCTIVE -> "Productive" to colors.productiveMint
        null -> "À classer" to colors.mutedInk // Décision #21
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(dotColor.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = FocusTheme.typography.monoTag.copy(fontSize = 10.sp),
            color = dotColor
        )
    }
}

// ==================== 4.19 COMPOSANT HEATMAP MENSUELLE 7 COLONNES ====================

@Composable
private fun MonthlyHeatmapCard(
    grid: MonthlyHeatmapGrid,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val monthTitle = remember(grid.yearMonth) {
        grid.yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH))
            .replaceFirstChar { it.uppercase() }
    }

    ElevatedCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("HEATMAP DES SÉRIES", style = typo.monoTag, color = colors.mutedInk)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("‹", modifier = Modifier.clickable { onPrevMonth() }.padding(4.dp), color = colors.ink)
                Text(monthTitle, style = typo.monoMetric, color = colors.electricAzureStart)
                Text("›", modifier = Modifier.clickable { onNextMonth() }.padding(4.dp), color = colors.ink)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // En-tête 7 colonnes Lundi -> Dimanche
        val dayHeaders = listOf("L", "M", "M", "J", "V", "S", "D")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayHeaders.forEach { d ->
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(d, style = typo.monoTag, color = colors.mutedInk)
                }
            }
        }

        val totalSlots = grid.leadingEmptyCells + grid.dayCells.size
        val rowsCount = (totalSlots + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until rowsCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (col in 0..6) {
                        val slotIdx = row * 7 + col
                        val cellIdx = slotIdx - grid.leadingEmptyCells
                        val cell = grid.dayCells.getOrNull(cellIdx)
                        if (cell == null) {
                            Spacer(modifier = Modifier.size(34.dp))
                        } else {
                            val bgColor = heatmapColorForLevel(cell.intensityLevel, colors)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .border(
                                        width = if (cell.isStreakValidated) 1.5.dp else 1.dp,
                                        color = if (cell.isStreakValidated) colors.mintClarityStart else colors.steelLine,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${cell.date.dayOfMonth}",
                                    style = typo.monoTag.copy(fontSize = 10.sp),
                                    color = if (cell.intensityLevel >= 3) colors.nightAbyss else colors.ink
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Légende des 5 niveaux (0, 1-29, 30-59, 60-119, >=120) + repère menthe
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val legendLevels = listOf(0 to "0m", 1 to "1-29", 2 to "30-59", 3 to "60-119", 4 to "120m+")
            legendLevels.forEach { (lvl, lbl) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatmapColorForLevel(lvl, colors))
                    )
                    Text(lbl, style = typo.monoTag.copy(fontSize = 9.sp), color = colors.mutedInk)
                }
            }
        }
    }
}

@Composable
private fun heatmapColorForLevel(
    level: Int,
    colors: com.example.focuslock.ui.theme.FocusColors
): Color = when (level) {
    0 -> colors.deepSlate
    1 -> colors.electricAzureStart.copy(alpha = 0.25f)
    2 -> colors.electricAzureStart.copy(alpha = 0.50f)
    3 -> colors.electricAzureStart.copy(alpha = 0.80f)
    else -> colors.mintClarityStart
}
