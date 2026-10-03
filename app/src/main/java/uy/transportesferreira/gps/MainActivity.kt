package uy.transportesferreira.gps
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.max
class MainActivity:AppCompatActivity(){
 private val navy=Color.rgb(16,35,61);private val blue=Color.rgb(28,100,242);private val muted=Color.rgb(99,115,136);private val pale=Color.rgb(241,246,252)
 private lateinit var root:LinearLayout
 private lateinit var store:TripStore
 private lateinit var api:Api
 private var screen="";private var name="";private var truck="";private var empty=false
 private var photo:File?=null;private var camera:File?=null
 private var distance:TextView?=null;private var status:TextView?=null;private var sync:TextView?=null;private var gps:TextView?=null;private var pause:Button?=null;private var count:TextView?=null
 private val handler=Handler(Looper.getMainLooper());private val tick=object:Runnable{override fun run(){refresh();handler.postDelayed(this,1000)}}
 private val trucks=arrayOf("Seleccioná un camión","Ford Cargo 1722","Mercedes-Benz 1618","Leyland","Mercedes-Benz 1630")
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)begin()
  else AlertDialog.Builder(this).setTitle("Activá la ubicación precisa").setMessage("El cuentakilómetros necesita ubicación precisa. Habilitala en los permisos de la app.").setPositiveButton("Abrir ajustes"){_,_->startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}.setNegativeButton("Ahora no",null).show()
 }
 private val take=registerForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)camera?.let{preparePhoto(Uri.fromFile(it))}else camera?.delete()}
 private val pick=registerForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)preparePhoto(uri)}
 override fun onCreate(b:Bundle?){super.onCreate(b);store=TripStore(this);api=Api(this);camera=b?.getString("camera")?.let{File(it)};photo=b?.getString("photo")?.let{File(it)}?.takeIf{it.exists()};truck=b?.getString("truck")?:"";empty=b?.getBoolean("empty")?:false
  window.statusBarColor=pale;window.navigationBarColor=pale
  if(api.session()==null)login() else {name=driverName();if(b?.getString("screen")=="fuel"&&store.current()?.optBoolean("active")==true)fuel() else homeOrTrip();Sync.schedule(this)}
 }
 override fun onSaveInstanceState(b:Bundle){super.onSaveInstanceState(b);b.putString("camera",camera?.path);b.putString("photo",photo?.path);b.putString("screen",screen);b.putString("truck",truck);b.putBoolean("empty",empty)}
 override fun onStart(){super.onStart();handler.post(tick)}
 override fun onStop(){handler.removeCallbacks(tick);super.onStop()}
 @Deprecated("Deprecated in Java") override fun onBackPressed(){if(screen=="fuel")trip() else if(screen=="trip")moveTaskToBack(true) else super.onBackPressed()}
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun bg(color:Int,r:Int=20)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
 private fun page(key:String){screen=key;distance=null;status=null;sync=null;gps=null;pause=null;count=null
  val scroll=ScrollView(this).apply{setBackgroundColor(pale);isFillViewport=true};root=LinearLayout(this).apply{orientation=1;setPadding(dp(24),dp(24),dp(24),dp(32))};scroll.addView(root);setContentView(scroll)
  ViewCompat.setOnApplyWindowInsetsListener(scroll){v,i->val s=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime());v.setPadding(s.left,s.top,s.right,s.bottom);i}
  label(root,"TR FERREIRA  /  CHOFERES",12f,blue,true).apply{letterSpacing=.12f;setPadding(0,0,0,dp(24))}
 }
 private fun label(p:LinearLayout,s:String,size:Float=16f,color:Int=navy,bold:Boolean=false):TextView{val v=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD);setPadding(0,dp(6),0,dp(8))};p.addView(v,LinearLayout.LayoutParams(-1,-2));return v}
 private fun card():LinearLayout{val v=LinearLayout(this).apply{orientation=1;background=bg(Color.WHITE);setPadding(dp(20),dp(18),dp(20),dp(18))};root.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18);bottomMargin=dp(4)});return v}
 private fun button(p:LinearLayout,s:String,primary:Boolean=true,color:Int=blue,action:()->Unit):Button{val v=Button(this).apply{text=s;isAllCaps=false;textSize=16f;setTypeface(null,Typeface.BOLD);setTextColor(if(primary)Color.WHITE else color);backgroundTintList=null;background=bg(if(primary)color else Color.rgb(231,239,253),14);minHeight=dp(56);setPadding(dp(12),dp(14),dp(12),dp(14));setOnClickListener{action()}};p.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)});return v}
 private fun field(p:LinearLayout,s:String,type:Int):EditText{label(p,s,13f,muted,true);return EditText(this).apply{inputType=type;textSize=17f;setTextColor(navy);background=bg(pale,12);setPadding(dp(14),dp(14),dp(14),dp(14));isSingleLine=true;p.addView(this,LinearLayout.LayoutParams(-1,dp(56)))}}
 private fun login(){page("login");label(root,"Tu viaje empieza acá.",32f,navy,true);label(root,"Ingresá con tu cuenta de chofer.",16f,muted)
  val c=card();label(c,"Iniciar sesión",21f,navy,true);val email=field(c,"Correo electrónico",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);email.setText(getSharedPreferences("gps",MODE_PRIVATE).getString("email",""));val pass=field(c,"Contraseña",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD);val msg=label(c,"",14f,muted)
  lateinit var submit:Button;submit=button(c,"Ingresar"){val e=email.text.toString().trim().lowercase();val p=pass.text.toString();if(e.isBlank()||p.isBlank()){msg.text="Completá correo y contraseña";return@button};submit.isEnabled=false;msg.text="Ingresando…"
   thread{try{api.login(e,p);name=driverName();runOnUiThread{pass.text.clear();homeOrTrip();Sync.schedule(this)}}catch(_:Exception){runOnUiThread{submit.isEnabled=true;msg.text="No se pudo ingresar. Revisá tus datos y la conexión."}}}}
  label(root,"Tu sesión queda guardada de forma segura en este teléfono.",13f,muted)
 }
 private fun driverName():String{val u=api.session()?.optJSONObject("user")?:return "Chofer";val m=u.optJSONObject("user_metadata");val known=mapOf("immer@trferreira.com" to "Immer Sampayo","luis@trferreira.com" to "Luis Ferreira","hugo@trferreira.com" to "Hugo Silva");return m?.optString("full_name")?.takeIf{it.isNotBlank()}?:m?.optString("name")?.takeIf{it.isNotBlank()}?:known[u.optString("email")]?:u.optString("email").substringBefore("@").replaceFirstChar{it.uppercase()}}
 private fun homeOrTrip(){if(store.current()?.optBoolean("active")==true)trip() else home()}
 private fun home(){page("home");label(root,"Hola,\n$name",30f,navy,true);label(root,"Prepará tu próximo viaje.",16f,muted)
  val c=card();label(c,"01  /  TU CAMIÓN",12f,blue,true);val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,trucks);minimumHeight=dp(56);background=bg(pale,12)};c.addView(spinner,LinearLayout.LayoutParams(-1,dp(60)));spinner.setSelection(trucks.indexOf(truck).coerceAtLeast(0));spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){truck=if(pos>0)trucks[pos] else ""}}
  label(c,"02  /  TIPO DE VIAJE",12f,blue,true);val group=RadioGroup(this);val a=RadioButton(this).apply{id=View.generateViewId();text="Con carga";setTextColor(navy);minHeight=dp(52);textSize=17f};val b=RadioButton(this).apply{id=View.generateViewId();text="Sin carga / retorno vacío";setTextColor(navy);minHeight=dp(52);textSize=17f};group.addView(a);group.addView(b);group.check(if(empty)b.id else a.id);group.setOnCheckedChangeListener{_,id->empty=id==b.id};c.addView(group)
  button(root,"Iniciar nuevo viaje  →"){if(truck.isBlank())toast("Seleccioná el camión") else requestStart()};label(root,"El GPS registra el recorrido mientras el viaje está en curso.",13f,muted)
  val sc=card();label(sc,"Sincronización",15f,navy,true);sync=label(sc,store.message(),14f,muted);button(sc,"Reintentar envío",false){Sync.schedule(this);toast("Envío programado")};button(sc,"Renovar acceso",false){login()};button(root,"Cerrar sesión",false){if(store.pendingCount()>0){toast("Hay datos pendientes. Sincronizalos antes de cerrar sesión.");return@button};api.clear();login()}
 }
 private fun requestStart(){val req=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=33)req.add(Manifest.permission.POST_NOTIFICATIONS);if(req.any{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED})permissions.launch(req.toTypedArray()) else begin()}
 private fun begin(){val u=api.session()?.optJSONObject("user")?:return login();try{store.start(u,name,truck,empty);service();trip();Sync.schedule(this)}catch(_:Exception){toast("No se pudo iniciar. Revisá ubicación y permisos.");homeOrTrip()}}
 private fun service(action:String?=null){ContextCompat.startForegroundService(this,Intent(this,GpsService::class.java).apply{this.action=action})}
 private fun trip(){val t=store.current()?:return home();page("trip");status=label(root,"VIAJE EN CURSO",12f,blue,true);label(root,t.optString("vehicle"),28f,navy,true);label(root,"$name · ${if(t.optString("load_type")=="empty")"Sin carga / retorno" else "Con carga"}",15f,muted)
  val c=card();label(c,"DISTANCIA RECORRIDA",12f,blue,true);distance=label(c,"0,0 km",52f,navy,true);label(c,"Estimación GPS de este viaje",13f,muted);gps=label(c,"Esperando ubicación precisa…",14f,muted)
  pause=button(root,"Pausar viaje",false){try{service(if(store.current()?.optBoolean("paused")==true)GpsService.RESUME else GpsService.PAUSE)}catch(_:Exception){toast("Revisá el permiso de ubicación")}}
  val fc=card();label(fc,"Combustible",20f,navy,true);count=label(fc,"Sin boletas adjuntas",14f,muted);button(fc,"+ Adjuntar boleta de combustible"){photo=null;fuel()};label(fc,"Sacá una foto o elegila de la galería. Los datos se completan en el panel.",13f,muted)
  button(root,"Finalizar viaje",false,Color.rgb(182,47,52)){AlertDialog.Builder(this).setTitle("¿Finalizar este viaje?").setMessage("Se guardarán el recorrido y las boletas. Para volver a salir tendrás que iniciar otro viaje.").setNegativeButton("Seguir viaje",null).setPositiveButton("Finalizar"){_,_->try{service(GpsService.FINISH)}catch(_:Exception){toast("No se pudo finalizar. Reintentá.")}}.show()};sync=label(root,store.message(),13f,muted);button(root,"Renovar acceso",false){login()};refresh();try{service()}catch(_:Exception){gps?.text="Revisá el permiso de ubicación para continuar"}
 }
 private fun refresh(){if(!::store.isInitialized)return;sync?.text=store.message();if(screen!="trip")return;val t=store.current()?:return;if(!t.optBoolean("active")){home();toast("Viaje finalizado. Los datos pendientes se enviarán con conexión.");return}
  val paused=t.optBoolean("paused");status?.text=if(paused)"VIAJE PAUSADO" else "VIAJE EN CURSO";status?.setTextColor(if(paused)Color.rgb(159,101,15) else Color.rgb(12,119,90));distance?.text=String.format(Locale("es","UY"),"%.1f km",t.optDouble("distance_meters",0.0)/1000);pause?.text=if(paused)"Reanudar viaje" else "Pausar viaje"
  val recent=try{System.currentTimeMillis()-java.time.Instant.parse(t.optJSONObject("last_point")?.optString("recorded_at")).toEpochMilli()<30000}catch(_:Exception){false};gps?.text=if(paused)"El GPS y los kilómetros están pausados" else if(recent)String.format(Locale("es","UY"),"GPS actualizado · %.0f km/h",t.optDouble("speed_kmh",0.0)) else "Esperando señal GPS precisa…";val n=store.receiptCount(t.getString("id"));count?.text=if(n==0)"Sin boletas adjuntas" else "$n boleta(s) vinculada(s) a este viaje"
 }
 private fun fuel(){val t=store.current()?:return home();page("fuel");button(root,"← Volver al viaje",false){trip()};label(root,"Boleta de combustible",28f,navy,true);label(root,"${t.optString("vehicle")} · Viaje actual",14f,muted);val c=card();label(c,"Una foto y listo.",21f,navy,true);label(c,"Queda vinculada a este viaje. No necesitás escribir litros ni importe.",15f,muted)
  photo?.takeIf{it.exists()}?.let{f->c.addView(ImageView(this).apply{setImageURI(Uri.fromFile(f));adjustViewBounds=true;contentDescription="Vista previa de la boleta"},LinearLayout.LayoutParams(-1,dp(260)))}
  button(c,"Sacar foto",false){try{camera=File(File(filesDir,"camera").apply{mkdirs()},"${UUID.randomUUID()}.jpg");take.launch(FileProvider.getUriForFile(this,"$packageName.files",camera!!))}catch(_:Exception){toast("No hay cámara disponible. Elegí una foto de la galería.")}};button(c,"Elegir de la galería",false){pick.launch("image/*")}
  button(root,"Guardar boleta en este viaje"){val f=photo;if(f==null||!f.exists()){toast("Sacá o elegí una foto primero");return@button};try{store.saveReceipt(f,t);photo=null;Sync.schedule(this);trip();toast("Boleta guardada. Se enviará al panel cuando haya conexión.")}catch(_:Exception){toast("No se pudo guardar. Revisá el espacio del teléfono y reintentá.")}};label(root,"Las fotos pendientes se conservan en el teléfono hasta poder enviarlas.",13f,muted)
 }
 private fun preparePhoto(uri:Uri){toast("Preparando foto…");thread{try{
  val original=File(cacheDir,"receipt-original-${UUID.randomUUID()}");contentResolver.openInputStream(uri)?.use{input->original.outputStream().use{out->val buf=ByteArray(8192);var total=0L;while(true){val n=input.read(buf);if(n<0)break;total+=n;check(total<=40*1024*1024);out.write(buf,0,n)}}}?:error("Sin imagen")
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(original.path,bounds);var sample=1;while(max(bounds.outWidth,bounds.outHeight)/sample>2400)sample*=2;val bitmap=BitmapFactory.decodeFile(original.path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Formato no compatible");val exif=ExifInterface(original);val matrix=Matrix();matrix.postRotate(exif.rotationDegrees.toFloat());if(exif.isFlipped)matrix.postScale(-1f,1f);val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);val out=File(cacheDir,"receipt-${UUID.randomUUID()}.jpg");out.outputStream().use{rotated.compress(Bitmap.CompressFormat.JPEG,88,it)};if(rotated!==bitmap)rotated.recycle();bitmap.recycle();original.delete();check(out.length()<=10*1024*1024);runOnUiThread{photo=out;fuel()}
 }catch(_:Exception){runOnUiThread{toast("No se pudo preparar la foto. Probá con otra imagen.")}}}}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
