package com.newmikhmon.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

class RouterOsApi {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    fun connect(host: String, port: Int, user: String, pass: String, timeout: Int = 7000): String {
        close()
        socket = Socket()
        socket!!.connect(InetSocketAddress(host, port), timeout)
        socket!!.soTimeout = timeout
        input = BufferedInputStream(socket!!.getInputStream())
        output = BufferedOutputStream(socket!!.getOutputStream())

        var reply = command(listOf("/login", "=name=$user", "=password=$pass"))
        if (reply.any { it.firstOrNull() == "!done" && it.any { w -> w.startsWith("=ret=") } }) {
            val ret = reply.flatMap { it }.first { it.startsWith("=ret=") }.removePrefix("=ret=")
            val challenge = hexToBytes(ret)
            val md = MessageDigest.getInstance("MD5")
            md.update(0)
            md.update(pass.toByteArray(Charsets.UTF_8))
            md.update(challenge)
            val digest = md.digest().joinToString("") { "%02x".format(it) }
            reply = command(listOf("/login", "=name=$user", "=response=00$digest"))
        }
        val trap = reply.firstOrNull { it.firstOrNull() == "!trap" }
        if (trap != null) throw Exception(trap.joinToString(" "))
        if (!reply.any { it.firstOrNull() == "!done" }) throw Exception("Login failed")
        return "Connected"
    }

    fun getUsers(): List<Map<String,String>> = print("/ip/hotspot/user/print")
    fun getActive(): List<Map<String,String>> = print("/ip/hotspot/active/print")
    fun getProfiles(): List<Map<String,String>> = print("/ip/hotspot/user/profile/print")

    fun addUser(name:String, password:String, profile:String?, speed:String?, data:String?, time:String?) {
        val w = mutableListOf("/ip/hotspot/user/add", "=name=$name", "=password=$password")
        if (!profile.isNullOrBlank()) w += "=profile=$profile"
        if (!speed.isNullOrBlank()) w += "=rate-limit=$speed"
        if (!data.isNullOrBlank()) w += "=limit-bytes-total=$data"
        if (!time.isNullOrBlank()) w += "=limit-uptime=$time"
        expectDone(command(w))
    }
    fun removeUser(id:String) { expectDone(command(listOf("/ip/hotspot/user/remove", "=.id=$id"))) }
    fun disableUser(id:String) { expectDone(command(listOf("/ip/hotspot/user/disable", "=.id=$id"))) }
    fun enableUser(id:String) { expectDone(command(listOf("/ip/hotspot/user/enable", "=.id=$id"))) }

    private fun print(path:String): List<Map<String,String>> {
        val rows = command(listOf(path))
        return rows.filter { it.firstOrNull() == "!re" }.map { words ->
            words.filter { it.startsWith("=") }.associate {
                val p=it.indexOf('=')
                val q=it.indexOf('=',p+1)
                it.substring(p+1,q) to it.substring(q+1)
            }
        }
    }
    private fun expectDone(rows:List<List<String>>) {
        val trap=rows.firstOrNull { it.firstOrNull()=="!trap" }
        if(trap!=null) throw Exception(trap.joinToString(" "))
        if(rows.none { it.firstOrNull()=="!done" }) throw Exception("Router did not confirm operation")
    }
    @Synchronized private fun command(words:List<String>):List<List<String>> {
        val out=output ?: throw Exception("Not connected")
        words.forEach { writeWord(it) }; writeWord(""); out.flush()
        val result=mutableListOf<List<String>>()
        while(true) {
            val sentence=readSentence()
            if(sentence.isEmpty()) continue
            result += sentence
            if(sentence[0] in listOf("!done","!trap","!fatal")) break
        }
        return result
    }
    private fun writeWord(word:String) {
        val b=word.toByteArray(Charsets.UTF_8); writeLength(b.size); output!!.write(b)
    }
    private fun writeLength(n:Int) {
        when {
            n < 0x80 -> output!!.write(n)
            n < 0x4000 -> { output!!.write((n shr 8) or 0x80); output!!.write(n and 0xff) }
            n < 0x200000 -> { output!!.write((n shr 16) or 0xC0); output!!.write((n shr 8) and 0xff); output!!.write(n and 0xff) }
            n < 0x10000000 -> { output!!.write((n shr 24) or 0xE0); output!!.write((n shr 16) and 0xff); output!!.write((n shr 8) and 0xff); output!!.write(n and 0xff) }
            else -> { output!!.write(0xF0); output!!.write((n shr 24) and 0xff); output!!.write((n shr 16) and 0xff); output!!.write((n shr 8) and 0xff); output!!.write(n and 0xff) }
        }
    }
    private fun readSentence():List<String> {
        val words=mutableListOf<String>()
        while(true) {
            val len=readLength()
            if(len==0) return words
            val b=ByteArray(len); var p=0
            while(p<len) { val r=input!!.read(b,p,len-p); if(r<0) throw Exception("Router disconnected"); p+=r }
            words += String(b,Charsets.UTF_8)
        }
    }
    private fun readLength():Int {
        val b=input!!.read(); if(b<0) throw Exception("Router disconnected")
        return when {
            b and 0x80 == 0 -> b
            b and 0xC0 == 0x80 -> ((b and 0x3f) shl 8) or input!!.read()
            b and 0xE0 == 0xC0 -> ((b and 0x1f) shl 16) or (input!!.read() shl 8) or input!!.read()
            b and 0xF0 == 0xE0 -> ((b and 0x0f) shl 24) or (input!!.read() shl 16) or (input!!.read() shl 8) or input!!.read()
            else -> { input!!.read(); input!!.read(); input!!.read(); input!!.read(); 0 }
        }
    }
    private fun hexToBytes(s:String):ByteArray = ByteArray(s.length/2) { s.substring(it*2,it*2+2).toInt(16).toByte() }
    fun close() { try { socket?.close() } catch(_:Exception){}; socket=null; input=null; output=null }
}
