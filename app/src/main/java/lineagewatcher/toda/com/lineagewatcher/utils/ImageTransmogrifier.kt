package lineagewatcher.toda.com.lineagewatcher.utils

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.Image
import android.media.ImageReader
import android.view.Display
import android.view.Surface
import lineagewatcher.toda.com.lineagewatcher.service.WatcherService
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ImageTransmogrifier internal constructor(private val svc: WatcherService) : ImageReader.OnImageAvailableListener {
    private val width: Int
    private val height: Int
    private val imageReader: ImageReader
    private var latestBitmap: Bitmap? = null

    init {

        val display = svc.getWindowManager()?.getDefaultDisplay()
        val size = Point()

        display?.getSize(size)

        var width = size.x
        var height = size.y

        while (width * height > 2 shl 19) {
            width = width shr 1
            height = height shr 1
        }

        this.width = width
        this.height = height

        imageReader = ImageReader.newInstance(width, height,
                PixelFormat.RGBA_8888, 2)
        imageReader.setOnImageAvailableListener(this, svc.getHandler())
    }

    override fun onImageAvailable(reader: ImageReader) {
        val image = imageReader.acquireLatestImage()

        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmapWidth = width + rowPadding / pixelStride

            if (latestBitmap == null ||
                    latestBitmap!!.width != bitmapWidth ||
                    latestBitmap!!.height != height) {
                if (latestBitmap != null) {
                    latestBitmap!!.recycle()
                }

                latestBitmap = Bitmap.createBitmap(bitmapWidth,
                        height, Bitmap.Config.ARGB_8888)
            }

            latestBitmap!!.copyPixelsFromBuffer(buffer)

            image?.close()

            svc.updateImage(latestBitmap)
        }
    }

    public val surface: Surface
        get() = imageReader.surface

    public fun getWidth(): Int {
        return width
    }

    public fun getHeight(): Int {
        return height
    }

    public fun close() {
        imageReader.close()
    }
}