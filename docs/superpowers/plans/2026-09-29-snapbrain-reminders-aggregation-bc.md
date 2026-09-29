# SnapBrain Pengingat + Gabungan — Fase B+C Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Item bertenggat memunculkan notifikasi pengingat (Fase B), dan daftar dari banyak screenshot tergabung di tab Belanja dan To-do dengan total, budget, dan perbandingan harga (Fase C).

**Architecture:**
- **core (JVM murni, dites lokal):** waktu pengingat, pengelompokan To-do, label tombol aktivasi, normalisasi nama bahan, ukuran kemasan dan harga per satuan, total/budget, tabel perbandingan, `IS_PRO`.
- **app:** query Room baru di `ListItemDao` (tanpa migrasi: semua kolom sudah ada sejak v2), `ReminderScheduler` di atas WorkManager, notifikasi platform (`Notification.Builder`, minSdk 26), navigasi bawah Inbox · Belanja · To-do, dan tiga layar baru.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.0, Compose BOM 2026.09.00 (material3), Room 2.8.5, WorkManager 2.12.0. Tidak ada dependency baru.

**Spec:** `docs/superpowers/specs/2026-09-29-snapbrain-smart-lists-design.md` §7 (Fase B), §8 (Fase C), §5 (tabel `list_item`), §6.3 (tombol aktivasi), keputusan S4, S7–S13, S17. Mockup: canvas "SnapBrain UI Directions" baris **B** (`BelanjaLight`, `TodoLight`) — https://claude.ai/artifact/7pejisETrkRkbiRnndRq9c.

**Gerbang tes perangkat antar-fase dihapus atas permintaan pengguna** ("Langsung bangun B dan C semuanya, tes belakangan"). Fase A, B, dan C dites di HP sekaligus setelah plan ini selesai, memakai checklist di `docs/manual-test-android.md`. Risiko yang diterima: bug Fase A (migrasi, splash, UI) baru ketahuan bersamaan dengan bug B+C.

## Global Constraints

- Tidak ada dependency baru. Notifikasi memakai API platform (`android.app.Notification`, `NotificationChannel`), bukan `NotificationCompat`.
- Tidak ada migrasi Room baru: database tetap versi 2. Kolom `remind`, `inBelanja`, `checked`, `checkedAt` (`list_item`) dan `active` (`item`) sudah ada.
- Teks UI dalam Bahasa Indonesia. Kontras teks ≥ 4.5:1 di tema terang dan gelap (pakai warna dari `MaterialTheme.colorScheme`, `soonColor`, `strongButtonColor`).
- Pengingat (spec §7): tanggal saja → H-1 08:00 dan hari H 08:00 waktu lokal; dengan jam → 1 jam sebelumnya; waktu yang sudah lewat tidak dijadwalkan. Nama kerja unik `reminder-<listItemId>-<slot>`, tag `reminder`. Kanal `reminders` bernama "Pengingat". Sakelar global di SharedPreferences `snapbrain` kunci `reminders_enabled` (default `true`).
- Tidak ada izin alarm persis (`USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`). Izin baru hanya `POST_NOTIFICATIONS`.
- Budget bulanan di SharedPreferences `snapbrain` kunci `budget_month` (Long rupiah, 0 = tidak diatur).
- `IS_PRO = true` (spec S4/S17). Flag ini menyembunyikan navigasi bawah (tab Belanja/To-do beserta Bandingkan dan Budget di dalamnya) dan tombol aktivasi. Pengingat (Fase B) gratis dan tidak memakai flag ini.
- Tanpa edit manual isi daftar (spec S13). Bahan yang sama dari resep berbeda tidak dijumlahkan jumlahnya; ditampilkan berdampingan (spec S12).
- `app` hanya bisa di-compile di CI. `core` dites lokal.

## Review Focus

1. **Tombol aktivasi yang tidak ada tujuannya:** tagihan tanpa daftar `todo`, atau `beli` tanpa baris `belanja`. Tombol tidak boleh menjanjikan "✓ Ada di To-do" yang kosong; tombol disembunyikan. Diuji di Task 1 (`ActivationTest.hiddenWhenNothingWouldLand`).
2. **Screenshot lama/hari ini disimpan setelah jam pengingat lewat:** tenggat hari ini jam 10:00 disimpan jam 12:00, tenggat 30 menit lagi, tenggat bulan lalu. Tidak ada notifikasi "telat" yang langsung muncul. Diuji di Task 1 (`RemindersTest.dropsPastTimes`).
3. **Nama bahan dengan angka/satuan di mana saja:** "Bawang merah 5 siung", "½ sdt garam", "500g tepung", "buah naga", "santan (dari 1 butir kelapa)". Harus tergabung dengan benar tanpa memakan kata yang bukan satuan. Diuji di Task 1 (`BelanjaTest.ingredientKey…`).
4. **Ukuran kemasan yang aneh:** "1,5 L", "1.500 ml", "isi 12", "2x250ml", "jumbo", "". Harga per satuan benar atau "–", tidak crash dan tidak angka absurd. Diuji di Task 1 (`BelanjaTest.unitPrice…`).
5. **Centang/hapus/ekstrak ulang/sakelar global yang harus membatalkan pengingat:** tidak boleh ada notifikasi untuk item yang sudah dicentang atau dihapus. `app` tidak punya source set unit test, jadi diverifikasi lewat review kode Task 4 (setiap jalur mutasi memanggil `reminders.sync`/`cancel`) dan checklist perangkat Task 5 (butir B3–B5).

## Cara verifikasi

- **Core Android:** `cd android && ./gradlew --no-daemon -p core test` (jalan lokal).
- **App Android:** hanya lewat CI (tidak ada Android SDK lokal):
  1. Push ke `claude/wizardly-dijkstra-4m9ayw` (`git push -u origin claude/wizardly-dijkstra-4m9ayw`; tidak pernah force-push).
  2. `mcp__github__actions_list` (`method: list_workflow_runs`, owner `hellvyn`, repo `SnapBrain`, `workflow_runs_filter.branch` = branch) untuk run `android` yang `head_sha`-nya sama dengan `git rev-parse HEAD`.
  3. Tunggu sampai `completed`, cek maksimal 20 kali dengan jeda sekitar 60 detik. Jangan pakai `sleep` lebih dari 60 detik per perintah.
  4. Kalau gagal, ambil log: `mcp__github__actions_list` `list_workflow_jobs` → `mcp__github__get_job_logs` (`job_id`, `return_content: true`, `tail_lines: 200`). Perbaiki, commit, push, ulangi.

## File map

| File | Tanggung jawab | Task |
|---|---|---|
| `android/core/src/main/kotlin/com/snapbrain/core/Due.kt` | + `parseDue`, `isOverdue` bersama | 1 |
| `…/core/Reminders.kt` (baru) | `reminderTimes`, `reminderTitle` | 1 |
| `…/core/Todo.kt` (baru) | `todoBucket`, `todoGroups` | 1 |
| `…/core/Activation.kt` (baru) | `activationLabel`, `activatesBelanja`, `todoEligible` | 1 |
| `…/core/Belanja.kt` (baru) | `ingredientKey`, `groupByIngredient`, `rupiah`, `parseSize`, `unitPriceText`, `belanjaTotal`, `budgetOf`, `monthStartMillis`, `compareTable` | 1 |
| `…/core/Policy.kt` | + `IS_PRO` | 1 |
| `android/app/…/data/ListItemDao.kt` | query Belanja/To-do/spent/remindable + mutasi | 2 |
| `…/data/ItemDao.kt` | + `setActive` | 2 |
| `…/data/ItemRepository.kt` | aktivasi, centang grup, budget, compare; lalu hook pengingat (Task 4) | 2, 4 |
| `…/ui/Theme.kt` | + `strongButtonColor` | 2 |
| `…/ui/DetailScreen.kt` | tombol aktivasi (Task 2), 🔔 per baris (Task 4) | 2, 4 |
| `…/ui/Icons.kt` | + `Inbox`, `Bell`, `BellOff` | 3, 4 |
| `…/ui/Components.kt` | + `snapCheckboxColors` | 3 |
| `…/ui/TodoScreen.kt` (baru) | layar To-do | 3, 4 |
| `…/ui/MainActivity.kt` | navigasi bawah (Task 3), deep link notifikasi + banner izin (Task 4), tab Belanja (Task 5) | 3, 4, 5 |
| `…/reminder/ReminderScheduler.kt`, `ReminderWorker.kt`, `ReminderReceiver.kt` (baru) | penjadwalan, notifikasi, tombol "Selesai" | 4 |
| `…/ui/Bell.kt` (baru) | `LocalNotificationsAllowed`, `ReminderBell`, `NotificationBanner` | 4 |
| `…/ui/Perform.kt` | + `openNotificationSettings` | 4 |
| `…/ui/InboxScreen.kt` | menu ⋮ sakelar pengingat | 4 |
| `…/SnapBrainApp.kt`, `…/AppContainer.kt`, `AndroidManifest.xml`, `res/drawable/ic_stat_snapbrain.xml` | kanal, wiring, izin, receiver, ikon notifikasi | 4 |
| `…/ui/BelanjaScreen.kt` (baru) | layar Belanja, budget, Bandingkan | 5 |
| `docs/manual-test-android.md` | checklist Fase B+C | 5 |

---

### Task 1: core — pengingat, To-do, aktivasi, Belanja

**Files:**
- Modify: `android/core/src/main/kotlin/com/snapbrain/core/Due.kt`, `android/core/src/main/kotlin/com/snapbrain/core/Policy.kt`
- Create: `android/core/src/main/kotlin/com/snapbrain/core/Reminders.kt`, `Todo.kt`, `Activation.kt`, `Belanja.kt`
- Test (create): `android/core/src/test/kotlin/com/snapbrain/core/RemindersTest.kt`, `TodoTest.kt`, `ActivationTest.kt`, `BelanjaTest.kt`

**Interfaces:**
- Consumes: `dueLabel(due: String?, now: LocalDateTime): DueLabel?` (sudah ada di `Due.kt`).
- Produces (dipakai Task 2–5):
  - `internal fun parseDue(due: String?): Pair<LocalDate, LocalTime?>?`, `internal fun isOverdue(date: LocalDate, time: LocalTime?, now: LocalDateTime): Boolean`
  - `enum class ReminderSlot { DAY_BEFORE, SAME_DAY, HOUR_BEFORE }`, `data class ReminderTime(val slot: ReminderSlot, val atMillis: Long)`, `fun reminderTimes(due: String?, now: ZonedDateTime): List<ReminderTime>`, `fun reminderTitle(text: String, due: String?, now: LocalDateTime): String`
  - `enum class TodoBucket(val label: String)`, `fun todoBucket(due: String?, now: LocalDateTime): TodoBucket`, `data class TodoGroup<T>(val title: String, val bucket: TodoBucket?, val rows: List<T>)`, `fun <T> todoGroups(rows: List<T>, now: LocalDateTime, due: (T) -> String?, stepsTitle: (T) -> String?): List<TodoGroup<T>>`
  - `fun activatesBelanja(activation: String?): Boolean`, `fun todoEligible(role: String, kind: String): Boolean`, `fun activationLabel(activation: String?, active: Boolean, toBelanja: Boolean, toTodo: Boolean): String?`
  - `fun ingredientKey(text: String): String`, `data class IngredientGroup<T>(val name: String, val rows: List<T>)`, `fun <T> groupByIngredient(rows: List<T>, text: (T) -> String): List<IngredientGroup<T>>`
  - `fun rupiah(amount: Long): String`, `enum class SizeUnit`, `data class PackSize(val amount: Double, val unit: SizeUnit)`, `fun parseSize(size: String?): PackSize?`, `fun unitPriceText(price: Long, size: String?): String`
  - `data class BelanjaTotal(val count: Int, val sum: Long, val noPrice: Int)`, `fun belanjaTotal(prices: List<Long>): BelanjaTotal`
  - `data class Budget(val spent: Long, val left: Long, val planned: Long) { val over: Boolean }`, `fun budgetOf(monthly: Long, spent: Long, planned: Long): Budget`, `fun monthStartMillis(now: ZonedDateTime): Long`
  - `data class CompareColumn(val title: String, val price: Long, val size: String?, val info: Map<String, String>)`, `fun compareTable(columns: List<CompareColumn>): List<Pair<String, List<String>>>`
  - `const val IS_PRO = true`

