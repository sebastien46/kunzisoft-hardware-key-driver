package com.kunzisoft.hardware.key

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import com.kunzisoft.hardware.yubikey.challenge.VirtualYubiKey
import com.kunzisoft.hardware.yubikey.challenge.NfcYubiKey
import com.kunzisoft.hardware.yubikey.challenge.UsbYubiKey
import com.kunzisoft.hardware.yubikey.challenge.YubiKey

data class ConnectionMethods(
    val isUsbSupported: Boolean,
    val isNfcSupported: Boolean,
    val isVirtualKeyConfigured: Boolean
)

internal val ConnectionMethods.hasAnySupport: Boolean
    get() = isUsbSupported || isNfcSupported || isVirtualKeyConfigured

/**
 * Manages the lifecycle of a YubiKey connection via USB or NFC.
 */
class ConnectionManager(private val activity: Activity) : BroadcastReceiver(),
    NfcAdapter.ReaderCallback,
    ActivityLifecycleCallbacks {

    private var connectReceiver: YubiKeyConnectReceiver? = null
    private var unplugReceiver: YubiKeyUsbUnplugReceiver? = null
    private var usbPermissionDeniedReceiver: UsbPermissionDeniedReceiver? = null

    private var requestingUsbPermission: Boolean = false

    private val connectionMethods = getSupportedConnectionMethods(activity)

    /**
     * Receiver interface that is called when a YubiKey was connected.
     */
    interface YubiKeyConnectReceiver {
        /**
         * Called when a YubiKey was connected via USB or NFC.
         *
         * @param yubiKey The YubiKey driver implementation, instantiated with a connection to the
         * YubiKey.
         */
        fun onYubiKeyConnected(yubiKey: YubiKey)
    }

    /**
     * Receiver interface that is called when a YubiKey connected via USB was unplugged.
     */
    interface YubiKeyUsbUnplugReceiver {
        /**
         * Called when a YubiKey connected via USB was unplugged.
         */
        fun onYubiKeyUnplugged()
    }

    /**
     * Receiver interface that is called when the user denied the permission for accessing the
     * YubiKey via USB.
     */
    interface UsbPermissionDeniedReceiver {
        /**
         * Called when the user denied the permission for accessing the YubiKey via USB.
         */
        fun onUsbPermissionDenied()
    }

    /**
     * May only be instantiated as soon as the basic initialization of a new activity is complete
     */
    init {
        activity.application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {
        initConnection()
    }

    fun initConnection() {
        // Debug with dummy connection if no supported connection
        if (connectionMethods.hasAnySupport) {
            if (connectionMethods.isVirtualKeyConfigured) {
                initVirtualKeyConnection(activity)
            } else {
                if (connectReceiver != null) {
                    if (connectionMethods.isUsbSupported) {
                        initUSBConnection(activity)
                    }
                    if (connectionMethods.isNfcSupported) {
                        initNFCConnection(activity)
                    }
                }
            }
        }
    }

    private fun initVirtualKeyConnection(activity: Activity) {
        // Debug by injecting a known byte array
        connectReceiver?.onYubiKeyConnected(VirtualYubiKey(activity))
    }

    private fun initUSBConnection(activity: Activity) {
        activity.registerReceiver(this, IntentFilter(ACTION_USB_PERMISSION_REQUEST))
        activity.registerReceiver(this, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))

        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        for (device in usbManager.deviceList.values) {
            handleUsbDevice(activity, device)
        }
    }

    private fun initNFCConnection(activity: Activity) {
        val options = Bundle()

        // Workaround for some broken Nfc firmware implementations that poll the card too fast
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

        NfcAdapter.getDefaultAdapter(activity).enableReaderMode(
            activity,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            options
        )
    }

    /**
     * Waits for a YubiKey to be connected.
     *
     * @param receiver The receiver implementation to be called as soon as a YubiKey was connected.
     */
    fun waitForYubiKey(receiver: YubiKeyConnectReceiver?) {
        connectReceiver = receiver
    }

    /**
     * Waits until no YubiKey is connected.
     *
     * @param receiver The receiver implementation to be called as soon as no YubiKey is connected
     * anymore.
     */
    fun waitForYubiKeyUnplug(context: Context, receiver: YubiKeyUsbUnplugReceiver) {
        if (!connectionMethods.isUsbSupported) {
            receiver.onYubiKeyUnplugged()
            return
        }
        if (!isYubiKeyPlugged(context)) {
            receiver.onYubiKeyUnplugged()
            return
        }
        unplugReceiver = receiver
        context.registerReceiver(this, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
    }

    fun registerUsbPermissionDeniedReceiver(receiver: UsbPermissionDeniedReceiver) {
        usbPermissionDeniedReceiver = receiver
    }

    private fun isYubiKeyPlugged(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (device in usbManager.deviceList.values) {
            if (UsbYubiKey.Type.isDeviceKnown(device)) return true
        }
        return false
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_USB_PERMISSION_REQUEST -> {
                requestingUsbPermission = false
                // Do not keep asking for permission to access a YubiKey that was unplugged already
                if (isYubiKeyPlugged(context)) {
                    (intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as? UsbDevice)
                        ?.let { device ->
                            val permissionGranted =
                                intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            if (permissionGranted) {
                                handleUsbDevice(context, device)
                            } else {
                                usbPermissionDeniedReceiver?.onUsbPermissionDenied()
                            }
                        }
                }
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                (intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as? UsbDevice)
                    ?.let { device ->
                        handleUsbDevice(context, device)
                    }
            UsbManager.ACTION_USB_DEVICE_DETACHED ->
                (intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as? UsbDevice)
                    ?.let { device ->
                        if (UsbYubiKey.Type.isDeviceKnown(device)) {
                            context.unregisterReceiver(this)
                            unplugReceiver?.onYubiKeyUnplugged()
                            unplugReceiver = null
                        }
                    }
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep = IsoDep.get(tag) ?: return
        if (connectionMethods.isUsbSupported)
            activity.unregisterReceiver(
                this
            )

        activity.runOnUiThread {
            connectReceiver?.onYubiKeyConnected(NfcYubiKey(isoDep))
            connectReceiver = null
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (connectReceiver != null && connectionMethods.isNfcSupported) {
            NfcAdapter.getDefaultAdapter(activity).disableReaderMode(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        try {
            if (connectReceiver != null || unplugReceiver != null) activity.unregisterReceiver(
                this
            )
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Error when unregister receiver", e)
        }
    }

    private fun handleUsbDevice(context: Context, device: UsbDevice) {
        if (!UsbYubiKey.Type.isDeviceKnown(device)) return
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            context.unregisterReceiver(this)
            if (connectionMethods.isNfcSupported) {
                try {
                    NfcAdapter.getDefaultAdapter(context).disableReaderMode(context as Activity)
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "Unable to disable NFC reader mode.", e)
                }
            }
            connectReceiver!!.onYubiKeyConnected(UsbYubiKey(device, usbManager.openDevice(device)))
            connectReceiver = null
        } else if (!requestingUsbPermission) {
            requestingUsbPermission = true
            requestPermission(context, device)
        }
    }

    private fun requestPermission(context: Context, device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }

        usbManager.requestPermission(
            device,
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION_REQUEST),
                flags
            )
        )
    }

    /**
     * Gets the connection methods (USB and/or NFC) that are supported on the Android device.
     *
     * @return A byte that may or may not have the [.CONNECTION_METHOD_USB] and
     * [.CONNECTION_METHOD_USB] bits set.
     */
    fun getSupportedConnectionMethods(context: Context): ConnectionMethods {
        val packageManager = context.packageManager
        var isUsbSupported = false
        var isNfcSupported = false
        var isVirtualKeyConfigured = false

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            isUsbSupported = true
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
            && NfcAdapter.getDefaultAdapter(context).isEnabled) {
            isNfcSupported = true
        }
        // TODO virtual key configured
        if (BuildConfig.DEBUG) {
            isVirtualKeyConfigured = true
        }

        return ConnectionMethods(isUsbSupported, isNfcSupported, isVirtualKeyConfigured)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        activity.application.unregisterActivityLifecycleCallbacks(this)
    }

    companion object {
        private const val ACTION_USB_PERMISSION_REQUEST =
            "android.yubikey.intent.action.USB_PERMISSION_REQUEST"
    }
}
