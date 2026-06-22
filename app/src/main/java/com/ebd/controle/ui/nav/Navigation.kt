package com.ebd.controle.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ebd.controle.ui.screens.*
import com.ebd.controle.ui.theme.LocalAppBrushes

/** Cada item agora tem ícone "cheio" (selecionado) e "contornado" (inativo),
 *  como em apps modernos — dá um feedback visual mais elegante. */
data class NavItem(
    val route: String,
    val label: String,
    val iconSel: ImageVector,
    val iconOff: ImageVector
)

private val bottomItems = listOf(
    NavItem("dashboard", "Início", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem("chamada", "Chamada", Icons.Filled.HowToReg, Icons.Outlined.HowToReg),
    NavItem("membros", "Membros", Icons.Filled.Groups, Icons.Outlined.Groups),
    NavItem("visitantes", "Visitantes", Icons.Filled.PersonAdd, Icons.Outlined.PersonAdd),
    NavItem("financas", "Finanças", Icons.Filled.Paid, Icons.Outlined.Paid)
)

private val titulos = mapOf(
    "dashboard" to "EBD Controle",
    "chamada" to "Chamada",
    "membros" to "Membros",
    "visitantes" to "Visitantes",
    "relatorios" to "Relatórios",
    "financas" to "Finanças",
    "classes" to "Classes",
    "aniversarios" to "Aniversariantes",
    "backup" to "Backup e dados",
    "settings" to "Configurações"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val rota = entry?.destination?.route ?: "dashboard"
    val brushes = LocalAppBrushes.current

    Scaffold(
        containerColor = Color.Transparent,
        // fundo com gradiente do tema (céu noturno no escuro / calor suave no claro)
        modifier = Modifier.background(brushes.fundo),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titulos[rota] ?: "EBD Controle",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    if (rota != "settings") {
                        IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Filled.Settings, "Configurações")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                bottomItems.forEach { item ->
                    val sel = rota == item.route
                    NavigationBarItem(
                        selected = sel,
                        onClick = { navegar(nav, item.route) },
                        icon = {
                            Icon(
                                if (sel) item.iconSel else item.iconOff,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            // pílula de seleção = "cápsula" da marca:
                            // verde-limão + ícone preto no claro; musgo + ícone
                            // limão no escuro (vem de primaryContainer/onPrimaryContainer)
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(nav) }
            composable("chamada") { ChamadaScreen() }
            composable("membros") { MembrosScreen() }
            composable("visitantes") { VisitantesScreen() }
            composable("relatorios") { RelatoriosScreen() }
            composable("financas") { FinancasScreen() }
            composable("classes") { ClassesScreen() }
            composable("aniversarios") { AniversariantesScreen() }
            composable("backup") { BackupScreen() }
            composable("settings") { SettingsScreen(nav) }
        }
    }
}

private fun navegar(nav: NavController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
