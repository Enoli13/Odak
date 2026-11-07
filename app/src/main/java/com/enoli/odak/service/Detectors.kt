package com.enoli.odak.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Kısa video ekranı algılama. Uygulamalar güncellendikçe görünüm kimlikleri
 * değişebilir; bu yüzden hem kimlik hem de "seçili sekme metni" kontrol edilir.
 * Bir şey kaçarsa önce buradaki listeleri güncelle.
 */
object Detectors {
    private const val IG = "com.instagram.android"
    private const val YT = "com.google.android.youtube"

    // Instagram: alt çubuktaki Reels ve Keşfet sekmeleri
    private val igSelectedTabIds = listOf("clips_tab", "search_tab")
    private val igSelectedTabTexts = listOf("Reels", "Keşfet", "Ara ve keşfet", "Search and explore", "Explore")

    // YouTube: Shorts oynatıcısı ve Shorts sekmesi
    private val ytScreenIds = listOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "shorts_container")
    private val ytSelectedTabTexts = listOf("Shorts")

    fun isShortVideo(pkg: String, root: AccessibilityNodeInfo): Boolean = when (pkg) {
        IG -> igSelectedTabIds.any { selectedById(root, pkg, it) } ||
            igSelectedTabTexts.any { selectedByText(root, it) }
        YT -> ytScreenIds.any { existsById(root, pkg, it) } ||
            ytSelectedTabTexts.any { selectedByText(root, it) }
        else -> true // bilinmeyen uygulamada "yalnızca kısa video" modu yok: tamamı sayılır
    }

    private fun existsById(root: AccessibilityNodeInfo, pkg: String, id: String): Boolean =
        try {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isVisibleToUser }
        } catch (_: Exception) {
            false
        }

    private fun selectedById(root: AccessibilityNodeInfo, pkg: String, id: String): Boolean =
        try {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isSelected }
        } catch (_: Exception) {
            false
        }

    private fun selectedByText(root: AccessibilityNodeInfo, text: String): Boolean =
        try {
            root.findAccessibilityNodeInfosByText(text).any { node ->
                val label = (node.contentDescription ?: node.text)?.toString()?.trim() ?: ""
                node.isSelected && label.equals(text, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
}
