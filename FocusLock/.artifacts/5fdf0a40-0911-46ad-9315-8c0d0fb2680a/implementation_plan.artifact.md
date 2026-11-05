# Implementation Plan - Fix Compilation Error in MainActivity

The app fails to build because of a compilation error in `MainActivity.kt`. Specifically, the `Icon` composable is being called with an invalid `size` parameter.

## Proposed Changes

### [app]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/mbugu/Videos/FocusLock/FocusLock/app/src/main/java/com/example/focuslock/MainActivity.kt)

- Replace `size = 28.dp` with `modifier = Modifier.size(28.dp)` in the `Icon` call within the `Header` function.

```diff
-            Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = ColorTextMuted, size = 28.dp)
+            Icon(Icons.Default.Settings, contentDescription = "Paramètres", modifier = Modifier.size(28.dp), tint = ColorTextMuted)
```

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify the project builds successfully.

### Manual Verification
- Deploy the app to a device or emulator to ensure it launches without crashing.
