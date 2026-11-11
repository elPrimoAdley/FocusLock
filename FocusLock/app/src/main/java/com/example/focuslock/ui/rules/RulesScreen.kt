package com.example.focuslock.ui.rules

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.focuslock.BuildConfig
import com.example.focuslock.domain.BlockRule
import com.example.focuslock.domain.DaysBitmask
import com.example.focuslock.domain.RuleType
import com.example.focuslock.engine.QuotaRingStatus
import com.example.focuslock.engine.RuleFormatterAndDetoxCalculator
import com.example.focuslock.ui.components.AppSelectorCard
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.components.FocusModalSheet
import com.example.focuslock.ui.components.FocusSwitch
import com.example.focuslock.ui.components.MintPillButton
import com.example.focuslock.ui.navigation.Destination
import com.example.focuslock.ui.navigation.TopSectionBar
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditorSheet by remember { mutableStateOf(false) }
    var ruleBeingEdited by remember { mutableStateOf<BlockRule?>(null) }

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
            TopSectionBar(title = Destination.Rules.title, onOpenDrawer = onOpenDrawer)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 3.18 : Indicateur "Quota réduit : -50 %" si un malus est actif aujourd'hui
                if (uiState.activeQuotaMalusPercent > 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.softCoral.copy(alpha = 0.15f))
                                .border(1.dp, colors.softCoral, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("⚠", style = typo.screenTitle, color = colors.softCoral)
                                Column {
                                    Text(
                                        text = "QUOTA RÉDUIT : −${uiState.activeQuotaMalusPercent} % AUJOURD'HUI",
                                        style = typo.monoTag,
                                        color = colors.softCoral
                                    )
                                    Text(
                                        text = "Tes plafonds journaliers sont automatiquement amputés suite aux échéances dépassées.",
                                        style = typo.bodySecondary,
                                        color = colors.ink
                                    )
                                }
                            }
                        }
                    }
                }

                // Bandeau Debug Bypass EditGuard (uniquement en BuildConfig.DEBUG)
                val bypass = uiState.pendingDebugBypass
                if (BuildConfig.DEBUG && bypass != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.softCoral.copy(alpha = 0.16f))
                                .border(1.dp, colors.softCoral, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "🛠 BUILD DEBUG — EDITGUARD DÉCLENCHÉ",
                                    style = typo.monoTag,
                                    color = colors.softCoral
                                )
                                Text(bypass.description, style = typo.bodySecondary, color = colors.ink)
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        text = "Forcer (Debug)",
                                        style = typo.monoMetric,
                                        color = colors.mintClarityStart,
                                        modifier = Modifier.clickable { bypass.onConfirmBypass() }
                                    )
                                    Text(
                                        text = "Annuler",
                                        style = typo.monoMetric,
                                        color = colors.mutedInk,
                                        modifier = Modifier.clickable { viewModel.dismissDebugBypass() }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    MintPillButton(
                        text = "+ Nouvelle Règle / Détox",
                        onClick = {
                            ruleBeingEdited = null
                            showEditorSheet = true
                        }
                    )
                }

                if (uiState.rules.isEmpty()) {
                    item {
                        ElevatedCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🛡", fontSize = 30.sp)
                                Text(
                                    text = "Aucune règle active",
                                    style = typo.cardTitleSerif,
                                    color = colors.ink
                                )
                                Text(
                                    text = "Configure une Plage Horaire (couvre-feu 22:30–07:00), un Quota Journalier partagé, une Détox 1j/2 ou une Pause Sabbatique.",
                                    style = typo.bodySecondary,
                                    color = colors.mutedInk,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.rules, key = { it.rule.id }) { cardModel ->
                        RuleItemCard(
                            model = cardModel,
                            onToggleEnabled = { enabled ->
                                viewModel.toggleRuleEnabled(cardModel.rule, enabled)
                            },
                            onEdit = {
                                ruleBeingEdited = cardModel.rule
                                showEditorSheet = true
                            },
                            onDelete = {
                                viewModel.requestDeleteRule(cardModel.rule)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        RuleEditorModalSheet(
            existingRule = ruleBeingEdited,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showEditorSheet = false },
            onSave = { draft ->
                viewModel.saveRule(
                    existingRule = ruleBeingEdited,
                    draftRule = draft,
                    onSavedSuccess = { showEditorSheet = false }
                )
            },
            onDelete = ruleBeingEdited?.let { rule ->
                {
                    showEditorSheet = false
                    viewModel.requestDeleteRule(rule)
                }
            }
        )
    }
}

@Composable
private fun RuleItemCard(
    model: RuleCardUiModel,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val rule = model.rule

    ElevatedCard(onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (model.quotaRingStatus != null) {
                QuotaRingMini(status = model.quotaRingStatus)
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.deepSlate)
                        .border(1.dp, colors.steelLine, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val iconSymbol = when (rule.ruleType) {
                        RuleType.SCHEDULE -> "🕒"
                        RuleType.ALTERNATING_DETOX -> "🌿"
                        RuleType.SABBATICAL -> "🔒"
                        else -> "🛡"
                    }
                    Text(iconSymbol, fontSize = 20.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rule.title,
                        style = typo.bodyPrimary,
                        color = if (rule.isEnabled) colors.ink else colors.mutedInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.isStrictLocked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.electricAzureStart.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("STRICT", style = typo.monoTag.copy(fontSize = 9.sp), color = colors.electricAzureStart)
                        }
                    }
                }

                Text(
                    text = model.formattedSummary,
                    style = typo.monoMetric.copy(fontSize = 12.sp),
                    color = colors.electricAzureStart
                )

                if (model.quotaRingStatus != null) {
                    val qs = model.quotaRingStatus
                    val quotaDetail = if (qs.malusReductionPercent > 0) {
                        "Reste ${qs.remainingMinutes}m / ${qs.effectiveQuotaMinutes}m (base ${qs.baseQuotaMinutes}m −${qs.malusReductionPercent}%)"
                    } else {
                        "Reste ${qs.remainingMinutes}m sur ${qs.effectiveQuotaMinutes}m (${qs.usedMinutes}m consommées)"
                    }
                    Text(
                        text = quotaDetail,
                        style = typo.monoTag,
                        color = if (qs.remainingMinutes == 0) colors.softCoral else colors.mintClarityStart
                    )
                }
            }

            FocusSwitch(
                checked = rule.isEnabled,
                onCheckedChange = onToggleEnabled
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                model.targetAppLabels.take(3).forEach { label ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.deepSlate)
                            .border(1.dp, colors.steelLine, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = typo.monoTag.copy(fontSize = 10.sp),
                            color = colors.ink,
                            maxLines = 1
                        )
                    }
                }
                if (model.targetAppLabels.size > 3) {
                    Text(
                        text = "+${model.targetAppLabels.size - 3}",
                        style = typo.monoTag,
                        color = colors.mutedInk,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            Text(
                text = "Suppr.",
                style = typo.monoTag,
                color = colors.softCoral,
                modifier = Modifier
                    .clickable { onDelete() }
                    .padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun QuotaRingMini(
    status: QuotaRingStatus,
    modifier: Modifier = Modifier
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val isExhausted = status.remainingMinutes <= 0

    Box(
        modifier = modifier.size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            val strokePx = 5.dp.toPx()
            drawArc(
                color = colors.deepSlate,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx)
            )
            drawArc(
                color = if (isExhausted) colors.softCoral else colors.mintClarityStart,
                startAngle = -90f,
                sweepAngle = (1f - status.usedFraction).coerceIn(0.04f, 1f) * 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "${status.remainingMinutes}m",
            style = typo.monoTag.copy(fontSize = 10.sp),
            color = if (isExhausted) colors.softCoral else colors.ink
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorModalSheet(
    existingRule: BlockRule?,
    uiState: RulesUiState,
    viewModel: RulesViewModel,
    onDismiss: () -> Unit,
    onSave: (BlockRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val today = remember(uiState.nowInstant) { uiState.nowInstant.atZone(uiState.zoneId).toLocalDate() }

    val availableRuleTypes = listOf(
        RuleType.SCHEDULE to "Plage Horaire",
        RuleType.DAILY_QUOTA to "Quota / Jour",
        RuleType.ALTERNATING_DETOX to "Détox 1j/N",
        RuleType.SABBATICAL to "Sabbatique"
    )

    var selectedType by remember { mutableStateOf(existingRule?.ruleType ?: RuleType.SCHEDULE) }
    var title by remember { mutableStateOf(existingRule?.title ?: "Couvre-feu Réseaux") }
    var selectedPackages by remember { mutableStateOf(existingRule?.targetPackages ?: emptySet()) }
    var isStrictLocked by remember { mutableStateOf(existingRule?.isStrictLocked ?: true) }
    var customMessage by remember { mutableStateOf(existingRule?.customBlockMessage ?: "") }

    var startMinutes by remember { mutableIntStateOf(existingRule?.startTimeMinutes ?: (22 * 60 + 30)) }
    var endMinutes by remember { mutableIntStateOf(existingRule?.endTimeMinutes ?: (7 * 60)) }
    var daysBitmask by remember { mutableIntStateOf(existingRule?.activeDaysBitmask ?: DaysBitmask.ALL_DAYS) }
    var editingStartClock by remember { mutableStateOf<Boolean?>(null) }

    var dailyQuotaMinutes by remember { mutableIntStateOf(existingRule?.dailyQuotaMinutes ?: 30) }

    var cycleIntervalDays by remember { mutableIntStateOf(existingRule?.cycleIntervalDays ?: 2) }
    var cycleStartEpochDay by remember {
        mutableLongStateOf(existingRule?.cycleStartEpochDay ?: today.minusDays(1).toEpochDay())
    }

    var pauseUntilInstant by remember {
        mutableStateOf(existingRule?.pauseUntil ?: uiState.nowInstant.plusSeconds(7L * 86_400L))
    }
    var showSabbaticalDatePicker by remember { mutableStateOf(false) }

    FocusModalSheet(
        title = if (existingRule == null) "Nouvelle Règle" else "Modifier la Règle",
        onDismissRequest = onDismiss,
        rightActionSymbol = if (onDelete != null) "🗑" else "🛡",
        onRightActionClick = onDelete,
        onSaveClick = {
            val draft = BlockRule(
                id = existingRule?.id ?: "draft",
                title = title,
                ruleType = selectedType,
                targetPackages = selectedPackages,
                isEnabled = existingRule?.isEnabled ?: true,
                isStrictLocked = isStrictLocked,
                activeDaysBitmask = daysBitmask,
                startTimeMinutes = if (selectedType == RuleType.SCHEDULE) startMinutes else null,
                endTimeMinutes = if (selectedType == RuleType.SCHEDULE) endMinutes else null,
                dailyQuotaMinutes = if (selectedType == RuleType.DAILY_QUOTA) dailyQuotaMinutes else null,
                cycleIntervalDays = if (selectedType == RuleType.ALTERNATING_DETOX) cycleIntervalDays else null,
                cycleStartEpochDay = if (selectedType == RuleType.ALTERNATING_DETOX) cycleStartEpochDay else null,
                pauseUntil = if (selectedType == RuleType.SABBATICAL) pauseUntilInstant else null,
                customBlockMessage = customMessage.trim().takeIf { it.isNotEmpty() }
            )
            onSave(draft)
        }
    ) {
        ElevatedCard {
            Text("FAMILLE DE RESTRICTION", style = typo.monoTag, color = colors.mutedInk)
            Spacer(modifier = Modifier.height(10.dp))
            availableRuleTypes.chunked(2).forEach { rowTypes ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTypes.forEach { (type, label) ->
                        val active = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) colors.deepSlate else colors.nightAbyss)
                                .border(
                                    1.dp,
                                    if (active) colors.electricAzureStart else colors.steelLine,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = existingRule == null) { selectedType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = typo.bodySecondary,
                                color = if (active) colors.electricAzureStart else colors.mutedInk
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nom de la règle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        when (selectedType) {
            RuleType.SCHEDULE -> {
                ElevatedCard {
                    Text("3.17 — PLAGE HORAIRE (TRAVERSE MINUIT SUPPORTÉ)", style = typo.monoTag, color = colors.mutedInk)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.deepSlate)
                                .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                                .clickable { editingStartClock = true }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Début : ${RuleFormatterAndDetoxCalculator.formatMinutesHHMM(startMinutes)}",
                                style = typo.monoMetric,
                                color = colors.ink
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.deepSlate)
                                .border(1.dp, colors.steelLine, RoundedCornerShape(12.dp))
                                .clickable { editingStartClock = false }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Fin : ${RuleFormatterAndDetoxCalculator.formatMinutesHHMM(endMinutes)}",
                                style = typo.monoMetric,
                                color = colors.electricAzureStart
                            )
                        }
                    }
                    if (endMinutes < startMinutes) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🌙 Plage nocturne traversant minuit : les pastilles L–D désignent le soir où le couvre-feu commence.",
                            style = typo.bodySecondary,
                            color = colors.mintClarityStart
                        )
                    }
                }
                DaysBitmaskCard(daysBitmask = daysBitmask, onBitmaskChange = { daysBitmask = it })
            }

            RuleType.DAILY_QUOTA -> {
                ElevatedCard {
                    Text("3.18 — QUOTA JOURNALIER (POOL PARTAGÉ)", style = typo.monoTag, color = colors.mutedInk)
                    Spacer(modifier = Modifier.height(10.dp))
                    val presets = listOf(10, 15, 30, 45, 60, 90)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { mins ->
                            val selected = dailyQuotaMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.deepSlate else colors.nightAbyss)
                                    .border(
                                        1.dp,
                                        if (selected) colors.mintClarityStart else colors.steelLine,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { dailyQuotaMinutes = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}m",
                                    style = typo.monoMetric,
                                    color = if (selected) colors.mintClarityStart else colors.mutedInk
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Si plusieurs applications sont cochées, ces $dailyQuotaMinutes min constituent un pool partagé entre elles.",
                        style = typo.bodySecondary,
                        color = colors.mutedInk
                    )
                }
                DaysBitmaskCard(daysBitmask = daysBitmask, onBitmaskChange = { daysBitmask = it })
            }

            RuleType.ALTERNATING_DETOX -> {
                ElevatedCard {
                    Text("3.19 — DÉTOX INTERMITTENTE (ALTERNANCE)", style = typo.monoTag, color = colors.mutedInk)
                    Spacer(modifier = Modifier.height(10.dp))

                    val detoxPresets = listOf(
                        "1j sur 2 (Auj. OFF)" to {
                            cycleIntervalDays = 2
                            cycleStartEpochDay = today.minusDays(1).toEpochDay()
                        },
                        "1j sur 2 (Auj. ON)" to {
                            cycleIntervalDays = 2
                            cycleStartEpochDay = today.toEpochDay()
                        },
                        "1j sur 3" to {
                            cycleIntervalDays = 3
                            cycleStartEpochDay = today.minusDays(1).toEpochDay()
                        },
                        "Uniquement le Samedi" to {
                            cycleIntervalDays = 7
                            cycleStartEpochDay =
                                RuleFormatterAndDetoxCalculator.saturdayOnlyCycleStartEpochDay(today)
                        }
                    )

                    detoxPresets.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { (label, applyPreset) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.deepSlate)
                                        .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                        .clickable { applyPreset() }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, style = typo.monoTag, color = colors.ink, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val previewRule = BlockRule(
                        id = "preview",
                        title = title,
                        ruleType = RuleType.ALTERNATING_DETOX,
                        targetPackages = emptySet(),
                        cycleIntervalDays = cycleIntervalDays,
                        cycleStartEpochDay = cycleStartEpochDay
                    )
                    val reopen = RuleFormatterAndDetoxCalculator.nextReopenDate(previewRule, today)
                    val statusText = if (reopen != null && reopen.isAfter(today)) {
                        "État actuel : VERROUILLÉ jusqu'au ${reopen.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} à 00:00"
                    } else {
                        "État actuel : JOUR ON (Autorisé aujourd'hui)"
                    }
                    Text(
                        text = statusText,
                        style = typo.monoMetric,
                        color = if (reopen != null && reopen.isAfter(today)) colors.softCoral else colors.mintClarityStart
                    )
                }
            }

            RuleType.SABBATICAL -> {
                ElevatedCard {
                    Text("3.20 — PAUSE SABBATIQUE (QUARANTAINE)", style = typo.monoTag, color = colors.mutedInk)
                    Spacer(modifier = Modifier.height(10.dp))
                    val sabPresets = listOf(
                        "3 Jours" to 3L,
                        "1 Semaine" to 7L,
                        "1 Mois (30j)" to 30L
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sabPresets.forEach { (label, days) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.deepSlate)
                                    .border(1.dp, colors.steelLine, RoundedCornerShape(10.dp))
                                    .clickable {
                                        pauseUntilInstant = uiState.nowInstant.plusSeconds(days * 86_400L)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, style = typo.monoTag, color = colors.ink)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.deepSlate)
                            .border(1.dp, colors.electricAzureStart, RoundedCornerShape(12.dp))
                            .clickable { showSabbaticalDatePicker = true }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val formattedUntil = pauseUntilInstant.atZone(uiState.zoneId)
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        Text(
                            text = "📅 Date de fin personnalisée : $formattedUntil",
                            style = typo.monoMetric,
                            color = colors.electricAzureStart
                        )
                    }
                }
            }

            else -> Unit
        }

        ElevatedCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Verrouillage Strict", style = typo.bodyPrimary, color = colors.ink)
                    Text(
                        "Empêche toute modification ou pause impulsive",
                        style = typo.bodySecondary,
                        color = colors.mutedInk
                    )
                }
                FocusSwitch(checked = isStrictLocked, onCheckedChange = { isStrictLocked = it })
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customMessage,
                onValueChange = { customMessage = it },
                label = { Text("Message bienveillant personnalisé sur l'overlay (optionnel)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        AppSelectorCard(
            apps = uiState.selectableApps,
            selectedPackages = selectedPackages,
            appsLoader = viewModel.appsLoader,
            onTogglePackage = { pkg ->
                selectedPackages = if (pkg in selectedPackages) selectedPackages - pkg else selectedPackages + pkg
            },
            onCycleCategory = { pkg, nextCat ->
                viewModel.updateAppCategory(pkg, nextCat)
            }
        )
    }

    val isStartClock = editingStartClock
    if (isStartClock != null) {
        val currentMins = if (isStartClock) startMinutes else endMinutes
        val pickerState = rememberTimePickerState(
            initialHour = currentMins / 60,
            initialMinute = currentMins % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingStartClock = null },
            containerColor = colors.deepSlate,
            title = {
                Text(
                    text = if (isStartClock) "Heure de début" else "Heure de fin",
                    style = typo.cardTitleSerif,
                    color = colors.ink
                )
            },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mins = pickerState.hour * 60 + pickerState.minute
                        if (isStartClock) startMinutes = mins else endMinutes = mins
                        editingStartClock = null
                    }
                ) {
                    Text("Valider", color = colors.mintClarityStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStartClock = null }) {
                    Text("Annuler", color = colors.mutedInk)
                }
            }
        )
    }

    if (showSabbaticalDatePicker) {
        val initialMillis = pauseUntilInstant.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showSabbaticalDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            pauseUntilInstant = pickedDate.atTime(8, 0).atZone(uiState.zoneId).toInstant()
                        }
                        showSabbaticalDatePicker = false
                    }
                ) {
                    Text("Valider", color = colors.mintClarityStart)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSabbaticalDatePicker = false }) {
                    Text("Annuler", color = colors.mutedInk)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DaysBitmaskCard(
    daysBitmask: Int,
    onBitmaskChange: (Int) -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val dayLabels = listOf("L", "M", "M", "J", "V", "S", "D")

    ElevatedCard {
        Text("JOURS ACTIFS (L–D)", style = typo.monoTag, color = colors.mutedInk)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayLabels.forEachIndexed { idx, label ->
                val bit = 1 shl idx
                val active = (daysBitmask and bit) != 0
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.electricAzureStart else colors.deepSlate)
                        .border(
                            1.dp,
                            if (active) colors.electricAzureEnd else colors.steelLine,
                            CircleShape
                        )
                        .clickable {
                            val next = daysBitmask xor bit
                            if (next != 0) onBitmaskChange(next)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = typo.monoMetric,
                        color = if (active) colors.nightAbyss else colors.mutedInk
                    )
                }
            }
        }
    }
}
