package com.stolineage.lineagewatcher.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.stolineage.lineagewatcher.R
import com.stolineage.lineagewatcher.databinding.ActivityMainBinding
import com.stolineage.lineagewatcher.service.WatcherService
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.stolineage.lineagewatcher.singleton.Config

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private val REQUEST_CODE_CAPTURE_PREMISSION_REQUEST = 1000
    private val REQUEST_CODE_OVERLAY_PREMISSION_REQUEST = 1001
    private var projectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main);
        (binding?.tvControlButton as? TextView)?.setOnClickListener({
            if (Config.runningService) {
                stopService(Intent(this@MainActivity, WatcherService::class.java))
            } else {
                init()
                startService(Intent(this@MainActivity, WatcherService::class.java))
            }

            Config.runningService = !Config.runningService;
            updateUI();
        })

        (binding?.tvSwitchKillApp as? TextView)?.setOnClickListener({
            Config.appKill = !Config.appKill
            updateUI();
        })
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun init() {

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PREMISSION_REQUEST)
        }

        if (projectionManager == null) {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            startActivityForResult(projectionManager?.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PREMISSION_REQUEST)
        }
    }

    private fun updateUI() {
        (binding?.tvControlButton as TextView).setText(if (Config.runningService) R.string.hide_overlay_ui else R.string.display_overlay_ui)
        (binding?.tvSwitchKillApp as TextView).setText(if (Config.appKill) R.string.vibrator_with_kill_app else R.string.vibrator)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        if (requestCode == REQUEST_CODE_CAPTURE_PREMISSION_REQUEST) {
            Config.mediaProjection = projectionManager?.getMediaProjection(resultCode, data);
            try {
                startActivity(packageManager.getLaunchIntentForPackage(Config.LINEAGE_PACKAGE_NAME))
            } catch(e: Exception) {
                Toast.makeText(this, R.string.toast_for_not_found_lineage, Toast.LENGTH_SHORT).show();
            }
        }

    }
}
