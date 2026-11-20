package com.chen.classtable

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 课表页滑动容器：在任意区域左右滑动切换上一周/下一周，
 * 同时不拦截点击与纵向滚动（仅在水平位移明显时拦截）。
 */
class SwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** +1 = 下一周（左滑），-1 = 上一周（右滑）。 */
    var onSwipe: ((Int) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > dp(24f) && abs(dx) > abs(dy) * 1.4f) {
                    swiping = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return swiping
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (swiping) return true
            MotionEvent.ACTION_UP -> {
                if (swiping) {
                    val dx = ev.x - downX
                    if (abs(dx) > dp(48f)) {
                        onSwipe?.invoke(if (dx < 0) 1 else -1)
                    }
                    swiping = false
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
