package tgo1014.gridlauncher.app.injection

import android.content.Context
import android.content.pm.PackageManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tgo1014.gridlauncher.data.DispatcherProviderImpl
import tgo1014.gridlauncher.domain.models.DispatcherProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun provideDispatchers(dispatch: DispatcherProviderImpl): DispatcherProvider

    companion object {
        @Provides
        @Singleton
        fun providePackageManager(@ApplicationContext context: Context): PackageManager {
            return context.packageManager
        }

        @Provides
        @Singleton
        fun provideScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Provides
        @Singleton
        fun provideAppWidgetManager(@ApplicationContext context: Context): android.appwidget.AppWidgetManager {
            return android.appwidget.AppWidgetManager.getInstance(context)
        }

        @Provides
        @Singleton
        fun provideLauncherAppWidgetHost(@ApplicationContext context: Context): tgo1014.gridlauncher.data.widget.LauncherAppWidgetHost {
            return tgo1014.gridlauncher.data.widget.LauncherAppWidgetHost(context)
        }
    }

}