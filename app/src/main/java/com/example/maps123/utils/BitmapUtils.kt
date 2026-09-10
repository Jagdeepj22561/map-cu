package com.example.maps123.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Rect
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

import com.example.shared.PlaceCategory
import com.example.maps123.R

object BitmapUtils {

    fun createPinWithLabel(context: Context, label: String, category: PlaceCategory = PlaceCategory.OTHER): BitmapDescriptor {
        val pinColor = Color.TRANSPARENT

        val iconRes = when (category) {
            PlaceCategory.BLOCK -> R.drawable.ic_block
            PlaceCategory.PARK -> R.drawable.ic_park
            PlaceCategory.BANK -> R.drawable.ic_bank
            else -> null
        }

        val iconText = if (iconRes == null) {
            when (category) {
                PlaceCategory.HOSTEL -> "🛏️"
                PlaceCategory.GATE -> "⛩️"
                else -> "📍"
            }
        } else ""

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, Color.argb(180, 255, 255, 255))
        }

        val textBounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)
        val horizontalPadding = 24f
        val verticalPadding = 16f

        val chipWidth = textBounds.width() + horizontalPadding * 2
        val chipHeight = textBounds.height() + verticalPadding * 2

        val width = chipWidth.toInt().coerceAtLeast(120)
        val height = chipHeight.toInt() + 60

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = width / 2f
        val cy = height - 30f
        val radius = 30f

        // Draw Icon (Image or Text)
        if (iconRes != null) {
            val iconBitmap = BitmapFactory.decodeResource(context.resources, iconRes)
            if (iconBitmap != null) {
                val iconSize = radius * 1.2f
                val destRect = RectF(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2)
                canvas.drawBitmap(iconBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } else {
            val iconPaint = Paint().apply {
                color = Color.BLACK
                textSize = 40f
                typeface = Typeface.DEFAULT
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val iconY = cy - (iconPaint.descent() + iconPaint.ascent()) / 2
            canvas.drawText(iconText, cx, iconY, iconPaint)
        }

        val chipLeft = (width - chipWidth) / 2f
        val chipTop = cy - radius - chipHeight - 8f
        val chipRect = RectF(
            chipLeft,
            chipTop,
            chipLeft + chipWidth,
            chipTop + chipHeight
        )

        val chipPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawRoundRect(chipRect, 16f, 16f, chipPaint)

        val borderPaint = Paint().apply {
            color = Color.argb(30, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(chipRect, 16f, 16f, borderPaint)

        val textX = chipRect.left + horizontalPadding
        val textY = chipRect.top + verticalPadding + textBounds.height()
        canvas.drawText(label, textX, textY, textPaint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
