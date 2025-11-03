import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
//    id("app.cash.licensee")
//    id("com.storyteller_f.sml")
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
        val path = System.getenv("storyteller_f_sign_path")
        val alias = System.getenv("storyteller_f_sign_alias")
        val storePassword = System.getenv("storyteller_f_sign_store_password")
        val keyPassword = System.getenv("storyteller_f_sign_key_password")
        if (path != null && alias != null && storePassword != null && keyPassword != null) {
            create("release") {
                keyAlias = alias
                this.keyPassword = keyPassword
                storeFile = file(path)
                this.storePassword = storePassword
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
    val javaVersion = JavaVersion.VERSION_17
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
        jvmTarget = JvmTarget.JVM_17
        optIn.add("kotlin.RequiresOptIn")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okio)
    implementation(libs.glide)
    implementation(libs.filament.android)
    implementation(libs.filament.utils.android)
    implementation(libs.gltfio.android)

    implementation(project(":CubismJavaFramework:framework"))
    implementation(fileTree("../Core/android"))
    implementation(libs.startup)
    implementation(libs.common.ktx)
    implementation(libs.compat.ktx)
    implementation(libs.common.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)

    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.leakcanary.android)
    implementation(libs.androidx.multidex)
    ksp(libs.ext.func.compiler)
    implementation(libs.common.pr)
    ksp(libs.androidx.databinding.compiler.common)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

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
