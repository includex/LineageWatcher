package lineagewatcher.toda.com.lineagewatcher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MotionEvent
import lineagewatcher.toda.com.lineagewatcher.R
import lineagewatcher.toda.com.lineagewatcher.utils.ImageTransmogrifier
import android.hardware.display.DisplayManager
import android.os.*
import android.content.pm.ApplicationInfo
import android.app.ActivityManager
import android.content.pm.PackageManager
import lineagewatcher.toda.com.lineagewatcher.activity.AlarmActivity
import lineagewatcher.toda.com.lineagewatcher.singleton.Const

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
    private var scaleX: Float = 0f
    private var scaleY: Float = 0f
    private var color: Int? = null
    private var vibrator: Vibrator? = null;

    private var params: WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

    private var imageTransmogrifier: ImageTransmogrifier? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager?.defaultDisplay?.getMetrics(metrics)
        installView()

        handlerThread.start();
        handler = Handler(handlerThread.getLooper());

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun toggle() {
        watching = !watching;
        ivMover?.visibility = if (watching) View.GONE else View.VISIBLE
        roundButton?.setBackgroundResource(if (watching) R.drawable.control_ui_round_watching else R.drawable.control_ui_round)
    }

    private fun installView() {
        uiView = getUIView();
        roundButton = uiView?.findViewById(R.id.v_round);
        ivMover = uiView?.findViewById(R.id.iv_mover)

        roundButton?.setOnClickListener({
            imageTransmogrifier = ImageTransmogrifier(this);

            toggle();

            if (watching == true) {
                color = null;
                if (virtualDisplay == null) {
                    virtualDisplay = Const.mediaProjection?.createVirtualDisplay(DISPLAY_NAME,
                            imageTransmogrifier?.getWidth()!!, imageTransmogrifier?.getHeight()!!,
                            getResources().getDisplayMetrics().densityDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, imageTransmogrifier?.surface, null, handler);

                    scaleY = metrics.heightPixels.toFloat() / imageTransmogrifier?.getHeight()!!.toFloat()
                    scaleX = metrics.widthPixels.toFloat() / imageTransmogrifier?.getWidth()!!.toFloat()
                    Const.mediaProjection?.registerCallback(CallBack(virtualDisplay), handler)
                }
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

    fun updateImage(bitmap: Bitmap?) {
        if (!watching) {
            return
        }

        if (currentX < 0 || currentY < 0) {
            return;
        }

        Handler(Looper.getMainLooper()).post({
            val currentColor = bitmap?.getPixel((currentX / scaleX + (10 * metrics.density) / 2).toInt(), (currentY / scaleY + (10 * metrics.density) / 2).toInt());
            if (color == null) {
                color = currentColor
            } else if (currentColor != color) {
                vibrator?.vibrate(5000)

                toggle();
                watching = false;

                if (Const.appKill) {
                    startActivity(Intent(this, AlarmActivity::class.java))
                }
            }
        })
    }

    fun getHandler(): Handler? {
        return handler
    }
}
