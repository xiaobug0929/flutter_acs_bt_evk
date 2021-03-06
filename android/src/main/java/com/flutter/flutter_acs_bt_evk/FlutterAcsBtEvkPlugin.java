package com.flutter.flutter_acs_bt_evk;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.acs.bluetooth.Acr1255uj1Reader;
import com.acs.bluetooth.BluetoothReader;
import com.acs.bluetooth.BluetoothReaderGattCallback;
import com.acs.bluetooth.BluetoothReaderManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * FlutterAcsBtEvkPlugin
 */
public class FlutterAcsBtEvkPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private MethodChannel channel;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_acs_bt_evk");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding activityPluginBinding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
        activity = activityPluginBinding.getActivity();
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        Log.e(TAG, "??????Native??????: " + call.method + "," + call.arguments);

        switch (call.method) {
            case "init":
                init();
                result.success(true);
                break;
            case "scanDevice":
                boolean enable = call.arguments();
                if (enable) {
                    result.success(scanDevice());
                } else {
                    stopScanDevice();
                    result.success(true);
                }
                break;
            case "connectDevice":
                String deviceAddress = call.arguments();
                if (deviceAddress != null && !deviceAddress.isEmpty()) {
                    connectDevice(deviceAddress);
                }
                result.success(true);
                break;
            case "disconnectDevice":
                result.success(disconnectDevice());
                break;
            case "authenticate":
                result.success(authenticate());
                break;
            case "startPolling":
                result.success(startPolling());
                break;
            case "stopPolling":
                result.success(stopPolling());
                break;
            case "powerOnCard":
                result.success(powerOnCard());
                break;
            case "powerOffCard":
                result.success(powerOffCard());
                break;
            case "transmitApdu":
                String apduCommand = call.arguments();
                if (apduCommand != null && !apduCommand.isEmpty()) {
                    result.success(transmitApdu(apduCommand));
                }
                break;
            case "transmitEscapeCommand":
                String escapeCommand = call.arguments();
                if (escapeCommand != null && !escapeCommand.isEmpty()) {
                    result.success(transmitEscapeCommand(escapeCommand));
                }
                break;
            case "getBatteryLevel":
                result.success(getBatteryLevel());
                break;
            case "clear":
                result.success(clear());
                break;
            case "openBluetooth":
                result.success(openBluetooth());
                break;
            case "isBluetoothEnabled":
                result.success(isBluetoothEnabled());
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private static final String TAG = "FlutterAcsBtEvkPlugin";
    private static final byte[] AUTO_POLLING_START = {(byte) 0xE0, 0x00, 0x00, 0x40, 0x01};
    private static final byte[] AUTO_POLLING_STOP = {(byte) 0xE0, 0x00, 0x00, 0x40, 0x00};
    private Activity activity;
    private final List<BluetoothDevice> mLeDevices = new ArrayList<>();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothReader mBluetoothReader;
    private BluetoothGatt mBluetoothGatt;
    /**
     * ???????????????
     */
    final BluetoothReaderManager mBluetoothReaderManager = new BluetoothReaderManager();
    /**
     * ?????????????????????
     */
    final BluetoothReaderGattCallback mGattCallback = new BluetoothReaderGattCallback();
    /**
     * ??????????????????,????????????????????????
     */
    BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (device != null && device.getName() != null && device.getName().startsWith("ACR")) {
                if (!mLeDevices.contains(device)) {
                    Log.e(TAG, "???????????????: " + device.getName());
                    mLeDevices.add(device);
                    notifyLeScan();
                }
            }
        }
    };

    /**
     * ??????????????????????????????
     */
    void initBluetoothReaderListener() {
        // Acr1255uj1Reader
        if (mBluetoothReader != null && mBluetoothReader instanceof Acr1255uj1Reader) {
            // ??????????????????
            ((Acr1255uj1Reader) mBluetoothReader)
                    .setOnBatteryLevelAvailableListener(new Acr1255uj1Reader.OnBatteryLevelAvailableListener() {
                        @Override
                        public void onBatteryLevelAvailable(BluetoothReader bluetoothReader, final int batteryLevel,
                                                            int status) {
                            Log.e(TAG, "onBatteryLevelAvailable: ????????????" + batteryLevel);
                            Map<String, Integer> ret = new HashMap<>();
                            ret.put("result", batteryLevel);
                            postMessage("onBatteryLevelAvailable", ret);
                        }
                    });
            // ???????????????
            mBluetoothReader.setOnCardStatusAvailableListener(new BluetoothReader.OnCardStatusAvailableListener() {
                @Override
                public void onCardStatusAvailable(BluetoothReader bluetoothReader, final int cardStatus,
                                                  final int errorCode) {
                    Log.e(TAG, "onCardStatusAvailable: ??????????????????");
                }
            });
            // ????????????????????????
            mBluetoothReader.setOnDeviceInfoAvailableListener(new BluetoothReader.OnDeviceInfoAvailableListener() {
                @Override
                public void onDeviceInfoAvailable(BluetoothReader bluetoothReader, final int infoId, final Object o,
                                                  final int status) {
                    Log.e(TAG, "onDeviceInfoAvailable: ??????????????????");
                }
            });
            // ????????????????????????
            ((Acr1255uj1Reader) mBluetoothReader)
                    .setOnBatteryLevelChangeListener(new Acr1255uj1Reader.OnBatteryLevelChangeListener() {
                        @Override
                        public void onBatteryLevelChange(BluetoothReader bluetoothReader, int batteryLevel) {
                            Log.e(TAG, "onBatteryLevelChange: ??????????????????" + batteryLevel);
                        }
                    });
            // ??????????????????????????????
            mBluetoothReader.setOnCardPowerOffCompleteListener(new BluetoothReader.OnCardPowerOffCompleteListener() {
                @Override
                public void onCardPowerOffComplete(BluetoothReader bluetoothReader, final int result) {
                    Log.e(TAG, "onCardPowerOffComplete: ??????????????????");
                }
            });

            // atr????????????
            mBluetoothReader.setOnAtrAvailableListener(new BluetoothReader.OnAtrAvailableListener() {
                @Override
                public void onAtrAvailable(BluetoothReader bluetoothReader, final byte[] atr, final int errorCode) {
                    Log.e(TAG, "onAtrAvailable: atr??????:" + Utils.toHexString(atr));
                    Map<String, String> ret = new HashMap<>();
                    ret.put("result", Utils.toHexString(atr));
                    postMessage("onAtrAvailable", ret);
                }
            });

            // APDU????????????
            mBluetoothReader.setOnResponseApduAvailableListener(new BluetoothReader.OnResponseApduAvailableListener() {
                @Override
                public void onResponseApduAvailable(BluetoothReader bluetoothReader, final byte[] response,
                                                    final int errorCode) {
                    Log.e(TAG, "onResponseApduAvailable: APDU??????:"
                            + Utils.toHexString(response).replace(" ", "").substring(0, 14));
                    Map<String, String> ret = new HashMap<>();
                    ret.put("result", Utils.toHexString(response));
                    postMessage("onResponseApduAvailable", ret);
                }
            });
            // escape????????????
            mBluetoothReader
                    .setOnEscapeResponseAvailableListener(new BluetoothReader.OnEscapeResponseAvailableListener() {
                        @Override
                        public void onEscapeResponseAvailable(BluetoothReader bluetoothReader, final byte[] response,
                                                              final int errorCode) {
                            Log.e(TAG, "onEscapeResponseAvailable: escape??????:" + Utils.toHexString(response));
                            Map<String, String> ret = new HashMap<>();
                            ret.put("result", Utils.toHexString(response));
                            postMessage("onEscapeResponseAvailable", ret);

                        }
                    });

            // ???????????????????????????
            mBluetoothReader.setOnEnableNotificationCompleteListener(
                    new BluetoothReader.OnEnableNotificationCompleteListener() {
                        @Override
                        public void onEnableNotificationComplete(BluetoothReader bluetoothReader, final int result) {
                            Log.e(TAG, "onEnableNotificationComplete: ??????????????????");
                            postMessage("onEnableNotificationComplete", null);
                        }
                    });
            // ????????????????????????
            mBluetoothReader
                    .setOnAuthenticationCompleteListener(new BluetoothReader.OnAuthenticationCompleteListener() {
                        @Override
                        public void onAuthenticationComplete(BluetoothReader bluetoothReader, final int errorCode) {
                            Log.e(TAG, "onAuthenticationComplete: ????????????");
                            postMessage("onAuthenticationComplete", null);
                        }
                    });
            // ?????????????????????,???????????????
            mBluetoothReader.setOnCardStatusChangeListener(new BluetoothReader.OnCardStatusChangeListener() {
                @Override
                public void onCardStatusChange(BluetoothReader bluetoothReader, int cardStatus) {
                    Log.e(TAG, "onCardStatusChange: ??????????????????" + cardStatus);
                    Map<String, Integer> ret = new HashMap<>();
                    ret.put("result", cardStatus);
                    postMessage("onCardStatusChange", ret);
                }
            });
        }
    }

    // ????????????
    void postMessage(final String method, final Map msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                channel.invokeMethod(method, msg);
            }
        });
    }

    // ??????flutter???
    void notifyLeScan() {
        if (mLeDevices.isEmpty()) {
            return;
        }
        Map<String, List<Map<String, String>>> ret = new HashMap<>();
        List<Map<String, String>> devices = new ArrayList<>();
        for (BluetoothDevice device : mLeDevices) {
            Map<String, String> map = new HashMap<>();
            map.put("name", device.getName());
            map.put("address", device.getAddress());
            devices.add(map);
        }
        ret.put("result", devices);
        channel.invokeMethod("onLeScan", ret);
    }


    /**
     * ????????????????????????
     */
    void init() {
        if (mBluetoothAdapter == null) {
            // ???????????????
            BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    /**
     * ??????????????????
     *
     * @return
     */
    boolean isBluetoothEnabled() {
        if (mBluetoothAdapter == null) {
            return false;
        }
        return mBluetoothAdapter.isEnabled();
    }


    /**
     * ??????????????????
     *
     * @return
     */
    boolean openBluetooth() {
        //??????????????????
        if (!isBluetoothEnabled()) {
            //????????????
            Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enabler, 1);
        }
        return true;
    }

    /**
     * ????????????????????????
     */
    boolean scanDevice() {
        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
        return false;
    }

    /**
     * ????????????????????????
     */
    void stopScanDevice() {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    /**
     * ????????????
     */
    void connectDevice(String deviceAddress) {
        mBluetoothReaderManager.setOnReaderDetectionListener(new BluetoothReaderManager.OnReaderDetectionListener() {
            @Override
            public void onReaderDetection(BluetoothReader reader) {
                if (reader instanceof Acr1255uj1Reader) {
                    mBluetoothReader = reader;
                    // ????????????????????????????????????
                    initBluetoothReaderListener();
                    // ????????????
                    mBluetoothReader.enableNotification(true);
                }
            }
        });

        mGattCallback
                .setOnConnectionStateChangeListener(new BluetoothReaderGattCallback.OnConnectionStateChangeListener() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int state, int newState) {
                        Log.e(TAG, "onConnectionStateChange: " + newState);
                        if (newState == BluetoothProfile.STATE_CONNECTED) {// ????????????
                            mBluetoothReaderManager.detectReader(gatt, mGattCallback);
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {// ??????????????????,??????
                            disconnectDevice();
                        }
                    }
                });
        // ??????????????????
        disconnectDevice();
        // ??????????????????
        if (mBluetoothAdapter != null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
            mBluetoothGatt = device.connectGatt(activity, false, mGattCallback);
        }
    }

    /**
     * ????????????
     */
    boolean disconnectDevice() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        return true;
    }

    /**
     * ????????????,????????????????????????
     */
    boolean authenticate() {
        byte[] masterKey = Utils.getTextHexBytes("41 43 52 31 32 35 35 55 2D 4A 31 20 41 75 74 68");
        if (mBluetoothReader != null) {
            return mBluetoothReader.authenticate(masterKey);
        }
        return false;
    }

    /**
     * ???????????????
     *
     * @return
     */
    boolean startPolling() {
        if (mBluetoothReader != null) {
            return mBluetoothReader.transmitEscapeCommand(AUTO_POLLING_START);
        }
        return false;
    }

    /**
     * ???????????????
     *
     * @return
     */
    boolean stopPolling() {
        if (mBluetoothReader != null) {
            return mBluetoothReader.transmitEscapeCommand(AUTO_POLLING_STOP);
        }
        return false;
    }

    /**
     * ????????????
     *
     * @return
     */
    boolean powerOnCard() {
        if (mBluetoothReader != null) {
            return mBluetoothReader.powerOnCard();
        }
        return false;
    }

    /**
     * ????????????
     *
     * @return
     */
    boolean powerOffCard() {
        if (mBluetoothReader != null) {
            return mBluetoothReader.powerOffCard();
        }
        return false;
    }

    /**
     * ??????APDU?????? ????????????
     *
     * @return
     */
    boolean transmitApdu(String apduCommand) {
        if (mBluetoothReader != null) {
            byte[] hexBytes = Utils.getTextHexBytes(apduCommand);
            if (hexBytes != null) {
                return mBluetoothReader.transmitApdu(hexBytes);
            }
        }
        return false;
    }

    /**
     * ??????escape?????? ??????????????????
     *
     * @return
     */
    boolean transmitEscapeCommand(String escapeCommand) {
        if (mBluetoothReader != null) {
            byte[] hexBytes = Utils.getTextHexBytes(escapeCommand);
            if (hexBytes != null) {
                return mBluetoothReader.transmitEscapeCommand(hexBytes);
            }
        }
        return false;
    }

    /**
     * ??????????????????
     *
     * @return
     */
    boolean getBatteryLevel() {
        if (mBluetoothReader != null) {
            if (mBluetoothReader instanceof Acr1255uj1Reader) {
                return ((Acr1255uj1Reader) mBluetoothReader).getBatteryLevel();
            }
        }
        return false;
    }

    /**
     * ??????,????????????
     */
    boolean clear() {
        stopPolling();
        stopScanDevice();
        disconnectDevice();
        mBluetoothReader = null;
        mLeDevices.clear();
        return true;
    }
}
