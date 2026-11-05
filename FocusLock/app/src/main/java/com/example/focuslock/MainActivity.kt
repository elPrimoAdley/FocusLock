package com.example.focuslock

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

// Palette issue du Design System CSS & des références Opal/AppBlock
val Night = Color(0xFF050505)
val SurfaceDark = Color(0xFF121723)
val Surface2 = Color(0xFF1C2333)
val Surface3 = Color(0xFF263044)
val LineColor = Color(0xFF283246)
val Ink = Color(0xFFF5F1E8)
val InkMuted = Color(0xFF94A0B8)
val Dawn1 = Color(0xFFFFC773)
val Dawn2 = Color(0xFFFF8B5E)
val Clarity = Color(0xFF6FE6C4)
val AccentBlue = Color(0xFF3B82F6)
val AccentPurple = Color(0xFF8B5CF6)

data class AppRuleItem(
    val name: String,
    val packageName: String,
    val badgeColor: Color,
    val badgeText: Color
)

data class RoutineTask(
    val id: Int,
    val title: String,
    val subtitle: String,
    val done: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Démarre le service de surveillance en arrière-plan
        val serviceIntent = Intent(this, FocusLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Night,
                    surface = SurfaceDark,
                    primary = Dawn2,
                    secondary = Clarity
                )
            ) {
                FocusLockApp()
            }
        }
    }
}

@Composable
fun FocusLockApp() {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) }

    // Vérification périodique de l'état des permissions et du minuteur
    var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasUsagePerm by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var activeRemainingSec by remember { mutableLongStateOf(FocusLockService.remainingSeconds) }
    var isFocusRunning by remember { mutableStateOf(FocusLockService.isFocusActive) }

    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPerm = Settings.canDrawOverlays(context)
            hasUsagePerm = checkUsageStatsPermission(context)
            activeRemainingSec = FocusLockService.remainingSeconds
            isFocusRunning = FocusLockService.isFocusActive
            delay(1000L)
        }
    }

    Scaffold(
        containerColor = Night,
        bottomBar = {
            BottomNavBar(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Night)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> HomeFocusScreen(
                    hasOverlayPerm = hasOverlayPerm,
                    hasUsagePerm = hasUsagePerm,
                    isFocusRunning = isFocusRunning,
                    activeRemainingSec = activeRemainingSec
                )
                1 -> RulesAndDetoxScreen()
                2 -> InsightsScreen(hasUsagePerm = hasUsagePerm)
                3 -> StrictModePinScreen()
            }
        }
    }
}

