package com.example.focuslock.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.focuslock.domain.AppCategory
import com.example.focuslock.domain.AppMetadata
import com.example.focuslock.domain.SecurityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SelectableAppInfo(
    val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.NEUTRAL
)

class InstalledAppsLoader(private val context: Context) {

    private val iconCache = object : LruCache<String, ImageBitmap>(160) {}

    @Volatile
    private var cachedBaseApps: List<Pair<String, String>>? = null

    /**
     * Retourne la liste triée des applications lançables hors FocusLock et hors liste blanche matérielle,
     * enrichie de leur catégorie issue de AppMetadataEntity (défaut NEUTRAL).
     */
    suspend fun loadSelectableApps(
        metadataMap: Map<String, AppMetadata>
    ): List<SelectableAppInfo> = withContext(Dispatchers.IO) {
        val baseList = cachedBaseApps ?: queryLauncherApps().also { cachedBaseApps = it }
        baseList.map { (pkg, label) ->
            SelectableAppInfo(
                packageName = pkg,
                label = label,
                category = metadataMap[pkg]?.category ?: AppCategory.NEUTRAL
            )
        }
    }

    /**
     * Charge et met en cache l'icône d'un package hors du thread principal.
     */
    suspend fun getAppIcon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(drawable, sizePx = 96).asImageBitmap()
                iconCache.put(packageName, bitmap)
                bitmap
            }.getOrNull()
        }
    }

    private fun queryLauncherApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val excludedPackages = SecurityState.DEFAULT_HARDWARE_WHITELIST + context.packageName

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg in excludedPackages) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                pkg to label
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}