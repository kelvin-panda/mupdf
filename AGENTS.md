# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the library (debug)
./gradlew :mupdf:assembleDebug

# Build the library (release)
./gradlew :mupdf:assembleRelease

# Build the example app
./gradlew :example:assembleDebug

# Clean all build artifacts
./gradlew clean

# Publish library to local maven repository (for testing)
./gradlew :mupdf:publishReleasePublicationToMavenLocal
```

- Gradle 7.1.2, Android Gradle Plugin 7.1.2, Kotlin 1.6.0, Java 11
- compileSdk 32, minSdk 21, targetSdk 34 (example)
- No unit test suite configured (no test dependencies)

## Project Architecture

Android library project wrapping MuPDF for PDF document rendering with annotation, signature, screenshot, and watermark features. Published via JitPack as `com.github.kelvin-panda:mupdf:6.0.36`.

### Modules

- **`:mupdf`** — Android library module, the main deliverable. Contains all PDF rendering and UI code.
- **`:example`** — Sample app for integration testing and demonstration.

### Package Layout (`:mupdf/src/main/java/`)

| Package | Role |
|---|---|
| `com.artifex.mupdf.fitz.*` | Core MuPDF JNI bindings — wraps native C library (Document, Page, PDFAnnotation, Pixmap, etc.). Direct native method calls. |
| `com.artifex.mupdf.viewer.*` | PDF viewer infrastructure — `MuPDFCore` (rendering engine bridge), `ReaderView`/`PageView` (paging UI), `PageAdapter`, outline/search support. |
| `com.artifex.mupdf.annotation.*` | Annotation data model — `AnnotationArtBoard`, `AnnotationBean`. |
| `com.artifex.mupdf.util.*` | Shared utilities — screen utils, activity helpers, debug logging. |
| `com.xlk.mupdf.library` | **Public API surface** — `MupdfConfig` (Builder-pattern config), `MuPdfDocumentActivity` (main viewer Activity). |
| `com.xlk.mupdf.library.view.*` | Custom UI widgets — `MupdfColorPickerDialog`/`MupdfColorPickerView`, `SignatureBoard`, `ArtBoardDialog`, `ScalableView`. |
| `com.xlk.mupdf.library.bus.*` | EventBus message types — `MupdfEventMessage`, `MupdfBusType`, `MupdfAnnotationBean`, `MupdfInkBean`. |

### Key Dependencies

- **EventBus 3.3.1** — Inter-component communication within the viewer (annotation events, ink changes, UI state).
- **AndroidAutoSize v1.2.1** — Screen adaptation; `MuPdfDocumentActivity` implements `CancelAdapt` to opt out when host app uses this library.
- **AppCompat 1.4.1, Material 1.5.0** — Standard Android UI.

### Key Architecture Decisions

- **`MupdfConfig`** (Builder pattern) is the single configuration entry point. Every feature (annotation, signature, screenshot, watermark, WPS integration, clarity mode, fullscreen) is toggled via this object.
- **`MuPdfDocumentActivity`** is the main viewer Activity. It bundles MuPDF's native `DocumentActivity` with customization layers. Callers use `MuPdfDocumentActivity.jump(context, config)`.
- **Native .so libraries** for MuPDF rendering are bundled for `armeabi-v7a` and `arm64-v8a`. Consumers must handle ABI splits and `libc++_shared.so` conflicts (see README).
- **Inter-activity communication** uses EventBus, not Intents, for complex UI state (annotation positions, color selections, ink strokes).
- **Version 6.0.18+** added `CancelAdapt` interface implementation on `MuPdfDocumentActivity` to prevent UI scaling conflicts when the host app uses AndroidAutoSize.

### Publishing

- Release publishing via `maven-publish` plugin (see `mupdf/build.gradle` `publishing` block).
- JitPack CI configured in `jitpack.yml` (Java 11, OpenJDK).
- Version defined in `mupdf/build.gradle` `version = '6.0.36'`.
