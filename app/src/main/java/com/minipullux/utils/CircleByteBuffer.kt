package com.minipullux.utils

class CircleByteBuffer(private val capacity: Int) {

    private val data = ByteArray(capacity)
    private var start = 0
    private var end = 0
    private val readLock = Any()
    private val writeLock = Any()

    private fun checkBounds(index: Int): Int {
        return if (index < 0 || index >= capacity) 0 else index
    }

    @Synchronized
    private fun write(b: Byte): Boolean {
        synchronized(writeLock) {
            if (remaining() == 0) {
                start = end
                return false
            }
            data[end++] = b
            end = checkBounds(end)
            return true
        }
    }

    @Synchronized
    private fun read(): Byte? {
        synchronized(readLock) {
            if (length() == 0)
                return null
            val b = data[start++]
            start = checkBounds(start)
            return b
        }
    }

    fun length(): Int = when {
        start == end -> 0
        start < end -> end - start
        else -> capacity - start + end
    }

    fun remaining(): Int = capacity - length()

    fun puts(bts: ByteArray, ind: Int, len: Int) {
        if (remaining() <= len) throw Exception("over flow")
        for (i in ind until len)
            write(bts[i])
    }

    fun puts(bts: ByteArray) = puts(bts, 0, bts.size)

    /**
     * 取出指定个数数据
     */
    fun gets(size: Int): ByteArray {
        return if (length() >= size) {
            val bts = ByteArray(size)
            for (i in 0 until size)
                bts[i] = read()!!
            bts
        } else byteArrayOf()
    }

    fun getAll(): ByteArray = gets(length())
    fun clear() {
        start = 0
        end = 0
    }

    private fun calculateIndex(i: Int): Int {
        return if (start >= (capacity - i))
            (start + i) % capacity else start + i
    }

    /**
     * 查看环形缓存区内数据，但不取出
     */
    @Synchronized
    fun peek(i: Int): Byte? {
        synchronized(readLock) {
            if (length() == 0)
                return null
            val index = calculateIndex(i)
            return data[index]
        }
    }

    /**
     * 获取但不删除环形缓存区内数据
     */
    fun peeks(size: Int): ByteArray {
        return if (length() >= size) {
            val bts = ByteArray(size)
            for (i in 0 until size)
                bts[i] = peek(i)!!
            bts
        } else byteArrayOf()
    }

    fun indexOf(value: Byte): Int {
        val len = length()
        if (len < 1) return -1
        for (i in 0 until len)
            if (peek(i) == value) return i
        return -1
    }

    fun indexOf(key: String): Int {
        val len = length()
        if (len < 1 || len < key.length) return -1
        for (i in 0 until len)
            if (peek(i) == key.last().code.toByte() && i >= key.lastIndex) {
                var match = true
                for (j in key.lastIndex downTo 0)
                    if (key[j].code.toByte() != peek(i - key.lastIndex + j)) {
                        match = false
                        break
                    }
                if (match) return i - key.lastIndex
            }
        return -1
    }

    private fun kmpPartialMatchTable(pattern: String): IntArray {

        val table = IntArray(pattern.length)
        var index = 0

        table[0] = 0
        for (i in 1 until pattern.length) {

            while (index > 0 && pattern[index] != pattern[i]) {
                index = table[index - 1]
            }

            if (pattern[index] == pattern[i]) {
                index++
            }

            table[i] = index
        }

        return table
    }

    fun indexOfKMP(pattern: String): Int {
        val len = length()
        if (len < 1 || len < pattern.length) return -1

        // 1. 计算KMP部分匹配表
        val partialMatch = kmpPartialMatchTable(pattern)

        // 2. 开始KMP搜索
        var j = 0
        for (i in 0 until length()) {
            while (j > 0 && pattern[j] != peek(i)!!.toInt().toChar()) {
                j = partialMatch[j - 1]
            }
            if (pattern[j] == peek(i)!!.toInt().toChar()) {
                j++
            }
            if (j == pattern.length) {
                return i - pattern.length + 1
            }
        }

        return -1
    }
    /**
     * 计算模式串的部分匹配表
     */
}
