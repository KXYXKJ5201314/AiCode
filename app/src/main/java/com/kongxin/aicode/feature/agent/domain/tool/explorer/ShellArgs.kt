package com.kongxin.aicode.feature.agent.domain.tool.explorer

/**
 * explorer 工具（search/list）共用的 shell 参数解析与安全白名单逻辑。
 *
 * 模型常把 `| head -30` 这类 shell 管道直接写进工具 args（如 `search(args="-n \"foo\" src | head -30")`）。
 * 这里把 `|` 从 rg/ls 参数中分离出来单独解析：**只放行 head 行数截断**，其余管道命令一律拒绝，
 * 防止借管道语法在容器里执行任意命令。
 */

/** 按 shell 词法把 args 拆成 tokens；引号未闭合返回 null。`\|`/`|` 解析为独立 token `|`，引号内的 `|` 是字面量。 */
fun parseShellWords(input: String): List<String>? {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var tokenStarted = false
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            quote == '\'' -> {
                if (c == '\'') quote = null else current.append(c)
                tokenStarted = true
            }
            quote == '"' -> {
                when (c) {
                    '"' -> quote = null
                    '\\' -> {
                        if (i + 1 < input.length) {
                            i++
                            current.append(input[i])
                        } else {
                            current.append(c)
                        }
                    }
                    else -> current.append(c)
                }
                tokenStarted = true
            }
            c.isWhitespace() -> {
                if (tokenStarted) {
                    result.add(current.toString())
                    current.clear()
                    tokenStarted = false
                }
            }
            c == '\'' || c == '"' -> {
                quote = c
                tokenStarted = true
            }
            c == '\\' -> {
                if (i + 1 < input.length) {
                    i++
                    current.append(input[i])
                } else {
                    current.append(c)
                }
                tokenStarted = true
            }
            else -> {
                current.append(c)
                tokenStarted = true
            }
        }
        i++
    }
    if (quote != null) return null
    if (tokenStarted) result.add(current.toString())
    return result
}

/**
 * 把 tokens 按独立的 `|` 拆为 (管道前的 tokens, 管道段列表)。
 * 无管道返回 (tokens, 空列表)；`|` 前没有任何参数返回 null。
 * 引号内（如 `"a|b"`）的 `|` 属于 token 本身，不会触发拆分。
 */
fun splitPipes(tokens: List<String>): Pair<List<String>, List<List<String>>>? {
    val pipeIndex = tokens.indexOf("|")
    if (pipeIndex < 0) return tokens to emptyList()
    val headTokens = tokens.subList(0, pipeIndex)
    if (headTokens.isEmpty()) return null
    val segments = mutableListOf<List<String>>()
    var i = pipeIndex
    while (i < tokens.size) {
        if (tokens[i] != "|") return null
        i++
        val segStart = i
        while (i < tokens.size && tokens[i] != "|") i++
        segments += tokens.subList(segStart, i)
    }
    return headTokens to segments
}

/**
 * head 管道段白名单：仅 `head`（默认 10 行）、`head -N`、`head -n N`，N 必须纯数字。
 * 返回该段允许输出的行数；命令不是 head、或带其它参数（含 `;`、重定向、`head -c` 等）返回 null。
 */
fun headLinesOf(seg: List<String>): Int? = when {
    seg.isEmpty() -> null
    seg[0] != "head" -> null
    seg.size == 1 -> 10
    seg.size == 2 -> {
        val n = seg[1]
        if (n.length > 1 && n[0] == '-' && n.drop(1).all(Char::isDigit)) n.drop(1).toInt() else null
    }
    seg.size == 3 && seg[1] == "-n" -> {
        val n = seg[2]
        if (n.isNotEmpty() && n.all(Char::isDigit)) n.toInt() else null
    }
    else -> null
}
/**
 * 组装传给容器的 rg 搜索命令：基础参数 + 全部参数 token（单引号转义、`~/` 展开为 [home]），
 * 若带管道则原样追加白名单校验过的 `| head ...` 段。管道段含非 head 命令/非法参数时返回 null。
 *
 * [home] 为命令执行环境的 home（本地 PRoot 容器为 /root，远程 SSH 为远程用户 home），
 * 用于展开 `~/` 参数——quote 后 shell 不会自动展开 `~`，故在构造命令时显式展开。
 */
fun buildSearchCommand(tokens: List<String>, home: String = "/root"): String? {
    val (rgTokens, pipeSegments) = splitPipes(tokens) ?: return null
    val args = mutableListOf(
        "rg",
        "--line-number",
        "--no-heading",
        "--with-filename",
        "--color",
        "never"
    )
    args.addAll(rgTokens.map { shellQuote(expandTilde(it, home)) })
    var command = args.joinToString(" ")
    for (seg in pipeSegments) {
        if (headLinesOf(seg) == null) return null
        command += " | " + seg.joinToString(" ")
    }
    return command
}

