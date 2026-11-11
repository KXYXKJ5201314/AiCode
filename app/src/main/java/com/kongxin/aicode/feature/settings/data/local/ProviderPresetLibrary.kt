package com.kongxin.aicode.feature.settings.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 内置/动态 provider 的展示预设：包含 name/type/baseUrl 及推荐与推广扩展属性。 */
@Serializable
data class ProviderPreset(
    val id: String,
    val name: String,
    /** 协议类型：OPENAI / ANTHROPIC / GEMINI。 */
    val type: String,
    /** Base URL，选中后自动填充，可在编辑页修改。 */
    val baseUrl: String,
    /** 关联的模型 id 列表（可选，选中后导入为可用模型）。 */
    val models: List<String> = emptyList(),
    /** 是否推荐/置顶 */
    val isRecommended: Boolean = false,
    /** 徽标/标签文案（如「推荐」、「赞助」） */
    val badge: String? = null,
    /** 供应商官网或推广注册链接 */
    val websiteUrl: String? = null,
    /** 特性/福利标签列表（如 ["注册送10元"]） */
    val tags: List<String> = emptyList(),

    /** 备用接口地址，选中后一并填入「备用接口地址」输入框。 */
    val backupBaseUrls: List<String> = emptyList(),

    /** 是否默认勾选「按延迟自动选择」。 */
    val autoSelectEndpoint: Boolean = false
)

/**
 * provider 预设库：
 * 数据只有一处来源——`assets/providers.json`（内置），UI 读取（[loadOfficial]）同步返回，零网络请求。
 * 上游原有的「启动拉取远程 providers.json + 磁盘缓存」两条路已全部切掉，避免覆盖本仓库的预置。
 */
object ProviderPresetLibrary {
    const val PROVIDERS_ASSET_FILE_NAME = "providers.json"

    /**
     * 是否启用预置供应商列表。
     *
     * 本仓库的预置完全由 `assets/providers.json` 内置（目前只有 SunAPI 一条），
     * 不参与远程拉取，也不读磁盘缓存——上游 data/providers.json 是一整批官方供应商，
     * 一旦拉下来会把这里的预置覆盖掉。
     */
    private const val PRESET_PROVIDERS_ENABLED = true

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<ProviderPreset>? = null

    /**
     * 仅供 App 启动阶段（AIEditorApp）在后台协程异步调用。
     *
     * 现在预置列表完全内置，这里保留空实现只是为了不改动调用点。
     */
    suspend fun refreshFromNetworkIfStale(@Suppress("UNUSED_PARAMETER") context: Context) {
    }

    /**
     * UI 专用快速同步读取：
     * 顺序：内存缓存 -> 磁盘下载文件 -> assets 内置文件。
     * 纯本地 IO，绝对不发任何网络请求，秒级返回。
     */
    fun loadOfficial(context: Context): List<ProviderPreset> {
        if (!PRESET_PROVIDERS_ENABLED) return emptyList()
        cached?.let { return it }
        // 只读 assets：磁盘缓存里可能留着旧版本从上游拉下来的整批官方供应商，
        // 读它会把本仓库唯一的 SunAPI 预置顶掉。
        val fallback = loadFromAssets(context)
        cached = fallback
        return fallback
    }

    private fun loadFromAssets(context: Context): List<ProviderPreset> = runCatching {
        val text = context.assets.open(PROVIDERS_ASSET_FILE_NAME).bufferedReader().use { it.readText() }
        val list = json.decodeFromString<List<ProviderPreset>>(text)
        list.sortedWith(
            compareByDescending<ProviderPreset> { it.isRecommended }
                .thenBy { it.name.lowercase() }
        )
    }.getOrDefault(emptyList())
}
