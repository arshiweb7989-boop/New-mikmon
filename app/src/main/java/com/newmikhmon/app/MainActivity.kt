package com.newmikhmon.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : Activity() {
    private val api = RouterOsApi()
    private val ex = Executors.newCachedThreadPool()
    private var connected = false
    private var searching = false
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var result: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.rgb(7,20,42)
        window.navigationBarColor = Color.rgb(7,20,42)
        home()
    }

    private fun home() {
        base("HOME")
        val logo = TextView(this).apply {
            text = "M"; textSize = 38f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(18,92,190)); setStroke(3, Color.rgb(80,180,255))
            }
        }
        root.addView(logo, LinearLayout.LayoutParams(92,92).apply { gravity = Gravity.CENTER; bottomMargin = 14 })
        root.addView(text("NEW MIKHMON",30,true,Color.WHITE,Gravity.CENTER))
        root.addView(text("MikroTik Hotspot Manager",15,false,Color.rgb(175,205,235),Gravity.CENTER))
        root.addView(Space(this), LinearLayout.LayoutParams(1,28))
        root.addView(actionCard("MANUAL CONNECT","Router IP + username + password","CONNECT"){ manual() })
        root.addView(Space(this), LinearLayout.LayoutParams(1,14))
        root.addView(actionCard("AUTO SEARCH","Find MikroTik API routers on this Wi-Fi","SCAN"){ autoSearch() })
        root.addView(Space(this), LinearLayout.LayoutParams(1,22))
        root.addView(text("API 8728  •  API-SSL 8729  •  RouterOS v6 / v7",12,false,Color.rgb(140,170,200),Gravity.CENTER))
    }

    private fun manual() {
        base("MANUAL CONNECT")
        root.addView(text("Connect to your MikroTik router",20,true,Color.WHITE,Gravity.START))
        root.addView(text("Enter the router IP, API port and credentials.",13,false,Color.rgb(165,190,215),Gravity.START))
        val ip=field("MikroTik Router IP Address","192.168.88.1")
        val u=field("Username","admin")
        val p=field("Password","",true)
        val port=field("API Port (1-65535)","8728")
        root.addView(primaryButton("TEST & CONNECT"){
            val host=ip.text.toString().trim()
            if(host.isBlank()){toast("Router IP is required");return@primaryButton}
            val po=port.text.toString().trim().toIntOrNull()
            if(po==null||po !in 1..65535){toast("API port must be 1-65535");return@primaryButton}
            connect(host,po,u.text.toString().trim(),p.text.toString(),po==8729)
        })
        root.addView(secondaryButton("BACK"){home()})
    }

    private fun autoSearch() {
        if(searching)return
        searching=true
        base("AUTO SEARCH")
        root.addView(text("MikroTik Router Discovery",20,true,Color.WHITE,Gravity.START))
        root.addView(text("Searching this Wi-Fi/LAN for both MikroTik API ports: 8728 and 8729.",13,false,Color.rgb(165,190,215),Gravity.START))
        status.text="Preparing local network scan…"
        result=text("",14,false,Color.WHITE,Gravity.START)
        result.setPadding(0,14,0,14)
        root.addView(result)
        root.addView(secondaryButton("CANCEL / HOME"){searching=false;home()})
        ex.execute {
            val found=try{scanLocalNetwork()}catch(e:Exception){emptyList()}
            ui {
                searching=false
                if(isFinishing)return@ui
                status.text=if(found.isEmpty())"No MikroTik API service found on this Wi-Fi/LAN." else "Found "+found.size+" MikroTik API service(s)."
                result.text=if(found.isEmpty())
                    "Check:\n• Phone and MikroTik are on the same Wi-Fi/LAN\n• IP → Services → api or api-ssl is enabled\n• API port is 8728 or 8729\n• Router firewall/service address is not blocking this phone"
                else "Select a router. The app will ask for username and password:"
                found.forEach{item->root.addView(routerCard(item.first,item.second),root.indexOfChild(result)+1)}
            }
        }
    }

    private fun routerCard(ip:String,port:Int):View {
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(18,14,18,14)
            background=rounded(Color.rgb(18,39,68),Color.rgb(48,88,135),16f)
        }
        card.addView(text("MIKROTIK ROUTER",11,true,Color.rgb(105,190,255),Gravity.START))
        card.addView(text(ip,18,true,Color.WHITE,Gravity.START))
        card.addView(text(if(port==8729)"API-SSL 8729 • Tap to connect" else "API 8728 • Tap to connect",12,false,Color.rgb(170,195,220),Gravity.START))
        card.setOnClickListener{credentials(ip,port)}
        card.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,6,0,6)}
        return card
    }

    private fun credentials(ip:String,port:Int){
        base("ROUTER FOUND")
        root.addView(text("MikroTik • "+ip,22,true,Color.WHITE,Gravity.CENTER))
        root.addView(text(if(port==8729)"API-SSL port 8729" else "API port 8728",13,false,Color.rgb(165,190,215),Gravity.CENTER))
        val u=field("Username","admin")
        val p=field("Password","",true)
        root.addView(primaryButton("CONNECT TO MIKROTIK"){connect(ip,port,u.text.toString().trim(),p.text.toString(),port==8729)})
        root.addView(secondaryButton("BACK TO SEARCH"){autoSearch()})
    }

    private fun connect(ip:String,port:Int,u:String,p:String,ssl:Boolean=(port==8729)){
        status.text="Connecting to "+ip+":"+port+"…"
        ex.execute{
            try{
                api.connect(ip,port,u,p,7000,ssl)
                connected=true
                ui{if(!isFinishing)dashboard(ip)}
            }catch(e:Exception){
                connected=false
                ui{status.text="Connection failed: "+(e.message?:"Unknown error");toast(e.message?:"Unable to connect to MikroTik")}
            }
        }
    }

    private fun dashboard(ip:String){
        base("ROUTER: "+ip)
        status.text="●  CONNECTED";status.setTextColor(Color.rgb(80,220,145))
        root.addView(primaryButton("HOTSPOT USERS"){task{showRows(api.getUsers(),"HOTSPOT USERS")}})
        root.addView(primaryButton("ACTIVE / ONLINE USERS"){task{showRows(api.getActive(),"ACTIVE USERS")}})
        root.addView(text("ADD USER / VOUCHER",19,true,Color.WHITE,Gravity.START))
        val n=field("Username / Voucher","");val pw=field("Password (blank = username)","")
        val pr=field("Profile","default");val sp=field("Speed e.g. 2M/2M","")
        val da=field("Data limit e.g. 1G","");val ti=field("Time limit e.g. 2h","")
        root.addView(primaryButton("ADD USER"){
            val x=n.text.toString().trim()
            if(x.isBlank()){toast("Username required");return@primaryButton}
            task{api.addUser(x,pw.text.toString().ifBlank{x},pr.text.toString(),sp.text.toString(),da.text.toString(),ti.text.toString());toast("User added")}
        })
        root.addView(primaryButton("BULK GENERATE 10 VOUCHERS"){task{
            val made=mutableListOf<String>()
            repeat(10){val v=randomCode();api.addUser(v,v,pr.text.toString(),sp.text.toString(),da.text.toString(),ti.text.toString());made+=v}
            ui{result.text=made.joinToString("\n");toast("10 vouchers created")}
        }})
        val id=field("User .id","")
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        listOf("DELETE","DISABLE","ENABLE").forEach{label->
            row.addView(secondaryButton(label){task{
                when(label){"DELETE"->api.removeUser(id.text.toString());"DISABLE"->api.disableUser(id.text.toString());else->api.enableUser(id.text.toString())}
                toast(label+" completed")
            }},LinearLayout.LayoutParams(0,56,1f).apply{setMargins(3,3,3,3)})
        }
        root.addView(row)
        result=text("",14,false,Color.WHITE,Gravity.START);result.setTextIsSelectable(true);result.setPadding(0,16,0,16);root.addView(result)
        root.addView(secondaryButton("DISCONNECT / HOME"){api.close();connected=false;home()})
    }

    private fun scanLocalNetwork():List<Pair<String,Int>>{
        val cm=getSystemService(ConnectivityManager::class.java)
        val network=cm.activeNetwork?:throw Exception("No active network")
        val caps=cm.getNetworkCapabilities(network)
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            throw Exception("Connect phone to the same Wi-Fi as MikroTik")
        val lp=cm.getLinkProperties(network)?:throw Exception("Could not read Wi-Fi network")
        val la=lp.linkAddresses.firstOrNull{it.address is Inet4Address}?:throw Exception("No IPv4 on Wi-Fi")
        val raw=(la.address as Inet4Address).address
        val prefix=la.prefixLength
        val scanPrefix=if(prefix<24)24 else prefix
        val mask=if(scanPrefix>=32)-1 else(-1 shl (32-scanPrefix))
        val ipInt=((raw[0].toInt() and 255) shl 24) or ((raw[1].toInt() and 255) shl 16) or
                ((raw[2].toInt() and 255) shl 8) or (raw[3].toInt() and 255)
        val networkInt=ipInt and mask
        val hostCount=if(scanPrefix>=31)0 else (1 shl (32-scanPrefix))-2
        if(hostCount<=0)throw Exception("Wi-Fi network is too small for discovery")
        val candidates=(1..hostCount).map{offset->
            val n=networkInt+offset
            ((n ushr 24) and 255).toString()+"."+((n ushr 16) and 255)+"."+((n ushr 8) and 255)+"."+(n and 255)
        }.distinct()
        val found=Collections.synchronizedList(mutableListOf<Pair<String,Int>>())
        val pool=Executors.newFixedThreadPool(32)
        try{
            val futures=candidates.flatMap{candidate->
                listOf(8728,8729).map{port->pool.submit{
                    if(probeApi(candidate,port,port==8729))found.add(candidate to port)
                }}
            }
            futures.forEach{it.get()}
        }finally{pool.shutdownNow()}
        return found.distinct().sortedWith(compareBy({it.first},{it.second}))
    }

    private fun probeApi(ip:String,port:Int,ssl:Boolean):Boolean=try{
        if(ssl){
            val s=javax.net.ssl.SSLContext.getInstance("TLS").socketFactory.createSocket() as javax.net.ssl.SSLSocket
            s.use{
                it.soTimeout=900
                it.connect(InetSocketAddress(ip,port),700)
                it.startHandshake()
                true
            }
        }else{
            Socket().use{s->
                s.soTimeout=900
                s.connect(InetSocketAddress(ip,port),700)
                val input=java.io.BufferedInputStream(s.getInputStream())
                val output=java.io.BufferedOutputStream(s.getOutputStream())
                val word="/login".toByteArray(Charsets.UTF_8)
                writeApiLength(output,word.size);output.write(word);output.write(0);output.flush()
                val first=readApiWord(input)
                first!=null && (first=="!done" || first=="!trap" || first=="!re")
            }
        }
    }catch(_:Exception){false}

    private fun writeApiLength(out:java.io.OutputStream,n:Int){
        when{
            n<128->out.write(n)
            n<16384->{out.write((n shr 8) or 128);out.write(n and 255)}
            n<2097152->{out.write((n shr 16) or 192);out.write((n shr 8) and 255);out.write(n and 255)}
            else->{out.write((n shr 24) or 224);out.write((n shr 16) and 255);out.write((n shr 8) and 255);out.write(n and 255)}
        }
    }

    private fun readApiWord(input:java.io.InputStream):String?{
        val b=input.read()
        if(b<0)return null
        val n=when{
            b and 128==0->b
            b and 192==128->((b and 63) shl 8) or input.read()
            b and 224==192->((b and 31) shl 16) or (input.read() shl 8) or input.read()
            b and 240==224->((b and 15) shl 24) or (input.read() shl 16) or (input.read() shl 8) or input.read()
            else->return null
        }
        if(n<=0)return null
        val data=ByteArray(n);var pos=0
        while(pos<n){val r=input.read(data,pos,n-pos);if(r<0)return null;pos+=r}
        return String(data,Charsets.UTF_8)
    }

    private fun base(title:String){
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(24,26,24,28)
            background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(7,20,42),Color.rgb(11,43,78)))
        }
        val scroll=ScrollView(this);scroll.setFillViewport(true);scroll.addView(root)
        root.addView(text("NEW MIKHMON",13,true,Color.rgb(105,190,255),Gravity.CENTER))
        root.addView(text(title,12,true,Color.rgb(145,175,205),Gravity.CENTER))
        status=text("",13,false,Color.rgb(175,205,235),Gravity.CENTER);root.addView(status)
        setContentView(scroll)
    }

    private fun actionCard(title:String,sub:String,tag:String,click:()->Unit):View{
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(20,18,20,18)
            background=rounded(Color.rgb(16,43,76),Color.rgb(43,104,163),20f)
        }
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        top.addView(text(title,19,true,Color.WHITE,Gravity.START),LinearLayout.LayoutParams(0,-2,1f))
        top.addView(text(tag,11,true,Color.rgb(110,205,255),Gravity.CENTER))
        card.addView(top);card.addView(text(sub,13,false,Color.rgb(170,200,225),Gravity.START))
        card.setOnClickListener{click()};return card
    }

    private fun field(h:String,v:String,secret:Boolean=false):EditText{
        val e=EditText(this).apply{
            hint=h;setText(v);textSize=16f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(130,160,190))
            setSingleLine(true);setPadding(16,0,16,0);background=rounded(Color.rgb(17,37,63),Color.rgb(50,92,135),14f)
            if(secret)inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(e,LinearLayout.LayoutParams(-1,56).apply{setMargins(0,6,0,8)});return e
    }

    private fun primaryButton(s:String,click:()->Unit)=Button(this).apply{
        text=s;textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.WHITE)
        background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.rgb(18,107,210),Color.rgb(27,157,220))).apply{cornerRadius=16f}
        stateListAnimator=null;setOnClickListener{click()};layoutParams=LinearLayout.LayoutParams(-1,58).apply{setMargins(0,6,0,8)}
    }

    private fun secondaryButton(s:String,click:()->Unit)=Button(this).apply{
        text=s;textSize=12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(195,220,240))
        background=rounded(Color.rgb(17,37,63),Color.rgb(57,95,130),14f);stateListAnimator=null;setOnClickListener{click()}
        layoutParams=LinearLayout.LayoutParams(-1,52).apply{setMargins(0,5,0,7)}
    }

    private fun rounded(fill:Int,stroke:Int,radius:Float)=GradientDrawable().apply{setColor(fill);setStroke(2,stroke);cornerRadius=radius}

    private fun text(s:String,z:Int,b:Boolean,c:Int,g:Int)=TextView(this).apply{text=s;textSize=z.toFloat();setTextColor(c);gravity=g;if(b)typeface=Typeface.DEFAULT_BOLD}

    private fun task(block:()->Unit){
        if(!connected){toast("Connect to MikroTik first");return}
        ex.execute{try{block()}catch(e:Exception){ui{toast(e.message?:"Operation failed")}}}
    }

    private fun showRows(rows:List<Map<String,String>>,title:String)=ui{
        result.text=title+"\n\n"+rows.mapIndexed{i,r->
            (i+1).toString()+". "+(r["name"]?:"?")+"  id="+(r[".id"]?:"?")+"  profile="+(r["profile"]?:"")+"  uptime="+(r["uptime"]?:"")
        }.joinToString("\n")
    }

    private fun randomCode():String{val c="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";return(1..8).map{c[Random.nextInt(c.length)]}.joinToString("")}
    private fun ui(b:()->Unit)=runOnUiThread(b)
    private fun toast(s:String)=ui{Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
    override fun onDestroy(){api.close();ex.shutdownNow();super.onDestroy()}
}
