package com.newmikhmon.app
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
class RouterOsApi{
 private var socket:Socket?=null;private var input:BufferedInputStream?=null;private var output:BufferedOutputStream?=null
 fun connect(host:String,port:Int,user:String,pass:String,timeout:Int=7000){
  close();socket=Socket();socket!!.connect(InetSocketAddress(host,port),timeout);socket!!.soTimeout=timeout
  input=BufferedInputStream(socket!!.getInputStream());output=BufferedOutputStream(socket!!.getOutputStream())
  var r=command(listOf("/login","=name=$user","=password=$pass"));checkTrap(r)
  val ret=r.flatMap{it}.firstOrNull{it.startsWith("=ret=")}
  if(ret!=null){val md=MessageDigest.getInstance("MD5");md.update(0);md.update(pass.toByteArray(Charsets.UTF_8));md.update(hex(ret.removePrefix("=ret=")));val digest=md.digest().joinToString(""){"%02x".format(it)};r=command(listOf("/login","=name=$user","=response=00$digest"));checkTrap(r)}
  if(r.none{it.firstOrNull()=="!done"})throw Exception("MikroTik login failed")
 }
 private fun checkTrap(r:List<List<String>>){val t=r.firstOrNull{it.firstOrNull()=="!trap"};if(t!=null)throw Exception(t.firstOrNull{it.startsWith("=message=")}?.removePrefix("=message=")?:"MikroTik rejected login")}
 fun getUsers()=print("/ip/hotspot/user/print");fun getActive()=print("/ip/hotspot/active/print")
 fun addUser(n:String,p:String,pr:String?,sp:String?,da:String?,ti:String?){val w=mutableListOf("/ip/hotspot/user/add","=name=$n","=password=$p");if(!pr.isNullOrBlank())w+="=profile=$pr";if(!sp.isNullOrBlank())w+="=rate-limit=$sp";if(!da.isNullOrBlank())w+="=limit-bytes-total=$da";if(!ti.isNullOrBlank())w+="=limit-uptime=$ti";done(command(w))}
 fun removeUser(id:String)=done(command(listOf("/ip/hotspot/user/remove","=.id=$id")));fun disableUser(id:String)=done(command(listOf("/ip/hotspot/user/disable","=.id=$id")));fun enableUser(id:String)=done(command(listOf("/ip/hotspot/user/enable","=.id=$id")))
 private fun print(path:String)=command(listOf(path)).filter{it.firstOrNull()=="!re"}.map{s->s.filter{it.startsWith("=")}.associate{val p=it.indexOf('=');val q=it.indexOf('=',p+1);it.substring(p+1,q) to it.substring(q+1)}}
 private fun done(r:List<List<String>>){checkTrap(r);if(r.none{it.firstOrNull()=="!done"})throw Exception("MikroTik did not confirm the operation")}
 @Synchronized private fun command(words:List<String>):List<List<String>>{val o=output?:throw Exception("Not connected");words.forEach{write(it)};write("");o.flush();val r=mutableListOf<List<String>>();while(true){val s=read();if(s.isEmpty())continue;r+=s;if(s[0] in listOf("!done","!trap","!fatal"))break};return r}
 private fun write(w:String){val b=w.toByteArray(Charsets.UTF_8);lenWrite(b.size);output!!.write(b)}
 private fun lenWrite(n:Int){when{n<128->output!!.write(n);n<16384->{output!!.write((n shr 8) or 128);output!!.write(n and 255)};n<2097152->{output!!.write((n shr 16) or 192);output!!.write((n shr 8) and 255);output!!.write(n and 255)};n<268435456->{output!!.write((n shr 24) or 224);output!!.write((n shr 16) and 255);output!!.write((n shr 8) and 255);output!!.write(n and 255)};else->{output!!.write(240);output!!.write((n shr 24) and 255);output!!.write((n shr 16) and 255);output!!.write((n shr 8) and 255);output!!.write(n and 255)}}}
 private fun read():List<String>{val w=mutableListOf<String>();while(true){val n=lenRead();if(n==0)return w;val b=ByteArray(n);var p=0;while(p<n){val x=input!!.read(b,p,n-p);if(x<0)throw Exception("Router closed connection");p+=x};w+=String(b,Charsets.UTF_8)}}
 private fun lenRead():Int{val b=input!!.read();if(b<0)throw Exception("Router closed connection");return when{b and 128==0->b;b and 192==128->((b and 63) shl 8) or input!!.read();b and 224==192->((b and 31) shl 16) or (input!!.read() shl 8) or input!!.read();b and 240==224->((b and 15) shl 24) or (input!!.read() shl 16) or (input!!.read() shl 8) or input!!.read();else->{repeat(4){input!!.read()};0}}}
 private fun hex(s:String)=ByteArray(s.length/2){s.substring(it*2,it*2+2).toInt(16).toByte()}
 fun close(){try{socket?.close()}catch(_:Exception){};socket=null;input=null;output=null}
}