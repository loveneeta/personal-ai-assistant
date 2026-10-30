plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  id("com.google.devtools.ksp")
}

android {
  namespace = "com.personalai.assistant"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.personalai.assistant"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val roomVersion = "2.8.5"
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("androidx.room:room-testing:$roomVersion")
  testImplementation("org.robolectric:robolectric:4.14.1")
  testImplementation("androidx.test:core:1.6.1")
  testImplementation("org.json:json:20231013")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Room Database
  implementation("androidx.room:room-runtime:$roomVersion")
  implementation("androidx.room:room-ktx:$roomVersion")
  ksp("androidx.room:room-compiler:$roomVersion")

  // Material Icons Extended
  implementation("androidx.compose.material:material-icons-extended")

  // Lifecycle Service for Foreground Service
  implementation("androidx.lifecycle:lifecycle-service:2.8.7")

  // WorkManager for Scheduled Daily Summaries
  implementation("androidx.work:work-runtime-ktx:2.10.0")

  // On-Device Offline Speech-to-Text (Vosk Android Engine)
  implementation("com.alphacephei:vosk-android:0.3.75@aar")
  implementation("net.java.dev.jna:jna:5.14.0@aar")
}
