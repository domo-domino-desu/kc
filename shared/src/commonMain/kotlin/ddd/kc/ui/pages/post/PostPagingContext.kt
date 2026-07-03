package ddd.kc.ui.pages.post

import ddd.kc.data.model.PlatformSerializable

sealed interface PostPagingContext : PlatformSerializable {
  data object None : PostPagingContext

  data class Popular(
      val date: String?,
      val period: String,
  ) : PostPagingContext

  data class Search(
      val query: String,
      val defaultPopularDate: String?,
  ) : PostPagingContext

  data class Tag(
      val tag: String,
  ) : PostPagingContext

  data class Creator(
      val service: String,
      val creatorId: String,
  ) : PostPagingContext
}
