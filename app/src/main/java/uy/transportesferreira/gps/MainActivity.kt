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
import org.json.JSONObject
import org.json.JSONArray
import android.text.TextWatcher
import android.text.Editable
import android.location.Geocoder
import android.app.DatePickerDialog
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
 private val mobile by lazy{MobileData(this)}
 private var tripForm=JSONObject();private var fuelForm=JSONObject();private var docForm=JSONObject();private var draftDocs=JSONArray()
 private var fuelTrip:JSONObject?=null;private var historyTrip:JSONObject?=null;private var docTrip:JSONObject?=null
 private var photoMode="fuel";private var docReturn="prepare";private var historyLimit=50
 private var activeMap:android.webkit.WebView?=null
 private val trucks=arrayOf("Seleccioná un camión","Ford Cargo 1722","Mercedes-Benz 1618","Leyland","Mercedes-Benz 1630")
 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){try{service()}catch(_:Exception){};homeOrTrip()}
  else AlertDialog.Builder(this).setTitle("Activá la ubicación precisa").setMessage("El cuentakilómetros necesita ubicación precisa. Habilitala en los permisos de la app.").setPositiveButton("Abrir ajustes"){_,_->startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}.setNegativeButton("Ahora no",null).show()
 }
 private val take=registerForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)camera?.let{preparePhoto(Uri.fromFile(it))}else camera?.delete()}
 private val pick=registerForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)preparePhoto(uri)}
 override fun onCreate(b:Bundle?){super.onCreate(b);store=TripStore(this);api=Api(this);
  tripForm=JSONObject(b?.getString("tripForm")?:"{}");fuelForm=JSONObject(b?.getString("fuelForm")?:"{}");docForm=JSONObject(b?.getString("docForm")?:"{}");draftDocs=JSONArray(b?.getString("draftDocs")?:"[]")
  fuelTrip=b?.getString("fuelTrip")?.let{JSONObject(it)};docTrip=b?.getString("docTrip")?.let{JSONObject(it)};historyTrip=b?.getString("historyTrip")?.let{JSONObject(it)};photoMode=b?.getString("photoMode")?:"fuel";docReturn=b?.getString("docReturn")?:"prepare"
 camera=b?.getString("camera")?.let{File(it)};photo=b?.getString("photo")?.let{File(it)}?.takeIf{it.exists()};truck=b?.getString("truck")?:"";empty=b?.getBoolean("empty")?:false
  window.statusBarColor=pale;window.navigationBarColor=pale
  if(api.session()==null)login() else {name=driverName();when(b?.getString("screen")){"fuel"->fuel();"document"->document();"prepare"->prepare();else->homeOrTrip()};Sync.schedule(this);thread{try{mobile.catalog(true)}catch(_:Exception){}}}

 }
 override fun onSaveInstanceState(b:Bundle){super.onSaveInstanceState(b);b.putString("camera",camera?.path);b.putString("photo",photo?.path);b.putString("screen",screen);b.putString("truck",truck);b.putBoolean("empty",empty);b.putString("tripForm",tripForm.toString());b.putString("fuelForm",fuelForm.toString());b.putString("docForm",docForm.toString());b.putString("draftDocs",draftDocs.toString());b.putString("fuelTrip",fuelTrip?.toString());b.putString("docTrip",docTrip?.toString());b.putString("historyTrip",historyTrip?.toString());b.putString("photoMode",photoMode);b.putString("docReturn",docReturn)}
 override fun onStart(){super.onStart();handler.post(tick)}
 override fun onStop(){handler.removeCallbacks(tick);super.onStop()}
 @Deprecated("Deprecated in Java") override fun onBackPressed(){when(screen){"fuel"->homeOrTrip();"document"->backFromDocument();"prepare","triphistory","fuelhistory"->home();"tripdetail"->history("trips");"trip"->moveTaskToBack(true);else->super.onBackPressed()}}
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun bg(color:Int,r:Int=20)=GradientDrawable().apply{setColor(color);cornerRadius=dp(r).toFloat()}
 private fun page(key:String){activeMap?.destroy();activeMap=null;screen=key;distance=null;status=null;sync=null;gps=null;pause=null;count=null
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
   thread{try{api.login(e,p);name=driverName();runOnUiThread{pass.text.clear();homeOrTrip();Sync.schedule(this);thread{try{mobile.catalog(true)}catch(_:Exception){}}}}catch(_:Exception){runOnUiThread{submit.isEnabled=true;msg.text="No se pudo ingresar. Revisá tus datos y la conexión."}}}}
  label(root,"Tu sesión queda guardada de forma segura en este teléfono.",13f,muted)
 }
 private fun driverName():String{val u=api.session()?.optJSONObject("user")?:return "Chofer";val m=u.optJSONObject("user_metadata");val known=mapOf("immer@trferreira.com" to "Immer Sampayo","luis@trferreira.com" to "Luis Ferreira","hugo@trferreira.com" to "Hugo Silva");return m?.optString("full_name")?.takeIf{it.isNotBlank()}?:m?.optString("name")?.takeIf{it.isNotBlank()}?:known[u.optString("email")]?:u.optString("email").substringBefore("@").replaceFirstChar{it.uppercase()}}
 private fun homeOrTrip(){if(store.current()?.optBoolean("active")==true)trip() else home()}
 private fun home(){
  page("home");label(root,"Hola,\n$name",30f,navy,true);label(root,"Tu jornada, en un solo lugar.",16f,muted)
  val c=card();label(c,"CAMIÓN DE HOY · OPCIONAL",12f,blue,true)
  val catalog=mobile.catalog().optJSONArray("equipos");val choices=mutableListOf("Sin seleccionar")
  if(catalog!=null)for(i in 0 until catalog.length()){val e=catalog.getJSONObject(i);choices.add(e.optString("tipo")+e.optString("matriculaCamion").let{if(it.isBlank())"" else " · $it"})}
  if(choices.size==1)choices.addAll(trucks.drop(1))
  val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,choices);minimumHeight=dp(56)}
  c.addView(spinner);spinner.setSelection(choices.indexOf(truck).coerceAtLeast(0));spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){truck=if(pos>0)choices[pos] else ""}}
  val active=store.current()?.optBoolean("active")==true
  button(root,if(active)"Volver al viaje en curso  →" else "Nuevo viaje  →"){if(active)trip() else {tripForm=JSONObject();draftDocs=JSONArray();empty=false;prepare()}}
  button(root,"Historial de mis viajes",false){history("trips",true)}
  val f=card();label(f,"Combustible",21f,navy,true)
  button(f,"Registrar combustible fuera de viaje",false){openFuel(null)}
  button(f,"Historial de mi combustible",false){history("fuel",true)}
  sync=label(root,store.message(),13f,muted)
  button(root,"Actualizar y enviar pendientes",false){Sync.schedule(this);thread{try{mobile.catalog(true);runOnUiThread{if(screen=="home")home()}}catch(_:Exception){runOnUiThread{toast("Sin conexión. Se conservan los datos guardados.")}}}}
  button(root,"Renovar acceso",false){login()}
  button(root,"Cerrar sesión",false){if(active||store.pendingCount()>0){toast("Finalizá el viaje y enviá los datos pendientes antes de cerrar sesión.");return@button};api.clear();login()}
 }
 private fun prepare(){
  page("prepare");button(root,"← Menú principal",false){home()};label(root,"Nuevo viaje",30f,navy,true);label(root,"Podés iniciar ahora y completar los datos después.",16f,muted)
  val c=card();val group=RadioGroup(this)
  val a=RadioButton(this).apply{id=View.generateViewId();text="Viaje común";minHeight=dp(52)};val b=RadioButton(this).apply{id=View.generateViewId();text="Retorno vacío";minHeight=dp(52)}
  group.addView(a);group.addView(b);group.check(if(empty)b.id else a.id);group.setOnCheckedChangeListener{_,id->empty=id==b.id};c.addView(group)
  bound(c,"Destino previsto · opcional",tripForm,"destino")
  button(c,"Buscar destino",false){searchDestination()}
  select(c,"Cliente · opcional",tripForm,"cliente",catalogNames("clientes"))
  select(c,"Tipo de carga · opcional",tripForm,"tipoCarga",catalogNames("tiposCarga"))
  bound(c,"Kilogramos · opcional",tripForm,"kg",true)
  label(c,"Origen y llegada se registran con las ubicaciones GPS disponibles.",13f,muted)
  button(root,"Agregar remito de salida o llegada (${draftDocs.length()})",false){docTrip=null;docReturn="prepare";docForm=JSONObject().put("kind","departure");photo=null;document()}
  button(root,"Iniciar viaje ahora  →"){begin()}
 }
 private fun requestStart(){
  val req=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=33)req.add(Manifest.permission.POST_NOTIFICATIONS)
  if(req.any{ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED})permissions.launch(req.toTypedArray()) else service()
 }
 private fun begin(){
  val u=api.session()?.optJSONObject("user")?:return login()
  try{val t=store.start(u,name,InputRules.vehicle(truck),empty,JSONObject(tripForm.toString()).put("kg",InputRules.optionalNumber(tripForm.optString("kg"))?:"").apply{if(empty)put("tipoCarga","")})
   for(i in 0 until draftDocs.length()){val d=draftDocs.getJSONObject(i);store.saveDocument(t,d,d.optString("draft_photo").takeIf{it.isNotBlank()}?.let{File(it)})};draftDocs=JSONArray();trip();Sync.schedule(this);requestStart()
  }catch(_:Exception){toast("No se pudo guardar el viaje en el teléfono. Revisá el espacio disponible.");homeOrTrip()}
 }
 private fun service(action:String?=null){
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){ContextCompat.startForegroundService(this,Intent(this,GpsService::class.java).apply{this.action=action});return}
  // A trip can exist without GPS permission. Business fields never block starting or ending it.
  when(action){GpsService.PAUSE->store.update{it.put("paused",true)};GpsService.RESUME->store.update{it.put("paused",false).put("segment",it.optInt("segment")+1)};GpsService.FINISH->store.update{it.put("active",false).put("paused",false).put("ended_at",java.time.Instant.now().toString())}}
  Sync.schedule(this)
 }
 private fun trip(){val t=store.current()?:return home();page("trip");button(root,"← Menú principal",false){home()};status=label(root,"VIAJE EN CURSO",12f,blue,true);label(root,t.optString("vehicle"),28f,navy,true);label(root,"$name · ${if(t.optString("load_type")=="empty")"Sin carga / retorno" else "Con carga"}",15f,muted)
  val c=card();label(c,"DISTANCIA RECORRIDA",12f,blue,true);distance=label(c,"0,0 km",52f,navy,true);label(c,"Estimación GPS de este viaje",13f,muted);gps=label(c,"Esperando ubicación precisa…",14f,muted)
  pause=button(root,"Pausar viaje",false){try{service(if(store.current()?.optBoolean("paused")==true)GpsService.RESUME else GpsService.PAUSE)}catch(_:Exception){toast("Revisá el permiso de ubicación")}}
  val fc=card();label(fc,"Combustible",20f,navy,true);count=label(fc,"Sin boletas adjuntas",14f,muted);button(fc,"+ Registrar combustible"){openFuel(store.current())};label(fc,"Sacá una foto o elegila de la galería. También podés ingresar los datos manualmente.",13f,muted)
  button(root,"Agregar remito de salida o llegada",false){docTrip=store.current();docReturn="trip";docForm=JSONObject().put("kind","arrival");photo=null;document()}
  button(root,"Activar / revisar GPS",false){requestStart()}
  button(root,"Finalizar viaje",false,Color.rgb(182,47,52)){AlertDialog.Builder(this).setTitle("¿Finalizar este viaje?").setMessage("Se guardarán el recorrido y las boletas. Para volver a salir tendrás que iniciar otro viaje.").setNegativeButton("Seguir viaje",null).setPositiveButton("Finalizar"){_,_->try{service(GpsService.FINISH)}catch(_:Exception){toast("No se pudo finalizar. Reintentá.")}}.show()};sync=label(root,store.message(),13f,muted);button(root,"Renovar acceso",false){login()};refresh();try{service()}catch(_:Exception){gps?.text="Revisá el permiso de ubicación para continuar"}
 }
 private fun refresh(){if(!::store.isInitialized)return;sync?.text=store.message();if(screen!="trip")return;val t=store.current()?:return;if(!t.optBoolean("active")){home();toast("Viaje finalizado. Los datos pendientes se enviarán con conexión.");return}
  val paused=t.optBoolean("paused");status?.text=if(paused)"VIAJE PAUSADO" else "VIAJE EN CURSO";status?.setTextColor(if(paused)Color.rgb(159,101,15) else Color.rgb(12,119,90));distance?.text=String.format(Locale("es","UY"),"%.1f km",t.optDouble("distance_meters",0.0)/1000);pause?.text=if(paused)"Reanudar viaje" else "Pausar viaje"
  val recent=try{System.currentTimeMillis()-java.time.Instant.parse(t.optJSONObject("last_point")?.optString("recorded_at")).toEpochMilli()<30000}catch(_:Exception){false};gps?.text=if(paused)"El GPS y los kilómetros están pausados" else if(recent)String.format(Locale("es","UY"),"GPS actualizado · %.0f km/h",t.optDouble("speed_kmh",0.0)) else "Esperando señal GPS precisa…";val n=store.receiptCount(t.getString("id"));count?.text=if(n==0)"Sin boletas adjuntas" else "$n boleta(s) vinculada(s) a este viaje"
 }
 private fun openFuel(t:JSONObject?){fuelTrip=t;fuelForm=JSONObject().put("receipt_date",java.time.LocalDate.now().toString());photo=null;photoMode="fuel";fuel()}
 private fun fuel(){
  page("fuel");photoMode="fuel";button(root,"← Volver",false){homeOrTrip()};label(root,"Registrar combustible",28f,navy,true)
  label(root,if(fuelTrip==null)"Fuera de viaje · ${truck.ifBlank{"Camión sin seleccionar"}}" else "Vinculado al viaje · ${fuelTrip!!.optString("vehicle")}",14f,muted)
  val c=card();button(c,"Fecha: ${fuelForm.optString("receipt_date",java.time.LocalDate.now().toString())}",false){
   val date=java.time.LocalDate.parse(fuelForm.optString("receipt_date",java.time.LocalDate.now().toString()));DatePickerDialog(this,{_,y,m,d->fuelForm.put("receipt_date",java.time.LocalDate.of(y,m+1,d).toString());fuel()},date.year,date.monthValue-1,date.dayOfMonth).show()
  }
  val stations=mobile.catalog().optJSONArray("estaciones")?:JSONArray();val names=(0 until stations.length()).map{stations.getJSONObject(it).optString("nombre")}
  select(c,"Estación de servicio",fuelForm,"station_name",names)
  if(names.isEmpty())bound(c,"Nombre de la estación",fuelForm,"station_name")
  bound(c,"Litros · opcional",fuelForm,"liters",true);bound(c,"Monto total en UYU · opcional",fuelForm,"total",true)
  photoControls(c)
  button(root,"Guardar combustible"){
   if(fuelForm.optString("station_name").isBlank()){toast("Indicá la estación de servicio para guardar el combustible");return@button}
   try{val fields=JSONObject(fuelForm.toString());for(key in listOf("liters","total")){val raw=fields.optString(key);val n=raw.replace(',','.').toDoubleOrNull();if(raw.isNotBlank()&&(n==null||!n.isFinite()||n<0)){toast("Revisá litros e importe");return@button};fields.put(key,n?:JSONObject.NULL)}
    for(i in 0 until stations.length()){val station=stations.getJSONObject(i);if(station.optString("nombre")==fields.optString("station_name"))fields.put("station_id",station.optString("id"))}
    store.saveReceipt(photo,fuelTrip,api.session()!!.getJSONObject("user"),name,truck,fields);photo=null;Sync.schedule(this);toast("Combustible guardado");homeOrTrip()
   }catch(_:Exception){toast("No se pudo guardar. Revisá el espacio del teléfono.")}
  };label(root,"Se enviará al panel cuando haya conexión.",13f,muted)
 }
 private fun document(){
  page("document");photoMode="document";button(root,"← Volver",false){backFromDocument()};label(root,"Agregar remito",28f,navy,true)
  val c=card();val group=RadioGroup(this);val a=RadioButton(this).apply{id=View.generateViewId();text="Salida de chacra / planta"};val b=RadioButton(this).apply{id=View.generateViewId();text="Llegada a destino"};group.addView(a);group.addView(b);group.check(if(docForm.optString("kind")=="arrival")b.id else a.id);group.setOnCheckedChangeListener{_,id->docForm.put("kind",if(id==b.id)"arrival" else "departure")};c.addView(group)
  bound(c,"Número de remito · opcional",docForm,"number");select(c,"Tipo de carga · opcional",docForm,"cargo_type",catalogNames("tiposCarga"));bound(c,"Kilogramos del remito · opcional",docForm,"kg",true);photoControls(c)
  button(root,"Agregar remito"){
   try{val d=JSONObject(docForm.toString());val raw=d.optString("kg");val n=raw.replace(',','.').toDoubleOrNull();if(raw.isNotBlank()&&(n==null||!n.isFinite()||n<0)){toast("Revisá los kilogramos");return@button};d.put("kg",n?:JSONObject.NULL)
    if(docTrip==null){photo?.let{d.put("draft_photo",it.path)};draftDocs.put(d)}else{store.saveDocument(docTrip!!,d,photo);Sync.schedule(this)}
    photo=null;toast("Remito agregado. Podés agregar otro.");backFromDocument()
   }catch(_:Exception){toast("No se pudo guardar el remito")}
  }
 }
 private fun backFromDocument(){when(docReturn){"prepare"->prepare();"tripdetail"->historyTrip?.let{detail(it,true)};else->trip()}}
 private fun photoControls(c:LinearLayout){
  photo?.takeIf{it.exists()}?.let{f->c.addView(ImageView(this).apply{setImageURI(Uri.fromFile(f));adjustViewBounds=true;contentDescription="Vista previa del comprobante"},LinearLayout.LayoutParams(-1,dp(230)))}
  button(c,"Sacar foto · opcional",false){try{camera=File(File(filesDir,"camera").apply{mkdirs()},"${UUID.randomUUID()}.jpg");take.launch(FileProvider.getUriForFile(this,"$packageName.files",camera!!))}catch(_:Exception){toast("No hay cámara disponible. Elegí una imagen de la galería.")}}
  button(c,"Elegir foto de la galería",false){pick.launch("image/*")}
 }
 private fun bound(c:LinearLayout,title:String,data:JSONObject,key:String,number:Boolean=false):EditText{
  val v=field(c,title,if(number)InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT)
  v.setText(data.optString(key).takeUnless{it=="null"}?:"");v.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){data.put(key,s.toString())};override fun afterTextChanged(e:Editable?){} });return v
 }
 private fun select(c:LinearLayout,title:String,data:JSONObject,key:String,values:List<String>){
  label(c,title,13f,muted,true);val options=listOf("Sin seleccionar")+values.distinct().filter{it.isNotBlank()};val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options);minimumHeight=dp(54)};c.addView(spinner)
  spinner.setSelection(options.indexOf(data.optString(key)).coerceAtLeast(0));spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){data.put(key,if(pos==0)"" else options[pos])}}
 }
 private fun catalogNames(key:String):List<String>{val a=mobile.catalog().optJSONArray(key)?:JSONArray();return (0 until a.length()).map{a.optString(it)}}
 private fun searchDestination(){
  val q=tripForm.optString("destino");if(q.isBlank()){toast("Escribí un lugar para buscar. También podés iniciar sin destino.");return};toast("Buscando destino…")
  thread{try{@Suppress("DEPRECATION") val found=Geocoder(this,Locale("es","UY")).getFromLocationName(q,5)?:emptyList();runOnUiThread{if(screen!="prepare")return@runOnUiThread;if(found.isEmpty()){toast("No se encontraron resultados. Podés conservar el nombre escrito.");return@runOnUiThread};AlertDialog.Builder(this).setTitle("Elegí un destino").setItems(found.map{it.getAddressLine(0)?:it.featureName?:q}.toTypedArray()){_,i->val a=found[i];tripForm.put("destino",a.getAddressLine(0)?:q).put("destination_lat",a.latitude).put("destination_lng",a.longitude);prepare()}.setNegativeButton("Cancelar",null).show()}}catch(_:Exception){runOnUiThread{toast("Búsqueda no disponible. Podés iniciar con el destino escrito o sin destino.")}}}
 }
 private fun history(kind:String,refresh:Boolean=false){
  val key=if(kind=="trips")"triphistory" else "fuelhistory";page(key);button(root,"← Menú principal",false){home()};label(root,if(kind=="trips")"Mis viajes" else "Mi combustible",28f,navy,true);label(root,"Solo registros de $name, en todos los camiones.",14f,muted)
  val rows=mobile.history(kind,historyLimit);if(rows.length()==0)label(root,"Todavía no hay registros guardados.",16f,muted)
  for(i in 0 until rows.length()){val row=rows.getJSONObject(i);val c=card()
   if(kind=="trips"){label(c,row.optString("vehicle").ifBlank{"Sin camión asignado"},19f,navy,true);label(c,"${date(row.optString("started_at"))} · ${if(row.optBoolean("active"))if(row.optBoolean("paused"))"Pausado" else "En curso" else "Finalizado"}",14f,muted);label(c,"${String.format(Locale("es","UY"),"%.1f",row.optDouble("distance_meters",0.0)/1000)} km GPS · ${if(row.optString("load_type")=="empty")"Retorno vacío" else "Viaje común"}",14f,muted);button(c,"Ver datos, remitos y mapa",false){detail(row,true)}}
   else{val d=row.optJSONObject("panel_data")?:JSONObject();label(c,d.optString("estacionNombre").ifBlank{row.optString("station_name").ifBlank{"Estación por completar"}},19f,navy,true);label(c,"${d.optString("fecha").ifBlank{row.optString("receipt_date")}} · ${d.optString("vehicle").ifBlank{row.optString("vehicle")}}",14f,muted);label(c,"${value(d,"litros",row,"liters")} L · UYU ${value(d,"monto",row,"total")}");label(c,if(row.isNull("trip_id"))"Fuera de viaje" else "Vinculado a un viaje",13f,muted);if(!row.isNull("object_path"))button(c,"Ver boleta",false){showPhoto(row,"trf-fuel-receipts")}}
  }
  button(root,"Actualizar historial",false){history(kind,true)};button(root,"Cargar más",false){historyLimit+=50;history(kind,true)}
  if(refresh)thread{try{mobile.history(kind,historyLimit,true);runOnUiThread{if(screen==key)history(kind)}}catch(_:Exception){runOnUiThread{toast("Sin conexión. Mostrando los registros guardados en este teléfono.")}}}
 }
 private fun value(a:JSONObject,k:String,b:JSONObject,l:String)=a.optString(k).takeUnless{it.isBlank()||it=="null"}?:b.optString(l).takeUnless{it.isBlank()||it=="null"}?:"Pendiente"
 private fun detail(t:JSONObject,refresh:Boolean=false){
  historyTrip=t;page("tripdetail");button(root,"← Mis viajes",false){history("trips")};label(root,t.optString("vehicle"),27f,navy,true)
  val bundle=mobile.detail(t);val d=bundle.optJSONObject("data")?:JSONObject();val docs=bundle.optJSONArray("documents")?:JSONArray();val c=card()
  fun coord(lat:String,lng:String)=if(t.has(lat)&&!t.isNull(lat))"${String.format(Locale.US,"%.5f",t.optDouble(lat))}, ${String.format(Locale.US,"%.5f",t.optDouble(lng))}" else "Sin lectura GPS"
  label(c,"Origen: ${d.optString("origen").ifBlank{coord("origin_lat","origin_lng")}}")
  label(c,"Destino: ${d.optString("destino").ifBlank{"Sin destino previsto"}}")
  label(c,"Última ubicación / llegada: ${coord("arrival_lat","arrival_lng")}",14f,muted)
  label(c,"Cliente: ${d.optString("cliente").ifBlank{"Pendiente"}}\nCarga: ${d.optString("tipoCarga").ifBlank{if(t.optString("load_type")=="empty")"Retorno vacío" else "Pendiente"}}\nKilogramos: ${d.optString("kg").ifBlank{"Pendiente"}}\nDistancia: ${d.optString("km").ifBlank{String.format(Locale.US,"%.1f",t.optDouble("distance_meters",0.0)/1000)}} km")
  if(d.optString("remito").isNotBlank())label(c,"Remitos completados en el panel: ${d.optString("remito")}")
  label(root,"Recorrido registrado",21f,navy,true);val points=mobile.route(t)
  if(points.length()>0){activeMap=TripMap.view(this,points);root.addView(activeMap,LinearLayout.LayoutParams(-1,dp(330)));label(root,"Las líneas unen lecturas GPS; los tramos sin señal o pausados quedan abiertos.",12f,muted)}else label(root,"Sin recorrido disponible. Actualizá para consultar el GPS guardado.",14f,muted)
  label(root,"Remitos (${docs.length()})",21f,navy,true)
  for(i in 0 until docs.length()){val r=docs.getJSONObject(i);val rc=card();label(rc,"${if(r.optString("kind")=="arrival")"Llegada" else "Salida"} · ${r.optString("number").ifBlank{"Sin número"}}",18f,navy,true);label(rc,"${r.optString("cargo_type")} · ${r.optString("kg").takeUnless{it=="null"}?:"—"} kg",14f,muted);if(!r.isNull("object_path"))button(rc,"Ver foto",false){showPhoto(r,"trf-trip-documents")}}
  button(root,"Agregar otro remito",false){docTrip=t;docReturn="tripdetail";docForm=JSONObject().put("kind","arrival");photo=null;document()}
  button(root,"Actualizar desde el panel",false){detail(t,true)}
  if(refresh)thread{try{mobile.detail(t,true);mobile.route(t,true);runOnUiThread{if(screen=="tripdetail"&&historyTrip?.optString("id")==t.optString("id"))detail(t)}}catch(_:Exception){runOnUiThread{toast("No se pudo actualizar. Se mantienen los datos guardados.")}}}
 }
 private fun showPhoto(r:JSONObject,bucket:String){
  toast("Abriendo foto…");thread{try{val local=r.optString("local_path").takeIf{it.isNotBlank()}?.let{File(it)};val bytes=if(local?.exists()==true)local.readBytes() else mobile.photo(r.getString("object_path"),bucket);val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Imagen no disponible");runOnUiThread{val image=ImageView(this).apply{setImageBitmap(bitmap);adjustViewBounds=true;contentDescription="Comprobante adjunto"};AlertDialog.Builder(this).setView(image).setPositiveButton("Cerrar",null).show()}}catch(_:Exception){runOnUiThread{toast("No se pudo abrir la foto. Revisá la conexión.")}}}
 }
 private fun date(s:String)=try{java.time.Instant.parse(s).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}catch(_:Exception){s}
 private fun preparePhoto(uri:Uri){toast("Preparando foto…");thread{try{
  val original=File(cacheDir,"receipt-original-${UUID.randomUUID()}");contentResolver.openInputStream(uri)?.use{input->original.outputStream().use{out->val buf=ByteArray(8192);var total=0L;while(true){val n=input.read(buf);if(n<0)break;total+=n;check(total<=40*1024*1024);out.write(buf,0,n)}}}?:error("Sin imagen")
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(original.path,bounds);var sample=1;while(max(bounds.outWidth,bounds.outHeight)/sample>2400)sample*=2;val bitmap=BitmapFactory.decodeFile(original.path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Formato no compatible");val exif=ExifInterface(original);val matrix=Matrix();matrix.postRotate(exif.rotationDegrees.toFloat());if(exif.isFlipped)matrix.postScale(-1f,1f);val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);val out=File(cacheDir,"receipt-${UUID.randomUUID()}.jpg");out.outputStream().use{rotated.compress(Bitmap.CompressFormat.JPEG,88,it)};if(rotated!==bitmap)rotated.recycle();bitmap.recycle();original.delete();check(out.length()<=10*1024*1024);runOnUiThread{photo=out;if(photoMode=="document")document() else fuel()}
 }catch(_:Exception){runOnUiThread{toast("No se pudo preparar la foto. Probá con otra imagen.")}}}}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