- [ ] **Step 1: Tulis test (gagal)**

`android/core/src/test/kotlin/com/snapbrain/core/RemindersTest.kt`:

```kotlin
package com.snapbrain.core

import com.snapbrain.core.ReminderSlot.DAY_BEFORE
import com.snapbrain.core.ReminderSlot.HOUR_BEFORE
import com.snapbrain.core.ReminderSlot.SAME_DAY
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RemindersTest {
    private val jakarta = ZoneId.of("Asia/Jakarta")
    private fun at(month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = jakarta) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)
    private fun millis(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private val now = at(9, 29, 12, 0) // Selasa 12:00 WIB

    @Test
    fun dateOnlyRemindsTheDayBeforeAndOnTheDayAtEight() {
        assertEquals(
            listOf(ReminderTime(DAY_BEFORE, millis(at(10, 1, 8, 0))), ReminderTime(SAME_DAY, millis(at(10, 2, 8, 0)))),
            reminderTimes("2026-10-02", now),
        )
    }

    @Test
    fun timedRemindsOneHourBefore() {
        assertEquals(listOf(ReminderTime(HOUR_BEFORE, millis(at(9, 30, 22, 59)))), reminderTimes("2026-09-30T23:59", now))
    }

    @Test
    fun dropsPastTimes() {
        assertEquals(listOf(SAME_DAY), reminderTimes("2026-09-30", now).map { it.slot }) // today 08:00 already passed
        assertEquals(emptyList(), reminderTimes("2026-09-29", now)) // due today, both 08:00 slots passed
        assertEquals(emptyList(), reminderTimes("2026-09-29T12:30", now)) // due within the hour
        assertEquals(emptyList(), reminderTimes("2026-09-01", now))
        assertEquals(emptyList(), reminderTimes(null, now))
        assertEquals(emptyList(), reminderTimes("besok", now))
    }

    @Test
    fun usesTheDeviceZone() {
        val papua = ZoneId.of("Asia/Jayapura") // UTC+9, two hours ahead of Jakarta
        val times = reminderTimes("2026-10-02", at(9, 29, 12, 0, papua))
        assertEquals(millis(at(10, 2, 8, 0, papua)), times[1].atMillis)
        assertEquals(millis(at(10, 2, 6, 0)), times[1].atMillis)
    }

    @Test
    fun titlesSayWhen() {
        val local = now.toLocalDateTime()
        assertEquals("⏰ Bayar listrik — hari ini", reminderTitle("Bayar listrik", "2026-09-29", local))
        assertEquals("⏰ Bayar listrik — besok", reminderTitle("Bayar listrik", "2026-09-30", local))
        assertEquals("⏰ Kumpul laporan — jam 23:59", reminderTitle("Kumpul laporan", "2026-09-29T23:59", local))
        assertEquals("⏰ Daftar ulang — Jum, 2 Okt", reminderTitle("Daftar ulang", "2026-10-02", local))
        assertEquals("⏰ Catat", reminderTitle("Catat", null, local))
    }
}
```

`android/core/src/test/kotlin/com/snapbrain/core/TodoTest.kt`:

```kotlin
package com.snapbrain.core

import com.snapbrain.core.TodoBucket.HARI_INI
import com.snapbrain.core.TodoBucket.MINGGU_INI
import com.snapbrain.core.TodoBucket.NANTI
import com.snapbrain.core.TodoBucket.TANPA
import com.snapbrain.core.TodoBucket.TERLAMBAT
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TodoTest {
    private val now = LocalDateTime.of(2026, 9, 29, 12, 0) // Selasa

    @Test
    fun bucketsByDue() {
        assertEquals(TERLAMBAT, todoBucket("2026-09-28", now))
        assertEquals(TERLAMBAT, todoBucket("2026-09-29T09:00", now))
        assertEquals(HARI_INI, todoBucket("2026-09-29", now)) // a date-only due lasts all day
        assertEquals(HARI_INI, todoBucket("2026-09-29T23:59", now))
        assertEquals(MINGGU_INI, todoBucket("2026-09-30", now))
        assertEquals(MINGGU_INI, todoBucket("2026-10-05", now)) // today + 6
        assertEquals(NANTI, todoBucket("2026-10-06", now))
        assertEquals(TANPA, todoBucket(null, now))
        assertEquals(TANPA, todoBucket("", now))
        assertEquals(TANPA, todoBucket("besok", now))
    }

    private data class Row(val text: String, val due: String?, val steps: String? = null)

    @Test
    fun groupsBucketsThenStepsThenUndated() {
        val rows = listOf(
            Row("laptop", "2026-10-02"),
            Row("haluskan bumbu", null, "Pepes Ayam"),
            Row("bayar listrik", "2026-09-28"),
            Row("catat", null),
            Row("kukus", null, "Pepes Ayam"),
            Row("laporan", "2026-09-29T23:59"),
            Row("isi formulir", "2026-10-01"),
            Row("rendam beras", "2026-09-30", "Nasi Liwet"), // a dated step goes to its bucket
        )
        val groups = todoGroups(rows, now, due = { it.due }, stepsTitle = { it.steps })
        assertEquals(listOf("Terlambat", "Hari ini", "Minggu ini", "Pepes Ayam", "Tanpa tenggat"), groups.map { it.title })
        assertEquals(listOf("rendam beras", "isi formulir", "laptop"), groups[2].rows.map { it.text })
        assertEquals(listOf("haluskan bumbu", "kukus"), groups[3].rows.map { it.text })
        assertEquals(null, groups[3].bucket)
        assertEquals(listOf("catat"), groups[4].rows.map { it.text })
    }

    @Test
    fun emptyInputHasNoGroups() {
        assertEquals(emptyList(), todoGroups(emptyList<Row>(), now, { it.due }, { it.steps }))
    }
}
```

`android/core/src/test/kotlin/com/snapbrain/core/ActivationTest.kt`:

```kotlin
package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivationTest {
    @Test
    fun verbsBeforeActivation() {
        assertEquals("Masak sekarang", activationLabel("masak", false, toBelanja = true, toTodo = true))
        assertEquals("Mau beli", activationLabel("beli", false, toBelanja = true, toTodo = false))
        assertEquals("Kerjakan", activationLabel("kerjakan", false, toBelanja = false, toTodo = true))
        assertEquals("Bayar", activationLabel("bayar", false, toBelanja = false, toTodo = true))
        assertEquals("Ikut acara", activationLabel("ikut", false, toBelanja = false, toTodo = true))
        assertEquals("Coba sekarang", activationLabel("coba", false, toBelanja = false, toTodo = true))
    }

    @Test
    fun activeSaysWhereItLanded() {
        assertEquals("✓ Ada di Belanja & To-do", activationLabel("masak", true, toBelanja = true, toTodo = true))
        assertEquals("✓ Ada di Belanja", activationLabel("beli", true, toBelanja = true, toTodo = false))
        assertEquals("✓ Ada di To-do", activationLabel("ikut", true, toBelanja = false, toTodo = true))
    }

    @Test
    fun hiddenWhenNothingWouldLand() {
        assertNull(activationLabel("bayar", false, toBelanja = false, toTodo = false))
        assertNull(activationLabel("none", false, toBelanja = true, toTodo = true))
        assertNull(activationLabel(null, false, toBelanja = true, toTodo = true))
        assertNull(activationLabel("terbang", false, toBelanja = true, toTodo = true))
    }

    @Test
    fun targets() {
        assertTrue(activatesBelanja("masak"))
        assertTrue(activatesBelanja("beli"))
        assertFalse(activatesBelanja("ikut"))
        assertFalse(activatesBelanja(null))
        assertTrue(todoEligible("todo", "checklist"))
        assertTrue(todoEligible("bawa", "checklist"))
        assertTrue(todoEligible("lainnya", "steps"))
        assertFalse(todoEligible("belanja", "checklist"))
        assertFalse(todoEligible("lainnya", "checklist"))
    }
}
```

`android/core/src/test/kotlin/com/snapbrain/core/BelanjaTest.kt`:

```kotlin
package com.snapbrain.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BelanjaTest {
    @Test
    fun ingredientKeyDropsQuantitiesAndUnits() {
        assertEquals("bawang merah", ingredientKey("9 butir bawang merah"))
        assertEquals("bawang merah", ingredientKey("Bawang merah 5 siung"))
        assertEquals("garam", ingredientKey("½ sdt garam"))
        assertEquals("garam", ingredientKey("garam secukupnya"))
        assertEquals("gula pasir", ingredientKey("1 1/2 sdm gula pasir"))
        assertEquals("bawang putih", ingredientKey("3-4 siung bawang putih"))
        assertEquals("tepung terigu", ingredientKey("500g tepung terigu"))
        assertEquals("tomat", ingredientKey("2 buah tomat, potong dadu"))
        assertEquals("santan", ingredientKey("santan (dari 1 butir kelapa)"))
        assertEquals("garam", ingredientKey("setengah sdt garam."))
    }

    @Test
    fun ingredientKeyKeepsWordsThatOnlyLookLikeUnits() {
        assertEquals("buah naga", ingredientKey("buah naga"))
        assertEquals("daun jeruk", ingredientKey("3 lembar daun jeruk"))
        assertEquals("200 ml", ingredientKey("200 ml")) // nothing left: fall back to the text
    }

    @Test
    fun groupsByIngredientInFirstSeenOrder() {
        val groups = groupByIngredient(listOf("9 butir bawang merah", "1 ekor ayam", "Bawang merah 5 siung")) { it }
        assertEquals(listOf("bawang merah", "ayam"), groups.map { it.name })
        assertEquals(listOf("9 butir bawang merah", "Bawang merah 5 siung"), groups[0].rows)
    }

    @Test
    fun formatsRupiah() {
        assertEquals("Rp 0", rupiah(0))
        assertEquals("Rp 734.000", rupiah(734_000))
        assertEquals("Rp 1.250.000", rupiah(1_250_000))
        assertEquals("-Rp 50.000", rupiah(-50_000))
    }

    @Test
    fun parsesPackSizes() {
        assertEquals(PackSize(500.0, SizeUnit.ML), parseSize("500 ml"))
        assertEquals(PackSize(1500.0, SizeUnit.ML), parseSize("1,5 L"))
        assertEquals(PackSize(1500.0, SizeUnit.ML), parseSize("1.500 ml"))
        assertEquals(PackSize(1000.0, SizeUnit.G), parseSize("1kg"))
        assertEquals(PackSize(250.0, SizeUnit.G), parseSize("250 gr"))
        assertEquals(PackSize(12.0, SizeUnit.PCS), parseSize("isi 12"))
        assertEquals(PackSize(10.0, SizeUnit.PCS), parseSize("10 sachet"))
        assertEquals(PackSize(250.0, SizeUnit.ML), parseSize("2x250ml")) // multipacks count one pack
        assertNull(parseSize("jumbo"))
        assertNull(parseSize(""))
        assertNull(parseSize(null))
        assertNull(parseSize("0 ml"))
    }

    @Test
    fun unitPriceOrDash() {
        assertEquals("Rp 5.000 / 100 ml", unitPriceText(25_000, "500 ml"))
        assertEquals("Rp 2.000 / 100 ml", unitPriceText(30_000, "1,5 L"))
        assertEquals("Rp 1.500 / 100 g", unitPriceText(15_000, "1kg"))
        assertEquals("Rp 4.000 / item", unitPriceText(48_000, "isi 12"))
        assertEquals("–", unitPriceText(0, "500 ml"))
        assertEquals("–", unitPriceText(25_000, "jumbo"))
        assertEquals("–", unitPriceText(25_000, null))
    }

    @Test
    fun totalsCountRowsWithoutPriceSeparately() {
        assertEquals(BelanjaTotal(count = 2, sum = 734_000, noPrice = 1), belanjaTotal(listOf(700_000, 0, 34_000)))
        assertEquals(BelanjaTotal(0, 0, 0), belanjaTotal(emptyList()))
    }

    @Test
    fun budgetWarnsWhenPlannedExceedsWhatIsLeft() {
        val b = budgetOf(monthly = 1_000_000, spent = 400_000, planned = 734_000)
        assertEquals(600_000, b.left)
        assertTrue(b.over)
        assertFalse(budgetOf(1_000_000, 0, 734_000).over)
        assertEquals(-50_000, budgetOf(100_000, 150_000, 0).left)
    }

    @Test
    fun monthStartIsLocalMidnightOnTheFirst() {
        val zone = ZoneId.of("Asia/Jakarta")
        val now = ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli(), monthStartMillis(now))
    }

    @Test
    fun compareTableUnionsInfoLabels() {
        val table = compareTable(
            listOf(
                CompareColumn("Susu A", 25_000, "500 ml", mapOf("Toko" to "Toko A", "Rating" to "4.9")),
                CompareColumn("Susu B", 0, null, mapOf("Toko" to "Toko B", "Terjual" to "1rb")),
            ),
        )
        assertEquals(listOf("Harga", "Ukuran", "Per satuan", "Toko", "Rating", "Terjual"), table.map { it.first })
        assertEquals(listOf("Rp 25.000", "–"), table[0].second)
        assertEquals(listOf("500 ml", "–"), table[1].second)
        assertEquals(listOf("Rp 5.000 / 100 ml", "–"), table[2].second)
        assertEquals(listOf("4.9", "–"), table[4].second)
    }
}
```

