package com.newmikhmon.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : Activity() {
    private val api=RouterOsApi()
    private val ex=Executors.newSingleThreadExecutor()
    private lateinit var status:TextView
    private lateinit var host:EditText
    private lateinit var port:EditText
    private lateinit var user:EditText
    private lateinit var pass:EditText
    private lateinit var profile:EditText
    private lateinit var speed:EditText
    private lateinit var data:EditText
    private lateinit var time:EditText
    private lateinit var result:TextView
    private var connected=false

    override fun onCreate(b:Bundle?) { super.onCreate(b); buildUi() }

    private fun buildUi() {
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,24,28,28) }
        scroll.addView(root)
        root.addView(TextView(this).apply { text="NEW MIKHMON"; textSize=28f; setTextColor(Color.rgb(13,71,161)); gravity=Gravity.CENTER })
        root.addView(TextView(this).apply { text="MikroTik Hotspot Manager • RouterOS v6 / v7"; gravity=Gravity.CENTER; setTextColor(Color.DKGRAY) })
        status=TextView(this).apply { text="● Disconnected"; textSize=16f; setPadding(0,20,0,12) }
        root.addView(status)

        host=field(root,"Router IP / Host","192.168.88.1")
        port=field(root,"API Port","8728")
        user=field(root,"Username","admin")
        pass=field(root,"Password","",true)
        root.addView(Button(this).apply { text="TEST & CONNECT"; setOnClickListener { connect() } })

        root.addView(section("HOTSPOT USERS"))
        root.addView(Button(this).apply { text="REFRESH USERS"; setOnClickListener { task { showRows(api.getUsers(),"USERS") } } })
        root.addView(Button(this).apply { text="ACTIVE / ONLINE USERS"; setOnClickListener { task { showRows(api.getActive(),"ACTIVE USERS") } } })

        root.addView(section("ADD USER / VOUCHER"))
        val name=field(root,"Username / Voucher","")
        val pwd=field(root,"Password (blank = same as username)","")
        profile=field(root,"Profile (optional)","default")
        speed=field(root,"Speed e.g. 2M/2M","")
        data=field(root,"Data limit e.g. 1G","")
        time=field(root,"Time limit e.g. 2h","")
        root.addView(Button(this).apply {
            text="ADD USER"
            setOnClickListener {
                task {
                    val n=name.text.toString().trim()
                    if(n.isBlank()) throw Exception("Username required")
                    val p=pwd.text.toString().ifBlank { n }
                    api.addUser(n,p,profile.text.toString(),speed.text.toString(),data.text.toString(),time.text.toString())
                    toast("User added: " + n)
                }
            }
        })
        root.addView(Button(this).apply {
            text="BULK GENERATE 10 VOUCHERS"
            setOnClickListener {
                task {
                    val made=mutableListOf<String>()
                    repeat(10) {
                        val v=randomCode()
                        api.addUser(v,v,profile.text.toString(),speed.text.toString(),data.text.toString(),time.text.toString())
                        made += v
                    }
                    ui { result.text=made.joinToString("\n"); toast("10 vouchers created") }
                }
            }
        })

        root.addView(section("USER ACTIONS"))
        val id=field(root,"User .id (from Users list)","")
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("DELETE","DISABLE","ENABLE").forEach { label ->
            val btn=Button(this).apply {
                text=label
                setOnClickListener { task {
                    when(label) {
                        "DELETE" -> api.removeUser(id.text.toString())
                        "DISABLE" -> api.disableUser(id.text.toString())
                        else -> api.enableUser(id.text.toString())
                    }
                    toast(label + " completed")
                }}
            }
            row.addView(btn,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        }
        root.addView(row)

        root.addView(section("RESULT"))
        result=TextView(this).apply { textSize=14f; setTextIsSelectable(true); setPadding(8,8,8,8) }
        root.addView(result)
        setContentView(scroll)
    }

    private fun section(s:String)=TextView(this).apply { text=s; textSize=18f; setTextColor(Color.rgb(13,71,161)); setPadding(0,24,0,8) }
    private fun field(root:LinearLayout,label:String,value:String,secret:Boolean=false):EditText {
        val e=EditText(this); e.hint=label; e.setText(value); e.textSize=16f
        if(secret) e.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(e,LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT))
        return e
    }
    private fun connect() {
        val h=host.text.toString().trim()
        val p=port.text.toString().toIntOrNull() ?: 8728
        val u=user.text.toString()
        val pw=pass.text.toString()
        ex.execute {
            try {
                api.connect(h,p,u,pw)
                connected=true
                ui { status.text="● Connected to " + h + ":" + p; status.setTextColor(Color.rgb(46,125,50)); toast("MikroTik connected") }
            } catch(e:Exception) {
                connected=false
                ui { status.text="● Connection failed"; status.setTextColor(Color.RED); toast(e.message ?: "Connection failed") }
            }
        }
    }
    private fun task(block:()->Unit) {
        if(!connected) { toast("Pehle TEST & CONNECT karein"); return }
        ex.execute { try { block() } catch(e:Exception) { ui { toast(e.message ?: "Operation failed") } } }
    }
    private fun showRows(rows:List<Map<String,String>>,title:String) {
        ui {
            result.text=title+"\n\n"+rows.mapIndexed { idx,r ->
                (idx+1).toString()+". "+(r["name"]?:r["user"]?:"?")+
                "  id="+(r[".id"]?:"?")+"  profile="+(r["profile"]?:"")+"  uptime="+(r["uptime"]?:"")
            }.joinToString("\n")
        }
    }
    private fun randomCode():String {
        val chars="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
    private fun ui(block:()->Unit)=runOnUiThread(block)
    private fun toast(s:String)=ui { Toast.makeText(this,s,Toast.LENGTH_SHORT).show() }
    override fun onDestroy(){ api.close(); ex.shutdownNow(); super.onDestroy() }
}
