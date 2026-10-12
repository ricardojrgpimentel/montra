package dev.montra.install

/**
 * As decisões que se tomam *antes* de transferir, numa forma que se testa sem
 * aparelho nem rede.
 *
 * Estão separadas do pipeline porque são regras e não passos: "esta rede é medida e
 * a pessoa não autorizou?" e "cabe no que resta do telefone?". Escritas lá dentro,
 * só se testavam a correr a transferência inteira.
 */
object TransferPolicy {

    /**
     * Segurar a transferência por a rede ser medida e a pessoa não a ter autorizado.
     *
     * Uma transferência já autorizada passa sempre: quem disse "sim" uma vez não deve
     * voltar a ser perguntado a cada pedaço.
     */
    fun holdForMeteredNetwork(wifiOnly: Boolean, metered: Boolean, allowMetered: Boolean): Boolean =
        wifiOnly && metered && !allowMetered

    /**
     * Quanto falta de espaço, ou zero se couber.
     *
     * O dobro do tamanho, e não o tamanho: no pico o APK existe duas vezes — uma no
     * cache e outra na área de preparação da sessão do instalador, porque o
     * `PackageInstaller` copia o ficheiro para lá antes de instalar. Verificar só o
     * tamanho deixava a falha a meio da cópia, que é exactamente o que esta barreira
     * existe para evitar.
     */
    fun missingBytes(requiredBytes: Long, freeBytes: Long): Long =
        (requiredBytes * 2 + HEADROOM_BYTES - freeBytes).coerceAtLeast(0L)

    /** 8 MB de margem para o resto do que o sistema escreve durante a sessão. */
    const val HEADROOM_BYTES = 8L * 1024 * 1024
}
