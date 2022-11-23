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

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.ambient.AmbientModeSupport.AmbientCallback
import com.clsw.drone.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.android.gms.wearable.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), AmbientModeSupport.AmbientCallbackProvider,
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private var activityContext: Context? = null

    private lateinit var binding: ActivityMainBinding

    private var mobileDeviceConnected: Boolean = false

    private val REQUEST_CODE = 101;

    private val PAIRING: String = "PAIRING"
    private val EXIT: String = "EXIT"
    private val ASK_LOC: String = "ASK_LOC"

    private val TAG_INIT: String = "Initialisation"
    private val TAG_MSG: String = "Messages"
    private val TAG: String = "Wear Drone"

    private var node: String? = null

    private lateinit var ambientController: AmbientModeSupport.AmbientController

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentLocation: Location? = null

    private var permission: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        activityContext = this

        // Enables Always-on
        ambientController = AmbientModeSupport.attach(this)

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
                Log.d(TAG, "Receive new Location !")
                p0.lastLocation?.let {
                    currentLocation = it
                    Log.d(TAG, "location in callback : ${it.latitude}")
                } ?: run {
                    Log.d(TAG, "Location information isn't available.")
                }
            }
        }



        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            permission = true;

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())

        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_CODE
            )
        }

    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            if (grantResults.size == 2) {
                permission = true
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
            }
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(p0: MessageEvent) {
        try {
            val messageEventPath: String = p0.path
            Log.d(TAG, "onMessageReceived event received : $messageEventPath")

                if (messageEventPath == PAIRING) {
                    try {
                        val nodeId: String = p0.sourceNodeId
                        this.node = nodeId

                        val task = Wearable.getMessageClient(activityContext!!).sendMessage(nodeId, PAIRING, null)

                        task.addOnCompleteListener {
                            if (it.isSuccessful) {
                                Log.d(TAG_INIT, "Message sent.")
                                binding.infoTxt.text = getText(R.string.waiting_for)
                                mobileDeviceConnected = true
                            }
                            else {
                                Log.d(TAG_INIT, "Message not sent.")
                            }
                        }

                    } catch (e: Exception) {
                        Log.d(TAG_INIT, "Paring Exception : $e")
                    }
            } else if (messageEventPath == ASK_LOC) {
                if (permission && isLocationEnabled()) {
                    Log.d(TAG, "location :${currentLocation?.latitude} ${currentLocation?.longitude}")
                } else {
                    Log.d(TAG, "Permission not set")
                }

            }
        } catch (e: Exception) {
            Log.d(TAG_MSG, "Handled in onMessageReceived")
            e.printStackTrace()
        }
    }


    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)

            val removeTask = fusedLocationClient.removeLocationUpdates(locationCallback)
            removeTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Location Callback removed.")
                } else {
                    Log.d(TAG, "Failed to remove Location Callback.")
                }
            }
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
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
            if (permission) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        Log.d(TAG, "Stopping wearable Device App")
        super.onStop()
        if (mobileDeviceConnected && node != null) {
            try {
                val task = Wearable.getMessageClient(this).sendMessage(node!!, EXIT, null)
                task.addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d(TAG, "Exit event sent")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Exception while exit")
            }
            val removeTask = fusedLocationClient.removeLocationUpdates(locationCallback)
            removeTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Location Callback removed.")
                } else {
                    Log.d(TAG, "Failed to remove Location Callback.")
                }
            }
        }
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