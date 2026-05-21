# 👻 Ghost Android Benchmark Dashboard

This is the official testing laboratory for **Ghost Serialization** in Android environments. It serves as both a performance validation tool and a blueprint for production-grade integrations on Android.

**Ghost version:** `1.1.17` from [Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer) (`com.ghostserializer`). Clone and build — no local checkout of [ghost-serializer](https://github.com/juanchurtado1991/ghost-serializer) required.

**Related projects:**

| Project | Description |
|:---|:---|
| [ghost-serializer](https://github.com/juanchurtado1991/ghost-serializer) | Main library, KMP sample app, JVM benchmarks |
| [ghost-ios-test-app](https://github.com/juanchurtado1991/ghost-ios-test-app) | Native iOS benchmark vs Apple Codable (XCFramework bundled) |
| [ghost-spring-boot-test-app](https://github.com/juanchurtado1991/ghost-spring-boot-test-app) | Spring Boot benchmark vs Jackson |

---

## 🚀 How to Run the Benchmark

1. Clone this repository.
2. Open the project in Android Studio (Gradle resolves Ghost **1.1.17** from Maven Central).
3. Use a physical device or emulator (API 24+).
4. Select the `app` module and run **Run** (or `./gradlew :app:assembleDebug`).
5. Adjust the stress load (e.g. **20 pages**).
6. Press **Run Benchmark**.
7. Wait for **JIT Warmup (200×)** to finish.
8. Results appear in the performance dashboard (parse, write, network).

```bash
./gradlew :app:assembleDebug
```

---

## 📦 Using Ghost in your own Android project (Maven Central)

> **Coordinates:** Maven artifacts use `com.ghostserializer`. Kotlin imports use `com.ghost.serialization` (package namespace).

### Ghost artifacts (`1.1.17` on Maven Central)

| Artifact | Purpose |
|:---|:---|
| `com.ghostserializer:ghost-api` | Annotations (`@GhostSerialization`, etc.) |
| `com.ghostserializer:ghost-serialization` | Runtime engine |
| `com.ghostserializer:ghost-compiler` | KSP code generator |
| `com.ghostserializer:ghost-retrofit` | Retrofit converter (auto-injected when Retrofit is present) |
| `com.ghostserializer:ghost-ktor` | Ktor 2.x content negotiation |
| `com.ghostserializer.ghost` (Gradle plugin) | Auto-wires KSP + dependencies |

### Version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
ghost = "1.1.17"

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
```

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        google()
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
```

The Gradle plugin pulls `ghost-api`, `ghost-serialization`, and `ghost-compiler` from Maven Central. With Retrofit on the classpath, it also injects `ghost-retrofit`.

---

## 🛠️ Integration in this app

### 1. Gradle (Ghost plugin)

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.ghost)
}

ghost {
    version.set(libs.versions.ghost.get()) // 1.1.17
    autoInjectKtor.set(false) // Ktor 3: this app uses GhostKtor3Converter (see below)
}

ksp {
    arg("ghost.moduleName", "app")
}
```

On Android/JVM, registry discovery is automatic after code generation — call `Ghost.prewarm()` once at startup (see `RickAndMortyRepository`).

### 2. Retrofit

```kotlin
import com.ghost.serialization.retrofit.GhostConverterFactory
import retrofit2.Retrofit

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GhostConverterFactory.create())
    .build()
```

### 3. Ktor 2.x (official adapter)

```kotlin
import com.ghost.serialization.ktor.ghost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation

val client = HttpClient {
    install(ContentNegotiation) {
        ghost()
    }
}
```

### 4. Ktor 3.x (this benchmark app)

This repo targets **Ktor 3**. The published `ghost-ktor` artifact targets Ktor 2.x, so network benchmarks use an in-app **`GhostKtor3Converter`** with the same zero-copy design as the Retrofit path. See `app/src/main/kotlin/com/ghost/android/test/data/GhostKtor3Converter.kt`.

### 5. Models & API

```kotlin
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.Ghost

@GhostSerialization
data class User(val id: Int, val name: String)

val user: User = Ghost.deserialize(jsonString)
val bytes: ByteArray = Ghost.encodeToBytes(user)
```

Full library docs: [ghost-serializer README](https://github.com/juanchurtado1991/ghost-serializer#usage---android).

---

## 📊 Benchmark results — Native Android (20 pages, ×100)

> **Methodology:** 100 measured iterations after 200-iteration JIT warmup. Payload: Rick & Morty API, 20 pages merged. Network tests use **local replay** (MockEngine / fake responses) — converter overhead only. Memory: thread-local allocation per iteration (`ThreadMXBean`).

| Engine | Operation | Mode | Avg latency | Avg memory |
|:---|:---|:---|:---:|:---:|
| **Gson** | Network | Retrofit | 7.52 ms | 883 KB |
| **Moshi** | Network | Retrofit | 7.72 ms | 695 KB |
| **KSer** | Network | Ktorfit | 16.10 ms | 2438 KB |
| **Ghost** | Network | Retrofit | **5.70 ms** | **683 KB** |
| **Gson** | Read | String | 5.98 ms | 693 KB |
| **Moshi** | Read | String | 7.79 ms | 687 KB |
| **KSer** | Read | String | 9.71 ms | 628 KB |
| **Ghost** | Read | String | **4.70 ms** | **506 KB** |
| **Gson** | Read | Bytes | 6.42 ms | 1084 KB |
| **Moshi** | Read | Bytes | 8.19 ms | 1077 KB |
| **KSer** | Read | Bytes | 10.18 ms | 1019 KB |
| **Ghost** | Read | Bytes | **4.18 ms** | **303 KB** |
| **Gson** | Read | Stream | 6.45 ms | 714 KB |
| **Moshi** | Read | Stream | 6.12 ms | 685 KB |
| **KSer** | Read | Stream | 10.15 ms | 1020 KB |
| **Ghost** | Read | Stream | **4.42 ms** | **675 KB** |
| **Gson** | Write | String | 10.93 ms | 1694 KB |
| **Moshi** | Write | String | 9.61 ms | 1130 KB |
| **KSer** | Write | String | 4.92 ms | 447 KB |
| **Ghost** | Write | String | **4.13 ms** | **428 KB** |
| **Gson** | Write | Bytes | 9.13 ms | 1886 KB |
| **Moshi** | Write | Bytes | 8.61 ms | 1314 KB |
| **KSer** | Write | Bytes | 3.43 ms | 634 KB |
| **Ghost** | Write | Bytes | **2.22 ms** | **220 KB** |
| **Gson** | Write | Buffer | 10.35 ms | 1856 KB |
| **Moshi** | Write | Buffer | 8.59 ms | 544 KB |
| **Ghost** | Write | Buffer | **2.22 ms** | **191 KB** |

### Key takeaways

- Ghost wins every category vs Gson, Moshi, and kotlinx.serialization in this suite.
- Byte writes: **~80% less allocation** than Gson (220 KB vs 1886 KB) — less GC pressure and UI jank on real devices.
- Compile-time serializers play well with **R8/ProGuard** (no reflection-based keep rules for model graphs).

---

## 🏁 Android vs iOS (Ghost)

| Operation | Android Ghost | [iOS Ghost](https://github.com/juanchurtado1991/ghost-ios-test-app) | Notes |
|:---|:---:|:---:|:---|
| Parse String | 4.70 ms | 1.54 ms | ART vs Apple Silicon |
| Parse Bytes | 4.18 ms | 0.86 ms | |
| Write Bytes | 2.22 ms | 0.39 ms | |
| Network | 5.70 ms | 2.27 ms | |

Both platforms beat the platform-native serializer in their respective benchmark apps.

---

## Troubleshooting

**Plugin `1.1.17` not found:** Sonatype can show PUBLISHED before `repo.maven.apache.org` syncs. Verify the version is on Maven:

```bash
curl -s https://repo.maven.apache.org/maven2/com/ghostserializer/ghost/com.ghostserializer.ghost.gradle.plugin/maven-metadata.xml | grep 1.1.17
```

Then: `./gradlew --stop && ./gradlew :app:assembleDebug --refresh-dependencies`.

---

*Part of the [Ghost Serialization](https://github.com/juanchurtado1991/ghost-serializer) ecosystem.* 👻
