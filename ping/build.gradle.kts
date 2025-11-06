import com.storyteller_f.jksify.getenv
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
    id("com.storyteller_f.jksify")
//    id("app.cash.licensee")
}
android {
    namespace = "com.storyteller_f.ping"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.storyteller_f.ping"
        minSdk = 26
        versionCode = 1
        versionName = "1.0"
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        val signPath: String? = getenv("storyteller_f_sign_path")
        val signKey: String? = getenv("storyteller_f_sign_key")
        val signAlias: String? = getenv("storyteller_f_sign_alias")
        val signStorePassword: String? = getenv("storyteller_f_sign_store_password")
        val signKeyPassword: String? = getenv("storyteller_f_sign_key_password")
        val signStorePath = when {
            signPath != null -> File(signPath)
            signKey != null -> layout.buildDirectory.file("signing/signing_key.jks").get().asFile
            else -> null
        }
        if (signStorePath != null && signAlias != null && signStorePassword != null && signKeyPassword != null) {
            create("release") {
                keyAlias = signAlias
                keyPassword = signKeyPassword
                storeFile = signStorePath
                storePassword = signStorePassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue(
                "string",
                "leak_canary_display_activity_label",
                defaultConfig.applicationId?.substringAfterLast(".") ?: "Leaks"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null)
                signingConfig = releaseSignConfig
        }
    }
    val javaVersion = JavaVersion.VERSION_21
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        optIn.add("kotlin.RequiresOptIn")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.okio)
    implementation(libs.glide)

    implementation(libs.filament.android)
    implementation(libs.filament.utils.android)
    implementation(libs.gltfio.android)

    implementation(project(":CubismJavaFramework:framework"))
    implementation(fileTree("../Core/android"))
    implementation(libs.startup)
    implementation(libs.common.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    ksp(libs.androidx.databinding.compiler.common)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.datastore.preferences)

    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.leakcanary.android)

    ksp(libs.ext.func.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

//licensee {
//    allow("Apache-2.0")
//    allow("MIT")
//    allow("ISC")
//}

//sml {
//
//}
