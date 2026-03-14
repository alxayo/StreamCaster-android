---
applyTo: "**/*.kt"
description: "Use when writing or editing Kotlin files in the StreamCaster Android project. Covers Kotlin coding conventions, Android API branching, coroutine patterns, and state modeling."
---
# Kotlin & Android Conventions

## Language

- Kotlin only. No Java files.
- Use `data class` for value types, `sealed class`/`sealed interface` for state hierarchies, `enum class` for fixed sets.
- Prefer `StateFlow` over `LiveData` for reactive state.
- Use `kotlinx.coroutines` for all async work. Never use raw `Thread` or `AsyncTask`.

## Coroutine Patterns

- Scope streaming work to the service's `CoroutineScope` (cancelled in `onDestroy`).
- Use `Mutex` for serializing encoder operations — never `synchronized` blocks on coroutine code.
- Use `CompletableDeferred` for one-shot synchronization (e.g., surface readiness).

## API Branching

Always branch on `Build.VERSION.SDK_INT`, never on `Build.VERSION.RELEASE`:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // API 29+ path
} else {
    // API 23–28 fallback
}
```

## Null Safety

- Avoid `!!` except in tests. Prefer `?.let`, `?:`, or `requireNotNull` with a descriptive message.
- Repository methods returning nullable indicate "not found" — check the return, don't assert non-null.

## Naming

- Package: `com.port80.app.<layer>` (e.g., `data.model`, `service`, `ui.stream`).
- Sealed class variants: `StreamState.Live`, `StreamState.Reconnecting`.
- Constants: `companion object` with `const val` for primitive types, top-level `val` for complex types.
