package com.example.shared.repository

/**
 * Canonical conversation id shared by Android, iOS, and the Render service.
 * This is UUID v3 (MD5) over the sorted user ids joined by a colon, matching
 * java.util.UUID.nameUUIDFromBytes and the backend's chatIdFor function.
 */
fun directChatId(firstUserId: String, secondUserId: String): String {
    require(firstUserId.isNotBlank() && secondUserId.isNotBlank()) { "User ids cannot be blank" }
    val input = listOf(firstUserId, secondUserId).sorted().joinToString(":").encodeToByteArray()
    val digest = md5(input)
    digest[6] = ((digest[6].toInt() and 0x0f) or 0x30).toByte()
    digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
}

private fun md5(input: ByteArray): ByteArray {
    val originalBitLength = input.size.toLong() * 8L
    val paddedSize = ((input.size + 9 + 63) / 64) * 64
    val message = ByteArray(paddedSize)
    input.copyInto(message)
    message[input.size] = 0x80.toByte()
    for (index in 0 until 8) {
        message[paddedSize - 8 + index] = (originalBitLength ushr (8 * index)).toByte()
    }

    var a0 = 0x67452301
    var b0 = 0xefcdab89u.toInt()
    var c0 = 0x98badcfeu.toInt()
    var d0 = 0x10325476

    for (offset in message.indices step 64) {
        val words = IntArray(16) { word ->
            val start = offset + word * 4
            (message[start].toInt() and 0xff) or
                ((message[start + 1].toInt() and 0xff) shl 8) or
                ((message[start + 2].toInt() and 0xff) shl 16) or
                ((message[start + 3].toInt() and 0xff) shl 24)
        }
        var a = a0
        var b = b0
        var c = c0
        var d = d0
        for (index in 0 until 64) {
            val (f, wordIndex) = when (index) {
                in 0..15 -> ((b and c) or (b.inv() and d)) to index
                in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * index + 1) % 16)
                in 32..47 -> (b xor c xor d) to ((3 * index + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * index) % 16)
            }
            val nextD = d
            d = c
            c = b
            b += (a + f + MD5_CONSTANTS[index] + words[wordIndex]).rotateLeft(MD5_SHIFTS[index])
            a = nextD
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    return ByteArray(16).also { output ->
        intArrayOf(a0, b0, c0, d0).forEachIndexed { word, value ->
            for (byte in 0 until 4) output[word * 4 + byte] = (value ushr (8 * byte)).toByte()
        }
    }
}

private val MD5_SHIFTS = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

private val MD5_CONSTANTS = intArrayOf(
    0xd76aa478u.toInt(), 0xe8c7b756u.toInt(), 0x242070db, 0xc1bdceeeu.toInt(),
    0xf57c0fafu.toInt(), 0x4787c62a, 0xa8304613u.toInt(), 0xfd469501u.toInt(),
    0x698098d8, 0x8b44f7afu.toInt(), 0xffff5bb1u.toInt(), 0x895cd7beu.toInt(),
    0x6b901122, 0xfd987193u.toInt(), 0xa679438eu.toInt(), 0x49b40821,
    0xf61e2562u.toInt(), 0xc040b340u.toInt(), 0x265e5a51, 0xe9b6c7aau.toInt(),
    0xd62f105du.toInt(), 0x02441453, 0xd8a1e681u.toInt(), 0xe7d3fbc8u.toInt(),
    0x21e1cde6, 0xc33707d6u.toInt(), 0xf4d50d87u.toInt(), 0x455a14ed,
    0xa9e3e905u.toInt(), 0xfcefa3f8u.toInt(), 0x676f02d9, 0x8d2a4c8au.toInt(),
    0xfffa3942u.toInt(), 0x8771f681u.toInt(), 0x6d9d6122, 0xfde5380cu.toInt(),
    0xa4beea44u.toInt(), 0x4bdecfa9, 0xf6bb4b60u.toInt(), 0xbebfbc70u.toInt(),
    0x289b7ec6, 0xeaa127fau.toInt(), 0xd4ef3085u.toInt(), 0x04881d05,
    0xd9d4d039u.toInt(), 0xe6db99e5u.toInt(), 0x1fa27cf8, 0xc4ac5665u.toInt(),
    0xf4292244u.toInt(), 0x432aff97, 0xab9423a7u.toInt(), 0xfc93a039u.toInt(),
    0x655b59c3, 0x8f0ccc92u.toInt(), 0xffeff47du.toInt(), 0x85845dd1u.toInt(),
    0x6fa87e4f, 0xfe2ce6e0u.toInt(), 0xa3014314u.toInt(), 0x4e0811a1,
    0xf7537e82u.toInt(), 0xbd3af235u.toInt(), 0x2ad7d2bb, 0xeb86d391u.toInt()
)
