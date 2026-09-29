package com.adr.checkiap.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Trợ năng (Accessibility Service) — Tự động hóa điều hướng trong app mục tiêu
 * để đưa người dùng trực tiếp tới hộp thoại Google Play Billing mà không cần Root hay LSPatch.
 */
class IapAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "IapAccessibility"
        private const val MAX_ACTIONS = 6
        private const val SESSION_TIMEOUT_MS = 35_000L
        private const val ACTION_DELAY_MS = 1_200L

        @Volatile
        private var activeTargetPackage: String? = null

        @Volatile
        private var customKeywords: List<String> = emptyList()

        private var sessionStartTime = 0L
        private var actionCount = 0
        private var lastActionTime = 0L

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expectedService = "${context.packageName}/${IapAccessibilityService::class.java.canonicalName}"
            return enabledServices.contains(expectedService) || enabledServices.contains(IapAccessibilityService::class.java.simpleName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun startAutomation(context: Context, targetPackage: String, keywords: List<String> = emptyList()) {
            activeTargetPackage = targetPackage
            customKeywords = keywords
            sessionStartTime = System.currentTimeMillis()
            actionCount = 0
            lastActionTime = 0L

            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Toast.makeText(
                    context,
                    "⚡ Trợ năng: Đang mở app & tự động tìm nút Dùng thử / Mua...",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "Không thể mở package: $targetPackage", Toast.LENGTH_SHORT).show()
            }
        }

        fun stopAutomation() {
            activeTargetPackage = null
            customKeywords = emptyList()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val target = activeTargetPackage ?: return
        val eventPackage = event?.packageName?.toString() ?: return

        // Check session timeout
        if (System.currentTimeMillis() - sessionStartTime > SESSION_TIMEOUT_MS) {
            stopAutomation()
            return
        }

        // When Google Play Billing sheet is displayed, mission complete!
        if (eventPackage == "com.android.vending") {
            mainHandler.post {
                Toast.makeText(applicationContext, "✓ [Trợ năng] Đã mở thành công Google Play Billing!", Toast.LENGTH_LONG).show()
            }
            stopAutomation()
            return
        }

        // Only process events when target app is in foreground
        if (eventPackage != target) return

        val now = System.currentTimeMillis()
        if (now - lastActionTime < ACTION_DELAY_MS) return
        if (actionCount >= MAX_ACTIONS) {
            stopAutomation()
            return
        }

        val rootNode = rootInActiveWindow ?: return
        try {
            findAndPerformAction(rootNode)
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {
        stopAutomation()
    }

    private fun findAndPerformAction(root: AccessibilityNodeInfo) {
        val candidateNodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(root, candidateNodes)

        // Tier 1: Exact Trial / 0đ / Free matches
        val tier1Keywords = listOf(
            "dùng thử miễn phí", "dùng thử 0đ", "dùng thử 7 ngày", "dùng thử 3 ngày",
            "free trial", "start free trial", "try for free", "7-day free trial",
            "7 days free", "3 days free", "0đ", "miễn phí 7 ngày"
        ) + customKeywords.filter { it.contains("trial", ignoreCase = true) || it.contains("free", ignoreCase = true) }

        // Tier 2: Upgrade / Pro / VIP / Subscribe / Purchase matches
        val tier2Keywords = listOf(
            "tiếp tục", "continue", "nâng cấp", "upgrade", "đăng ký ngay", "subscribe",
            "tham gia ngay", "join pro", "mở khóa pro", "mua ngay", "mua pro", "nhận ưu đãi"
        )

        // Tier 3: Pro badges / banners
        val tier3Keywords = listOf("pro", "vip", "premium")

        val targetNode = findBestMatch(candidateNodes, tier1Keywords)
            ?: findBestMatch(candidateNodes, tier2Keywords)
            ?: findBestMatch(candidateNodes, tier3Keywords)

        if (targetNode != null) {
            val clickable = getClickableAncestor(targetNode) ?: targetNode
            val clickedText = targetNode.text?.toString() ?: targetNode.contentDescription?.toString() ?: "Nút"
            val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                lastActionTime = System.currentTimeMillis()
                actionCount++
                mainHandler.post {
                    Toast.makeText(
                        applicationContext,
                        "⚡ [Trợ năng] Đã bấm: \"$clickedText\"",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun findBestMatch(nodes: List<AccessibilityNodeInfo>, keywords: List<String>): AccessibilityNodeInfo? {
        for (keyword in keywords) {
            for (node in nodes) {
                val text = node.text?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (text.contains(keyword.lowercase()) || desc.contains(keyword.lowercase())) {
                    return node
                }
            }
        }
        return null
    }

    private fun getClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        node ?: return
        if (node.isVisibleToUser) {
            val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
            if (hasText || node.isClickable) {
                list.add(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectNodes(child, list)
            }
        }
    }
}
