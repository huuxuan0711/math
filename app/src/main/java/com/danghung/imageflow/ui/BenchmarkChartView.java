package com.danghung.imageflow.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<BarItem> items = new ArrayList<>();

    public BenchmarkChartView(Context context) {
        super(context);
        init();
    }

    public BenchmarkChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {

        textPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        labelPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);

        labelPaint.setColor(Color.DKGRAY);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        gridPaint.setColor(Color.parseColor("#E0E0E0"));
        gridPaint.setStrokeWidth(getResources().getDisplayMetrics().density);
    }

    public void setItems(List<BarItem> items) {

        this.items = new ArrayList<>(items);

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;

        float w = getWidth();
        float h = getHeight();

        if (items == null || items.isEmpty()) {

            labelPaint.setColor(Color.GRAY);

            canvas.drawText("Chưa có dữ liệu so sánh", w / 2, h / 2, labelPaint);

            return;
        }

        float padL = 20 * density;
        float padR = 20 * density;
        float padT = 28 * density;
        float padB = 32 * density;

        float chartH = h - padT - padB;

        if (chartH <= 0) return;

        // Tìm thời gian lớn nhất
        long maxTime = 1;

        for (BarItem item : items) {
            maxTime = Math.max(maxTime, item.timeMs);
        }

        // Trục ngang
        float bottom = h - padB;

        canvas.drawLine(padL, bottom, w - padR, bottom, gridPaint);

        // Chia vùng biểu đồ
        int count = items.size();

        float availableW = w - padL - padR;
        float slotW = availableW / count;
        float barW = slotW * 0.55f;

        for (int i = 0; i < count; i++) {

            BarItem item = items.get(i);

            float barH = (float) item.timeMs / maxTime * chartH;

            float left = padL + i * slotW + (slotW - barW) / 2;

            float right = left + barW;

            float top = bottom - barH;

            // Vẽ cột
            barPaint.setColor(item.color);

            canvas.drawRoundRect(new RectF(left, top, right, bottom), 6 * density, 6 * density, barPaint);

            // Hiển thị thời gian
            textPaint.setColor(item.color);

            canvas.drawText(item.timeMs + " ms", left + barW / 2, Math.max(padT - 4 * density, top - 6 * density), textPaint);

            // Hiển thị nhãn Worker
            labelPaint.setColor(Color.DKGRAY);

            canvas.drawText(item.label, left + barW / 2, bottom + 22 * density, labelPaint);
        }
    }

    public static class BarItem {

        public final String label;
        public final long timeMs;
        public final int color;

        public BarItem(String label, long timeMs, int color) {
            this.label = label;
            this.timeMs = timeMs;
            this.color = color;
        }
    }
}
