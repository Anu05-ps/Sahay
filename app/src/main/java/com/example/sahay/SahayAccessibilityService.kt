package com.example.sahay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class SahayAccessibilityService :
    AccessibilityService() {

    companion object {

        private var instance:
                SahayAccessibilityService? = null

        fun stopMapsNavigation(): Boolean {

            return instance?.clickMapsButton(
                listOf(
                    "Exit navigation",
                    "Stop navigation",
                    "End navigation",
                    "Stop",
                    "End"
                )
            ) ?: false
        }

        fun continueMapsNavigation(): Boolean {

            return instance?.clickMapsButton(
                listOf(
                    "Resume navigation",
                    "Resume",
                    "Continue navigation",
                    "Continue"
                )
            ) ?: false
        }
    }

    override fun onServiceConnected() {

        super.onServiceConnected()

        instance = this
    }

    override fun onAccessibilityEvent(
        event: android.view.accessibility.AccessibilityEvent?
    ) {
        // No automatic action.
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {

        instance = null

        super.onDestroy()
    }

    private fun clickMapsButton(
        possibleTexts: List<String>
    ): Boolean {

        val root =
            rootInActiveWindow
                ?: return false

        // Search visible text
        for (text in possibleTexts) {

            val nodes =
                root.findAccessibilityNodeInfosByText(
                    text
                )

            for (node in nodes) {

                if (clickNode(node)) {

                    return true
                }
            }
        }

        // Search content descriptions
        return searchContentDescriptions(
            root,
            possibleTexts
        )
    }

    private fun searchContentDescriptions(
        node: AccessibilityNodeInfo,
        possibleTexts: List<String>
    ): Boolean {

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()
                ?.lowercase()

        if (description != null) {

            for (text in possibleTexts) {

                if (
                    description.contains(
                        text.lowercase()
                    )
                ) {

                    if (clickNode(node)) {

                        return true
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {

            val child =
                node.getChild(i)

            if (child != null) {

                if (
                    searchContentDescriptions(
                        child,
                        possibleTexts
                    )
                ) {

                    return true
                }
            }
        }

        return false
    }

    private fun clickNode(
        node: AccessibilityNodeInfo
    ): Boolean {

        if (node.isClickable) {

            return node.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )
        }

        var parent =
            node.parent

        while (parent != null) {

            if (parent.isClickable) {

                return parent.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }

            parent =
                parent.parent
        }

        return false
    }
}