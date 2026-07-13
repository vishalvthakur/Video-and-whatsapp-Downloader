package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.util.Patterns
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.database.DownloadDatabase
import com.example.data.repository.DownloadRepository
import com.example.download.MediaDownloadManager
import com.example.presentation.*
import com.example.ui.theme.MyApplicationTheme
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var repository: DownloadRepository
    private var sharedViewModel: VideoDownloaderViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room database & manual DI repository
        val database = DownloadDatabase.getDatabase(this)
        repository = DownloadRepository(database.downloadDao())

        // Initialize download manager on start
        MediaDownloadManager.init(this)

        // Initialize Cobalt Custom API URL & YouTube Cookie
        val sharedPrefs = getSharedPreferences("video_downloader_prefs", android.content.Context.MODE_PRIVATE)
        com.example.extractor.YtDlpManager.customCobaltUrl = sharedPrefs.getString("custom_cobalt_url", null)
        com.example.extractor.YtDlpManager.customYoutubeCookie = sharedPrefs.getString("custom_youtube_cookie", null)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val factory = remember { VideoDownloaderViewModelFactory(repository) }
                val viewModel: VideoDownloaderViewModel = viewModel(factory = factory)
                sharedViewModel = viewModel

                // Runtime POST_NOTIFICATIONS request on Android 13+
                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Toast.makeText(this, "Notification permission is required to track download progress in background.", Toast.LENGTH_LONG).show()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Check for shared URL intent on cold start
                LaunchedEffect(intent) {
                    handleShareIntent(intent, viewModel)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onSplashComplete = {
                                    navController.navigate("whatsapp_saver") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToAnalysis = {
                                    navController.navigate("analysis")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToWhatsAppSaver = {
                                    navController.navigate("whatsapp_saver")
                                },
                                onPlayVideo = { uri, title ->
                                    val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("player/$encodedUri/$encodedTitle")
                                }
                            )
                        }

                        composable("analysis") {
                            VideoAnalysisScreen(
                                viewModel = viewModel,
                                onBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToProgress = {
                                    navController.navigate("progress") {
                                        popUpTo("analysis") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("progress") {
                            DownloadProgressScreen(
                                viewModel = viewModel,
                                onBackToHome = {
                                    navController.navigate("home") {
                                        popUpTo("progress") { inclusive = true }
                                    }
                                },
                                onPlayVideo = { uri, title ->
                                    val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("player/$encodedUri/$encodedTitle") {
                                        popUpTo("progress") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("whatsapp_saver") {
                            WhatsAppSaverScreen(
                                onBack = {
                                    if (!navController.popBackStack()) {
                                        navController.navigate("home")
                                    }
                                },
                                onNavigateToHome = {
                                    navController.navigate("home")
                                },
                                onPlayVideo = { uri, title ->
                                    val encodedUri = URLEncoder.encode(uri, "UTF-8")
                                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("player/$encodedUri/$encodedTitle")
                                }
                            )
                        }

                        composable(
                            route = "player/{uri}/{title}",
                            arguments = listOf(
                                navArgument("uri") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val encodedUri = backStackEntry.arguments?.getString("uri") ?: ""
                            val encodedTitle = backStackEntry.arguments?.getString("title") ?: ""
                            val uri = URLDecoder.decode(encodedUri, "UTF-8")
                            val title = URLDecoder.decode(encodedTitle, "UTF-8")

                            VideoPlayerScreen(
                                videoUri = uri,
                                videoTitle = title,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedViewModel?.let { viewModel ->
            handleShareIntent(intent, viewModel)
        }
    }

    private fun handleShareIntent(intent: Intent?, viewModel: VideoDownloaderViewModel) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type

        if ("com.example.downloader.ACTION_ERROR_RETRY" == action) {
            val errorMsg = intent.getStringExtra("ERROR_MESSAGE") ?: "Download failed"
            val retryUrl = intent.getStringExtra("RETRY_URL")
            Log.d(TAG, "Opened from error notification: $errorMsg, url: $retryUrl")
            if (!retryUrl.isNullOrEmpty()) {
                viewModel.onUrlChange(retryUrl)
                Toast.makeText(this, "Error: $errorMsg\nLoaded URL to retry.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Download failed: $errorMsg", Toast.LENGTH_LONG).show()
            }
            return
        }

        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    Log.d(TAG, "Received shared text URL: $sharedText")
                    // Parse out the first URL found in the text if it contains other words
                    val extractedUrl = extractUrlFromText(sharedText)
                    if (extractedUrl.isNotEmpty()) {
                        viewModel.onUrlChange(extractedUrl)
                        Toast.makeText(this, "Shared URL loaded. Ready to Analyze!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun extractUrlFromText(text: String): String {
        val parts = text.split(Regex("\\s+"))
        for (part in parts) {
            if (Patterns.WEB_URL.matcher(part).matches()) {
                return if (!part.startsWith("http://") && !part.startsWith("https://")) {
                    "https://$part"
                } else {
                    part
                }
            }
        }
        return ""
    }
}
