package com.aicode.feature.workspace.domain

import kotlinx.coroutines.sync.Mutex

/**
 * 工作区路径作用域的门闸：让「当前生效的工作区路径」在并发场景下不串。
 *
 * ### 为什么需要它
 *
 * 路径映射（[WorkspacePathMapper]）是单例，文件工具通过它把 AI 的容器路径解析到宿主持久化路径。
 * 「每个会话独立工作区」要求映射根随会话变化，但文件工具的方法签名里没有会话参数。
 * 折中方案：进入工具执行前设置作用域，结束后恢复。
 *
 * 裸用一个 `var` 会在并发时串台——多个会话同时跑 AI 时，A 会把 B 的工作区当成自己的。
 * 因此这里做两道保护：
 *
 * - **不同路径互斥**：持有 A 路径作用域期间，请求 B 路径的调用必须等待，避免互相覆盖。
 * - **相同路径共享**：同一会话派生的子代理、同一批并行工具都用同一路径，允许并发进入，
 *   否则会把本该并行的工具执行压成串行（明显变慢）。
 *
 * ### 为什么用引用计数而非递归锁
 *
 * [Mutex] 不可重入。同一工作区的嵌套作用域（如工具内部再调用另一个带作用域的方法）
 * 若直接 `lock()` 会自锁死。这里用「路径 + 计数 + 单把互斥锁」实现可重入：
 * 路径相同则计数加一直接返回，归零时才真正释放锁。
 *
 * ### 已知边界
 *
 * 这是**进程内、粗粒度**的门闸，不区分协程。若将来需要真正按会话隔离，
 * 应把工作区路径沿调用链显式传参，而不是依赖本类。
 */
internal class WorkspaceScopeGate {

    private val lock = Mutex()

    /** 当前持有作用域的路径；null 表示无人在用。受 [lock] 保护。 */
    private var currentPath: String? = null

    /** 当前路径的重入计数；归零时释放 [lock]。受 [lock] 保护。 */
    private var depth = 0

    /** 当前生效的路径（无人在用时为 null）。 */
    fun currentPath(): String? = currentPath

    /**
     * 在 [path] 作用域内执行 [block]；[path] 为空时直接执行，不建立作用域。
     */
    suspend fun <T> runWithPath(path: String?, block: suspend () -> T): T {
        if (path.isNullOrBlank()) return block()

        // 已是同一路径：重入，不再抢锁。
        if (currentPath == path) {
            depth++
            try {
                return block()
            } finally {
                depth--
            }
        }

        // 不同路径或无人在用：串行获取。
        lock.lock()
        val previousPath = currentPath
        val previousDepth = depth
        currentPath = path
        depth = 1
        try {
            return block()
        } finally {
            currentPath = previousPath
            depth = previousDepth
            lock.unlock()
        }
    }
}
