package com.aicode.feature.settings.data.local

import android.content.Context
import com.aicode.core.net.RepoDataFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val tags: List<String> = emptyList()
)

/**
 * 官方及自定义 provider 预设库：
 * 采用双轨机制：
 * 1. 启动时由后台异步预热拉取（[refreshFromNetworkIfStale]），写磁盘并更新内存 [cached]；
 * 2. 任何 UI 读取（[loadOfficial]）均同步从「内存 -> 磁盘已下载文件 -> 内置 assets」读取，
 *    绝对不发起网络请求，毫秒级直接返回。
 */
object ProviderPresetLibrary {
    const val PROVIDERS_ASSET_FILE_NAME = "providers.json"
    const val REMOTE_PROVIDERS_PATH = "data/providers.json"

    /**
     * 是否启用官方预置供应商列表。
     *
     * 本仓库已移除全部预置供应商（[PROVIDERS_ASSET_FILE_NAME] 为空数组），只保留「自定义
     * 供应商」一条路径。置 false 会同时切断三处来源，否则删掉 assets 也会被下面两条
     * 兜底覆盖回来：
     * 1. 启动时的远程预置拉取（[refreshFromNetworkIfStale]）；
     * 2. 磁盘缓存文件（[loadOfficial] 的第 1 步）——旧版本下载过的 providers.json
     *    会一直留在用户设备上，不跳过它的话老用户删不干净。
     */
    private const val PRESET_PROVIDERS_ENABLED = false

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<ProviderPreset>? = null

    @Volatile
    private var refreshAttemptedThisProcess = false

    /**
     * 仅供 App 启动阶段（AIEditorApp）在后台协程异步调用：
     * 走 RepoDataFetcher 测活拉取最新配置并持久化，失败完全静默。
     */
    suspend fun refreshFromNetworkIfStale(context: Context) = withContext(Dispatchers.IO) {
        if (!PRESET_PROVIDERS_ENABLED) return@withContext
        if (refreshAttemptedThisProcess) return@withContext
        refreshAttemptedThisProcess = true

        val fetcher = RepoDataFetcher(context)
        val remoteResult = runCatching { fetcher.fetch(REMOTE_PROVIDERS_PATH) }.getOrNull()
        val remoteContent = when (remoteResult) {
            is RepoDataFetcher.FetchResult.Success -> remoteResult.content
            is RepoDataFetcher.FetchResult.FallbackDiskCache -> remoteResult.content
            else -> null
        }

        if (!remoteContent.isNullOrBlank()) {
            val parsedRemote = runCatching {
                json.decodeFromString<List<ProviderPreset>>(remoteContent)
            }.getOrNull()
            if (!parsedRemote.isNullOrEmpty()) {
                val sorted = parsedRemote.sortedWith(
                    compareByDescending<ProviderPreset> { it.isRecommended }
                        .thenBy { it.name.lowercase() }
                )
                cached = sorted
            }
        }
    }

    /**
     * UI 专用快速同步读取：
     * 顺序：内存缓存 -> 磁盘下载文件 -> assets 内置文件。
     * 纯本地 IO，绝对不发任何网络请求，秒级返回。
     */
    fun loadOfficial(context: Context): List<ProviderPreset> {
        // 预置供应商已移除：直接返回空列表，跳过磁盘缓存与 assets。
        // 磁盘上可能还留着旧版本下载的 providers.json，必须一并跳过，否则删不干净。
        if (!PRESET_PROVIDERS_ENABLED) return emptyList()

        cached?.let { return it }

        // 1. 尝试直接读取本地磁盘缓存文件（由 RepoDataFetcher 写入）
        val diskContent = RepoDataFetcher(context).readLocalCache(REMOTE_PROVIDERS_PATH)
        if (!diskContent.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString<List<ProviderPreset>>(diskContent)
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                val sorted = parsed.sortedWith(
                    compareByDescending<ProviderPreset> { it.isRecommended }
                        .thenBy { it.name.lowercase() }
                )
                cached = sorted
                return sorted
            }
        }

        // 2. 兜底读取内置 assets（必定存在，必定瞬间返回）
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
