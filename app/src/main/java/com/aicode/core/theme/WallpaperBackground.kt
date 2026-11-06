package com.aicode.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * 是否有全局背景图（壁纸）正在显示。
 *
 * 背景图绘制在所有页面内容**下方**，因此页面级容器的底色必须透出去，否则整块不透明
 * 底色会把壁纸完全盖住。各页面通过 [appPageBackground] 取底色即可自动适配，
 * 不需要各自去读设置仓库。
 */
val LocalWallpaperActive = staticCompositionLocalOf { false }

/**
 * 页面级容器的底色。
 *
 * - 没有背景图时返回主题 background，与改造前完全一致；
 * - 有背景图时返回 [Color.Transparent]，让下方的壁纸与它的遮罩透出来。
 *
 * 文字对比度由壁纸之上的主题色遮罩（见 `BackgroundSettingsRepository.scrimAlphaFor`）
 * 保证，不依赖页面底色，所以透明化不会让正文变得看不清。
 */
@Composable
fun appPageBackground(): Color =
    if (LocalWallpaperActive.current) Color.Transparent
    else MaterialTheme.colorScheme.background

/**
 * 当前背景图的不透明度（0f~1f）。未设置背景图时为 0f。
 *
 * [LocalWallpaperActive] 只回答「有没有壁纸」，无法表达「有多明显」。卡片、胶囊这类
 * 控件的底色需要**跟随滑块联动**（图越清楚、控件越透），因此这里单独暴露数值。
 */
val LocalWallpaperAlpha = staticCompositionLocalOf { 0f }

/** 背景图强度：0 = 完全淡出（等同无壁纸），1 = 滑块拉满。 */
@Composable
private fun wallpaperStrength(): Float = LocalWallpaperAlpha.current.coerceIn(0f, 1f)

/**
 * 卡片底色的透明度曲线：图很淡时接近原来的实色（0.93），图最清楚时降到 0.55。
 *
 * 保持「半透明」而不是全透明，是为了让可点区域仍然看得出边界，同时保证卡片上的
 * 文字有一层薄底可依，不至于直接压在图上去拼运气。
 */
private fun cardSurfaceAlphaFor(strength: Float): Float =
    0.93f + (0.55f - 0.93f) * strength

/**
 * 侧边栏卡片（会话行、入口分组、设置分组）的底色。
 *
 * 透明度**跟随背景图滑块联动**：图越明显，卡片越透（0.93 → 0.55）。
 * 未设置背景图时原样返回，与改造前完全一致。
 */
@Composable
fun appSidebarCardSurface(): Color {
    val base = MaterialTheme.semanticColors.cardSurface
    if (!LocalWallpaperActive.current) return base
    return base.copy(alpha = cardSurfaceAlphaFor(wallpaperStrength()))
}

/**
 * 侧边栏 / 设置页里的胶囊控件底色（搜索框、分段 Tab 轨道、Tab 滑块）。
 *
 * 同 [appSidebarCardSurface]，透明度跟随滑块联动，未设置背景图时原样返回。
 */
@Composable
fun appCapsuleSurface(base: Color): Color {
    if (!LocalWallpaperActive.current) return base
    return base.copy(alpha = cardSurfaceAlphaFor(wallpaperStrength()))
}

/**
 * 强调色底色（置顶会话行、选中项高亮）。
 *
 * 与卡片同样跟随滑块联动：图越明显高亮越透，但仍保留一部分颜色，让「这一条被置顶/
 * 选中」这件事看得出来。未设置背景图时原样返回。
 */
@Composable
fun appAccentSurface(base: Color): Color {
    if (!LocalWallpaperActive.current) return base
    val strength = wallpaperStrength()
    // 强调色需要比普通卡片更醒目，因此透明度下限给到 0.65。
    return base.copy(alpha = 0.95f + (0.65f - 0.95f) * strength)
}

