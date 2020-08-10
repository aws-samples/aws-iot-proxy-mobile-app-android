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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.amazonaws.auth.AWSCredentialsProvider;

import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSConstants;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;

public class MyDummyCustomizedThing extends CustomizedThing {
    private static final String TAG = "MyDummyCustomizedThing";
    private static final int TO_APP = 0;
    private static final int TO_DEVICE = 1;
    private Handler mThingHandler;
    private Thread mDummyDeviceThread;
    private Context mContext;

    /**
     * Instantiates a new Dummy Customized thing.
     *
     * @param thingId        Unique client ID.
     * @param brokerEndpoint Broker endpoint.
     * @param awsCredentials AWS credentials.
     * @param context        Activity context.
     */
    public MyDummyCustomizedThing(final String thingId, String brokerEndpoint, AWSCredentialsProvider awsCredentials, Context context) {
        super(thingId, brokerEndpoint, awsCredentials);
        this.mContext = context;
        if (mDummyDeviceThread == null) {
            mThingHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(@NonNull Message msg) {
                    byte[] encodedBytes = (byte[]) msg.obj;
                    MyTLV tlvMsg = new MyTLV(encodedBytes);
                    if (msg.what == TO_APP && getMqttConnectionState() == AmazonFreeRTOSConstants.MqttConnectionState.MQTT_Connected) {
                        CustomizedMqttEnvelope envelope = tlvMsg.toCustomizedMqttEnvelope();
                        /**
                         * Dummy device to app
                         */
                        if (envelope == null) {
                            return false;
                        }
                        switch (envelope.envelopeType) {
                            case Publish:
                                publishToIoT(envelope.topic, envelope.qoS, envelope.payload);
                                break;
                            default:
                                break;
                        }
                    } else if (msg.what == TO_DEVICE) {
                        /**
                         * App tp Dummy device
                         */
                        Log.i(TAG, "Dummy device received" + new String((byte[]) msg.obj));
                    }
                    return false;
                }
            });
        }
    }

    public void runDevice() {
        if (mDummyDeviceThread != null) {
            return;
        }
        mDummyDeviceThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (getThingConnectionState() == ThingConnectionState.Thing_Connected) {
                    if (getMqttConnectionState() != AmazonFreeRTOSConstants.MqttConnectionState.MQTT_Connected) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                    Message msg = Message.obtain();
                    msg.what = TO_APP;
                    msg.obj = (Object) (new MyTLV(MyTLV.TLVType.PUB,
                            "[proxy/test]0{name:dummy;temp:25.56;bat:98%}".getBytes())).encodedBytesStream;
                    mThingHandler.sendMessage(msg);
                    /**
                     * This dummy device will request publish to cloud every 5000ms
                     */
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        mDummyDeviceThread.start();
    }

    @Override
    public void connectToThing() {
        setThingConnectionState(ThingConnectionState.Thing_Connected);
        runDevice();
        Log.i(TAG, "Connected to dummy device");
    }

    @Override
    public void disconnectFromThing() {
        setThingConnectionState(ThingConnectionState.Thing_Disconnected);
        Log.i(TAG, "Disconnected from dummy device");
    }

    @Override
    protected void sendAckToThing(final CustomizedMqttEnvelope message) {
        /**
         * Here we implement the adapter which transfer ACK message into dummy local
         * protocol(Customized TLV)
         */
        MyTLV tlv;
        switch (message.envelopeType) {
            case Publish:
                tlv = new MyTLV(MyTLV.TLVType.PUBACK, message.payload);
                break;
            case Subscribe:
                tlv = new MyTLV(MyTLV.TLVType.SUBACK, message.topic.getBytes());
                break;
            case Unsubscribe:
                tlv = new MyTLV(MyTLV.TLVType.UNSUBACK, message.topic.getBytes());
                break;
            default:
                Log.e(TAG, "Unexpected message type");
                return;
        }
        sendDataToThing(tlv.encodedBytesStream);
    }

    @Override
    protected void publishToThing(final CustomizedMqttEnvelope message) {
        /**
         * Here we implement the adapter which transfer CustomizedMqttEnvelope into
         * dummy local protocol(Customized TLV)
         */
        if (message.envelopeType != CustomizedMqttEnvelope.CustomizedMqttEnvelopeType.Publish) {
            Log.e(TAG, "Unexpected message type");
            return;
        }
        MyTLV tlv = new MyTLV(message);
        sendDataToThing(tlv.encodedBytesStream);
        new Thread(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext,
                                "Receive Topic:" + message.topic + "\n" + new String(message.payload),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void sendDataToThing(final byte[] data) {
        /**
         * Here we implement the logic to send encoded bytes stream back to thing.
         */
        Message msg = Message.obtain();
        msg.what = TO_DEVICE;
        msg.obj = (Object) data;
        mThingHandler.sendMessage(msg);
    }
}
