package lineagewatcher.toda.com.lineagewatcher.singleton

import android.media.projection.MediaProjection

object Const {
    public var mediaProjection: MediaProjection? = null
    public var runningService: Boolean = false;
    public var appKill: Boolean = false;
}
