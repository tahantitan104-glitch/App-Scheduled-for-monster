package com.example.vpn

/**
 * Pure Kotlin RFC 1035 IPv4 + UDP DNS packet parser and response builder.
 * Runs cleanly on Android 5.1.1+ and JVM unit tests with zero native or OS dependencies.
 */
data class ParsedDnsPacket(
    val transactionId: Int,
    val flags: Int,
    val queryDomain: String,
    val queryType: Int,
    val rawPacket: ByteArray,
    val length: Int,
    val ipHeaderLength: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayloadOffset: Int,
    val dnsPayloadLength: Int,
    val questionEndOffset: Int
)

object DnsPacketCodec {

    private const val IP_PROTO_UDP = 17
    private const val DNS_PORT = 53
    private const val DNS_HEADER_LEN = 12

    fun parseIpv4UdpDnsQuery(packet: ByteArray, length: Int): ParsedDnsPacket? {
        if (length < 20 + 8 + DNS_HEADER_LEN || length > packet.size) return null

        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (ipHeaderLen < 20 || ipHeaderLen + 8 + DNS_HEADER_LEN > length) return null

        val totalIpLen = readU16(packet, 2)
        if (totalIpLen < ipHeaderLen + 8 + DNS_HEADER_LEN || totalIpLen > length) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != IP_PROTO_UDP) return null

        val udpOffset = ipHeaderLen
        val srcPort = readU16(packet, udpOffset)
        val dstPort = readU16(packet, udpOffset + 2)
        if (dstPort != DNS_PORT) return null

        val udpLen = readU16(packet, udpOffset + 4)
        if (udpLen < 8 + DNS_HEADER_LEN || udpOffset + udpLen > totalIpLen) return null

        val dnsOffset = udpOffset + 8
        val dnsLen = udpLen - 8
        val dnsEnd = dnsOffset + dnsLen

        val txId = readU16(packet, dnsOffset)
        val flags = readU16(packet, dnsOffset + 2)
        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return null

        val qdCount = readU16(packet, dnsOffset + 4)
        if (qdCount <= 0) return null

        val labels = ArrayList<String>(4)
        var pos = dnsOffset + DNS_HEADER_LEN
        var terminated = false
        while (pos < dnsEnd) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos += 1
                terminated = true
                break
            }
            if ((labelLen and 0xC0) != 0) {
                // Compression pointer inside first question is non-standard
                return null
            }
            if (labelLen > 63 || pos + 1 + labelLen >= dnsEnd) {
                return null
            }
            val labelChars = CharArray(labelLen) { idx ->
                packet[pos + 1 + idx].toInt().toChar().lowercaseChar()
            }
            labels.add(String(labelChars))
            pos += 1 + labelLen
        }

        if (!terminated || pos + 4 > dnsEnd || labels.isEmpty()) return null

        val queryType = readU16(packet, pos)
        val questionEndOffset = pos + 4
        val domain = labels.joinToString(".")

        return ParsedDnsPacket(
            transactionId = txId,
            flags = flags,
            queryDomain = domain,
            queryType = queryType,
            rawPacket = packet,
            length = totalIpLen,
            ipHeaderLength = ipHeaderLen,
            srcIp = packet.copyOfRange(12, 16),
            dstIp = packet.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            dnsPayloadOffset = dnsOffset,
            dnsPayloadLength = dnsLen,
            questionEndOffset = questionEndOffset
        )
    }

    fun buildNxDomainResponse(query: ParsedDnsPacket): ByteArray {
        val questionBytesLen = (query.questionEndOffset - (query.dnsPayloadOffset + DNS_HEADER_LEN))
            .coerceAtLeast(0)
        val dnsPayload = ByteArray(DNS_HEADER_LEN + questionBytesLen)

        // Transaction ID
        writeU16(dnsPayload, 0, query.transactionId)
        // Flags: QR=1, copy RD (0x0100), RA=1 (0x0080), RCODE=3 (NXDOMAIN)
        val responseFlags = 0x8083 or (query.flags and 0x0100)
        writeU16(dnsPayload, 2, responseFlags)
        // QDCOUNT = 1, ANCOUNT/NSCOUNT/ARCOUNT = 0
        writeU16(dnsPayload, 4, 1)
        writeU16(dnsPayload, 6, 0)
        writeU16(dnsPayload, 8, 0)
        writeU16(dnsPayload, 10, 0)

        if (questionBytesLen > 0) {
            System.arraycopy(
                query.rawPacket,
                query.dnsPayloadOffset + DNS_HEADER_LEN,
                dnsPayload,
                DNS_HEADER_LEN,
                questionBytesLen
            )
        }
        return buildForwardedUdpDnsResponse(query, dnsPayload, dnsPayload.size)
    }

    fun buildForwardedUdpDnsResponse(
        query: ParsedDnsPacket,
        upstreamDnsPayload: ByteArray,
        payloadLen: Int
    ): ByteArray {
        val safeLen = payloadLen.coerceIn(0, upstreamDnsPayload.size)
        val ipHeaderLen = 20
        val udpLen = 8 + safeLen
        val totalLen = ipHeaderLen + udpLen
        val out = ByteArray(totalLen)

        // IPv4 Header
        out[0] = 0x45.toByte()
        out[1] = 0x00
        writeU16(out, 2, totalLen)
        writeU16(out, 4, query.transactionId)
        writeU16(out, 6, 0x4000) // Don't fragment
        out[8] = 64.toByte() // TTL
        out[9] = IP_PROTO_UDP.toByte()
        writeU16(out, 10, 0)
        // Swap src and dst IPs
        System.arraycopy(query.dstIp, 0, out, 12, 4)
        System.arraycopy(query.srcIp, 0, out, 16, 4)

        val ipChecksum = computeIpv4Checksum(out, 0, ipHeaderLen)
        writeU16(out, 10, ipChecksum)

        // UDP Header (swap src and dst ports)
        val udpOffset = ipHeaderLen
        writeU16(out, udpOffset, query.dstPort)
        writeU16(out, udpOffset + 2, query.srcPort)
        writeU16(out, udpOffset + 4, udpLen)
        writeU16(out, udpOffset + 6, 0)

        if (safeLen > 0) {
            System.arraycopy(upstreamDnsPayload, 0, out, udpOffset + 8, safeLen)
        }

        val udpChecksum = computeUdpChecksum(
            srcIp = query.dstIp,
            dstIp = query.srcIp,
            udpSegment = out,
            udpOffset = udpOffset,
            udpLength = udpLen
        )
        writeU16(out, udpOffset + 6, udpChecksum)
        return out
    }

    fun computeIpv4Checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < length) {
            val word = ((buf[offset + i].toInt() and 0xFF) shl 8) or
                (buf[offset + i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < length) {
            sum += (buf[offset + i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun computeUdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpSegment: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0L
        sum += readU16(srcIp, 0)
        sum += readU16(srcIp, 2)
        sum += readU16(dstIp, 0)
        sum += readU16(dstIp, 2)
        sum += IP_PROTO_UDP
        sum += udpLength

        var i = 0
        while (i + 1 < udpLength) {
            sum += readU16(udpSegment, udpOffset + i)
            i += 2
        }
        if (i < udpLength) {
            sum += (udpSegment[udpOffset + i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFFL) + (sum ushr 16)
        }
        val inverted = (sum.inv() and 0xFFFFL).toInt()
        return if (inverted == 0) 0xFFFF else inverted
    }

    private fun readU16(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
    }

    private fun writeU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }
}
