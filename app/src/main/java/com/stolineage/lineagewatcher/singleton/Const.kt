package com.stolineage.lineagewatcher.singleton

import android.media.projection.MediaProjection

object Config {
    public var mediaProjection: MediaProjection? = null
    public var runningService: Boolean = false;
    public var appKill: Boolean = false;
    public val LINEAGE_PACKAGE_NAME: String = "com.ncsoft.lineagem"
}
