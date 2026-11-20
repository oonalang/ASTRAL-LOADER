package kryvex.loader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

class KryvexGradientDrawable : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val image by lazy { buildBitmap() }

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(image, null, bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private fun buildBitmap(): Bitmap {
        val width = 360
        val height = 800
        val xStops = floatArrayOf(0f, 0.12f, 0.27f, 0.50f, 0.72f, 1f)
        val yStops = floatArrayOf(0f, 0.18f, 0.34f, 0.51f, 0.68f, 0.82f, 1f)
        val palette = arrayOf(
        intArrayOf(0xFF3E2F30.toInt(), 0xFF3B2736.toInt(), 0xFF2B283E.toInt(), 0xFF212F42.toInt(), 0xFF203043.toInt(), 0xFF223242.toInt()),
        intArrayOf(0xFF3F312E.toInt(), 0xFF392637.toInt(), 0xFF29283D.toInt(), 0xFF1E2E41.toInt(), 0xFF202F42.toInt(), 0xFF202F42.toInt()),
        intArrayOf(0xFF3E2E30.toInt(), 0xFF352539.toInt(), 0xFF26283D.toInt(), 0xFF1D2C41.toInt(), 0xFF1D2C40.toInt(), 0xFF1F2F41.toInt()),
        intArrayOf(0xFF3C2836.toInt(), 0xFF2E253C.toInt(), 0xFF22293F.toInt(), 0xFF1B2C41.toInt(), 0xFF1B2C41.toInt(), 0xFF1B2C41.toInt()),
        intArrayOf(0xFF2D263C.toInt(), 0xFF23283E.toInt(), 0xFF1D2A41.toInt(), 0xFF1A2A41.toInt(), 0xFF1B2D3E.toInt(), 0xFF1D3039.toInt()),
        intArrayOf(0xFF1F283F.toInt(), 0xFF1D293F.toInt(), 0xFF1B2A41.toInt(), 0xFF1B2C3F.toInt(), 0xFF1D3337.toInt(), 0xFF20372E.toInt()),
        intArrayOf(0xFF1A293E.toInt(), 0xFF1A293E.toInt(), 0xFF1C2C3C.toInt(), 0xFF1E3432.toInt(), 0xFF20372F.toInt(), 0xFF21392C.toInt())
        )

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val fy = y.toFloat() / (height - 1)
            val yi = segment(yStops, fy)
            val ty = ((fy - yStops[yi]) / (yStops[yi + 1] - yStops[yi])).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val fx = x.toFloat() / (width - 1)
                val xi = segment(xStops, fx)
                val tx = ((fx - xStops[xi]) / (xStops[xi + 1] - xStops[xi])).coerceIn(0f, 1f)
                val top = blend(palette[yi][xi], palette[yi][xi + 1], tx)
                val bottom = blend(palette[yi + 1][xi], palette[yi + 1][xi + 1], tx)
                pixels[y * width + x] = blend(top, bottom, ty)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun segment(stops: FloatArray, position: Float): Int {
        for (i in 0 until stops.size - 2) {
            if (position < stops[i + 1]) return i
        }
        return stops.size - 2
    }

    private fun blend(first: Int, second: Int, fraction: Float): Int {
        val r = (Color.red(first) * (1f - fraction) + Color.red(second) * fraction).toInt()
        val g = (Color.green(first) * (1f - fraction) + Color.green(second) * fraction).toInt()
        val b = (Color.blue(first) * (1f - fraction) + Color.blue(second) * fraction).toInt()
        return Color.rgb(r, g, b)
    }
}
