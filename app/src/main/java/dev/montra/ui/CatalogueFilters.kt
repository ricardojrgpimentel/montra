package dev.montra.ui

import dev.montra.data.model.IndexApp
import dev.montra.data.model.RequirementFilter

fun IndexApp.matchesCatalogueFilters(
    category: String?,
    filter: AppFilter?,
    requirement: RequirementFilter?,
): Boolean = (category == null || category in categories) &&
    when (filter) {
        AppFilter.OFF_PLAY -> playStore?.present == false
        AppFilter.RESTRICTED -> hasRestrictedLicense()
        null -> true
    } && (requirement == null || requirement.matches(this))

fun UiState.filteredRows(): List<AppRow> = rows.filter {
    it.app.matchesCatalogueFilters(category, filter, requirement)
}
