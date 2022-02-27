package com.zhlw.myscratchview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * author: zlw 2022/02/26
 * 刮刮卡效果
 */
class ScratchRewardView : View{

    private val TAG = "ScratchRewardView"

    /**
     * 未配置蒙版图片
     */
    private val RESOURCE_UNCONFIG = 0

    /**
     * 默认蒙版颜色
     */
    private val DEFAULT_MASK_COLOR = Color.LTGRAY

    /*
     * 默认画笔宽度
     */
    private val DEFAULT_STROKE_WIDTH = 30f

    /**
     * 默认完全刮开阈值  XX %
     */
    private val DEFAULT_UNCOVER_PERCENT = 50 //默认滑动到50%后展示刮刮卡内容

    /**
     * 默认完全刮开数值  XX%
     */
    private val DEFAULT_TOTAL_UNCOVER_PERCENT = 100 //默认滑动到50%后展示刮刮卡内容

    /**
     * 默认遮罩Bitmap 可以是一张图也可用由默认蒙版颜色构造出的纯色Bitmap
     */
    private var mMaskBitmap: Bitmap? = null // 遮罩图层

    /**
     * 蒙版画笔
     */
    private var mMaskPaint : Paint? = null

    /**
     * 轨迹画笔
     */
    private var mTrackPaint : Paint? = null

    /**
     * 保留轨迹的Bitmap
     */
    private var mTrackBitmap : Bitmap? = null

    /**
     * 使用canvas离屏缓存  用于绘制轨迹用的画布
     */
    private var mTrackBitmapCanvas : Canvas? = null

    /**
     * 图像混合模式  需要提供 源图像/目标图像 做融合操作
     */
    private var mXferMode : Xfermode? = null

    /**
     * 目标图像
     */
    private var mDstRect : Rect? = null

    /**
     * 源图像
     */
    private var mSrcRect : Rect? = null

    /**
     * 监听手指滑动事件
     */
    private var mGestureDetector : GestureDetector? = null

    /**
     * 默认蒙版颜色
     */
    private var mMaskColor = DEFAULT_MASK_COLOR

    /**
     * 默认判定擦除比例
     */
    private var mUnCoverPercent = DEFAULT_UNCOVER_PERCENT

    /**
     * 默认完全擦除比例
     */
    private var mTotalUnCoverPercent = DEFAULT_TOTAL_UNCOVER_PERCENT

    /**
     * 当前擦除比例
     */
    private val mPercent = 0

    /**
     * 创建CPU密集性作业
     */
    private var mScope : CoroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * 存放蒙层像素信息的数组
     */
    private var mPixels: IntArray? = null

    /**
     * 设置擦除状态监听器
     */
    private var mEraseStatusListener : EraseStatusListener? = null

    /**
     * 是否完全擦除
     */
    @Volatile var mIsCompleted  = false //是否刮开完


    constructor(context: Context?) : super(context){
        init(context, null, 0)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs){
        init(context, attrs, 0)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ){
        init(context, attrs, defStyleAttr)
    }

    private fun init(context: Context?, attrs: AttributeSet?, defStyleAttr: Int){

        val typedArray = context?.obtainStyledAttributes(attrs, R.styleable.ScratchRewardView)

        val paintWidth : Float = typedArray?.getFloat(
            R.styleable.ScratchRewardView_traceWidth,
            DEFAULT_STROKE_WIDTH
        )?:DEFAULT_STROKE_WIDTH
        val maskColor = typedArray?.getString(R.styleable.ScratchRewardView_maskColor) ?: "#FFCCCCCC"
        mMaskColor = Color.parseColor(maskColor)
        val uncoverPercent = typedArray?.getInt(
            R.styleable.ScratchRewardView_uncoverPercent,
            DEFAULT_UNCOVER_PERCENT
        )?:DEFAULT_UNCOVER_PERCENT
        setUnCoverPercent(uncoverPercent)
        val maskBitmapId = typedArray?.getResourceId(
            R.styleable.ScratchRewardView_maskBitmap,
            RESOURCE_UNCONFIG
        ) ?: RESOURCE_UNCONFIG
        setMaskBitmap(maskBitmapId)

        mMaskPaint = Paint().also {
            it.color = Color.BLACK
            it.isFilterBitmap = true
        }

        mTrackPaint = Paint().also {
            it.isAntiAlias = true
            it.color = Color.BLACK
            it.style = Paint.Style.STROKE
            it.strokeJoin = Paint.Join.ROUND
            it.strokeCap = Paint.Cap.ROUND
            it.strokeWidth = paintWidth
        }

        mXferMode = PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)
        mGestureDetector = GestureDetector(context, GesturePathController())

