package com.clsw.drone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.ambient.AmbientModeSupport.AmbientCallback
import com.clsw.drone.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.android.gms.wearable.*
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), AmbientModeSupport.AmbientCallbackProvider,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {


    private var activityContext: Context? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private val REQUEST_CODE = 101;
    private val PAIRING: String = "PAIRING"
    private val EXIT: String = "EXIT"
    private val ASK_LOC: String = "ASK_LOC"
    private val FLY_INFO: String = "FLY_INFO"

    private val TAG: String = "Wear Drone"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentLocation: Location? = null
    private var mobileDeviceConnected: Boolean = false
    private var permission: Boolean = false
    private var node: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        activityContext = this

        // Enables Always-on
        ambientController = AmbientModeSupport.attach(this)

        initFusedClient()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            permission = true;
        } else {
            requestPermission()
        }
    }

    override fun onPause() {
        super.onPause()

        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)

            removeLocationUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!).addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)

            addLocationUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()

        Log.d(TAG, "Stopping wearable Device App")
        if (mobileDeviceConnected && node != null) {
            sendMessages(node!!, EXIT, null, "Exit wearable app event sent", "null")
            removeLocationUpdate()
            mobileDeviceConnected = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mobileDeviceConnected && node != null) {
            sendMessages(node!!, EXIT, null, "Exit wearable app event sent", "null")
            removeLocationUpdate()
        }
    }

    private fun initFusedClient() {
        // Fused Location Client Initialization
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest().apply {
            interval = TimeUnit.SECONDS.toMillis(2)
            fastestInterval = TimeUnit.SECONDS.toMillis(1)
            maxWaitTime = TimeUnit.SECONDS.toMillis(3)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)

                p0.lastLocation?.let {
                    currentLocation = it
                }
            }
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            if (grantResults.size == 2) {
                permission = true
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
            }
        }
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(p0: MessageEvent) {
        val messageEventPath: String = p0.path
        Log.d(TAG, "Message event receive : $messageEventPath")

        when (messageEventPath) {
            PAIRING -> {
                val nodeId: String = p0.sourceNodeId
                this.node = nodeId
                binding.infoTxt.text = getString(R.string.waiting_for)
                sendMessages(nodeId, PAIRING, null, "Pairing msg sent", "Pairing msg not sent")
                addLocationUpdate()
                mobileDeviceConnected = true
            }
            ASK_LOC -> {
                if (permission && isLocationEnabled()) {
                    val json = JSONObject()
                    json.put("latitude", currentLocation?.latitude)
                    json.put("longitude", currentLocation?.longitude)
                    json.put("timestamp", currentLocation?.time)
                    val payload: ByteArray = json.toString().toByteArray()
                    sendMessages(node!!, ASK_LOC, payload, "Location sent to the mobile", "Location NOT sent to the mobile")
                } else {
                    Toast.makeText(
                        activityContext,
                        "Permission not set, you have to allow location on the device",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            FLY_INFO -> {
                val json = JSONObject(String(p0.data))

                try {
                    val allow = json.get("allow") as Boolean
                    val alt = json.get("alt") as Int

                    setFlyInfo(allow, alt)
                } catch (e: JSONException) {
                    Log.e(TAG, "JSON obect on FLY INFO not correct : $e")
                }
            }
            EXIT -> {
                mobileDeviceConnected = false
                binding.infoAlt.visibility = View.INVISIBLE
                binding.infoTxt.text = getString(R.string.not_connected)
                removeLocationUpdate()
            }
            else -> Log.d(TAG, "Unknown path : $messageEventPath")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setFlyInfo(allow: Boolean, alt: Int) {
        if (allow) {
            binding.infoTxt.text = getText(R.string.allow_fly)
            binding.infoTxt.background = getDrawable(R.drawable.rounded_corner_frame_ok)
            binding.infoAlt.text = "$alt m"
            binding.infoAlt.visibility = View.VISIBLE
        } else {
            binding.infoTxt.text = getText(R.string.not_allow_fly)
            binding.infoTxt.background = getDrawable(R.drawable.rounded_corner_frame_nope)
            binding.infoAlt.visibility = View.INVISIBLE
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

    @SuppressLint("MissingPermission")
    private fun addLocationUpdate() {
        if (permission) {
            val task = fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
            if (task.isSuccessful) {
                Log.d(TAG, "Location update added successfuly")
            }
        }
        else {
            requestPermission()
        }
    }

    private fun removeLocationUpdate() {
        val removeTask = fusedLocationClient.removeLocationUpdates(locationCallback)
        removeTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Location Callback removed.")
            } else {
                Log.d(TAG, "Failed to remove Location Callback.")
            }
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
    }

    override fun getAmbientCallback(): AmbientCallback = MyAmbientCallback()

    private inner class MyAmbientCallback : AmbientCallback() {
        override fun onEnterAmbient(ambientDetails: Bundle) {
            super.onEnterAmbient(ambientDetails)
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
        }
    }

}