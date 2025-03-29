package com.minipullux.utils

import android.content.Context
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.pow
import kotlin.math.roundToInt

fun getMin(a: Int, b: Int): Int = if (a > b) b else a

fun toastShow(context: Context, tag: String = "TAG", msg: String) =
    Toast.makeText(context, "$tag $msg", Toast.LENGTH_SHORT).show()

/**
 * imsi判断运营商 0 移动 1 联通 2 电信 3未知
 */
fun getOperator(imsi: String): Int {
    if (imsi.isEmpty()) return 3
    if (imsi[0] == '4' && imsi[1] == '6' && imsi[2] == '0')
        if (imsi[3] == '0' && imsi[4] == '0'
            || imsi[3] == '0' && imsi[4] == '2'
            || imsi[3] == '0' && imsi[4] == '7'
            || imsi[3] == '2' && imsi[4] == '0'
        ) return 0 //移动
        else if (imsi[3] == '0' && imsi[4] == '1'
            || imsi[3] == '0' && imsi[4] == '6'
            || imsi[3] == '0' && imsi[4] == '9'
        ) return 1 //联通
        else if (imsi[3] == '0' && imsi[4] == '3'
            || imsi[3] == '0' && imsi[4] == '5'
            || imsi[1] == '0' && imsi[4] == '1'
        ) return 2 //电信
    return 3
}

data class Coord(
    val value: Double,
    val direct: Char
)

//  22:34:38.59479N  113:56:17.04757E
fun coordTransform(s: String): Coord {
    val direct = s.last()
    val splitPart = """(\d+[.]\d+)|\d+"""
    val res = Regex(splitPart).findAll(s).toList()
    val num =
        res[0].value.toDouble() + res[1].value.toDouble() / 60 + res[2].value.toDouble() / 3600
    return Coord(num, s.last())
}

fun dmm2Deg(value: Double): Double {
    return (value / 100).toInt() + value % 100 / 60
}

fun Double.round(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToInt() / factor
}

/**
 * v1 大于 v2 return 1
 * v1 小于 v2 return -1
 * v1 等于 v2 return 0
 */
fun strCmp(v1: String, v2: String): Int {
    // v1 大于 v2
    if (v1.length > v2.length) return 1
    // v1 小于 v2
    for (i in v2.indices) {
        if (v1[i] < v2[i]) return -1
        if (v1[i] > v2[i]) return 1
    }
    // v1 等于 v2
    return 0
}

/**
 * Compress a string using GZIP.
 *
 * @return an UTF-8 encoded byte array.
 */
fun String.gzipCompress(): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8).use { it.write(this) }
    return bos.toByteArray()
}

/**
 * Decompress a byte array using GZIP.
 *
 * @return an UTF-8 encoded string.
 */
fun ByteArray.gzipDecompress(): String {
    val bais = ByteArrayInputStream(this)
    lateinit var string: String
    GZIPInputStream(bais).bufferedReader(Charsets.UTF_8).use { return it.readText() }
}
