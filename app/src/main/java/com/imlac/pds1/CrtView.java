package com.imlac.pds1;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * CRT SurfaceView — phosphor green vector renderer.
 * Hard-capped at MAX_FPS (default 30).
 */
public class CrtView extends SurfaceView implements SurfaceHolder.Callback {

    private static final int PDS = 1024;  // PDS-1 coordinate space

    // Phosphor colours
    private static final int CORE  = Color.argb(255, 20, 255, 65);
    private static final int MID   = Color.argb(110,  0, 200, 50);
    private static final int OUTER = Color.argb( 35,  0, 140, 35);

    private final Paint pCore  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMid   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDecay = new Paint();
    private final Paint pScan  = new Paint();

    private Bitmap offBmp;
    private Canvas offCvs;

    private volatile Machine machine;
    private volatile Demos   demos;
    private volatile boolean running = false;
    private volatile int     maxFps  = 30;
    private Thread renderThread;

    // FPS tracking
    private long   fpsTime  = 0;
    private int    fpsCnt   = 0;
    private float  fpsActual = 0f;

    public CrtView(Context ctx)              { super(ctx); init(); }
    public CrtView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        getHolder().addCallback(this);
        pCore .setStrokeWidth(1.4f);
        pMid  .setStrokeWidth(4.0f);
        pOuter.setStrokeWidth(8.0f);
        pCore .setColor(CORE);
        pMid  .setColor(MID);
        pOuter.setColor(OUTER);
        pDecay.setColor(Color.argb(40, 0, 0, 0));
        pScan .setColor(Color.argb(18, 0, 0, 0));
        pScan .setStrokeWidth(1f);
    }

    public void setMachine(Machine m, Demos d) { machine = m; demos = d; }
    public void setMaxFps(int fps) { maxFps = Math.max(1, Math.min(60, fps)); }
    public float getActualFps()   { return fpsActual; }

    @Override public void surfaceCreated(SurfaceHolder h)  { createBitmap(); startRender(); }
    @Override public void surfaceDestroyed(SurfaceHolder h){ stopRender(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        stopRender(); createBitmap(); startRender();
    }

    private void createBitmap() {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        offBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        offCvs = new Canvas(offBmp);
        offCvs.drawColor(Color.BLACK);
    }

    private void startRender() {
        running = true;
        renderThread = new Thread(this::loop, "crt-render");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void stopRender() {
        running = false;
        if (renderThread != null) {
            try { renderThread.join(600); } catch (InterruptedException ignored) {}
        }
    }

    private void loop() {
        while (running) {
            long t0 = System.nanoTime();

            Machine m = machine;
            Demos   d = demos;
            if (m != null && d != null && offBmp != null) {
                m.dlClear();
                d.runCurrentDemo();
                renderFrame(m);
                blitToSurface();
            }

            // FPS counter
            fpsCnt++;
            long now = System.nanoTime();
            if (now - fpsTime >= 1_000_000_000L) {
                fpsActual = fpsCnt;
                fpsCnt = 0;
                fpsTime = now;
            }

            // FPS cap
            long frameBudget = 1_000_000_000L / maxFps;
            long elapsed = System.nanoTime() - t0;
            long sleep = (frameBudget - elapsed) / 1_000_000;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void renderFrame(Machine m) {
        int sw = offBmp.getWidth(), sh = offBmp.getHeight();
        float sx = (float) sw / PDS, sy = (float) sh / PDS;

        // Phosphor decay
        offCvs.drawRect(0, 0, sw, sh, pDecay);

        int nv = m.nvec;
        for (int i = 0; i < nv; i++) {
            float b = m.vbr[i] / 255f;
            if (b < 0.04f) continue;

            float x1 =  m.vx1[i] * sx;
            float y1 = sh - m.vy1[i] * sy;

            if (m.vpt[i]) {
                drawPoint(x1, y1, b);
            } else {
                float x2 =  m.vx2[i] * sx;
                float y2 = sh - m.vy2[i] * sy;
                drawLine(x1, y1, x2, y2, b);
            }
        }
    }

    private void drawLine(float x1, float y1, float x2, float y2, float b) {
        pOuter.setColor(Color.argb((int)(b*32),  0, 140, 35));
        pOuter.setStrokeWidth(8f);
        offCvs.drawLine(x1, y1, x2, y2, pOuter);
        pMid.setColor(Color.argb((int)(b*100), 0, 200, 50));
        pMid.setStrokeWidth(3.5f);
        offCvs.drawLine(x1, y1, x2, y2, pMid);
        pCore.setColor(Color.argb((int)(b*255), 20, 255, 65));
        pCore.setStrokeWidth(1.3f);
        offCvs.drawLine(x1, y1, x2, y2, pCore);
    }

    private void drawPoint(float x, float y, float b) {
        pOuter.setColor(Color.argb((int)(b*30), 0, 140, 35));
        offCvs.drawCircle(x, y, 6f, pOuter);
        pMid.setColor(Color.argb((int)(b*90), 0, 200, 50));
        offCvs.drawCircle(x, y, 3f, pMid);
        pCore.setColor(Color.argb((int)(b*255), 20, 255, 65));
        offCvs.drawCircle(x, y, 1.5f, pCore);
    }

    private final Paint pScanline = new Paint();
    private void blitToSurface() {
        SurfaceHolder holder = getHolder();
        Canvas c = null;
        try {
            c = holder.lockCanvas();
            if (c == null) return;
            c.drawBitmap(offBmp, 0, 0, null);
            // Scanlines
            int h = c.getHeight();
            pScan.setColor(Color.argb(18, 0, 0, 0));
            for (int y = 0; y < h; y += 3) c.drawLine(0, y, c.getWidth(), y, pScan);
            // Vignette
            RadialGradient vg = new RadialGradient(
                c.getWidth()/2f, h/2f, Math.max(c.getWidth(), h) * 0.65f,
                Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP);
            pScanline.setShader(vg);
            c.drawRect(0, 0, c.getWidth(), h, pScanline);
            pScanline.setShader(null);
        } finally {
            if (c != null) holder.unlockCanvasAndPost(c);
        }
    }

    public int[] screenToPDS(float tx, float ty) {
        return new int[]{
            (int)(tx / getWidth()  * PDS),
            (int)((1f - ty / getHeight()) * PDS)
        };
    }
}
