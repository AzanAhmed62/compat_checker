package com.example.compatchecker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.compatchecker.core.di.AppContainer
import com.example.compatchecker.domain.model.ProductCategory
import com.example.compatchecker.ui.screens.CommunityHubScreen
import com.example.compatchecker.ui.screens.CompatibilityCheckerScreen
import com.example.compatchecker.ui.screens.GuidesScreen
import com.example.compatchecker.ui.screens.HomeScreen
import com.example.compatchecker.ui.screens.ProductDetailScreen
import com.example.compatchecker.ui.screens.ProfileScreen
import com.example.compatchecker.ui.screens.SearchScreen
import com.example.compatchecker.ui.screens.SettingsScreen
import com.example.compatchecker.ui.theme.TechCyan
import com.example.compatchecker.ui.viewmodels.CommunityViewModel
import com.example.compatchecker.ui.viewmodels.CompatibilityCheckerViewModel
import com.example.compatchecker.ui.viewmodels.GuidesViewModel
import com.example.compatchecker.ui.viewmodels.HomeViewModel
import com.example.compatchecker.ui.viewmodels.ProductDetailViewModel
import com.example.compatchecker.ui.viewmodels.ProfileViewModel
import com.example.compatchecker.ui.viewmodels.SearchViewModel
import com.example.compatchecker.ui.viewmodels.SettingsViewModel

enum class NavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SEARCH("Search", Icons.Default.Search),
    CHECKER("Checker", Icons.Default.CompareArrows),
    COMMUNITY("Community", Icons.Default.Group),
    GUIDES("Guides", Icons.Default.Build),
    PROFILE("Profile", Icons.Default.Person)
}

sealed class ScreenRoute {
    data class Main(val tab: NavDestination) : ScreenRoute()
    data class ProductDetail(val productId: String) : ScreenRoute()
    object Settings : ScreenRoute()
}

@Composable
fun AppNavigation(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    var currentRoute by remember { mutableStateOf<ScreenRoute>(ScreenRoute.Main(NavDestination.HOME)) }

    // Lazy remember ViewModels instantiated from AppContainer
    val homeViewModel = remember {
        HomeViewModel(container.productRepository, container.compatibilityRepository, container.favoritesRepository)
    }
    val searchViewModel = remember {
        SearchViewModel(container.productRepository, container.favoritesRepository)
    }
    val checkerViewModel = remember {
        CompatibilityCheckerViewModel(container.productRepository, container.compatibilityRepository)
    }
    val communityViewModel = remember {
        CommunityViewModel(container.communityRepository)
    }
    val guidesViewModel = remember {
        GuidesViewModel(container.repairGuideRepository)
    }
    val profileViewModel = remember {
        ProfileViewModel(container.userRepository, container.favoritesRepository)
    }
    val settingsViewModel = remember {
        SettingsViewModel(container.userPreferences, container.database, container.productRepository, container.userRepository)
    }

    // Android Hardware & Gesture Back Handler
    BackHandler(enabled = currentRoute !is ScreenRoute.Main || (currentRoute as ScreenRoute.Main).tab != NavDestination.HOME) {
        when (currentRoute) {
            is ScreenRoute.ProductDetail -> currentRoute = ScreenRoute.Main(NavDestination.SEARCH)
            is ScreenRoute.Settings -> currentRoute = ScreenRoute.Main(NavDestination.PROFILE)
            is ScreenRoute.Main -> {
                if ((currentRoute as ScreenRoute.Main).tab != NavDestination.HOME) {
                    currentRoute = ScreenRoute.Main(NavDestination.HOME)
                }
            }
        }
    }

    val isTopLevel = currentRoute is ScreenRoute.Main
    val activeTab = (currentRoute as? ScreenRoute.Main)?.tab ?: NavDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    NavDestination.values().forEach { destination ->
                        val isSelected = activeTab == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentRoute = ScreenRoute.Main(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TechCyan,
                                selectedTextColor = TechCyan,
                                indicatorColor = TechCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (val route = currentRoute) {
            is ScreenRoute.Main -> {
                val screenModifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                when (route.tab) {
                    NavDestination.HOME -> HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToProduct = { currentRoute = ScreenRoute.ProductDetail(it) },
                        onNavigateToSearch = { categoryName ->
                            if (categoryName != null) {
                                searchViewModel.onCategorySelect(
                                    try { ProductCategory.valueOf(categoryName) } catch (e: Exception) { null }
                                )
                            }
                            currentRoute = ScreenRoute.Main(NavDestination.SEARCH)
                        },
                        onNavigateToChecker = { currentRoute = ScreenRoute.Main(NavDestination.CHECKER) },
                        onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                        modifier = screenModifier
                    )
                    NavDestination.SEARCH -> SearchScreen(
                        viewModel = searchViewModel,
                        onNavigateToProduct = { currentRoute = ScreenRoute.ProductDetail(it) },
                        onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                        modifier = screenModifier
                    )
                    NavDestination.CHECKER -> CompatibilityCheckerScreen(
                        viewModel = checkerViewModel,
                        onNavigateToProduct = { currentRoute = ScreenRoute.ProductDetail(it) },
                        onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                        onNavigateToRequests = { currentRoute = ScreenRoute.Main(NavDestination.COMMUNITY) },
                        modifier = screenModifier
                    )
                    NavDestination.COMMUNITY -> CommunityHubScreen(
                        viewModel = communityViewModel,
                        onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                        modifier = screenModifier
                    )
                    NavDestination.GUIDES -> GuidesScreen(
                        viewModel = guidesViewModel,
                        onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                        modifier = screenModifier
                    )
                    NavDestination.PROFILE -> ProfileScreen(
                        viewModel = profileViewModel,
                        onNavigateToProduct = { currentRoute = ScreenRoute.ProductDetail(it) },
                        onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                        modifier = screenModifier
                    )
                }
            }
            is ScreenRoute.ProductDetail -> {
                val detailViewModel = remember(route.productId) {
                    ProductDetailViewModel(
                        productId = route.productId,
                        productRepository = container.productRepository,
                        compatibilityRepository = container.compatibilityRepository,
                        communityRepository = container.communityRepository,
                        repairGuideRepository = container.repairGuideRepository,
                        favoritesRepository = container.favoritesRepository
                    )
                }
                ProductDetailScreen(
                    viewModel = detailViewModel,
                    onBackClick = { currentRoute = ScreenRoute.Main(NavDestination.SEARCH) },
                    onCheckCompatibilityWith = { productId ->
                        val target = container.database.productDao()
                        // Load and select in checker
                        val prod = container.database.productDao().getAllProducts()
                        checkerViewModel.reset()
                        val allProds = checkerViewModel.uiState.value.allProducts
                        val match = allProds.find { it.id == productId }
                        if (match != null) {
                            checkerViewModel.selectSource(match)
                        }
                        currentRoute = ScreenRoute.Main(NavDestination.CHECKER)
                    },
                    onNavigateToGuide = { currentRoute = ScreenRoute.Main(NavDestination.GUIDES) },
                    onNavigateToSettings = { currentRoute = ScreenRoute.Settings },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is ScreenRoute.Settings -> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBackClick = { currentRoute = ScreenRoute.Main(NavDestination.PROFILE) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
