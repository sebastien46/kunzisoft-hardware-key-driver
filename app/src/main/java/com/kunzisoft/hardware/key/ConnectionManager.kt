package com.kunzisoft.hardware.key

import android.app.Activity
import android.content.BroadcastReceiver
import android.app.Application.ActivityLifecycleCallbacks
import com.kunzisoft.hardware.key.ConnectionManager.YubiKeyConnectReceiver
import com.kunzisoft.hardware.key.ConnectionManager.YubiKeyUsbUnplugReceiver
import com.kunzisoft.hardware.yubikey.challenge.YubiKey
import android.os.Bundle
import com.kunzisoft.hardware.key.ConnectionManager
import com.kunzisoft.hardware.yubikey.challenge.DummyYubiKey
import android.hardware.usb.UsbManager
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.nfc.NfcAdapter
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.content.Intent
import android.nfc.tech.IsoDep
import com.kunzisoft.hardware.yubikey.challenge.UsbYubiKey
import android.os.Parcelable
import com.kunzisoft.hardware.yubikey.challenge.NfcYubiKey
import android.content.pm.PackageManager
import android.nfc.Tag
import android.util.Log
import java.lang.Exception
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Manages the lifecycle of a YubiKey connection via USB or NFC.
 */
internal class ConnectionManager(private val activity: Activity) : BroadcastReceiver(),
    ActivityLifecycleCallbacks {
    private var isActivityResumed = false
    private var connectReceiver: YubiKeyConnectReceiver? = null
    private var unplugReceiver: YubiKeyUsbUnplugReceiver? = null

    /**
     * Receiver interface that is called when a YubiKey was connected.
     */
    internal interface YubiKeyConnectReceiver {
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
    internal interface YubiKeyUsbUnplugReceiver {
        /**
         * Called when a YubiKey connected via USB was unplugged.
         */
        fun onYubiKeyUnplugged()
    }

    /**
     * May only be instantiated as soon as the basic initialization of a new activity is complete
     *
     * @param activity As the connection lifecycle depends on the activity lifecycle, an active
     * [Activity] must be passed
     */
    init {
        activity.application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        // Debug with dummy connection if no supported connection
        if (BuildConfig.DEBUG && supportedConnectionMethods == CONNECTION_VOID) {
            initDummyConnection()
        } else {
            if (connectReceiver == null
                || supportedConnectionMethods and CONNECTION_METHOD_USB == CONNECTION_VOID)
                return
            initUSBConnection()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (connectReceiver == null
            || supportedConnectionMethods and CONNECTION_METHOD_NFC == CONNECTION_VOID)
            return
        initNFCConnection()
        isActivityResumed = true
    }

    private fun initDummyConnection() {
        // Debug by injecting a known byte array
        connectReceiver!!.onYubiKeyConnected(DummyYubiKey(activity))
    }

    private fun initUSBConnection() {
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        activity.registerReceiver(this, IntentFilter(ACTION_USB_PERMISSION_REQUEST))
        activity.registerReceiver(this, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
        for (device in usbManager.deviceList.values) requestPermission(device)
    }

    private fun initNFCConnection() {
        val filter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
        NfcAdapter.getDefaultAdapter(activity).enableForegroundDispatch(
            activity,
            PendingIntent.getActivity(
                activity,
                -1,
                Intent(activity, activity.javaClass),
                flags
            ), arrayOf(filter), arrayOf(
                arrayOf(
                    IsoDep::class.java.name
                )
            )
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
    fun waitForYubiKeyUnplug(receiver: YubiKeyUsbUnplugReceiver) {
        if (supportedConnectionMethods and CONNECTION_METHOD_USB == CONNECTION_VOID) {
            receiver.onYubiKeyUnplugged()
            return
        }
        if (isYubiKeyNotPlugged) {
            receiver.onYubiKeyUnplugged()
            return
        }
        unplugReceiver = receiver
        activity.registerReceiver(this, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
    }

    private val isYubiKeyNotPlugged: Boolean
        get() {
            val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
            for (device in usbManager.deviceList.values) {
                if (UsbYubiKey.Type.isDeviceKnown(device)) return false
            }
            return true
        }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_USB_PERMISSION_REQUEST -> {
                if (!isYubiKeyNotPlugged) // Do not keep asking for permission to access a YubiKey that was unplugged already
                requestPermission(intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?)
            }
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> requestPermission(
                intent.getParcelableExtra<Parcelable>(
                    UsbManager.EXTRA_DEVICE
                ) as UsbDevice?
            )
            UsbManager.ACTION_USB_DEVICE_DETACHED -> if (UsbYubiKey.Type.isDeviceKnown(
                    intent.getParcelableExtra<Parcelable>(
                        UsbManager.EXTRA_DEVICE
                    ) as UsbDevice?
                )
            ) {
                activity.unregisterReceiver(this)
                unplugReceiver!!.onYubiKeyUnplugged()
                unplugReceiver = null
            }
            NfcAdapter.ACTION_TECH_DISCOVERED -> {
                val isoDep =
                    IsoDep.get(intent.getParcelableExtra<Parcelable>(NfcAdapter.EXTRA_TAG) as Tag?)
                        ?: // Not a YubiKey
                        return
                if (supportedConnectionMethods and CONNECTION_METHOD_USB != CONNECTION_VOID)
                    activity.unregisterReceiver(
                    this
                )
                connectReceiver!!.onYubiKeyConnected(NfcYubiKey(isoDep))
                connectReceiver = null
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (connectReceiver != null
            && supportedConnectionMethods and CONNECTION_METHOD_NFC != CONNECTION_VOID)
            NfcAdapter.getDefaultAdapter(
            this.activity
        ).disableForegroundDispatch(this.activity)
        isActivityResumed = false
    }

    override fun onActivityStopped(activity: Activity) {
        try {
            if (connectReceiver != null || unplugReceiver != null) this.activity.unregisterReceiver(
                this
            )
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Error when unregister receiver", e)
        }
    }

    private fun requestPermission(device: UsbDevice?) {
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!UsbYubiKey.Type.isDeviceKnown(device)) return
        if (usbManager.hasPermission(device)) {
            activity.unregisterReceiver(this)
            if (supportedConnectionMethods and CONNECTION_METHOD_NFC != CONNECTION_VOID
                && isActivityResumed)
                NfcAdapter.getDefaultAdapter(activity).disableForegroundDispatch(activity)
            connectReceiver!!.onYubiKeyConnected(UsbYubiKey(device, usbManager.openDevice(device)))
            connectReceiver = null
        } else {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            }
            usbManager.requestPermission(
                device,
                PendingIntent.getBroadcast(
                    activity,
                    0,
                    Intent(ACTION_USB_PERMISSION_REQUEST),
                    flags
                )
            )
        }
    }

    /**
     * Gets the connection methods (USB and/or NFC) that are supported on the Android device.
     *
     * @return A byte that may or may not have the [.CONNECTION_METHOD_USB] and
     * [.CONNECTION_METHOD_USB] bits set.
     */
    val supportedConnectionMethods: Byte
        get() {
            val packageManager = activity.packageManager
            var result = CONNECTION_VOID
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) result =
                result or CONNECTION_METHOD_USB
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC) && NfcAdapter.getDefaultAdapter(
                    activity
                ).isEnabled
            ) result = result or CONNECTION_METHOD_NFC
            return result
        }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        this.activity.application.unregisterActivityLifecycleCallbacks(this)
    }

    companion object {
        private const val ACTION_USB_PERMISSION_REQUEST =
            "android.yubikey.intent.action.USB_PERMISSION_REQUEST"
        const val CONNECTION_VOID: Byte = 0

        /**
         * Flag used to indicate that support for USB host mode is present on the Android device.
         */
        const val CONNECTION_METHOD_USB: Byte = 1

        /**
         * Flag used to indicate that support for NFC is present on the Android device.
         */
        const val CONNECTION_METHOD_NFC: Byte = 2
    }
}