package uy.transportesferreira.gps

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TripStore(private val c:Context) {
    private val prefs=c.getSharedPreferences("trip-v7",Context.MODE_PRIVATE)
    private fun dir(name:String)=File(c.filesDir,name).apply{mkdirs()}
    private fun write(file:File,j:JSONObject){val a=AtomicFile(file);val out=a.startWrite();try{out.write(j.toString().toByteArray());a.finishWrite(out)}catch(e:Exception){a.failWrite(out);throw e}}
    private fun read(file:File):JSONObject?=try{JSONObject(AtomicFile(file).openRead().bufferedReader().use{it.readText()})}catch(_:Exception){null}
    fun current():JSONObject?=synchronized(lock){prefs.getString("current",null)?.let{read(File(dir("trips"),"$it.json"))}}
    fun save(trip:JSONObject)=synchronized(lock){write(File(dir("trips"),"${trip.getString("id")}.json"),trip);Unit}
    fun start(user:JSONObject,name:String,vehicle:String,empty:Boolean):JSONObject=synchronized(lock){
        check(current()?.optBoolean("active")!=true){"Ya hay un viaje activo"}
        val t=JSONObject().put("id",UUID.randomUUID().toString()).put("driver_id",user.getString("id")).put("driver_name",name).put("vehicle",vehicle)
            .put("started_at",Instant.now().toString()).put("ended_at",JSONObject.NULL).put("active",true).put("paused",false)
            .put("load_type",if(empty)"empty" else "loaded").put("distance_meters",0.0).put("segment",0).put("revision",1).put("synced_revision",0)
        save(t);check(prefs.edit().putString("current",t.getString("id")).commit());t
    }
    fun update(change:(JSONObject)->Unit):JSONObject?=synchronized(lock){val t=current()?:return@synchronized null;change(t);t.put("revision",t.optInt("revision")+1);save(t);t}
    fun trips()=synchronized(lock){dir("trips").listFiles()?.filter{it.extension=="json"}?.mapNotNull{read(it)}?.sortedBy{it.optString("started_at")} ?: emptyList()}
    fun addPoint(point:JSONObject)=synchronized(lock){write(File(dir("points"),"${point.getString("event_id")}.json"),point);Unit}
    fun points()=synchronized(lock){dir("points").listFiles()?.filter{it.extension=="json"}?.mapNotNull{f->read(f)?.let{Pair(f,it)}}?.sortedBy{it.second.optString("recorded_at")} ?: emptyList()}
    fun receipts()=synchronized(lock){dir("receipts").listFiles()?.filter{it.extension=="json"}?.mapNotNull{f->read(f)?.let{Pair(f,it)}} ?: emptyList()}
    fun receiptCount(id:String)=receipts().count{it.second.optString("trip_id")==id}
    fun pendingCount()=trips().count{it.optInt("revision")>it.optInt("synced_revision")}+points().size+receipts().count{!it.second.optBoolean("synced")}
    fun saveReceipt(photo:File,trip:JSONObject):JSONObject=synchronized(lock){
        val id=UUID.randomUUID().toString();val dest=File(dir("receipts"),"$id.jpg")
        photo.copyTo(dest,overwrite=false)
        val r=JSONObject().put("id",id).put("trip_id",trip.getString("id")).put("driver_id",trip.getString("driver_id")).put("driver_name",trip.getString("driver_name"))
            .put("vehicle",trip.getString("vehicle")).put("recorded_at",Instant.now().toString()).put("receipt_date",LocalDate.now().toString())
            .put("distance_meters",trip.optDouble("distance_meters",0.0)).put("object_path","${trip.getString("driver_id")}/${trip.getString("id")}/$id.jpg")
            .put("local_path",dest.absolutePath).put("synced",false)
        write(File(dir("receipts"),"$id.json"),r);r
    }
    fun markTripSynced(id:String,revision:Int)=synchronized(lock){val f=File(dir("trips"),"$id.json");val t=read(f)?:return@synchronized;t.put("synced_revision",revision);write(f,t)}
    fun markReceiptSynced(file:File,r:JSONObject)=synchronized(lock){r.put("synced",true);write(file,r)}
    fun message(value:String){prefs.edit().putString("sync_message",value).apply()}
    fun message()=prefs.getString("sync_message","Esperando sincronización") ?: ""
    companion object{private val lock=Any()}
}
