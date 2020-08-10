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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;

import java.io.UnsupportedEncodingException;

import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSConstants.MqttConnectionState;

public abstract class CustomizedThing {
    private static final String TAG = "CustomizedThing";
    protected String mThingId;
    protected String mBrokerEndpoint;
    protected AWSIotMqttManager mIotMqttManager;
    private MqttConnectionState mMqttConnectionState = MqttConnectionState.MQTT_Disconnected;
    private ThingConnectionState mThingConnectionState = ThingConnectionState.Thing_Disconnected;
    private AWSCredentialsProvider mAWSCredential;
    private OnConnectionStateListener mOnConnectionStateListener;

    public enum ThingConnectionState {
        Thing_Disconnected,
        Thing_Connecting,
        Thing_Connected
    }

    /**
     * Instantiates a new Customized thing.
     *
     * @param thingId        Unique client ID.
     * @param brokerEndpoint Broker endpoint.
     * @param awsCredentials AWS credentials.
     */
    public CustomizedThing(String thingId,
                           String brokerEndpoint,
                           AWSCredentialsProvider awsCredentials) {
        this.mThingId = thingId;
        this.mBrokerEndpoint = brokerEndpoint;
        this.mAWSCredential = awsCredentials;
    }

    public String getThingId() {
        return mThingId;
    }

    public MqttConnectionState getMqttConnectionState() {
        return mMqttConnectionState;
    }

    public ThingConnectionState getThingConnectionState() {
        return mThingConnectionState;
    }

    public void setOnConnectionStateListener(@Nullable OnConnectionStateListener listener) {
        mOnConnectionStateListener = listener;
    }

