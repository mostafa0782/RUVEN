package iq.ruven.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.sin

class RuvenLiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = RuvenEngine()
    private inner class RuvenEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val frame = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 40L)
            }
        }
        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(frame)
            if (visible) frame.run()
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }
        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(frame)
            super.onDestroy()
        }
        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                    val phase = (System.currentTimeMillis() % 12000L) / 12000f
                    paint.shader = LinearGradient(0f, 0f, w, h,
                        intArrayOf(Color.rgb(7,18,42), Color.rgb(22,65,120), Color.rgb(8,24,52)), null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
                    paint.color = Color.argb(55, 80, 190, 255)
                    canvas.drawCircle(w * (.25f + .12f * sin(phase * 6.28318).toFloat()), h * (.34f + .08f * sin((phase + .25f) * 6.28318).toFloat()), w * .36f, paint)
                    paint.color = Color.argb(36, 170, 220, 255)
                    canvas.drawCircle(w * (.75f + .1f * sin((phase + .5f) * 6.28318).toFloat()), h * (.68f + .07f * sin((phase + .7f) * 6.28318).toFloat()), w * .29f, paint)
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null) runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
