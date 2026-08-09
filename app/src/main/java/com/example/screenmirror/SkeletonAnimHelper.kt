package com.example.screenmirror

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView

class SkeletonAnimHelper {

    private var animatorSet: AnimatorSet? = null
    private val animatedViews = mutableListOf<View>()

    var container: View? = null
        private set
    var iconView: View? = null
        private set
    var titleView: View? = null
        private set
    var subtitleView: View? = null
        private set
    var statusView: View? = null
        private set
    var hintView: TextView? = null
        private set

    fun init(
        container: View,
        icon: View,
        title: View,
        subtitle: View,
        status: View,
        hint: TextView
    ) {
        this.container = container
        this.iconView = icon
        this.titleView = title
        this.subtitleView = subtitle
        this.statusView = status
        this.hintView = hint
    }

    fun show(hint: String) {
        val c = container ?: return
        c.visibility = View.VISIBLE
        hintView?.text = hint
        startSkeletonAnimation(
            iconView ?: return,
            titleView ?: return,
            subtitleView ?: return,
            statusView ?: return
        )
    }

    fun hide() {
        val c = container ?: return
        hideSkeleton(c)
    }

    fun startSkeletonAnimation(vararg views: View) {
        stopAnimation()
        animatedViews.clear()
        animatedViews.addAll(views)

        val animators = views.map { view ->
            createPulseAnimator(view)
        }

        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            interpolator = LinearInterpolator()
            duration = 1500
            start()
        }
    }

    private fun createPulseAnimator(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 0.3f, 1f, 0.3f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    fun startShimmerAnimation(vararg views: View) {
        stopAnimation()
        animatedViews.clear()
        animatedViews.addAll(views)

        val animators = views.map { view ->
            createShimmerAnimator(view)
        }

        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            interpolator = LinearInterpolator()
            duration = 1200
            start()
        }
    }

    private fun createShimmerAnimator(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -100f, 100f, -100f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
    }

    fun showSkeleton(container: View) {
        container.visibility = View.VISIBLE
    }

    fun hideSkeleton(container: View) {
        container.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(null)
            .withEndAction {
                container.visibility = View.GONE
                container.alpha = 1f
                stopAnimation()
            }
            .start()
    }

    fun stopAnimation() {
        animatorSet?.cancel()
        animatorSet = null
        animatedViews.clear()
    }
}
