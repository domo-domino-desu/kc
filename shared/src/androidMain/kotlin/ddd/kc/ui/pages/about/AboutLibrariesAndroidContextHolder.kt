package ddd.kc.ui.pages.about

import android.content.Context

object AboutLibrariesAndroidContextHolder {
  @Volatile
  var context: Context? = null
    private set

  fun initialize(context: Context) {
    this.context = context.applicationContext
  }
}
