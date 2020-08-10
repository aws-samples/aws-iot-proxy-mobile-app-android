/*
 *
 *  * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  * SPDX-License-Identifier: MIT-0
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 *  * software and associated documentation files (the "Software"), to deal in the Software
 *  * without restriction, including without limitation the rights to use, copy, modify,
 *  * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 *  * permit persons to whom the Software is furnished to do so.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 *  * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 *  * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 *  * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.amazonaws.awsiotproxy;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.auth.AWSCredentialsProvider;

import java.util.List;
import java.util.UUID;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class ESP32CustomizedThing extends CustomizedThing {
    private static final String TAG = "ESP32CustomizedThing";
    public static final String SERVICE_UUID = "000000ee-0000-1000-8000-00805f9b34fb";
    private static final String CHAR_UUID = "0000ee01-0000-1000-8000-00805f9b34fb";
    private static final int ATT_MTU = 64;
    private static final int READ_PERIOD_MS = 5000;
    private static final UUID mServiceUuid = UUID.fromString(SERVICE_UUID);
    private static final UUID mCharUuid = UUID.fromString(CHAR_UUID);
    private Context mContext;
    private BluetoothDevice mDevice;
    private BluetoothGatt mDeviceGatt;

    /**
     * Instantiates a new Customized thing.
     *
     * @param thingId         Unique client ID.
     * @param brokerEndpoint  Broker endpoint.
     * @param awsCredentials  AWS credentials.
     * @param context         Context.
     * @param bluetoothDevice Bluetooth device.
     */
    public ESP32CustomizedThing(String thingId, String brokerEndpoint, AWSCredentialsProvider awsCredentials, Context context, BluetoothDevice bluetoothDevice) {
        super(thingId, brokerEndpoint, awsCredentials);
        mContext = context;
        mDevice = bluetoothDevice;
    }

    @Override
    public void connectToThing() {
        mDeviceGatt = mDevice.connectGatt(mContext, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        setThingConnectionState(ThingConnectionState.Thing_Connected);
                        gatt.discoverServices();
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        setThingConnectionState(ThingConnectionState.Thing_Connecting);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                    default:
                        gatt.close();
                        setThingConnectionState(ThingConnectionState.Thing_Disconnected);
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (gatt.getService(mServiceUuid) != null) {
                    gatt.requestMtu(ATT_MTU);
                    new BleReadTask(ESP32CustomizedThing.this).execute();
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    MyTLV tlv = new MyTLV(characteristic.getValue());
                    final CustomizedMqttEnvelope envelope = tlv.toCustomizedMqttEnvelope();
                    if (envelope.envelopeType == CustomizedMqttEnvelope.CustomizedMqttEnvelopeType.Publish) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(mContext, "Received from ESP32: " + new String(envelope.payload), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }).start();
                        publishToIoT(envelope.topic, envelope.qoS, envelope.payload);
                    }
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Sent back to thing: " + new String(characteristic.getValue()));
                }
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                Log.d(TAG, "MTU changed to " + mtu);
            }
        });
    }

    @Override
    public void disconnectFromThing() {
        mDeviceGatt.disconnect();
    }

    @Override
    protected void sendAckToThing(CustomizedMqttEnvelope envelope) {
        MyTLV tlv;

        switch (envelope.envelopeType) {
            case Publish:
                tlv = new MyTLV(MyTLV.TLVType.PUBACK, envelope.payload);
                break;
            case Subscribe:
                tlv = new MyTLV(MyTLV.TLVType.SUBACK, envelope.topic.getBytes());
                break;
            case Unsubscribe:
                tlv = new MyTLV(MyTLV.TLVType.UNSUBACK, envelope.topic.getBytes());
                break;
            default:
                return;
        }

        BluetoothGattCharacteristic characteristic = ESP32CustomizedThing.this.mDeviceGatt.getService(mServiceUuid).getCharacteristic(mCharUuid);

        characteristic.setValue(tlv.encodedBytesStream);
        mDeviceGatt.writeCharacteristic(characteristic);
    }

    @Override
    protected void publishToThing(CustomizedMqttEnvelope envelope) {
        if (envelope.envelopeType != CustomizedMqttEnvelope.CustomizedMqttEnvelopeType.Publish) {
            Log.e(TAG, "Unexpected message type");
            return;
        }

        MyTLV tlv = new MyTLV(envelope);
        BluetoothGattCharacteristic characteristic = ESP32CustomizedThing.this.mDeviceGatt.getService(mServiceUuid).getCharacteristic(mCharUuid);

        characteristic.setValue(tlv.encodedBytesStream);
        mDeviceGatt.writeCharacteristic(characteristic);
    }

    private static class BleReadTask extends AsyncTask<String, String, String> {

        private ESP32CustomizedThing mThing;

        public BleReadTask(ESP32CustomizedThing thing) {
            super();
            mThing = thing;
        }

        @Override
        protected String doInBackground(String... strings) {
            while (mThing.getThingConnectionState() == ThingConnectionState.Thing_Connected) {
                try {
                    Thread.sleep(READ_PERIOD_MS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<BluetoothGattCharacteristic> characteristics = mThing.mDeviceGatt.getService(mServiceUuid).getCharacteristics();
                if (characteristics.size() == 0) {
                    Log.e("TAG", "Can't find characteristic");
                    continue;
                }
                if (!mThing.mDeviceGatt.readCharacteristic(characteristics.get(0))) {
                    Log.e("TAG", "Read characteristic error");
                }
            }
            return null;
        }
    }
}
