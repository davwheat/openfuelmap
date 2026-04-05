package dev.davwheat.openfuelmap.common.ui.extension

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityManager

fun View.triggerHapticFeedback() =
    reallyPerformHapticFeedback(
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK
    )

fun View.triggerStrongHapticFeedback() =
    reallyPerformHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

private fun View.reallyPerformHapticFeedback(feedbackConstant: Int) {
    if (context.isTouchExplorationEnabled()) {
        // Don't mess with a blind person's vibrations
        return
    }
    // Either this needs to be set to true, or android:hapticFeedbackEnabled="true" needs to be set
    // in XML
    isHapticFeedbackEnabled = true

    performHapticFeedback(feedbackConstant)
}

private fun Context.isTouchExplorationEnabled(): Boolean {
    // can be null during unit tests
    val accessibilityManager =
        getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager?
    return accessibilityManager?.isTouchExplorationEnabled ?: false
}
