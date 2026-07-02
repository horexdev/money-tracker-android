package dev.horex.moneytracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class NotificationManifestTest {
    @Test
    fun manifestDeclaresLocalNotificationPermissionWithoutNetworkPermission() {
        val permissions = readManifestPermissions()

        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertEquals(false, "android.permission.INTERNET" in permissions)
    }

    private fun readManifestPermissions(): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val permissions = document.getElementsByTagName("uses-permission")

        return (0 until permissions.length).map { index ->
            val element = permissions.item(index) as Element
            element.getAttributeNS(ANDROID_NAMESPACE, "name").ifBlank {
                element.getAttribute("android:name")
            }
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
