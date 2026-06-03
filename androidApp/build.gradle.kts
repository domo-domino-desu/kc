import org.gradle.api.Project
import org.gradle.api.provider.ProviderFactory

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "ddd.kc.android"
  compileSdk = 36
  buildToolsVersion = "36.0.0"

  val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
  val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").map(String::toInt).get()
  val releaseSigning = providers.resolveAndroidReleaseSigning(project)

  defaultConfig {
    applicationId = "ddd.kc"
    minSdk = 29
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = file(releaseSigning.storeFilePath)
      storePassword = releaseSigning.storePassword
      keyAlias = releaseSigning.keyAlias
      keyPassword = releaseSigning.keyPassword
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":shared"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.datastore.preferences.core)
  implementation(libs.ksafe)
  implementation(libs.koin.android)
  implementation(libs.koin.core)
  implementation(libs.kermit)
  implementation(libs.room.runtime)
}

private data class AndroidReleaseSigning(
    val storeFilePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

private fun ProviderFactory.resolveAndroidReleaseSigning(project: Project): AndroidReleaseSigning =
    AndroidReleaseSigning(
        storeFilePath =
            gradleProperty("ANDROID_SIGNING_STORE_FILE")
                .orElse(project.rootProject.file("dummy.keystore").absolutePath)
                .get(),
        storePassword = gradleProperty("ANDROID_SIGNING_STORE_PASSWORD").orElse("123456").get(),
        keyAlias = gradleProperty("ANDROID_SIGNING_KEY_ALIAS").orElse("dummy").get(),
        keyPassword = gradleProperty("ANDROID_SIGNING_KEY_PASSWORD").orElse("123456").get(),
    )
