package com.compatchecker.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.compatchecker.app.R
import com.compatchecker.app.core.di.AppContainer
import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.ui.auth.AuthScreen
import com.compatchecker.app.ui.catalog.CatalogScreen
import com.compatchecker.app.ui.checker.CheckerScreen
import com.compatchecker.app.ui.guides.GuideDetailScreen
import com.compatchecker.app.ui.guides.GuidesScreen
import com.compatchecker.app.ui.product.ProductScreen
import com.compatchecker.app.ui.profile.ContributionsScreen
import com.compatchecker.app.ui.profile.FavoritesScreen
import com.compatchecker.app.ui.profile.NotificationsScreen
import com.compatchecker.app.ui.profile.ProfileScreen
import com.compatchecker.app.ui.requests.RequestsScreen
import com.compatchecker.app.ui.rules.RuleScreen
import com.compatchecker.app.ui.settings.SettingsScreen
import com.compatchecker.app.ui.social.CollectionDetailScreen
import com.compatchecker.app.ui.social.CollectionsScreen
import com.compatchecker.app.ui.social.LeaderboardScreen
import com.compatchecker.app.ui.social.PublicProfileScreen

private object Routes {
    const val CATALOG = "catalog"
    const val CHECKER = "checker?productId={productId}"
    const val GUIDES = "guides"
    const val REQUESTS = "requests"
    const val PROFILE = "profile"
    const val AUTH = "auth"
    const val FAVORITES = "favorites"
    const val COLLECTIONS = "collections"
    const val CONTRIBUTIONS = "contributions"
    const val NOTIFICATIONS = "notifications"
    const val LEADERBOARD = "leaderboard"
    const val SETTINGS = "settings"
    const val PRODUCT = "product/{productId}"
    const val GUIDE = "guide/{guideId}"
    const val RULE = "rule/{ruleId}"
    const val COLLECTION = "collection/{collectionId}"
    const val PUBLIC_PROFILE = "profile/{userId}"

    fun checker(productId: String? = null): String =
        if (productId.isNullOrBlank()) "checker" else "checker?productId=$productId"

    fun product(id: String) = "product/$id"
    fun guide(id: String) = "guide/$id"
    fun rule(id: String) = "rule/$id"
    fun collection(id: String) = "collection/$id"
    fun publicProfile(id: String) = "profile/$id"
}

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab(Routes.CATALOG, R.string.tab_browse, Icons.Outlined.Search),
    Tab("checker", R.string.tab_checker, Icons.AutoMirrored.Outlined.CompareArrows),
    Tab(Routes.GUIDES, R.string.tab_guides, Icons.AutoMirrored.Outlined.MenuBook),
    Tab(Routes.REQUESTS, R.string.tab_requests, Icons.Outlined.Forum),
    Tab(Routes.PROFILE, R.string.tab_profile, Icons.Outlined.Person),
)

