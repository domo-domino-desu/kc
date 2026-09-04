package ddd.kc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PageInfo(
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val currentOffset: Int = 0,
    val lastOffset: Int = 0,
) {
  val hasPrevious: Boolean
    get() = currentPage > 1

  val hasNext: Boolean
    get() = currentPage < lastPage
}

@Serializable
data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val pageInfo: PageInfo? = null,
)

@Serializable
data class CreatorPostsPage(
    val items: List<Post> = emptyList(),
    val pageInfo: PageInfo? = null,
    val communityAvailable: Boolean = false,
)

@Serializable
data class PopularPage(
    val props: PopularProps = PopularProps(),
    val info: PopularInfo = PopularInfo(),
    val posts: List<Post> = emptyList(),
    val pageInfo: PageInfo? = null,
)

@Serializable
data class PopularProps(
    val count: Int = 0,
    @SerialName("earliest_date_for_popular") val earliestDateForPopular: String? = null,
    val today: String? = null,
)

@Serializable
data class PopularInfo(
    val date: String? = null,
    @SerialName("max_date") val maxDate: String? = null,
    @SerialName("min_date") val minDate: String? = null,
    @SerialName("navigation_dates") val navigationDates: PopularNavigationDates? = null,
    @SerialName("range_desc") val rangeDesc: String? = null,
    val scale: String? = null,
)

@Serializable
data class PopularNavigationDates(
    val recent: List<String> = emptyList(),
    val day: List<String> = emptyList(),
    val month: List<String> = emptyList(),
    val week: List<String> = emptyList(),
)
