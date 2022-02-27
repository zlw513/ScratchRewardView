package com.zhlw.myscratchview

import android.content.Context
import android.util.Log
import android.view.View

object ScratchDialogHelper {

    private val TAG = "ScratchDialogHelper"

    fun showScratchDialog(context: Context) : DialogScratchRewardView{
        val dialogBuilder = DialogScratchRewardView.Builder(context,R.layout.dialog_scratch_reward)
        dialogBuilder.setScratchResultText("恭喜你中了")
        dialogBuilder.setEraseStatusListener(object : ScratchRewardView.EraseStatusListener{

            override fun onCompleted(view : ScratchRewardView?) {
                Log.d(TAG, "onCompleted! ")
                view?.showEraseAnim()
            }

        })
        val scratchDialog = dialogBuilder.build()
        scratchDialog.show()
        return scratchDialog
    }



}