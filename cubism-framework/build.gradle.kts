plugins { alias(libs.plugins.android.library) }

abstract class FrameworkSources : Sync() {
    @get:OutputDirectory abstract val generatedDirectory: DirectoryProperty
    @get:InputFile abstract val lazyShaderPatch: RegularFileProperty

    fun patchShaders() {
        val shader = generatedDirectory.file("com/live2d/sdk/cubism/framework/rendering/android/CubismShaderAndroid.java").get().asFile
        val source = shader.readText()
        val start = source.indexOf("    private void generateShaders() {")
        val end = source.indexOf("    private int loadShaderProgramFromFile(", start)
        check(start >= 0 && end > start) { "R5 shader initialization changed; review lazy shader patch" }
        val lookup = "CubismShaderSet shaderSet = shaderSets.get("
        check(source.split(lookup).size - 1 == 4) { "R5 shader lookups changed; review lazy shader patch" }
        shader.writeText(source.substring(0, start).replace(lookup, "CubismShaderSet shaderSet = getShaderSet(") +
            lazyShaderPatch.get().asFile.readText() + source.substring(end))
    }
}

// R5 caches GL programs and offscreen targets globally. Wallpaper preview and
// home engines use separate GL threads/contexts, so caches must follow the owner.
val frameworkSources by tasks.registering(FrameworkSources::class) {
    from("upstream/src/main/java")
    generatedDirectory.set(layout.buildDirectory.dir("generated/framework"))
    lazyShaderPatch.set(layout.projectDirectory.file("patches/LazyShaders.java.inc"))
    into(generatedDirectory)
    doLast { patchShaders() }
    filesMatching("**/CubismShaderAndroid.java") {
        filter { line: String ->
            line.replace("private static CubismShaderAndroid s_instance;",
                "private static final ThreadLocal<CubismShaderAndroid> s_instance = new ThreadLocal<>();")
                .replace("s_instance == null", "s_instance.get() == null")
                .replace("s_instance != null", "s_instance.get() != null")
                .replace("s_instance = new CubismShaderAndroid();", "s_instance.set(new CubismShaderAndroid());")
                .replace("s_instance.generateShaders()", "s_instance.get().generateShaders()")
                .replace("s_instance.releaseShaderProgram()", "s_instance.get().releaseShaderProgram()")
                .replace("return s_instance;", "return s_instance.get();")
                .replace("s_instance = null;", "s_instance.remove();")
        }
    }
    filesMatching("**/CubismOffscreenManagerAndroid.java") {
        filter { line: String ->
            line.replace("private static CubismOffscreenManagerAndroid s_instance;",
                "private static final ThreadLocal<CubismOffscreenManagerAndroid> s_instance = new ThreadLocal<>();")
                .replace("s_instance == null", "s_instance.get() == null")
                .replace("s_instance != null", "s_instance.get() != null")
                .replace("s_instance = new CubismOffscreenManagerAndroid();", "s_instance.set(new CubismOffscreenManagerAndroid());")
                .replace("s_instance.dispose()", "s_instance.get().dispose()")
                .replace("return s_instance;", "return s_instance.get();")
                .replace("s_instance = null;", "s_instance.remove();")
        }
    }
}
android {
    namespace = "com.live2d.sdk.cubism.framework"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    sourceSets.getByName("main").assets.srcDir("upstream/src/main/assets")
}
androidComponents.onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(frameworkSources, FrameworkSources::generatedDirectory)
}
dependencies { api(project(":purism-core")) }
