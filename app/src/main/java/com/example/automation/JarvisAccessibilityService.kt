package com.example.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${JarvisAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be used to track active foreground window
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Native Android 11-16 Screen Capture using AccessibilityService.takeScreenshot.
     * Works in background across all applications without prompting MediaProjection dialogs.
     */
    fun captureScreen(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                    ?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                callback(bitmap)
                            } catch (e: Exception) {
                                callback(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                callback(null)
            }
        } else {
            callback(null)
        }
    }

    /**
     * Finds any node in the active window containing target text and clicks it.
     */
    fun clickText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanTarget = targetText.trim()
        val matchingNodes = root.findAccessibilityNodeInfosByText(cleanTarget)
        if (!matchingNodes.isNullOrEmpty()) {
            for (node in matchingNodes) {
                if (performClickRecursive(node)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Finds and clicks the first clickable video / card in YouTube search results.
     */
    fun clickFirstClickableItem(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findFirstClickableRecursive(root)
    }

    private fun findFirstClickableRecursive(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.className?.toString()?.contains("Button", ignoreCase = true) != true) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            // Avoid clicking top search bar or tabs
            if (text.isNotBlank() && !text.contains("Search", ignoreCase = true) && !text.contains("Filters", ignoreCase = true)) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findFirstClickableRecursive(child)) {
                return true
            }
        }
        return false
    }

    /**
     * Finds and clicks a send button (used for automated WhatsApp dispatch).
     */
    fun clickSendButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        // Search by contentDescription or text "Send"
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        candidates.addAll(root.findAccessibilityNodeInfosByText("Send"))
        candidates.addAll(root.findAccessibilityNodeInfosByText("send"))

        for (node in candidates) {
            if (performClickRecursive(node)) {
                return true
            }
        }
        return false
    }

    /**
     * Scrolls active window forward or backward.
     */
    fun scrollScreen(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return performScrollRecursive(root, action)
    }

    private fun performScrollRecursive(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (performScrollRecursive(child, action)) {
                return true
            }
        }
        return false
    }

    private fun performClickRecursive(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }
}
