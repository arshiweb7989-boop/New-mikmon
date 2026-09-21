package com.newmikhmon.app
import android.app.*
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity:Activity(){
 private val api=RouterOsApi(); private val ex=Executors.newCachedThreadPool()
 private var connected=false; private lateinit var root:LinearLayout; private lateinit var status:TextView; private lateinit var result:TextView
 override fun onCreate(b:Bundle?){super.onCreate(b);home()}
 private fun home(){
  base("HOME")
  root.addView(button("MANUAL CONNECT"){manual()},LinearLayout.LayoutParams(-1,70))
  root.addView(Space(this),LinearLayout.LayoutParams(1,18))
  root.addView(button("AUTO SEARCH"){autoSearch()},LinearLayout.LayoutParams(-1,70))
  root.addView(text("Manual: Router IP + username + password\nAuto Search: find MikroTik routers on the local Wi-Fi, then ask for username/password.",14,false,Color.GRAY,Gravity.CENTER))
 }
 private fun manual(){
  base("MANUAL CONNECT")
  val ip=field("MikroTik Router IP Address","192.168.88.1"); val u=field("Username","admin"); val p=field("Password","",true)
  root.addView(button("TEST & CONNECT"){connect(ip.text.toString().trim(),8728,u.text.toString(),p.text.toString())})
  root.addView(button("BACK"){home()})
 }
 private fun autoSearch(){
  base("AUTO SEARCH"); status.text="Searching local Wi-Fi for MikroTik API (8728)…"
  root.addView(status); result=TextView(this); root.addView(result)
  ex.execute{
   val f=scan()
   ui{status.text=if(f.isEmpty())"No MikroTik API found. Check same Wi-Fi and API service 8728." else "Routers found: "+f.size; result.text=""
    f.forEach{ip->root.addView(button(ip+"  •  MikroTik API"){credentials(ip)})}}
  }
  root.addView(button("HOME"){home()})
 }
 private fun credentials(ip:String){
  base("ROUTER FOUND: "+ip); val u=field("Username","admin"); val p=field("Password","",true)
  root.addView(button("CONNECT"){connect(ip,8728,u.text.toString(),p.text.toString())})
  root.addView(button("BACK TO SEARCH"){autoSearch()})
 }
 private fun connect(ip:String,port:Int,u:String,p:String){
  status.text="Connecting to "+ip+":"+port+"…"
  ex.execute{try{api.connect(ip,port,u,p);connected=true;ui{dashboard(ip)}}catch(e:Exception){connected=false;ui{status.text="Connection failed: "+(e.message?:"Unknown error");toast(e.message?:"Connection failed")}}}
 }
 private fun dashboard(ip:String){
  base("ROUTER: "+ip);status.text="● Connected";status.setTextColor(Color.rgb(46,125,50))
  root.addView(button("HOTSPOT USERS"){task{showRows(api.getUsers(),"HOTSPOT USERS")}})
  root.addView(button("ACTIVE / ONLINE USERS"){task{showRows(api.getActive(),"ACTIVE USERS")}})
  root.addView(text("ADD USER / VOUCHER",19,true,Color.rgb(13,71,161),Gravity.START))
  val n=field("Username / Voucher","");val pw=field("Password (blank = username)","");val pr=field("Profile","default");val sp=field("Speed e.g. 2M/2M","");val da=field("Data limit e.g. 1G","");val ti=field("Time limit e.g. 2h","")
  root.addView(button("ADD USER"){val x=n.text.toString().trim();if(x.isBlank()){toast("Username required");return@button};task{api.addUser(x,pw.text.toString().ifBlank{x},pr.text.toString(),sp.text.toString(),da.text.toString(),ti.text.toString());toast("User added")}})
  root.addView(button("BULK GENERATE 10 VOUCHERS"){task{val made=mutableListOf<String>();repeat(10){val v=randomCode();api.addUser(v,v,pr.text.toString(),sp.text.toString(),da.text.toString(),ti.text.toString());made+=v};ui{result.text=made.joinToString("\n");toast("10 vouchers created")}}})
  val id=field("User .id","");val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  listOf("DELETE","DISABLE","ENABLE").forEach{label->row.addView(button(label){task{when(label){"DELETE"->api.removeUser(id.text.toString());"DISABLE"->api.disableUser(id.text.toString());else->api.enableUser(id.text.toString())};toast(label+" completed")}},LinearLayout.LayoutParams(0,60,1f))}
  root.addView(row);result=TextView(this);result.setTextIsSelectable(true);root.addView(result);root.addView(button("DISCONNECT / HOME"){api.close();connected=false;home()})
 }
 private fun scan():List<String>{
  val out=mutableListOf<String>(); val nets=listOf("192.168.88.","192.168.1.","192.168.0.","10.0.0.")
  for(net in nets){val fs=(1..254).map{n->ex.submit<Boolean>{probe(net+n)}};fs.forEachIndexed{i,f->if(f.get())out.add(net+(i+1))}}
  return out.distinct()
 }
 private fun probe(ip:String)=try{Socket().use{s->s.connect(InetSocketAddress(ip,8728),250);true}}catch(_:Exception){false}
 private fun base(title:String){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,24,22,28)};val s=ScrollView(this);s.addView(root);root.addView(text("NEW MIKHMON",27,true,Color.rgb(13,71,161),Gravity.CENTER));root.addView(text(title,19,true,Color.DKGRAY,Gravity.CENTER));status=text("",14,false,Color.GRAY,Gravity.CENTER);root.addView(status);setContentView(s)}
 private fun field(h:String,v:String,secret:Boolean=false):EditText{val e=EditText(this);e.hint=h;e.setText(v);e.textSize=16f;if(secret)e.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;root.addView(e);return e}
 private fun button(s:String,click:()->Unit)=Button(this).apply{text=s;setOnClickListener{click()}}
 private fun text(s:String,z:Float,b:Boolean,c:Int,g:Int)=TextView(this).apply{text=s;textSize=z;setTextColor(c);gravity=g;if(b)typeface=Typeface.DEFAULT_BOLD}
 private fun task(block:()->Unit){if(!connected){toast("Connect to MikroTik first");return};ex.execute{try{block()}catch(e:Exception){ui{toast(e.message?:"Operation failed")}}}}
 private fun showRows(rows:List<Map<String,String>>,title:String){ui{result.text=title+"\n\n"+rows.mapIndexed{i,r->(i+1).toString()+". "+(r["name"]?:"?")+" id="+(r[".id"]?:"?")+" profile="+(r["profile"]?:"")+" uptime="+(r["uptime"]?:"")}.joinToString("\n")}}
 private fun randomCode():String{val c="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";return(1..8).map{c[Random.nextInt(c.length)]}.joinToString("")}
 private fun ui(b:()->Unit)=runOnUiThread(b);private fun toast(s:String)=ui{Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
 override fun onDestroy(){api.close();ex.shutdownNow();super.onDestroy()}
}