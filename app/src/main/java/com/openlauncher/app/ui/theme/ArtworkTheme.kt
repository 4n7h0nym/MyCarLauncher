package com.openlauncher.app.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.openlauncher.app.model.NowPlayingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
fun rememberAlbumAccent(state: NowPlayingState?, enabled: Boolean): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(null, enabled, state?.artUri, state?.albumArt) {
        value = null
        if (enabled && state != null) value = withContext(Dispatchers.IO) {
            try {
                val loaded = state.albumArt ?: (context.imageLoader.execute(
                    ImageRequest.Builder(context).data(state.artUri).size(64).allowHardware(false).build()
                ).drawable as? BitmapDrawable)?.bitmap
                loaded?.let { source ->
                    val readable = if (android.os.Build.VERSION.SDK_INT >= 26 && source.config == Bitmap.Config.HARDWARE)
                        source.copy(Bitmap.Config.ARGB_8888, false) else source
                    val sample = Bitmap.createScaledBitmap(readable, 16, 16, true)
                    val hsl = FloatArray(3)
                    var best: FloatArray? = null
                    var score = 0f
                    for (y in 0 until 16) for (x in 0 until 16) {
                        val pixel = sample.getPixel(x, y)
                        ColorUtils.colorToHSL(pixel, hsl)
                        val candidate = hsl[1] * (1f - kotlin.math.abs(hsl[2] - 0.5f) * 2f)
                        if (android.graphics.Color.alpha(pixel) > 200 && candidate > score) {
                            score = candidate; best = hsl.copyOf()
                        }
                    }
                    if (sample !== readable) sample.recycle()
                    if (readable !== source) readable.recycle()
                    best?.takeIf { score > 0.15f }?.let {
                        it[1] = it[1].coerceIn(0.35f, 0.65f); it[2] = 0.72f
                        Color(ColorUtils.HSLToColor(it))
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
        }
    }
    return color
}

/** Small, cached, two-pass box blur; Coil executes transformations off the main thread. */
class WallpaperBlur(private val radius: Int) : Transformation {
    override val cacheKey = "wallpaper-blur-v1-$radius"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val scale = minOf(1f, 384f / maxOf(input.width, input.height))
        val w = (input.width * scale).toInt().coerceAtLeast(1)
        val h = (input.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, w, h, true)
        val pixels = IntArray(w * h); small.getPixels(pixels, 0, w, 0, 0, w, h)
        val temp = IntArray(pixels.size)
        val r = (radius * scale).toInt().coerceIn(1, 12)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            for (y in 0 until h) for (x in 0 until w) {
                var red = 0; var green = 0; var blue = 0
                for (d in -r..r) {
                    val xx = if (horizontal) (x+d).coerceIn(0,w-1) else x
                    val yy = if (horizontal) y else (y+d).coerceIn(0,h-1)
                    val c = src[yy*w+xx]
                    red += c shr 16 and 255; green += c shr 8 and 255; blue += c and 255
                }
                val count = 2*r+1
                dst[y*w+x] = android.graphics.Color.rgb(red/count, green/count, blue/count)
            }
        }
        pass(pixels, temp, true); pass(temp, pixels, false)
        if (small !== input) small.recycle()
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