/** rg 中「本参数是搜索模式、不是路径」的短选项（其后的 token 仍是路径，故无需吞参数）。 */
private val RG_FLAG_TAKES_VALUE_NONE = setOf("-i", "-s", "-S", "-F", "-w", "-x", "-U", "-u", "-uu", "-uuu")

/** rg 中「下一个 token 是选项值」的短/长选项：这些值不是路径，跳过校验。 */
private val RG_FLAG_WITH_VALUE = setOf(
    "-e", "-f", "-g", "-t", "-T", "-m", "-A", "-B", "-C", "-j", "--max-count",
    "--glob", "--type", "--type-not", "--context", "--after-context", "--before-context",
    "--max-depth", "--max-filesize", "--threads", "--regexp", "--file", "--replace",
    "--encoding", "--sort", "--sortr", "--colors", "--label", "--engine"
)

/**
 * 校验 rg 参数里的路径 token 是否都落在工作区内，用于把 search 限制在当前工作区（禁止跨工作区搜索）。
 *
 * 判定规则（宽松但能挡住越界）：
 * - 选项与其取值不算路径；
 * - `-e`/`--regexp` 之后、直到 `--` 之前的内容算正则，不算路径；
 * - `--` 之后一律按路径处理；
 * - 其余非选项 token 视为路径。
 *
 * 路径合法性：只允许相对路径，或 [workspaceRoots] 内的绝对路径（含 `~/`、`$HOME/` 展开形式）。
 * 出现 `..` 逃逸、`/`、`/etc` 等越界路径即判为非法。
 *
 * @param workspaceRoots 允许的容器根路径集合（如 `~/workspace`、展开后的 `/root/workspace`）。
 * @return 第一个越界路径；全部合法返回 null。
 */
fun findOutOfWorkspacePath(
    tokens: List<String>,
    workspaceRoots: Collection<String>,
    home: String = "/root"
): String? {
    val normalizedRoots = workspaceRoots
        .asSequence()
        .map { expandTilde(it, home).trimEnd('/') }
        .filter { it.isNotEmpty() }
        .toList()
    if (normalizedRoots.isEmpty()) return null

    val (rgTokens, _) = splitPipes(tokens) ?: return null
    var patternMode = false
    var afterDoubleDash = false
    var skipNext = false
    for (token in rgTokens) {
        if (skipNext) {
            skipNext = false
            continue
        }
        if (token == "--") {
            afterDoubleDash = true
            continue
        }
        if (!afterDoubleDash && token.startsWith("-") && token != "-") {
            if (token == "-e" || token == "--regexp") {
                patternMode = true
                continue
            }
            if (token in RG_FLAG_WITH_VALUE || RG_FLAG_WITH_VALUE.any { token.startsWith("$it=") }) {
                if (!token.contains('=')) skipNext = true
                continue
            }
            if (token in RG_FLAG_TAKES_VALUE_NONE) {
                // 纯开关，但 rg 的 -e 模式在开启后仍会吃下一个位置参数作为 pattern；此处保守起见不改变 patternMode。
                continue
            }
            continue
        }
        // 位置参数：第一个（未进入 patternMode 时）是搜索模式，其余是路径
        if (!patternMode && !afterDoubleDash) {
            patternMode = true
            continue
        }
        if (!isPathInsideWorkspace(token, normalizedRoots, home)) return token
    }
    return null
}

/** [path] 是否落在 [roots] 任一目录内；相对路径视为工作区内。 */
private fun isPathInsideWorkspace(path: String, roots: List<String>, home: String): Boolean {
    val expanded = expandTilde(path, home).replace('\\', '/')
    // 相对路径：不含根、且不借 `..` 跳出
    if (!expanded.startsWith("/")) {
        val depth = expanded.split('/').fold(0) { acc, part ->
            when (part) {
                "", "." -> acc
                ".." -> acc - 1
                else -> acc + 1
            }
        }
        return depth >= 0
    }
    val normalized = normalizeLogicalPath(expanded)
    return roots.any { root -> normalized == root || normalized.startsWith("$root/") }
}

/** 逻辑上消除 `.`/`..` 段，保留前导 `/`（不触碰文件系统，纯字符串）。 */
private fun normalizeLogicalPath(path: String): String {
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

/** 把 `~`/`~/` 开头的路径参数展开为 [home] 路径，避免被单引号包裹后 shell 不展开 `~`。 */
fun expandTilde(arg: String, home: String = "/root"): String =
    when {
        arg == "~" -> home
        arg.startsWith("~/") -> home.trimEnd('/') + arg.removePrefix("~")
        else -> arg
    }

/** 单引号包裹，shell 命令安全。 */
fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}
