plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.storyteller_f.ping.purism"
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64", "x86") }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
        consumerProguardFiles("consumer-rules.pro")
    }
    externalNativeBuild { cmake { path = file("CMakeLists.txt"); version = "3.22.1" } }
}

// Java compatibility API only; all native code is built from Purism Core sources.
dependencies {
    api(files("libs/cubism-java-api.jar"))
}
