# SnapBrain Android Core Implementation Plan (Rencana 2 dari 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android SnapBrain dengan loop inti yang lengkap. User share screenshot, app menjalankan OCR dan AI, hasilnya disimpan di database lokal, lalu bisa dilihat di Inbox, Search, dan Detail. Antrian offline ikut jalan. Semua di-build dan dites di GitHub Actions.

**Architecture:** Ada dua build Gradle di bawah `android/`.
- **`android/core`** adalah build Kotlin/JVM murni yang di-include ke app lewat `includeBuild`. Isinya semua logika yang bisa dites tanpa Android: parsing respons, normalisasi, pemetaan aksi, kebijakan status/retry, dan device id. Modul ini dikerjakan test-first dan bisa dites lokal.
- **`android/app`** adalah lapisan Android yang tipis: Room, ML Kit, Firebase callable, WorkManager, dan Compose. App hanya memanggil `core`.
- Container pengembang gak punya Android SDK. Karena itu gerbang compile untuk `app` adalah workflow CI `android.yml`. Workflow ini juga mengunggah APK debug.

**Tech Stack:** AGP 9.4.0 (built-in Kotlin), Gradle 9.6.1, Kotlin 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00 (Material 3), Activity 1.13.0, Room 2.8.5, WorkManager 2.12.0, Coil 3.6.3, kotlinx-serialization-json 1.11.0, kotlinx-coroutines 1.11.0, Firebase (Auth, Functions, App Check), dan ML Kit Text Recognition (bundled).

**Spec:** `docs/superpowers/specs/2026-09-29-snapbrain-v1-design.md`

**Di luar rencana ini (Rencana 3):** AdMob banner/rewarded, Play Billing, paywall, dan membuka kunci Task Planner. Di rencana ini, kuota habis hanya menampilkan pesan, dan task yang terkunci hanya ditampilkan sebagai placeholder terkunci.

## Global Constraints

- Root Gradle Android ada di `android/`. `android/core` adalah included build (`includeBuild("core")`). App bergantung ke `com.snapbrain:core`.
- `applicationId` dan `namespace` = `com.snapbrain.app`. `minSdk 26`, `compileSdk 36`, `targetSdk 36`. Java/Kotlin JVM target 17.
- Versi yang dipakai persis: AGP `9.4.0`, Gradle wrapper `9.6.1`, Kotlin `2.4.20` (plugin `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.serialization`), KSP `2.3.12`, Compose BOM `2026.09.00`, `androidx.activity:activity-compose:1.13.0`, Room `2.8.5`, `androidx.work:work-runtime-ktx:2.12.0`, `io.coil-kt.coil3:coil-compose:3.6.3`, `kotlinx-serialization-json:1.11.0`, `kotlinx-coroutines-play-services:1.11.0`, `junit:junit:4.13.2`.
- Firebase BOM, `com.google.mlkit:text-recognition` dan plugin `com.google.gms:google-services` di-resolve sebagai `latest.release` di CI Task 1, lalu **wajib di-pin** ke versi hasil resolve sebelum Task 1 di-commit final.
- AGP 9 memakai built-in Kotlin. **Jangan** pasang plugin `org.jetbrains.kotlin.android`. Room memakai KSP, bukan kapt.
- `google-services.json` tidak pernah di-commit (sudah ada di `.gitignore`). CI menulis file dummy dengan `package_name` `com.snapbrain.app`.
- Backend: region `asia-southeast2`, callable `extract` dengan payload `{ ocr_text, device_id, item_id }`. `device_id` = SHA-256 hex huruf kecil dari `ANDROID_ID`. `item_id` = UUID huruf kecil.
- `ocr_text` dipotong ke 20.000 karakter di klien. Teks dengan kurang dari 10 karakter tidak dikirim ke AI.
- Teks OCR tidak pernah di-log (`Log.*`) dan tidak dikirim ke tempat lain selain `extract`.
- Status item: `UNPROCESSED`, `DONE`, `QUOTA_BLOCKED`, `FAILED`. Batas retry otomatis 5 percobaan untuk error yang bisa diulang. Timeout sinkron di sheet share 6 detik.
- `open_url` hanya dibuka jika skemanya `http` atau `https`.
- Semua teks UI dalam Bahasa Indonesia. Material 3, warna primer teal (`#00897B` terang, `#4DB6AC` gelap), mendukung dark mode.
- Tanpa Hilt, tanpa Navigation library, tanpa ViewModel. DI memakai satu `AppContainer`, dan navigasi memakai state `openId` + `BackHandler`.
- Penyimpangan dari spec yang sengaja diambil, karena lebih sederhana dan tidak menurunkan fungsi:
  - Search memakai `LIKE` di `ocrText`/`title`, bukan FTS4. `LIKE` juga menemukan potongan nomor resi.
  - Test Room instrumented dihapus, karena CI tidak menjalankan emulator.

## Review Focus

1. **User menutup sheet share sebelum hasil keluar.** Item harus tetap tersimpan dan diproses oleh worker. Diuji lewat kebijakan di Task 3 (`statusAfter` untuk `Retryable` di bawah batas → `UNPROCESSED`), dan lewat checklist manual di Task 7.
2. **Server mengembalikan kategori atau `action_type` yang tidak dikenal, atau payload kosong** (spec §6 hanya daftar enum). App tidak boleh crash atau menampilkan tombol rusak. Diuji di Task 2 (`normalizes unknown category and action`) dan Task 3 (`returns null for empty payload`).
3. **Screenshot tanpa teks** (foto, meme). Tidak boleh memotong kuota, dan tetap tersimpan. Diuji di Task 3 (`needsAi false below 10 chars`).
4. **Payload kalender dengan format rusak dari LLM.** Tombol kalender tidak ditampilkan, app tidak crash. Diuji di Task 3 (`rejects malformed calendar payload`).
5. **Teks OCR sangat panjang** (screenshot artikel lebih dari 20k karakter). Tidak boleh jadi `invalid-argument` permanen. Diuji di Task 3 (`truncates to 20000 chars`).

## File Structure

```
.github/workflows/android.yml      CI: core tests, assembleDebug, upload APK
.github/workflows/backend.yml      (ubah trigger push → main saja)
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  gradle/wrapper/gradle-wrapper.properties + gradle-wrapper.jar, gradlew, gradlew.bat
  core/                            included build, Kotlin/JVM
    settings.gradle.kts, build.gradle.kts
    src/main/kotlin/com/snapbrain/core/Extract.kt     model respons + JSON + normalisasi + label kategori
    src/main/kotlin/com/snapbrain/core/Actions.kt     Action + actionOf + label
    src/main/kotlin/com/snapbrain/core/Policy.kt      ItemStatus, ExtractOutcome, statusAfter, needsAi, truncateForApi, deviceIdOf, canDeleteOriginal
    src/test/kotlin/com/snapbrain/core/*Test.kt
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/res/values/themes.xml, src/main/res/drawable/ic_launcher.xml
    src/main/kotlin/com/snapbrain/app/SnapBrainApp.kt, AppContainer.kt
    src/main/kotlin/com/snapbrain/app/data/ItemEntity.kt, ItemDao.kt, AppDatabase.kt, ImageStore.kt, ItemRepository.kt
    src/main/kotlin/com/snapbrain/app/process/OcrEngine.kt, ExtractClient.kt, ProcessWorker.kt
    src/main/kotlin/com/snapbrain/app/share/ShareActivity.kt
    src/main/kotlin/com/snapbrain/app/ui/Theme.kt, MainActivity.kt, InboxScreen.kt, DetailScreen.kt, Components.kt, Perform.kt
    src/debug/kotlin/com/snapbrain/app/AppCheckSetup.kt    debug provider
    src/release/kotlin/com/snapbrain/app/AppCheckSetup.kt  Play Integrity provider
docs/manual-test-android.md        checklist tes di HP
```