- [ ] **Step 2: Jalankan, pastikan gagal**

Run: `cd android && ./gradlew --no-daemon -p core test`
Expected: FAIL (compile error: `reminderTimes`, `todoBucket`, `activationLabel`, `ingredientKey`, … unresolved).

- [ ] **Step 3: Refaktor `Due.kt`**

Ganti seluruh isi `android/core/src/main/kotlin/com/snapbrain/core/Due.kt`:

```kotlin
package com.snapbrain.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class DueLabel(val text: String, val overdue: Boolean)

private val DAYS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

/** Local date plus optional time, as stored from the server; null for blank or malformed values. */
internal fun parseDue(due: String?): Pair<LocalDate, LocalTime?>? {
    val s = due?.trim().orEmpty()
    if (s.isEmpty()) return null
    return try {
        if (s.length > 10) LocalDateTime.parse(s).let { it.toLocalDate() to it.toLocalTime() } else LocalDate.parse(s) to null
    } catch (e: DateTimeParseException) {
        null
    }
}

/** A date-only due lasts all day. */
internal fun isOverdue(date: LocalDate, time: LocalTime?, now: LocalDateTime): Boolean =
    if (time == null) date.isBefore(now.toLocalDate()) else LocalDateTime.of(date, time).isBefore(now)

/** "Hari ini", "Besok 23:59", "Kam, 1 Okt". Null for blank or malformed values. */
fun dueLabel(due: String?, now: LocalDateTime): DueLabel? {
    val (date, time) = parseDue(due) ?: return null
    val today = now.toLocalDate()
    val day = when (date) {
        today -> "Hari ini"
        today.plusDays(1) -> "Besok"
        else -> "${DAYS[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
    }
    val text = if (time == null) day else "%s %02d:%02d".format(day, time.hour, time.minute)
    return DueLabel(text, isOverdue(date, time, now))
}
```

- [ ] **Step 4: Tulis `Reminders.kt`**

```kotlin
package com.snapbrain.core

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

enum class ReminderSlot { DAY_BEFORE, SAME_DAY, HOUR_BEFORE }

data class ReminderTime(val slot: ReminderSlot, val atMillis: Long)

private val MORNING = LocalTime.of(8, 0)

/**
 * Spec §7: a date-only due reminds at 08:00 the day before and 08:00 on the day; a due with a time reminds
 * one hour before. Times already past are dropped, so saving an old screenshot never fires a late alert.
 */
fun reminderTimes(due: String?, now: ZonedDateTime): List<ReminderTime> {
    val (date, time) = parseDue(due) ?: return emptyList()
    val local = if (time == null) {
        listOf(ReminderSlot.DAY_BEFORE to date.minusDays(1).atTime(MORNING), ReminderSlot.SAME_DAY to date.atTime(MORNING))
    } else {
        listOf(ReminderSlot.HOUR_BEFORE to LocalDateTime.of(date, time).minusHours(1))
    }
    val nowMillis = now.toInstant().toEpochMilli()
    return local.map { (slot, at) -> ReminderTime(slot, at.atZone(now.zone).toInstant().toEpochMilli()) }
        .filter { it.atMillis > nowMillis }
}

/** Notification title: "⏰ Bayar listrik — besok", "⏰ Kumpul laporan — jam 23:59". */
fun reminderTitle(text: String, due: String?, now: LocalDateTime): String {
    val (date, time) = parseDue(due) ?: return "⏰ $text"
    val today = now.toLocalDate()
    val whenText = when {
        time != null -> "jam %02d:%02d".format(time.hour, time.minute)
        date == today -> "hari ini"
        date == today.plusDays(1) -> "besok"
        else -> dueLabel(due, now)?.text.orEmpty()
    }
    return "⏰ $text — $whenText"
}
```

- [ ] **Step 5: Tulis `Todo.kt`**

```kotlin
package com.snapbrain.core

import java.time.LocalDateTime

enum class TodoBucket(val label: String) {
    TERLAMBAT("Terlambat"),
    HARI_INI("Hari ini"),
    MINGGU_INI("Minggu ini"),
    NANTI("Nanti"),
    TANPA("Tanpa tenggat"),
}

/** "Minggu ini" runs through today + 6. */
fun todoBucket(due: String?, now: LocalDateTime): TodoBucket {
    val (date, time) = parseDue(due) ?: return TodoBucket.TANPA
    val today = now.toLocalDate()
    return when {
        isOverdue(date, time, now) -> TodoBucket.TERLAMBAT
        date == today -> TodoBucket.HARI_INI
        !date.isAfter(today.plusDays(6)) -> TodoBucket.MINGGU_INI
        else -> TodoBucket.NANTI
    }
}

/** [bucket] is null for a group of undated steps from one screenshot. */
data class TodoGroup<T>(val title: String, val bucket: TodoBucket?, val rows: List<T>)

/**
 * Spec §8: dated rows go to their bucket (sorted by due), undated steps group under their screenshot
 * ([stepsTitle] non-null), and everything else lands in "Tanpa tenggat" at the end.
 */
fun <T> todoGroups(rows: List<T>, now: LocalDateTime, due: (T) -> String?, stepsTitle: (T) -> String?): List<TodoGroup<T>> {
    val byBucket = rows.groupBy { todoBucket(due(it), now) }
    val dated = listOf(TodoBucket.TERLAMBAT, TodoBucket.HARI_INI, TodoBucket.MINGGU_INI, TodoBucket.NANTI).mapNotNull { b ->
        byBucket[b]?.let { TodoGroup(b.label, b, it.sortedBy(due)) }
    }
    val undated = byBucket[TodoBucket.TANPA].orEmpty()
    val (steps, rest) = undated.partition { stepsTitle(it) != null }
    val stepGroups = steps.groupBy { stepsTitle(it)!! }.map { (title, r) -> TodoGroup(title, null, r) }
    val restGroup = if (rest.isEmpty()) emptyList() else listOf(TodoGroup(TodoBucket.TANPA.label, TodoBucket.TANPA, rest))
    return dated + stepGroups + restGroup
}
```

`sortedBy(due)`: ISO strings sort chronologically; a date-only due sorts before timed dues of the same day.

- [ ] **Step 6: Tulis `Activation.kt`**

```kotlin
package com.snapbrain.core

private val VERBS = mapOf(
    "masak" to "Masak sekarang",
    "beli" to "Mau beli",
    "kerjakan" to "Kerjakan",
    "bayar" to "Bayar",
    "ikut" to "Ikut acara",
    "coba" to "Coba sekarang",
)

/** masak/beli also put the screenshot's shopping rows in Belanja (spec §8). */
fun activatesBelanja(activation: String?): Boolean = activation == "masak" || activation == "beli"

/** Rows that can appear in To-do (spec §8). */
fun todoEligible(role: String, kind: String): Boolean = role == "todo" || role == "bawa" || kind == "steps"

/**
 * The hero button (spec §6.3, S7). [toBelanja]/[toTodo] say where this screenshot's rows would land; with
 * nowhere to land the button is hidden instead of promising a list that stays empty.
 */
fun activationLabel(activation: String?, active: Boolean, toBelanja: Boolean, toTodo: Boolean): String? {
    val verb = VERBS[activation] ?: return null
    if (!toBelanja && !toTodo) return null
    if (!active) return verb
    return "✓ Ada di " + listOfNotNull("Belanja".takeIf { toBelanja }, "To-do".takeIf { toTodo }).joinToString(" & ")
}
```

- [ ] **Step 7: Tulis `Belanja.kt`**

