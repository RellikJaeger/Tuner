package de.moekadu.tuner

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat

class TouchControlDrawable(context: Context, tint: Int, drawableId: Int) {
    private val drawable = ContextCompat.getDrawable(context, drawableId)?.mutate()

    private val aspectRatio = (drawable?.intrinsicHeight?.toFloat() ?: 1f) / (drawable?.intrinsicWidth?.toFloat() ?: 1f)
    private var width = 0f
    private var height = 0f

    private val paint = Paint().apply {
        colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
    }

    private var bitmap: Bitmap? = null

    fun setSize(width: Float = USE_ASPECT_RATIO, height: Float = USE_ASPECT_RATIO) {
        require(!(width == USE_ASPECT_RATIO && height == USE_ASPECT_RATIO))
        val newWidth = if (width == USE_ASPECT_RATIO) height / aspectRatio else width
        val newHeight = if (height == USE_ASPECT_RATIO) width * aspectRatio else height

        if (newWidth != this.width || newHeight != this.height) {
//            Log.v("Tuner", "PlotDrawable.setSize: newWidth=$newWidth, newHeight=$newHeight")
            val newBitmap = Bitmap.createBitmap(newWidth.toInt(), newHeight.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(newBitmap)
            drawable?.setBounds(0 ,0, canvas.width, canvas.height)
            drawable?.draw(canvas)
            this.width = newWidth
            this.height = newHeight
            bitmap?.recycle()
            bitmap = newBitmap
        }
    }

    fun drawToCanvas(xPosition: Float, yPosition: Float, anchor: MarkAnchor, canvas: Canvas?) {
        if (height != 0f && width != 0f) {
            val x = when (anchor) {
                MarkAnchor.West, MarkAnchor.SouthWest, MarkAnchor.NorthWest -> xPosition
                MarkAnchor.Center, MarkAnchor.South, MarkAnchor.North -> xPosition - 0.5f * width
                MarkAnchor.East, MarkAnchor.SouthEast, MarkAnchor.NorthEast -> xPosition - width
            }

            val y = when (anchor) {
                MarkAnchor.North, MarkAnchor.NorthWest, MarkAnchor.NorthEast -> yPosition
                MarkAnchor.Center, MarkAnchor.West, MarkAnchor.East -> yPosition - 0.5f * height
                MarkAnchor.South, MarkAnchor.SouthWest, MarkAnchor.SouthEast -> yPosition - height
            }

            bitmap?.let {
                canvas?.drawBitmap(it, x, y, paint)
            }
        }
    }

    companion object {
        const val USE_ASPECT_RATIO = Float.MAX_VALUE
    }
}