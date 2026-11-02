package dev.vereda.core

object Router {
    fun targets(
        packet: Packet,
        fromLink: String?,
        self: String,
        links: Set<String>,
        directLinkOf: (String) -> String?,
    ): List<String> {
        if (packet.ttl <= 1 || packet.linkLocal) return emptyList()
        val others = links.filter { it != fromLink }
        val recipient = packet.recipient ?: return others
        if (recipient == self) return emptyList()
        val direct = directLinkOf(recipient)
        if (direct != null) {
            return if (direct == fromLink) emptyList() else listOf(direct)
        }
        return others.sorted().take(MeshLimits.FANOUT)
    }
}
