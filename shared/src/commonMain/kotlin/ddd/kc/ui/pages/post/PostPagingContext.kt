package ddd.kc.ui.pages.post

import kotlinx.serialization.Serializable

@Serializable
sealed interface PostPagingContext {
  @Serializable data object None : PostPagingContext

  @Serializable
  data class Popular(
      val date: String?,
      val period: String,
  ) : PostPagingContext

  @Serializable
  data class Search(
      val query: String,
      val defaultPopularDate: String?,
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
