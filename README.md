# 👻 Ghost Android Benchmark Dashboard

This is the official testing laboratory for **Ghost Serialization** in Android environments. It serves as both a performance validation tool and a blueprint for production-grade integrations on Android.

**Ghost version:** `1.2.0` from [Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer) (`com.ghostserializer`). Clone and build — no local checkout of [ghost-serializer](https://github.com/juanchurtado1991/ghost-serializer) required.

**Related projects:**

| Project | Description |
|:---|:---|
| [ghost-serializer](https://github.com/juanchurtado1991/ghost-serializer) | Main library, KMP sample app, JVM benchmarks |
| [ghost-ios-test-app](https://github.com/juanchurtado1991/ghost-ios-test-app) | Native iOS benchmark vs Apple Codable (XCFramework bundled) |
| [ghost-spring-boot-test-app](https://github.com/juanchurtado1991/ghost-spring-boot-test-app) | Spring Boot benchmark vs Jackson |

---

## 🚀 How to Run the Benchmark

1. Clone this repository.
2. Open the project in Android Studio (Gradle resolves Ghost **1.2.0** from Maven Central).
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

### Ghost artifacts (`1.2.0` on Maven Central)

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
ghost = "1.2.0"

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
    version.set(libs.versions.ghost.get()) // 1.2.0
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
import com.ghost.serialization.annotations.GhostStrict
import com.ghost.serialization.annotations.GhostCoerce
import retrofit2.Retrofit

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GhostConverterFactory.create())
    .build()

interface UserApi {
    // 1. Lenient (Default): Bypasses all bitwise comma validation for maximum par speed (same as 1.1.20)
    @GET("users/lenient")
    suspend fun getStandardUsers(): List<User>

    // 2. Strict Comma & Format Validation: Enforces correct comma placements and rejects trailing commas
    @GhostStrict
    @GET("users/strict")
    suspend fun getStrictUsers(): List<User>

    // 3. Coercion: Automatically parses stringified values (e.g. "42", "true") into primitive fields
    @GhostCoerce
    @GET("users/coerce")
    suspend fun getCoercedUsers(): List<User>
}
```

### 3. Ktor 2.x (official adapter)

```kotlin
import com.ghost.serialization.ktor.ghost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation

val client = HttpClient {
    install(ContentNegotiation) {
        ghost() // registers Ghost as the JSON engine (Default: lenient)
    }
}

// Or configure strict mode & coercion dynamically for your KMP client:
val strictClient = HttpClient {
    install(ContentNegotiation) {
        ghost { reader ->
            reader.strictMode = true
            reader.coerceStringsToNumbers = true
            reader.coerceBooleans = true
        }
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
| **Gson** | Network | Retrofit | 1.60 ms | 708 KB |
| **Moshi** | Network | Retrofit | 2.30 ms | 615 KB |
| **KSer** | Network | Ktorfit | 2.96 ms | 2455 KB |
| **Ghost** | Network | Retrofit | **1.18 ms** | **345 KB** |
| **Ghost** | Network | Ktorfit | **1.32 ms** | **1105 KB** |
| **Gson** | Read | String | 1.47 ms | 504 KB |
| **Moshi** | Read | String | 2.17 ms | 603 KB |
| **KSer** | Read | String | 1.78 ms | 614 KB |
| **Ghost** | Read | String | **1.20 ms** | **367 KB** |
| **Gson** | Read | Bytes | 1.80 ms | 902 KB |
| **Moshi** | Read | Bytes | 2.53 ms | 1004 KB |
| **KSer** | Read | Bytes | 2.13 ms | 1012 KB |
| **Ghost** | Read | Bytes | **0.79 ms** | **172 KB** |
| **Gson** | Read | Stream | 1.87 ms | 526 KB |
| **Moshi** | Read | Stream | 2.08 ms | 608 KB |
| **KSer** | Read | Stream | 2.28 ms | 1017 KB |
| **Ghost** | Read | Stream | **0.89 ms** | **340 KB** |
| **Gson** | Write | String | 2.62 ms | 1704 KB |
| **Moshi** | Write | String | 2.23 ms | 1119 KB |
| **KSer** | Write | String | 0.96 ms | 428 KB |
| **Ghost** | Write | String | **0.93 ms** | **421 KB** |
| **Gson** | Write | Bytes | 3.03 ms | 1890 KB |
| **Moshi** | Write | Bytes | 2.71 ms | 1306 KB |
| **KSer** | Write | Bytes | 1.36 ms | 628 KB |
| **Ghost** | Write | Bytes | **0.69 ms** | **218 KB** |
| **Gson** | Write | Buffer | 2.96 ms | 1858 KB |
| **Moshi** | Write | Buffer | 2.00 ms | 542 KB |
| **Ghost** | Write | Buffer | **0.67 ms** | **189 KB** |

### Key takeaways

- Ghost wins every category vs Gson, Moshi, and kotlinx.serialization in this suite.
- Byte writes: **~80% less allocation** than Gson (218 KB vs 1890 KB) — less GC pressure and UI jank on real devices.
- Compile-time serializers play well with **R8/ProGuard** (no reflection-based keep rules for model graphs).

---

## 🏁 Android vs iOS (Ghost)

| Operation | Android Ghost | [iOS Ghost](https://github.com/juanchurtado1991/ghost-ios-test-app) | Notes |
|:---|:---:|:---:|:---|
| Parse String | 1.20 ms | 1.54 ms | ART vs Apple Silicon |
| Parse Bytes | 0.79 ms | 0.86 ms | |
| Write Bytes | 0.69 ms | 0.39 ms | |
| Network | 1.18 ms | 2.27 ms | |

Both platforms beat the platform-native serializer in their respective benchmark apps.

---

## Troubleshooting

**Plugin `1.2.0` not found:** Sonatype can show PUBLISHED before `repo.maven.apache.org` syncs. Verify the version is on Maven:

```bash
curl -s https://repo.maven.apache.org/maven2/com/ghostserializer/ghost/com.ghostserializer.ghost.gradle.plugin/maven-metadata.xml | grep 1.2.0
```

Then: `./gradlew --stop && ./gradlew :app:assembleDebug --refresh-dependencies`.

---

*Part of the [Ghost Serialization](https://github.com/juanchurtado1991/ghost-serializer) ecosystem.* 👻
