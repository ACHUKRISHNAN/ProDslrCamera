package com.pro.dslrcamera

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val GALLERY_TAG = "CapturedGalleryScreen"

enum class GalleryViewMode {
    PAGER,
    GRID
}

data class CapturedPhotoItem(
    val uri: Uri,
    val displayName: String,
    val dateAddedSec: Long
)

suspend fun queryCameraPhotos(context: Context): List<CapturedPhotoItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<CapturedPhotoItem>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
    )
    val baseSelection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        "$baseSelection AND ${MediaStore.Images.Media.IS_TRASHED} = 0"
    } else {
        baseSelection
    }
    val selectionArgs = arrayOf("%${CameraShaderRenderer.DEFAULT_CAMERA_RELATIVE_PATH}%")
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"

    try {
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                val dateAdded = cursor.getLong(dateCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                items.add(
                    CapturedPhotoItem(
                        uri = contentUri,
                        displayName = name,
                        dateAddedSec = dateAdded
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e(GALLERY_TAG, "Failed querying camera photos", e)
    }
    items
}

suspend fun loadPhotoBitmap(
    context: Context,
    uri: Uri,
    maxDim: Int = 1600
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val boundsOpts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }

        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return@withContext null

        var sampleSize = 1
        val longestSide = max(srcW, srcH)
        while (longestSide / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        }
    } catch (e: Exception) {
        Log.e(GALLERY_TAG, "Failed decoding bitmap for uri=$uri", e)
        null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CapturedGalleryScreen(
    refreshTrigger: Int,
    onManualRefresh: () -> Unit,
    onClose: () -> Unit,
    onDeletePhoto: (CapturedPhotoItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localRefreshKey by remember { mutableIntStateOf(0) }
    val loadedPhotos by produceState<List<CapturedPhotoItem>>(
        initialValue = emptyList(),
        key1 = refreshTrigger,
        key2 = localRefreshKey
    ) {
        value = queryCameraPhotos(context)
    }

    var suppressedUris by remember { mutableStateOf(setOf<Uri>()) }
    var pendingTrashItem by remember { mutableStateOf<CapturedPhotoItem?>(null) }
    var undoToken by remember { mutableIntStateOf(0) }
    var viewMode by remember { mutableStateOf(GalleryViewMode.PAGER) }

    LaunchedEffect(loadedPhotos) {
        val activeUriSet = loadedPhotos.map { it.uri }.toSet()
        val stillInPendingUndo = pendingTrashItem?.uri
        suppressedUris = suppressedUris.filterTo(mutableSetOf()) { uri ->
            uri in activeUriSet || uri == stillInPendingUndo
        }
    }

    val photos = remember(loadedPhotos, suppressedUris) {
        loadedPhotos.filterNot { it.uri in suppressedUris }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { photos.size }
    )

    val trashIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val item = pendingTrashItem
        pendingTrashItem = null
        if (result.resultCode == Activity.RESULT_OK) {
            if (item != null) {
                suppressedUris = suppressedUris + item.uri
                onDeletePhoto(item)
            }
            localRefreshKey += 1
        } else {
            if (item != null) {
                suppressedUris = suppressedUris - item.uri
            }
        }
    }

    fun executeMoveToTrashOrDelete(item: CapturedPhotoItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createTrashRequest(
                    context.contentResolver,
                    listOf(item.uri),
                    true
                )
                pendingTrashItem = item
                val intentSender: IntentSender = pendingIntent.intentSender
                trashIntentLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            } catch (e: Exception) {
                Log.e(GALLERY_TAG, "createTrashRequest failed for ${item.uri}", e)
                suppressedUris = suppressedUris - item.uri
                pendingTrashItem = null
            }
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(item.uri, null, null)
                    withContext(Dispatchers.Main) {
                        pendingTrashItem = null
                        suppressedUris = suppressedUris + item.uri
                        localRefreshKey += 1
                        onDeletePhoto(item)
                    }
                } catch (e: Exception) {
                    Log.e(GALLERY_TAG, "Delete failed on legacy API for ${item.uri}", e)
                    withContext(Dispatchers.Main) {
                        pendingTrashItem = null
                        suppressedUris = suppressedUris - item.uri
                    }
                }
            }
        }
    }

    fun stageDeleteWithUndo(item: CapturedPhotoItem) {
        suppressedUris = suppressedUris + item.uri
        pendingTrashItem = item
        undoToken += 1
    }

    LaunchedEffect(undoToken, pendingTrashItem) {
        val queued = pendingTrashItem ?: return@LaunchedEffect
        val currentToken = undoToken
        delay(3500L)
        if (undoToken == currentToken && pendingTrashItem?.uri == queued.uri) {
            executeMoveToTrashOrDelete(queued)
        }
    }

    LaunchedEffect(photos.size) {
        if (photos.isNotEmpty() && pagerState.currentPage >= photos.size) {
            pagerState.scrollToPage(photos.lastIndex)
        }
    }

    var showChrome by remember { mutableStateOf(true) }
    var activePageZoomed by remember { mutableStateOf(false) }

    val activePhoto = photos.getOrNull(pagerState.currentPage)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (photos.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = "NO PHOTOS IN ROLL",
                    color = CameraAmberAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Captured shots in DCIM/Camera will appear here.",
                    color = Color(0xFFA1A1AA),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CameraAmberAccent)
                        .clickable { onClose() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "BACK TO CAMERA",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else if (viewMode == GalleryViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    top = 74.dp,
                    bottom = 84.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(photos, key = { _, item -> item.uri }) { index: Int, item: CapturedPhotoItem ->
                    val thumb by produceState<Bitmap?>(
                        initialValue = null,
                        key1 = item.uri
                    ) {
                        value = loadPhotoBitmap(context, item.uri, maxDim = 420)
                    }
                    val isCurrent = index == pagerState.currentPage

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF18181B))
                            .border(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) CameraAmberAccent else Color(0xFF27272A),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                viewMode = GalleryViewMode.PAGER
                                scope.launch {
                                    pagerState.scrollToPage(index)
                                }
                            }
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb!!.asImageBitmap(),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "...",
                                color = Color(0xFF71717A),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !activePageZoomed,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex: Int ->
                val item = photos[pageIndex]
                val bitmap by produceState<Bitmap?>(
                    initialValue = null,
                    key1 = item.uri
                ) {
                    value = loadPhotoBitmap(context, item.uri, maxDim = 2048)
                }

                var scale by remember(item.uri) { mutableFloatStateOf(1f) }
                var panOffset by remember(item.uri) { mutableStateOf(Offset.Zero) }

                LaunchedEffect(pagerState.currentPage, scale) {
                    if (pagerState.currentPage != pageIndex) {
                        scale = 1f
                        panOffset = Offset.Zero
                    } else {
                        activePageZoomed = scale > 1.02f
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(item.uri) {
                            detectTapGestures(
                                onTap = { showChrome = !showChrome },
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        scale = 2.2f
                                    }
                                }
                            )
                        }
                        .pointerInput(item.uri, scale) {
                            if (scale > 1.01f) {
                                detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = nextScale
                                    if (nextScale <= 1.01f) {
                                        panOffset = Offset.Zero
                                    } else {
                                        val maxX = (size.width * (nextScale - 1f)) / 2f
                                        val maxY = (size.height * (nextScale - 1f)) / 2f
                                        panOffset = Offset(
                                            x = (panOffset.x + pan.x).coerceIn(-maxX, maxX),
                                            y = (panOffset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                        )
                    } else {
                        Text(
                            text = "LOADING...",
                            color = CameraAmberAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (showChrome) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CameraHudDark)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Camera",
                        tint = CameraAmberAccent
                    )
                }

                Text(
                    text = if (photos.isEmpty()) "0 / 0" else "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewMode = if (viewMode == GalleryViewMode.PAGER) {
                                GalleryViewMode.GRID
                            } else {
                                GalleryViewMode.PAGER
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (viewMode == GalleryViewMode.PAGER) {
                                Icons.Default.GridView
                            } else {
                                Icons.Default.ViewCarousel
                            },
                            contentDescription = if (viewMode == GalleryViewMode.PAGER) "Grid View" else "Pager View",
                            tint = CameraAmberAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            localRefreshKey += 1
                            onManualRefresh()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Photos",
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        enabled = activePhoto != null,
                        onClick = { activePhoto?.let { stageDeleteWithUndo(it) } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Move to Trash",
                            tint = if (activePhoto != null) Color(0xFFF87171) else Color(0xFF52525B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (pendingTrashItem != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1C1917))
                    .border(1.dp, CameraAmberAccent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "MOVING TO TRASH: ${pendingTrashItem?.displayName ?: ""}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "UNDO",
                    color = CameraAmberAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF27272A))
                        .clickable {
                            val restored = pendingTrashItem
                            pendingTrashItem = null
                            undoToken += 1
                            if (restored != null) {
                                suppressedUris = suppressedUris - restored.uri
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        } else if (showChrome && activePhoto != null && viewMode == GalleryViewMode.PAGER) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CameraHudDark)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = activePhoto.displayName,
                    color = Color(0xFFE4E4E7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}