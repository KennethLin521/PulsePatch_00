package com.example.pulsepatch_00

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
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
    private val REQUEST_ENABLE_BT = 3 // Constant for enabling Bluetooth
    private var connectedThread: ConnectedThread? = null
    private var createConnectThread: CreateConnectThread? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private var scanning = false
    private var handler = Handler()
    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000

    companion object {
        var handler: Handler? = null
        var mmSocket: BluetoothSocket? = null
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

        // Check Bluetooth feature availability
        val bluetoothAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val bluetoothLEAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        // Check for Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissions()
        } else {
            initBluetooth()
        }

        deviceName = intent.getStringExtra("deviceName")
        deviceAddress = intent.getStringExtra("deviceAddress")

        // Get BluetoothAdapter using BluetoothManager
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

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
        val bluetoothManager = getSystemService(BluetoothManager::class.java) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth, inform the user
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            finish()
    }

    // Define CreateConnectThread and ConnectedThread classes here...
}

    private fun scanLeDevice() {
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner.stopScan(leScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    private val leDeviceListAdapter = LeDeviceListAdapter()
    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            leDeviceListAdapter.addDevice(result.device)
            leDeviceListAdapter.notifyDataSetChanged()
        }
    }




    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // Bluetooth has been enabled
                Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
            } else {
                // User denied Bluetooth, handle accordingly
                Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_SHORT).show()
            }
        }
    }

}