// ==================== ONGLET 1 : ACCUEIL & MINUTEUR FOCUS ====================
@Composable
fun HomeFocusScreen(
    hasOverlayPerm: Boolean,
    hasUsagePerm: Boolean,
    isFocusRunning: Boolean,
    activeRemainingSec: Long
) {
    val context = LocalContext.current
    var selectedMinutes by remember { mutableIntStateOf(45) }
    val presetMinutes = listOf(1, 15, 30, 45, 60, 90)

    var routineTasks by remember {
        mutableStateOf(
            listOf(
                RoutineTask(1, "Hydratation & Lumière", "Boire 500ml d'eau au réveil", true),
                RoutineTask(2, "Apprentissage de langue", "Session sur tablette / livre hors écran", false),
                RoutineTask(3, "Exercice physique", "20 pompes ou étirements", false)
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // En-tête + Badge Streak
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FocusLock",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                    Text(
                        text = "Contrat de discipline quotidienne",
                        fontSize = 13.sp,
                        color = InkMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface2)
                        .border(1.dp, LineColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "🔥 7 JOURS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Dawn1
                    )
                }
            }
        }

        // Bannière d'activation des permissions pour le test en direct
        if (!hasOverlayPerm || !hasUsagePerm) {
            item {
                PermissionSetupCard(
                    hasOverlayPerm = hasOverlayPerm,
                    hasUsagePerm = hasUsagePerm
                )
            }
        }

        // Carte Objectif du Jour (Hero Card)
        item {
            val completedCount = routineTasks.count { it.done }
            val progress by animateFloatAsState(
                targetValue = completedCount.toFloat() / routineTasks.size.toFloat(),
                label = "routine_progress"
            )

            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, LineColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "OBJECTIF DU JOUR",
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Routine & Apprentissage",
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            color = Ink
                        )
                        Text(
                            text = "$completedCount / ${routineTasks.size} complétés",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Clarity
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Surface3)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(Dawn1, Dawn2)))
                        )
                    }
                }
            }
        }

        // Anneau Central du Minuteur (Ring Timer)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val sweep = if (isFocusRunning) {
                    (activeRemainingSec.toFloat() / (selectedMinutes * 60f)).coerceIn(0.05f, 1f) * 360f
                } else {
                    (selectedMinutes.toFloat() / 90f).coerceIn(0.1f, 1f) * 360f
                }

                Canvas(modifier = Modifier.size(210.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    drawArc(
                        color = Surface2,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Dawn1, Dawn2, Dawn1)),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isFocusRunning) "VERROUILLÉ" else "DURÉE DU CONTRAT",
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = if (isFocusRunning) Clarity else InkMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFocusRunning) {
                            String.format("%02d:%02d", activeRemainingSec / 60, activeRemainingSec % 60)
                        } else {
                            "$selectedMinutes min"
                        },
                        fontFamily = FontFamily.Serif,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFocusRunning) "Session en cours" else "Mode Focus Strict",
                        fontSize = 12.sp,
                        color = InkMuted
                    )
                }
            }
        }

        // Sélecteur rapide de durée (inclut 1 min pour tester en live rapidement)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetMinutes.forEach { mins ->
                    val isSelected = selectedMinutes == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Dawn2.copy(alpha = 0.22f) else SurfaceDark)
                            .border(
                                1.dp,
                                if (isSelected) Dawn2 else LineColor,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMinutes = mins }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Dawn1 else InkMuted
                        )
                    }
                }
            }
        }

        // Bouton principal d'action (Démarrer le Focus + Test immédiat de l'Overlay)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Dawn1, Dawn2)))
                        .clickable {
                            if (!Settings.canDrawOverlays(context)) {
                                Toast.makeText(
                                    context,
                                    "Active d'abord la permission 'Superposition' ci-dessus",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val intent = Intent(context, FocusLockService::class.java).apply {
                                    action = FocusLockService.ACTION_START_FOCUS
                                    putExtra(FocusLockService.EXTRA_DURATION_MINUTES, selectedMinutes)
                                }
                                context.startService(intent)
                            }
                        }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DÉMARRER LE FOCUS ($selectedMinutes MIN)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF2E1607)
                    )
                }

                // Bouton de test instantané de l'écran de blocage
                OutlinedButton(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, FocusLockService::class.java).apply {
                                action = FocusLockService.ACTION_TEST_OVERLAY
                            }
                            context.startService(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LineColor)
                ) {
                    Text(
                        text = "Tester l'écran de verrouillage maintenant (PIN: 4289)",
                        color = InkMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Section Morning Routine Checklist
        item {
            Text(
                text = "CONTRATS & MORNING ROUTINE",
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items(routineTasks) { task ->
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LineColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        routineTasks = routineTasks.map {
                            if (it.id == task.id) it.copy(done = !it.done) else it
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (task.done) Clarity else Color.Transparent)
                            .border(1.5.dp, if (task.done) Clarity else InkMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.done) {
                            Text("✓", color = Color(0xFF0F2E25), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Ink
                        )
                        Text(
                            text = task.subtitle,
                            fontSize = 12.sp,
                            color = InkMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (task.done) Clarity.copy(alpha = 0.14f) else Surface3)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (task.done) "Fait" else "En attente",
                            fontSize = 11.sp,
                            color = if (task.done) Clarity else InkMuted
                        )
                    }
                }
            }
        }
    }
}

