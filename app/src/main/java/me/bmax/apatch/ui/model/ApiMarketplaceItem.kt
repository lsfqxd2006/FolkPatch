package me.bmax.apatch.ui.model

import java.util.Locale

/**
 * API Marketplace item data model
 * Represents a banner API source from the marketplace
 */
data class ApiMarketplaceItem(
    val name: String,
    val url: String,
    val description: String,
    val descriptionEn: String
) {
    /**
     * Get localized description based on current locale
     */
    fun getLocalizedDescription(): String {
        return if (Locale.getDefault().language == "zh") {
            description
        } else {
            descriptionEn
        }
    }
}
