# kotlinx-datetime-locale

Locale extensions for [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime).

> Early scaffold. The API is a placeholder while the project shape settles.

## Modules

- `core` (published as `kotlinx-datetime-locale`) — the library itself.

## Supported platforms

JVM, Android, JS (Node.js), Wasm (JS and WASI), and every Kotlin/Native target
published by kotlinx-datetime (iOS, macOS, watchOS, tvOS, Linux, Windows,
Android Native).

## Building

```sh
./gradlew build
```

Runs all compilations plus the tests that can execute on the host
(JVM, Android host tests, JS/Wasm on Node.js, macOS and Apple simulators).
