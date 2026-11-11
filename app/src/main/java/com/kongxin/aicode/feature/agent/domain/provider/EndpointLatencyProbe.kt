package com.kongxin.aicode.feature.agent.domain.provider

import com.kongxin.aicode.core.util.FileLogger
import com.kongxin.aicode.feature.settings.domain.model.AIProviderConfig
import com.kongxin.aicode.feature.settings.domain.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多接口地址的延迟探测与自动选路。
 *
 * 部分中转供应商会同时给出多个入口域名（主站 + CDN 加速站），不同网络环境下哪个更快并不固定。
 * 用户可以在提供商编辑页把备用入口填进「备用接口地址」并打开「按延迟自动选择」，
 * 之后每次发起请求前由本类实测各入口延迟，挑最快的那个作为本次 baseUrl。
 *
 * 设计要点：
 * 1. 只探测、不改配置：选出的地址仅用于本次运行期，不写回数据库，用户手填的 baseUrl 永远是主地址；
 * 2. 缓存：同一提供商 + 同一地址组合的结果缓存 [CACHE_TTL_MS]，避免每条消息都打一轮探测；
 * 3. 降级：全部失败时回退原 baseUrl，绝不因为选路问题让请求发不出去；
 * 4. 轻量：探测用 HEAD 请求打到模型列表端点，只计拿到响应头的耗时，读完即丢。
 */
@Singleton
class EndpointLatencyProbe @Inject constructor(
    private val client: OkHttpClient
) {
    private companion object {
        const val TAG = "EndpointLatencyProbe"

        /** 探测结果缓存有效期：5 分钟。 */
        const val CACHE_TTL_MS = 5 * 60 * 1000L

        /** 单次探测的总超时。 */
        const val PROBE_TIMEOUT_MS = 4_000L

        /** 单次探测失败后的重试次数（网络抖动时不要因一次失败就判定入口不可用）。 */
        const val PROBE_ATTEMPTS = 2
    }

    /** 一条探测结果。[latencyMs] 为 -1 表示不可用。 */
    data class ProbeResult(
        val baseUrl: String,
        val latencyMs: Long,
        val error: String? = null
    ) {
        val reachable: Boolean get() = latencyMs >= 0
    }

    private data class CacheEntry(val results: List<ProbeResult>, val at: Long) {
        fun fresh(now: Long): Boolean = now - at < CACHE_TTL_MS
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** 探测专用客户端：短超时，避免一个死掉的入口拖住整轮选路。 */
    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * 解析出本次请求应该使用的 baseUrl。
     *
     * 未开启自动选路、或没有备用地址时直接返回原值，不产生任何网络开销。
     */
    suspend fun resolveBaseUrl(config: AIProviderConfig): String {
        if (!config.autoSelectEndpoint) return config.baseUrl
        val candidates = config.endpointCandidates()
        if (candidates.size <= 1) return config.baseUrl
        val fastest = runCatching { fastestEndpoint(config, candidates) }.getOrNull()
        return fastest ?: config.baseUrl
    }

    /** 立即探测全部入口（供设置页测速按钮调用），忽略缓存。 */
    suspend fun probeNow(config: AIProviderConfig): List<ProbeResult> =
        probeAll(config, config.endpointCandidates(), useCache = false)

    private suspend fun fastestEndpoint(config: AIProviderConfig, candidates: List<String>): String? {
        val results = probeAll(config, candidates, useCache = true)
        val reachable = results.filter { it.reachable }.sortedBy { it.latencyMs }
        if (reachable.isEmpty()) {
            FileLogger.w(TAG, "provider=${config.id} 所有接口地址均不可用，回退主地址")
            return null
        }
        val best = reachable.first()
        if (best.baseUrl != config.baseUrl) {
            FileLogger.i(TAG, "provider=${config.id} 自动选路 -> ${best.baseUrl} (${best.latencyMs}ms)")
        }
        return best.baseUrl
    }

    private suspend fun probeAll(
        config: AIProviderConfig,
        candidates: List<String>,
        useCache: Boolean
    ): List<ProbeResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val key = "${config.id}|${config.type}|${candidates.joinToString(",")}"
        if (useCache) {
            cache[key]?.takeIf { it.fresh(now) }?.let { return@withContext it.results }
        }
        val results = candidates.map { probeOne(config, it) }
        cache[key] = CacheEntry(results, now)
        results
    }

    /** 探测单个地址：打到该供应商的模型列表端点，用拿到响应头的耗时作为延迟。 */
    private fun probeOne(config: AIProviderConfig, baseUrl: String): ProbeResult {
        val modelsPath = if (config.type == ProviderType.GEMINI) "v1beta/models" else "v1/models"
        val url = if (config.useFullUrl) baseUrl else joinUrl(baseUrl, modelsPath)
        val apiKey = config.firstUsableApiKey
        var lastError = ""
        for (attempt in 1..PROBE_ATTEMPTS) {
            val start = System.nanoTime()
            val outcome = runCatching {
                val builder = Request.Builder().url(url).head()
                if (apiKey.isNotBlank()) {
                    when (config.type) {
                        ProviderType.ANTHROPIC -> builder
                            .header("x-api-key", apiKey)
                            .header("anthropic-version", "2023-06-01")

                        ProviderType.GEMINI -> builder.header("x-goog-api-key", apiKey)
                        else -> builder.header("Authorization", "Bearer $apiKey")
                    }
                }
                probeClient.newCall(builder.build()).execute().use { it.code }
            }
            val latency = (System.nanoTime() - start) / 1_000_000
            outcome.fold(
                // 只要拿到 HTTP 响应就算这个入口通，哪怕是 401/404（说明网络链路本身没问题）。
                onSuccess = { return ProbeResult(baseUrl, latency) },
                onFailure = { t -> lastError = t.message ?: t.javaClass.simpleName }
            )
        }
        return ProbeResult(baseUrl, -1, lastError)
    }
}