## Cara verifikasi CI (dipakai semua task `app`)

1. Commit, lalu `git push origin claude/wizardly-dijkstra-4m9ayw`.
2. Cari run workflow `android` untuk commit ini dengan tool GitHub MCP. Jika belum dimuat, muat dulu via ToolSearch `select:mcp__github__actions_list,mcp__github__get_job_logs`. Panggil `mcp__github__actions_list`: method `list_workflow_runs`, owner `hellvyn`, repo `SnapBrain`, `workflow_runs_filter: {branch: "claude/wizardly-dijkstra-4m9ayw"}`. Cocokkan `head_sha` dengan `git rev-parse HEAD`.
3. Tunggu sampai `status` = `completed`. Tunggu dengan `sleep 60` di antara pengecekan, maksimal 20 kali.
4. Jika `conclusion` = `failure`: ambil log dengan `mcp__github__get_job_logs` (`run_id`, `failed_only: true`, `return_content: true`, `tail_lines: 200`), perbaiki, lalu ulangi dari langkah 1.
5. Laporkan run id dan conclusion `success` di report.

---

### Task 1: CI Android + scaffold yang bisa di-build (Milestone 1)

**Files:**
- Create: `.github/workflows/android.yml`
- Modify: `.github/workflows/backend.yml` (trigger)
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, wrapper files
- Create: `android/core/settings.gradle.kts`, `android/core/build.gradle.kts`, `android/core/src/main/kotlin/com/snapbrain/core/Version.kt`, `android/core/src/test/kotlin/com/snapbrain/core/VersionTest.kt`
- Create: `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/values/themes.xml`, `android/app/src/main/res/drawable/ic_launcher.xml`, `android/app/src/main/kotlin/com/snapbrain/app/ui/MainActivity.kt`

**Interfaces:**
- Produces:
  - `com.snapbrain.core.CORE_VERSION: String`. Ini smoke test bahwa app bisa melihat `core`, dan dihapus di Task 2.
  - Workflow `android` yang menjalankan `:core:test`, `assembleDebug`, lalu mengunggah artifact `snapbrain-debug-apk`.

- [ ] **Step 1: Test core yang gagal**

`android/core/src/test/kotlin/com/snapbrain/core/VersionTest.kt`
```kotlin
package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionTest {
    @Test
    fun exposesVersion() {
        assertEquals("0.1.0", CORE_VERSION)
    }
}
```

`android/core/settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "core"
```

`android/core/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}

group = "com.snapbrain"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 2: Jalankan dan pastikan gagal**

Run: `cd android/core && gradle test --no-daemon`
Expected: FAIL, `Unresolved reference 'CORE_VERSION'`.

- [ ] **Step 3: Implementasi minimal**

`android/core/src/main/kotlin/com/snapbrain/core/Version.kt`
```kotlin
package com.snapbrain.core

