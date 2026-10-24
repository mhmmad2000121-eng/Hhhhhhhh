import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Automatically materialize required build files (debug.keystore, .env, google-services.json,
// firebase_applet_config.xml) in CI environments such as GitHub Actions when absent.
fun ensureCiBuildFiles() {
  val ciDebugKeystore = rootProject.file("debug.keystore")
  if (!ciDebugKeystore.exists()) {
    val ciDebugKeystoreBase64 = rootProject.file("debug.keystore.base64")
    val base64Keystore = if (ciDebugKeystoreBase64.exists()) {
      ciDebugKeystoreBase64.readText()
    } else {
      "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"
    }
    ciDebugKeystore.writeBytes(Base64.getDecoder().decode(base64Keystore.replace("\\s+".toRegex(), "")))
  }

  val ciEnvExampleFile = rootProject.file(".env.example")
  if (!ciEnvExampleFile.exists()) {
    ciEnvExampleFile.writeText("# GEMINI_API_KEY=MY_GEMINI_API_KEY\n")
  }
  val ciEnvFile = rootProject.file(".env")
  if (!ciEnvFile.exists()) {
    ciEnvExampleFile.copyTo(ciEnvFile, overwrite = false)
  }

  val ciGoogleServices = project.file("google-services.json")
  if (!ciGoogleServices.exists()) {
    ciGoogleServices.writeText(
      """
      {
        "project_info": {
          "project_number": "536553984394",
          "project_id": "gen-lang-client-0488603510",
          "storage_bucket": "gen-lang-client-0488603510.firebasestorage.app"
        },
        "client": [
          {
            "client_info": {
              "mobilesdk_app_id": "1:536553984394:android:1428f9e8b51b3067a23873",
              "android_client_info": {
                "package_name": "com.example"
              }
            },
            "oauth_client": [
              {
                "client_id": "536553984394-r4786fsc2d805kuilukgk7n957io45ge.apps.googleusercontent.com",
                "client_type": 1,
                "android_info": {
                  "package_name": "com.example",
                  "certificate_hash": "dc22c4e44c4af4cab26225a886e51c6f431f45c4"
                }
              },
              {
                "client_id": "536553984394-i0utge56picluqdb5gnv79j6mghjcv7i.apps.googleusercontent.com",
                "client_type": 3
              }
            ],
            "api_key": [
              {
                "current_key": "AIzaSyBMYD7_hdo0zPdWZGj90MrDsnPhibLfP1o"
              }
            ],
            "services": {
              "appinvite_service": {
                "other_platform_oauth_client": [
                  {
                    "client_id": "536553984394-i0utge56picluqdb5gnv79j6mghjcv7i.apps.googleusercontent.com",
                    "client_type": 3
                  }
                ]
              }
            }
          }
        ],
        "configuration_version": "1"
      }
      """.trimIndent()
    )
  }

  val ciFirebaseConfigXml = project.file("src/main/res/values/firebase_applet_config.xml")
  if (!ciFirebaseConfigXml.exists()) {
    ciFirebaseConfigXml.parentFile?.mkdirs()
    ciFirebaseConfigXml.writeText(
      """
      <?xml version="1.0" encoding="utf-8" standalone="no"?>
      <resources>
          <string name="firestore_database_id" translatable="false">ai-studio-android-stealthv-cfbbe454-fb5e-4983-90a3-ef9cb70410b1</string>
      </resources>
      """.trimIndent()
    )
  }

  // Remove any leftover files from previous commits in the target GitHub repository
  val validSourcePaths = setOf(
    "MainActivity.kt",
    "auth/AuthManager.kt",
    "data/local/LocalVaultStorage.kt",
    "data/model/WorkspaceModels.kt",
    "data/repository/FirestoreErrorHandler.kt",
    "data/repository/WorkspaceRepository.kt",
    "ui/screens/AuthScreen.kt",
    "ui/screens/WorkspaceScreen.kt",
    "ui/theme/Color.kt",
    "ui/theme/Theme.kt",
    "ui/theme/Type.kt",
    "ui/viewmodel/WorkspaceViewModel.kt"
  )
  val sourceRoot = project.file("src/main/java/com/example")
  if (sourceRoot.exists()) {
    sourceRoot.walkTopDown().filter { it.isFile }.forEach { file ->
      val relPath = file.relativeTo(sourceRoot).invariantSeparatorsPath
      if (relPath !in validSourcePaths) {
        file.delete()
      }
    }
  }
}

ensureCiBuildFiles()

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Keep all template dependencies active to avoid unresolved symbol errors on any synced files.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