        typedArray?.recycle()
    }


    private fun computeSrcRect(sourceSize: Point, targetSize: Point, scaleType: ImageView.ScaleType) : Rect{
        if (scaleType == ImageView.ScaleType.CENTER_INSIDE ||
            scaleType == ImageView.ScaleType.MATRIX ||
            scaleType == ImageView.ScaleType.FIT_XY
        ) {
            return Rect(0, 0, sourceSize.x, sourceSize.y)
        } else {
            var scale: Float
            if (Math.abs(sourceSize.x - targetSize.x) < Math.abs(sourceSize.y - targetSize.y)) {
                scale = sourceSize.x.toFloat() / targetSize.x
                if ((targetSize.y * scale).toInt() > sourceSize.y) {
                    scale = sourceSize.y.toFloat() / targetSize.y
                }
            } else {
                scale = sourceSize.y.toFloat() / targetSize.y
                if ((targetSize.x * scale).toInt() > sourceSize.x) {
                    scale = sourceSize.x.toFloat() / targetSize.x
                }
            }
            val srcLeft: Int
            val srcTop: Int
            val srcWidth = (targetSize.x * scale).toInt()
            val srcHeight = (targetSize.y * scale).toInt()
            if (scaleType == ImageView.ScaleType.FIT_START) {
                srcLeft = 0
                srcTop = 0
            } else if (scaleType == ImageView.ScaleType.FIT_END) {
                if (sourceSize.x > sourceSize.y) {
                    srcLeft = sourceSize.x - srcWidth
                    srcTop = 0
                } else {
                    srcLeft = 0
                    srcTop = sourceSize.y - srcHeight
                }
            } else {
                if (sourceSize.x > sourceSize.y) {
                    srcLeft = (sourceSize.x - srcWidth) / 2
                    srcTop = 0
                } else {
                    srcLeft = 0
                    srcTop = (sourceSize.y - srcHeight) / 2
                }
            }
            return Rect(srcLeft, srcTop, srcLeft + srcWidth, srcTop + srcHeight)
        }
    }

    /**
     * As of API Level API level {@value Build.VERSION_CODES#P} the only valid
     * {@code saveFlags} is {@link #ALL_SAVE_FLAG}.
     *
     * All other flags are ignored
     *
     * PS:saveLayer 只接受 all_save_flag
     */
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.run {
            val layerCount = saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

            drawBitmap(mTrackBitmap!!, paddingLeft.toFloat(), paddingTop.toFloat(), mMaskPaint)
            mMaskPaint?.xfermode = mXferMode

            drawBitmap(mMaskBitmap!!, mSrcRect, mDstRect!!, mMaskPaint)
            mMaskPaint?.xfermode = null
            if (!isInEditMode) {
                restoreToCount(layerCount)
            }
        }

    }

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val width = right - left
        val height = bottom - top

        initStatus(width, height)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return isEnabled && mGestureDetector!!.onTouchEvent(event);
    }

    private fun initStatus(width: Int, height: Int){
        if (mTrackBitmap == null || mTrackBitmapCanvas == null){
            mTrackBitmap = Bitmap.createBitmap(
                width - paddingRight - paddingLeft,
                height - paddingBottom - paddingTop,
                Bitmap.Config.ARGB_8888
            )
            mTrackBitmapCanvas = Canvas(mTrackBitmap!!)
        } else if (mTrackBitmap!!.width != width - paddingRight - paddingLeft || mTrackBitmap!!.height != height - paddingBottom - paddingTop){
            mTrackBitmap!!.recycle()
            mTrackBitmap = Bitmap.createBitmap(
                width - paddingRight - paddingLeft,
                height - paddingBottom - paddingTop,
                Bitmap.Config.ARGB_8888
            )
            mTrackBitmapCanvas!!.setBitmap(mTrackBitmap)
        }

        if (mMaskBitmap == null){
            mMaskBitmap = Bitmap.createBitmap(
                width - paddingRight - paddingLeft,
                height - paddingBottom - paddingTop,
                Bitmap.Config.ARGB_8888
            )
            Canvas(mMaskBitmap!!).drawColor(mMaskColor)
        }

        if (mDstRect == null) {
            mDstRect = Rect(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        } else {
            mDstRect!!.set(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        }

        mSrcRect = computeSrcRect(
            Point(mMaskBitmap!!.width, mMaskBitmap!!.height), Point(
                mDstRect!!.width(),
                mDstRect!!.height()
            ), ImageView.ScaleType.CENTER_CROP
        )
        mPixels = IntArray(mMaskBitmap!!.width * mMaskBitmap!!.height)
    }

    /**
     * 完全刮开的动画
     */
    fun showEraseAnim(){
        if (!mIsCompleted) return
        mScope.launch {
            try {
                val pixelLen = width * height//总像素个数
                mMaskBitmap?.getPixels(mPixels, 0, width, 0, 0, width, height)

                for (pos in 0 until pixelLen){
                    erasePixel(pos)
                }

                invalidate()
            } catch (t: Throwable){
                Log.e(TAG, "showEraseAnim error $t")
            }
        }
    }

    private fun erasePixel(pos: Int){
        if (mPixels!![pos] != 0) { //透明的像素值为 0
            mPixels!![pos] = 0
        }

        if (pos%60 == 0){
//            invalidate()
            mMaskBitmap?.setPixels(mPixels, 0, width, 0, 0, width, height)
        }
    }

    /**
     * 重置状态
     */
    fun resetStatus(){
        mTrackBitmap = null
        mMaskBitmap = null
        mPixels = null
        initStatus(width, height)
        mIsCompleted = false
        invalidate()
    }

    /**
     * 设置遮罩图片
     */
    fun setMaskBitmap(maskBitmap: Bitmap){
        mMaskBitmap = maskBitmap
        requestLayout()
    }

    inner class GesturePathController : GestureDetector.OnGestureListener {
        private var downX = 0f
        private var downY = 0f
        private var moveX = 0f
        private var moveY = 0f

        override fun onDown(event: MotionEvent): Boolean {
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(true)
            }
            downX = event.x
            downY = event.y
            return true
        }

        override fun onShowPress(e: MotionEvent) {}

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (isEnabled && isClickable) {
                performClick()
            }
            computeDrawingPercent()
            return true
        }

        override fun onScroll(
            e1: MotionEvent,
            event: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            moveX = event.x
            moveY = event.y
            mTrackBitmapCanvas?.drawLine(downX, downY, moveX, moveY, mTrackPaint!!)
            downX = moveX
            downY = moveY
            invalidate()
            return true
        }

        override fun onLongPress(e: MotionEvent) {}

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            computeDrawingPercent()
            return false
        }

    }

    /**
     * 设置达到多少后完全揭开
     *
     * @param uncoverPercent  100 代表100%  需要 > 30
     */
    private fun setUnCoverPercent(uncoverPercent: Int){
        if (uncoverPercent <= 30){
            mUnCoverPercent = DEFAULT_UNCOVER_PERCENT
        } else {
            mUnCoverPercent = uncoverPercent
        }
    }

    private fun setMaskBitmap(resId: Int){
        if (resId != RESOURCE_UNCONFIG){
            mMaskBitmap = BitmapFactory.decodeResource(resources, resId);
        }
    }

    /**
     * 计算已经擦除比例多少
     */
    private fun computeDrawingPercent(){
        val width = getWidth()
        val height = getHeight()
        mScope.launch {
            try {
                function(width, height)
            } catch (t: Throwable) {
                Log.e(TAG, "computeDrawingPercent: error $t")
            }
        }
    }

    private fun function(width: Int, height: Int){
        if (!mIsCompleted){
            mTrackBitmap?.getPixels(mPixels, 0, width, 0, 0, width, height)

            var erasePixelCount = 0f //擦除的像素个数

            val totalPixelCount = (width * height).toFloat() //总像素个数

            for (pos in 0 until totalPixelCount.toInt()) {
                if (mPixels!![pos] != 0) { //透明的像素值为 0
                    erasePixelCount++
                }
            }

            var percent = 0
            if (erasePixelCount >= 0 && totalPixelCount > 0) {
                percent = Math.round(erasePixelCount * 100 / totalPixelCount)
            }

            mIsCompleted = (percent >= mTotalUnCoverPercent) || (percent >= mUnCoverPercent)  //大于100%或大于设定的数值都代表已经刮开了
            if (mIsCompleted){

                mEraseStatusListener?.onCompleted(this@ScratchRewardView)
            }
        }
    }

    /**
     * 擦除状态监听器
     */
    interface EraseStatusListener {
        /**
         * 擦除完成回调函数
         *
         * @param view
         */
        fun onCompleted(view: ScratchRewardView?)
    }

    fun setEraseStatusListener(listener: EraseStatusListener){
        mEraseStatusListener = listener
    }

    /**
     * 结束任务
     */
    private fun stopTask(){
        mScope.cancel()
    }

    /**
     * 从窗口上消失时取消任务
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow: ")
        stopTask()
    }

}