const val CORE_VERSION = "0.1.0"
```

Run: `cd android/core && gradle test --no-daemon`
Expected: PASS.

- [ ] **Step 4: Scaffold app**

Wrapper. Generate di direktori kosong, lalu salin, supaya tidak memicu konfigurasi build Android:
```bash
tmp=$(mktemp -d) && (cd "$tmp" && gradle wrapper --gradle-version 9.6.1 --no-daemon) && cp -r "$tmp/gradle" "$tmp/gradlew" "$tmp/gradlew.bat" android/ && chmod +x android/gradlew
```

`android/settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SnapBrain"
include(":app")
includeBuild("core")
```

`android/build.gradle.kts`
```kotlin
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
```

`android/gradle.properties`
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`android/app/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.snapbrain.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.snapbrain.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("com.snapbrain:core")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")

    // Pinned in Step 7 from the versions CI resolves.
    implementation(platform("com.google.firebase:firebase-bom:latest.release"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.mlkit:text-recognition:latest.release")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
```

`android/app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="SnapBrain"
        android:supportsRtl="true"
        android:theme="@style/Theme.SnapBrain">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`android/app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.SnapBrain" parent="android:Theme.Material.Light.NoActionBar" />
    <style name="Theme.SnapBrain.Translucent" parent="android:Theme.Translucent.NoTitleBar" />
</resources>
```

`android/app/src/main/res/drawable/ic_launcher.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#00897B" android:pathData="M54,54m-50,0a50,50 0,1 1,100 0a50,50 0,1 1,-100 0" />
    <path android:fillColor="#FFFFFF" android:pathData="M34,38h40v8h-40z M34,52h28v8h-28z M34,66h36v8h-36z" />
</vector>
```

`android/app/src/main/kotlin/com/snapbrain/app/ui/MainActivity.kt`
```kotlin
package com.snapbrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.snapbrain.core.CORE_VERSION

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("SnapBrain $CORE_VERSION") }
    }
}
```

- [ ] **Step 5: Workflow CI**

`.github/workflows/android.yml`
```yaml
name: android
on:
  push:
    branches: [main]
    paths: ["android/**", ".github/workflows/android.yml"]
  pull_request:
    paths: ["android/**", ".github/workflows/android.yml"]
jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - name: Dummy google-services.json (real one stays off git)
        run: |
          cat > app/google-services.json <<'JSON'
          {
            "project_info": { "project_number": "000000000000", "project_id": "snapbrain-ci", "storage_bucket": "snapbrain-ci.appspot.com" },
            "client": [{
              "client_info": { "mobilesdk_app_id": "1:000000000000:android:0000000000000000", "android_client_info": { "package_name": "com.snapbrain.app" } },
              "oauth_client": [],
              "api_key": [{ "current_key": "AIzaSyDUMMY-DUMMY-DUMMY-DUMMY-DUMMY00" }],
              "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
            }],
            "configuration_version": "1"
          }
          JSON
      - run: ./gradlew --no-daemon :core:test assembleDebug
      - name: Print resolved dynamic versions
        run: ./gradlew --no-daemon :app:dependencies --configuration releaseRuntimeClasspath | grep -E "firebase-bom|text-recognition|google-services" || true
      - uses: actions/upload-artifact@v4
        with:
          name: snapbrain-debug-apk
          path: android/app/build/outputs/apk/debug/*.apk
```

Di `.github/workflows/backend.yml`, ubah blok `push` menjadi `push: { branches: [main], paths: [...] }` (path tetap sama). Tujuannya supaya PR tidak menjalankan dua run untuk satu commit.

- [ ] **Step 6: Push dan verifikasi CI** (lihat "Cara verifikasi CI"). Workflow ini terpicu oleh event `pull_request` karena PR #1 terbuka di branch ini.

Jika CI gagal di konfigurasi Gradle (misalnya bentrok versi Kotlin Gradle plugin antara `core` yang di-include dan built-in Kotlin milik AGP), pakai **fallback**: jadikan `core` subproject biasa. Caranya: di `android/settings.gradle.kts` ganti `includeBuild("core")` dengan `include(":core")`, hapus `android/core/settings.gradle.kts`, pindahkan versi plugin `kotlin("jvm")` dan `kotlin("plugin.serialization")` ke `plugins { ... apply false }` di `android/build.gradle.kts`, hapus versi dari `android/core/build.gradle.kts`, lalu ganti dependency app menjadi `implementation(project(":core"))`. Setelah fallback, `gradle test` lokal di `android/core` tidak lagi berjalan dan test core hanya jalan di CI. Catat fallback ini di report.

- [ ] **Step 7: Pin versi dinamis**

Ambil output step "Print resolved dynamic versions" dari log CI (`get_job_logs` dengan `job_id` job `build`, `return_content: true`). Ganti ketiga `latest.release` dengan versi yang tampil di log: `com.google.gms:google-services:X`, `firebase-bom:Y`, dan `text-recognition:Z`. Jika grep tidak menampilkan google-services (karena ini classpath buildscript, bukan dependency app), jalankan `./gradlew --no-daemon buildEnvironment | grep google-services` sebagai step sementara di workflow, baca hasilnya, lalu hapus lagi step tersebut. Push, lalu pastikan CI hijau.

- [ ] **Step 8: Commit**

```bash
git add .github android
git commit -m "build(android): scaffold app + core with CI (milestone 1)"
```

---

### Task 2: Model respons `extract` + normalisasi (core)

**Files:**
- Create: `android/core/src/main/kotlin/com/snapbrain/core/Extract.kt`
- Delete: `android/core/src/main/kotlin/com/snapbrain/core/Version.kt`, `android/core/src/test/kotlin/com/snapbrain/core/VersionTest.kt`
- Modify: `android/app/src/main/kotlin/com/snapbrain/app/ui/MainActivity.kt` (hapus referensi `CORE_VERSION`, tampilkan `Text("SnapBrain")`)
- Test: `android/core/src/test/kotlin/com/snapbrain/core/ExtractTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class TaskItem(val id: Int, val description: String, @SerialName("is_completed") val isCompleted: Boolean = false)`
  - `@Serializable data class ExtractData(category: String, title: String, extractedInfo: Map<String,String> = emptyMap() @SerialName("extracted_info"), actionType: String = "none" @SerialName("action_type"), actionPayload: String = "" @SerialName("action_payload"), tasks: List<TaskItem> = emptyList())`
  - `@Serializable data class Quota(val used: Int, val limit: Int)`
  - `@Serializable data class ExtractResponse(val data: ExtractData, @SerialName("tasks_total") val tasksTotal: Int = 0, val quota: Quota)`
  - `object ExtractJson { fun parse(json: String): ExtractResponse; fun encodeInfo(Map<String,String>): String; fun decodeInfo(String?): Map<String,String>; fun encodeTasks(List<TaskItem>): String; fun decodeTasks(String?): List<TaskItem> }`
  - `val CATEGORIES: List<String>` (urutan: task, finance, shopping, event, reference, unclassified)
  - `fun ExtractData.normalized(): ExtractData`
  - `fun categoryLabel(category: String?): String`

- [ ] **Step 1: Test yang gagal**

`android/core/src/test/kotlin/com/snapbrain/core/ExtractTest.kt`
```kotlin
package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractTest {
    private val sample = """
        {"data":{"category":"shopping","title":"Paket Shopee dikirim",
         "extracted_info":{"No. Resi":"JP123"},"action_type":"track_parcel",
         "action_payload":"JP123","tasks":[{"id":1,"description":"Cek paket","is_completed":false}]},
         "tasks_total":3,"quota":{"used":2,"limit":15},"extra_field":true}
    """.trimIndent()

    @Test
    fun parsesServerResponseAndIgnoresUnknownFields() {
        val r = ExtractJson.parse(sample)
        assertEquals("shopping", r.data.category)
        assertEquals(mapOf("No. Resi" to "JP123"), r.data.extractedInfo)
        assertEquals(TaskItem(1, "Cek paket", false), r.data.tasks.single())
        assertEquals(3, r.tasksTotal)
        assertEquals(Quota(2, 15), r.quota)
    }

    @Test
    fun normalizesUnknownCategoryAndAction() {
        val d = ExtractData(category = "gossip", title = "x", actionType = "teleport", actionPayload = "p").normalized()
        assertEquals("unclassified", d.category)
        assertEquals("none", d.actionType)
        assertEquals("", d.actionPayload)
    }

    @Test
    fun downgradesNonHttpOpenUrl() {
        val bad = ExtractData(category = "reference", title = "x", actionType = "open_url", actionPayload = "javascript:alert(1)").normalized()
        assertEquals("none", bad.actionType)
        val ok = ExtractData(category = "reference", title = "x", actionType = "open_url", actionPayload = "https://a.id").normalized()
        assertEquals("open_url", ok.actionType)
    }

    @Test
    fun dropsBlankTasks() {
        val d = ExtractData(category = "task", title = "x", tasks = listOf(TaskItem(1, " "), TaskItem(2, "Kerjakan"))).normalized()
        assertEquals(listOf("Kerjakan"), d.tasks.map { it.description })
    }

    @Test
    fun roundTripsInfoAndTasksForStorage() {
        val info = mapOf("Total" to "Rp 50.000")
        assertEquals(info, ExtractJson.decodeInfo(ExtractJson.encodeInfo(info)))
        val tasks = listOf(TaskItem(1, "a", true))
        assertEquals(tasks, ExtractJson.decodeTasks(ExtractJson.encodeTasks(tasks)))
        assertTrue(ExtractJson.decodeInfo(null).isEmpty())
        assertTrue(ExtractJson.decodeTasks("not json").isEmpty())
    }

    @Test
    fun labelsCategoriesInIndonesian() {
        assertEquals("🛒 Belanja", categoryLabel("shopping"))
        assertEquals("📄 Lainnya", categoryLabel(null))
    }
}
```

- [ ] **Step 2: Jalankan dan pastikan gagal**

Run: `cd android/core && gradle test --no-daemon` (atau lewat CI jika fallback Task 1 dipakai)
Expected: FAIL, `Unresolved reference 'ExtractJson'`.

- [ ] **Step 3: Implementasi**

`android/core/src/main/kotlin/com/snapbrain/core/Extract.kt`
```kotlin
package com.snapbrain.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class TaskItem(
    val id: Int,
    val description: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
)

@Serializable
data class ExtractData(
    val category: String,
    val title: String,
    @SerialName("extracted_info") val extractedInfo: Map<String, String> = emptyMap(),
    @SerialName("action_type") val actionType: String = "none",
    @SerialName("action_payload") val actionPayload: String = "",
    val tasks: List<TaskItem> = emptyList(),
)

@Serializable
data class Quota(val used: Int, val limit: Int)

@Serializable
data class ExtractResponse(
    val data: ExtractData,
    @SerialName("tasks_total") val tasksTotal: Int = 0,
    val quota: Quota,
)

val CATEGORIES = listOf("task", "finance", "shopping", "event", "reference", "unclassified")
private val ACTIONS = setOf("track_parcel", "add_calendar", "copy_text", "open_url", "none")
private val HTTP_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

object ExtractJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val infoSerializer = MapSerializer(String.serializer(), String.serializer())
    private val tasksSerializer = ListSerializer(TaskItem.serializer())

    fun parse(text: String): ExtractResponse = json.decodeFromString(ExtractResponse.serializer(), text)

    fun encodeInfo(info: Map<String, String>): String = json.encodeToString(infoSerializer, info)
    fun decodeInfo(text: String?): Map<String, String> =
        text?.let { runCatching { json.decodeFromString(infoSerializer, it) }.getOrNull() } ?: emptyMap()

    fun encodeTasks(tasks: List<TaskItem>): String = json.encodeToString(tasksSerializer, tasks)
    fun decodeTasks(text: String?): List<TaskItem> =
        text?.let { runCatching { json.decodeFromString(tasksSerializer, it) }.getOrNull() } ?: emptyList()
}

/** Defense in depth: the server already validates, but the app must never render a broken action. */
fun ExtractData.normalized(): ExtractData {
    var action = if (actionType in ACTIONS) actionType else "none"
    var payload = actionPayload.trim()
    if (action == "open_url" && !HTTP_URL.matches(payload)) action = "none"
    if (action == "none") payload = ""
    return copy(
        category = if (category in CATEGORIES) category else "unclassified",
        actionType = action,
        actionPayload = payload,
        tasks = tasks.filter { it.description.isNotBlank() },
    )
}

fun categoryLabel(category: String?): String = when (category) {
    "task" -> "✅ Tugas"
    "finance" -> "💰 Keuangan"
    "shopping" -> "🛒 Belanja"
    "event" -> "📅 Event"
    "reference" -> "📚 Referensi"
    else -> "📄 Lainnya"
}
```

- [ ] **Step 4: Jalankan dan pastikan lolos**

Run: `cd android/core && gradle test --no-daemon`
Expected: PASS (6 test).

- [ ] **Step 5: Commit, push, dan pastikan CI hijau** (lihat "Cara verifikasi CI")

```bash
git add android/core android/app/src/main/kotlin/com/snapbrain/app/ui/MainActivity.kt
git commit -m "feat(core): extract response model and normalization"
```

---

### Task 3: Aksi dinamis + kebijakan status + device id (core)

**Files:**
- Create: `android/core/src/main/kotlin/com/snapbrain/core/Actions.kt`, `android/core/src/main/kotlin/com/snapbrain/core/Policy.kt`
- Test: `android/core/src/test/kotlin/com/snapbrain/core/ActionsTest.kt`, `android/core/src/test/kotlin/com/snapbrain/core/PolicyTest.kt`

**Interfaces:**
- Consumes: `ExtractResponse` (Task 2)
- Produces:
  - `sealed interface Action { val label: String }` dengan `OpenUrl(url)`, `TrackParcel(resi, searchUrl)`, `AddCalendar(title, beginMillis: Long)`, `CopyText(text)`
  - `fun actionOf(type: String?, payload: String?, zone: ZoneId = ZoneId.of("Asia/Jakarta")): Action?`
  - `enum class ItemStatus { UNPROCESSED, DONE, QUOTA_BLOCKED, FAILED }`
  - `sealed interface ExtractOutcome { data class Success(val response: ExtractResponse); data object QuotaExhausted; data object Retryable; data object Invalid }`
  - `const val MAX_ATTEMPTS = 5`, `const val MIN_OCR_CHARS = 10`, `const val MAX_OCR_CHARS = 20_000`
  - `fun outcomeOfCode(code: String): ExtractOutcome` (menerima nama `FirebaseFunctionsException.Code`)
  - `fun statusAfter(outcome: ExtractOutcome, attempts: Int): ItemStatus`
  - `fun needsAi(ocrText: String): Boolean`, `fun truncateForApi(ocrText: String): String`
  - `fun deviceIdOf(androidId: String): String`
  - `fun canDeleteOriginal(sdkInt: Int, authority: String?): Boolean`

- [ ] **Step 1: Test yang gagal**

`android/core/src/test/kotlin/com/snapbrain/core/ActionsTest.kt`
```kotlin
package com.snapbrain.core

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionsTest {
    private val wib = ZoneId.of("Asia/Jakarta")

    @Test
    fun buildsParcelSearch() {
        val a = actionOf("track_parcel", "JP123", wib) as Action.TrackParcel
        assertEquals("JP123", a.resi)
        assertTrue(a.searchUrl.startsWith("https://www.google.com/search?q="))
        assertTrue(a.searchUrl.contains("JP123"))
        assertEquals("🚚 Lacak Paket", a.label)
    }

    @Test
    fun parsesCalendarPayloadInJakartaTime() {
        val a = actionOf("add_calendar", "2026-10-01T19:30|Rapat RT", wib) as Action.AddCalendar
        assertEquals("Rapat RT", a.title)
        assertEquals(1790857800000L, a.beginMillis) // 2026-10-01 19:30 WIB = 12:30 UTC
    }

    @Test
    fun rejectsMalformedCalendarPayload() {
        assertNull(actionOf("add_calendar", "besok malam|Rapat", wib))
        assertNull(actionOf("add_calendar", "2026-10-01T19:30", wib))
    }

    @Test
    fun opensOnlyHttpUrls() {
        assertEquals(Action.OpenUrl("https://a.id"), actionOf("open_url", "https://a.id", wib))
        assertNull(actionOf("open_url", "intent://x", wib))
    }

    @Test
    fun returnsNullForEmptyPayloadOrNone() {
        assertNull(actionOf("copy_text", "  ", wib))
        assertNull(actionOf("none", "x", wib))
        assertNull(actionOf(null, null, wib))
        assertEquals(Action.CopyText("1234567890"), actionOf("copy_text", "1234567890", wib))
    }
}
```

`android/core/src/test/kotlin/com/snapbrain/core/PolicyTest.kt`
```kotlin
package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyTest {
    private val ok = ExtractOutcome.Success(ExtractResponse(ExtractData("task", "x"), 0, Quota(1, 15)))

    @Test
    fun mapsFunctionsErrorCodes() {
        assertEquals(ExtractOutcome.QuotaExhausted, outcomeOfCode("RESOURCE_EXHAUSTED"))
        assertEquals(ExtractOutcome.Invalid, outcomeOfCode("INVALID_ARGUMENT"))
        assertEquals(ExtractOutcome.Retryable, outcomeOfCode("UNAVAILABLE"))
        assertEquals(ExtractOutcome.Retryable, outcomeOfCode("INTERNAL"))
    }

    @Test
    fun decidesStatusAfterAnAttempt() {
        assertEquals(ItemStatus.DONE, statusAfter(ok, 1))
        assertEquals(ItemStatus.QUOTA_BLOCKED, statusAfter(ExtractOutcome.QuotaExhausted, 1))
        assertEquals(ItemStatus.FAILED, statusAfter(ExtractOutcome.Invalid, 1))
        assertEquals(ItemStatus.UNPROCESSED, statusAfter(ExtractOutcome.Retryable, MAX_ATTEMPTS - 1))
        assertEquals(ItemStatus.FAILED, statusAfter(ExtractOutcome.Retryable, MAX_ATTEMPTS))
    }

    @Test
    fun needsAiFalseBelow10Chars() {
        assertFalse(needsAi("  hai  "))
        assertTrue(needsAi("Transfer Rp 50.000"))
    }

    @Test
    fun truncatesTo20000Chars() {
        assertEquals(20_000, truncateForApi("a".repeat(25_000)).length)
        assertEquals("abc", truncateForApi("  abc  "))
    }

    @Test
    fun hashesAndroidIdToLowercaseHex() {
        val id = deviceIdOf("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", id)
    }

    @Test
    fun deletesOriginalOnlyFromMediaStoreOnAndroid11Plus() {
        assertTrue(canDeleteOriginal(30, "media"))
        assertFalse(canDeleteOriginal(29, "media"))
        assertFalse(canDeleteOriginal(34, "com.whatsapp.provider.media"))
        assertFalse(canDeleteOriginal(34, null))
    }
}
```

- [ ] **Step 2: Jalankan dan pastikan gagal**

Run: `cd android/core && gradle test --no-daemon`
Expected: FAIL, `Unresolved reference 'actionOf'`.

- [ ] **Step 3: Implementasi**

`android/core/src/main/kotlin/com/snapbrain/core/Actions.kt`
```kotlin
package com.snapbrain.core

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

sealed interface Action {
    val label: String

    data class OpenUrl(val url: String) : Action {
        override val label get() = "🔗 Buka Link"
    }

    data class TrackParcel(val resi: String, val searchUrl: String) : Action {
        override val label get() = "🚚 Lacak Paket"
    }

    data class AddCalendar(val title: String, val beginMillis: Long) : Action {
        override val label get() = "📅 Tambah ke Kalender"
    }

    data class CopyText(val text: String) : Action {
        override val label get() = "📋 Salin"
    }
}

private val HTTP_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

/** Maps the stored action_type/action_payload to something the UI can run; null means "no button". */
fun actionOf(type: String?, payload: String?, zone: ZoneId = ZoneId.of("Asia/Jakarta")): Action? {
    val p = payload?.trim().orEmpty()
    if (p.isEmpty()) return null
    return when (type) {
        "open_url" -> if (HTTP_URL.matches(p)) Action.OpenUrl(p) else null
        "track_parcel" -> Action.TrackParcel(
            p,
            "https://www.google.com/search?q=" + URLEncoder.encode("cek resi $p", StandardCharsets.UTF_8),
        )
        "copy_text" -> Action.CopyText(p)
        "add_calendar" -> {
            val parts = p.split("|", limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return null
            try {
                val begin = LocalDateTime.parse(parts[0].trim()).atZone(zone).toInstant().toEpochMilli()
                Action.AddCalendar(parts[1].trim(), begin)
            } catch (e: DateTimeParseException) {
                null
            }
        }
        else -> null
    }
}
```

`android/core/src/main/kotlin/com/snapbrain/core/Policy.kt`
```kotlin
package com.snapbrain.core

import java.security.MessageDigest

enum class ItemStatus { UNPROCESSED, DONE, QUOTA_BLOCKED, FAILED }

sealed interface ExtractOutcome {
    data class Success(val response: ExtractResponse) : ExtractOutcome
    data object QuotaExhausted : ExtractOutcome
    data object Retryable : ExtractOutcome
    data object Invalid : ExtractOutcome
}

const val MAX_ATTEMPTS = 5
const val MIN_OCR_CHARS = 10
const val MAX_OCR_CHARS = 20_000

/** [code] is FirebaseFunctionsException.Code.name. */
fun outcomeOfCode(code: String): ExtractOutcome = when (code) {
    "RESOURCE_EXHAUSTED" -> ExtractOutcome.QuotaExhausted
    "INVALID_ARGUMENT" -> ExtractOutcome.Invalid
    else -> ExtractOutcome.Retryable
}

/** [attempts] counts retryable failures including this one. */
fun statusAfter(outcome: ExtractOutcome, attempts: Int): ItemStatus = when (outcome) {
    is ExtractOutcome.Success -> ItemStatus.DONE
    ExtractOutcome.QuotaExhausted -> ItemStatus.QUOTA_BLOCKED
    ExtractOutcome.Invalid -> ItemStatus.FAILED
    ExtractOutcome.Retryable -> if (attempts >= MAX_ATTEMPTS) ItemStatus.FAILED else ItemStatus.UNPROCESSED
}

fun needsAi(ocrText: String): Boolean = ocrText.trim().length >= MIN_OCR_CHARS

fun truncateForApi(ocrText: String): String = ocrText.trim().take(MAX_OCR_CHARS)

/** Backend expects SHA-256(ANDROID_ID) as 64 lowercase hex chars. */
fun deviceIdOf(androidId: String): String =
    MessageDigest.getInstance("SHA-256").digest(androidId.toByteArray()).joinToString("") { "%02x".format(it) }

/** MediaStore.createDeleteRequest needs API 30+ and a MediaStore uri (authority "media"). */
fun canDeleteOriginal(sdkInt: Int, authority: String?): Boolean = sdkInt >= 30 && authority == "media"
```

- [ ] **Step 4: Jalankan dan pastikan lolos**

Run: `cd android/core && gradle test --no-daemon`
Expected: PASS (semua test core).

- [ ] **Step 5: Commit, push, dan pastikan CI hijau**

```bash
git add android/core
git commit -m "feat(core): dynamic actions, status policy and device id"
```

---

### Task 4: Lapisan data app (Room + ImageStore)

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/data/ItemEntity.kt`, `ItemDao.kt`, `AppDatabase.kt`, `ImageStore.kt`

**Interfaces:**
- Consumes: `ItemStatus` (Task 3)
- Produces:
  - `@Entity(tableName = "item") data class ItemEntity(id: String PK, createdAt: Long, imagePath: String, ocrText: String, status: String, category: String? = null, title: String? = null, extractedInfo: String? = null, actionType: String? = null, actionPayload: String? = null, tasks: String? = null, tasksTotal: Int = 0, attempts: Int = 0)`
  - `ItemDao`: `observe(query: String, category: String?): Flow<List<ItemEntity>>`, `observeById(id): Flow<ItemEntity?>`, `get(id): ItemEntity?`, `withStatus(status: String): List<ItemEntity>`, `countWithStatus(status: String): Int`, `insert(item)`, `update(item)`, `delete(id)`
  - `AppDatabase.itemDao()`, file `snapbrain.db`
  - `ImageStore(context)`: `save(uri: Uri, id: String): File`, `delete(path: String)`

- [ ] **Step 1: Implementasi**

`ItemEntity.kt`
```kotlin
package com.snapbrain.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item")
data class ItemEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val imagePath: String,
    val ocrText: String,
    val status: String,
    val category: String? = null,
    val title: String? = null,
    val extractedInfo: String? = null, // JSON object, see ExtractJson
    val actionType: String? = null,
    val actionPayload: String? = null,
    val tasks: String? = null, // JSON array, see ExtractJson
    val tasksTotal: Int = 0,
    val attempts: Int = 0,
)
```

`ItemDao.kt`
```kotlin
package com.snapbrain.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    // ponytail: LIKE scan over every row; move to FTS4 if inboxes reach tens of thousands of items.
    @Query(
        """SELECT * FROM item
           WHERE (:category IS NULL OR category = :category)
             AND (:query = '' OR ocrText LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%')
           ORDER BY createdAt DESC""",
    )
    fun observe(query: String, category: String?): Flow<List<ItemEntity>>

    @Query("SELECT * FROM item WHERE id = :id")
    fun observeById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM item WHERE status = :status ORDER BY createdAt")
    suspend fun withStatus(status: String): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM item WHERE status = :status")
    suspend fun countWithStatus(status: String): Int

    @Insert
    suspend fun insert(item: ItemEntity)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun delete(id: String)
}
```

`AppDatabase.kt`
```kotlin
package com.snapbrain.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "snapbrain.db").build()
    }
}
```

`ImageStore.kt`
```kotlin
package com.snapbrain.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.IOException

