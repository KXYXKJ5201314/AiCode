package com.kongxin.aicode.feature.agent.domain.permission

/**
 * 判断一次工具调用是否「完全落在当前会话的工作区内部」。
 *
 * 用途：BUILD 模式下为常规开发操作去掉无谓的授权弹窗（见
 * [com.kongxin.aicode.feature.settings.data.repository.ToolSafetySettingsRepository.autoApproveInWorkspaceFlow]）。
 * 与 AUTO 模式的根本区别是**只放行工作区内部**：任何一处越出工作区（读 `/etc`、写 `~/.bashrc`、
 * `cd /tmp`、`rm /root/x`）都回到逐次询问，因此不存在「切了 AUTO 忘记切回来」的风险。
 *
 * 判定基于 AI 视角的**容器路径**：AI 始终把项目根当作 `~/workspace`（展开为 `/root/workspace`），
 * 无需了解工作区在宿主上的真实目录，故本类纯字符串运算、不碰文件系统。
 *
 * 保守原则：看不懂就不放行。含 `$()`、反引号、子 shell、未闭合引号的命令一律判为越界，
 * 交给原有的逐次询问流程。
 */
object WorkspaceScopeInspector {

    /** 工作区在容器内的根路径（PRoot 以 root 运行，`~` 即 `/root`）。 */
    const val WORKSPACE_ROOT = "/root/workspace"

    /** 容器内的家目录。 */
    private const val HOME_DIR = "/root"

    /** Agent 自己的配置目录（`/root/.aicode`）：属 Agent 配置，不算工作区内容，改动仍要询问。 */
    private const val AICODE_DIR = "/root/.aicode"

    /** 段首环境赋值形如 `NAME=value`。 */
    private val ENV_ASSIGN = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")

    /**
     * 会改变后续段工作目录的内建命令。它们的参数解析方式与其他程序不同
     * （`cd` 的 `-` 是「回上一个目录」而非 flag），单独处理。
     */
    private val CD_COMMANDS = setOf("cd", "pushd")

    /**
     * 以 stdin 读取脚本、内容完全不可知的解释器：不带脚本文件参数时命令正文不在命令行里，
     * 无法确认它会动哪里，按越界处理。
     */
    private val STDIN_INTERPRETERS = setOf("sh", "bash", "zsh", "dash", "ksh", "fish")

    /**
     * 天然会改写工作区之外系统状态的程序 → 其**会写系统的子命令**。
     *
     * 这些程序的参数常是普通名字（`apt-get install vim`），按路径解析会误判成「工作区内的
     * 相对路径」，实际却写 `/var/lib/...`、`/usr/...`。
     *
     * 按子命令区分而不是整程序排除：`npm run build`、`npm test`、`cargo build` 只动工作区内
     * 的产物，属正常开发，应当免打扰；`npm install -g`、`apt-get install` 才会写系统。
     * 值为空集表示该程序任何子命令都不放行（权限、挂载、开关机一类）。
     */
    private val SYSTEM_SUBCOMMANDS: Map<String, Set<String>> = mapOf(
        // 包管理器：安装/卸载/更新类子命令写系统
        "apk" to setOf("add", "del", "update", "upgrade", "fix"),
        "apt" to setOf("install", "remove", "purge", "update", "upgrade", "dist-upgrade", "autoremove"),
        "apt-get" to setOf("install", "remove", "purge", "update", "upgrade", "dist-upgrade", "autoremove"),
        "dpkg" to setOf("-i", "--install", "-r", "--remove", "-P", "--purge"),
        "dnf" to setOf("install", "remove", "update", "upgrade"),
        "yum" to setOf("install", "remove", "update", "upgrade"),
        "rpm" to setOf("-i", "-U", "-e", "--install", "--upgrade", "--erase"),
        "pacman" to setOf("-S", "-R", "-U", "-Syu"),
        "pip" to setOf("install", "uninstall"),
        "pip3" to setOf("install", "uninstall"),
        "poetry" to setOf("install", "add", "remove", "update"),
        "uv" to setOf("pip", "add", "remove", "sync"),
        "conda" to setOf("install", "remove", "update", "create"),
        "npm" to setOf("install", "i", "uninstall", "remove", "link", "update"),
        "npx" to setOf("--yes"),
        "yarn" to setOf("add", "remove", "global", "upgrade"),
        "pnpm" to setOf("add", "remove", "install", "i", "update"),
        "pnpx" to setOf("--yes"),
        "gem" to setOf("install", "uninstall", "update"),
        "bundle" to setOf("install", "update", "add"),
        "cargo" to setOf("install", "uninstall"),
        "rustup" to setOf("install", "uninstall", "update", "default", "toolchain"),
        // 权限与服务管理：任何子命令都不放行
        "systemctl" to emptySet(),
        "service" to emptySet(),
        "rc-service" to emptySet(),
        "update-rc.d" to emptySet(),
        "chown" to emptySet(),
        "chmod" to emptySet(),
        "chgrp" to emptySet(),
        "useradd" to emptySet(),
        "userdel" to emptySet(),
        "usermod" to emptySet(),
        "groupadd" to emptySet(),
        "passwd" to emptySet(),
        "su" to emptySet(),
        "sudo" to emptySet(),
        // 磁盘与内核
        "mount" to emptySet(),
        "umount" to emptySet(),
        "mkfs" to emptySet(),
        "fdisk" to emptySet(),
        "parted" to emptySet(),
        "swapon" to emptySet(),
        "swapoff" to emptySet(),
        "sysctl" to emptySet(),
        "insmod" to emptySet(),
        "rmmod" to emptySet(),
        "modprobe" to emptySet(),
        "kill" to emptySet(),
        "killall" to emptySet(),
        "pkill" to emptySet(),
        // 网络配置
        "iptables" to emptySet(),
        "ip6tables" to emptySet(),
        "nft" to emptySet(),
        "ifconfig" to emptySet(),
        "ip" to emptySet(),
        "route" to emptySet(),
        // 越界写系统状态
        "crontab" to emptySet(),
        "systemd-run" to emptySet(),
        "reboot" to emptySet(),
        "shutdown" to emptySet(),
        "halt" to emptySet(),
        "poweroff" to emptySet()
    )

