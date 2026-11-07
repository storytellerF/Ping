# Cubism Java Framework

Source: https://github.com/Live2D/CubismJavaFramework

- Release: `5-r.5`
- Commit: `c2d420012d004b8e61d4c589bd5c34513122f0ea`
- Included: `framework/src/main/java`, `framework/src/main/assets`, and the upstream license.
- Local source modifications: none. Copyright headers and `LICENSE.md` are preserved.

These files are tracked directly in this repository; no Git submodule is required.
The upstream Gradle project, wrapper, CI and empty Android manifest are not needed
by the `cubism-framework` library and are omitted.

`../build.gradle.kts` generates compiled sources from this snapshot, applying
GL-thread cache isolation and `../patches/LazyShaders.java.inc`. Keep those changes
outside this directory. When updating upstream, replace this snapshot and update
the revision above, then check the generation guards, shaders and model rendering.
