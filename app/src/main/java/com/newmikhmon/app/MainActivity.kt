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
        root.addView(text("API 8728  •  RouterOS v6 / v7",12,false,Color.rgb(140,170,200),Gravity.CENTER))
    }

    private fun manual() {
        base("MANUAL CONNECT")
        root.addView(text("Connect to your MikroTik router",20,true,Color.WHITE,Gravity.START))
        root.addView(text("Enter the router's local IP and API credentials.",13,false,Color.rgb(165,190,215),Gravity.START))
        val ip=field("MikroTik Router IP Address","192.168.88.1")
        val u=field("Username","admin")
        val p=field("Password","",true)
        root.addView(primaryButton("TEST & CONNECT"){
            val host=ip.text.toString().trim()
            if(host.isBlank()){toast("Router IP is required");return@primaryButton}
            connect(host,8728,u.text.toString().trim(),p.text.toString())
        })
        root.addView(secondaryButton("BACK"){home()})
    }

    private fun autoSearch() {
        if(searching)return
        searching=true
        base("AUTO SEARCH")
        root.addView(text("MikroTik Router Discovery",20,true,Color.WHITE,Gravity.START))
        root.addView(text("Scanning only the current Wi-Fi/LAN for API 8728. This replaces the old fixed-network scan that could freeze the app.",13,false,Color.rgb(165,190,215),Gravity.START))
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
                status.text=if(found.isEmpty())"No API 8728 router found on this Wi-Fi." else "Found "+found.size+" possible router(s)."
                result.text=if(found.isEmpty())
                    "Check:\n• Phone and MikroTik are on the same Wi-Fi/LAN\n• IP → Services → api is enabled\n• API port is 8728"
                else "Select a router to enter username and password:"
                found.forEach{ip->root.addView(routerCard(ip),root.indexOfChild(result)+1)}
            }
        }
    }

    private fun routerCard(ip:String):View {
        val card=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(18,14,18,14)
            background=rounded(Color.rgb(18,39,68),Color.rgb(48,88,135),16f)
        }
        card.addView(text("MIKROTIK ROUTER",11,true,Color.rgb(105,190,255),Gravity.START))
        card.addView(text(ip,18,true,Color.WHITE,Gravity.START))
        card.addView(text("API 8728 • Tap to connect",12,false,Color.rgb(170,195,220),Gravity.START))
        card.setOnClickListener{credentials(ip)}
        card.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,6,0,6)}
        return card
    }

    private fun credentials(ip:String){
        base("ROUTER FOUND")
        root.addView(text("MikroTik • "+ip,22,true,Color.WHITE,Gravity.CENTER))
        root.addView(text("Enter router credentials",13,false,Color.rgb(165,190,215),Gravity.CENTER))
        val u=field("Username","admin");val p=field("Password","",true)
        root.addView(primaryButton("CONNECT TO MIKROTIK"){connect(ip,8728,u.text.toString().trim(),p.text.toString())})
        root.addView(secondaryButton("BACK TO SEARCH"){autoSearch()})
    }

    private fun connect(ip:String,port:Int,u:String,p:String){
        status.text="Connecting to "+ip+":"+port+"…"
        ex.execute{
            try{api.connect(ip,port,u,p);connected=true;ui{if(!isFinishing)dashboard(ip)}}
            catch(e:Exception){connected=false;ui{status.text="Connection failed";toast(e.message?:"Unable to connect to MikroTik")}}
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

    private fun scanLocalNetwork():List<String>{
        val cm=getSystemService(ConnectivityManager::class.java)
        val network=cm.activeNetwork?:throw Exception("No active network")
        val caps=cm.getNetworkCapabilities(network)
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            throw Exception("Connect phone to the same Wi-Fi as MikroTik")
        val lp=cm.getLinkProperties(network)?:throw Exception("Could not read Wi-Fi network")
        val la=lp.linkAddresses.firstOrNull{it.address is Inet4Address}?:throw Exception("No IPv4 on Wi-Fi")
        val raw=(la.address as Inet4Address).address
        val prefix=la.prefixLength
        val effectivePrefix=maxOf(prefix,24)
        val mask=if(effectivePrefix==32)-1 else(-1 shl (32-effectivePrefix))
        val ipInt=((raw[0].toInt() and 255) shl 24) or ((raw[1].toInt() and 255) shl 16) or
                ((raw[2].toInt() and 255) shl 8) or (raw[3].toInt() and 255)
        val networkInt=ipInt and mask
        val candidates=(1..254).map{h->
            val n=(networkInt and -256) or h
            ((n ushr 24) and 255).toString()+"."+((n ushr 16) and 255)+"."+((n ushr 8) and 255)+"."+(n and 255)
        }.distinct()
        val found=Collections.synchronizedList(mutableListOf<String>())
        val pool=Executors.newFixedThreadPool(24)
        try{
            val futures=candidates.mapIndexed{index,candidate->
                pool.submit{
                    if(probe(candidate))found.add(candidate)
                    if(index%20==0)ui{if(!isFinishing&&searching)status.text="Scanning local Wi-Fi… "+(index+1)+"/"+candidates.size}
                }
            }
            futures.forEach{it.get()}
        }finally{pool.shutdownNow()}
        return found.distinct().sorted()
    }

    private fun probe(ip:String)=try{
        Socket().use{s->s.connect(InetSocketAddress(ip,8728),300);true}
    }catch(_:Exception){false}

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
