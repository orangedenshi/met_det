package com.nyamadax.met_det

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.UUID
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() , OnClickListener {
    private val lock = java.util.concurrent.locks.ReentrantLock()
    private var devices  : MutableList<BluetoothDevice?> = mutableListOf()
    private var devices2 : MutableList<String?> = mutableListOf()

    private enum class bleCommand(val id: Int){
        None(0),
        Parameter(1),
        Reset(2),
        ResetDetect(3);
    }

    /** 温度と湿度を表示するTextView */
    private val name_tv by lazy {
        findViewById<TextView>(R.id.editText1)
    }
    private val threshold_tv by lazy {
        findViewById<TextView>(R.id.editText2)
    }
    private val keeptime_tv by lazy {
        findViewById<TextView>(R.id.editText3)
    }
    private val nonetime_tv by lazy {
        findViewById<TextView>(R.id.editText4)
    }
    private val delaytime_tv by lazy {
        findViewById<TextView>(R.id.editText5)
    }
    private val rms_tv by lazy {
        findViewById<TextView>(R.id.textView14)
    }
    private val rmsave_tv by lazy {
        findViewById<TextView>(R.id.textView17)
    }
    private val rmsaveall_tv by lazy {
        findViewById<TextView>(R.id.textView11)
    }
    private val max_tv by lazy {
        findViewById<TextView>(R.id.textView20)
    }
    private val maxall_tv by lazy {
        findViewById<TextView>(R.id.textView23)
    }
    private val maxdet_tv by lazy {
        findViewById<TextView>(R.id.textView26)
    }
    private val cnt_tv by lazy {
        findViewById<TextView>(R.id.textView29)
    }
    private val button_connect by lazy {
        findViewById<Button>(R.id.button4)
    }

    private val error_text by lazy {
        findViewById<TextView>(R.id.textView2)
    }

    private val version_text by lazy {
        findViewById<TextView>(R.id.textView5)
    }

    private val holdReleaseButton by lazy {
        findViewById<Button>(R.id.button5)
    }

    private val holding_text by lazy {
        findViewById<TextView>(R.id.textView34)
    }

    private val hold_annotation by lazy {
        findViewById<TextView>(R.id.textView33)
    }

    private val det_setten by lazy {
        findViewById<TextView>(R.id.textView36)
    }
    private val err_setten by lazy {
        findViewById<TextView>(R.id.textView38)
    }

    private val delaytime_row by lazy {
        findViewById<TableRow>(R.id.tableRow1)
    }


    private var bluetoothGatt: BluetoothGatt? = null

    private var characteristic_tx: BluetoothGattCharacteristic? = null

    private var parameterSet: Int = 1

    private var oName       : String = ""
    private var oThresholdI : Int    = 0
    private var oKeepTime   : Int    = 0
    private var oNoneTime   : Int    = 0

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (allPermissionsGranted()) {
            // 位置情報のパーミッションが取得できている場合は、BLEのスキャンを開始
            bleScanStart()
        }
        // else {
        //     // 位置情報のパーミッションが取得できていない場合は、位置情報の取得のパーミッションの許可を求める
        //     ActivityCompat.requestPermissions(
        //         this,
        //         REQUIRED_PERMISSIONS,
        //         REQUEST_CODE_PERMISSIONS
        //     )
        // }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.button1).also { it.setOnClickListener(this) }
        findViewById<Button>(R.id.button2).also { it.setOnClickListener(this) }
        findViewById<Button>(R.id.button3).also { it.setOnClickListener(this) }
        findViewById<Button>(R.id.button5).also { it.setOnClickListener(this) }
        button_connect.also { it.setOnClickListener(this) }

        val pName = packageManager.getPackageInfo(packageName,0).versionName
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        // // val versionName = packageInfo.versionName
        val packageVersionCode = packageInfo.longVersionCode // API 28以上

        findViewById<TextView>(R.id.textView32).setOnClickListener{
            AlertDialog.Builder(this )
                .setTitle("Metal Detector Controller")
                .setPositiveButton("OK",null)
                .setMessage("Version: $pName \nBuild Version $packageVersionCode")
                .create()
                .show()
        }

        val rootView: ScrollView = findViewById(R.id.mainView)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight < screenHeight * 0.15) {
                val focusEditText = currentFocus as? EditText
                focusEditText?.clearFocus()
            }
        }
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode==2){
            bleScanStart()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(
                arrayOf<String>(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), 2
            )
            // ActivityCompat.requestPermissions(
            //     this,
            //     REQUIRED_PERMISSIONS,
            //     REQUEST_CODE_PERMISSIONS,
            // )
        }
        return ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED

        // if (
        //     checkSelfPermission(Manifest.permission.BLUETOOTH             ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN       ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN        ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT     ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION  ) == PackageManager.PERMISSION_GRANTED
        // ){
        // }else {
        //     requestPermissions(
        //         arrayOf<String>(
        //             Manifest.permission.BLUETOOTH,
        //             Manifest.permission.BLUETOOTH_ADMIN,
        //             Manifest.permission.BLUETOOTH_SCAN,
        //             Manifest.permission.BLUETOOTH_CONNECT,
        //             Manifest.permission.ACCESS_FINE_LOCATION
        //         ), 2
        //     )
        // }

        // return (ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH             ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN       ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN        ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT     ) == PackageManager.PERMISSION_GRANTED &&
        //     checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION  ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun bleScanStart() {
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            // sensorTextView.text = getString(R.string.not_support)
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            // sensorTextView.text = getString(R.string.no_power)
            return
        }

        // val devices1 = adapter.bondedDevices.toList()
        // val deviceNames: List<String> = devices1.map {
        //     if (ActivityCompat.checkSelfPermission(
        //             this,
        //             Manifest.permission.BLUETOOTH_CONNECT
        //         ) != PackageManager.PERMISSION_GRANTED
        //     ) {
        //         return
        //     }
        //     "${it.name},${it.address}"}
        // Log.d("aaaaaaaaaaaaaaaaaa",deviceNames.toString())

        val scanner = bluetoothAdapter.bluetoothLeScanner

        val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BLE_SERVICE_UUID)).build()
        val scanSettings = ScanSettings.Builder() .setScanMode(ScanSettings.SCAN_MODE_BALANCED) .build()
        Log.d(TAG, "Start BLE scan.")

        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.BLUETOOTH_SCAN ) != PackageManager.PERMISSION_GRANTED ) {
            return
        }
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    /** スキャンでデバイスが見つかった際のコールバック */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)


            lock.withLock {
                if(!devices.contains(result.device))
                {
                    devices.add(result.device)
                    devices2.add(result.scanRecord?.deviceName)
                }
            }


            // // Log.d("aaaaaaaaaaaaaaaaaa",result.device.name.toString())
            // // デバイスのGattサーバに接続
            // bluetoothGatt = result.device.connectGatt(this@MainActivity, false, gattCallback)
            // val resultConnectGatt = bluetoothGatt?.connect()
            // if (resultConnectGatt == true) {
            //     Log.d(TAG, "Success to connect gatt.")
            // } else {
            //     Log.w(TAG, "Failed to connect gatt.")
            // }
        }
    }

    private val supportedGattServices: List<BluetoothGattService>? get() = bluetoothGatt?.services

    /** デバイスのGattサーバに接続された際のコールバック */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (gatt == null) {
                Log.w("onConnectionStateChange", "Gatt is empty. Maybe Bluetooth adapter not initialized.")
                return
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("onConnectionStateChange", "Discover services.")
                if (ActivityCompat.checkSelfPermission( this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                gatt.discoverServices()
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            Log.d("onServicesDiscovered", "Services discovered.")

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (gatt == null) {
                    Log.w("onServicesDiscovered", "Gatt is empty. Maybe Bluetooth adapter not initialized.")
                    return
                }

                while(gatt.requestMtu(256)==false){
                    Log.w("onServicesDiscovered", "Gatt is empty. Maybe Bluetooth adapter not initialized.")
                }

                val service = gatt.getService(BLE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BLE_CHARACTERISTIC_UUID_RX)
                if (characteristic == null) {
                    Log.w("onServicesDiscovered", "Characteristic is empty. Maybe Bluetooth adapter not initialized.")
                    return
                }

                characteristic_tx = service.getCharacteristic(BLE_CHARACTERISTIC_UUID_TX)
                if (characteristic_tx == null) {
                    Log.w("onServicesDiscovered", "Characteristic_tx is empty. Maybe Bluetooth adapter not initialized.")
                    return
                }

                // Characteristic "4fafc201-1fb5-459e-8fcc-c5c9c331914b" のNotifyを監視する。
                // 変化があったら onCharacteristicChanged が呼ばれる。
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                gatt.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

                // descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                // gatt.writeDescriptor(descriptor)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,value:ByteArray) {
            super.onCharacteristicChanged(gatt, characteristic,value)

            // dataToView(characteristic?.value!!)
            this@MainActivity.runOnUiThread {
                dataToView(value)
            }
//             this@MainActivity.runOnUiThread {
//                 // val aaa = characteristic?.value
//                 // if(aaa!=null)Log.d("bbbbbbbbbbbbbbbbbbb",String(aaa))
//
//                 val data = Data.parse(characteristic?.value) ?: return@runOnUiThread
//
//                 // Log.d("bbb_rms      ",data.rms      .toString())
//                 // Log.d("bbb_rmsAve   ",data.rmsAve   .toString())
//                 // Log.d("bbb_max      ",data.max      .toString())
//                 // Log.d("bbb_maxAll   ",data.maxAll   .toString())
//                 // Log.d("bbb_maxDet   ",data.maxDet   .toString())
//                 // Log.d("bbb_count    ",data.count    .toString())
//                 // Log.d("bbb_threshold",data.threshold.toString())
//                 // Log.d("bbb_keepTime ",data.keepTime .toString())
//                 // Log.d("bbb_noneTime ",data.noneTime .toString())
//                 // Log.d("bbb_name     ",data.name                )
//
//                 if( oName        != data.name       ||
//                     oThresholdI  != data.thresholdI ||
//                     oKeepTime    != data.keepTime   ||
//                     oNoneTime    != data.noneTime   ||
//                     parameterSet != 0
//                 ){
//                     oName       = data.name
//                     oThresholdI = data.thresholdI
//                     oKeepTime   = data.keepTime
//                     oNoneTime   = data.noneTime
//
//                     name_tv     .text =   data.name
//                     threshold_tv.text = ((data.threshold*10.0).roundToInt()/10.0).toString()
//                     keeptime_tv .text =   data.keepTime.toString()
//                     nonetime_tv .text =   data.noneTime.toString()
//                     parameterSet = 0
//                 }
//
//                 rms_tv      .text = ((data.rms      *10.0).roundToInt()/10.0).toString()
//                 rmsave_tv   .text = ((data.rmsAve   *10.0).roundToInt()/10.0).toString()
//                 max_tv      .text = ((data.max      *10.0).roundToInt()/10.0).toString()
//                 maxall_tv   .text = ((data.maxAll   *10.0).roundToInt()/10.0).toString()
//                 maxdet_tv   .text = ((data.maxDet   *10.0).roundToInt()/10.0).toString()
//                 cnt_tv      .text =   data.count.toString()
//
//                 // val sb = StringBuilder()
//                 // sb.append("Temperature: ${String.format("%.2f", data.temperature)}\n")
//                 // sb.append("Pressure:    ${String.format("%.2f", data.pressure)}")
//             }
        }
    }
    @SuppressLint("ResourceType")
    fun dataToView(bytearray:ByteArray) {

        val data = Data.parse(bytearray) ?: return

        // Log.d("bbb_rms      ",data.rms      .toString())
        // Log.d("bbb_rmsAve   ",data.rmsAve   .toString())
        // Log.d("bbb_max      ",data.max      .toString())
        // Log.d("bbb_maxAll   ",data.maxAll   .toString())
        // Log.d("bbb_maxDet   ",data.maxDet   .toString())
        // Log.d("bbb_count    ",data.count    .toString())
        // Log.d("bbb_threshold",data.threshold.toString())
        // Log.d("bbb_keepTime ",data.keepTime .toString())
        // Log.d("bbb_noneTime ",data.noneTime .toString())
        // Log.d("bbb_name     ",data.name                )

        // Log.d("dataToView","error:"   + data.error.toString())
        // if(data.version!=null){
        //     Log.d("dataToView","version:" + (data.version/256).toString() + "." + (data.version%256).toString())
        // }

        if(data.error==null || data.error==0) {
            error_text.text = ""
        }else{
            error_text.text = "ERROR!"
        }

        if(data.version==null) {
            version_text.text = "-"
        }else{
            version_text.text = (String.format("%02d",data.version/256) + "." + String.format("%02d",data.version%256))
            hold_annotation.text = if(data.version>2) getString(R.string.label_annotation) else ""
        }

        if(data.version==null || data.version<=4){
            delaytime_row.visibility = View.INVISIBLE
        }else{
            delaytime_row.visibility = View.VISIBLE
        }

        if (oName != data.name ||
            oThresholdI != data.thresholdI ||
            oKeepTime != data.keepTime ||
            oNoneTime != data.noneTime ||
            parameterSet != 0
        ) {
            oName = data.name
            oThresholdI = data.thresholdI
            oKeepTime = data.keepTime
            oNoneTime = data.noneTime

            try {
                name_tv.text = data.name
                threshold_tv.text = ((data.threshold * 10.0).roundToInt() / 10.0).toString()
                keeptime_tv.text  = data.keepTime.toString()
                nonetime_tv.text  = data.noneTime.toString()
                delaytime_tv.text = data.delayTime.toString()
            }catch(e:Exception){
                parameterSet = 2
                Log.d("dataToView",e.toString())
            }
            if(parameterSet!=0){
                parameterSet--
            }
        }

        rms_tv.text       = ((data.rms * 10.0).roundToInt() / 10.0).toString()
        rmsave_tv.text    = ((data.rmsAve * 10.0).roundToInt() / 10.0).toString()
        rmsaveall_tv.text = if(data.rmsAveAll!=null){((data.rmsAveAll * 10.0).roundToInt() / 10.0).toString()}else{"-"}
        max_tv.text       = ((data.max * 10.0).roundToInt() / 10.0).toString()
        maxall_tv.text    = ((data.maxAll * 10.0).roundToInt() / 10.0).toString()
        maxdet_tv.text    = ((data.maxDet * 10.0).roundToInt() / 10.0).toString()
        cnt_tv.text       = data.count.toString()

        holdReleaseButton.isEnabled = data.isHoloding==1
        holding_text.text = if (data.isHoloding==1) getString(R.string.label_holding) else ""

        det_setten.text = if((data.setten and 1)==1) getString(R.string.label_b_setu) else getString(R.string.label_a_setu)
        err_setten.text = if((data.setten and 2)==2) getString(R.string.label_b_setu) else getString(R.string.label_a_setu)

        // val sb = StringBuilder()
        // sb.append("Temperature: ${String.format("%.2f", data.temperature)}\n")
        // sb.append("Pressure:    ${String.format("%.2f", data.pressure)}")
    }

    private data class Data(
        val rms        : Double,
        val rmsAve     : Double,
        val max        : Double,
        val maxAll     : Double,
        val maxDet     : Double,
        val count      : Int,
        val threshold  : Double,
        val keepTime   : Int,
        val noneTime   : Int,
        val name       : String,
        val thresholdI : Int,
        val error      : Int?,
        val version    : Int?,
        val rmsAveAll  : Double?,
        val isHoloding : Int,
        val setten     : Int,
        val delayTime  : Int,
    ) {
        companion object {
            /**
             * BLEから飛んできたデータをDataクラスにパースする
             */
            fun parse(data: ByteArray?): Data? {
                if (data == null || data.size<40) {
                    return null
                }

                val rmsBytes       = try{ ByteBuffer.wrap(data, 0 , 4)}catch (e:Exception){return null}
                val rmsAveBytes    = try{ ByteBuffer.wrap(data, 4 , 4)}catch (e:Exception){return null}
                val maxBytes       = try{ ByteBuffer.wrap(data, 8 , 4)}catch (e:Exception){return null}
                val maxAllBytes    = try{ ByteBuffer.wrap(data, 12, 4)}catch (e:Exception){return null}
                val maxDetBytes    = try{ ByteBuffer.wrap(data, 16, 4)}catch (e:Exception){return null}
                val countBytes     = try{ ByteBuffer.wrap(data, 20, 4)}catch (e:Exception){return null}
                val thresholdBytes = try{ ByteBuffer.wrap(data, 24, 4)}catch (e:Exception){return null}
                val keepTimeBytes  = try{ ByteBuffer.wrap(data, 28, 4)}catch (e:Exception){return null}
                val noneTimeBytes  = try{ ByteBuffer.wrap(data, 32, 4)}catch (e:Exception){return null}
                val len            = try{ ByteBuffer.wrap(data, 36, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){return null}
                var nameBytes = try{
                        ByteBuffer.wrap(data, 40, len)
                    } catch (e: Exception) {
                        return null
                    }

                val error          = try{ ByteBuffer.wrap(data, 72, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){null}
                val version        = try{ ByteBuffer.wrap(data, 76, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){null}
                val rmsAveAllI     = try{ ByteBuffer.wrap(data, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){null}
                val isHoloding     = try{ ByteBuffer.wrap(data, 84, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){0   }
                val setten         = try{ ByteBuffer.wrap(data, 88, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){0   }
                val delayTime      = try{ ByteBuffer.wrap(data, 92, 4).order(ByteOrder.LITTLE_ENDIAN).int}catch (e:Exception){0   }


//                 val rms        = rmsBytes      .order(ByteOrder.LITTLE_ENDIAN).int.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 val rmsAve     = rmsAveBytes   .order(ByteOrder.LITTLE_ENDIAN).int.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 val max        = maxBytes      .order(ByteOrder.LITTLE_ENDIAN).int.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 val maxAll     = maxAllBytes   .order(ByteOrder.LITTLE_ENDIAN).int.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 val maxDet     = maxDetBytes   .order(ByteOrder.LITTLE_ENDIAN).int.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 val count      = countBytes    .order(ByteOrder.LITTLE_ENDIAN).int
//                 val thresholdI = thresholdBytes.order(ByteOrder.LITTLE_ENDIAN).int
//                 val threshold  = thresholdI.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 // val threshold  = thresholdBytes.order(ByteOrder.LITTLE_ENDIAN).int.toDouble()*5.0*2.3*1000/(8192.0*2.0)
//                 val keepTime   = keepTimeBytes .order(ByteOrder.LITTLE_ENDIAN).int
//                 val noneTime   = noneTimeBytes .order(ByteOrder.LITTLE_ENDIAN).int
//                 val name       = StandardCharsets.UTF_8.decode(nameBytes.order(ByteOrder.LITTLE_ENDIAN)).toString()

               var rms       :Double
               var rmsAve    :Double
               var max       :Double
               var maxAll    :Double
               var maxDet    :Double
               var count     :Int
               var thresholdI:Int
               var threshold :Double
               var keepTime  :Int
               var noneTime  :Int
               var name      :String
               var rmsAveAll :Double?
               try {
                   rms        = rmsBytes.order(ByteOrder.LITTLE_ENDIAN).int.toDouble()    * 5.0 * 2.3 * 1000 / (8192.0 * 2.0)
                   rmsAve     = rmsAveBytes.order(ByteOrder.LITTLE_ENDIAN).int.toDouble() * 5.0 * 2.3 * 1000 / (8192.0 * 2.0)
                   max        = maxBytes.order(ByteOrder.LITTLE_ENDIAN).int.toDouble()    * 5.0 * 2.3 * 1000 / (8192.0 * 2.0)
                   maxAll     = maxAllBytes.order(ByteOrder.LITTLE_ENDIAN).int.toDouble() * 5.0 * 2.3 * 1000 / (8192.0 * 2.0)
                   maxDet     = maxDetBytes.order(ByteOrder.LITTLE_ENDIAN).int.toDouble() * 5.0 * 2.3 * 1000 / (8192.0 * 2.0)
                   count      = countBytes.order(ByteOrder.LITTLE_ENDIAN).int
                   thresholdI = thresholdBytes.order(ByteOrder.LITTLE_ENDIAN).int
                   threshold  = thresholdI.toDouble() * 5.0 * 2.3 * 1000 / (8192.0 * 2.0)
                   keepTime   = keepTimeBytes.order(ByteOrder.LITTLE_ENDIAN).int
                   noneTime   = noneTimeBytes.order(ByteOrder.LITTLE_ENDIAN).int
                   name       = StandardCharsets.UTF_8.decode(nameBytes.order(ByteOrder.LITTLE_ENDIAN)).toString()
                   rmsAveAll  = if(rmsAveAllI!=null){rmsAveAllI* 5.0 * 2.3 * 1000 / (8192.0 * 2.0)}else{null}
               }catch (e:Exception){
                   Log.d("Data class",e.toString())
                   return null
               }

                return Data( rms       ,
                    rmsAve    ,
                    max       ,
                    maxAll    ,
                    maxDet    ,
                    count     ,
                    threshold ,
                    keepTime  ,
                    noneTime  ,
                    name      ,
                    thresholdI,
                    error     ,
                    version   ,
                    rmsAveAll ,
                    isHoloding,
                    setten,
                    delayTime,
                )
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        private const val REQUEST_CODE_PERMISSIONS = 10

        @RequiresApi(Build.VERSION_CODES.S)
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
            )

        /** BLEのサービスUUID */
        private val BLE_SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

        /** BLEのCharacteristic UUID */
        private val BLE_CHARACTERISTIC_UUID_TX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val BLE_CHARACTERISTIC_UUID_RX = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    }

    private fun checkThreshold(): ByteArray? {
        val dlogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.err_threshold)
            .setPositiveButton("OK", null)

        var value: Double?
        try {
            value = threshold_tv.text.toString().toDoubleOrNull()
        } catch (e: Exception) {
            value = null
        }

        if(value ==null) {
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_exchange))
                it.show()
            }
            return null
        }

        if(value<0    ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_th_lower))
                it.show()
            }
            return null
        }
        if(value>8000 ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_th_higher))
                it.show()
            }
            return null
        }

        val iValue = (value*(8192.0*2.0)/(5.0*2.3*1000)).roundToInt()

        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(iValue).array()
    }

    private fun checkKeepTime(): ByteArray? {
        val dlogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.err_keepTime)
            .setPositiveButton("OK", null)
        var value: Int?
        try {
            value = keeptime_tv .text.toString().toInt()
        } catch (e: Exception) {
            value = null
        }

        if(value ==null) {
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_exchange))
                it.show()
            }
            return null
        }
        if(value<0    ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_ke_lower))
                it.show()
            }
            return null
        }
        if(value>10000 ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_ke_higher))
                it.show()
            }
            return null
        }

        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun checkNoneTime(): ByteArray? {
        val dlogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.err_noneTime)
            .setPositiveButton("OK", null)
        var value: Int?
        try {
            value = nonetime_tv.text.toString().toInt()
        } catch (e: Exception) {
            value = null
        }

        if(value ==null) {
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_exchange))
                it.show()
            }
            return null
        }
        if(value<0    ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_no_lower))
                it.show()
            }
            return null
        }
        if(value>1000 ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_no_higher))
                it.show()
            }

            return null
        }

        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun checkDelayTime(): ByteArray? {
        val dlogBuilder = AlertDialog.Builder(this)
            .setTitle(R.string.err_delayTime)
            .setPositiveButton("OK", null)
        var value: Int?
        try {
            value = delaytime_tv.text.toString().toInt()
        } catch (e: Exception) {
            value = null
        }

        if(value ==null) {
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_exchange))
                it.show()
            }
            return null
        }
        if(value<0    ){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_de_lower))
                it.show()
            }
            return null
        }
        if(value>10000){
            dlogBuilder.create().also { it ->
                it.setMessage(getString(R.string.err_de_higher))
                it.show()
            }
            return null
        }

        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onClick(v: View?) {
        if(v==null)return
        when(v.id){
            R.id.button1 -> {
                characteristic_tx?.also {
                    val z = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bleCommand.Reset.id).array()
                    bluetoothGatt?.writeCharacteristic(it,z,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                }
            }
            R.id.button5 -> {
                characteristic_tx?.also {
                    val z = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bleCommand.ResetDetect.id).array()
                    bluetoothGatt?.writeCharacteristic(it,z,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                }
            }
            R.id .button2 ->{
                val focusEditText = currentFocus as? EditText
                if(focusEditText!=null){
                    val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(focusEditText.windowToken, 0)
                    focusEditText.clearFocus() // フォーカスを解除する
                }

                characteristic_tx?.also {
                    val z = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bleCommand.Parameter.id).array()
                    // val z = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array()
                    val a = checkThreshold()
                    if(a==null) {
                        Log.d("onClick","threshold error")
                        return
                    }
                    val b = checkKeepTime()
                    if(b==null) {
                        Log.d("onClick","keeptime error")
                        return
                    }
                    val c = checkNoneTime()
                    if(c==null) {
                        Log.d("onClick","nonetime error")
                        return
                    }
                    val d = try{
                        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(name_tv.text.length).array()
                    }catch (e: Exception){
                        return
                    }

                    val e = checkDelayTime()
                    if(e==null) {
                        Log.d("onClick","delaytime error")
                        return
                    }

                    // val e = name_tv     .text.toString().toByteArray(Charsets.UTF_8)
                    // val f = z + a + b + c + d + e
                    val f = z + a + b + c + d + e
                    oThresholdI = 0
                    oKeepTime   = 0
                    oNoneTime   = 0
                    parameterSet = 2

                    bluetoothGatt?.writeCharacteristic(it,f,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                }
            }
            R.id .button3 ->{
                parameterSet = 1
            }
            R.id.button4 -> {
                if(bluetoothGatt==null) {

                    // if(devices.isEmpty())return

                    var l: MutableList<String> = mutableListOf()
                    lock.withLock {
                        // l = devices.map { it?.name.toString() }
                        devices.forEachIndexed {index,element ->
                            var name = element?.name
                            if(name==null){
                                name = devices2?.get(index).toString()
                            }

                            l.add(name)

                        }
                    }

                    val sl = l.toTypedArray()
                    var v = -1
                    AlertDialog.Builder(this) // FragmentではActivityを取得して生成
                        .setTitle(R.string.choose_sensor)
                        .setSingleChoiceItems(sl, v) { dialog, which ->
                            v = which
                        }
                        .setPositiveButton("OK") { dialog, which ->
                            if(v==-1) return@setPositiveButton
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                return@setPositiveButton
                            }
                            bluetoothGatt = devices[v]?.connectGatt(this@MainActivity, false, gattCallback)
                            val resultConnectGatt = bluetoothGatt?.connect()
                            if (resultConnectGatt == true) {
                                button_connect.text = getString(R.string.bt_disconnect)
                                parameterSet = 1
                                Log.d(TAG, "Success to connect gatt.")
                            } else {
                                Log.w(TAG, "Failed to connect gatt.")
                            }

                        }
                        .show()
                }else{
                    bluetoothGatt?.disconnect()
                    bluetoothGatt = null
                    // lock.withLock {
                    //     devices.clear()
                    //     devices2.clear()
                    // }
                    bleScanStart()
                    name_tv       .text = ""
                    threshold_tv  .text = ""
                    keeptime_tv   .text = ""
                    delaytime_tv  .text = ""
                    nonetime_tv   .text = ""
                    rms_tv        .text = "0"
                    rmsave_tv     .text = "0"
                    max_tv        .text = "0"
                    maxall_tv     .text = "0"
                    maxdet_tv     .text = "0"
                    cnt_tv        .text = "0"
                    button_connect.text = getString(R.string.bt_connect)
                    error_text    .text = ""
                    version_text  .text = ""
                    rmsaveall_tv  .text = "0"

                    det_setten.text = getString(R.string.label_non_setu)
                    err_setten.text = getString(R.string.label_non_setu)

                    holdReleaseButton.isEnabled = false
                    holding_text.text = ""
                    hold_annotation.text = ""
                }
            }
        }
    }
}