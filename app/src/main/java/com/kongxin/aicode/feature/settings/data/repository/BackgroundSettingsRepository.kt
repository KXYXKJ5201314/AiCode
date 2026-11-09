package com.kongxin.aicode.feature.settings.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kongxin.aicode.core.datastore.preferencesCorruptionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backgroundDataStore by preferencesDataStore(
    name = "background_prefs",
    corruptionHandler = preferencesCorruptionHandler
)

/**
 * 全局自定义背景图：图片文件拷贝到应用私有目录持久化（不依赖 content URI 授权），
 * 透明度与图片路径存 DataStore。路径为 null 表示未设置背景。
 *
 * 渲染方式为「壁纸」：图片绘制在所有页面内容**下方**，图片之上再叠一层主题色遮罩。
 * 因此透明度可以放到很高（图片清楚可见），文字对比度由遮罩保证，不受图片内容影响。
 */
@Singleton
class BackgroundSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_ALPHA = 0.35f
        /**
         * 壁纸模式下图片不透明度上限。
         *
         * 图片位于内容下方，不再遮挡文字，所以可以全不透明；让「滑块拉满 = 图片完全
         * 清晰可见」，用户想要多清楚都能给到位。
         */
        const val MAX_ALPHA = 1.0f
        /** 最小透明度：0 表示完全透明（背景不可见）。 */
        const val MIN_ALPHA = 0f
        private val IMAGE_PATH_KEY = stringPreferencesKey("background_image_path")
        private val ALPHA_KEY = floatPreferencesKey("background_alpha")

        /** 把 UI 百分比（0~100）线性映射为图片不透明度（0~MAX_ALPHA）。 */
        fun sliderToAlpha(percent: Float): Float =
            MAX_ALPHA * (percent / 100f).coerceIn(0f, 1f)

        /** 把实际不透明度反映射为 UI 百分比（0~100）。 */
        fun alphaToSlider(alpha: Float): Float =
            (alpha / MAX_ALPHA).coerceIn(0f, 1f) * 100f

        /**
         * 图片之上那层主题色遮罩的不透明度，随用户设定的图片不透明度自动反向调整。
         *
         * 曲线：`0.80 → MIN_SCRIM_ALPHA`，图片越清楚遮罩越薄。
         *
         * **为什么不再归零**：遮罩用的是主题 background 色（浅色主题=白、深色主题=黑），
         * 它是正文的对比度来源。之前让滑块拉满时遮罩归零，等于让主题深色文字直接压在
         * 图片原色上 —— 图片只要偏暗，文字立刻看不清，而改字色又会破坏没背景图时的
         * 一致性。因此这里保留 [MIN_SCRIM_ALPHA] 作为保底：拉满时图片依然最清楚，
         * 但始终垫着薄薄一层主题色，保证正文可读。
         */
        fun scrimAlphaFor(imageAlpha: Float): Float {
            val t = (imageAlpha / MAX_ALPHA).coerceIn(0f, 1f)
            val raw = 0.80f * (1f - t)
            return maxOf(raw, MIN_SCRIM_ALPHA)
        }

        /**
         * 遮罩保底不透明度。取 0.28：既让图片保持明显可见（图片本身 alpha 仍可达 1.0），
         * 又足以把正文的对比度拉回可读区间。
         */
        const val MIN_SCRIM_ALPHA = 0.28f
    }

    /** 当前背景图文件绝对路径；null 表示未设置。 */
    val imagePathFlow: Flow<String?> = context.backgroundDataStore.data.map { it[IMAGE_PATH_KEY] }

    /** 图片不透明度（0~MAX_ALPHA），图片位于内容下方，不遮挡文字。 */
    val alphaFlow: Flow<Float> = context.backgroundDataStore.data.map {
        (it[ALPHA_KEY] ?: DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA)
    }

    /**
     * 选择新背景图：把 [uri] 拷贝到私有目录 backgrounds/ 后替换旧图。
     * 先写新文件成功再更新 DataStore，失败时旧背景保持不变。
     */
    suspend fun setBackgroundImage(uri: Uri) {
        val extension = extensionFor(uri)
        val target = File(context.filesDir, "backgrounds/bg_${System.currentTimeMillis()}.$extension")
        target.parentFile?.mkdirs()
        val copied = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.length() > 0
            }.getOrDefault(false)
        }
        if (!copied) return
        val oldPath = context.backgroundDataStore.data.first()[IMAGE_PATH_KEY]
        context.backgroundDataStore.edit { it[IMAGE_PATH_KEY] = target.absolutePath }
        oldPath?.let { runCatching { File(it).delete() } }
    }

    /** 移除背景：删图片文件并清路径。 */
    suspend fun clearBackground() {
        val oldPath = context.backgroundDataStore.data.first()[IMAGE_PATH_KEY]
        context.backgroundDataStore.edit { it.remove(IMAGE_PATH_KEY) }
        oldPath?.let { runCatching { File(it).delete() } }
    }

    suspend fun setBackgroundAlpha(alpha: Float) {
        context.backgroundDataStore.edit {
            it[ALPHA_KEY] = alpha.coerceIn(MIN_ALPHA, MAX_ALPHA)
        }
    }

    private fun extensionFor(uri: Uri): String = when (context.contentResolver.getType(uri)) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}