```kotlin
package com.snapbrain.core

import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val UNITS = setOf(
    "kg", "g", "gr", "gram", "ons", "ml", "l", "liter", "cc", "sdm", "sdt", "butir", "btr", "siung", "buah", "bh",
    "batang", "btg", "lembar", "lbr", "ruas", "genggam", "bungkus", "bks", "ikat", "sachet", "sct", "pcs", "biji",
    "potong", "ekor", "gelas", "cangkir", "mangkuk",
)
private val AMOUNT_WORDS = setOf("secukupnya", "sedikit", "sejumput", "segenggam", "setengah", "seperempat")
private val NUMBER = Regex("""^[\d½¼¾⅓⅔]+([.,/\-][\d½¼¾⅓⅔]+)*(kg|g|gr|ml|l|cc)?$""")
private val SPACES = Regex("""\s+""")

/**
 * Spec §8: "9 butir bawang merah" and "Bawang merah 5 siung" both become "bawang merah". A unit word is
 * dropped only right after a number or amount word, so "buah naga" stays whole.
 */
fun ingredientKey(text: String): String {
    val head = text.lowercase(Locale.ROOT).split(',', '(').first().trim().trimEnd('.', ':', ';')
    val kept = mutableListOf<String>()
    var afterAmount = false
    for (word in head.split(SPACES).filter { it.isNotEmpty() }) {
        val amount = NUMBER.matches(word) || word in AMOUNT_WORDS
        if (!amount && !(afterAmount && word in UNITS)) kept += word
        afterAmount = amount
    }
    return kept.joinToString(" ").ifEmpty { text.trim().lowercase(Locale.ROOT) }
}

data class IngredientGroup<T>(val name: String, val rows: List<T>)

/** Same-named rows sit side by side; their amounts are never added up (spec S12). */
fun <T> groupByIngredient(rows: List<T>, text: (T) -> String): List<IngredientGroup<T>> =
    rows.groupBy { ingredientKey(text(it)) }.map { (name, r) -> IngredientGroup(name, r) }

/** "Rp 734.000"; negative amounts (over budget) get a leading minus. */
fun rupiah(amount: Long): String {
    val digits = "%,d".format(Locale.US, abs(amount)).replace(',', '.')
    return if (amount < 0) "-Rp $digits" else "Rp $digits"
}

enum class SizeUnit(val per: String, val base: Double) { ML("100 ml", 100.0), G("100 g", 100.0), PCS("item", 1.0) }

data class PackSize(val amount: Double, val unit: SizeUnit)

private val SIZE = Regex("""(\d+(?:[.,]\d+)?)\s*(ml|liter|ltr|l|gram|gr|g|kg|pcs|pc|lembar|sachet|butir|buah)\b""")
private val ISI = Regex("""isi\s*(\d+)""")
private val THOUSANDS = Regex("""\d{1,3}\.\d{3}""")

/**
 * "500 ml", "1,5 L", "1.500 ml", "1kg", "isi 12", "10 sachet"; null when no known unit is found.
 * ponytail: "2x250ml" counts one 250 ml pack; parse the multiplier if multipacks show up in real screenshots.
 */
fun parseSize(size: String?): PackSize? {
    val s = size?.lowercase(Locale.ROOT) ?: return null
    ISI.find(s)?.let { return PackSize(it.groupValues[1].toDouble(), SizeUnit.PCS).takeIf { p -> p.amount > 0 } }
    val m = SIZE.find(s) ?: return null
    val raw = m.groupValues[1]
    val n = if (THOUSANDS.matches(raw)) raw.replace(".", "").toDouble() else raw.replace(',', '.').toDouble()
    val size = when (m.groupValues[2]) {
        "ml" -> PackSize(n, SizeUnit.ML)
        "l", "ltr", "liter" -> PackSize(n * 1000, SizeUnit.ML)
        "g", "gr", "gram" -> PackSize(n, SizeUnit.G)
        "kg" -> PackSize(n * 1000, SizeUnit.G)
        else -> PackSize(n, SizeUnit.PCS)
    }
    return size.takeIf { it.amount > 0 }
}

/** Spec §8 Bandingkan: per 100 ml, per 100 g, or per item; "–" without a price or a readable size. */
fun unitPriceText(price: Long, size: String?): String {
    val pack = parseSize(size) ?: return "–"
    if (price <= 0) return "–"
    return rupiah((price * pack.unit.base / pack.amount).roundToLong()) + " / " + pack.unit.per
}

data class BelanjaTotal(val count: Int, val sum: Long, val noPrice: Int)

/** Spec §8 "Total incaran": pass the prices of the rows still to buy. */
fun belanjaTotal(prices: List<Long>): BelanjaTotal =
    BelanjaTotal(prices.count { it > 0 }, prices.filter { it > 0 }.sum(), prices.count { it <= 0 })

data class Budget(val spent: Long, val left: Long, val planned: Long) {
    val over: Boolean get() = planned > left
}

fun budgetOf(monthly: Long, spent: Long, planned: Long): Budget = Budget(spent, monthly - spent, planned)

/** Start of the current month in the device zone; "Terbeli" counts rows checked since then. */
fun monthStartMillis(now: ZonedDateTime): Long =
    now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone).toInstant().toEpochMilli()

data class CompareColumn(val title: String, val price: Long, val size: String?, val info: Map<String, String>)

/** Rows of (label, one value per column): price, size and unit price first, then every info label any column has. */
fun compareTable(columns: List<CompareColumn>): List<Pair<String, List<String>>> {
    val fixed = listOf(
        "Harga" to columns.map { if (it.price > 0) rupiah(it.price) else "–" },
        "Ukuran" to columns.map { it.size?.ifBlank { null } ?: "–" },
        "Per satuan" to columns.map { unitPriceText(it.price, it.size) },
    )
    val labels = columns.flatMap { it.info.keys }.distinct()
    return fixed + labels.map { label -> label to columns.map { it.info[label] ?: "–" } }
}
```

- [ ] **Step 8: Tambah `IS_PRO` di akhir `Policy.kt`**

```kotlin

/** Spec S4/S17: Belanja, To-do, Bandingkan and Budget are Pro; everything stays open until Plan 3 adds billing. */
const val IS_PRO = true
```

- [ ] **Step 9: Jalankan test, pastikan lulus**

Run: `cd android && ./gradlew --no-daemon -p core test`
Expected: PASS, termasuk `DueTest` lama (refaktor tidak mengubah perilaku).

- [ ] **Step 10: Commit**

```bash
git add android/core/src
git commit -m "feat(core): reminder times, to-do buckets, activation labels, shopping aggregation"
```

Tidak perlu push/CI di task ini (app belum memakai fungsi baru; CI tetap hijau). Boleh push.

---

### Task 2: app — query gabungan, aktivasi, budget

**Files:**
- Modify (rewrite): `android/app/src/main/kotlin/com/snapbrain/app/data/ListItemDao.kt`
- Modify: `android/app/src/main/kotlin/com/snapbrain/app/data/ItemDao.kt`, `…/data/ItemRepository.kt`, `…/ui/Theme.kt`, `…/ui/DetailScreen.kt`

**Interfaces:**
- Consumes (Task 1): `activatesBelanja`, `todoEligible`, `activationLabel`, `CompareColumn`, `IS_PRO`.
- Produces (Task 3–5):
  - `data class SourcedRow(@Embedded val row: ListItemEntity, val itemTitle: String?)`
  - `ListItemDao`: `insertAll(rows): List<Long>`, `get(id: Long): ListItemEntity?`, `rowsFor(itemId: String): List<ListItemEntity>`, `sourced(id: Long): SourcedRow?`, `observeBelanja(): Flow<List<SourcedRow>>`, `observeTodo(): Flow<List<SourcedRow>>`, `observeSpent(since: Long): Flow<Long>`, `observeHasDue(): Flow<Boolean>`, `remindable(): List<ListItemEntity>`, `setChecked(ids: List<Long>, checked: Boolean, now: Long)`, `toggleRemind(id: Long)`, `addToBelanja(itemId)`, `removeFromBelanja(itemId)`, `finishShopping()`
  - `ItemDao.setActive(id: String, active: Boolean)`
  - `ItemRepository`: `budget: StateFlow<Long>`, `setBudget(amount: Long)`, `observeBelanja()`, `observeTodo()`, `observeSpent(since: Long)`, `suspend setActive(id: String, active: Boolean)`, `suspend setChecked(ids: List<Long>, checked: Boolean)`, `suspend finishShopping()`, `suspend compareColumns(ids: List<String>): List<CompareColumn>`
  - `Theme.kt`: `val strongButtonColor: Color @Composable get()`

- [ ] **Step 1: Tulis ulang `ListItemDao.kt`**

```kotlin
package com.snapbrain.app.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ItemProgress(val itemId: String, val total: Int, val done: Int, val nextDue: String?)

/** A list row plus the title of the screenshot it came from, for the Belanja and To-do tabs. */
data class SourcedRow(@Embedded val row: ListItemEntity, val itemTitle: String?)

@Dao
interface ListItemDao {
    @Query("SELECT * FROM list_item WHERE itemId = :itemId ORDER BY listIndex, position")
    fun observe(itemId: String): Flow<List<ListItemEntity>>

    /** Per-item progress and the earliest open due, for the Inbox cards. */
    @Query(
        """SELECT itemId, COUNT(*) AS total, SUM(checked) AS done,
                  MIN(CASE WHEN checked = 0 AND due IS NOT NULL AND due != '' THEN due END) AS nextDue
           FROM list_item GROUP BY itemId""",
    )
    fun observeProgress(): Flow<List<ItemProgress>>

    /** Spec §8 Belanja: rows put there by an activation, newest screenshot first. */
    @Query(
        """SELECT list_item.*, item.title AS itemTitle FROM list_item JOIN item ON item.id = list_item.itemId
           WHERE list_item.inBelanja = 1
           ORDER BY item.createdAt DESC, list_item.listIndex, list_item.position""",
    )
    fun observeBelanja(): Flow<List<SourcedRow>>

    /** Spec §8 To-do: to-do/bring/step rows of activated screenshots, plus any such row with a due. */
    @Query(
        """SELECT list_item.*, item.title AS itemTitle FROM list_item JOIN item ON item.id = list_item.itemId
           WHERE (list_item.role IN ('todo', 'bawa') OR list_item.kind = 'steps')
             AND (item.active = 1 OR (list_item.due IS NOT NULL AND list_item.due != ''))
           ORDER BY item.createdAt DESC, list_item.listIndex, list_item.position""",
    )
    fun observeTodo(): Flow<List<SourcedRow>>

    /** Spec §8 budget "Terbeli": shopping rows checked since [since]. */
    @Query("SELECT COALESCE(SUM(price), 0) FROM list_item WHERE role = 'belanja' AND checked = 1 AND checkedAt >= :since")
    fun observeSpent(since: Long): Flow<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM list_item WHERE checked = 0 AND remind = 1 AND due IS NOT NULL AND due != '')")
    fun observeHasDue(): Flow<Boolean>

    @Query("SELECT * FROM list_item WHERE checked = 0 AND remind = 1 AND due IS NOT NULL AND due != ''")
    suspend fun remindable(): List<ListItemEntity>

    @Query("SELECT * FROM list_item WHERE id = :id")
    suspend fun get(id: Long): ListItemEntity?

    @Query("SELECT * FROM list_item WHERE itemId = :itemId")
    suspend fun rowsFor(itemId: String): List<ListItemEntity>

    @Query("SELECT list_item.*, item.title AS itemTitle FROM list_item JOIN item ON item.id = list_item.itemId WHERE list_item.id = :id")
    suspend fun sourced(id: Long): SourcedRow?

    @Insert
    suspend fun insertAll(rows: List<ListItemEntity>): List<Long>

    @Query("DELETE FROM list_item WHERE itemId = :itemId")
    suspend fun deleteFor(itemId: String)

    @Query("UPDATE list_item SET checked = NOT checked, checkedAt = CASE WHEN checked = 0 THEN :now ELSE NULL END WHERE id = :id")
    suspend fun toggle(id: Long, now: Long)

    /** Rows already in the wanted state keep their original checkedAt. */
    @Query(
        """UPDATE list_item SET checked = :checked, checkedAt = CASE WHEN :checked THEN :now ELSE NULL END
           WHERE id IN (:ids) AND checked != :checked""",
    )
    suspend fun setChecked(ids: List<Long>, checked: Boolean, now: Long)

    @Query("UPDATE list_item SET remind = NOT remind WHERE id = :id")
    suspend fun toggleRemind(id: Long)

    @Query("UPDATE list_item SET inBelanja = 1 WHERE itemId = :itemId AND role = 'belanja'")
    suspend fun addToBelanja(itemId: String)

    /** Deactivating keeps checked rows in Belanja until "Selesai belanja" (spec §8). */
    @Query("UPDATE list_item SET inBelanja = 0 WHERE itemId = :itemId AND checked = 0")
    suspend fun removeFromBelanja(itemId: String)

    @Query("UPDATE list_item SET inBelanja = 0 WHERE inBelanja = 1 AND checked = 1")
    suspend fun finishShopping()
}
```

- [ ] **Step 2: Tambah `setActive` di `ItemDao.kt`** (sebelum `@Query("DELETE FROM item …")`):

```kotlin
    @Query("UPDATE item SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)
```

