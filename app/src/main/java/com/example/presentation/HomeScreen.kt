package com.example.presentation

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.DownloadEntity
import com.example.download.DownloadQueueManager
import com.example.download.MediaDownloadManager
import com.example.download.QueueStatus
import com.example.extractor.VideoInfoParser
import com.example.service.VideoDownloadService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VideoDownloaderViewModel,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWhatsAppSaver: () -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlInput by viewModel.urlInput.collectAsState()
    val extractionState by viewModel.extractionState.collectAsState()
    val historyList by viewModel.downloadHistory.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) } // 0 = Single, 1 = Batch Queue
    var batchInput by remember { mutableStateOf("") }

    var showVerificationDialog by remember { mutableStateOf(false) }
    var showExtractorDialogAfterFailure by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val urls = text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://")) }
                if (urls.isNotEmpty()) {
                    batchInput = (if (batchInput.isEmpty()) "" else batchInput + "\n") + urls.joinToString("\n")
                    Toast.makeText(context, "Imported ${urls.size} URLs from text file", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No valid URLs found in file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Listen to Extraction success/failure to trigger navigation
    LaunchedEffect(extractionState) {
        if (extractionState is ExtractionState.Success) {
            onNavigateToAnalysis()
        } else if (extractionState is ExtractionState.Error) {
            val errorMsg = (extractionState as ExtractionState.Error).message
            if (errorMsg.contains("requires account verification") || 
                errorMsg.contains("login") || 
                errorMsg.contains("cookie") ||
                errorMsg.contains("bot detection") ||
                errorMsg.contains("rate-limited") ||
                errorMsg.contains("extraction server")
            ) {
                showVerificationDialog = true
            } else {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
            viewModel.resetExtraction()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "PRO ENCODER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Video Downloader",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL Input & Control Area
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Modes Segmented Control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TabButton(
                            text = "Single URL",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            text = "Batch Queue",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (selectedTab == 0) {
                        Text(
                            text = "Paste Video URL",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { viewModel.onUrlChange(it) },
                            placeholder = { Text("Paste video link here...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onUrlChange("") }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val text = clipData.getItemAt(0).text?.toString() ?: ""
                                        viewModel.onUrlChange(text)
                                        Toast.makeText(context, "Link pasted from clipboard", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste")
                            }

                            Button(
                                onClick = {
                                    viewModel.analyzeUrl()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                if (extractionState is ExtractionState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Analyze", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Enter Multiple URLs (One per line) or Select Text File",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = batchInput,
                            onValueChange = { batchInput = it },
                            placeholder = { Text("https://example.com/video1\nhttps://example.com/video2") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    filePickerLauncher.launch("text/plain")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import File", maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    val urls = batchInput.lines()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://")) }
                                    if (urls.isNotEmpty()) {
                                        DownloadQueueManager.addToQueue(urls)
                                        batchInput = ""
                                        Toast.makeText(context, "Added ${urls.size} videos to queue", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No valid URLs found in input", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add to Queue", maxLines = 1)
                            }
                        }
                    }
                }
            }

            val queueItems by DownloadQueueManager.queue.collectAsState()
            val isProcessingQueue by DownloadQueueManager.isProcessing.collectAsState()

            if (queueItems.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Queue Progress (${queueItems.count { it.status == QueueStatus.COMPLETED }}/${queueItems.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            IconButton(
                                onClick = { DownloadQueueManager.clearQueue() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear Queue",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            queueItems.take(4).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.status == QueueStatus.DOWNLOADING) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = item.progress / 100f,
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        }
                                    }

                                    val (badgeText, badgeColor, contentColor) = when (item.status) {
                                        QueueStatus.PENDING -> Triple("Pending", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                        QueueStatus.EXTRACTING -> Triple("Extracting", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                        QueueStatus.DOWNLOADING -> Triple("${item.progress}%", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                        QueueStatus.COMPLETED -> Triple("Done", Color(0xFF2E7D32).copy(alpha = 0.15f), Color(0xFF2E7D32))
                                        QueueStatus.FAILED -> Triple("Failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(badgeColor, shape = RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = badgeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = contentColor)
                                    }

                                    IconButton(
                                        onClick = { DownloadQueueManager.removeItem(item.id) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (queueItems.size > 4) {
                                Text(
                                    text = "+ ${queueItems.size - 4} more items in queue",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isProcessingQueue) {
                                Button(
                                    onClick = {
                                        val startIntent = Intent(context, VideoDownloadService::class.java).apply {
                                            action = "START_QUEUE_DOWNLOAD"
                                        }
                                        if (BuildVersionHelper.isAtLeastO()) {
                                            context.startForegroundService(startIntent)
                                        } else {
                                            context.startService(startIntent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Downloading Queue")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        MediaDownloadManager.cancelActiveJob()
                                        DownloadQueueManager.setProcessing(false)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel Queue Download")
                                }
                            }
                        }
                    }
                }
            }

            // WhatsApp Utilities Entry Card
            Card(
                onClick = onNavigateToWhatsAppSaver,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF128C7E).copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, Color(0xFF128C7E).copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF128C7E).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = Color(0xFF128C7E),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WhatsApp Status & Profile Saver",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF128C7E)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Save contact statuses, download profile photos, or open direct chats.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Explore",
                        tint = Color(0xFF128C7E).copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // History Section Header
            Text(
                text = "Download History",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No download history found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(historyList, key = { it.id }) { item ->
                        HistoryItemCard(
                            item = item,
                            onPlay = {
                                if (isUriAccessible(context, item.localUri)) {
                                    onPlayVideo(item.localUri, item.title)
                                } else {
                                    Toast.makeText(context, "Source file no longer exists in gallery", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                if (isUriAccessible(context, item.localUri)) {
                                    shareMediaFile(context, item.localUri, item.title)
                                } else {
                                    Toast.makeText(context, "Source file no longer exists to share", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = { deleteFile ->
                                viewModel.deleteHistoryItem(context, item, deleteFile)
                                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = { showVerificationDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("YouTube Verification Required")
                }
            },
            text = {
                Text(
                    text = "YouTube requires a one-time account verification to extract and download this video.\n\n" +
                           "The app will securely open a safe browser to YouTube's official login page. After you sign in, the app will automatically capture your session cookies, return back here, and start your download immediately.\n\n" +
                           "This is a one-time setup, so you won't need to sign in again.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            dismissButton = {
                TextButton(onClick = { showVerificationDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVerificationDialog = false
                        showExtractorDialogAfterFailure = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Verify Now")
                }
            }
        )
    }

    if (showExtractorDialogAfterFailure) {
        YouTubeCookieExtractorDialog(
            onDismiss = { showExtractorDialogAfterFailure = false },
            onCookieExtracted = { cookie ->
                val sharedPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("custom_youtube_cookie", cookie).apply()
                com.example.extractor.YtDlpManager.customYoutubeCookie = cookie
                
                showExtractorDialogAfterFailure = false
                Toast.makeText(context, "Verification successful! Resuming download...", Toast.LENGTH_SHORT).show()
                viewModel.analyzeUrl()
            }
        )
    }
}

@Composable
fun HistoryItemCard(
    item: DownloadEntity,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Download") },
            text = { Text("Would you like to remove this from history or also delete the physical media file from your gallery?") },
            dismissButton = {
                TextButton(onClick = {
                    onDelete(false)
                    showDeleteDialog = false
                }) {
                    Text("History Only")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(true)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete File & History")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!item.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Info Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.resolution} • ${VideoInfoParser.formatFileSize(item.fileSize)} • ${item.extension.uppercase()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.downloadDate)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Interactive Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun isUriAccessible(context: Context, uriString: String): Boolean {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
}

fun shareMediaFile(context: Context, uriString: String, title: String) {
    try {
        val uri = Uri.parse(uriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share video", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        elevation = if (selected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null,
        modifier = modifier
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
