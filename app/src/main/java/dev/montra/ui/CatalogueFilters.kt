package dev.montra.ui

import dev.montra.data.model.IndexApp
import dev.montra.data.model.RequirementFilter

/**
 * Os filtros que dependem só da app: categoria, origem e licença.
 *
 * "Novidade" não está aqui porque não é uma propriedade da app — depende de quando ela
 * entrou no catálogo e de que horas são agora. Quem o sabe é a linha, e é por isso que
 * existe a sobrecarga para [AppRow], que é a que a interface usa.
 */
fun IndexApp.matchesCatalogueFilters(
    category: String?,
    filter: AppFilter?,
    requirement: RequirementFilter?,
): Boolean = (category == null || category in categories) &&
    when (filter) {
        // "Novidade" e "instalada" não são propriedades da app: dependem do relógio e
        // do telemóvel. Quem as sabe é a linha (ver a sobrecarga para [AppRow]).
        AppFilter.NEW, AppFilter.INSTALLED -> false
        AppFilter.OFF_PLAY -> playStore?.present == false
        AppFilter.RESTRICTED -> hasRestrictedLicense()
        null -> true
    } && (requirement == null || requirement.matches(this))

/** A mesma pergunta, mas com a linha, que sabe se a app é novidade ou já está cá. */
fun AppRow.matchesCatalogueFilters(
    category: String?,
    filter: AppFilter?,
    requirement: RequirementFilter?,
): Boolean = app.matchesCatalogueFilters(category, filter, requirement) &&
    when (filter) {
        AppFilter.NEW -> isNew
        AppFilter.INSTALLED -> isInstalled
        else -> true
    }

fun UiState.filteredRows(): List<AppRow> = rows.filter {
    it.matchesCatalogueFilters(category, filter, requirement)
}
