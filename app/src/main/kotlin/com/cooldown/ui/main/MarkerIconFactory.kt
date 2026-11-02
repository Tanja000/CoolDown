package com.cooldown.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.cooldown.data.SpotCategory

object MarkerIconFactory {
  private val cache = mutableMapOf<Pair<SpotCategory, Boolean>, Drawable>()

  fun getMarkerDrawable(
    context: Context,
    category: SpotCategory,
    isSelected: Boolean = false,
  ): Drawable {
    val key = category to isSelected
    cache[key]?.let { return it }

    val density = context.resources.displayMetrics.density
    val sizeDp = if (isSelected) 46f else 36f
    val width = (sizeDp * density).toInt().coerceAtLeast(48)
    val height = (sizeDp * 1.22f * density).toInt().coerceAtLeast(58)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val cy = width / 2f
    val radius = (width / 2f) - (3f * density)

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(65, 0, 0, 0)
      style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 2f * density, radius, shadowPaint)

    // Pin tail triangle
    val pinPath = Path().apply {
      moveTo(cx - radius * 0.55f, cy + radius * 0.55f)
      lineTo(cx + radius * 0.55f, cy + radius * 0.55f)
      lineTo(cx, height - 2f * density)
      close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = category.colorHex
      style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, fillPaint)
    canvas.drawCircle(cx, cy, radius, fillPaint)

