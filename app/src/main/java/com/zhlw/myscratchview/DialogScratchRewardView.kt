package com.zhlw.myscratchview

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast


/**
 * author: zlw 2022/02/24
 * 自定义dialog 刮刮卡弹窗
 */
class DialogScratchRewardView : Dialog {

    constructor(context: Context) : super(context){

    }

    constructor(context: Context, themeResId: Int) : super(context,themeResId){

    }

    constructor(context: Context, cancelable: Boolean, cancelListener: DialogInterface.OnCancelListener) : super(context, cancelable, cancelListener){

    }

    class Builder(val context: Context,val contentViewId : Int) {

        private var mDialogTitle : String? = null
        private var mScratchResultText : String? = null
        private var mEraseStatusListener : ScratchRewardView.EraseStatusListener? = null

        fun setDialogTitle(title : String) : Builder{
            mDialogTitle = title
            return this
        }

        fun setScratchResultText(resultText : String) : Builder{
            mScratchResultText = resultText
            return this
        }

        fun setEraseStatusListener(eraseStatusListener: ScratchRewardView.EraseStatusListener) : Builder{
            mEraseStatusListener = eraseStatusListener
            return this
        }

        fun build() : DialogScratchRewardView {
            val dialog = DialogScratchRewardView(context,R.style.Scratch_Dialog)
            val contentView = LayoutInflater.from(context).inflate(contentViewId, null)

            val cancel = contentView.findViewById<TextView>(R.id.cancel)
            val copyCdk = contentView.findViewById<TextView>(R.id.copy_cdk)
            val scratchView = contentView.findViewById<ScratchRewardView>(R.id.scratch_view)
            val scratchResultView = contentView.findViewById<TextView>(R.id.scratch_result_content)
            val refresh = contentView.findViewById<ImageView>(R.id.refresh)

            cancel.setOnClickListener {
                dialog.dismiss()
            }

            copyCdk.setOnClickListener {
                if (scratchView.mIsCompleted){
                    Toast.makeText(context,"已复制到剪贴板",Toast.LENGTH_SHORT).show()
//                    dialog.dismiss()
                } else {
                    Toast.makeText(context,"请先刮开后复制",Toast.LENGTH_SHORT).show()
                }
            }

            mScratchResultText?.let {
                scratchResultView.text = it
            }

            mEraseStatusListener?.let {
                scratchView.setEraseStatusListener(it)
            }

            refresh.setOnClickListener {
                if (scratchView.mIsCompleted){
                    Toast.makeText(context,"已重置",Toast.LENGTH_SHORT).show()
                    scratchView.resetStatus()
                }
            }

            dialog.setContentView(contentView)
//            dialog.setLocation()
            return dialog
        }

    }

    //更改弹窗位置的
    private fun setLocation() {
        val layoutParams = this.window!!.attributes
        layoutParams.x = 0
        layoutParams.y = -300
        this.window!!.attributes = layoutParams
    }

}