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

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * This is an example to implement private local protocol.
 */
public class MyTLV {
    private static final String TAG = "MyTLV";
    private static final int HEAD_SIZE = 2;
    private TLVType mType;
    private int mLength;
    private byte[] mValue;
    public byte[] encodedBytesStream;

    public enum TLVType {
        INVALID,
        PUBACK,
        SUBACK,
        UNSUBACK,
        PUB,
        SUB,
        UNSUB,
    }

    public MyTLV(TLVType type, byte[] value) {
        this.mType = type;
        this.mValue = value;
        this.mLength = HEAD_SIZE + value.length;
        this.encode();
    }

    /**
     * Base on encoded bytes stream from device generates the TLV package to app.
     *
     * @param encodedBytesStream Encoded bytes stream.
     */
    public MyTLV(@NonNull byte[] encodedBytesStream) {
        this.encodedBytesStream = encodedBytesStream;
        this.decode();
    }

    /**
     * Base on the received publish message generates the TLV package send to device.
     *
     * @param envelope Mqtt message.
     */
    public MyTLV(CustomizedMqttEnvelope envelope) {
        if (envelope.envelopeType != CustomizedMqttEnvelope.CustomizedMqttEnvelopeType.Publish) {
            mType = TLVType.INVALID;
            return;
        }
        String raw;
        raw = String.format(Locale.US, "[%s]%d{%s}", envelope.topic, envelope.qoS.ordinal(), new String(envelope.payload));

        mType = TLVType.PUB;
        mValue = raw.getBytes();
        mLength = HEAD_SIZE + mValue.length;
        this.encode();
    }

    public CustomizedMqttEnvelope toCustomizedMqttEnvelope() {

        if (mType != TLVType.PUB && mType != TLVType.SUB && mType != TLVType.UNSUB) {
            return null;
        }

        String raw = new String(mValue);
        String topic = raw.substring(raw.indexOf("[") + 1, raw.indexOf("]"));
        JsonObject jsonObject = new JsonObject();
        CustomizedMqttEnvelope.CustomizedMqttEnvelopeType msgType;
        AWSIotMqttQos qos;

        try {
            int qosInt = Integer.parseInt(raw.substring(raw.indexOf("]") + 1, raw.indexOf("]") + 2));
            /* Here we made json string base on raw payload */
            String[] pairs = (raw.substring(raw.indexOf("{") + 1, raw.indexOf("}"))).split(";");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                jsonObject.addProperty(keyValue[0], keyValue[1]);
            }
            qos = AWSIotMqttQos.values()[qosInt];
        } catch (Exception e) {
            qos = AWSIotMqttQos.QOS0;
        }

        switch (mType) {
            case PUB:
                return CustomizedMqttEnvelope.newPublishEnvelope(topic, qos, jsonObject.toString().getBytes());
            case SUB:
                return CustomizedMqttEnvelope.newSubscribeEnvelope(topic, qos);
            case UNSUB:
                return CustomizedMqttEnvelope.newUnsubscribeEnvelope(topic);
            default:
                return null;
        }
    }

    private void encode() {
        try {
            encodedBytesStream = new byte[mLength];
            encodedBytesStream[0] = (byte) (mType.ordinal());
            encodedBytesStream[1] = (byte) (mLength);
            System.arraycopy(mValue, 0, encodedBytesStream, HEAD_SIZE, mValue.length);
        } catch (Exception e) {
            Log.e(TAG, "encode failed");
            mType = TLVType.INVALID;
        }
    }

    private void decode() {
        try {
            mType = TLVType.values()[encodedBytesStream[0]];
            mLength = encodedBytesStream.length;
            mValue = new byte[mLength - HEAD_SIZE];
            System.arraycopy(encodedBytesStream, HEAD_SIZE, mValue, 0, mValue.length);
        } catch (Exception e) {
            Log.e(TAG, "decode failed");
            mType = TLVType.INVALID;
        }
    }
}