/** Keeps our own copy of every screenshot, so Detail still works after the original is deleted. */
class ImageStore(private val context: Context) {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }

    fun save(uri: Uri, id: String): File {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = (resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("decode failed")
        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val file = File(dir, "$id.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    fun delete(path: String) {
        File(path).delete()
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}
```

- [ ] **Step 2: Commit, push, dan pastikan CI hijau** (`assembleDebug` meng-compile DAO lewat KSP. Error query SQL gagal di tahap ini.)

```bash
git add android/app/src/main/kotlin/com/snapbrain/app/data
git commit -m "feat(app): Room item store and image copies"
```

---

### Task 5: Inisialisasi app, OCR, klien extract, repository, worker

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/SnapBrainApp.kt`, `AppContainer.kt`
- Create: `android/app/src/debug/kotlin/com/snapbrain/app/AppCheckSetup.kt`, `android/app/src/release/kotlin/com/snapbrain/app/AppCheckSetup.kt`
- Create: `android/app/src/main/kotlin/com/snapbrain/app/process/OcrEngine.kt`, `ExtractClient.kt`, `ProcessWorker.kt`
- Create: `android/app/src/main/kotlin/com/snapbrain/app/data/ItemRepository.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (`android:name=".SnapBrainApp"`)

**Interfaces:**
- Consumes: `ItemDao`, `ItemEntity`, `ImageStore` (Task 4); `ExtractOutcome`, `ItemStatus`, `outcomeOfCode`, `statusAfter`, `needsAi`, `truncateForApi`, `deviceIdOf` (Task 3); `ExtractJson`, `normalized`, `TaskItem` (Task 2)
- Produces:
  - `SnapBrainApp.container: AppContainer`, `AppContainer.repository: ItemRepository`
  - `ItemRepository`: `observe(query: String, category: String?)`, `observe(id: String)`, `suspend capture(uri: Uri): ItemEntity`, `suspend process(item: ItemEntity): ItemEntity`, `suspend processPending()`, `suspend hasPending(): Boolean`, `suspend requeueQuotaBlocked()`, `suspend retry(id: String)`, `suspend discard(id: String)`, `suspend toggleTask(id: String, taskId: Int)`
  - `ProcessWorker.enqueue(context: Context)`
  - `fun installAppCheck()` (per build type)

- [ ] **Step 1: Implementasi**

`AppCheckSetup.kt` (debug)
```kotlin
package com.snapbrain.app

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug builds print a token to logcat; register it in Firebase Console → App Check → Manage debug tokens. */
fun installAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
```

`AppCheckSetup.kt` (release)
```kotlin
package com.snapbrain.app

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

fun installAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
```

`SnapBrainApp.kt`
```kotlin
package com.snapbrain.app

import android.app.Application

class SnapBrainApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        container = AppContainer(this)
    }
}
```

`AppContainer.kt`
```kotlin
package com.snapbrain.app

