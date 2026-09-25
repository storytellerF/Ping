# Android releases

Push a tag to build the release APK and attach it to the matching GitHub Release.
Configure `SIGNING_KEY` (Base64 keystore), `ALIAS`, `STORE_PASSWORD`, and `KEY_PASSWORD`.
The build job restores the keystore and saves the APK as an Actions artifact. The independent
release job sets `GH_REPO`, creates the release only if it is missing, and uploads the APK
with replacement of same-named assets on reruns. Main-branch builds upload Actions artifacts only.
