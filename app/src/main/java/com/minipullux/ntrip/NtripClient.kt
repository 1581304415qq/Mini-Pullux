package com.minipullux.ntrip

import android.os.Build
import com.minipullux.utils.CircleByteBuffer
import com.minipullux.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

class NtripClient {
    val TAG = "NtripClient"

    private val sendBuffer = CircleByteBuffer(1024)
    private var outputStream: OutputStream? = null
    private var state = NtripStatus.DISCONNECTED
    fun connect(
        ip: String,
        port: Int,
        account: String,
        password: String,
        mount: String,
        callBack: (Boolean) -> Unit = {}
    ) {
        if (state != NtripStatus.DISCONNECTED) return

        CoroutineScope(Dispatchers.IO).launch {
            val socket = Socket()
            withContext(Dispatchers.IO) {
                try {
                    socket.connect(InetSocketAddress(ip, port), 5000)
                } catch (e: Exception) {
                    return@withContext
                }
                if (!socket.isConnected) return@withContext
                state = NtripStatus.AUTHENTICATING
                val buff = ByteArray(1024)
                val inputStream = socket.getInputStream()
                outputStream = socket.getOutputStream()

                outputStream!!.write(buildNtripRequestHeader(account, password, mount))
                // Read data
                while (state > NtripStatus.DISCONNECTED) {
                    val len = inputStream.read(buff, 0, 1024)
                    if (len < 0) continue
                    val data = buff.copyOf(len)
                    if (state == NtripStatus.AUTHENTICATING) { // Handle response
                        val response = data.decodeToString()
                        Log.i(TAG, "response:$len")
                        if (response.startsWith("ICY 200 OK")) {// Connection successful
                            Log.i(TAG, "Connected!")
                            state = NtripStatus.AUTHENTICATED
                            sendDataTh()
                            callBack(true)
                        } else {
                            // Connection failed
                            Log.i(TAG, "Connection failed: $response")
                            callBack(false)
                        }
                    } else if (state == NtripStatus.AUTHENTICATED) {
                        // TODO
                        // 处理接收的RTCM数据
                    }
                }

                socket.close()
                inputStream.close()
                outputStream!!.close()
            }
        }
    }

    fun disconnect() {
        state = NtripStatus.DISCONNECTED
    }

    fun sendGGA(data: ByteArray) {
//        Log.d(TAG, String(data))
        if (state == NtripStatus.AUTHENTICATED)
            sendBuffer.puts(data)
    }

    private fun sendDataTh() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                while (state == NtripStatus.AUTHENTICATED) {
                    val data = sendBuffer.getAll()
                    if (data.isNotEmpty()) outputStream?.write(data)
                }
            }
        }
    }

    private fun getAuthString(account: String, password: String): String {
        val auth = "$account:$password\r\n"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Base64.getEncoder().encodeToString(auth.toByteArray())
        else
            android.util.Base64.encode(auth.toByteArray(), android.util.Base64.DEFAULT).toString()
    }

    private fun buildNtripRequestHeader(
        account: String,
        password: String,
        mount: String
    ): ByteArray {
        return (
                "GET /${mount} HTTP/1.0\r\n" +
                        "User-Agent:  NTRIP GNSSInternetRadio/1.4.10\r\n" +
                        "Accept: */*\r\nConnection: close\r\n" +
                        "Authorization: Basic ${getAuthString(account, password)}\r\n" +
                        "\r\n"
                ).toByteArray()
    }

    enum class NtripStatus {
        /** 未连接/已断开 */
        DISCONNECTED,

        /** 连接中（TCP连接建立中） */
        CONNECTING,

        /** 已连接但未认证（仅当需要认证时会出现此状态） */
        AUTHENTICATING,

        /** 已连接并认证成功（对于无需认证的公共源，连接成功即为此状态） */
        AUTHENTICATED,

        /** 连接/认证失败 */
        ERROR
    }
}