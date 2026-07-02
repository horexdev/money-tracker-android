package dev.horex.moneytracker.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AndroidNotificationPermissionController(
    context: Context,
    preferencesName: String = NOTIFICATION_PERMISSION_PREFERENCES,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    val runtimePermissionName: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun permissionStatus(): NotificationPermissionStatus {
        return resolveNotificationPermissionStatus(
            sdkInt = Build.VERSION.SDK_INT,
            hasPostNotificationsPermission = hasPostNotificationsPermission(),
            hasRequestedRuntimePermission = hasRequestedRuntimePermission(),
        )
    }

    fun canPostNotifications(): Boolean {
        return permissionStatus().canPostNotifications
    }

    fun shouldRequestRuntimePermission(): Boolean {
        return permissionStatus().shouldRequestRuntimePermission
    }

    fun markRuntimePermissionRequested() {
        preferences.edit()
            .putBoolean(KEY_RUNTIME_PERMISSION_REQUESTED, true)
            .apply()
    }

    fun onRuntimePermissionResult(granted: Boolean) {
        preferences.edit()
            .putBoolean(KEY_RUNTIME_PERMISSION_REQUESTED, true)
            .putBoolean(KEY_RUNTIME_PERMISSION_LAST_GRANTED, granted)
            .apply()
    }

    fun lastRuntimePermissionGranted(): Boolean? {
        if (!hasRequestedRuntimePermission()) {
            return null
        }
        return preferences.getBoolean(KEY_RUNTIME_PERMISSION_LAST_GRANTED, false)
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequestedRuntimePermission(): Boolean {
        return preferences.getBoolean(KEY_RUNTIME_PERMISSION_REQUESTED, false)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_PREFERENCES = "money_tracker_notification_permissions"
        const val KEY_RUNTIME_PERMISSION_REQUESTED = "runtime_permission_requested"
        const val KEY_RUNTIME_PERMISSION_LAST_GRANTED = "runtime_permission_last_granted"
    }
}
