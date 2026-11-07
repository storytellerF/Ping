import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs.kotlin)
//    alias(libs.plugins.licensee)
}
android {
    namespace = "com.storyteller_f.ping"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.storyteller_f.ping"
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64", "x86") }
        versionCode = 1
        versionName = "1.0"
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        val signPath = providers.environmentVariable("storyteller_f_sign_path").orNull
        val signAlias = providers.environmentVariable("storyteller_f_sign_alias").orNull
        val signStorePassword = providers.environmentVariable("storyteller_f_sign_store_password").orNull
        val signKeyPassword = providers.environmentVariable("storyteller_f_sign_key_password").orNull
        val signStorePath = signPath?.let(::File)
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
//            resValue(
//                "string",
//                "leak_canary_display_activity_label",
//                defaultConfig.applicationId?.substringAfterLast(".") ?: "Leaks"
//            )
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
    providers.gradleProperty("live2dTestAssets").orNull?.let {
        sourceSets.getByName("androidTest").assets.srcDir(it)
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

    implementation(project(":cubism-framework"))
    implementation(project(":purism-core"))
//    implementation(libs.startup)
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
