package ddd.kc.di

import ddd.kc.data.local.AppDatabase
import ddd.kc.data.local.AppDatabaseBuilderFactory
import org.koin.dsl.module

fun databaseModule() = module {
  single<AppDatabase> { get<AppDatabaseBuilderFactory>().create().build() }
}
