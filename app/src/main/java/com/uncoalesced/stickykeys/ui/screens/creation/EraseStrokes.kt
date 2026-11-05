// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.creation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

/** One eraser drag, in bitmap pixels, so it can be drawn and applied by the same numbers. */
internal data class EraseStroke(
    val points: List<EraseStrokePoint>,
    val width: Float,
)

internal data class EraseStrokePoint(
    val x: Float,
    val y: Float,
)

/** The path of [stroke]. A single point becomes a tiny segment, which a round cap draws as a dot. */
internal fun erasePathOf(stroke: EraseStroke): Path =
    Path().apply {
        val first = stroke.points.firstOrNull() ?: return@apply
        moveTo(first.x, first.y)
        if (stroke.points.size == 1) {
            lineTo(first.x, first.y + 0.01f)
        } else {
            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
    }

/** A copy of [source] with [strokes] cut out of it down to full transparency. */
internal fun applyEraseStrokes(
    source: Bitmap,
    strokes: List<EraseStroke>,
): Bitmap {
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    // A decoded JPEG or an opaque PNG is flagged as having no alpha, and the encoders believe the
    // flag: what was cut out would be saved as solid black.
    result.setHasAlpha(true)
    val canvas = Canvas(result)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    strokes.forEach { stroke ->
        paint.strokeWidth = stroke.width
        canvas.drawPath(erasePathOf(stroke), paint)
    }
    return result
}
