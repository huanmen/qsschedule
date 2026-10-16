package cn.qztc.timetable.background

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import cn.qztc.timetable.AppPreferences
import cn.qztc.timetable.core.ui.background.BackgroundMotion
import cn.qztc.timetable.core.ui.background.BackgroundSource
import cn.qztc.timetable.core.ui.theme.LocalQztcColors
import cn.qztc.timetable.core.ui.theme.QztcPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PageBackground(
    preferences: AppPreferences?,
    darkTheme: Boolean,
    reduceMotion: Boolean,
    importedImagePaths: List<String> = emptyList(),
    carouselEnabled: Boolean = true,
    carouselIntervalSeconds: Int = 8,
    modifier: Modifier = Modifier,
    onBackdropColor: (Color) -> Unit = {},
    preparedImage: android.graphics.Bitmap? = null,
) {
    val colors = LocalQztcColors.current
    // Preferences arrive asynchronously; never render the default scene before
    // knowing whether the user selected an image.
    if (preferences == null || preferences.backgroundMotion == BackgroundMotion.OFF) {
        LaunchedEffect(colors.appBg) { onBackdropColor(colors.appBg) }
        Canvas(modifier.fillMaxSize()) { drawRect(colors.appBg) }
        return
    }
    when {
        preferences.backgroundSource == BackgroundSource.IMAGE && preferences.backgroundImagePath != null &&
            preparedImage != null -> {
            ImageBackground(
                path = preferences.backgroundImagePath,
                preparedImage = preparedImage,
                importedPaths = importedImagePaths,
                rotate = preferences.backgroundMotion == BackgroundMotion.DYNAMIC &&
                    preferences.backgroundCarouselEnabled && carouselEnabled && !reduceMotion,
                intervalSeconds = preferences.backgroundCarouselIntervalSeconds,
                positionX = preferences.backgroundPositionX,
                positionY = preferences.backgroundPositionY,
                darkTheme = darkTheme,
                modifier = modifier,
                onBackdropColor = onBackdropColor,
            )
        }

        else -> SceneBackground(
            darkTheme = darkTheme,
            palette = preferences.palette,
            animated = preferences.backgroundMotion == BackgroundMotion.DYNAMIC && !reduceMotion,
            modifier = modifier,
            onBackdropColor = onBackdropColor,
        )
    }
}

