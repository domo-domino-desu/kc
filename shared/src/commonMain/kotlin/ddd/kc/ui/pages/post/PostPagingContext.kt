package ddd.kc.ui.pages.post

import ddd.kc.data.model.AiFilterMode
import kotlinx.serialization.Serializable

@Serializable
sealed interface PostPagingContext {
  @Serializable data object None : PostPagingContext

  @Serializable
  data class Popular(
      val date: String?,
      val period: String,
      val aiFilter: AiFilterMode = AiFilterMode.SHOW,
  ) : PostPagingContext

  @Serializable
  data class Search(
      val query: String,
      val defaultPopularDate: String?,
      val aiFilter: AiFilterMode = AiFilterMode.SHOW,
  ) : PostPagingContext

  @Serializable
  data class Tag(
      val tag: String,
  ) : PostPagingContext

  @Serializable
  data class Creator(
      val service: String,
      val creatorId: String,
  ) : PostPagingContext
}
