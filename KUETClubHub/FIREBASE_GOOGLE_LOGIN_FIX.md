# Fixing "package certificate hash" on Google sign-in

The error means Firebase does not know the key your build is signed with.
`app/google-services.json` previously had `"oauth_client": []`; the supplied google-services.json (with the registered SHA-1) is now in place.

1. Run the app and tap **Continue with Google**. A dialog now shows this build's SHA-1 and SHA-256 with copy buttons.
   (Or run `./gradlew signingReport` and use the `debug` variant values.)
2. Firebase console -> Project settings -> Your apps -> `com.kuet.clubhub` -> **Add fingerprint**. Add SHA-1 and SHA-256.
3. Firebase console -> Authentication -> Sign-in method -> make sure **Google** is enabled.
4. Download the new `google-services.json` and replace `app/google-services.json`.
5. Sync Gradle, uninstall the old app from the phone, run again.

Every machine has its own debug keystore, so each teammate/PC must add its own fingerprints.
A release build needs the release keystore fingerprints added the same way.
