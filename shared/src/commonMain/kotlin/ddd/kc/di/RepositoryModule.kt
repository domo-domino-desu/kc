package ddd.kc.di

import ddd.kc.data.repository.ActivityHistoryRepository
import ddd.kc.data.repository.CreatorRepository
import ddd.kc.data.repository.DiscordRepository
import ddd.kc.data.repository.PostRepository
import ddd.kc.data.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun repositoryModule() = module {
  single {
    Json {
      ignoreUnknownKeys = true
      isLenient = true
      coerceInputValues = true
    }
  }
  single { PostRepository(get(), get(), get(), Dispatchers.IO) }
  single { CreatorRepository(get(), get(), get(), Dispatchers.IO) }
  single { TagRepository(get(), get(), get(), Dispatchers.IO) }
  single { DiscordRepository(get(), Dispatchers.IO) }
  single { ActivityHistoryRepository(get(), get(), Dispatchers.IO) }
}
