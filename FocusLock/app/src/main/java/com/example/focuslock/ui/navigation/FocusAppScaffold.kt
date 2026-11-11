package com.example.focuslock.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.focuslock.ui.components.ComponentGalleryScreen
import com.example.focuslock.ui.components.ElevatedCard
import com.example.focuslock.ui.hub.HubScreen
import com.example.focuslock.ui.hub.HubViewModel
import com.example.focuslock.ui.quests.QuestsScreen
import com.example.focuslock.ui.theme.FocusTheme
import kotlinx.coroutines.launch

@Composable
fun FocusAppScaffold() {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Hub.route

    fun navigateSingleTop(destination: Destination) {
        scope.launch { drawerState.close() }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = colors.nightAbyss.copy(alpha = 0.72f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.deepSlate,
                drawerContentColor = colors.ink,
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Partie haute : En-tête Grade/XP + 5 items principaux
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // En-tête du Drawer avec Grade & Barre d'XP factices (1.6)
                        ElevatedCard {
                            Column {
                                Text(
                                    text = "NIVEAU 5 • STRUCTURÉ",
                                    style = typo.monoTag,
                                    color = colors.electricAzureStart
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "450 / 600 XP",
                                    style = typo.monoMetric,
                                    color = colors.ink
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.deepSlate)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .fillMaxHeight()
                                        .background(colors.electricAzureGradient)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Destination.mainDrawerDestinations.forEach { dest ->
                            DrawerMenuItem(
                                destination = dest,
                                isSelected = currentRoute == dest.route,
                                onClick = { navigateSingleTop(dest) }
                            )
                        }
                    }

                    // Pied du Drawer : 6e section Profil/Sécurité + accès Galerie Debug
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = colors.steelLine)
                        DrawerMenuItem(
                            destination = Destination.ProfileSecurity,
                            isSelected = currentRoute == Destination.ProfileSecurity.route,
                            onClick = { navigateSingleTop(Destination.ProfileSecurity) }
                        )
                        DrawerMenuItem(
                            destination = Destination.GalleryDebug,
                            isSelected = currentRoute == Destination.GalleryDebug.route,
                            onClick = { navigateSingleTop(Destination.GalleryDebug) }
                        )
                    }
                }
            }
        }
    ) {
        FocusNavHost(
            navController = navController,
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}

@Composable
private fun DrawerMenuItem(
    destination: Destination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = FocusTheme.colors
    val typo = FocusTheme.typography
    val bg = if (isSelected) colors.elevatedCard else colors.deepSlate
    val borderColor = if (isSelected) colors.electricAzureStart else colors.steelLine.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = destination.symbol,
            style = typo.cardTitleSerif,
            color = if (isSelected) colors.electricAzureStart else colors.mutedInk
        )
        Column {
            Text(
                text = destination.title,
                style = typo.bodyPrimary,
                color = if (isSelected) colors.ink else colors.mutedInk
            )
            Text(
                text = destination.subtitle,
                style = typo.bodySecondary,
                color = colors.mutedInk
            )
        }
    }
}

@Composable
private fun FocusNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as com.example.focuslock.FocusLockApplication).container

    NavHost(
        navController = navController,
        startDestination = Destination.Hub.route
    ) {
        composable(Destination.Hub.route) {
            val hubViewModel: HubViewModel = viewModel()
            HubScreen(
                viewModel = hubViewModel,
                onOpenDrawer = onOpenDrawer
            )
        }
        composable(Destination.Quests.route) {
            val questsViewModel: com.example.focuslock.ui.quests.QuestsViewModel = viewModel(
                factory = com.example.focuslock.ui.quests.QuestsViewModel.factory(container)
            )
            QuestsScreen(
                viewModel = questsViewModel,
                onOpenDrawer = onOpenDrawer
            )
        }
        composable(Destination.Rules.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val container = remember {
                (context.applicationContext as com.example.focuslock.FocusLockApplication).container
            }
            val rulesViewModel: com.example.focuslock.ui.rules.RulesViewModel = viewModel(
                factory = com.example.focuslock.ui.rules.RulesViewModel.factory(container)
            )
            com.example.focuslock.ui.rules.RulesScreen(
                viewModel = rulesViewModel,
                onOpenDrawer = onOpenDrawer
            )
        }
        composable(Destination.Vault.route) {
            PlaceholderSectionScreen(destination = Destination.Vault, onOpenDrawer = onOpenDrawer)
        }
        composable(Destination.Insights.route) {
            PlaceholderSectionScreen(destination = Destination.Insights, onOpenDrawer = onOpenDrawer)
        }
        composable(Destination.ProfileSecurity.route) {
            PlaceholderSectionScreen(destination = Destination.ProfileSecurity, onOpenDrawer = onOpenDrawer)
        }
        composable(Destination.GalleryDebug.route) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FocusTheme.colors.nightAbyss)
                    .systemBarsPadding()
            ) {
                TopSectionBar(title = Destination.GalleryDebug.title, onOpenDrawer = onOpenDrawer)
                ComponentGalleryScreen()
            }
        }
    }
}

@Composable
fun TopSectionBar(
    title: String,
    onOpenDrawer: () -> Unit
) {
    val colors = FocusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.elevatedCard)
                .border(1.dp, colors.steelLine, CircleShape)
                .clickable(onClick = onOpenDrawer),
            contentAlignment = Alignment.Center
        ) {
            Text("☰", color = colors.ink, style = FocusTheme.typography.bodyPrimary)
        }
        Text(
            text = title,
            style = FocusTheme.typography.screenTitle,
            color = colors.ink
        )
    }
}

@Composable
private fun PlaceholderSectionScreen(
    destination: Destination,
    onOpenDrawer: () -> Unit
) {
    val colors = FocusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.nightAbyss)
            .systemBarsPadding()
    ) {
        TopSectionBar(title = destination.title, onOpenDrawer = onOpenDrawer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(destination.symbol, style = FocusTheme.typography.timerDisplay, color = colors.electricAzureStart)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(destination.title, style = FocusTheme.typography.screenTitle, color = colors.ink)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(destination.subtitle, style = FocusTheme.typography.bodySecondary, color = colors.mutedInk)
                }
            }
        }
    }
}