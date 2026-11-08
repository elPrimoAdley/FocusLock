# Walkthrough - Fix Compilation Error

I have fixed the compilation error that was preventing the app from launching.

## Changes Made

### [app]

#### [MainActivity.kt](file:///C:/Users/mbugu/Videos/FocusLock/FocusLock/app/src/main/java/com/example/focuslock/MainActivity.kt)

- Corrected the `Icon` usage in the `Header` composable.
- Replaced the invalid `size` parameter with `modifier = Modifier.size(28.dp)`.

```diff
-        IconButton(onClick = { /* Ouvrir les paramètres */ }) {
-            Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = ColorTextMuted, size = 28.dp)
-        }
+        IconButton(onClick = { /* Ouvrir les paramètres */ }) {
+            Icon(
+                Icons.Default.Settings,
+                contentDescription = "Paramètres",
+                modifier = Modifier.size(28.dp),
+                tint = ColorTextMuted
+            )
+        }
```

## Verification Results

### Automated Tests
- Executed `./gradlew assembleDebug`: **PASSED**