    // White or yellow border
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (isSelected) Color.rgb(255, 235, 59) else Color.WHITE
      style = Paint.Style.STROKE
      strokeWidth = (if (isSelected) 3.2f else 2.2f) * density
    }
    canvas.drawCircle(cx, cy, radius, strokePaint)

    // Draw inner icon glyph in white
    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      style = Paint.Style.FILL
      strokeWidth = 2.2f * density
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }

    val r = radius * 0.48f
    when (category) {
      SpotCategory.DRINKING_WATER -> {
        val drop = Path().apply {
          moveTo(cx, cy - r * 1.05f)
          cubicTo(cx + r * 0.9f, cy - r * 0.1f, cx + r * 0.85f, cy + r * 0.95f, cx, cy + r * 0.95f)
          cubicTo(cx - r * 0.85f, cy + r * 0.95f, cx - r * 0.9f, cy - r * 0.1f, cx, cy - r * 1.05f)
          close()
        }
        canvas.drawPath(drop, glyphPaint)
      }
      SpotCategory.REFILL_STATION -> {
        glyphPaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(
          cx - r * 0.55f,
          cy - r * 0.55f,
          cx + r * 0.55f,
          cy + r * 0.9f,
          3f * density,
          3f * density,
          glyphPaint,
        )
        canvas.drawLine(cx - r * 0.3f, cy - r * 0.9f, cx + r * 0.3f, cy - r * 0.9f, glyphPaint)
        canvas.drawLine(cx, cy - r * 0.15f, cx, cy + r * 0.45f, glyphPaint)
        canvas.drawLine(cx - r * 0.3f, cy + r * 0.15f, cx + r * 0.3f, cy + r * 0.15f, glyphPaint)
      }
      SpotCategory.SWIMMING_BATHING, SpotCategory.KNEIPP -> {
        glyphPaint.style = Paint.Style.STROKE
        for (i in -1..1) {
          val wy = cy + i * (r * 0.52f)
          val wave = Path().apply {
            moveTo(cx - r * 0.85f, wy)
            quadTo(cx - r * 0.42f, wy - r * 0.35f, cx, wy)
            quadTo(cx + r * 0.42f, wy + r * 0.35f, cx + r * 0.85f, wy)
          }
          canvas.drawPath(wave, glyphPaint)
        }
      }
      SpotCategory.WATER_PLAYGROUND, SpotCategory.FOUNTAIN -> {
        // Spraying water fountain arcs
        glyphPaint.style = Paint.Style.STROKE
        val leftArc = Path().apply {
          moveTo(cx, cy + r * 0.7f)
          quadTo(cx - r * 0.2f, cy - r * 0.8f, cx - r * 0.85f, cy - r * 0.1f)
        }
        val rightArc = Path().apply {
          moveTo(cx, cy + r * 0.7f)
          quadTo(cx + r * 0.2f, cy - r * 0.8f, cx + r * 0.85f, cy - r * 0.1f)
        }
        canvas.drawPath(leftArc, glyphPaint)
        canvas.drawPath(rightArc, glyphPaint)
        canvas.drawLine(cx, cy + r * 0.7f, cx, cy - r * 0.95f, glyphPaint)
        canvas.drawLine(cx - r * 0.75f, cy + r * 0.75f, cx + r * 0.75f, cy + r * 0.75f, glyphPaint)
      }
      SpotCategory.PARK -> {
        canvas.drawCircle(cx, cy - r * 0.3f, r * 0.55f, glyphPaint)
        canvas.drawCircle(cx - r * 0.45f, cy + r * 0.1f, r * 0.45f, glyphPaint)
        canvas.drawCircle(cx + r * 0.45f, cy + r * 0.1f, r * 0.45f, glyphPaint)
        glyphPaint.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy + r * 0.2f, cx, cy + r * 0.95f, glyphPaint)
      }
      SpotCategory.ICE_CREAM -> {
        // Ice cream cone
        canvas.drawCircle(cx, cy - r * 0.35f, r * 0.52f, glyphPaint)
        val cone = Path().apply {
          moveTo(cx - r * 0.5f, cy - r * 0.15f)
          lineTo(cx + r * 0.5f, cy - r * 0.15f)
          lineTo(cx, cy + r * 0.95f)
          close()
        }
        canvas.drawPath(cone, glyphPaint)
      }
      SpotCategory.CHURCH_COOL_BUILDING -> {
        // Cool stone building / church with cross
        glyphPaint.style = Paint.Style.STROKE
        val building = Path().apply {
          moveTo(cx - r * 0.7f, cy + r * 0.8f)
          lineTo(cx - r * 0.7f, cy - r * 0.1f)
          lineTo(cx, cy - r * 0.65f)
          lineTo(cx + r * 0.7f, cy - r * 0.1f)
          lineTo(cx + r * 0.7f, cy + r * 0.8f)
          close()
        }
        canvas.drawPath(building, glyphPaint)
        canvas.drawLine(cx, cy - r * 1.05f, cx, cy - r * 0.45f, glyphPaint)
        canvas.drawLine(cx - r * 0.28f, cy - r * 0.82f, cx + r * 0.28f, cy - r * 0.82f, glyphPaint)
      }
      SpotCategory.LIBRARY -> {
        glyphPaint.style = Paint.Style.STROKE
        canvas.drawRect(cx - r * 0.8f, cy - r * 0.6f, cx + r * 0.8f, cy + r * 0.65f, glyphPaint)
        canvas.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.65f, glyphPaint)
      }
      SpotCategory.MUSEUM -> {
        glyphPaint.style = Paint.Style.STROKE
        val roof = Path().apply {
          moveTo(cx - r * 0.9f, cy - r * 0.3f)
          lineTo(cx, cy - r * 0.9f)
          lineTo(cx + r * 0.9f, cy - r * 0.3f)
          close()
        }
        canvas.drawPath(roof, glyphPaint)
        canvas.drawLine(cx - r * 0.6f, cy - r * 0.3f, cx - r * 0.6f, cy + r * 0.7f, glyphPaint)
        canvas.drawLine(cx, cy - r * 0.3f, cx, cy + r * 0.7f, glyphPaint)
        canvas.drawLine(cx + r * 0.6f, cy - r * 0.3f, cx + r * 0.6f, cy + r * 0.7f, glyphPaint)
        canvas.drawLine(cx - r * 0.85f, cy + r * 0.75f, cx + r * 0.85f, cy + r * 0.75f, glyphPaint)
      }
      SpotCategory.SHADED_BENCH_SHELTER -> {
        // Pavilion roof + bench line
        glyphPaint.style = Paint.Style.STROKE
        val roof = Path().apply {
          moveTo(cx - r * 0.9f, cy - r * 0.15f)
          lineTo(cx, cy - r * 0.85f)
          lineTo(cx + r * 0.9f, cy - r * 0.15f)
        }
        canvas.drawPath(roof, glyphPaint)
        canvas.drawLine(cx - r * 0.65f, cy + r * 0.45f, cx + r * 0.65f, cy + r * 0.45f, glyphPaint)
        canvas.drawLine(cx - r * 0.45f, cy + r * 0.45f, cx - r * 0.45f, cy + r * 0.85f, glyphPaint)
        canvas.drawLine(cx + r * 0.45f, cy + r * 0.45f, cx + r * 0.45f, cy + r * 0.85f, glyphPaint)
      }
      SpotCategory.TREE_SHADE -> {
        val tree = Path().apply {
          moveTo(cx, cy - r * 0.95f)
          lineTo(cx + r * 0.8f, cy + r * 0.3f)
          lineTo(cx - r * 0.8f, cy + r * 0.3f)
          close()
        }
        canvas.drawPath(tree, glyphPaint)
        glyphPaint.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy + r * 0.3f, cx, cy + r * 0.95f, glyphPaint)
      }
    }

    val drawable = BitmapDrawable(context.resources, bitmap)
    cache[key] = drawable
    return drawable
  }
}
