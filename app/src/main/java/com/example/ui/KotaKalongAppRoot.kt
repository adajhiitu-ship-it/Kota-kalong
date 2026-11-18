package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.QuestionEntity
import com.example.ui.components.QuestionEditorSheet
import com.example.ui.screens.DatabaseScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.PureWhite

object AppRoutes {
    const val HOME = "beranda"
    const val UPLOAD = "upload"
    const val DATABASE = "database"
    const val FAVORITES = "favorit"
    const val HISTORY = "riwayat"
}

private data class BottomNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val BOTTOM_DESTINATIONS = listOf(
    BottomNavDestination(
        route = AppRoutes.HOME,
        label = "Beranda",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_beranda"
    ),
    BottomNavDestination(
        route = AppRoutes.UPLOAD,
        label = "Upload",
        selectedIcon = Icons.Filled.UploadFile,
        unselectedIcon = Icons.Outlined.UploadFile,
        testTag = "nav_upload"
    ),
    BottomNavDestination(
        route = AppRoutes.DATABASE,
        label = "Database",
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage,
        testTag = "nav_database"
    ),
    BottomNavDestination(
        route = AppRoutes.FAVORITES,
        label = "Favorit",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.StarBorder,
        testTag = "nav_favorit"
    )
)

@Composable
fun KotaKalongAppRoot(
    viewModel: KotaKalongViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.HOME

    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    var showEditorSheet by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<QuestionEntity?>(null) }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTier by viewModel.selectedTierFilter.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val questionCount by viewModel.questionCount.collectAsStateWithLifecycle()
    val documentCount by viewModel.documentCount.collectAsStateWithLifecycle()
    val favoriteCount by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    val preventDuplicates by viewModel.preventDuplicates.collectAsStateWithLifecycle()
    val defaultUploadCategory by viewModel.defaultUploadCategory.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val lastImportReport by viewModel.lastImportReport.collectAsStateWithLifecycle()
    val allDocuments by viewModel.allDocuments.collectAsStateWithLifecycle()

    val filteredDbQuestions by viewModel.filteredDatabaseQuestions.collectAsStateWithLifecycle()
    val categories by viewModel.categoryCounts.collectAsStateWithLifecycle()
    val dbFilterQuery by viewModel.dbFilterQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedDocumentFilter by viewModel.selectedDocumentFilter.collectAsStateWithLifecycle()
    val pendingRestoreUri by viewModel.pendingRestoreUri.collectAsStateWithLifecycle()

    val favoriteQuestions by viewModel.favoriteQuestions.collectAsStateWithLifecycle()

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (currentRoute != AppRoutes.HISTORY) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    BOTTOM_DESTINATIONS.forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PureWhite,
                                indicatorColor = ForestGreen,
                                selectedTextColor = ForestGreen,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            NavHost(
                navController = navController,
                startDestination = AppRoutes.HOME,
                modifier = Modifier.widthIn(max = 760.dp)
            ) {
                composable(AppRoutes.HOME) {
                    HomeScreen(
                        searchQuery = searchQuery,
                        selectedTier = selectedTier,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        questionCount = questionCount,
                        documentCount = documentCount,
                        favoriteCount = favoriteCount,
                        recentHistory = searchHistory,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        onSubmitSearch = { q -> viewModel.submitSearch(q) },
                        onClearSearch = viewModel::clearSearch,
                        onSelectTier = viewModel::setTierFilter,
                        onNavigateUpload = { navigateTopLevel(AppRoutes.UPLOAD) },
                        onNavigateDatabase = { navigateTopLevel(AppRoutes.DATABASE) },
                        onNavigateFavorites = { navigateTopLevel(AppRoutes.FAVORITES) },
                        onNavigateHistory = { navController.navigate(AppRoutes.HISTORY) },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onEditQuestion = { q ->
                            editingQuestion = q
                            showEditorSheet = true
                        }
                    )
                }

                composable(AppRoutes.UPLOAD) {
                    UploadScreen(
                        preventDuplicates = preventDuplicates,
                        defaultCategory = defaultUploadCategory,
                        importProgress = importProgress,
                        lastReport = lastImportReport,
                        documents = allDocuments,
                        onPreventDuplicatesChange = viewModel::setPreventDuplicates,
                        onDefaultCategoryChange = viewModel::setDefaultUploadCategory,
                        onFilesPicked = viewModel::importDocuments,
                        onClearReport = viewModel::clearImportReport,
                        onDeleteDocument = viewModel::deleteDocument
                    )
                }

                composable(AppRoutes.DATABASE) {
                    DatabaseScreen(
                        questions = filteredDbQuestions,
                        totalQuestionCount = questionCount,
                        categories = categories,
                        documents = allDocuments,
                        filterQuery = dbFilterQuery,
                        selectedCategory = selectedCategory,
                        selectedDocumentName = selectedDocumentFilter,
                        pendingRestoreUri = pendingRestoreUri,
                        onFilterQueryChange = viewModel::setDbFilterQuery,
                        onSelectCategory = viewModel::setSelectedCategory,
                        onSelectDocument = viewModel::setSelectedDocumentFilter,
                        onAddManualQuestion = {
                            editingQuestion = null
                            showEditorSheet = true
                        },
                        onEditQuestion = { q ->
                            editingQuestion = q
                            showEditorSheet = true
                        },
                        onDeleteQuestion = viewModel::deleteQuestion,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onBackupToUri = viewModel::exportBackup,
                        onRequestRestoreUri = viewModel::requestRestoreFromUri,
                        onConfirmRestore = viewModel::confirmRestore,
                        onDismissRestore = viewModel::dismissRestoreDialog
                    )
                }

                composable(AppRoutes.FAVORITES) {
                    FavoritesScreen(
                        favorites = favoriteQuestions,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onEditQuestion = { q ->
                            editingQuestion = q
                            showEditorSheet = true
                        }
                    )
                }

                composable(AppRoutes.HISTORY) {
                    HistoryScreen(
                        history = searchHistory,
                        onBack = { navController.popBackStack() },
                        onSelectQuery = { q ->
                            viewModel.submitSearch(q)
                            navController.popBackStack()
                        },
                        onDeleteItem = viewModel::deleteHistoryItem,
                        onClearAll = viewModel::clearAllHistory
                    )
                }
            }
        }
    }

    if (showEditorSheet) {
        QuestionEditorSheet(
            editingQuestion = editingQuestion,
            onDismiss = { showEditorSheet = false },
            onSave = { qText, a, b, c, d, e, ans, cat, srcDoc, srcLoc ->
                viewModel.saveManualQuestion(
                    editingQuestion = editingQuestion,
                    questionText = qText,
                    optionA = a,
                    optionB = b,
                    optionC = c,
                    optionD = d,
                    optionE = e,
                    correctAnswer = ans,
                    category = cat,
                    sourceDocumentName = srcDoc,
                    sourceLocation = srcLoc
                )
                showEditorSheet = false
            }
        )
    }
}
