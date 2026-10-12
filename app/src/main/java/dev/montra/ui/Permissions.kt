package dev.montra.ui

import android.content.Context
import android.content.pm.PermissionInfo

/**
 * O que uma app pede, dito pelo sistema.
 *
 * **Não há aqui uma lista de permissões perigosas.** Pergunta-se ao sistema, que sabe
 * o nível de proteção de cada permissão da plataforma (`PROTECTION_DANGEROUS`), e que
 * sabe dizê-las no idioma de quem está a ler. Uma lista mantida à mão envelhece — o
 * Android acrescenta permissões que pedem atenção em cada versão — e uma permissão que
 * deixasse de ser assinalada por esquecimento seria pior do que não assinalar nenhuma:
 * daria a uma app um atestado de inocência que ninguém verificou.
 *
 * As que o sistema não conhece — definidas pela própria app, ou por outra que não está
 * instalada — ficam sem nível, e não se inventa um.
 */
object Permissions {

    /**
     * @param label o nome que o sistema lhe dá, no idioma atual ("Câmara"). Sem isso,
     * o nome curto da permissão, que é feio mas é verdade.
     * @param description a frase do sistema ("tirar fotografias e gravar vídeos").
     */
    data class Line(
        val name: String,
        val label: String,
        val description: String?,
        val sensitive: Boolean,
    )

    /** As linhas a mostrar, com as que pedem atenção primeiro. */
    fun lines(context: Context, names: List<String>): List<Line> = names
        .map { name -> line(context, name) }
        .sortedWith(compareByDescending<Line> { it.sensitive }.thenBy { it.label.lowercase() })

    private fun line(context: Context, name: String): Line {
        val manager = context.packageManager
        val info = runCatching { manager.getPermissionInfo(name, 0) }.getOrNull()
        return Line(
            name = name,
            label = info?.loadLabel(manager)?.toString()?.takeIf { it.isNotBlank() }
                ?: name.substringAfterLast('.'),
            description = info?.loadDescription(manager)?.toString()?.takeIf { it.isNotBlank() },
            sensitive = info != null && isAlarming(info, name),
        )
    }

    /**
     * A única exceção à regra "o sistema é que sabe".
     *
     * `POST_NOTIFICATIONS` é mesmo uma permissão de runtime — o sistema chama-lhe
     * perigosa e não está errado. Mas não chega a dados nenhuns, e assinalá-la punha a
     * nota âmbar em quase todas as apps: a nota passaria a afirmar de "mostrar
     * notificações" o mesmo que afirma da câmara, e uma cor que aparece sempre deixa de
     * dizer o que quer que seja — é o que o `DESIGN.md` diz sobre as secções todas
     * coloridas. É a única exceção, e é por isso que está escrita em vez de escondida.
     */
    private const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"

    private fun isAlarming(info: PermissionInfo, name: String): Boolean =
        name != NOTIFICATION_PERMISSION && isDangerous(info)

    /**
     * O nível base da permissão vive em `protectionLevel`, um campo que existe desde a
     * API 1. O `protectionFlags` da API 28 tem **só** os sinalizadores adicionais — o
     * nome engana, e foi por isso que a primeira versão disto não assinalava a câmara.
     * O método `getProtection()` também só existe a partir da API 28, e em Kotlin
     * `info.protection` chama o método, não o campo.
     */
    private fun isDangerous(info: PermissionInfo): Boolean {
        @Suppress("DEPRECATION")
        val level = info.protectionLevel
        return level and PermissionInfo.PROTECTION_DANGEROUS != 0
    }
}
