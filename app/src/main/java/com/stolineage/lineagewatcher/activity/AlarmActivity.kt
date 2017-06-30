package com.stolineage.lineagewatcher.activity

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import com.stolineage.lineagewatcher.singleton.Config

class AlarmActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()

        Handler().postDelayed({
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(Config.LINEAGE_PACKAGE_NAME)
        }, 500)

        finish()
    }
}