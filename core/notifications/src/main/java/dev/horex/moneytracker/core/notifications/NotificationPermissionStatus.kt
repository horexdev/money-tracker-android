package dev.horex.moneytracker.core.notifications

import android.os.Build

enum class NotificationPermissionStatus {
    NotRequired,
    Granted,
    NeedsRuntimePermission,
    Denied,
}

val NotificationPermissionStatus.canPostNotifications: Boolean
    get() = this == NotificationPermissionStatus.NotRequired ||
        this == NotificationPermissionStatus.Granted

val NotificationPermissionStatus.shouldRequestRuntimePermission: Boolean
    get() = this == NotificationPermissionStatus.NeedsRuntimePermission

fun resolveNotificationPermissionStatus(
    sdkInt: Int,
    hasPostNotificationsPermission: Boolean,
    hasRequestedRuntimePermission: Boolean,
): NotificationPermissionStatus {
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        return NotificationPermissionStatus.NotRequired
    }
    if (hasPostNotificationsPermission) {
        return NotificationPermissionStatus.Granted
    }
    return if (hasRequestedRuntimePermission) {
        NotificationPermissionStatus.Denied
    } else {
        NotificationPermissionStatus.NeedsRuntimePermission
    }
}
