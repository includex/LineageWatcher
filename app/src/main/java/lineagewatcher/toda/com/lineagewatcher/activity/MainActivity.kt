package lineagewatcher.toda.com.lineagewatcher.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import lineagewatcher.toda.com.lineagewatcher.R
import lineagewatcher.toda.com.lineagewatcher.databinding.ActivityMainBinding
import lineagewatcher.toda.com.lineagewatcher.service.WatcherService
import android.media.projection.MediaProjectionManager
import lineagewatcher.toda.com.lineagewatcher.singleton.Const

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private var self: MainActivity? = null
    private val REQUEST_CODE_CAPTURE_PREMISSION_REQUEST = 1000
    private var projectionManager: MediaProjectionManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        self = this

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main);
        (binding?.tvControlButton as? TextView)?.setOnClickListener({
            if (Const.runningService) {
                stopService(Intent(self, WatcherService::class.java))
            } else {
                startService(Intent(self, WatcherService::class.java))
            }

            Const.runningService = !Const.runningService;
            updateUI();
        })

        (binding?.tvSwitchKillApp as? TextView)?.setOnClickListener({
            Const.appKill = !Const.appKill
            updateUI();
        })

        init()
    }

    private fun init() {
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        startActivityForResult(projectionManager?.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PREMISSION_REQUEST)
    }

    private fun updateUI() {
        (binding?.tvControlButton as TextView).setText(if (Const.runningService) R.string.hide_overlay_ui else R.string.display_overlay_ui)
        (binding?.tvSwitchKillApp as TextView).setText(if (Const.appKill) R.string.vibrator_with_kill_app else R.string.vibrator)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_CAPTURE_PREMISSION_REQUEST && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        if (requestCode == REQUEST_CODE_CAPTURE_PREMISSION_REQUEST) {
            Const.mediaProjection = projectionManager?.getMediaProjection(resultCode, data);
        }

    }
}
