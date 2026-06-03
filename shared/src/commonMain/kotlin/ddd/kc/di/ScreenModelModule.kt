package ddd.kc.di

import ddd.kc.data.model.DiscordChannel
import ddd.kc.ui.pages.creator.CreatorScreenModel
import ddd.kc.ui.pages.creators.CreatorSearchScreenModel
import ddd.kc.ui.pages.discord.DiscordChannelScreenModel
import ddd.kc.ui.pages.dm.DmSearchScreenModel
import ddd.kc.ui.pages.more.FavoritesScreenModel
import ddd.kc.ui.pages.more.LoginScreenModel
import ddd.kc.ui.pages.more.MoreScreenModel
import ddd.kc.ui.pages.post.PostScreenModel
import ddd.kc.ui.pages.recent.PopularPostsScreenModel
import ddd.kc.ui.pages.recent.RecentDMsScreenModel
import ddd.kc.ui.pages.tagposts.TagPostsScreenModel
import ddd.kc.ui.pages.tags.TagsScreenModel
import ddd.kc.ui.pages.works.PostSearchScreenModel
import org.koin.dsl.module

fun screenModelModule() = module {
  // Tab-level models: singletons so state survives tab switches and detail screen pushes
  single { RecentDMsScreenModel(get()) }
  single { PopularPostsScreenModel(get()) }
  single { CreatorSearchScreenModel(get()) }
  single { PostSearchScreenModel(get()) }
  single { DmSearchScreenModel(get()) }
  single { TagsScreenModel(get()) }
  single { MoreScreenModel(get(), get(), get()) }

  // Detail models: factory (parameterised per navigation target)
  factory { (tag: String) -> TagPostsScreenModel(get(), tag) }
  factory {
      (platform: ddd.kc.data.model.Platform, posts: List<ddd.kc.data.model.Post>, startIndex: Int)
    ->
    PostScreenModel(get(), get(), get(), platform, posts, startIndex)
  }
  factory {
      (
          platform: ddd.kc.data.model.Platform,
          creators: List<ddd.kc.data.model.Creator>,
          startIndex: Int) ->
    CreatorScreenModel(get(), get(), get(), get(), platform, creators, startIndex)
  }
  factory { (platform: ddd.kc.data.model.Platform, channels: List<DiscordChannel>, startIndex: Int)
    ->
    DiscordChannelScreenModel(get(), platform, channels, startIndex)
  }
  factory { LoginScreenModel(get()) }
  factory { FavoritesScreenModel(get(), get()) }
}
