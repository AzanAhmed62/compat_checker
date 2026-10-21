import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.isFile) file.inputStream().use(::load)
}

fun configuredValue(name: String): String =
  (localProperties.getProperty(name) ?: System.getenv(name) ?: "").trim()

fun buildConfigString(value: String): String =
  "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
  namespace = "com.compatchecker.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.compatchecker.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables.useSupportLibrary = true

    buildConfigField("String", "SUPABASE_URL", buildConfigString(configuredValue("SUPABASE_URL")))
    buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigString(configuredValue("SUPABASE_ANON_KEY")))
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  signingConfigs {
    val store = configuredValue("RELEASE_STORE_FILE")
    if (store.isNotEmpty()) {
      create("release") {
        storeFile = file(store)
        storePassword = configuredValue("RELEASE_STORE_PASSWORD")
        keyAlias = configuredValue("RELEASE_KEY_ALIAS")
        keyPassword = configuredValue("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      isMinifyEnabled = false
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.findByName("release")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }

  testOptions {
    unitTests.isIncludeAndroidResources = false
  }

  packaging {
    resources {
      excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/INDEX.LIST",
        "/META-INF/DEPENDENCIES",
      )
    }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.generateKotlin", "true")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)

  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.auth)
  implementation(libs.ktor.client.okhttp)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Fail fast on a release build without a configured keystore. Debug and local
// verification builds do not require signing material.
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
  doFirst {
    val required = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
    val missing = required.filter { configuredValue(it).isEmpty() }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "Release signing is not configured. Missing: ${missing.joinToString()}. " +
          "See docs/PLAY_STORE_RELEASE.md; never commit keystores or passwords."
      )
    }
  }
}
