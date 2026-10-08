package dev.montra.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.montra.util.Log

/**
 * "Este telemóvel tem internet?" — perguntado ao sistema, não adivinhado pelo erro.
 *
 * Existe por uma razão concreta: quando o pedido ao índice falha, a app não deve
 * dizer que *não consegue ligar-se ao GitHub* sem antes saber se o problema é a
 * rede de quem está a ler. A mensagem passa a ser uma afirmação sobre o estado do
 * telemóvel, que é o que a pessoa pode resolver — em vez de um `UnknownHostException`
 * despejado no ecrã, que fala do sítio errado.
 *
 * [hasInternet] responde "há um caminho para a internet?", não "este pedido vai
 * funcionar": um portal cativo, um DNS privado avariado ou o host do índice em baixo
 * passam por aqui como *online*. É por isso que isto só decide o *texto* da falha e
 * nunca substitui a tentativa de rede — quem manda é sempre o pedido.
 */
class NetworkStatus(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    /** O último estado conhecido, para quem precisa de decidir sem bloquear. */
    @Volatile
    var hasInternet: Boolean = false
        private set

    @Volatile
    private var watching = false

    /**
     * Pergunta ao sistema agora. Só é chamado no arranque e quando a rede muda de
     * forma que interesse, não a cada pintura do ecrã.
     */
    fun refresh(): Boolean {
        hasInternet = manager?.let { activeNetworkHasInternet(it) } ?: true
        return hasInternet
    }

    /**
     * Avisa quando a rede volta. `onBack` corre no thread do sistema: quem o usa
     * lança trabalho e sai logo.
     *
     * Registar o callback já entrega o estado atual, portanto a app não tem de
     * esperar pela primeira mudança para saber onde está.
     */
    fun watch(onBack: () -> Unit) {
        val manager = this.manager ?: return
        if (watching) return
        watching = true
        try {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        val online = capabilities.hasInternet()
                        if (online == hasInternet) return
                        hasInternet = online
                        Log.i(if (online) "a rede voltou" else "a rede desapareceu")
                        if (online) onBack()
                    }

                    override fun onLost(network: Network) {
                        if (!hasInternet) return
                        hasInternet = false
                        Log.i("a rede desapareceu")
                    }
                },
            )
        } catch (error: RuntimeException) {
            // Algumas ROMs rebentam aqui (e um telemóvel sem rede nenhuma não tem
            // callback nenhum para registar). Não vale a pena levar a app abaixo por
            // causa de uma conveniência: sem callback, o botão "Tentar de novo"
            // continua a funcionar.
            watching = false
            Log.w("não consegui vigiar a rede: ${error.message}", error)
        }
    }

    private fun activeNetworkHasInternet(manager: ConnectivityManager): Boolean =
        manager.activeNetwork?.let { manager.getNetworkCapabilities(it)?.hasInternet() } ?: false

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
