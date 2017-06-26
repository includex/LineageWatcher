package lineagewatcher.toda.com.lineagewatcher.activity

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.support.v7.app.AppCompatActivity

class AlarmActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()

        Handler().postDelayed({
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses("com.ncsoft.lineagem")
        }, 1000)

        finish()
    }
}