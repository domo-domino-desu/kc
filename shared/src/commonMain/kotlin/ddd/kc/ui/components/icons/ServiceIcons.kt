package ddd.kc.ui.components.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import ddd.kc.generated.symbols.icons.arcticons.Icons as ArcticonsIcons
import ddd.kc.generated.symbols.icons.arcticons.icons.FanslyArcticons
import ddd.kc.generated.symbols.icons.fontawesomebrands.Icons as FontAwesomeBrandIcons
import ddd.kc.generated.symbols.icons.fontawesomebrands.icons.DiscordFontawesomebrands
import ddd.kc.generated.symbols.icons.lucide.Icons as LucideIcons
import ddd.kc.generated.symbols.icons.lucide.icons.SectionLucide
import ddd.kc.generated.symbols.icons.materialsymbols.Icons as MaterialSymbolsIcons
import ddd.kc.generated.symbols.icons.materialsymbols.icons.LockOpenW400Outlined
import ddd.kc.generated.symbols.icons.mdi.Icons as MdiIcons
import ddd.kc.generated.symbols.icons.mdi.icons.PatreonMdi
import ddd.kc.generated.symbols.icons.service.Icons as LocalServiceIcons
import ddd.kc.generated.symbols.icons.service.icons.Dlsite
import ddd.kc.generated.symbols.icons.service.icons.Fantia
import ddd.kc.generated.symbols.icons.simpleicons.Icons as SimpleIcons
import ddd.kc.generated.symbols.icons.simpleicons.icons.AfdianSimpleicons
import ddd.kc.generated.symbols.icons.simpleicons.icons.BoostySimpleicons
import ddd.kc.generated.symbols.icons.simpleicons.icons.GumroadSimpleicons
import ddd.kc.generated.symbols.icons.simpleicons.icons.OnlyfansSimpleicons
import ddd.kc.generated.symbols.icons.simpleicons.icons.PixivSimpleicons
import kc.shared.generated.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val serviceIconsResourcePath = "files/kc-service-icons.json"

private val serviceIconsJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
  explicitNulls = false
}

@Serializable
data class ServiceIconCatalog(
    val version: Int = 1,
    val platforms: Map<String, List<String>> = emptyMap(),
    val services: Map<String, ServiceIconDefinition> = emptyMap(),
) {
  fun servicesForPlatform(platformKey: String): List<String> =
      platforms[platformKey.lowercase()] ?: emptyList()
}

@Serializable
data class ServiceIconDefinition(
    val label: String = "",
    val icon: String? = null,
)

object ServiceIconCatalogRepository {
  private val loadMutex = Mutex()
  private val mutableCatalog = MutableStateFlow<ServiceIconCatalog?>(null)

  @Volatile private var preparedCatalog: ServiceIconCatalog? = null

  val catalog: StateFlow<ServiceIconCatalog?> = mutableCatalog.asStateFlow()

  suspend fun ensureLoaded() {
    if (preparedCatalog != null) return

    loadMutex.withLock {
      if (preparedCatalog != null) return

      val loaded =
          runCatching {
                serviceIconsJson.decodeFromString<ServiceIconCatalog>(
                    Res.readBytes(serviceIconsResourcePath).decodeToString()
                )
              }
              .getOrElse { ServiceIconCatalog() }
      preparedCatalog = loaded
      mutableCatalog.value = loaded
    }
  }
}

private val pawchiveServices = listOf("patreon", "fanbox")

fun pawchiveServices(): List<String> {
  val catalog = ServiceIconCatalogRepository.catalog.value
  val fromJson = catalog?.servicesForPlatform("pawchive")
  return if (!fromJson.isNullOrEmpty()) fromJson else pawchiveServices
}

@Composable
fun rememberServiceIconDefinition(service: String): ServiceIconDefinition {
  LaunchedEffect(Unit) { ServiceIconCatalogRepository.ensureLoaded() }
  val catalog by ServiceIconCatalogRepository.catalog.collectAsState()
  val normalized = service.lowercase()
  return catalog?.services?.get(normalized)
      ?: ServiceIconDefinition(label = service.replaceFirstChar { it.uppercase() })
}

fun serviceIconVector(icon: String?): ImageVector? =
    when (icon) {
      "mdi:patreon" -> MdiIcons.PatreonMdi
      "simple-icons:pixiv" -> SimpleIcons.PixivSimpleicons
      "fa6-brands:discord" -> FontAwesomeBrandIcons.DiscordFontawesomebrands
      "service:fantia" -> LocalServiceIcons.Fantia
      "simple-icons:afdian" -> SimpleIcons.AfdianSimpleicons
      "simple-icons:boosty" -> SimpleIcons.BoostySimpleicons
      "simple-icons:gumroad" -> SimpleIcons.GumroadSimpleicons
      "lucide:section" -> LucideIcons.SectionLucide
      "service:dlsite" -> LocalServiceIcons.Dlsite
      "simple-icons:onlyfans" -> SimpleIcons.OnlyfansSimpleicons
      "arcticons:fansly" -> ArcticonsIcons.FanslyArcticons
      "material-symbols:lock-open-outline-rounded" -> MaterialSymbolsIcons.LockOpenW400Outlined
      else -> null
    }
