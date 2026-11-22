// Versi plugin dipin di sini. AGP 9 memakai Kotlin bawaan, jadi plugin kotlin-android tidak dipakai.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.14" apply false
}
