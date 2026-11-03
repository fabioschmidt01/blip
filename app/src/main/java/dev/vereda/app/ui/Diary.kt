package dev.vereda.app.ui

import android.content.Context
import dev.vereda.app.R

fun localizeDiary(context: Context, line: String): String {
    val split = line.indexOf("  ")
    if (split < 0) return line
    val time = line.substring(0, split)
    val body = line.substring(split + 2)
    val text = when {
        body == "serviço no ar" -> context.getString(R.string.log_on)
        body == "rádio desligado" -> context.getString(R.string.log_off)
        body == "não atualizei o nome anunciado" -> context.getString(R.string.log_name)
        body == "histórico apagado" -> context.getString(R.string.log_cleared)
        body == "quadro inválido" -> context.getString(R.string.log_bad_frame)
        body == "assinatura inválida" -> context.getString(R.string.log_bad_sig)
        body == "ttl inválido" -> context.getString(R.string.log_bad_ttl)
        body == "direta ilegível" -> context.getString(R.string.log_dm_unreadable)
        body == "mídia ilegível" -> context.getString(R.string.log_media_unreadable)
        body == "anúncio inválido" -> context.getString(R.string.log_bad_announce)
        body == "entrega confirmada" -> context.getString(R.string.log_ack)
        body == "falei no canal" -> context.getString(R.string.log_spoke)
        body == "pacote grande demais para o rádio" -> context.getString(R.string.log_too_big)
        body == "caminho de internet no ar" -> context.getString(R.string.log_net_on)
        body == "caminho de internet indisponível" -> context.getString(R.string.log_net_off)
        body.startsWith("anúncio de ") -> context.getString(R.string.log_announce, body.removePrefix("anúncio de "))
        body.startsWith("mídia de ") -> context.getString(R.string.log_media_from, body.removePrefix("mídia de "))
        body.startsWith("texto de ") -> context.getString(R.string.log_text_from, body.removePrefix("texto de "))
        body.startsWith("direta de ") -> context.getString(R.string.log_dm_from, body.removePrefix("direta de "))
        body.startsWith("direta para ") -> context.getString(R.string.log_dm_to, body.removePrefix("direta para "))
        body.startsWith("mídia cifrada para ") -> context.getString(R.string.log_media_sealed, body.removePrefix("mídia cifrada para "))
        body.startsWith("mídia para ") -> context.getString(R.string.log_media_to, body.removePrefix("mídia para "))
        body.startsWith("conexão recusada (") && body.endsWith(")") -> {
            context.getString(R.string.log_refused, body.removeSurrounding("conexão recusada (", ")"))
        }
        else -> body
    }
    return "$time  $text"
}
