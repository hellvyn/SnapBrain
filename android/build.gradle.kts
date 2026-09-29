buildscript {
    repositories { google() }
    dependencies {
        // Pinned in Step 7 from the version CI resolves.
        classpath("com.google.gms:google-services:latest.release")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
