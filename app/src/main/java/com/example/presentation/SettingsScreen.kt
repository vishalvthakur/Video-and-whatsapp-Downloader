package com.example.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("video_downloader_prefs", Context.MODE_PRIVATE) }
    var customUrl by remember { mutableStateOf(sharedPrefs.getString("custom_cobalt_url", "") ?: "") }
    var customCookie by remember { mutableStateOf(sharedPrefs.getString("custom_youtube_cookie", "") ?: "") }
    var showCookieExtractor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage Settings Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Storage Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Save Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Movies/VideoDownloader/",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Extraction Server Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Cobalt API",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Extraction Server (Cobalt)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Customize the API endpoint for extracting videos.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { newValue ->
                            customUrl = newValue
                            sharedPrefs.edit().putString("custom_cobalt_url", newValue.trim()).apply()
                            com.example.extractor.YtDlpManager.customCobaltUrl = newValue.trim()
                        },
                        label = { Text("Custom API URL (Optional)") },
                        placeholder = { Text("https://api.cobalt.tools") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Text(
                        text = "Quick Presets:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        val presets = listOf(
                            Triple("Official", "", "Default official API"),
                            Triple("Xenon", "https://rue-cobalt.xenon.zone", "Alternate public instance"),
                            Triple("Kittycat", "https://dog.kittycat.boo", "Alternate public instance"),
                            Triple("Fast-Ref", "https://cobalt.fast-ref.xyz", "Alternate public instance"),
                            Triple("PabloMG", "https://cobalt.pablomg.net", "Alternate public instance"),
                            Triple("Wuk", "https://co.wuk.sh", "Alternate public instance"),
                            Triple("CJS", "https://cobaltapi.cjs.nz", "Alternate public instance")
                        )
                        presets.forEach { (name, url, desc) ->
                            val isSelected = (customUrl.trim() == url.trim())
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    customUrl = url
                                    sharedPrefs.edit().putString("custom_cobalt_url", url).apply()
                                    com.example.extractor.YtDlpManager.customCobaltUrl = url
                                },
                                label = { Text(name, fontSize = 12.sp) }
                            )
                        }
                    }
                    
                    Text(
                        text = "Leave blank to use the official server (api.cobalt.tools). If you experience connection or resolution errors (like error.api.youtube.login), you can specify or quick-select an alternate working Cobalt instance.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                }
            }

            // YouTube Bot Bypass Settings Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Bypass Config",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "YouTube Bot Bypass",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Automate session cookies to prevent YouTube bot blocking.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showCookieExtractor = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🔑 Sign in & Auto-Extract Cookie")
                    }

                    OutlinedTextField(
                        value = customCookie,
                        onValueChange = { newValue ->
                            customCookie = newValue
                            sharedPrefs.edit().putString("custom_youtube_cookie", newValue.trim()).apply()
                            com.example.extractor.YtDlpManager.customYoutubeCookie = newValue.trim()
                        },
                        label = { Text("Local YouTube Cookie string (Optional)") },
                        placeholder = { Text("__Secure-3PAPISID=...; __Secure-3PSID=...; SID=...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "💡 How to extract your YouTube cookie string:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "1. On a computer, open youtube.com and login to your account.\n" +
                                   "2. Right-click and choose \"Inspect\" or press F12, then select the \"Console\" tab.\n" +
                                   "3. Copy and run: copy(document.cookie) then paste the clipboard content in the input field above.\n" +
                                   "Or use a cookie export extension (like \"Get cookies.txt\") to get the text format.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🐳 For Self-Hosted Cobalt API users:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "To bypass YouTube blocking automatically on your own server, set these Environment Variables in your server setup:\n" +
                                   "• YT_CONN_SESSION_GENERATOR = true (enables automated YouTube sessions)\n" +
                                   "• YOUTUBE_COOKIE = [Your extracted cookie string]",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Privacy Settings Card (Mandatory Requirement)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Privacy Policy",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Privacy Statement",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "URLs are processed only to analyze user-authorized media. Downloaded videos are stored locally on your device.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // App Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "App Info",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "Video Downloader",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Version 1.0.0 (Production)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showCookieExtractor) {
        YouTubeCookieExtractorDialog(
            onDismiss = { showCookieExtractor = false },
            onCookieExtracted = { extracted ->
                customCookie = extracted
                sharedPrefs.edit().putString("custom_youtube_cookie", extracted).apply()
                com.example.extractor.YtDlpManager.customYoutubeCookie = extracted
            }
        )
    }
}

@Composable
fun YouTubeCookieExtractorDialog(
    onDismiss: () -> Unit,
    onCookieExtracted: (String) -> Unit
) {
    var extractedCookie by remember { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf("https://www.youtube.com") }
    var isLoading by remember { mutableStateOf(true) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YouTube Session Login",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Sign in to your YouTube account to automatically extract the cookies.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // WebView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = { context ->
                            android.webkit.WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    // Bypass Google login blocks inside WebViews by using a Windows Desktop Chrome User-Agent (which doesn't trigger Chromium JS API checks and is fully trusted)
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                                }
                                val cookieManager = android.webkit.CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        android.util.Log.d("YouTubeLogin", "onPageStarted: $url")
                                    }

                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        android.util.Log.d("YouTubeLogin", "onPageFinished: $url")
                                        url?.let { pageUrl = it }
                                        val cookies = cookieManager.getCookie("https://.youtube.com") ?: cookieManager.getCookie("https://www.youtube.com")
                                        if (!cookies.isNullOrEmpty()) {
                                            extractedCookie = cookies
                                            if (cookies.contains("SID=") || cookies.contains("__Secure-3PSID=")) {
                                                cookieManager.flush()
                                                onCookieExtracted(cookies)
                                                onDismiss()
                                            }
                                        }
                                    }

                                    override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        isLoading = false
                                        android.util.Log.e("YouTubeLogin", "WebView Error: ${error?.description} (${error?.errorCode})")
                                    }

                                    override fun onReceivedSslError(view: android.webkit.WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                        android.util.Log.w("YouTubeLogin", "SSL Error: $error")
                                        handler?.proceed()
                                    }
                                }
                                // Direct YouTube sign-in page, which redirects cleanly to Google Account login
                                loadUrl("https://www.youtube.com/signin")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0x80FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                // Footer controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (extractedCookie.contains("SID=")) "✅ Cookie Extracted (Logged In)" else "ℹ️ Please sign in to your Google Account on YouTube",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (extractedCookie.contains("SID=")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (extractedCookie.isNotEmpty()) {
                                    onCookieExtracted(extractedCookie)
                                }
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Cookie")
                        }
                    }
                }
            }
        }
    }
}
