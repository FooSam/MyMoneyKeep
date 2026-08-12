import groovy.json.JsonSlurper
import java.io.File

val versionJsonFile = file("${rootDir}/version.json")
val versionMap: Map<String, Any> = if (versionJsonFile.exists()) {
  @Suppress("UNCHECKED_CAST")
  (JsonSlurper().parseText(versionJsonFile.readText()) as? Map<String, Any>) ?: emptyMap()
} else {
  emptyMap()
}

val appVersion = (versionMap["APP_Version"] as? String) ?: "1.00.00.01"
val appProductName = (versionMap["APP_ProductName"] as? String) ?: "MyMoneyKeep"

val parsedVersionCode: Int = try {
  val parts = appVersion.split(".").mapNotNull { it.toIntOrNull() }
  if (parts.size == 4) {
    parts[0] * 1000000 + parts[1] * 10000 + parts[2] * 100 + parts[3]
  } else if (parts.isNotEmpty()) {
    parts.reduce { acc, i -> acc * 100 + i }
  } else {
    1
  }
} catch (e: Exception) {
  1
}

plugins {
  alias(libs.plugins.android.application)
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  // Firebase Google Services plugin 已移除（無 google-services.json，不使用 Firebase）
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.mymoneykeep.app"
    minSdk = 24
    targetSdk = 35
    versionCode = parsedVersionCode
    versionName = appVersion

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }

  signingConfigs {
    val candidateKeystores = listOfNotNull(
      System.getenv("KEYSTORE_PATH")?.let { file(it) },
      file("${rootDir}/../../AppKeys/upload-key.jks"),
      file("D:/Work/Sam/Project/AppKeys/upload-key.jks"),
      file("${rootDir}/../AppKeys/upload-key.jks"),
      file("${rootDir}/my-upload-key.jks")
    )
    val keystoreFile = candidateKeystores.firstOrNull { it.exists() }
      ?: error("FATAL: Release signing keystore (upload-key.jks) not found! Searched: $candidateKeystores")

    create("release") {
      storeFile = keystoreFile
      storePassword = System.getenv("STORE_PASSWORD") ?: "\$Sam13661366"
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "\$Sam13661366"
    }

    create("debugConfig") {
      val debugStore = file("${rootDir}/debug.keystore")
      if (debugStore.exists()) {
        storeFile = debugStore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

      // 嚴格強制使用 release 簽署金鑰，絕不允許靜默退回 debug 簽名
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val debugSigning = signingConfigs.findByName("debugConfig")
      if (debugSigning != null && debugSigning.storeFile?.exists() == true) {
        signingConfig = debugSigning
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }

  packaging {
    resources {
      excludes += "META-INF/DEPENDENCIES"
    }
  }

}

val copyReleaseApk = tasks.register("copyReleaseApk") {
  dependsOn("packageRelease")
  doLast {
    val releaseDir = file("${rootDir}/../Release")
    releaseDir.mkdirs()
    val apkFiles = layout.buildDirectory.asFileTree.matching {
      include("**/*.apk")
      exclude("**/apk_for_local_test/**")
    }.files

    println(">> [Copy Release APK] Found candidate APK files: " + apkFiles)
    val releaseApk = apkFiles.firstOrNull { it.name.contains("release") || it.name.contains(appProductName) }
      ?: apkFiles.firstOrNull()

    if (releaseApk != null) {
      val targetApk = File(releaseDir, "${appProductName}-v${appVersion}-release.apk")
      releaseApk.copyTo(targetApk, overwrite = true)
      println(">> [Copy Release APK] Successfully copied ${releaseApk.name} -> ${targetApk.absolutePath} (${targetApk.length()} bytes)")
    } else {
      println(">> [Copy Release APK] WARNING: No APK file found in build directory!")
    }
  }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
  finalizedBy(copyReleaseApk)
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Firebase BOM 已移除（不使用 Firebase，避免啟動閃退）
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // Firebase AI (Gemini SDK) 已移除：改用 OkHttp REST API 直接呼叫，不需 Firebase
  // implementation(libs.firebase.ai)
  // Firestore 未啟用：
  // implementation(libs.firebase.firestore)

  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)
  implementation(libs.google.api.client.android)
  implementation(libs.google.api.services.drive)
  implementation(libs.google.api.services.sheets)
  // Firebase App Check 已移除：無 google-services.json，執行時初始化崩潰
  // implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
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