- [ ] **Step 3: `ItemRepository.kt`**

Tambah import:

```kotlin
import com.snapbrain.core.CompareColumn
import com.snapbrain.core.activatesBelanja
```

Di bawah `val quota: StateFlow<Quota?> = _quota`, tambah:

```kotlin
    private val _budget = MutableStateFlow(prefs.getLong(BUDGET, 0L))

    /** Monthly shopping budget in rupiah; 0 means not set (spec §8). */
    val budget: StateFlow<Long> = _budget
```

Di bawah `fun observeProgress() = lists.observeProgress()`, tambah:

```kotlin
    fun observeBelanja() = lists.observeBelanja()
    fun observeTodo() = lists.observeTodo()
    fun observeSpent(since: Long) = lists.observeSpent(since)
```

Di bawah `suspend fun toggleListItem(id: Long) = …`, tambah:

```kotlin
    suspend fun setChecked(ids: List<Long>, checked: Boolean) = lists.setChecked(ids, checked, System.currentTimeMillis())

    suspend fun finishShopping() = lists.finishShopping()

    fun setBudget(amount: Long) {
        prefs.edit().putLong(BUDGET, amount).apply()
        _budget.value = amount
    }

    /** Spec §8: activating masak/beli puts the shopping rows in Belanja; deactivating takes back the unchecked ones. */
    suspend fun setActive(id: String, active: Boolean) {
        val item = dao.get(id) ?: return
        db.withTransaction {
            dao.setActive(id, active)
            when {
                !active -> lists.removeFromBelanja(id)
                activatesBelanja(item.activation) -> lists.addToBelanja(id)
            }
        }
    }

    /** One column per screenshot: the first priced shopping row stands for the product. */
    suspend fun compareColumns(ids: List<String>): List<CompareColumn> = ids.mapNotNull { id ->
        val item = dao.get(id) ?: return@mapNotNull null
        val rows = lists.rowsFor(id).filter { it.role == "belanja" }
        val main = rows.firstOrNull { it.price > 0 } ?: rows.firstOrNull()
        CompareColumn(item.title ?: "Screenshot", main?.price ?: 0, main?.size, ExtractJson.decodeInfo(item.extractedInfo))
    }
```

Di `private companion object`, tambah `const val BUDGET = "budget_month"`.

- [ ] **Step 4: `Theme.kt` — tambah di bawah `soonColor`:**

```kotlin
/** Filled buttons: dark primary is a light blue meant for text, so filled buttons keep a darker blue under white text. */
val strongButtonColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4262E8) else MaterialTheme.colorScheme.primary
```

- [ ] **Step 5: `DetailScreen.kt` — tombol aktivasi**

Import tambahan:

```kotlin
import com.snapbrain.core.IS_PRO
import com.snapbrain.core.activatesBelanja
import com.snapbrain.core.activationLabel
import com.snapbrain.core.todoEligible
```

Di `DetailScreen`, setelah `val now = LocalDateTime.now()`:

```kotlin
    // Spec §6.3/S7: the button only shows when its rows have somewhere to land.
    val toBelanja = activatesBelanja(current.activation) && rows.any { it.role == "belanja" }
    val toTodo = rows.any { todoEligible(it.role, it.kind) }
    val activation = if (IS_PRO) activationLabel(current.activation, current.active, toBelanja, toTodo) else null
```

Ganti pemanggilan `HeroCard(...)`:

```kotlin
            item {
                HeroCard(
                    current, style, title, activation,
                    onActivate = { scope.launch { repository.setActive(id, !current.active) } },
                    onRetry = { scope.launch { repository.retry(id); ProcessWorker.enqueue(context.applicationContext) } },
                )
            }
```

Ganti fungsi `HeroCard` seluruhnya:

```kotlin
@Composable
private fun HeroCard(item: ItemEntity, style: CategoryStyle, title: String, activation: String?, onActivate: () -> Unit, onRetry: () -> Unit) {
    SnapCard(Modifier.fillMaxWidth(), color = style.tile) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(style.icon, contentDescription = null, tint = style.tint)
                }
                Spacer(Modifier.width(12.dp))
                Text(style.name.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = style.tint)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            statusText(item)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = statusColor(item)) }
            if (item.status == ItemStatus.FAILED.name) {
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White)) { Text("Coba lagi") }
            }
            if (activation != null) {
                val shape = RoundedCornerShape(16.dp)
                val modifier = Modifier.fillMaxWidth().height(52.dp)
                if (item.active) {
                    OutlinedButton(
                        onClick = onActivate,
                        modifier = modifier,
                        shape = shape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) { Text(activation, fontWeight = FontWeight.ExtraBold) }
                } else {
                    Button(
                        onClick = onActivate,
                        modifier = modifier,
                        shape = shape,
                        colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                    ) { Text(activation, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}
```

Hapus import `androidx.compose.foundation.isSystemInDarkTheme` dari `DetailScreen.kt` bila tidak dipakai lagi.

- [ ] **Step 6: `ItemRepository.saveResult` tetap compile**

`insertAll` kini mengembalikan `List<Long>`. Di `saveResult`, blok `db.withTransaction { … lists.insertAll(listRowsOf(done.id, d)) }` tetap valid (nilai dibuang). Tidak ada perubahan lain.

- [ ] **Step 7: Verifikasi CI**

Jalankan core test lokal (`./gradlew --no-daemon -p core test`), commit, push, lalu ikuti "Cara verifikasi" sampai run `android` untuk HEAD hijau.

```bash
git add android/app/src/main/kotlin/com/snapbrain/app/data android/app/src/main/kotlin/com/snapbrain/app/ui/Theme.kt android/app/src/main/kotlin/com/snapbrain/app/ui/DetailScreen.kt
git commit -m "feat(app): belanja/to-do queries, activation button, monthly budget"
git push -u origin claude/wizardly-dijkstra-4m9ayw
```

---

### Task 3: app — navigasi bawah + layar To-do

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/ui/TodoScreen.kt`
- Modify: `…/ui/MainActivity.kt`, `…/ui/Icons.kt`, `…/ui/Components.kt`

**Interfaces:**
- Consumes: `ItemRepository.observeTodo()`, `ItemRepository.toggleListItem(id: Long)`, `SourcedRow`, `todoGroups`, `TodoGroup`, `TodoBucket`, `dueLabel`, `IS_PRO`, `soonColor`, `SnapCard`.
- Produces (Task 4, 5):
  - `SnapIcons.Inbox`
  - `@Composable fun snapCheckboxColors(): CheckboxColors` (Components.kt)
  - `@Composable fun TodoScreen(repository: ItemRepository, onOpen: (String) -> Unit)`; baris To-do dirender oleh `private fun TodoRow(source: SourcedRow, now: LocalDateTime, onToggle: () -> Unit, onOpen: () -> Unit)`
  - `MainActivity`: `private enum class Tab(val label: String, val icon: ImageVector) { INBOX, TODO }`, state `tab`, `NavBar`.

- [ ] **Step 1: `Icons.kt` — tambah di dalam `object SnapIcons`:**

```kotlin
    val Inbox = icon("inbox", "M4 13h4l2 3h4l2 -3h4M5 5h14l1 8v6H4v-6z")
```

- [ ] **Step 2: `Components.kt` — tambah import `androidx.compose.material3.CheckboxColors` dan `androidx.compose.material3.CheckboxDefaults`, lalu di akhir file:**

```kotlin
@Composable
fun snapCheckboxColors(): CheckboxColors =
    CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary, checkmarkColor = MaterialTheme.colorScheme.onSecondary)
