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

package com.amazonaws.awsiotproxy.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amazonaws.awsiotproxy.CustomizedThing;
import com.amazonaws.awsiotproxy.R;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.google.android.material.textfield.TextInputLayout;

import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSConstants;

public class SubscribeFragment extends Fragment implements ThingChangeListener {

    private Button mButtonSubscribe;
    private Button mButtonUnsubscribe;
    private CustomizedThing mThing;
    private TextInputLayout mTopicInputLayout;
    private RadioGroup mQosRadioGroup;
    private View.OnClickListener mClickListener;

    public static SubscribeFragment newInstance() {
        return new SubscribeFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.subscribe_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mTopicInputLayout = getView().findViewById(R.id.subTopic);
        mQosRadioGroup = getView().findViewById(R.id.subQoSRadioGroup);

        mClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mThing != null && mThing.getMqttConnectionState() == AmazonFreeRTOSConstants.MqttConnectionState.MQTT_Connected) {
                    RadioButton selectedButton = getView().findViewById(mQosRadioGroup.getCheckedRadioButtonId());
                    int selectedIndex = mQosRadioGroup.indexOfChild(selectedButton);
                    AWSIotMqttQos qos = selectedIndex == 0 ? AWSIotMqttQos.QOS0 : AWSIotMqttQos.QOS1;

                    String topic = mTopicInputLayout.getEditText().getText().toString();

                    if (((Button) v).getId() == R.id.buttonSubscribe) {
                        mThing.subscribeToIoT(topic, qos);
                        Toast.makeText(getActivity(), "Subscribed", Toast.LENGTH_SHORT).show();
                    } else if (((Button) v).getId() == R.id.buttonUnsubscribe) {
                        mThing.unsubscribeToIoT(topic);
                        Toast.makeText(getActivity(), "Unsubscribed", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getActivity(), "Not Connected", Toast.LENGTH_SHORT).show();
                }
            }
        };

        mButtonSubscribe = getView().findViewById(R.id.buttonSubscribe);
        mButtonSubscribe.setOnClickListener(mClickListener);

        mButtonUnsubscribe = getView().findViewById(R.id.buttonUnsubscribe);
        mButtonUnsubscribe.setOnClickListener(mClickListener);

        ((DevicesFragment) getFragmentManager().getFragments().get(0)).addThingChangeListener(this);
    }

    @Override
    public void onDevicesChanged(CustomizedThing thing) {
        mThing = thing;
    }
}

