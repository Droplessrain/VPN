package app.orbitvpn.client;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Soft rotating globe with glowing nodes and animated link pulses.
 */
public class GlobeNetworkView extends View {

    private static final int NODE_COUNT = 14;
    private static final int LINK_COUNT = 18;

    private final Paint globePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latitudePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] nodeLon = new float[NODE_COUNT];
    private final float[] nodeLat = new float[NODE_COUNT];
    private final int[] linkA = new int[LINK_COUNT];
    private final int[] linkB = new int[LINK_COUNT];
    private final float[] linkPhase = new float[LINK_COUNT];
    private float rotation;
    private boolean running;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            rotation += 0.35f;
            if (rotation >= 360f) {
                rotation -= 360f;
            }
            for (int i = 0; i < LINK_COUNT; i++) {
                linkPhase[i] += 0.02f;
                if (linkPhase[i] > 1f) {
                    linkPhase[i] -= 1f;
                }
            }
            invalidate();
            postDelayed(this, 16);
        }
    };

    public GlobeNetworkView(Context context) {
        super(context);
        init();
    }

    public GlobeNetworkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GlobeNetworkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        latitudePaint.setStyle(Paint.Style.STROKE);
        latitudePaint.setStrokeWidth(1.5f);
        latitudePaint.setColor(0x55A8D8FF);
        linkPaint.setStyle(Paint.Style.STROKE);
        linkPaint.setStrokeWidth(2f);
        linkPaint.setColor(0x887ED6FF);
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(3.5f);
        pulsePaint.setColor(0xFFB8F0FF);
        nodePaint.setStyle(Paint.Style.FILL);
        Random random = new Random(42);
        for (int i = 0; i < NODE_COUNT; i++) {
            nodeLon[i] = random.nextFloat() * 360f;
            nodeLat[i] = -55f + random.nextFloat() * 110f;
        }
        for (int i = 0; i < LINK_COUNT; i++) {
            linkA[i] = random.nextInt(NODE_COUNT);
            do {
                linkB[i] = random.nextInt(NODE_COUNT);
            } while (linkB[i] == linkA[i]);
            linkPhase[i] = random.nextFloat();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        removeCallbacks(ticker);
        post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float cx = width * 0.5f;
        float cy = height * 0.42f;
        float radius = Math.min(width, height) * 0.32f;

        globePaint.setShader(new RadialGradient(cx - radius * 0.25f, cy - radius * 0.3f, radius * 1.2f,
                new int[]{0xFF1B4F72, 0xFF0B2A40, 0xFF061018},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, globePaint);

        for (int i = -2; i <= 2; i++) {
            float lat = i * 22f;
            Path path = new Path();
            boolean started = false;
            for (int deg = 0; deg <= 360; deg += 6) {
                float[] p = project(cx, cy, radius, deg + rotation, lat);
                if (p == null) {
                    started = false;
                    continue;
                }
                if (!started) {
                    path.moveTo(p[0], p[1]);
                    started = true;
                } else {
                    path.lineTo(p[0], p[1]);
                }
            }
            canvas.drawPath(path, latitudePaint);
        }

        float[][] points = new float[NODE_COUNT][];
        for (int i = 0; i < NODE_COUNT; i++) {
            points[i] = project(cx, cy, radius, nodeLon[i] + rotation, nodeLat[i]);
        }

        for (int i = 0; i < LINK_COUNT; i++) {
            float[] a = points[linkA[i]];
            float[] b = points[linkB[i]];
            if (a == null || b == null) {
                continue;
            }
            canvas.drawLine(a[0], a[1], b[0], b[1], linkPaint);
            float t = linkPhase[i];
            float px = a[0] + (b[0] - a[0]) * t;
            float py = a[1] + (b[1] - a[1]) * t;
            canvas.drawCircle(px, py, 4f, pulsePaint);
        }

        for (float[] point : points) {
            if (point == null) {
                continue;
            }
            nodePaint.setColor(0xEE7ED6FF);
            canvas.drawCircle(point[0], point[1], 5.5f, nodePaint);
            nodePaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(point[0], point[1], 2.2f, nodePaint);
        }
    }

    private static float[] project(float cx, float cy, float radius, float lonDeg, float latDeg) {
        double lon = Math.toRadians(lonDeg);
        double lat = Math.toRadians(latDeg);
        double x = Math.cos(lat) * Math.sin(lon);
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.cos(lon);
        if (z < -0.05) {
            return null;
        }
        float depth = (float) ((z + 1.0) * 0.5);
        float scale = radius * (0.92f + 0.08f * depth);
        return new float[]{
                cx + (float) (x * scale),
                cy - (float) (y * scale)
        };
    }
}
