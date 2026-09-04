package ddd.kc.di

import ddd.kc.ui.pages.creator.CreatorScreenModel
import ddd.kc.ui.pages.creators.CreatorSearchScreenModel
import ddd.kc.ui.pages.dm.DmScreenModel
import ddd.kc.ui.pages.dm.DmSearchScreenModel
import ddd.kc.ui.pages.more.FavoritesScreenModel
import ddd.kc.ui.pages.more.MoreScreenModel
import ddd.kc.ui.pages.post.PostScreenModel
import ddd.kc.ui.pages.recent.PopularPostsScreenModel
import ddd.kc.ui.pages.recent.RecentDMsScreenModel
import ddd.kc.ui.pages.settings.SettingsScreenModel
import ddd.kc.ui.pages.tagposts.TagPostsScreenModel
import ddd.kc.ui.pages.works.PostSearchScreenModel
import ddd.kc.ui.pages.works.WorksScreenModel
import org.koin.dsl.module

fun uiModule() = module {
  // Tab-level models: singletons so state survives tab switches and detail screen pushes
  single { RecentDMsScreenModel(get()) }
  single { PopularPostsScreenModel(get(), get()) }
  single { CreatorSearchScreenModel(get(), get()) }
  single { PostSearchScreenModel(get(), get(), get()) }
  single { DmScreenModel() }
  single { DmSearchScreenModel(get(), get()) }
  single { WorksScreenModel() }
  single { MoreScreenModel(get(), get(), get(), get()) }

  // Detail models: factory (parameterised per navigation target)
  factory { (tag: String) -> TagPostsScreenModel(get(), tag) }
  factory { params ->
    PostScreenModel(
        get(),
        get(),
        get(),
        params[0],
        params[1],
        params[2],
        params[3],
        params[4],
    )
  }
  factory { (creators: List<ddd.kc.data.model.Creator>, startIndex: Int) ->
    CreatorScreenModel(get(), get(), get(), creators, startIndex)
  }
  factory { FavoritesScreenModel(get(), get()) }
  factory { SettingsScreenModel(get()) }
}
