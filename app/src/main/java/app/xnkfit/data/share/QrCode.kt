package app.xnkfit.data.share

/**
 * A small QR Code encoder (ISO 18004): byte mode, error correction level M, versions 1 to 10 (up to 213 bytes).
 * Written for the share card's link, so no library is added (Design §12). Pure Kotlin, no Android types.
 */
class QrCode private constructor(val version: Int, private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    /** True for a dark module; false outside the symbol. */
    fun isDark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    companion object {
        const val MAX_BYTES = 213
        private const val MIN_VERSION = 1
        private const val MAX_VERSION = 10

        // Level M: error correction codewords per block and number of blocks, index = version.
        private val ECC_PER_BLOCK = intArrayOf(0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val BLOCKS = intArrayOf(0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

        /** Encodes [text] as UTF-8 bytes. Throws [IllegalArgumentException] when it is longer than [MAX_BYTES]. */
        fun encode(text: String): QrCode {
            val bytes = text.toByteArray(Charsets.UTF_8)
            for (version in MIN_VERSION..MAX_VERSION) {
                val capacity = dataCodewords(version)
                val countBits = if (version <= 9) 8 else 16
                if (4 + countBits + bytes.size * 8 > capacity * 8) continue
                return build(version, interleave(version, dataBits(bytes, version, capacity)))
            }
            throw IllegalArgumentException("text too long for a QR code")
        }

        private fun rawModules(ver: Int): Int {
            var result = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val align = ver / 7 + 2
                result -= (25 * align - 10) * align - 55
                if (ver >= 7) result -= 36
            }
            return result
        }

        private fun dataCodewords(ver: Int) = rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * BLOCKS[ver]

        private fun dataBits(bytes: ByteArray, version: Int, capacity: Int): ByteArray {
            val bits = ArrayList<Int>()
            fun put(value: Int, count: Int) { for (i in count - 1 downTo 0) bits.add((value ushr i) and 1) }
            put(4, 4) // byte mode
            put(bytes.size, if (version <= 9) 8 else 16)
            bytes.forEach { put(it.toInt() and 0xFF, 8) }
            put(0, minOf(4, capacity * 8 - bits.size))
            while (bits.size % 8 != 0) bits.add(0)
            var pad = 0xEC
            while (bits.size < capacity * 8) { put(pad, 8); pad = pad xor (0xEC xor 0x11) }
            return ByteArray(capacity) { i -> var v = 0; for (j in 0 until 8) v = (v shl 1) or bits[i * 8 + j]; v.toByte() }
        }

        private fun mul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun divisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    result[j] = mul(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = mul(root, 2)
            }
            return result
        }

        private fun remainder(data: ByteArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor mul(divisor[i], factor)
            }
            return result
        }

        private fun interleave(ver: Int, data: ByteArray): ByteArray {
            val blocks = BLOCKS[ver]
            val eccLen = ECC_PER_BLOCK[ver]
            val raw = rawModules(ver) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLen = raw / blocks
            val div = divisor(eccLen)
            val parts = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until blocks) {
                val len = shortLen - eccLen + if (i < shortBlocks) 0 else 1
                val dat = data.copyOfRange(k, k + len)
                k += len
                val ecc = remainder(dat, div)
                val block = IntArray(shortLen + 1)
                for (j in dat.indices) block[j] = dat[j].toInt() and 0xFF
                // Short blocks leave one slot unused so every block has the same width.
                for (j in ecc.indices) block[shortLen + 1 - eccLen + j] = ecc[j]
                parts.add(block)
            }
            val out = ArrayList<Byte>()
            for (i in 0..shortLen) {
                for (j in parts.indices) {
                    if (i != shortLen - eccLen || j >= shortBlocks) out.add(parts[j][i].toByte())
                }
            }
            return out.toByteArray()
        }

        private fun build(version: Int, codewords: ByteArray): QrCode {
            val size = version * 4 + 17
            val m = Array(size) { BooleanArray(size) }
            val fn = Array(size) { BooleanArray(size) }
            fun set(x: Int, y: Int, dark: Boolean) { m[y][x] = dark; fn[y][x] = true }

            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val d = maxOf(Math.abs(dx), Math.abs(dy))
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) set(x, y, d != 2 && d != 4)
                }
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            val pos = alignmentPositions(version, size)
            for (i in pos.indices) for (j in pos.indices) {
                if ((i == 0 && j == 0) || (i == 0 && j == pos.size - 1) || (i == pos.size - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
            formatBits(m, fn, size, 0)
            if (version >= 7) {
                var rem = version
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (version shl 12) or rem
                for (i in 0 until 18) {
                    val bit = ((bits ushr i) and 1) != 0
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }
            // Codewords in the zigzag order.
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!fn[y][x] && i < codewords.size * 8) {
                        m[y][x] = ((codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                        i++
                    }
                }
                right -= 2
            }
            var best = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                applyMask(m, fn, size, mask)
                formatBits(m, fn, size, mask)
                val p = penalty(m, size)
                if (p < bestPenalty) { bestPenalty = p; best = mask }
                applyMask(m, fn, size, mask) // undo
            }
            applyMask(m, fn, size, best)
            formatBits(m, fn, size, best)
            return QrCode(version, m)
        }

        private fun alignmentPositions(ver: Int, size: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = (ver * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val result = IntArray(n)
            result[0] = 6
            var p = size - 7
            for (i in n - 1 downTo 1) { result[i] = p; p -= step }
            return result
        }

        private fun formatBits(m: Array<BooleanArray>, fn: Array<BooleanArray>, size: Int, mask: Int) {
            fun set(x: Int, y: Int, dark: Boolean) { m[y][x] = dark; fn[y][x] = true }
            val data = mask // level M has format bits 00
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((bits ushr i) and 1) != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9..14) set(14 - i, 8, bit(i))
            for (i in 0..7) set(size - 1 - i, 8, bit(i))
            for (i in 8..14) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun applyMask(m: Array<BooleanArray>, fn: Array<BooleanArray>, size: Int, mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (!fn[y][x] && invert) m[y][x] = !m[y][x]
            }
        }

        private fun penalty(m: Array<BooleanArray>, size: Int): Int {
            var total = 0
            for (pass in 0..1) {
                for (a in 0 until size) {
                    val line = StringBuilder()
                    var run = 1
                    for (b in 0 until size) {
                        val dark = if (pass == 0) m[a][b] else m[b][a]
                        line.append(if (dark) '1' else '0')
                        if (b > 0) {
                            val prev = if (pass == 0) m[a][b - 1] else m[b - 1][a]
                            if (prev == dark) {
                                run++
                                if (run == 5) total += 3 else if (run > 5) total++
                            } else {
                                run = 1
                            }
                        }
                    }
                    val s = line.toString()
                    for (pat in listOf("10111010000", "00001011101")) {
                        var at = s.indexOf(pat)
                        while (at >= 0) { total += 40; at = s.indexOf(pat, at + 1) }
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                if (m[y][x] == m[y][x + 1] && m[y][x] == m[y + 1][x] && m[y][x] == m[y + 1][x + 1]) total += 3
            }
            var dark = 0
            for (row in m) for (v in row) if (v) dark++
            val all = size * size
            total += ((Math.abs(dark * 20 - all * 10) + all - 1) / all - 1) * 10
            return total
        }
    }
}
