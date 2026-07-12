package com.example.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.download.DownloadState
import com.example.extractor.VideoInfoParser

@Composable
fun DownloadProgressScreen(
    viewModel: VideoDownloaderViewModel,
    onBackToHome: () -> Unit,
    onPlayVideo: (uri: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadState by viewModel.downloadState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (val state = downloadState) {
                is DownloadState.PreparingDownload -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Text(
                        text = "Preparing Download...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Connecting to media servers and preparing cache folders.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadState.Downloading -> {
                    val progress = state.progress
                    val percentInt = progress.percent.toInt()
                    
                    Text(
                        text = "Downloading Media",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Linear Progress Bar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$percentInt%",
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Download Statistics Details Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Downloaded", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val sizeText = if (progress.totalBytes != null) {
                                    "${VideoInfoParser.formatFileSize(progress.downloadedBytes)} / ${VideoInfoParser.formatFileSize(progress.totalBytes)}"
                                } else {
                                    VideoInfoParser.formatFileSize(progress.downloadedBytes)
                                }
                                Text(sizeText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speed", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val speedText = progress.speedBytesPerSecond?.let {
                                    "${VideoInfoParser.formatFileSize(it)}/s"
                                } ?: "Calculating..."
                                Text(speedText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ETA", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val etaText = progress.etaSeconds?.let {
                                    if (it < 60) "$it seconds" else "${it / 60}m ${it % 60}s"
                                } ?: "Calculating..."
                                Text(etaText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.cancelDownload() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Download")
                    }
                }

                is DownloadState.Saving -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Text(
                        text = "Saving to Gallery...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Writing file to Movies/VideoDownloader folder.",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadState.Completed -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Download Completed!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = state.title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onPlayVideo(state.filePath, state.title) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Video")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.resetExtraction()
                                onBackToHome()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back to Home")
                        }
                    }
                }

                is DownloadState.Failed -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Download Failed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.resetExtraction()
                            onBackToHome()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Back to Home")
                    }
                }

                is DownloadState.Cancelled -> {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "Download Cancelled",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.resetExtraction()
                            onBackToHome()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back to Home")
                    }
                }

                else -> {
                    // Fallback to home
                    LaunchedEffect(Unit) {
                        onBackToHome()
                    }
                }
            }
        }
    }
}
