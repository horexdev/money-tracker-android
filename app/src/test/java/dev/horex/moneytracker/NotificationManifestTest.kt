package dev.horex.moneytracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class NotificationManifestTest {
    @Test
    fun manifestDeclaresNotificationAndExplicitOnlineRatePermissions() {
        val permissions = readManifestPermissions()

        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertTrue("android.permission.INTERNET" in permissions)
    }

    @Test
    fun manifestRegistersApplicationForWorkManagerConfiguration() {
        assertEquals(".MoneyTrackerApplication", readApplicationName())
    }

    private fun readApplicationName(): String {
        val document = parseManifest()
        val application = document.getElementsByTagName("application").item(0) as Element

        return application.getAttributeNS(ANDROID_NAMESPACE, "name").ifBlank {
            application.getAttribute("android:name")
        }
    }

    private fun readManifestPermissions(): List<String> {
        val document = parseManifest()
        val permissions = document.getElementsByTagName("uses-permission")

        return (0 until permissions.length).map { index ->
            val element = permissions.item(index) as Element
            element.getAttributeNS(ANDROID_NAMESPACE, "name").ifBlank {
                element.getAttribute("android:name")
            }
        }
    }

    private fun parseManifest() =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
