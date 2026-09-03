package org.synergy.injection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Lightweight pointer rendered in an accessibility overlay. */
final class CursorOverlayView extends View {
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointer = new Path();

    CursorOverlayView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        pointer.moveTo(2f * density, 1f * density);
        pointer.lineTo(2f * density, 25f * density);
        pointer.lineTo(8f * density, 19f * density);
        pointer.lineTo(13f * density, 29f * density);
        pointer.lineTo(18f * density, 26f * density);
        pointer.lineTo(13f * density, 17f * density);
        pointer.lineTo(22f * density, 17f * density);
        pointer.close();

        shadow.setColor(0x66000000);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeWidth(4f * density);
        shadow.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
        outline.setColor(Color.BLACK);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.5f * density);
        outline.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(pointer, shadow);
        canvas.drawPath(pointer, fill);
        canvas.drawPath(pointer, outline);
    }
}