import android.content.Context
import android.provider.Settings
import com.snapbrain.app.data.AppDatabase
import com.snapbrain.app.data.ImageStore
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.core.deviceIdOf

class AppContainer(context: Context) {
    private val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    val repository = ItemRepository(
        dao = AppDatabase.create(context).itemDao(),
        images = ImageStore(context),
        ocr = OcrEngine(context),
        client = ExtractClient(deviceIdOf(androidId)),
    )
}
```

`OcrEngine.kt`
```kotlin
package com.snapbrain.app.process

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File

class OcrEngine(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(file: File): String =
        recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(file))).await().text
}
```

`ExtractClient.kt`
```kotlin
package com.snapbrain.app.process

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ExtractOutcome
import com.snapbrain.core.outcomeOfCode
import com.snapbrain.core.truncateForApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class ExtractClient(private val deviceId: String) {
    private val functions = Firebase.functions("asia-southeast2")

    suspend fun extract(itemId: String, ocrText: String): ExtractOutcome = try {
        if (Firebase.auth.currentUser == null) Firebase.auth.signInAnonymously().await()
        val payload = mapOf("ocr_text" to truncateForApi(ocrText), "device_id" to deviceId, "item_id" to itemId)
        val result = functions.getHttpsCallable("extract").call(payload).await()
        val json = JSONObject(result.data as Map<*, *>).toString()
        ExtractOutcome.Success(ExtractJson.parse(json))
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFunctionsException) {
        outcomeOfCode(e.code.name)
    } catch (e: Exception) {
        // Network, auth or unexpected payload: worth retrying with backoff; never log OCR text here.
        ExtractOutcome.Retryable
    }
}
```

`ItemRepository.kt`
```kotlin
package com.snapbrain.app.data

