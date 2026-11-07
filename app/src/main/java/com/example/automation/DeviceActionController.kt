package com.example.automation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.memory.MemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

object DeviceActionController {

    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Resolves a phone number from Memory store or system ContactsContract.
     */
    fun resolvePhoneNumber(context: Context, nameOrRelationship: String, memoryManager: MemoryManager): String? {
        val query = nameOrRelationship.trim()
        if (query.isBlank()) return null

        // 1. Check if it's already a numeric phone string
        val digitCount = query.count { it.isDigit() }
        if (digitCount >= 7 && (query.startsWith("+") || query.all { it.isDigit() || it == '-' || it == ' ' })) {
            return query.filter { it.isDigit() || it == '+' }
        }

        // 2. Check persistent user memory store
        val memoryValue = memoryManager.getMemory(query)
            ?: memoryManager.getMemory("${query}_phone")
            ?: memoryManager.getMemory("${query}_number")
            ?: memoryManager.getMemory("${query}_mobile")

        if (!memoryValue.isNullOrBlank()) {
            val digits = memoryValue.filter { it.isDigit() || it == '+' }
            if (digits.length >= 5) return digits
        }

        // 3. Query Android ContactsContract
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$query%"),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        if (!number.isNullOrBlank()) {
                            return number.filter { char -> char.isDigit() || char == '+' }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Directive 7.1: YouTube Automation.
     * Launches YouTube search intent and auto-clicks the first result so it plays immediately.
     */
    fun playOnYouTube(context: Context, query: String): Boolean {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return false

        try {
            val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val launched = try {
                context.startActivity(ytIntent)
                true
            } catch (_: Exception) {
                // Fallback to web browser YouTube search
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(cleanQuery, "UTF-8")}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                true
            }

            if (launched) {
                // Schedule automated click on first result via AccessibilityService
                scope.launch {
                    delay(1500)
                    JarvisAccessibilityService.instance?.clickFirstClickableItem()
                }
            }
            return launched
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Directive 7.2: WhatsApp Automation.
     * Resolves contact, opens WhatsApp chat, and automatically clicks send.
     */
    fun sendWhatsAppMessage(context: Context, recipient: String, message: String, memoryManager: MemoryManager): String {
        val phone = resolvePhoneNumber(context, recipient, memoryManager)
        val cleanMsg = message.trim()

        try {
            val uri = if (!phone.isNullOrBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${URLEncoder.encode(cleanMsg, "UTF-8")}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode(cleanMsg, "UTF-8")}")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)

            // Automate clicking send button if accessibility service is active
            scope.launch {
                delay(1800)
                JarvisAccessibilityService.instance?.clickSendButton()
            }

            return if (!phone.isNullOrBlank()) {
                "Opening WhatsApp dispatch to $recipient ($phone)..."
            } else {
                "Opening WhatsApp for $recipient..."
            }
        } catch (e: Exception) {
            return "WhatsApp client not accessible on this device."
        }
    }

    /**
     * Directive 7.3: Direct Phone Calling using standard safe Intent.ACTION_DIAL (zero Play Protect warning).
     */
    fun callContact(context: Context, nameOrNumber: String, memoryManager: MemoryManager): String {
        val phone = resolvePhoneNumber(context, nameOrNumber, memoryManager) ?: nameOrNumber.filter { it.isDigit() || it == '+' }
        if (phone.isBlank()) {
            return "Unable to resolve contact number for $nameOrNumber."
        }

        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return "Dialing phone frequency for $nameOrNumber ($phone)..."
        } catch (e: Exception) {
            return "Call dialer error: ${e.localizedMessage}"
        }
    }

    /**
     * Directive 7.4: App Launcher.
     */
    fun openApp(context: Context, appName: String): String {
        val target = appName.trim().lowercase()
        val packageMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "settings" to "com.android.settings",
            "camera" to "com.android.camera",
            "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm"
        )

        val targetPkg = packageMap[target]

        try {
            val pm = context.packageManager
            if (targetPkg != null) {
                val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    return "Launching $appName subsystem."
                }
            }

            // Search installed applications matching name
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.contains(target, ignoreCase = true)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)
                        return "Launching $label."
                    }
                }
            }

            return "Application '$appName' not found on system."
        } catch (e: Exception) {
            return "App launch error: ${e.localizedMessage}"
        }
    }
}