@Composable
private fun SceneBackground(darkTheme: Boolean, animated: Boolean, palette: QztcPalette, modifier: Modifier = Modifier, onBackdropColor: (Color) -> Unit = {}) {
    val sky = remember(palette, darkTheme) { when (palette) {
        QztcPalette.ORIGINAL -> if (darkTheme) listOf(0xFF060D1D, 0xFF0C1830, 0xFF1A2A4A, 0xFF2C3A58)
            else listOf(0xFF7FC4E4, 0xFFA9D9E9, 0xFFEFE0CB, 0xFFF6CFA2)
        QztcPalette.SODA -> if (darkTheme) listOf(0xFF0C1D2C, 0xFF142D3F, 0xFF20414C, 0xFF284D54)
            else listOf(0xFFB8DCE9, 0xFFD8EDF2, 0xFFEAF5F5, 0xFFBBDDD9)
        QztcPalette.CITRUS -> if (darkTheme) listOf(0xFF121C21, 0xFF202E26, 0xFF2E3D2B, 0xFF3D4B30)
            else listOf(0xFFD8E9EC, 0xFFEEF3DF, 0xFFF3F3D5, 0xFFD6E3B8)
    }.map { Color(it) } }
    LaunchedEffect(sky) { onBackdropColor(sky.first()) }
    val hills = remember(palette, darkTheme) { when (palette) {
        QztcPalette.ORIGINAL -> if (darkTheme) listOf(0xFF152840, 0xFF20354B) else listOf(0xFFA6C8B4, 0xFF6D9C88)
        QztcPalette.SODA -> if (darkTheme) listOf(0xFF1C3543, 0xFF244953) else listOf(0xFFA5CDD2, 0xFF79ABB5)
        QztcPalette.CITRUS -> if (darkTheme) listOf(0xFF293928, 0xFF354A2E) else listOf(0xFFBDCF99, 0xFF97B274)
    }.map { Color(it) } }
    val cloudOffset = remember { mutableStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        val startedAt = android.os.SystemClock.uptimeMillis()
        // Clouds move only a fraction of a pixel per frame; avoid a full-screen
        // backdrop capture at the display's 120 Hz while the page is idle.
        while (true) {
            val phase = ((android.os.SystemClock.uptimeMillis() - startedAt) % 240_000L) / 120_000f
            cloudOffset.value = -.08f + .2f * if (phase <= 1f) phase else 2f - phase
            delay(100)
        }
    }

    Canvas(modifier.fillMaxSize()) {
        if (darkTheme) {
            drawRect(
                Brush.verticalGradient(
                    0f to sky[0], .42f to sky[1], .78f to sky[2], 1f to sky[3],
                ),
            )
            drawCircle(Color(0xFFFFF7D6).copy(alpha = .94f), size.minDimension * .055f, Offset(size.width * .18f, size.height * .13f))
            val stars = listOf(.08f to .18f, .31f to .09f, .47f to .20f, .67f to .12f, .84f to .23f, .92f to .08f, .17f to .33f, .38f to .29f, .58f to .35f, .76f to .31f, .89f to .42f, .27f to .44f, .51f to .49f, .71f to .52f, .11f to .55f)
            stars.forEachIndexed { index, point ->
                drawCircle(Color.White.copy(alpha = .48f + (index % 3) * .13f), radius = 1.2f + (index % 2), center = Offset(size.width * point.first, size.height * point.second))
            }
            drawMountains(hills[0], .78f, .13f)
            drawMountains(hills[1], .84f, .09f)
        } else {
            drawRect(
                Brush.verticalGradient(
                    0f to sky[0], .34f to sky[1], .68f to sky[2], 1f to sky[3],
                ),
            )
            if (palette != QztcPalette.CITRUS) {
                drawSun(Offset(size.width * .82f, size.height * .13f), size.minDimension * .065f, Color(0xFFF9DEA0))
            }
            // Read animation state in drawing so each frame does not recompose the scene.
            val shift = size.width * cloudOffset.value
            drawCloud(Offset(size.width * .12f + shift, size.height * .22f), size.width * .15f, .28f)
            drawCloud(Offset(size.width * .60f - shift * .55f, size.height * .31f), size.width * .19f, .22f)
            drawCloud(Offset(size.width * .28f + shift * .35f, size.height * .48f), size.width * .12f, .16f)
            if (palette == QztcPalette.ORIGINAL) {
                drawMountains(hills[0], .77f, .15f)
                drawMountains(hills[1], .84f, .11f)
            }
            when (palette) {
                QztcPalette.SODA -> {
                    // Sea-salt bubbles and a glassy wave line make Soda read as
                    // a fizzy drink scene instead of a recolored landscape.
                    listOf(.16f to .58f, .29f to .64f, .72f to .52f, .84f to .61f).forEach { (x, y) ->
                        drawCircle(Color.White.copy(alpha = .28f), size.minDimension * .028f,
                            Offset(size.width * x, size.height * y))
                    }
                    val wave = Path().apply {
                        moveTo(0f, size.height * .68f)
                        cubicTo(size.width * .2f, size.height * .63f, size.width * .36f, size.height * .74f,
                            size.width * .56f, size.height * .68f)
                        cubicTo(size.width * .74f, size.height * .63f, size.width * .86f, size.height * .72f,
                            size.width, size.height * .67f)
                    }
                    drawPath(wave, Color.White.copy(alpha = .24f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * .008f))
                    drawOval(Color(0xFF8FD5DE).copy(alpha = .30f), Offset(size.width * .03f, size.height * .76f), Size(size.width * .42f, size.height * .24f))
                    drawOval(Color(0xFFB8E5E8).copy(alpha = .26f), Offset(size.width * .42f, size.height * .80f), Size(size.width * .62f, size.height * .20f))
                }
                QztcPalette.CITRUS -> {
                    // Citrus uses a warm sun and leaf silhouettes rather than
                    // the Soda bubbles, so the default scene has its own identity.
                    drawSun(Offset(size.width * .80f, size.height * .18f), size.minDimension * .085f, Color(0xFFFFD56A))
                    drawOval(Color(0xFF6E9A62).copy(alpha = .30f),
                        topLeft = Offset(size.width * .12f, size.height * .65f),
                        size = Size(size.width * .16f, size.height * .055f))
                    drawOval(Color(0xFF6E9A62).copy(alpha = .24f),
                        topLeft = Offset(size.width * .24f, size.height * .70f),
                        size = Size(size.width * .13f, size.height * .05f))
                    drawOval(Color(0xFFAFC87A).copy(alpha = .32f), Offset(size.width * .0f, size.height * .78f), Size(size.width * .58f, size.height * .22f))
                    drawOval(Color(0xFF89A967).copy(alpha = .28f), Offset(size.width * .48f, size.height * .82f), Size(size.width * .58f, size.height * .18f))
                }
                QztcPalette.ORIGINAL -> Unit
            }
        }
    }
}

