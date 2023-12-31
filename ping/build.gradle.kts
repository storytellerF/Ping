import com.storyteller_f.version_manager.baseApp
import com.storyteller_f.version_manager.constraintCommonUIListVersion
import com.storyteller_f.version_manager.implModule
import com.storyteller_f.version_manager.setupDataBinding
import com.storyteller_f.version_manager.setupGeneric
import com.storyteller_f.version_manager.setupPreviewFeature

val versionManager: String by project

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
//    id("app.cash.licensee")
    id("com.storyteller_f.sml")
    id("com.storyteller_f.version_manager")
    id("kotlin-kapt")
}
android {
    defaultConfig {
        applicationId = "com.storyteller_f.ping"
    }
    namespace = "com.storyteller_f.ping"
}

dependencies {
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.squareup.okio:okio:3.5.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implModule(":file-system-ktx")
    implementation("com.google.android.filament:filament-android:1.45.0")
    implementation("com.google.android.filament:filament-utils-android:1.45.0")
    implementation("com.google.android.filament:gltfio-android:1.45.0")

    implementation(project(":CubismJavaFramework:framework"))
    implementation(fileTree("../Core/android"))
}
constraintCommonUIListVersion(versionManager)
baseApp()
setupGeneric()
setupDataBinding()
setupPreviewFeature()

//licensee {
//    allow("Apache-2.0")
//    allow("MIT")
//    allow("ISC")
//}

sml {

}
