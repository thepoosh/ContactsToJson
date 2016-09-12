package me.glide.testcontactupload

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById(R.id.sync)!!.setOnClickListener { ContactSyncService.startSync(applicationContext) }
    }

}
