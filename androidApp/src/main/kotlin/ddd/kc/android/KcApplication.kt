package ddd.kc.android

import android.app.Application
import co.touchlab.kermit.Severity
import ddd.kc.androidPlatformModule
import ddd.kc.di.startAppKoin
import ddd.kc.ui.pages.about.AboutLibrariesAndroidContextHolder
import ddd.kc.utils.logging.KcLog

class KcApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    KcLog.init(Severity.Info)
    AboutLibrariesAndroidContextHolder.initialize(applicationContext)
    startAppKoin(androidPlatformModule(applicationContext, R.mipmap.ic_launcher))
  }
}