@Composable
fun CompatApp(container: AppContainer) {
    val configError = container.config.configurationError
    if (configError != null) {
        ConfigurationScreen(configError)
        return
    }

    val navController = rememberNavController()
    val activity = LocalContext.current.findActivity()
    DisposableEffect(navController, activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<Intent> { intent ->
            navController.handleDeepLink(intent)
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentBase = currentRoute?.substringBefore('?')
    val showBottomBar = tabs.any { it.route.substringBefore('?') == currentBase }
    val session by container.authRepository.session.collectAsStateWithLifecycle(initialValue = null)
    val signedIn = session != null

    val openAuth: () -> Unit = { navController.navigate(Routes.AUTH) }

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.CATALOG,
            modifier = Modifier.weight(1f),
        ) {
            composable(Routes.CATALOG) {
                CatalogScreen(
                    catalog = container.catalogRepository,
                    onProductClick = { navController.navigate(Routes.product(it)) },
                )
            }
            composable(
                route = Routes.CHECKER,
                arguments = listOf(
                    navArgument("productId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "compatchecker://checker?productId={productId}" },
                    navDeepLink { uriPattern = "compatchecker://checker" },
                ),
            ) { entry ->
                CheckerScreen(
                    initialProductId = entry.arguments?.getString("productId"),
                    catalog = container.catalogRepository,
                    compatibility = container.compatibilityRepository,
                    onRuleClick = { navController.navigate(Routes.rule(it)) },
                )
            }
            composable(Routes.GUIDES) {
                GuidesScreen(
                    guidesRepository = container.guideRepository,
                    onGuideClick = { navController.navigate(Routes.guide(it)) },
                )
            }
            composable(Routes.REQUESTS) {
                RequestsScreen(
                    community = container.communityRepository,
                    catalog = container.catalogRepository,
                    auth = container.authRepository,
                    onAuthRequired = openAuth,
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    signedIn = signedIn,
                    auth = container.authRepository,
                    profiles = container.profileRepository,
                    onSignIn = openAuth,
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onOpenCollections = { navController.navigate(Routes.COLLECTIONS) },
                    onOpenContributions = { navController.navigate(Routes.CONTRIBUTIONS) },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenLeaderboard = { navController.navigate(Routes.LEADERBOARD) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.AUTH) {
                AuthScreen(
                    auth = container.authRepository,
                    onBack = { navController.popBackStack() },
                    onAuthenticated = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PRODUCT,
                arguments = listOf(navArgument("productId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "compatchecker://product/{productId}" },
                ),
            ) { entry ->
                ProductScreen(
                    productId = entry.arguments?.getString("productId").orEmpty(),
                    catalog = container.catalogRepository,
                    community = container.communityRepository,
                    profiles = container.profileRepository,
                    auth = container.authRepository,
                    onBack = { navController.popBackStack() },
                    onAuthRequired = openAuth,
                    onRuleClick = { navController.navigate(Routes.rule(it)) },
                    onGuideClick = { navController.navigate(Routes.guide(it)) },
                    onCompare = { navController.navigate(Routes.checker(it)) },
                    social = container.socialRepository,
                    signedIn = signedIn,
                    onOpenCollections = { navController.navigate(Routes.COLLECTIONS) },
                )
            }
            composable(
                route = Routes.GUIDE,
                arguments = listOf(navArgument("guideId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "compatchecker://guide/{guideId}" },
                ),
            ) { entry ->
                GuideDetailScreen(
                    guideId = entry.arguments?.getString("guideId").orEmpty(),
                    guidesRepository = container.guideRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.RULE,
                arguments = listOf(navArgument("ruleId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "compatchecker://rule/{ruleId}" },
                ),
            ) { entry ->
                RuleScreen(
                    ruleId = entry.arguments?.getString("ruleId").orEmpty(),
                    compatibility = container.compatibilityRepository,
                    profiles = container.profileRepository,
                    auth = container.authRepository,
                    onBack = { navController.popBackStack() },
                    onAuthRequired = openAuth,
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    catalog = container.catalogRepository,
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate(Routes.product(it)) },
                )
            }
            composable(Routes.CONTRIBUTIONS) {
                ContributionsScreen(
                    profiles = container.profileRepository,
                    catalog = container.catalogRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    profiles = container.profileRepository,
                    onBack = { navController.popBackStack() },
                    onOpenNotification = { openNotification(navController, it) },
                )
            }
            composable(Routes.COLLECTIONS) {
                CollectionsScreen(
                    social = container.socialRepository,
                    onBack = { navController.popBackStack() },
                    onCollectionClick = { navController.navigate(Routes.collection(it)) },
                )
            }
            composable(
                route = Routes.COLLECTION,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "compatchecker://collection/{collectionId}" },
                ),
            ) { entry ->
                CollectionDetailScreen(
                    collectionId = entry.arguments?.getString("collectionId").orEmpty(),
                    social = container.socialRepository,
                    onBack = { navController.popBackStack() },
                    onProductClick = { navController.navigate(Routes.product(it)) },
                )
            }
            composable(Routes.LEADERBOARD) {
                LeaderboardScreen(
                    social = container.socialRepository,
                    onBack = { navController.popBackStack() },
                    onProfileClick = { navController.navigate(Routes.publicProfile(it)) },
                )
            }
            composable(
                route = Routes.PUBLIC_PROFILE,
                arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "compatchecker://profile/{userId}" },
                ),
            ) { entry ->
                PublicProfileScreen(
                    userId = entry.arguments?.getString("userId").orEmpty(),
                    social = container.socialRepository,
                    signedIn = signedIn,
                    onBack = { navController.popBackStack() },
                    onAuthRequired = openAuth,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    preferences = container.preferencesRepository,
                    profiles = container.profileRepository,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (showBottomBar) {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = tab.route.substringBefore('?') == currentBase
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.CATALOG) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }
    }
}

private fun openNotification(
    navController: androidx.navigation.NavHostController,
    notification: AppNotification,
): Boolean = when (val destination = parseNotificationLink(notification.link)) {
    NotificationDestination.None -> true
    is NotificationDestination.Product -> {
        navController.navigate(Routes.product(destination.id))
        true
    }
    is NotificationDestination.Guide -> {
        navController.navigate(Routes.guide(destination.id))
        true
    }
    is NotificationDestination.Rule -> {
        navController.navigate(Routes.rule(destination.id))
        true
    }
    is NotificationDestination.Profile -> {
        navController.navigate(Routes.publicProfile(destination.id))
        true
    }
    is NotificationDestination.Collection -> {
        navController.navigate(Routes.collection(destination.id))
        true
    }
    NotificationDestination.Unsupported -> false
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ConfigurationScreen(error: String) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.configuration_required), style = MaterialTheme.typography.titleLarge)
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.configuration_help), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
