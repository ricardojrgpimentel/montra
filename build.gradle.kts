// Montra — a store with no backend.
//
// The catalogue lives in a git repository as JSON. This app downloads that one
// file, verifies its signature against a public key baked into the APK, and then
// verifies every APK it installs by SHA-256 and signing certificate. There is no
// server to trust and nothing to keep running.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
