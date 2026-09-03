package io.github.koniho.synerdroid.injection;
// Modified for Synerdroid by Alexander Ho, 2026.

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** A translucent dot whose center is the exact accessibility gesture coordinate. */
final class CursorOverlayView extends View {
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;
    private float pointerX;
    private float pointerY;

    CursorOverlayView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        radius = 9f * density;
        halo.setColor(0x55000000);
        dot.setColor(0x99FFFFFF);
    }

    void setPointerPosition(float globalX, float globalY) {
        int[] windowOrigin = new int[2];
        getLocationOnScreen(windowOrigin);
        pointerX = globalX - windowOrigin[0];
        pointerY = globalY - windowOrigin[1];
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(pointerX, pointerY, radius + 2f, halo);
        canvas.drawCircle(pointerX, pointerY, radius, dot);
    }
}
