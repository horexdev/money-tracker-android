package dev.horex.moneytracker.core.notifications

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionStatusTest {
    @Test
    fun permissionIsNotRequiredBeforeAndroid13() {
        assertEquals(
            NotificationPermissionStatus.NotRequired,
            resolveNotificationPermissionStatus(
                sdkInt = Build.VERSION_CODES.S_V2,
                hasPostNotificationsPermission = false,
                hasRequestedRuntimePermission = false,
            ),
        )
    }

    @Test
    fun android13PermissionStatusTracksGrantAndPromptHistory() {
        assertEquals(
            NotificationPermissionStatus.Granted,
            resolveNotificationPermissionStatus(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasPostNotificationsPermission = true,
                hasRequestedRuntimePermission = false,
            ),
        )
        assertEquals(
            NotificationPermissionStatus.NeedsRuntimePermission,
            resolveNotificationPermissionStatus(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasPostNotificationsPermission = false,
                hasRequestedRuntimePermission = false,
            ),
        )
        assertEquals(
            NotificationPermissionStatus.Denied,
            resolveNotificationPermissionStatus(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasPostNotificationsPermission = false,
                hasRequestedRuntimePermission = true,
            ),
        )
    }

    @Test
    fun statusExtensionsDriveRuntimeFlow() {
        assertEquals(true, NotificationPermissionStatus.NotRequired.canPostNotifications)
        assertEquals(true, NotificationPermissionStatus.Granted.canPostNotifications)
        assertEquals(false, NotificationPermissionStatus.NeedsRuntimePermission.canPostNotifications)
        assertEquals(false, NotificationPermissionStatus.Denied.canPostNotifications)

        assertEquals(true, NotificationPermissionStatus.NeedsRuntimePermission.shouldRequestRuntimePermission)
        assertEquals(false, NotificationPermissionStatus.Denied.shouldRequestRuntimePermission)
    }
}
