package com.stolineage.lineagewatcher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import com.stolineage.lineagewatcher.R
import com.stolineage.lineagewatcher.activity.AlarmActivity
import com.stolineage.lineagewatcher.singleton.Config
import com.stolineage.lineagewatcher.utils.ImageTransmogrifier

class WatcherService : Service() {
    private var windowManager: WindowManager? = null;
    private var uiView: View? = null;
    private var metrics: DisplayMetrics = DisplayMetrics();
    private var roundButton: View? = null
    private var ivMover: View? = null
    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private var viewX: Int = 0
    private var viewY: Int = 0
    private var currentX: Int = 0
    private var currentY: Int = 0
    private var watching: Boolean = false
    private var virtualDisplay: VirtualDisplay? = null
    private val DISPLAY_NAME: String = "capture"
    private val handlerThread = HandlerThread(javaClass.simpleName, android.os.Process.THREAD_PRIORITY_BACKGROUND)
    private var handler: Handler? = null
    private var color: Int? = null
    private var vibrator: Vibrator? = null
    private var defaultDisplay: Display? = null

    private var params: WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

    private var imageTransmogrifier: ImageTransmogrifier? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        defaultDisplay = windowManager?.defaultDisplay;
        defaultDisplay?.getMetrics(metrics)

        installView()

        handlerThread.start();
        handler = Handler(handlerThread.looper);

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun updateUI(watching: Boolean) {
        this.watching = watching
        ivMover?.visibility = if (watching) View.GONE else View.VISIBLE
        roundButton?.setBackgroundResource(if (watching) R.drawable.control_ui_round_watching else R.drawable.control_ui_round)
    }

    private fun installView() {
        uiView = getUIView();
        roundButton = uiView?.findViewById(R.id.v_round)
        ivMover = uiView?.findViewById(R.id.iv_mover)

        roundButton?.setOnClickListener {
            updateUI(!watching);

            if (watching) {
                color = null

                if (virtualDisplay != null) {
                    virtualDisplay?.release()
                    virtualDisplay = null
                }

                imageTransmogrifier = ImageTransmogrifier(this)
                virtualDisplay = Config.mediaProjection?.createVirtualDisplay(DISPLAY_NAME,
                        imageTransmogrifier?.getWidth()!!, imageTransmogrifier?.getHeight()!!,
                        resources.displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageTransmogrifier?.surface, null, handler);

                Config.mediaProjection?.registerCallback(CallBack(virtualDisplay), handler)

            }
        }
        bindDragEvent(uiView)

        params.height = (43 * metrics.density).toInt()
        params.width = (43 * metrics.density).toInt()

        windowManager?.addView(uiView, params)
    }

    private fun bindDragEvent(view: View?) {
        view?.setOnTouchListener(fun(view: View, ev: MotionEvent): Boolean {
            if (watching) {
                return false
            }
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchX = ev.rawX
                    touchY = ev.rawY
                    viewX = params.x
                    viewY = params.y
                    currentX = params.x
                    currentY = params.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val offsetX = ev.rawX - touchX
                    val offsetY = ev.rawY - touchY

                    params.x = viewX + offsetX.toInt()
                    params.y = viewY + offsetY.toInt()

                    var rect: Rect? = Rect()
                    var array: IntArray = IntArray(2)
                    roundButton?.getLocationOnScreen(array)
                    currentX = array[0];
                    currentY = array[1];


                    windowManager?.updateViewLayout(uiView, params)
                }
            }
            return false
        })
    }

    private fun getUIView(): View? {
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as? LayoutInflater;
        return layoutInflater?.inflate(R.layout.overlay_view, null)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (virtualDisplay != null) {
            virtualDisplay?.release()
            virtualDisplay = null
        }

        if (uiView != null) {
            windowManager?.removeView(uiView)
        }
    }

    override fun onBind(pendingIntent: Intent?): IBinder? {
        return null;
    }

    class CallBack(virtualDisplay: VirtualDisplay?) : MediaProjection.Callback() {

        private var display: VirtualDisplay? = virtualDisplay

        override fun onStop() {
            display?.release()
            super.onStop()
        }
    }

    fun getWindowManager(): WindowManager? {
        return windowManager
    }

    fun updateImage(bitmap: Bitmap?) {
        if (!watching) {
            return
        }

        if (currentX < 0 || currentY < 0) {
            return;
        }

        Handler(Looper.getMainLooper()).post {
            var x: Int? = null
            var y: Int? = null

            if (imageTransmogrifier?.orientation != resources.configuration.orientation) {
                updateUI(false)
            } else {
                val currentColor = bitmap?.getPixel((currentX / imageTransmogrifier?.scaleX!! + (10 * metrics.density) / 2).toInt(), (currentY / imageTransmogrifier?.scaleY!! + (10 * metrics.density) / 2).toInt())
                if (color == null) {
                    color = currentColor
                } else if (currentColor != color) {
                    vibrator?.vibrate(5000)
                    updateUI(false)

                    if (Config.appKill) {
                        val intent = Intent(this, AlarmActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                }
            }
        }
    }

    fun getHandler(): Handler? {
        return handler
    }
}
