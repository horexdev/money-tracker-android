package dev.horex.moneytracker

import android.app.Application
import androidx.work.Configuration

class MoneyTrackerApplication : Application(), Configuration.Provider {
    private var appContainerInstance: MoneyTrackerAppContainer? = null

    internal val appContainer: MoneyTrackerAppContainer
        get() = appContainerInstance ?: MoneyTrackerAppContainer(this).also { container ->
            appContainerInstance = container
        }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(appContainer.backgroundWorkerFactory)
            .build()

    override fun onTerminate() {
        appContainerInstance?.close()
        super.onTerminate()
    }
}