import android.net.Uri
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ExtractOutcome
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.needsAi
import com.snapbrain.core.normalized
import com.snapbrain.core.statusAfter
import kotlinx.coroutines.CancellationException
import java.util.UUID

class ItemRepository(
    private val dao: ItemDao,
    private val images: ImageStore,
    private val ocr: OcrEngine,
    private val client: ExtractClient,
) {
    fun observe(query: String, category: String?) = dao.observe(query.trim(), category)
    fun observe(id: String) = dao.observeById(id)

    /** Saves the screenshot locally before any network call, so nothing is lost offline. */
    suspend fun capture(uri: Uri): ItemEntity {
        val id = UUID.randomUUID().toString()
        val file = images.save(uri, id)
        val text = try {
            ocr.read(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        val item = if (needsAi(text)) {
            ItemEntity(id, System.currentTimeMillis(), file.path, text, ItemStatus.UNPROCESSED.name)
        } else {
            ItemEntity(id, System.currentTimeMillis(), file.path, text, ItemStatus.DONE.name, category = "unclassified")
        }
        dao.insert(item)
        return item
    }

    suspend fun process(item: ItemEntity): ItemEntity {
        val updated = when (val outcome = client.extract(item.id, item.ocrText)) {
            is ExtractOutcome.Success -> {
                val d = outcome.response.data.normalized()
                item.copy(
                    status = ItemStatus.DONE.name,
                    category = d.category,
                    title = d.title,
                    extractedInfo = ExtractJson.encodeInfo(d.extractedInfo),
                    actionType = d.actionType,
                    actionPayload = d.actionPayload,
                    tasks = ExtractJson.encodeTasks(d.tasks),
                    tasksTotal = outcome.response.tasksTotal,
                )
            }
            ExtractOutcome.Retryable -> {
                val attempts = item.attempts + 1
                item.copy(status = statusAfter(outcome, attempts).name, attempts = attempts)
            }
            else -> item.copy(status = statusAfter(outcome, item.attempts).name)
        }
        dao.update(updated)
        return updated
    }

    suspend fun processPending() {
        dao.withStatus(ItemStatus.UNPROCESSED.name).forEach { process(it) }
    }

    suspend fun hasPending(): Boolean = dao.countWithStatus(ItemStatus.UNPROCESSED.name) > 0

    /** Quota-blocked items get another try on app start; the server answers resource-exhausted cheaply. */
    suspend fun requeueQuotaBlocked() {
        dao.withStatus(ItemStatus.QUOTA_BLOCKED.name).forEach { dao.update(it.copy(status = ItemStatus.UNPROCESSED.name)) }
    }

    suspend fun retry(id: String) {
        dao.get(id)?.let { dao.update(it.copy(status = ItemStatus.UNPROCESSED.name, attempts = 0)) }
    }

    suspend fun discard(id: String) {
        dao.get(id)?.let {
            images.delete(it.imagePath)
            dao.delete(id)
        }
    }

    suspend fun toggleTask(id: String, taskId: Int) {
        val item = dao.get(id) ?: return
        val tasks = ExtractJson.decodeTasks(item.tasks).map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
        dao.update(item.copy(tasks = ExtractJson.encodeTasks(tasks)))
    }
}
```

`ProcessWorker.kt`
```kotlin
package com.snapbrain.app.process

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.snapbrain.app.SnapBrainApp
import java.util.concurrent.TimeUnit

class ProcessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as SnapBrainApp).container.repository
        repository.processPending()
        return if (repository.hasPending()) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProcessWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("process-pending", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
```

Di `AndroidManifest.xml`, tambahkan `android:name=".SnapBrainApp"` ke `<application>`.

- [ ] **Step 2: Commit, push, dan pastikan CI hijau** (sekarang `assembleDebug` dan `assembleRelease` sama-sama perlu compile. Ubah langkah Gradle di `android.yml` menjadi `./gradlew --no-daemon :core:test assembleDebug compileReleaseKotlin`, supaya source set `release` ikut di-compile.)

```bash
git add android .github/workflows/android.yml
git commit -m "feat(app): OCR, extract client, repository and background worker"
```

---

### Task 6: Alur share (Milestone 2)

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/ui/Theme.kt`, `android/app/src/main/kotlin/com/snapbrain/app/ui/Perform.kt`, `android/app/src/main/kotlin/com/snapbrain/app/share/ShareActivity.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ItemRepository.capture/process/discard`, `ProcessWorker.enqueue`, `ItemEntity` (Task 4-5); `actionOf`, `Action`, `canDeleteOriginal`, `categoryLabel`, `ExtractJson`, `ItemStatus` (core)
- Produces:
  - `@Composable fun SnapBrainTheme(content: @Composable () -> Unit)`
  - `fun Context.perform(action: Action)`

- [ ] **Step 1: Implementasi**

`Theme.kt`
```kotlin
package com.snapbrain.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF00897B))
private val Dark = darkColorScheme(primary = Color(0xFF4DB6AC))

@Composable
fun SnapBrainTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
```

`Perform.kt`
```kotlin
package com.snapbrain.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import com.snapbrain.core.Action

fun Context.perform(action: Action) {
    val intent = when (action) {
        is Action.OpenUrl -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        is Action.TrackParcel -> Intent(Intent.ACTION_VIEW, Uri.parse(action.searchUrl))
        is Action.AddCalendar -> Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, action.title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.beginMillis)
        is Action.CopyText -> {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("SnapBrain", action.text))
            Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show()
            return
        }
    }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Tidak ada aplikasi untuk membuka ini", Toast.LENGTH_SHORT).show()
    }
}
```

`ShareActivity.kt`
```kotlin
package com.snapbrain.app.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.app.ui.SnapBrainTheme
import com.snapbrain.app.ui.perform
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.actionOf
import com.snapbrain.core.canDeleteOriginal
import com.snapbrain.core.categoryLabel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SYNC_TIMEOUT_MS = 6_000L

private sealed interface ShareState {
    data object Reading : ShareState
    data object Analyzing : ShareState
    data class Result(val item: ItemEntity) : ShareState
    data object Queued : ShareState
    data object QuotaBlocked : ShareState
    data object Unreadable : ShareState
}

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri == null) {
            finish()
            return
        }
        val repository = (application as SnapBrainApp).container.repository
        setContent { SnapBrainTheme { ShareSheet(uri, repository, onClose = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(uri: Uri, repository: ItemRepository, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShareState>(ShareState.Reading) }
    var itemId by remember { mutableStateOf<String?>(null) }
    val deleteOriginal = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onClose() }

    LaunchedEffect(uri) {
        val item = try {
            repository.capture(uri)
        } catch (e: java.io.IOException) {
            state = ShareState.Unreadable
            return@LaunchedEffect
        }
        itemId = item.id
        if (item.status == ItemStatus.DONE.name) {
            state = ShareState.Result(item)
            return@LaunchedEffect
        }
        state = ShareState.Analyzing
        val processed = withTimeoutOrNull(SYNC_TIMEOUT_MS) { repository.process(item) }
        state = when (processed?.status) {
            ItemStatus.DONE.name -> ShareState.Result(processed)
            ItemStatus.QUOTA_BLOCKED.name -> ShareState.QuotaBlocked
            else -> ShareState.Queued
        }
    }
    // Leaving before the AI answered (timeout, offline, sheet closed): the worker picks the item up.
    DisposableEffect(Unit) {
        onDispose {
            val done = state is ShareState.Result || state is ShareState.QuotaBlocked || state is ShareState.Unreadable
            if (itemId != null && !done) ProcessWorker.enqueue(context.applicationContext)
        }
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                ShareState.Reading -> Loading("Mengekstrak teks...")
                ShareState.Analyzing -> Loading("AI sedang menganalisis konteks...")
                ShareState.Unreadable -> {
                    Text("Gambar tidak bisa dibaca.")
                    Button(onClick = onClose) { Text("Tutup") }
                }
                ShareState.Queued -> {
                    Text("Tersimpan. Akan diproses otomatis saat online.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { itemId?.let { repository.discard(it) }; onClose() } }) { Text("Batal") }
                        Button(onClick = onClose) { Text("Tutup") }
                    }
                }
                ShareState.QuotaBlocked -> {
                    Text("Kuota AI bulan ini habis. Screenshot tetap tersimpan dan akan diproses saat kuota tersedia.")
                    Button(onClick = onClose) { Text("Tutup") }
                }
                is ShareState.Result -> {
                    val item = s.item
                    Text(categoryLabel(item.category), style = MaterialTheme.typography.labelMedium)
                    Text(item.title ?: "Screenshot tersimpan", style = MaterialTheme.typography.titleLarge)
                    ExtractJson.decodeInfo(item.extractedInfo).entries.take(2).forEach { (k, v) -> Text("$k: $v") }
                    actionOf(item.actionType, item.actionPayload)?.let { action ->
                        OutlinedButton(onClick = { context.perform(action) }) { Text(action.label) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { repository.discard(item.id); onClose() } }) { Text("Batal") }
                        Button(onClick = onClose) { Text("Simpan") }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canDeleteOriginal(Build.VERSION.SDK_INT, uri.authority)) {
                            OutlinedButton(onClick = {
                                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                                deleteOriginal.launch(IntentSenderRequest.Builder(request.intentSender).build())
                            }) { Text("Simpan & Hapus Asli") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(text)
    }
}
```

Tambahkan ke `<application>` di manifest:
```xml
<activity
    android:name=".share.ShareActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:theme="@style/Theme.SnapBrain.Translucent">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Commit, push, dan pastikan CI hijau.** Artifact `snapbrain-debug-apk` menjadi build Milestone 2.

```bash
git add android/app
git commit -m "feat(app): share sheet with sync attempt and offline fallback (milestone 2)"
```

---

### Task 7: Inbox, Search, Detail (Milestone 3) + checklist tes manual

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/ui/Components.kt`, `InboxScreen.kt`, `DetailScreen.kt`
- Modify: `android/app/src/main/kotlin/com/snapbrain/app/ui/MainActivity.kt`
- Create: `docs/manual-test-android.md`

**Interfaces:**
- Consumes: `ItemRepository` (observe, retry, discard, toggleTask, requeueQuotaBlocked), `ProcessWorker.enqueue`, `SnapBrainTheme`, `perform` (Task 5-6); `actionOf`, `categoryLabel`, `CATEGORIES`, `ExtractJson`, `ItemStatus` (core)
- Produces: layar yang bisa dipakai. Tidak ada interface baru untuk task lain.

- [ ] **Step 1: Implementasi**

`Components.kt`
```kotlin
package com.snapbrain.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.actionOf
import com.snapbrain.core.categoryLabel
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun statusText(item: ItemEntity): String? = when (item.status) {
    ItemStatus.UNPROCESSED.name -> "⏳ Menunggu internet"
    ItemStatus.QUOTA_BLOCKED.name -> "⛔ Kuota habis"
    ItemStatus.FAILED.name -> "⚠️ Gagal, coba lagi"
    else -> null
}

fun dateText(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("id")).format(Date(millis))

@Composable
fun SmartCard(item: ItemEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(
                model = File(item.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.15f), BlendMode.Darken),
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${categoryLabel(item.category)} · ${dateText(item.createdAt)}", style = MaterialTheme.typography.labelSmall)
                Text(
                    item.title ?: "Screenshot tersimpan",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                statusText(item)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                actionOf(item.actionType, item.actionPayload)?.let { action ->
                    OutlinedButton(onClick = { context.perform(action) }) { Text(action.label) }
                }
            }
        }
    }
}
```

`InboxScreen.kt`
```kotlin
package com.snapbrain.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapbrain.app.data.ItemRepository

private val FILTERS = listOf(
    "Semua" to null,
    "Tugas" to "task",
    "Keuangan" to "finance",
    "Event" to "event",
    "Belanja" to "shopping",
    "Referensi" to "reference",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(repository: ItemRepository, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val items by remember(query, category) { repository.observe(query, category) }.collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("SnapBrain") }) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari resep, resi, catatan...") },
                leadingIcon = { Text("🔍") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FILTERS) { (label, key) ->
                    FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank() && category == null) {
                            "Belum ada screenshot. Coba share screenshot ke aplikasi ini!"
                        } else {
                            "Tidak ada hasil."
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item -> SmartCard(item, onClick = { onOpen(item.id) }) }
                }
            }
        }
    }
}
```

`DetailScreen.kt`
```kotlin
package com.snapbrain.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.core.ExtractJson
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.actionOf
import com.snapbrain.core.categoryLabel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, repository: ItemRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item by remember(id) { repository.observe(id) }.collectAsState(initial = null)
    BackHandler(onBack = onBack)
    val current = item ?: return
    val tasks = ExtractJson.decodeTasks(current.tasks)
    val locked = current.tasksTotal - tasks.size
    val info = ExtractJson.decodeInfo(current.extractedInfo).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title ?: "Detail") },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = {
                    TextButton(onClick = { scope.launch { repository.discard(id); onBack() } }) { Text("Hapus") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ZoomableImage(current.imagePath) }
            item {
                Text(categoryLabel(current.category), style = MaterialTheme.typography.labelMedium)
                Text(current.title ?: "Screenshot tersimpan", style = MaterialTheme.typography.headlineSmall)
                statusText(current)?.let { Text(it) }
                if (current.status == ItemStatus.FAILED.name) {
                    Button(onClick = {
                        scope.launch { repository.retry(id); ProcessWorker.enqueue(context.applicationContext) }
                    }) { Text("Coba lagi") }
                }
            }
            items(info.size) { index ->
                val (key, value) = info[index]
                Row(Modifier.fillMaxWidth()) {
                    Text(key, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.4f))
                    Text(value, modifier = Modifier.weight(0.6f))
                }
            }
            actionOf(current.actionType, current.actionPayload)?.let { action ->
                item { OutlinedButton(onClick = { context.perform(action) }) { Text(action.label) } }
            }
            if (tasks.isNotEmpty()) {
                item { Text("Tugas", style = MaterialTheme.typography.titleMedium) }
                items(tasks.size) { index ->
                    val task = tasks[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = task.isCompleted, onCheckedChange = { scope.launch { repository.toggleTask(id, task.id) } })
                        Text(task.description)
                    }
                }
            }
            if (locked > 0) item { LockedTasks(locked) }
        }
    }
}

/** Free tier placeholder; Plan 3 turns the CTA into the paywall. */
@Composable
private fun LockedTasks(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count.coerceAtMost(3)) {
            Box(
                Modifier.fillMaxWidth().height(20.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
            )
        }
        Text("🔒 $count tugas lain — Buka AI Task Planner (Pro)", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ZoomableImage(path: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().height(360.dp).clipToBounds()
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .transformable(state),
    )
}
```

`MainActivity.kt` (ganti seluruh isi file)
```kotlin
package com.snapbrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.process.ProcessWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SnapBrainApp).container.repository
        setContent {
            SnapBrainTheme {
                LaunchedEffect(Unit) {
                    repository.requeueQuotaBlocked()
                    ProcessWorker.enqueue(applicationContext)
                }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                val id = openId
                if (id == null) {
                    InboxScreen(repository, onOpen = { openId = it })
                } else {
                    DetailScreen(id, repository, onBack = { openId = null })
                }
            }
        }
    }
}
```

`docs/manual-test-android.md`
```markdown
# Checklist tes di HP (APK debug dari artifact CI `snapbrain-debug-apk`)

Prasyarat: backend sudah di-deploy (docs/backend-ops.md). Token debug App Check sudah didaftarkan: jalankan app sekali, cari "DebugAppCheckProvider" di logcat, lalu tempel token-nya di Firebase Console → App Check → Manage debug tokens. Tanpa backend, item akan tersimpan sebagai "Menunggu internet".

1. Buka SnapBrain. Inbox kosong menampilkan "Belum ada screenshot...".
2. Galeri → pilih screenshot struk transfer → Share → SnapBrain. Sheet muncul, lalu tampil "Mengekstrak teks..." dan "AI sedang menganalisis konteks...". Hasil tampil dengan kategori 💰 Keuangan dan tombol Salin/aksi.
3. Tekan "Simpan & Hapus Asli" (Android 11+). Dialog sistem muncul. Setelah disetujui, foto hilang dari galeri, dan item tetap terbuka di Detail beserta gambarnya.
4. Share dari WhatsApp. Hanya tombol "Simpan" dan "Batal" yang muncul.
5. Mode pesawat → share screenshot. Muncul "Tersimpan. Akan diproses otomatis saat online.". Matikan mode pesawat. Dalam beberapa menit item di Inbox berubah dari "⏳ Menunggu internet" menjadi hasil AI.
6. Share lalu langsung tutup sheet sebelum hasil keluar. Item tetap muncul di Inbox dan akhirnya terproses.
7. Share foto tanpa teks. Langsung tersimpan sebagai 📄 Lainnya, dan kuota tidak berkurang.
8. Search nomor resi sebagian (misal 4 digit terakhir). Item yang cocok muncul. Chip "Belanja" memfilter kategori.
9. Detail: pinch-zoom gambar, centang task, lalu buka ulang app. Centang task tersimpan.
10. Tombol aksi: "Lacak Paket" membuka pencarian, "Tambah ke Kalender" membuka form kalender, "Buka Link" membuka browser.
```

- [ ] **Step 2: Commit, push, dan pastikan CI hijau.** Artifact menjadi build Milestone 3.

```bash
git add android/app docs/manual-test-android.md
git commit -m "feat(app): inbox, search and detail screens (milestone 3)"
```
