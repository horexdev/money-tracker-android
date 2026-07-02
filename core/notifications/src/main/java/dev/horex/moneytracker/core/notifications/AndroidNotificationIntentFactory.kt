package dev.horex.moneytracker.core.notifications

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AndroidNotificationIntentFactory(
    context: Context,
    private val launcherActivityClass: Class<out Activity>,
) {
    private val appContext = context.applicationContext

    fun openAppPendingIntent(
        requestCode: Int,
        flags: Int = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
    ): PendingIntent {
        val intent = Intent(appContext, launcherActivityClass)
            .addFlags(flags)

        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
