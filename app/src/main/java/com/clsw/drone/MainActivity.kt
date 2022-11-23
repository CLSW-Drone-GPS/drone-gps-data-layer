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

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context? = null

    private var wearableDeviceConnected: Boolean = false

    private val PAIRING: String = "PAIRING"
    private val EXIT: String = "EXIT"
    private val ASK_LOC: String = "ASK_LOC"

    private val TAG_INIT: String = "Initialisation"
    private val TAG_MSG: String = "Messages"

    private var nodeId: String? = null

    private lateinit var binding: ActivityMainBinding

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
                //Couroutine
                Log.d(TAG_INIT, "Initialisation")
                initialiseDevicePairing(tempAct)
            }
        }

        binding.getLocationButton.setOnClickListener {
            if (wearableDeviceConnected) {
                Log.d(TAG_MSG, "sent")
                val task = Wearable.getMessageClient(this).sendMessage(nodeId!!, ASK_LOC, null)
                task.addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d(TAG_MSG, "get location Message sent")
                    }
                }
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun initialiseDevicePairing(tempAct: Activity) {
        //Coroutine
        launch(Dispatchers.Default) {

            try {
                getNodesNew(tempAct.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG_INIT, "Fail on get nodes : $e")
            }
        }
    }

    private fun getNodesNew(context: Context) {

        val nodeTasks = Wearable.getNodeClient(context).connectedNodes

        try {
            val nodes = Tasks.await(nodeTasks)
            Log.d(TAG_INIT, "Get nodes successfull")

            for (node in nodes) {
                this.nodeId = node.id
                try {
                    Wearable.getMessageClient(context).sendMessage(node.id, PAIRING, null)

                } catch (exception: Exception) {
                    Log.e(TAG_INIT, "Node pairing failed : $exception")
                }
            }

        } catch (exception: Exception) {
            Log.e(TAG_INIT, "Can't reach Wearable : $exception")
        }

    }

    override fun onDataChanged(p0: DataEventBuffer) {
    }

    @SuppressLint("SetTextI18n")
    override fun onMessageReceived(p0: MessageEvent) {
        try {
            val messageEventPath: String = p0.path
            Log.d(TAG_MSG, "receive message $messageEventPath")

            if (messageEventPath == PAIRING) {
                Toast.makeText(
                    activityContext,
                    "Wearable device paired and app is open. Tap the \"Get the current Location\".",
                    Toast.LENGTH_LONG
                ).show()
                binding.getLocationButton.visibility = View.VISIBLE
                binding.checkConnectionBtn.visibility = View.INVISIBLE
                wearableDeviceConnected = true

            } else if (messageEventPath == EXIT) {
                Toast.makeText(
                    activityContext,
                    "Wearable device app is stopped. Re-open it and connect it again.",
                    Toast.LENGTH_LONG
                ).show()
                binding.getLocationButton.visibility = View.INVISIBLE
                binding.checkConnectionBtn.visibility = View.VISIBLE
                wearableDeviceConnected = false
            } else {
                Log.d(TAG_INIT, "Unknown path : $messageEventPath")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG_INIT, "Handled")
        }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
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
}