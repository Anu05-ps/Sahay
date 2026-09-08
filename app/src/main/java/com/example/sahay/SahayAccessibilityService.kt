package com.example.sahay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

class SahayAccessibilityService :
    AccessibilityService() {

    companion object {

        private var instance:
                SahayAccessibilityService? = null

        fun stopMapsNavigation(): Boolean {

            return instance?.clickMapsButton(
                listOf(
                    "Pause navigation",
                    "Pause",
                    "Stop navigation",
                    "Stop",
                    "Exit navigation"
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

        fun returnToSahay() {

            val service =
                instance ?: return

            /*
             * Stop the microphone service first.
             */
            val serviceIntent =
                Intent(
                    service,
                    NavigationVoiceService::class.java
                )

            service.stopService(serviceIntent)

            Handler(
                Looper.getMainLooper()
            ).postDelayed({

                val intent =
                    Intent(
                        service,
                        MainActivity::class.java
                    )

                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP

                service.startActivity(intent)

            }, 300)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this
    }

    override fun onAccessibilityEvent(
        event: android.view.accessibility.AccessibilityEvent?
    ) {
        // Nothing required here.
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

        /*
         * Some Maps versions expose the text
         * differently, so also inspect content
         * descriptions.
         */
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

                if (description.contains(
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