    sealed interface Result {
        /** 完全落在工作区内部，可免打扰放行。 */
        data object Inside : Result

        /** 越出工作区或无法判定，需走常规授权流程；[reason] 仅用于日志排查。 */
        data class Outside(val reason: String) : Result
    }

    /**
     * 判定一条 shell 命令是否全程只在工作区内活动。
     *
     * 逐段检查：`cd` 的目标必须落在工作区内并更新后续段的当前目录；其余段的每个非 flag 参数
     * 都按「相对当前目录解析后的路径」校验。普通字面量参数（如 `git commit -m "fix"` 里的
     * `fix`）解析后仍落在当前目录下，不会误判。
     */
    fun inspectShellCommand(command: String): Result {
        val analysis = ShellCommandParser.analyze(command)
        if (!analysis.analyzable) {
            return Result.Outside("命令含无法静态判定的构造：${analysis.blockers.joinToString("、")}")
        }

        // 工作目录从工作区根开始：容器以 `-w /root/workspace` 启动，AI 的相对路径都以此为基准。
        var cwd = WORKSPACE_ROOT

        for (segment in analysis.segments) {
            val tokens = segment.dropWhile { ENV_ASSIGN.matches(it) }
            if (tokens.isEmpty()) continue
            val program = tokens.first()
            val args = tokens.drop(1)

            if (program in STDIN_INTERPRETERS && args.none { !it.startsWith("-") }) {
                return Result.Outside("$program 从标准输入读取脚本，内容不可知")
            }

            // 会写系统的子命令（`npm install -g`、`apt-get install`）：仍逐次询问。
            val systemSubs = SYSTEM_SUBCOMMANDS[program]
            if (systemSubs != null) {
                if (systemSubs.isEmpty()) return Result.Outside("$program 会改写工作区外的系统状态")
                val sub = args.firstOrNull { !it.startsWith("-") } ?: return Result.Outside("$program 缺少明确子命令")
                if (sub in systemSubs) return Result.Outside("$program $sub 会改写工作区外的系统状态")
            }

            if (program in CD_COMMANDS) {
                // `cd` 无参数回家目录、`cd -` 回上一个目录，都离开工作区语义范围。
                val target = args.firstOrNull { !it.startsWith("-") }
                if (target == null || target == "-") {
                    return Result.Outside("$program 切换目录但目标不是明确的工作区内路径")
                }
                val next = resolve(cwd, target)
                if (!isInside(next)) return Result.Outside("$program 到工作区外：$next")
                cwd = next
                continue
            }

            for (arg in args) {
                if (arg.startsWith("-")) continue // 选项与取值不当作路径
                if (arg.isEmpty()) continue
                val resolved = resolve(cwd, arg)
                if (!isInside(resolved)) {
                    return Result.Outside("参数 `$arg` 指向工作区外：$resolved")
                }
            }
        }
        return Result.Inside
    }

    /**
     * 判定单个文件路径参数是否落在工作区内（供 `readFile`/`writeFile`/`editFile` 等使用）。
     *
     * 相对路径按工作区根解析。含 `$`/反引号的路径可能被 shell 展开，无法静态确认，按越界处理。
     */
    fun inspectPath(path: String): Result {
        val raw = path.trim()
        if (raw.isEmpty()) return Result.Outside("路径为空")
        if (raw.contains('$') || raw.contains('`')) {
            return Result.Outside("路径含变量或命令替换，无法静态判定：$raw")
        }
        val resolved = resolve(WORKSPACE_ROOT, raw)
        return if (isInside(resolved)) Result.Inside else Result.Outside("路径在工作区外：$resolved")
    }

    /** 展开 `~`/`$HOME` 并做词法归一化（折叠 `/`、解析 `.` 与 `..`），返回绝对路径。 */
    private fun resolve(cwd: String, raw: String): String {
        val expanded = when {
            raw == "~" -> HOME_DIR
            raw.startsWith("~/") -> HOME_DIR + raw.drop(1)
            raw == "\$HOME" || raw == "\${HOME}" -> HOME_DIR
            raw.startsWith("\$HOME/") -> HOME_DIR + raw.drop("\$HOME".length)
            raw.startsWith("\${HOME}/") -> HOME_DIR + raw.drop("\${HOME}".length)
            raw.startsWith("/") -> raw
            else -> "$cwd/$raw"
        }
        return normalizeAbsolute(expanded)
    }

    private fun normalizeAbsolute(path: String): String {
        val parts = ArrayDeque<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return "/" + parts.joinToString("/")
    }

    /** 是否为工作区内部路径；`/root/.aicode` 属 Agent 配置，单独排除。 */
    private fun isInside(path: String): Boolean {
        if (path == AICODE_DIR || path.startsWith("$AICODE_DIR/")) return false
        return path == WORKSPACE_ROOT || path.startsWith("$WORKSPACE_ROOT/")
    }
}