// ==================== ONGLET 2 : RÈGLES, DÉTOX 1J/2 & PAUSE SABBATIQUE ====================
@Composable
fun RulesAndDetoxScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }

    val targetApps = remember {
        listOf(
            AppRuleItem("Instagram", "com.instagram.android", Color(0xFF3A2A55), Color(0xFFD9B8FF)),
            AppRuleItem("TikTok", "com.zhiliaoapp.musically", Color(0xFF1F3A38), Color(0xFF7FE6C9)),
            AppRuleItem("YouTube", "com.google.android.youtube", Color(0xFF3A2A24), Color(0xFFFFB48A)),
            AppRuleItem("Chrome", "com.android.chrome", Color(0xFF1E293B), Color(0xFF93C5FD)),
            AppRuleItem("X / Twitter", "com.twitter.android", Color(0xFF27272A), Color(0xFFE4E4E7))
        )
    }

    var rulesMap by remember {
        mutableStateOf(
            targetApps.associate { app ->
                app.packageName to (prefs.getString("rule_${app.packageName}", "NONE") ?: "NONE")
            }
        )
    }

    fun updateRule(pkg: String, newMode: String) {
        prefs.edit().apply {
            putString("rule_$pkg", newMode)
            if (newMode == "SABBATICAL_7D") {
                putLong("sabbatical_until_$pkg", System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
            }
            apply()
        }
        rulesMap = rulesMap.toMutableMap().apply { put(pkg, newMode) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Règles & Détox Dopamine",
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                color = Ink
            )
            Text(
                text = "Configure le blocage permanent, la détox 1j/2 ou une pause sabbatique.",
                fontSize = 13.sp,
                color = InkMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        items(targetApps) { app ->
            val currentMode = rulesMap[app.packageName] ?: "NONE"

            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LineColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(app.badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.name.take(2).uppercase(),
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = app.badgeText,
                                fontSize = 13.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                            Text(
                                text = when (currentMode) {
                                    "ALTERNATING_1_2" -> "Mode Détox : Autorisé 1 jour sur 2"
                                    "SABBATICAL_7D" -> "Pause Sabbatique : Bloqué 7 jours"
                                    "ALWAYS_BLOCKED" -> "Verrouillage strict 24h/24"
                                    else -> "Aucune restriction active"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (currentMode == "NONE") InkMuted else Dawn1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sélecteur de mode de blocage (incluant Détox 1j/2 et Pause Sabbatique)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            "NONE" to "Libre",
                            "ALTERNATING_1_2" to "1j / 2",
                            "SABBATICAL_7D" to "Pause 7j",
                            "ALWAYS_BLOCKED" to "Bloqué"
                        )
                        modes.forEach { (modeKey, label) ->
                            val active = currentMode == modeKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Dawn2.copy(alpha = 0.25f) else Surface2)
                                    .border(
                                        1.dp,
                                        if (active) Dawn2 else LineColor,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { updateRule(app.packageName, modeKey) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) Dawn1 else InkMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== ONGLET 3 : INSIGHTS & TEMPS D'ÉCRAN RÉEL ====================
@Composable
fun InsightsScreen(hasUsagePerm: Boolean) {
    val context = LocalContext.current
    val usageList = remember(hasUsagePerm) {
        if (hasUsagePerm) queryTodayTopApps(context) else emptyList()
    }
    val totalMinutes = usageList.sumOf { it.second }.coerceAtLeast(45)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("AUJOURD'HUI", fontSize = 12.sp, letterSpacing = 1.4.sp, color = AccentBlue)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${totalMinutes / 60}h ${totalMinutes % 60}m",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text("TEMPS D'ÉCRAN MESURÉ", fontSize = 11.sp, color = InkMuted)
            }
        }

        // Graphique en barres inspiré de la référence Insights
        item {
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, LineColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("ACTIVITÉ PAR TRANCHE HORAIRE", fontSize = 11.sp, color = InkMuted)
                    Spacer(modifier = Modifier.height(16.dp))
                    val bars = listOf(0.35f, 0.1f, 0.0f, 0.25f, 0.65f, 0.85f, 0.4f, 0.55f, 0.3f, 0.7f, 0.45f, 0.2f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        bars.forEachIndexed { idx, ratio ->
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Surface2),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(ratio.coerceAtLeast(0.06f))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                if (idx % 2 == 0) listOf(AccentPurple, AccentBlue)
                                                else listOf(Dawn1, Dawn2)
                                            )
                                        )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("00h", fontSize = 10.sp, color = InkMuted)
                        Text("06h", fontSize = 10.sp, color = InkMuted)
                        Text("12h", fontSize = 10.sp, color = InkMuted)
                        Text("18h", fontSize = 10.sp, color = InkMuted)
                    }
                }
            }
        }

        item {
            Text("APPLICATIONS LES PLUS UTILISÉES", fontSize = 11.sp, letterSpacing = 1.1.sp, color = InkMuted)
        }

        if (usageList.isEmpty()) {
            item {
                Text(
                    text = "Accorde la permission 'Accès aux données d'utilisation' sur l'accueil pour voir tes vraies statistiques d'applications en direct.",
                    fontSize = 13.sp,
                    color = InkMuted
                )
            }
        } else {
            items(usageList) { (pkgName, mins) ->
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LineColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink
                            )
                            Text(pkgName, fontSize = 11.sp, color = InkMuted)
                        }
                        Text(
                            text = "${mins}m",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Dawn1
                        )
                    }
                }
            }
        }
    }
}

