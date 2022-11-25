package com.clsw.drone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clsw.drone.databinding.ActivityMainBinding
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.json.responseJson
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import com.google.maps.android.data.geojson.GeoJsonLayer
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener,
    OnMapReadyCallback {

    var activityContext: Context? = null

    private var wearableDeviceConnected: Boolean = false

    private val PAIRING: String = "PAIRING"
    private val EXIT: String = "EXIT"
    private val ASK_LOC: String = "ASK_LOC"
    private val FLY_INFO: String = "FLY_INFO"

    private val TAG: String = "Mobile Drone"
    private var nodeId: String? = null

    private lateinit var binding: ActivityMainBinding

    private lateinit var mMap: GoogleMap

    private var allowFly = false

    private var prevLat = 0.0
    private var prevLong = 0.0

    private lateinit var updateLocJob: Job

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        activityContext = this
        wearableDeviceConnected = false

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.checkConnectionBtn.setOnClickListener {
            if (!wearableDeviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                // Couroutine
                Log.d(TAG, "Initialisation")
                initialiseDevicePairing(tempAct)
            }
        }



//        binding.getLocationButton.setOnClickListener {
//            if (wearableDeviceConnected) {
//                Log.d(TAG, "sent")
////                sendMessages(nodeId!!, ASK_LOC, null, "Get location message event sent", "null")
//            }
//        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            Log.d(TAG, "Wearable DESTROY : $wearableDeviceConnected")

            if (wearableDeviceConnected) {
                wearableDeviceConnected = false
                sendMessages(nodeId!!, EXIT, null, "Mobile app destroyed event sent to wearable", "null")
            }
            try {
                Wearable.getDataClient(activityContext!!).removeListener(this)
                Wearable.getMessageClient(activityContext!!).removeListener(this)
                Wearable.getCapabilityClient(activityContext!!).removeListener(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun onResume() {
        super.onResume()

        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    @SuppressLint("SetTextI18n")
    private fun initialiseDevicePairing(tempAct: Activity) {
        // Coroutine
        launch(Dispatchers.Default) {
            try {
                exploreNodes(tempAct.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Fail on get nodes : $e")
            }
        }
    }

    private fun updateLocLoop() {
        updateLocJob = launch(Dispatchers.Main) {
            try {
                var bool: Boolean = true
                while (true) {
                    sendMessages(nodeId!!, ASK_LOC, null, "Get location message event sent", "null")

                    withContext(Dispatchers.IO) {
                        Thread.sleep(10000)
                    };
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun exploreNodes(context: Context) {
        val nodeTasks = Wearable.getNodeClient(context).connectedNodes

        try {
            val nodes = Tasks.await(nodeTasks)
            Log.d(TAG, "Get nodes successfull")

            for (node in nodes) {
                this.nodeId = node.id
                sendMessages(node.id, PAIRING, null, "Node paring message sent", "null")
            }

        } catch (exception: Exception) {
            Log.e(TAG, "Can't reach Wearable : $exception")
        }

    }

    private fun sendMessages(node: String, path: String, data: ByteArray?, msgSuccess: String?, msgError: String?) {
        try {
            val task = Wearable.getMessageClient(activityContext!!).sendMessage(node, path, data)
            task.addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d(TAG, "Path: $path MSG: $msgSuccess")
                } else {
                    Log.e(TAG, "$msgError")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sending message error : $e")
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {
    }

    @SuppressLint("SetTextI18n")
    override fun onMessageReceived(p0: MessageEvent) {
        val messageEventPath: String = p0.path
        Log.d(TAG, "Message event receive : $messageEventPath")

        when (messageEventPath) {
            PAIRING -> {
                Toast.makeText(
                    activityContext,
                    "Wearable device paired and app is open. Tap the \"Get the current Location\".",
                    Toast.LENGTH_LONG
                ).show()
                binding.getLocationButton.visibility = View.INVISIBLE
                binding.checkConnectionBtn.visibility = View.INVISIBLE
                wearableDeviceConnected = true
                updateLocLoop()

            }
            EXIT -> {
                Toast.makeText(
                    activityContext,
                    "Wearable device app is stopped. Re-open it and connect it again.",
                    Toast.LENGTH_LONG
                ).show()
                binding.getLocationButton.visibility = View.INVISIBLE
                binding.checkConnectionBtn.visibility = View.VISIBLE
                wearableDeviceConnected = false

                updateLocJob.cancel()
            }
            ASK_LOC -> {
                val json = JSONObject(String(p0.data))
                Log.d(TAG, "revceive data rom wear : $json")


                val long = json.get("longitude") as Double
                val lat = json.get("latitude") as Double

                if (!isLocationDifferent(lat, long)) return
                moveLocation(lat, long)
                saveToDB(lat, long)

                val jsonResponse = JSONObject()

                if (allowFly) {
                    jsonResponse.put("allow", true)
                    jsonResponse.put("alt", 1400)
                } else {
                    jsonResponse.put("allow", false)
                    jsonResponse.put("alt", 0)
                }
                allowFly = !allowFly
                sendMessages(nodeId!!, FLY_INFO, jsonResponse.toString().toByteArray(), "Allow fly sent to the wearable", "null")
            }
            else -> {
                Log.d(TAG, "Unknown path : $messageEventPath")
            }
        }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
    }

    private fun deg2rad(deg: Double): Double {
        return deg * (Math.PI/180)
    }

    private fun isLocationDifferent(lat: Double, long: Double): Boolean {

        val R = 6371
        var dLat = deg2rad(this.prevLat - lat)
        var dLon = deg2rad(this.prevLong - long)

        var a =
            sin(dLat/2) * sin(dLat/2) +
                    cos(deg2rad(lat)) * cos(deg2rad(prevLat)) *
                    sin(dLon/2) * sin(dLon/2)

        var c = 2 * atan2(sqrt(a), sqrt(1-a));
        var d = R * c; // Distance in km

        return d > 1;
    }

    private fun moveLocation(lat: Double, long: Double) {

        prevLat = lat
        prevLong = long

        val newLoc = LatLng(lat, long)

        Log.d(TAG, "Move location")

        mMap.clear()
        mMap.addMarker(MarkerOptions().position(newLoc).title("Current Location"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(newLoc))

        Fuel.get("https://api.cquest.org/drone?lat=$lat&lon=$long&rayon=5000&limite=50")
            .responseJson { request, response, result ->
                println(request)
                println(response)
                println(result)

                var droneString: String = result.get().content
                droneString = droneString.replace("Featurecollection","FeatureCollection")

                Log.d("DroneString", droneString)

                val droneGeoJson: JSONObject? = JSONObject(droneString)

                val layer = GeoJsonLayer(mMap,droneGeoJson)
                layer.addLayerToMap()

                for (feature in layer.features) {
                    feature.polygonStyle.fillColor = Color.rgb(127,82,255)
                }
            }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Antibes and move the camera
        val antibes = LatLng(43.580418, 7.125102)
        mMap.addMarker(MarkerOptions().position(antibes).title("Marker in Antibes"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(antibes))

        Fuel.get("https://api.cquest.org/drone?lat=43.580418&lon=7.125102&rayon=5000&limite=50")
            .responseJson { request, response, result ->
                println(request)
                println(response)
                println(result)

                var droneString: String = result.get().content
                droneString = droneString.replace("Featurecollection","FeatureCollection")

                Log.d("DroneString", droneString)

                val droneGeoJson: JSONObject? = JSONObject(droneString)

                val layer = GeoJsonLayer(mMap,droneGeoJson)
                layer.addLayerToMap()

                for (feature in layer.features) {
                    feature.polygonStyle.fillColor = Color.rgb(127,82,255)
                }
            }
    }

    private fun saveToDB(lat: Double, long: Double) {

        val map = mapOf<String, String>(
            "api-key" to "637724584dbec05fc7d3101f",
            "Content-Type" to "application/json",
            "Access-Control-Request-Headers" to "*" )

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val currentDate = sdf.format(Date())


        val b = JSONObject(mapOf(
            "lat" to lat,
            "long" to long,
            "date" to currentDate
        ))

        val c = JSONObject();
        c.accumulate("dataSource", "DroneApp");
        c.accumulate("database", "geo_local");
        c.accumulate("collection", "all");
        c.accumulate("document", b);

        var url = "https://data.mongodb-api.com/app/data-pwjwq/endpoint/data/v1/action/insertOne"


        Log.d(TAG, "Sending request FUEL DB")
        Fuel.post(url)
            .header(map)
            .body(c.toString())
            .response { request, response, result ->
                println(request)
                println(response)
                val (bytes, error) = result
                if (bytes != null) {
                    println("[response bytes] ${String(bytes)}")
                }
            }
    }

}