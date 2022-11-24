package com.clsw.drone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.clsw.drone.databinding.ActivityMainBinding
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import org.json.JSONObject

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context? = null

    private var wearableDeviceConnected: Boolean = false

    private val PAIRING: String = "PAIRING"
    private val EXIT: String = "EXIT"
    private val ASK_LOC: String = "ASK_LOC"
    private val FLY_INFO: String = "FLY_INFO"

    private val TAG: String = "Mobile Drone"
    private var nodeId: String? = null

    private lateinit var binding: ActivityMainBinding

    private var allowFly = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        activityContext = this
        wearableDeviceConnected = false

        binding.checkConnectionBtn.setOnClickListener {
            if (!wearableDeviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                // Couroutine
                Log.d(TAG, "Initialisation")
                initialiseDevicePairing(tempAct)
            }
        }

        binding.getLocationButton.setOnClickListener {
            if (wearableDeviceConnected) {
                Log.d(TAG, "sent")

                sendMessages(nodeId!!, ASK_LOC, null, "Get location message event sent", "null")
            }
        }
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
                binding.getLocationButton.visibility = View.VISIBLE
                binding.checkConnectionBtn.visibility = View.INVISIBLE
                wearableDeviceConnected = true
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
            }
            ASK_LOC -> {
                val json = JSONObject(String(p0.data))
                Log.d(TAG, "revceive data rom wear : $json")

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



}