// ==================== ONGLET 4 : MODE STRICT & CODE PIN ====================
@Composable
fun StrictModePinScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }

    var mindfulEdits by remember { mutableStateOf(prefs.getBoolean("mindful_edits", true)) }
    var blockFloating by remember { mutableStateOf(prefs.getBoolean("block_floating", true)) }
    var secureRestart by remember { mutableStateOf(prefs.getBoolean("secure_restart", true)) }
    var enteredPin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Préférences & Mode Strict",
                fontFamily = FontFamily.Serif,
                fontSize = 24.sp,
                color = Ink
            )
            Text(
                text = "Protection anti-triche et code PIN indépendant (ni biométrie, ni code téléphone).",
                fontSize = 13.sp,
                color = InkMuted
            )
        }

        // Options inspirées de l'écran Blocking Preferences
        item {
            StrictPreferenceToggle(
                title = "Bloquer les fenêtres flottantes",
                subtitle = "Empêche l'ouverture des applications bloquées en mode pop-up",
                checked = blockFloating,
                onCheckedChange = {
                    blockFloating = it
                    prefs.edit().putBoolean("block_floating", it).apply()
                }
            )
        }

        item {
            StrictPreferenceToggle(
                title = "Modifications conscientes (Mindful edits)",
                subtitle = "Impose un délai de réflexion de 10s avant de modifier une règle",
                checked = mindfulEdits,
                onCheckedChange = {
                    mindfulEdits = it
                    prefs.edit().putBoolean("mindful_edits", it).apply()
                }
            )
        }

        item {
            StrictPreferenceToggle(
                title = "Verrouillage après redémarrage",
                subtitle = "Relance automatiquement le bouclier au démarrage du téléphone",
                checked = secureRestart,
                onCheckedChange = {
                    secureRestart = it
                    prefs.edit().putBoolean("secure_restart", it).apply()
                }
            )
        }

        // Pavé numérique pour définir le Code PIN Indépendant (SCREEN 5 : PIN)
        item {
            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, LineColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Définir le Code PIN Indépendant",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        color = Ink
                    )
                    Text(
                        text = "Requis pour annuler un blocage d'urgence",
                        fontSize = 12.sp,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )

                    // Points du PIN (pin-dots)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(4) { index ->
                            val filled = index < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) Dawn2 else Color.Transparent)
                                    .border(1.5.dp, if (filled) Dawn2 else InkMuted, CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )

                    keys.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Surface2)
                                        .clickable {
                                            when (key) {
                                                "C" -> enteredPin = ""
                                                "OK" -> {
                                                    if (enteredPin.length == 4) {
                                                        prefs.edit().putString("strict_pin", enteredPin).apply()
                                                        Toast.makeText(
                                                            context,
                                                            "Nouveau PIN enregistré : $enteredPin",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        enteredPin = ""
                                                    }
                                                }
                                                else -> if (enteredPin.length < 4) enteredPin += key
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp,
                                        color = Ink
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrictPreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LineColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = InkMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentBlue,
                    uncheckedThumbColor = InkMuted,
                    uncheckedTrackColor = Surface3
                )
            )
        }
    }
}

// ==================== CARTE D'ACTIVATION RAPIDE DES PERMISSIONS ====================
@Composable
fun PermissionSetupCard(hasOverlayPerm: Boolean, hasUsagePerm: Boolean) {
    val context = LocalContext.current
    Surface(
        color = Color(0xFF1F1924),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Dawn2.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "⚡ Configuration requise pour le blocage en direct",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Dawn1
            )
            if (!hasOverlayPerm) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Dawn2),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. Autoriser 'Afficher par-dessus d'autres applis'", color = Color(0xFF2E1607), fontSize = 12.sp)
                }
            }
            if (!hasUsagePerm) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Surface3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2. Autoriser 'Accès aux données d'utilisation'", color = Ink, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==================== BARRE DE NAVIGATION INFÉRIEURE ====================
@Composable
fun BottomNavBar(currentTab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        "Focus" to "◎",
        "Détox & Règles" to "🛡",
        "Insights" to "📊",
        "Mode Strict" to "🔒"
    )
    Surface(
        color = SurfaceDark.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, LineColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEachIndexed { index, (label, symbol) ->
                val active = currentTab == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = symbol,
                        fontSize = 16.sp,
                        color = if (active) Dawn2 else InkMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Dawn2 else InkMuted
                    )
                }
            }
        }
    }
}

// ==================== UTILITAIRES SYSTÈME ANDROID ====================
fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun queryTodayTopApps(context: Context): List<Pair<String, Int>> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    val stats = usm.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        cal.timeInMillis,
        System.currentTimeMillis()
    )
    return stats
        ?.filter { it.totalTimeInForeground > 60_000L }
        ?.map { it.packageName to (it.totalTimeInForeground / 60_000L).toInt() }
        ?.sortedByDescending { it.second }
        ?.take(6)
        ?: emptyList()
}