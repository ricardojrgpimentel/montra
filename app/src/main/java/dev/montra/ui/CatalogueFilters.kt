package dev.montra.ui

import dev.montra.data.model.IndexApp
import dev.montra.data.model.RequirementFilter

/**
 * Os filtros que dependem só da app: categoria, origem e licença.
 *
 * "Novidade", "instalada" e "guardada" não estão aqui porque não são propriedades da
 * app — dependem do relógio, do telemóvel e da pessoa. Para esses, esta função é uma
 * condição *necessária* e não suficiente: não os exclui, e quem decide é a linha, na
 * sobrecarga para [AppRow], que é a que a interface usa.
 */
fun IndexApp.matchesCatalogueFilters(
    category: String?,
    filter: AppFilter?,
    requirement: RequirementFilter?,
): Boolean = (category == null || category in categories) &&
    when (filter) {
        AppFilter.NEW, AppFilter.INSTALLED, AppFilter.SAVED -> true
        AppFilter.OFF_PLAY -> playStore?.present == false
        AppFilter.RESTRICTED -> hasRestrictedLicense()
        null -> true
    } && (requirement == null || requirement.matches(this))

/** A mesma pergunta, mas com a linha, que sabe o que a app sozinha não sabe. */
fun AppRow.matchesCatalogueFilters(
    category: String?,
    filter: AppFilter?,
    requirement: RequirementFilter?,
): Boolean {
    val decidedByTheRow = when (filter) {
        AppFilter.NEW -> isNew
        AppFilter.INSTALLED -> isInstalled
        AppFilter.SAVED -> saved
        else -> true
    }
    return decidedByTheRow && app.matchesCatalogueFilters(category, filter, requirement)
}

fun UiState.filteredRows(): List<AppRow> = rows.filter {
    it.matchesCatalogueFilters(category, filter, requirement)
}
