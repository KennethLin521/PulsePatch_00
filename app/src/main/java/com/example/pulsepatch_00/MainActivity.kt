package com.example.pulsepatch_00

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var deviceName: String? = null
    private var deviceAddress: String? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var connectButton: Button
    private lateinit var toggleButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar

    private val CONNECTING_STATUS = 1
    private val MESSAGE_READ = 2

    companion object {
        var handler: Handler? = null
        var mmSocket: BluetoothSocket? = null
        var connectedThread: ConnectedThread? = null
        var createConnectThread: CreateConnectThread? = null

        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        connectButton = findViewById(R.id.buttonConnect)
        toggleButton = findViewById(R.id.buttonToggle)
        statusTextView = findViewById(R.id.textViewInfo)
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.GONE

        // Check for Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissions()
        } else {
            initBluetooth()
        }

        deviceName = intent.getStringExtra("deviceName")
        deviceAddress = intent.getStringExtra("deviceAddress")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (deviceName != null && deviceAddress != null) {
            statusTextView.text = "Connecting to $deviceName..."
            progressBar.visibility = View.VISIBLE
            connectButton.isEnabled = false

            // Start connection thread
            createConnectThread = CreateConnectThread(bluetoothAdapter, deviceAddress!!)
            createConnectThread?.start()
        }

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    CONNECTING_STATUS -> {
                        when (msg.arg1) {
                            1 -> {
                                statusTextView.text = "Connected to $deviceName"
                                progressBar.visibility = View.GONE
                                connectButton.isEnabled = true
                                toggleButton.isEnabled = true
                            }
                            -1 -> {
                                statusTextView.text = "Failed to connect"
                                progressBar.visibility = View.GONE
                                connectButton.isEnabled = true
                            }
                        }
                    }
                    MESSAGE_READ -> {
                        val arduinoMsg = msg.obj.toString()
                        statusTextView.text = "Arduino Message: $arduinoMsg"
                    }
                }
            }
        }

        connectButton.setOnClickListener {
            val intent = Intent(this, SelectDeviceActivity::class.java)
            startActivity(intent)
        }

        toggleButton.setOnClickListener {
            val command = if (toggleButton.text == "Turn On") "<turn on>" else "<turn off>"
            connectedThread?.write(command)
            toggleButton.text = if (toggleButton.text == "Turn On") "Turn Off" else "Turn On"
        }
    }

    // Bluetooth Permissions Handling
    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private fun requestBluetoothPermissions() {
        if (bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            initBluetooth()
        } else {
            ActivityCompat.requestPermissions(
                this,
                bluetoothPermissions,
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBluetooth()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initBluetooth() {
        // Your existing code to initialize Bluetooth, if any
    }

    // Rest of your code including CreateConnectThread and ConnectedThread...

}
