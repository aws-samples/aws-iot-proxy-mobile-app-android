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

import androidx.annotation.NonNull;

import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;

public class CustomizedMqttEnvelope {
    public String topic;
    public AWSIotMqttQos qoS;

    public CustomizedMqttEnvelopeType envelopeType;
    public byte[] payload;

    public enum CustomizedMqttEnvelopeType {
        Publish,
        Subscribe,
        Unsubscribe,
    }

    /**
     * Instantiates a new CustomizedMqttEnvelope.
     *
     * @param topic        Topic to publish/subscribe/unsubscribe.
     * @param qoS          MQTT QoS.
     * @param envelopeType Request type.
     * @param payload      Payload bytes stream.
     */
    private CustomizedMqttEnvelope(String topic, AWSIotMqttQos qoS, CustomizedMqttEnvelopeType envelopeType, byte[] payload) {
        this.topic = topic;
        this.qoS = qoS;
        this.envelopeType = envelopeType;
        this.payload = payload;
    }

    public static CustomizedMqttEnvelope newPublishEnvelope(String topic, AWSIotMqttQos qoS, @NonNull final byte[] payload) {
        return new CustomizedMqttEnvelope(topic, qoS, CustomizedMqttEnvelopeType.Publish, payload);
    }

    public static CustomizedMqttEnvelope    newSubscribeEnvelope(String topic, AWSIotMqttQos qoS) {
        return new CustomizedMqttEnvelope(topic, qoS, CustomizedMqttEnvelopeType.Subscribe, null);
    }

    public static CustomizedMqttEnvelope newUnsubscribeEnvelope(String topic) {
        return new CustomizedMqttEnvelope(topic, AWSIotMqttQos.QOS0, CustomizedMqttEnvelopeType.Unsubscribe, null);
    }
}
