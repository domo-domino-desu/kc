package ddd.kc.ui.pages.more

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import ddd.kc.data.model.Platform
import ddd.kc.data.network.KcApiClient
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.data.repository.PostRepository
import ddd.kc.util.logging.KcLog
import kotlinx.coroutines.launch

private val log = KcLog.withTag("MoreScreenModel")

class MoreScreenModel(
    private val api: KcApiClient,
    private val postRepo: PostRepository,
    private val creatorRepo: CreatorRepository,
) : ScreenModel {

  fun logout(platform: Platform, onError: (String) -> Unit) {
    screenModelScope.launch {
      log.i { "退出登录 -> 开始(platform=${platform.name})" }
      runCatching { api.logout(platform) }
          .onSuccess {
            postRepo.clearFavoritesCache(platform)
            creatorRepo.clearFavoritesCache(platform)
            log.i { "退出登录 -> 成功(platform=${platform.name})" }
          }
          .onFailure {
            log.e(it) { "退出登录 -> 失败(platform=${platform.name})" }
            onError(it.message ?: "退出失败")
          }
    }
  }
}
