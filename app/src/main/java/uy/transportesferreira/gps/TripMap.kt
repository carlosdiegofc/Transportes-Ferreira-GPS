package uy.transportesferreira.gps
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import org.json.JSONArray
object TripMap {
 @Suppress("SetJavaScriptEnabled") fun view(c:Context,points:JSONArray):WebView{
  val safe=JSONArray();for(i in 0 until points.length()){val p=points.getJSONObject(i);val lat=p.optDouble("latitude");val lng=p.optDouble("longitude");if(lat.isFinite()&&lng.isFinite()&&lat in -90.0..90.0&&lng in -180.0..180.0)safe.put(JSONArray().put(lat).put(lng).put(p.optInt("segment")).put(p.optString("recorded_at")))}
  val js=c.assets.open("map/leaflet.js").bufferedReader().use{it.readText()};val css=c.assets.open("map/leaflet.css").bufferedReader().use{it.readText()}
  val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>$css html,body,#map{height:100%;margin:0}</style></head><body><div id="map"></div><script>$js</script><script>
  const points=${safe.toString().replace("<","\\u003c")};const map=L.map('map').setView([-32.2,-56],6);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,referrerPolicy:'strict-origin-when-cross-origin',attribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
  let segment=[];function draw(){if(segment.length>1)L.polyline(segment,{color:'#1c64f2',weight:4}).addTo(map)}
  points.forEach((p,i)=>{if(i&&(p[2]!==points[i-1][2]||Date.parse(p[3])-Date.parse(points[i-1][3])>90000)){draw();segment=[]}segment.push([p[0],p[1]])});draw();
  if(points.length){const first=points[0],last=points[points.length-1];L.circleMarker([first[0],first[1]],{color:'#0c775a'}).addTo(map).bindPopup('Inicio GPS');L.circleMarker([last[0],last[1]],{color:'#b62f34'}).addTo(map).bindPopup('Última ubicación');map.fitBounds(points.map(p=>[p[0],p[1]]),{padding:[25,25],maxZoom:15})}
  </script></body></html>"""
  return WebView(c).apply{settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true};loadDataWithBaseURL("https://transportes-ferreira-gestion.carlosdiegofc2001.chatgpt.site/",html,"text/html","UTF-8",null)}
 }
}
