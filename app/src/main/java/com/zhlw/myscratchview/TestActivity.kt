package com.zhlw.myscratchview

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView

class TestActivity : AppCompatActivity() {

    private var mScratchDialog : DialogScratchRewardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.test).setOnClickListener {
            if (mScratchDialog == null || !mScratchDialog!!.isShowing()){
                mScratchDialog = ScratchDialogHelper.showScratchDialog(this@TestActivity)
            } else {
                mScratchDialog?.dismiss()
            }
        }


    }

}