    /**
     * Connect to AWS IoT core.
     */
    public void connectToIoT() {
        if (mMqttConnectionState == MqttConnectionState.MQTT_Connected) {
            Log.w(TAG, "Already connected to IOT.");
            return;
        }
        if (mMqttConnectionState != MqttConnectionState.MQTT_Disconnected) {
            Log.w(TAG, "Previous connection is active, please retry or disconnect MQTT first.");
            return;
        }
        mIotMqttManager = new AWSIotMqttManager(mThingId, mBrokerEndpoint);

        AWSIotMqttClientStatusCallback mqttClientStatusCallback = new AWSIotMqttClientStatusCallback() {
            @Override
            public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                Log.i(TAG, "MQTT connection status changed to: " + String.valueOf(status));
                switch (status) {
                    case Connected:
                        setMqttConnectionState(MqttConnectionState.MQTT_Connected);
                        break;
                    case Connecting:
                    case Reconnecting:
                        setMqttConnectionState(MqttConnectionState.MQTT_Connecting);
                        break;
                    case ConnectionLost:
                        setMqttConnectionState(MqttConnectionState.MQTT_Disconnected);
                        break;
                    default:
                        Log.e(TAG, "Unknown MQTT connection state: " + status);
                        return;
                }
            }
        };
        mIotMqttManager.connect(mAWSCredential, mqttClientStatusCallback);

    }

    /**
     * Disconnect from AWS IoT core.
     */
    public void disconnectFromIoT() {
        if (mIotMqttManager != null) {
            try {
                mIotMqttManager.disconnect();
                mMqttConnectionState = MqttConnectionState.MQTT_Disconnected;
            } catch (Exception e) {
                Log.e(TAG, "MQTT disconnect error: ", e);
            }
        }
    }

    /**
     * Publish a message to a specific topic.
     *
     * @param topic   Topic to publish.
     * @param qoS     QoS.
     * @param payload Payload to publish.
     */
    public void publishToIoT(final String topic, final AWSIotMqttQos qoS, @NonNull final byte[] payload) {
        if (mMqttConnectionState != MqttConnectionState.MQTT_Connected) {
            Log.e(TAG, "Cannot publish message to IoT because MQTT connection state" +
                    " is not connected.");
            return;
        }
        AWSIotMqttMessageDeliveryCallback deliveryCallback = new AWSIotMqttMessageDeliveryCallback() {
            @Override
            public void statusChanged(MessageDeliveryStatus status, Object userData) {
                Log.d(TAG, "Publish msg delivery status: " + status.toString());
                if (status == MessageDeliveryStatus.Success && qoS == AWSIotMqttQos.QOS1) {
                    sendAckToThing(CustomizedMqttEnvelope.newPublishEnvelope(topic, qoS, payload));
                }
            }
        };
        try {
            Log.i(TAG, "Sending MQTT message to IoT on topic: " + topic
                    + " message: " + new String(payload));
            mIotMqttManager.publishData(payload, topic, qoS, deliveryCallback, null);
        } catch (Exception e) {
            Log.e(TAG, "Publish error.", e);
        }
    }

    /**
     * Subscribe to a specific topic.
     *
     * @param topic Topic to subscribe.
     * @param qoS   QoS.
     */
    public void subscribeToIoT(final String topic, final AWSIotMqttQos qoS) {
        if (mMqttConnectionState != MqttConnectionState.MQTT_Connected) {
            Log.e(TAG, "Cannot subscribe because MQTT state is not connected.");
            return;
        }

        try {
            Log.i(TAG, "Subscribing to IoT on topic : " + topic);
            mIotMqttManager.subscribeToTopic(topic, qoS, new AWSIotMqttNewMessageCallback() {
                @Override
                public void onMessageArrived(final String topic, final byte[] data) {
                    try {
                        Log.i(TAG, " Message arrived on topic: " + topic);
                        Log.v(TAG, "   Message: " + new String(data, "UTF-8"));
                        CustomizedMqttEnvelope envelope = CustomizedMqttEnvelope.newPublishEnvelope(
                                topic, qoS, data
                        );
                        publishToThing(envelope);
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Message encoding error.", e);
                    }
                }
            });
            sendAckToThing(CustomizedMqttEnvelope.newSubscribeEnvelope(topic, qoS));
        } catch (Exception e) {
            Log.e(TAG, "Subscription error.", e);
        }
    }

    /**
     * Unsubscribe from a specific topic.
     *
     * @param topic Topic to unsubscribe.
     */
    public void unsubscribeToIoT(final String topic) {
        if (mMqttConnectionState != MqttConnectionState.MQTT_Connected) {
            Log.e(TAG, "Cannot unsubscribe because MQTT state is not connected.");
            return;
        }

        try {
            Log.i(TAG, "UnSubscribing to IoT on topic : " + topic);
            mIotMqttManager.unsubscribeTopic(topic);
            sendAckToThing(CustomizedMqttEnvelope.newUnsubscribeEnvelope(topic));
        } catch (Exception e) {
            Log.e(TAG, "Subscription error.", e);
        }
    }

    public interface OnConnectionStateListener {
        /**
         * Called when MQTT connection state changed.
         *
         * @param thing Customized thing.
         * @param state New MQTT connection state.
         */
        void onMqttConnectionStateChanged(CustomizedThing thing, MqttConnectionState state);

        /**
         * Called when Thing connection state changed.
         *
         * @param thing Customized thing.
         * @param state New thing connection state.
         */
        void onThingConnectionStateChanged(CustomizedThing thing, ThingConnectionState state);
    }

    /**
     * Connect to local device.
     */
    public abstract void connectToThing();

    /**
     * Disconnect from local device.
     */
    public abstract void disconnectFromThing();

    protected void setMqttConnectionState(final MqttConnectionState state) {
        if (state == mMqttConnectionState) {
            return;
        }
        mMqttConnectionState = state;
        if (mOnConnectionStateListener != null) {
            mOnConnectionStateListener.onMqttConnectionStateChanged(this, mMqttConnectionState);
        }
    }

    protected void setThingConnectionState(final ThingConnectionState state) {
        if (state == mThingConnectionState) {
            return;
        }
        mThingConnectionState = state;
        if (mOnConnectionStateListener != null) {
            mOnConnectionStateListener.onThingConnectionStateChanged(this, mThingConnectionState);
        }
    }

    /**
     * Send publish/subscribe/unsubscribe ack back to thing.
     *
     * @param envelope Message passed when publish/subscribe/unsubscribe.
     */
    protected abstract void sendAckToThing(final CustomizedMqttEnvelope envelope);

    /**
     * Request to publish message received from cloud to thing.
     *
     * @param envelope Received message from cloud.
     */
    protected abstract void publishToThing(final CustomizedMqttEnvelope envelope);
}
