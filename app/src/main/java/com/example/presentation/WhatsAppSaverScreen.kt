package com.example.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.example.media.MediaStoreManager
import com.example.presentation.components.VideoPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatusItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val size: Long,
    val lastModified: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppSaverScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedMainTab by remember { mutableStateOf(0) } // 0 = Status Saver, 1 = Profile Downloader
    
    // Hoisted Selection & Search States
    var selectedStatusItem by remember { mutableStateOf<StatusItem?>(null) }
    var fetchedAvatarUrl by remember { mutableStateOf("") }
    var fullPhoneNumber by remember { mutableStateOf("") }
    var isProfileFetched by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WHATSAPP UTILITIES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = if (selectedMainTab == 0) "Status Saver" else "Profile Downloader",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHome) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Switch to Video Downloader",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (selectedMainTab == 0) {
                        if (selectedStatusItem != null) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val savedUri = MediaStoreManager.saveMediaFromUri(
                                            context = context,
                                            srcUri = selectedStatusItem!!.uri,
                                            title = selectedStatusItem!!.name.substringBeforeLast("."),
                                            isVideo = selectedStatusItem!!.isVideo
                                        )
                                        if (savedUri != null) {
                                            Toast.makeText(context, "Successfully Downloaded Status to Gallery!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Failed to download status", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF128C7E),
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download Selected Status",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No Status Selected",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        if (isProfileFetched && fetchedAvatarUrl.isNotEmpty()) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val titleName = "WA_DP_+$fullPhoneNumber"
                                        val savedUri = MediaStoreManager.downloadAndSaveImage(
                                            context = context,
                                            imageUrl = fetchedAvatarUrl,
                                            title = titleName
                                        )
                                        if (savedUri != null) {
                                            Toast.makeText(context, "Profile Picture Saved to Gallery!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Could not save profile picture", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF128C7E),
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download Selected Profile Photo",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Search Profile to Download",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Tabs Row
            TabRow(
                selectedTabIndex = selectedMainTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    text = { Text("Status Saver", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) }
                )
                Tab(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    text = { Text("Profile Photo", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (selectedMainTab == 0) {
                    StatusSaverTab(
                        selectedStatusItem = selectedStatusItem,
                        onStatusSelected = { selectedStatusItem = it },
                        onPlayVideo = onPlayVideo
                    )
                } else {
                    ProfileDownloaderTab(
                        onProfileFetched = { avatarUrl, phone ->
                            fetchedAvatarUrl = avatarUrl
                            fullPhoneNumber = phone
                            isProfileFetched = true
                        },
                        onProfileReset = {
                            fetchedAvatarUrl = ""
                            fullPhoneNumber = ""
                            isProfileFetched = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusSaverTab(
    selectedStatusItem: StatusItem?,
    onStatusSelected: (StatusItem?) -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Status Type: 0 = Normal WhatsApp, 1 = WhatsApp Business
    var statusType by remember { mutableStateOf(0) }
    
    val sharedPrefs = remember { context.getSharedPreferences("whatsapp_saver_prefs", Context.MODE_PRIVATE) }
    val uriKey = if (statusType == 0) "wa_statuses_uri" else "wa_business_statuses_uri"
    
    var savedUriStr by remember(statusType) {
        mutableStateOf(sharedPrefs.getString(uriKey, null))
    }
    
    var statusesList by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<StatusItem?>(null) }
    
    // SAF Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Request persistable permission
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                // Save string URI in SharedPreferences
                sharedPrefs.edit().putString(uriKey, uri.toString()).apply()
                savedUriStr = uri.toString()
                Toast.makeText(context, "Permission granted!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to persist permission", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Load statuses when permission URI or tab changes
    val loadStatuses = {
        if (!savedUriStr.isNullOrEmpty()) {
            isLoading = true
            coroutineScope.launch {
                val list = withContext(Dispatchers.IO) {
                    getStatusesFromUri(context, savedUriStr)
                }
                statusesList = list
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(savedUriStr) {
        loadStatuses()
    }

    // Auto-select first item when statusesList changes or is loaded
    LaunchedEffect(statusesList) {
        if (statusesList.isNotEmpty()) {
            if (selectedStatusItem == null || !statusesList.any { it.id == selectedStatusItem.id }) {
                onStatusSelected(statusesList.first())
            }
        } else {
            onStatusSelected(null)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // WhatsApp Selector Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { statusType = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (statusType == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (statusType == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            
            Button(
                onClick = { statusType = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (statusType == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (statusType == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("WA Business", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        
        if (savedUriStr.isNullOrEmpty()) {
            // Permission Instruction Page
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Text(
                        text = "Access Permission Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "To fetch and save active WhatsApp statuses, you need to grant permission to the WhatsApp private folder in Android media storage.\n\n" +
                               "1. Click the button below.\n" +
                               "2. A file chooser will open.\n" +
                               "3. Tap 'USE THIS FOLDER' at the bottom of the screen, and click 'ALLOW'.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    
                    Button(
                        onClick = {
                            val initialUri = getStatusesInitialUri(statusType)
                            folderPickerLauncher.launch(initialUri)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Permission")
                    }
                }
            }
        } else {
            // Folder access has been granted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Statuses (${statusesList.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { loadStatuses() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            // Reset permission
                            sharedPrefs.edit().remove(uriKey).apply()
                            savedUriStr = null
                            statusesList = emptyList()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Reset Permission",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (statusesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            text = "No active statuses found.\nOpen WhatsApp, watch some statuses, and check back!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Display Grid of statuses
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(statusesList, key = { it.id }) { item ->
                        val isSelected = selectedStatusItem?.id == item.id
                        StatusGridCard(
                            item = item,
                            isSelected = isSelected,
                            onSelect = {
                                onStatusSelected(item)
                            },
                            onPlay = {
                                previewItem = item
                            },
                            onPreviewImage = {
                                previewItem = item
                            },
                            onSave = {
                                coroutineScope.launch {
                                    val savedUri = MediaStoreManager.saveMediaFromUri(
                                        context = context,
                                        srcUri = item.uri,
                                        title = item.name.substringBeforeLast("."),
                                        isVideo = item.isVideo
                                    )
                                    if (savedUri != null) {
                                        Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save status", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onShare = {
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (item.isVideo) "video/mp4" else "image/*"
                                        putExtra(Intent.EXTRA_STREAM, item.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Unified Preview Dialog for photo and video status
    if (previewItem != null) {
        MediaPreviewDialog(
            item = previewItem!!,
            onDismiss = { previewItem = null },
            onSave = {
                coroutineScope.launch {
                    val savedUri = MediaStoreManager.saveMediaFromUri(
                        context = context,
                        srcUri = previewItem!!.uri,
                        title = previewItem!!.name.substringBeforeLast("."),
                        isVideo = previewItem!!.isVideo
                    )
                    if (savedUri != null) {
                        Toast.makeText(context, "Successfully Downloaded Status to Gallery!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to download status", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShare = {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = if (previewItem!!.isVideo) "video/mp4" else "image/*"
                        putExtra(Intent.EXTRA_STREAM, previewItem!!.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not share status", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun StatusGridCard(
    item: StatusItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onPreviewImage: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val border = if (isSelected) {
        BorderStroke(3.dp, Color(0xFF128C7E))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
        ),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail / Media Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.Black)
                    .clickable {
                        onSelect()
                        if (item.isVideo) onPlay() else onPreviewImage()
                    }
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Preview badge overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Preview",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "PREVIEW",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Play Button overlay for video
                if (item.isVideo) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Checkmark badge overlay when selected
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                            .background(Color(0xFF128C7E), CircleShape)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Date badge overlay
                val dateStr = remember(item.lastModified) {
                    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(item.lastModified))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = dateStr,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Action Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Button(
                    onClick = onSave,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color(0xFF128C7E) else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProfileDownloaderTab(
    onProfileFetched: (avatarUrl: String, fullPhone: String) -> Unit,
    onProfileReset: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var countryCode by remember { mutableStateOf("+91") }
    var phoneNumber by remember { mutableStateOf("") }
    
    var showPreviewCard by remember { mutableStateOf(false) }
    var fetchedAvatarUrl by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    
    val fullPhoneNumber = remember(countryCode, phoneNumber) {
        val cleanCC = countryCode.replace("+", "").trim()
        val cleanNum = phoneNumber.replace(" ", "").replace("-", "").trim()
        "$cleanCC$cleanNum"
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Fetch Profile Picture",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Country Code selector
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { 
                            countryCode = it 
                            onProfileReset()
                            showPreviewCard = false
                        },
                        label = { Text("Code") },
                        placeholder = { Text("+1") },
                        modifier = Modifier.width(80.dp),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                    
                    // Phone Number text input
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { 
                            phoneNumber = it 
                            onProfileReset()
                            showPreviewCard = false
                        },
                        label = { Text("WhatsApp Number") },
                        placeholder = { Text("9876543210") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
                
                Button(
                    onClick = {
                        val cleanNum = phoneNumber.replace(Regex("[^0-9]"), "")
                        if (cleanNum.isEmpty()) {
                            Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        isFetching = true
                        coroutineScope.launch {
                            // Unavatar WhatsApp fetch URL: https://unavatar.io/whatsapp/{phone_number}
                            fetchedAvatarUrl = "https://unavatar.io/whatsapp/$fullPhoneNumber"
                            showPreviewCard = true
                            isFetching = false
                            onProfileFetched(fetchedAvatarUrl, fullPhoneNumber)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Profile Pic")
                    }
                }
            }
        }
        
        if (showPreviewCard) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Result for +$fullPhoneNumber",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Large Image preview
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(Color.Black.copy(alpha = 0.1f), CircleShape)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = fetchedAvatarUrl,
                            contentDescription = "WhatsApp DP",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Text(
                        text = "Note: If the avatar is the generic placeholder or doesn't load, the number might not have a public profile photo set or the number is invalid.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Open Chat button
                        Button(
                            onClick = {
                                try {
                                    val uri = Uri.parse("https://wa.me/$fullPhoneNumber")
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Chat")
                        }
                        
                        // Download DP button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val titleName = "WA_DP_+$fullPhoneNumber"
                                    val savedUri = MediaStoreManager.downloadAndSaveImage(
                                        context = context,
                                        imageUrl = fetchedAvatarUrl,
                                        title = titleName
                                    )
                                    if (savedUri != null) {
                                        Toast.makeText(context, "Profile Picture Saved to Gallery!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Could not save image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Image")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaPreviewDialog(
    item: StatusItem,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Fullscreen preview
            if (item.isVideo) {
                VideoPlayerView(
                    videoUri = item.uri.toString(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = "Fullscreen preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Top Controls overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                    
                    IconButton(
                        onClick = {
                            onSave()
                            onDismiss()
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Save", tint = Color.White)
                    }
                }
            }
            
            // Bottom Info Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val sizeStr = remember(item.size) {
                    val kb = item.size / 1024.0
                    val mb = kb / 1024.0
                    if (mb >= 1) String.format("%.2f MB", mb) else String.format("%.1f KB", kb)
                }
                
                val dateStr = remember(item.lastModified) {
                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(item.lastModified))
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.isVideo) "Video • $sizeStr" else "Photo • $sizeStr",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Text(
                        text = dateStr,
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        onSave()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF128C7E),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// Helpers for WhatsApp SAF URIs
fun getStatusesInitialUri(statusType: Int): Uri {
    val folderPath = if (statusType == 0) {
        "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    } else {
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    }
    return Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + folderPath.replace("/", "%2F"))
}

fun getStatusesFromUri(context: Context, treeUriStr: String?): List<StatusItem> {
    if (treeUriStr.isNullOrEmpty()) return emptyList()
    val list = mutableListOf<StatusItem>()
    try {
        val treeUri = Uri.parse(treeUriStr)
        val document = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val files = document.listFiles()
        for (file in files) {
            val name = file.name ?: ""
            if (file.isFile && (name.endsWith(".mp4") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif"))) {
                val isVideo = name.endsWith(".mp4")
                list.add(
                    StatusItem(
                        id = file.uri.toString(),
                        uri = file.uri,
                        name = name,
                        isVideo = isVideo,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.sortedByDescending { it.lastModified }
}
