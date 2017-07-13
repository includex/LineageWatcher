package com.stolineage.lineagewatcher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import com.stolineage.lineagewatcher.R
import com.stolineage.lineagewatcher.utils.ImageTransmogrifier
import android.hardware.display.DisplayManager
import android.os.*
import android.content.pm.ApplicationInfo
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.pm.ConfigurationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.test.TouchUtils
import android.util.Log
import android.view.*
import com.stolineage.lineagewatcher.activity.AlarmActivity
import com.stolineage.lineagewatcher.singleton.Config
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

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
    //private var scaleX: Float = 0f
    //private var scaleY: Float = 0f
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
        handler = Handler(handlerThread.getLooper());

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun updateUI(watching: Boolean) {
        this.watching = watching
        ivMover?.visibility = if (watching) View.GONE else View.VISIBLE
        roundButton?.setBackgroundResource(if (watching) R.drawable.control_ui_round_watching else R.drawable.control_ui_round)
    }

    private fun installView() {
        uiView = getUIView();
        roundButton = uiView?.findViewById(R.id.v_round);
        ivMover = uiView?.findViewById(R.id.iv_mover)

        roundButton?.setOnClickListener({
            updateUI(!watching);

            if (watching == true) {
                color = null;

                if (virtualDisplay != null) {
                    virtualDisplay?.release();
                    virtualDisplay = null;
                }

                imageTransmogrifier = ImageTransmogrifier(this);
                virtualDisplay = Config.mediaProjection?.createVirtualDisplay(DISPLAY_NAME,
                        imageTransmogrifier?.getWidth()!!, imageTransmogrifier?.getHeight()!!,
                        getResources().getDisplayMetrics().densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageTransmogrifier?.surface, null, handler);

                Config.mediaProjection?.registerCallback(CallBack(virtualDisplay), handler)

            }
        })
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
                    var array: IntArray = IntArray(4);
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
        val view = layoutInflater?.inflate(R.layout.overlay_view, null)
        return view
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

    class CallBack : MediaProjection.Callback {

        private var display: VirtualDisplay?

        constructor(virtualDisplay: VirtualDisplay?) {
            display = virtualDisplay
        }

        override fun onStop() {
            display?.release()
            super.onStop()
        }
    }

    fun getWindowManager(): WindowManager? {
        return windowManager
    }

    fun isRed(color: Int): Boolean {
        var red = Color.red(color)
        var blue = Color.blue(color)
        var green = Color.green(color)

//        Log.d("-------", " red " + red + ", blue " + blue + ", green " + green)
        return red > 90 && blue < 50 && green < 50;
    }


    var lastAt: Long = 0

    fun updateImage(bitmap: Bitmap?) {
        if (!watching) {
            return
        }

        if (currentX < 0 || currentY < 0) {
            return;
        }

        Handler(Looper.getMainLooper()).post({
            var x: Int? = null
            var y: Int? = null

            if (imageTransmogrifier?.orientation != getResources().getConfiguration().orientation) {
                updateUI(false);
            } else {
                val currentColor = bitmap?.getPixel((currentX / imageTransmogrifier?.scaleX!! + (10 * metrics.density) / 2).toInt(), (currentY / imageTransmogrifier?.scaleY!! + (10 * metrics.density) / 2).toInt());
                if (color == null) {
                    color = currentColor
                } else if (isRed(currentColor!!)) {
                    if (lastAt + 5000 < Date().getTime()) {
                        lastAt = Date().getTime()

                        handler?.post({
                            Log.d("-------","send call")
                            val url = URL("http://192.168.0.8:8888/")
                            val urlConnection = url.openConnection() as HttpURLConnection
                            try {
                                val in1 = BufferedInputStream(urlConnection.getInputStream())
                                in1.read()
                                in1.close()
                            } finally {
                                urlConnection.disconnect()
                            }
                        })
                    }
                }
            }
        })
    }

    fun getHandler(): Handler? {
        return handler
    }
}