@Composable
private fun ImageBackground(
    path: String,
    preparedImage: android.graphics.Bitmap,
    importedPaths: List<String>,
    rotate: Boolean,
    intervalSeconds: Int,
    positionX: Float,
    positionY: Float,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    onBackdropColor: (Color) -> Unit = {},
) {
    val paths = remember(path, importedPaths) { (listOf(path) + importedPaths).distinct() }
    var displayedPath by remember(path, paths) { mutableStateOf(path) }
    LaunchedEffect(paths, rotate) {
        if (!rotate || paths.size < 2) {
            // Pausing the carousel must keep the currently displayed image.
        } else {
            var index = paths.indexOf(displayedPath).coerceAtLeast(0)
            while (true) {
                delay(intervalSeconds.coerceIn(1, 86_400) * 1_000L)
                index = (index + 1) % paths.size
                // Decode before swapping carousel layers so the incoming frame is never empty.
                if (BackgroundBitmapCache.load(paths[index]) != null) displayedPath = paths[index]
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        if (paths.size > 1) {
            Crossfade(targetState = displayedPath, label = "background-image-carousel") { target ->
                BackgroundImageFrame(target, positionX, positionY, darkTheme, preparedImage.takeIf { target == path }) { sample -> onBackdropColor((if(darkTheme) Color(0xFF060C12).copy(alpha=.52f) else Color.White.copy(alpha=.28f)).compositeOver(sample)) }
            }
        } else {
            BackgroundImageFrame(path, positionX, positionY, darkTheme, preparedImage) { sample -> onBackdropColor((if(darkTheme) Color(0xFF060C12).copy(alpha=.52f) else Color.White.copy(alpha=.28f)).compositeOver(sample)) }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                if (darkTheme) {
                    Brush.verticalGradient(listOf(Color(0xFF060C12).copy(alpha = .54f), Color(0xFF060C12).copy(alpha = .30f), Color(0xFF060C12).copy(alpha = .58f)))
                } else {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .13f), Color.White.copy(alpha = .26f)))
                },
            )
        }
    }
}

@Composable
private fun BackgroundImageFrame(path: String, positionX: Float, positionY: Float, darkTheme: Boolean, preparedImage: android.graphics.Bitmap?, onBackdropColor: (Color) -> Unit) {
    var bitmap by remember(path) { mutableStateOf(preparedImage ?: BackgroundBitmapCache.peek(path)) }
    LaunchedEffect(path) {
        if (bitmap == null) bitmap = BackgroundBitmapCache.load(path)
    }
    val configuration = LocalConfiguration.current
    LaunchedEffect(bitmap, positionX, positionY, darkTheme, configuration.screenWidthDp, configuration.screenHeightDp) {
        val image = bitmap ?: return@LaunchedEffect
        val sample = withContext(Dispatchers.Default) {
            val ratio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
            val visibleHeight = minOf(image.height.toFloat(), image.width / ratio)
            val visibleWidth = minOf(image.width.toFloat(), image.height * ratio)
            val left = (image.width-visibleWidth)*positionX.coerceIn(0f,1f)
            val top = (image.height-visibleHeight)*positionY.coerceIn(0f,1f)
            var red=0f;var green=0f;var blue=0f
            for(x in 0..17)for(y in 0..5) {
                val pixel=Color(image.getPixel((left+visibleWidth*(.15f+.7f*x/17)).toInt().coerceIn(0,image.width-1),
                    (top+visibleHeight*(.035f+.055f*y/5)).toInt().coerceIn(0,image.height-1)))
                red+=pixel.red;green+=pixel.green;blue+=pixel.blue
            }
            Color(red/108,green/108,blue/108)
        }
        onBackdropColor(sample)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(positionX.coerceIn(0f, 1f) * 2f - 1f, positionY.coerceIn(0f, 1f) * 2f - 1f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(origin: Offset, width: Float, alpha: Float) {
    val color = Color.White.copy(alpha = alpha)
    drawOval(color, topLeft = origin, size = Size(width, width * .34f))
    drawCircle(color, width * .19f, origin + Offset(width * .28f, 0f))
    drawCircle(color, width * .24f, origin + Offset(width * .53f, -width * .04f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSun(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center)
    val ray = radius * .52f
    val outer = radius * 1.55f
    for (i in 0 until 8) {
        val angle = Math.PI * i / 4.0
        val start = center + Offset((kotlin.math.cos(angle) * outer).toFloat(), (kotlin.math.sin(angle) * outer).toFloat())
        val end = center + Offset((kotlin.math.cos(angle) * (outer + ray)).toFloat(), (kotlin.math.sin(angle) * (outer + ray)).toFloat())
        drawLine(color, start, end, strokeWidth = radius * .07f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMountains(color: Color, baseline: Float, rise: Float) {
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, size.height * baseline)
        cubicTo(size.width * .18f, size.height * (baseline - rise), size.width * .36f, size.height * (baseline + .02f), size.width * .53f, size.height * (baseline - rise * .72f))
        cubicTo(size.width * .72f, size.height * (baseline - rise * 1.2f), size.width * .88f, size.height * (baseline + .01f), size.width, size.height * (baseline - rise * .45f))
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}
