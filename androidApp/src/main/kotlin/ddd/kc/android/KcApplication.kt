package ddd.kc.android

import android.app.Application
import co.touchlab.kermit.Severity
import ddd.kc.androidPlatformModule
import ddd.kc.di.startAppKoin
import ddd.kc.utils.logging.KcLog

class KcApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    KcLog.init(Severity.Info)
    startAppKoin(androidPlatformModule(applicationContext))
  }
}