```

- [ ] **Step 3: Tulis `TodoScreen.kt`**

```kotlin
package com.snapbrain.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.data.SourcedRow
import com.snapbrain.core.TodoBucket
import com.snapbrain.core.TodoGroup
import com.snapbrain.core.dueLabel
import com.snapbrain.core.todoGroups
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Spec §8 To-do: every open task across screenshots, grouped by due. */
@Composable
fun TodoScreen(repository: ItemRepository, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val rows: List<SourcedRow>? by remember { repository.observeTodo() }.collectAsState(initial = null)
    var showDone by rememberSaveable { mutableStateOf(false) }
    val all = rows ?: return
    val now = remember(all) { LocalDateTime.now() }
    val visible = if (showDone) all else all.filter { !it.row.checked }
    val groups = todoGroups(
        visible,
        now,
        due = { it.row.due },
        stepsTitle = { if (it.row.kind == "steps") it.itemTitle ?: "Langkah" else null },
    )
    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${all.count { !it.row.checked }} tugas aktif",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("To-do", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                    TextButton(onClick = { showDone = !showDone }) {
                        Text(if (showDone) "Sembunyikan selesai" else "Tampilkan selesai", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        "Belum ada tugas. Tekan tombol di kartu screenshot (misalnya \"Kerjakan\" atau \"Masak sekarang\"), " +
                            "atau simpan screenshot yang punya tenggat.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 12.dp, end = 12.dp),
                    )
                }
            }
            groups.forEach { group ->
                item { GroupHeader(group) }
                item {
                    SnapCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            group.rows.forEach { source ->
                                TodoRow(
                                    source,
                                    now,
                                    onToggle = { scope.launch { repository.toggleListItem(source.row.id) } },
                                    onOpen = { onOpen(source.row.itemId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TodoGroup<SourcedRow>) {
    val color = when (group.bucket) {
        TodoBucket.TERLAMBAT -> MaterialTheme.colorScheme.error
        TodoBucket.HARI_INI -> soonColor
        TodoBucket.MINGGU_INI -> MaterialTheme.colorScheme.secondary
        null -> categoryStyle("reference").tint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            group.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text(
            if (group.bucket == null) "${group.rows.size} langkah" else "${group.rows.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun TodoRow(source: SourcedRow, now: LocalDateTime, onToggle: () -> Unit, onOpen: () -> Unit) {
    val row = source.row
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = row.checked, onCheckedChange = { onToggle() }, colors = snapCheckboxColors())
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 10.dp)) {
            Text(
                row.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (row.checked) TextDecoration.LineThrough else null,
            )
            Text(
                source.itemTitle ?: "Screenshot",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        dueLabel(row.due, now)?.let { due ->
            Text(
                due.text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (due.overdue) MaterialTheme.colorScheme.error else soonColor,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Tulis ulang `MainActivity.kt`**

```kotlin
package com.snapbrain.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.process.ProcessWorker
import com.snapbrain.core.IS_PRO

/** Spec §8 bottom navigation; Belanja joins in Task 5. */
private enum class Tab(val label: String, val icon: ImageVector) {
    INBOX("Inbox", SnapIcons.Inbox),
    TODO("To-do", SnapIcons.Task),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as SnapBrainApp).container.repository
        val freshStart = savedInstanceState == null
        setContent {
            SnapBrainTheme {
                // Recovery path for items stranded by a dismissed share sheet; skipped on rotation.
                if (freshStart) {
                    LaunchedEffect(Unit) {
                        repository.requeueQuotaBlocked()
                        ProcessWorker.enqueue(applicationContext)
                    }
                }
                var showSplash by rememberSaveable { mutableStateOf(freshStart) }
                var openId by rememberSaveable { mutableStateOf<String?>(null) }
                var tab by rememberSaveable { mutableStateOf(Tab.INBOX) }
                var query by rememberSaveable { mutableStateOf("") }
                var category by rememberSaveable { mutableStateOf<String?>(null) }
                val listState = rememberLazyListState()
                // Measured once; kept here so the Inbox does not draw a frame under the header after returning from Detail.
                val headerHeightPx = remember { mutableIntStateOf(0) }
                val id = openId
                when {
                    showSplash -> SplashScreen(onDone = { showSplash = false })
                    id != null -> DetailScreen(id, repository, onBack = { openId = null })
                    else -> {
                        BackHandler(enabled = tab != Tab.INBOX) { tab = Tab.INBOX }
                        Scaffold(bottomBar = { if (IS_PRO) NavBar(tab, onSelect = { tab = it }) }) { padding ->
                            // Each tab has its own Scaffold; consuming the insets here keeps them from padding twice.
                            Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                                when (tab) {
                                    Tab.INBOX -> InboxScreen(
                                        repository,
                                        query, { query = it },
                                        category, { category = it },
                                        listState,
                                        headerHeightPx,
                                        onOpen = { openId = it },
                                    )
                                    Tab.TODO -> TodoScreen(repository, onOpen = { openId = it })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Verifikasi CI, commit, push**

```bash
git add android/app/src/main/kotlin/com/snapbrain/app/ui
git commit -m "feat(app): bottom navigation and To-do tab"
git push -u origin claude/wizardly-dijkstra-4m9ayw
```

Ikuti "Cara verifikasi" sampai run `android` untuk HEAD hijau.

---

### Task 4: app — pengingat (Fase B)

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/reminder/ReminderScheduler.kt`, `…/reminder/ReminderWorker.kt`, `…/reminder/ReminderReceiver.kt`, `…/ui/Bell.kt`, `android/app/src/main/res/drawable/ic_stat_snapbrain.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`, `…/SnapBrainApp.kt`, `…/AppContainer.kt`, `…/data/ItemRepository.kt`, `…/ui/MainActivity.kt`, `…/ui/InboxScreen.kt`, `…/ui/DetailScreen.kt`, `…/ui/TodoScreen.kt`, `…/ui/Icons.kt`, `…/ui/Perform.kt`

**Interfaces:**
- Consumes: Task 1 `reminderTimes`, `reminderTitle`, `ReminderSlot`; Task 2 `ListItemDao.get/rowsFor/sourced/remindable/observeHasDue/toggleRemind/setChecked/insertAll(): List<Long>`, `SourcedRow`; Task 3 `MainActivity` (Tab, NavBar), `TodoRow`.
- Produces (Task 5):
  - `class ReminderScheduler(context: Context, prefs: SharedPreferences)`: `enabled: StateFlow<Boolean>`, `setEnabled(on: Boolean)`, `sync(row: ListItemEntity)`, `cancel(rowId: Long)`
  - `ItemRepository`: constructor param `reminders: ReminderScheduler`; `remindersEnabled: StateFlow<Boolean>`, `suspend setRemindersEnabled(on: Boolean)`, `observeHasDue()`, `suspend reminderRow(id: Long): SourcedRow?`, `suspend toggleRemind(id: Long)`; `toggleListItem`, `setChecked`, `discard`, `saveResult` sync/cancel reminders.
  - `ui/Bell.kt`: `val LocalNotificationsAllowed: ProvidableCompositionLocal<Boolean>`, `@Composable fun ReminderBell(remind: Boolean, globalOn: Boolean, onToggle: () -> Unit)`, `@Composable fun NotificationBanner(onDismiss: () -> Unit)`
  - `Perform.kt`: `fun Context.openNotificationSettings()`
  - `MainActivity.EXTRA_OPEN_ITEM = "open_item_id"`

- [ ] **Step 1: Ikon notifikasi** `android/app/src/main/res/drawable/ic_stat_snapbrain.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#00000000"
        android:pathData="M6,8a6,6 0,0 1,12 0c0,7 3,9 3,9H3s3,-2 3,-9M10,21h4"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:strokeWidth="2" />
</vector>
```

- [ ] **Step 2: `Icons.kt` — tambah di dalam `object SnapIcons`:**

```kotlin
    val Bell = icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3 -2 3 -9M10 21h4")
    val BellOff = icon("bell-off", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3 -2 3 -9M10 21h4M3 3l18 18")
```

- [ ] **Step 3: `reminder/ReminderScheduler.kt`**

```kotlin
package com.snapbrain.app.reminder

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.snapbrain.app.data.ListItemEntity
import com.snapbrain.core.ReminderSlot
import com.snapbrain.core.reminderTimes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Spec §7: one WorkManager job per list row and slot, so each is replaced or cancelled on its own.
 * WorkManager keeps them across reboots. Delivery may slip a few minutes in Doze; exact alarms are not used.
 */
class ReminderScheduler(context: Context, private val prefs: SharedPreferences) {
    private val appContext = context.applicationContext
    private val work by lazy { WorkManager.getInstance(appContext) }
    private val _enabled = MutableStateFlow(prefs.getBoolean(ENABLED, true))

    /** The global switch in the Inbox menu. */
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(ENABLED, on).apply()
        _enabled.value = on
        if (!on) work.cancelAllWorkByTag(TAG)
    }

    /** Brings the jobs for [row] in line with its current state: checked, bell off or switch off means none. */
    fun sync(row: ListItemEntity) {
        cancel(row.id)
        if (!_enabled.value || !row.remind || row.checked) return
        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()
        reminderTimes(row.due, now).forEach { time ->
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(time.atMillis - nowMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ReminderWorker.ROW_ID to row.id))
                .addTag(TAG)
                .build()
            work.enqueueUniqueWork(name(row.id, time.slot), ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun cancel(rowId: Long) {
        ReminderSlot.entries.forEach { work.cancelUniqueWork(name(rowId, it)) }
    }

    private fun name(rowId: Long, slot: ReminderSlot) = "reminder-$rowId-$slot"

    private companion object {
        const val TAG = "reminder"
        const val ENABLED = "reminders_enabled"
    }
}
```

- [ ] **Step 4: `reminder/ReminderWorker.kt`**

```kotlin
package com.snapbrain.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.snapbrain.app.R
import com.snapbrain.app.SnapBrainApp
import com.snapbrain.app.data.SourcedRow
import com.snapbrain.app.ui.MainActivity
import com.snapbrain.core.reminderTitle
import java.time.LocalDateTime

internal const val CHANNEL_ID = "reminders"
internal const val NOTIFICATION_TAG = "reminder"

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as SnapBrainApp).container.repository
        val source = repository.reminderRow(inputData.getLong(ROW_ID, -1)) ?: return Result.success()
        // Re-check at fire time: the row may have been checked or muted after scheduling.
        if (source.row.checked || !source.row.remind || !repository.remindersEnabled.value) return Result.success()
        post(applicationContext, source)
        return Result.success()
    }

    companion object {
        const val ROW_ID = "row_id"
    }
}

/** Spec §7: "⏰ <teks> — besok", "dari: <judul>", tap opens Detail, "Selesai" checks the row. */
private fun post(context: Context, source: SourcedRow) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (!manager.areNotificationsEnabled()) return
    val row = source.row
    val code = row.id.toInt()
    val open = PendingIntent.getActivity(
        context,
        code,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ITEM, row.itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val done = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE).putExtra(ReminderReceiver.ROW_ID, row.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_snapbrain)
        .setContentTitle(reminderTitle(row.text, row.due, LocalDateTime.now()))
        .setContentText("dari: ${source.itemTitle ?: "Screenshot"}")
        .setContentIntent(open)
        .setAutoCancel(true)
        .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_snapbrain), "Selesai", done).build())
        .build()
    try {
        manager.notify(NOTIFICATION_TAG, code, notification)
    } catch (e: SecurityException) {
        // Permission revoked between the check and the post; nothing to show.
    }
}
```

- [ ] **Step 5: `reminder/ReminderReceiver.kt`**

```kotlin
package com.snapbrain.app.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.snapbrain.app.SnapBrainApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The notification's "Selesai" button: checks the row (which also cancels its other slot) and clears the alert. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getLongExtra(ROW_ID, -1)
        val repository = (context.applicationContext as SnapBrainApp).container.repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.setChecked(listOf(id), true)
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_TAG, id.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.snapbrain.app.REMINDER_DONE"
        const val ROW_ID = "row_id"
    }
}
```

- [ ] **Step 6: Manifest**

Tambah setelah `SET_ALARM`:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Di `<activity android:name=".ui.MainActivity"`, tambah atribut `android:launchMode="singleTop"`.

Di dalam `<application>`, setelah activity `ShareActivity`:

```xml
        <receiver
            android:name=".reminder.ReminderReceiver"
            android:exported="false" />
```

- [ ] **Step 7: `SnapBrainApp.kt`**

```kotlin
package com.snapbrain.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.snapbrain.app.reminder.CHANNEL_ID

class SnapBrainApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installAppCheck()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Pengingat", NotificationManager.IMPORTANCE_DEFAULT))
        container = AppContainer(this)
    }
}
```

- [ ] **Step 8: `AppContainer.kt`**

```kotlin
package com.snapbrain.app

import android.content.Context
import android.provider.Settings
import com.snapbrain.app.data.AppDatabase
import com.snapbrain.app.data.ImageStore
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.process.ExtractClient
import com.snapbrain.app.process.OcrEngine
import com.snapbrain.app.reminder.ReminderScheduler
import com.snapbrain.core.deviceIdOf

class AppContainer(context: Context) {
    private val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    private val prefs = context.getSharedPreferences("snapbrain", Context.MODE_PRIVATE)

    val repository = ItemRepository(
        db = AppDatabase.create(context),
        images = ImageStore(context),
        ocr = OcrEngine(context),
        client = ExtractClient(deviceIdOf(androidId), BuildConfig.API_BASE_URL),
        prefs = prefs,
        reminders = ReminderScheduler(context, prefs),
    )
}
```

- [ ] **Step 9: `ItemRepository.kt` — hook pengingat**

Tambah import `com.snapbrain.app.reminder.ReminderScheduler`. Tambah parameter terakhir constructor:

```kotlin
    private val reminders: ReminderScheduler,
```

Tambah di bawah `val budget …`:

```kotlin
    /** Global reminder switch (spec §7). */
    val remindersEnabled: StateFlow<Boolean> = reminders.enabled
```

Tambah di bawah `fun observeSpent(…)`:

```kotlin
    fun observeHasDue() = lists.observeHasDue()

    suspend fun reminderRow(id: Long): SourcedRow? = lists.sourced(id)

    suspend fun setRemindersEnabled(on: Boolean) {
        reminders.setEnabled(on)
        if (on) lists.remindable().forEach(reminders::sync)
    }

    suspend fun toggleRemind(id: Long) {
        lists.toggleRemind(id)
        lists.get(id)?.let(reminders::sync)
    }
```

Ganti `saveResult` bagian transaksi (dari `db.withTransaction {` sampai `}`) dengan:

```kotlin
        val old = lists.rowsFor(done.id)
        val rows = listRowsOf(done.id, d)
        val ids = db.withTransaction {
            dao.update(done)
            lists.deleteFor(done.id)
            lists.insertAll(rows)
        }
        old.forEach { reminders.cancel(it.id) }
        rows.zip(ids).forEach { (row, rowId) -> reminders.sync(row.copy(id = rowId)) }
```

Ganti `toggleListItem` dan `setChecked`:

```kotlin
    suspend fun toggleListItem(id: Long) {
        lists.toggle(id, System.currentTimeMillis())
        lists.get(id)?.let(reminders::sync)
    }

    suspend fun setChecked(ids: List<Long>, checked: Boolean) {
        lists.setChecked(ids, checked, System.currentTimeMillis())
        ids.forEach { rowId -> lists.get(rowId)?.let(reminders::sync) }
    }
```

Di `discard`, sebelum `lists.deleteFor(id)`:

```kotlin
            lists.rowsFor(id).forEach { row -> reminders.cancel(row.id) }
```

Setelah langkah ini, setiap jalur yang mengubah `checked`, `remind`, `due`, atau menghapus baris memanggil `reminders.sync`/`cancel`: `saveResult`, `toggleListItem`, `setChecked` (dipakai To-do grup, Belanja, tombol "Selesai" notifikasi), `toggleRemind`, `discard`, `setRemindersEnabled`. `finishShopping` dan `setActive` tidak mengubah ketiganya.

- [ ] **Step 10: `Perform.kt` — tambah import `android.provider.Settings` dan fungsi publik (di atas `private fun view`):**

```kotlin
/** The system screen where the user can allow SnapBrain's notifications again. */
fun Context.openNotificationSettings() {
    launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}
```

- [ ] **Step 11: `ui/Bell.kt`**

```kotlin
package com.snapbrain.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** False when the system blocks SnapBrain's notifications; bells then show off and lead to settings (spec §7). */
val LocalNotificationsAllowed = staticCompositionLocalOf { true }

/** Per-row 🔔 (spec §7). Off when notifications are blocked, the global switch is off, or the row is muted. */
@Composable
fun ReminderBell(remind: Boolean, globalOn: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val allowed = LocalNotificationsAllowed.current
    val on = allowed && globalOn && remind
    IconButton(
        onClick = {
            when {
                !allowed -> context.openNotificationSettings()
                !globalOn -> Toast.makeText(context, "Pengingat sedang dimatikan. Nyalakan lewat menu ⋮ di Inbox.", Toast.LENGTH_LONG).show()
                else -> onToggle()
            }
        },
    ) {
        Icon(
            if (on) SnapIcons.Bell else SnapIcons.BellOff,
            contentDescription = if (on) "Pengingat nyala" else "Pengingat mati",
            tint = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Spec §7: asks for POST_NOTIFICATIONS with a short reason once there is something to remind about.
 * The first "Izinkan" shows the system dialog (Android 13+); after that, or on older Android, it opens settings.
 */
@Composable
fun NotificationBanner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SnapCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nyalakan notifikasi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Supaya SnapBrain bisa mengingatkan tenggat dari screenshot kamu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Nanti") }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !asked) {
                            asked = true
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.openNotificationSettings()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                ) { Text("Izinkan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
```

- [ ] **Step 12: `MainActivity.kt` — deep link, izin, banner**

Terapkan perubahan berikut pada versi Task 3:

Import tambahan:

```kotlin
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
```

Di dalam `class MainActivity`, sebelum `onCreate`:

```kotlin
    /** Item to open, set by a reminder notification (cold start or [onNewIntent]). */
    private val openRequest = mutableStateOf<String?>(null)
    private val notificationsAllowed = mutableStateOf(true)
```

Di `onCreate`, ganti baris `val freshStart = savedInstanceState == null` dengan:

```kotlin
        val freshStart = savedInstanceState == null
        if (freshStart) openRequest.value = intent.getStringExtra(EXTRA_OPEN_ITEM)
        val fromNotification = openRequest.value != null
```

Bungkus isi `SnapBrainTheme { … }` dengan `CompositionLocalProvider(LocalNotificationsAllowed provides notificationsAllowed.value) { … }`.

Ganti `var showSplash by rememberSaveable { mutableStateOf(freshStart) }` dengan:

```kotlin
                // Spec §6.5: splash on a launcher cold start only, not when a reminder opens the app.
                var showSplash by rememberSaveable { mutableStateOf(freshStart && !fromNotification) }
```

Setelah `val headerHeightPx = …`, tambah:

```kotlin
                val request = openRequest.value
                LaunchedEffect(request) {
                    if (request != null) {
                        openId = request
                        openRequest.value = null
                    }
                }
                val remindersOn by repository.remindersEnabled.collectAsState()
                val hasDue by remember { repository.observeHasDue() }.collectAsState(initial = false)
                var bannerDismissed by rememberSaveable { mutableStateOf(false) }
                val showBanner = !notificationsAllowed.value && remindersOn && hasDue && !bannerDismissed
```

Ganti `Scaffold(bottomBar = { if (IS_PRO) NavBar(tab, onSelect = { tab = it }) })` dengan:

```kotlin
                        Scaffold(
                            bottomBar = {
                                Column {
                                    if (showBanner) NotificationBanner(onDismiss = { bannerDismissed = true })
                                    if (IS_PRO) NavBar(tab, onSelect = { tab = it })
                                }
                            },
                        )
```

Setelah `onCreate`, tambah:

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_ITEM)?.let { openRequest.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Covers both the Android 13+ permission and notifications switched off in system settings.
        notificationsAllowed.value = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    companion object {
        const val EXTRA_OPEN_ITEM = "open_item_id"
    }
```

Catatan: saat notifikasi dibuka ketika Detail item lain sedang tampil, `openId` langsung berganti ke item baru (perilaku yang diharapkan).

- [ ] **Step 13: `InboxScreen.kt` — menu ⋮ sakelar pengingat**

Import tambahan:

```kotlin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

Di awal `InboxScreen` (setelah `val quota by …`):

```kotlin
    val scope = rememberCoroutineScope()
    val remindersOn by repository.remindersEnabled.collectAsState()
    var menu by remember { mutableStateOf(false) }
```

Di header `Row`, setelah blok `if (headerOffsetPx < …) { IconButton(…) }`, tambah:

```kotlin
                Box {
                    IconButton(onClick = { menu = true }) { Icon(SnapIcons.More, contentDescription = "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (remindersOn) "Matikan semua pengingat" else "Nyalakan pengingat") },
                            leadingIcon = { Icon(if (remindersOn) SnapIcons.BellOff else SnapIcons.Bell, contentDescription = null) },
                            onClick = {
                                menu = false
                                scope.launch { repository.setRemindersEnabled(!remindersOn) }
                            },
                        )
                    }
                }
```

- [ ] **Step 14: 🔔 di Detail dan To-do**

`DetailScreen.kt`:
- Di `DetailScreen`, setelah `val rows by …`: `val remindersOn by repository.remindersEnabled.collectAsState()`.
- `ListCard` mendapat parameter baru `remindersOn: Boolean` dan `onBell: (ListItemEntity) -> Unit`, diteruskan ke `ListRow`. Pemanggilan di `DetailScreen`:

```kotlin
                ListCard(
                    list,
                    now,
                    remindersOn,
                    onToggle = { row -> scope.launch { repository.toggleListItem(row.id) } },
                    onBell = { row -> scope.launch { repository.toggleRemind(row.id) } },
                    onCopy = { context.copyText(text) },
                    onShare = { context.shareText(text) },
                )
```

- Signature `ListCard(rows, now, remindersOn: Boolean, onToggle, onBell: (ListItemEntity) -> Unit, onCopy, onShare)`; di dalamnya `ListRow(row, if (steps) index + 1 else null, now, remindersOn, onToggle = { onToggle(row) }, onBell = { onBell(row) })`.
- `ListRow(row, number, now, remindersOn: Boolean, onToggle: () -> Unit, onBell: () -> Unit)`: ubah `Modifier.fillMaxWidth().toggleable(… onValueChange = { onToggle() })` tetap, dan di akhir Row (setelah chip timer) tambah:

```kotlin
        if (!row.due.isNullOrBlank()) ReminderBell(row.remind, remindersOn, onToggle = onBell)
```

`TodoScreen.kt`:
- Di `TodoScreen`: `val remindersOn by repository.remindersEnabled.collectAsState()`.
- `TodoRow` mendapat parameter `remindersOn: Boolean` dan `onBell: () -> Unit`; di akhir Row (setelah label tenggat):

```kotlin
        if (!row.due.isNullOrBlank()) ReminderBell(row.remind, remindersOn, onToggle = onBell)
```

- Pemanggilan: `TodoRow(source, now, remindersOn, onToggle = { … }, onBell = { scope.launch { repository.toggleRemind(source.row.id) } }, onOpen = { … })`.

- [ ] **Step 15: Verifikasi CI, commit, push**

Re-read diff: setiap jalur mutasi di Step 9 memanggil `sync`/`cancel`; `ReminderWorker` memeriksa ulang status saat berjalan.

```bash
git add android/app/src/main
git commit -m "feat(app): due-date reminders with per-item bell, global switch and Done action"
git push -u origin claude/wizardly-dijkstra-4m9ayw
```

Ikuti "Cara verifikasi" sampai run `android` untuk HEAD hijau.

---

### Task 5: app — layar Belanja, budget, Bandingkan, checklist tes

**Files:**
- Create: `android/app/src/main/kotlin/com/snapbrain/app/ui/BelanjaScreen.kt`
- Modify: `…/ui/MainActivity.kt`, `docs/manual-test-android.md`

**Interfaces:**
- Consumes: Task 1 `groupByIngredient`, `IngredientGroup`, `rupiah`, `belanjaTotal`, `BelanjaTotal`, `budgetOf`, `monthStartMillis`, `compareTable`, `ShareList`, `shareText`; Task 2 `observeBelanja`, `observeSpent`, `budget`, `setBudget`, `setChecked`, `finishShopping`, `compareColumns`, `SourcedRow`; Task 3 `snapCheckboxColors`, `Tab`; `ItemRepository.observe(query, category)`, `ItemStatus`.
- Produces: `@Composable fun BelanjaScreen(repository: ItemRepository, onOpen: (String) -> Unit)`; `Tab.BELANJA`.

- [ ] **Step 1: Tulis `BelanjaScreen.kt`**

```kotlin
package com.snapbrain.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.snapbrain.app.data.ItemEntity
import com.snapbrain.app.data.ItemRepository
import com.snapbrain.app.data.SourcedRow
import com.snapbrain.core.BelanjaTotal
import com.snapbrain.core.IngredientGroup
import com.snapbrain.core.ItemStatus
import com.snapbrain.core.ShareList
import com.snapbrain.core.belanjaTotal
import com.snapbrain.core.budgetOf
import com.snapbrain.core.compareTable
import com.snapbrain.core.groupByIngredient
import com.snapbrain.core.monthStartMillis
import com.snapbrain.core.rupiah
import com.snapbrain.core.shareText
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Spec §8 Belanja: shopping rows from every activated screenshot, with total, budget and price comparison. */
@Composable
fun BelanjaScreen(repository: ItemRepository, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows: List<SourcedRow>? by remember { repository.observeBelanja() }.collectAsState(initial = null)
    val monthStart = remember { monthStartMillis(ZonedDateTime.now()) }
    val spent by remember(monthStart) { repository.observeSpent(monthStart) }.collectAsState(initial = 0L)
    val budget by repository.budget.collectAsState()
    var byIngredient by rememberSaveable { mutableStateOf(true) }
    var hideChecked by rememberSaveable { mutableStateOf(false) }
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val all = rows ?: return
    val visible = if (hideChecked) all.filter { !it.row.checked } else all
    val total = belanjaTotal(all.filter { !it.row.checked }.map { it.row.price })
    val toggle: (List<SourcedRow>) -> Unit = { group ->
        val check = !group.all { it.row.checked }
        scope.launch { repository.setChecked(group.map { it.row.id }, check) }
    }

    if (editBudget) BudgetDialog(budget, onSave = { repository.setBudget(it); editBudget = false }, onDismiss = { editBudget = false })
    if (comparing) CompareDialog(repository, onClose = { comparing = false })

    Scaffold { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${all.size} barang dari ${all.distinctBy { it.row.itemId }.size} screenshot",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Belanja", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                    if (all.isNotEmpty()) {
                        IconButton(onClick = { context.shareText(belanjaShareText(all)) }) { Icon(SnapIcons.Share, contentDescription = "Bagikan daftar") }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(SnapIcons.More, contentDescription = "Menu belanja") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Bandingkan harga") }, onClick = { menu = false; comparing = true })
                            DropdownMenuItem(text = { Text("Atur budget bulanan") }, onClick = { menu = false; editBudget = true })
                        }
                    }
                }
            }
            item { SummaryCard(total, budget, spent, onSetBudget = { editBudget = true }) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = byIngredient, onClick = { byIngredient = true }, label = { Text("Per bahan", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(50))
                    FilterChip(selected = !byIngredient, onClick = { byIngredient = false }, label = { Text("Per asal", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(50))
                    FilterChip(selected = hideChecked, onClick = { hideChecked = !hideChecked }, label = { Text("Sembunyikan yang dicentang", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(50))
                }
            }
            if (all.isEmpty()) {
                item {
                    Text(
                        "Belanja masih kosong. Buka screenshot resep atau produk, lalu tekan \"Masak sekarang\" atau \"Mau beli\".",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 12.dp, end = 12.dp),
                    )
                }
            } else if (byIngredient) {
                item {
                    SnapCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            groupByIngredient(visible) { it.row.text }.forEach { group -> IngredientRow(group, onToggle = { toggle(group.rows) }) }
                        }
                    }
                }
            } else {
                items(visible.groupBy { it.row.itemId }.toList()) { (itemId, source) ->
                    SourceCard(source.first().itemTitle ?: "Screenshot", source, onOpen = { onOpen(itemId) }, onToggle = { toggle(listOf(it)) })
                }
            }
            if (all.any { it.row.checked }) {
                item {
                    Button(
                        onClick = { scope.launch { repository.finishShopping() } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                    ) { Text("Selesai belanja", fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

/** One list per screenshot, with ☐/☑, for WhatsApp and friends. */
private fun belanjaShareText(rows: List<SourcedRow>): String = shareText(
    "Daftar belanja",
    emptyMap(),
    rows.groupBy { it.row.itemId }.values.map { r -> ShareList(r.first().itemTitle ?: "Screenshot", false, r.map { it.row.text to it.row.checked }) },
)

@Composable
private fun SummaryCard(total: BelanjaTotal, budget: Long, spent: Long, onSetBudget: () -> Unit) {
    SnapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total incaran", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${total.count} barang · ${rupiah(total.sum)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (total.noPrice > 0) {
                Text("${total.noPrice} tanpa harga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (budget > 0) {
                val b = budgetOf(budget, spent, total.sum)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SummaryLine("Budget bulan ini", rupiah(budget))
                SummaryLine("Terbeli", rupiah(b.spent))
                SummaryLine("Sisa", rupiah(b.left))
                if (b.over) {
                    Text(
                        "Incaran lebih ${rupiah(b.planned - b.left)} dari sisa budget",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onSetBudget) { Text("Ubah budget", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onSetBudget) { Text("Atur budget bulanan", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** One checkbox for every row of the same ingredient; the amounts stay listed apart (spec S12). */
@Composable
private fun IngredientRow(group: IngredientGroup<SourcedRow>, onToggle: () -> Unit) {
    val checked = group.rows.all { it.row.checked }
    val price = group.rows.filter { !it.row.checked }.sumOf { it.row.price }
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }), verticalAlignment = Alignment.Top) {
        Checkbox(checked = checked, onCheckedChange = null, colors = snapCheckboxColors(), modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                group.name.replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            group.rows.forEach { source ->
                Text(
                    "${source.row.text} · ${source.itemTitle ?: "Screenshot"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (price > 0) {
            Text(rupiah(price), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
        }
    }
}

@Composable
private fun SourceCard(title: String, rows: List<SourcedRow>, onOpen: () -> Unit, onToggle: (SourcedRow) -> Unit) {
    SnapCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 8.dp),
            )
            rows.forEach { source ->
                val row = source.row
                Row(
                    Modifier.fillMaxWidth().toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { onToggle(source) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = row.checked, onCheckedChange = null, colors = snapCheckboxColors(), modifier = Modifier.padding(12.dp))
                    Text(
                        row.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    if (row.price > 0) {
                        Text(rupiah(row.price), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetDialog(current: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget belanja bulanan") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(12) },
                prefix = { Text("Rp ") },
                supportingText = { Text("Kosongkan untuk mematikan budget.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.toLongOrNull() ?: 0L) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Spec §8 Bandingkan: pick 2–3 shopping screenshots, then see them side by side. */
@Composable
private fun CompareDialog(repository: ItemRepository, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val items: List<ItemEntity>? by remember { repository.observe("", "shopping") }.collectAsState(initial = null)
    var selected by remember { mutableStateOf(listOf<String>()) }
    var table by remember { mutableStateOf<Pair<List<String>, List<Pair<String, List<String>>>>?>(null) }
    val back: () -> Unit = { if (table != null) { table = null } else { onClose() } }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) { Icon(SnapIcons.Back, contentDescription = "Kembali") }
                    Text("Bandingkan harga", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                val shown = table
                if (shown == null) {
                    val choices = items.orEmpty().filter { it.status == ItemStatus.DONE.name }
                    Text(
                        if (choices.size < 2) "Butuh minimal 2 screenshot belanja (halaman produk atau keranjang)." else "Pilih 2–3 screenshot belanja.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        items(choices, key = { it.id }) { item ->
                            val on = item.id in selected
                            val enabled = on || selected.size < 3
                            Row(
                                Modifier.fillMaxWidth().toggleable(value = on, enabled = enabled, role = Role.Checkbox, onValueChange = {
                                    selected = if (on) selected - item.id else selected + item.id
                                }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = null, enabled = enabled, colors = snapCheckboxColors(), modifier = Modifier.padding(12.dp))
                                Text(item.title ?: "Screenshot", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val columns = repository.compareColumns(selected)
                                table = columns.map { it.title } to compareTable(columns)
                            }
                        },
                        enabled = selected.size in 2..3,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = strongButtonColor, contentColor = Color.White),
                    ) { Text("Bandingkan (${selected.size})", fontWeight = FontWeight.ExtraBold) }
                } else {
                    CompareTable(shown.first, shown.second)
                }
            }
        }
    }
}

@Composable
private fun CompareTable(titles: List<String>, rows: List<Pair<String, List<String>>>) {
    Column(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        TableRow("", titles, header = true)
        rows.forEach { (label, values) -> TableRow(label, values, header = false) }
    }
}

@Composable
private fun TableRow(label: String, values: List<String>, header: Boolean) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp))
        values.forEach { value ->
            Text(
                value,
                style = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.ExtraBold else FontWeight.SemiBold,
                modifier = Modifier.width(148.dp).padding(start = 8.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
```

- [ ] **Step 2: `MainActivity.kt` — tab Belanja**

Enum menjadi:

```kotlin
/** Spec §8 bottom navigation. */
private enum class Tab(val label: String, val icon: ImageVector) {
    INBOX("Inbox", SnapIcons.Inbox),
    BELANJA("Belanja", SnapIcons.Shopping),
    TODO("To-do", SnapIcons.Task),
}
```

Di `when (tab)`, tambah cabang:

```kotlin
                                    Tab.BELANJA -> BelanjaScreen(repository, onOpen = { openId = it })
```

- [ ] **Step 3: `docs/manual-test-android.md` — tambah di akhir file:**

```markdown

## Fase B (pengingat)

Siapkan: screenshot chat grup dengan tenggat "besok" dan satu dengan jam ("hari ini jam 21:00", minimal 2 jam dari sekarang).

1. **Izin:** di Android 13+, setelah screenshot bertenggat pertama diproses, kartu "Nyalakan notifikasi" muncul di atas navigasi bawah. "Izinkan" menampilkan dialog sistem. Bila ditolak, 🔔 di Detail tampil mati; tap 🔔 membuka setelan notifikasi app. "Nanti" menyembunyikan kartu.
2. **Jadwal:** tenggat jam 21:00 → notifikasi sekitar 20:00 (boleh telat beberapa menit): judul "⏰ <teks> — jam 21:00", isi "dari: <judul>". Tenggat besok tanpa jam → notifikasi besok 08:00 "— hari ini" (dan hari ini 08:00 "— besok" bila belum lewat).
   - Cepat: ubah jam HP maju melewati waktu pengingat, lalu tunggu 1–2 menit.
3. **Selesai:** tombol "Selesai" di notifikasi mencentang item (cek di Detail) dan menutup notifikasi. Tap notifikasi membuka Detail item itu tanpa splash.
4. **Batal:** centang item bertenggat, matikan 🔔 item, atau hapus screenshot-nya sebelum waktunya → notifikasi tidak muncul.
5. **Sakelar global:** Inbox ⋮ → "Matikan semua pengingat" → tidak ada notifikasi; 🔔 di Detail tampil mati dan tap menampilkan petunjuk. "Nyalakan pengingat" menjadwalkan ulang item bertenggat yang belum dicentang.
6. **Restart HP:** pengingat yang sudah dijadwalkan tetap muncul.

## Fase C (Belanja, To-do)

1. **Navigasi:** bar bawah Inbox · Belanja · To-do. Back dari Belanja/To-do kembali ke Inbox. Buka Detail dari Belanja/To-do lalu back → kembali ke tab asal.
2. **Aktivasi:**
   - resep → "Masak sekarang" → menjadi "✓ Ada di Belanja & To-do"; bahan muncul di Belanja, langkah di To-do di bawah judul resep;
   - produk/keranjang → "Mau beli" → "✓ Ada di Belanja";
   - chat tugas → "Kerjakan" → "✓ Ada di To-do";
   - tap lagi → item yang belum dicentang keluar dari Belanja/To-do;
   - tagihan tanpa daftar tugas: tombol tidak tampil.
3. **Belanja per bahan:** dua resep yang sama-sama memakai bawang merah tampil satu baris "Bawang merah" dengan dua sub-baris (jumlah tidak dijumlahkan). Satu centang mencentang keduanya. "Per asal" mengelompokkan per screenshot; tap judul membuka Detail.
4. **Belanja tombol:** "Sembunyikan yang dicentang", Bagikan (teks ☐/☑ per screenshot), "Selesai belanja" mengeluarkan item yang dicentang (di Detail tetap tercentang).
5. **Total & budget:** "N barang · Rp X" hanya dari item belum dicentang yang ada harganya, "N tanpa harga" untuk sisanya. Atur budget (menu ⋮ atau tombol "Atur budget bulanan") → tampil Budget, Terbeli, Sisa. Centang produk berharga → Terbeli naik. Incaran > Sisa → peringatan merah. Kosongkan budget → bagian budget hilang.
6. **Bandingkan:** menu ⋮ → "Bandingkan harga" → pilih 2–3 screenshot produk (pilihan ke-4 tidak bisa) → tabel Harga, Ukuran, Per satuan ("Rp …/100 ml", "/100 g", "/item", atau "–"), lalu label info lain. Back kembali ke pilihan, back lagi menutup.
7. **To-do:** grup Terlambat · Hari ini · Minggu ini · Nanti · Tanpa tenggat, langkah resep di grup judul resep. Centang dari To-do tersimpan di Detail. "Tampilkan selesai" memunculkan item tercentang. 🔔 per item bertenggat.
8. **Mode gelap:** Belanja, To-do, dialog budget, Bandingkan, dan navigasi bawah tetap terbaca.
```

- [ ] **Step 4: Verifikasi CI, commit, push**

```bash
git add android/app/src/main/kotlin/com/snapbrain/app/ui docs/manual-test-android.md
git commit -m "feat(app): Belanja tab with totals, monthly budget and price comparison"
git push -u origin claude/wizardly-dijkstra-4m9ayw
```

Ikuti "Cara verifikasi" sampai run `android` untuk HEAD hijau.
