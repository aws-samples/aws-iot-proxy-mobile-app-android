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

import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.awsiotproxy.ui.main.MainFragment;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.SignInUIOptions;
import com.amazonaws.mobile.client.UserStateDetails;
import com.amazonaws.regions.Region;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPolicyRequest;

import java.util.concurrent.CountDownLatch;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private HandlerThread mAuthHandlerThread;
    private Handler mAuthHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, MainFragment.newInstance())
                    .commitNow();
        }
        if (mAuthHandlerThread == null) {
            mAuthHandlerThread = new HandlerThread("SignInThread");
            mAuthHandlerThread.start();
            mAuthHandler = new Handler(mAuthHandlerThread.getLooper());
        }
        final CountDownLatch latch = new CountDownLatch(1);
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {

                    @Override
                    public void onResult(UserStateDetails userStateDetails) {
                        Log.i(TAG, "AWSMobileClient initialization onResult: " + userStateDetails.getUserState());
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Initialization error.", e);
                    }
                }
        );
        Log.d(TAG, "waiting for AWSMobileClient Initialization");
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mAuthHandler.post(new Runnable() {
            @Override
            public void run() {
                if (AWSMobileClient.getInstance().isSignedIn()) {
                    attachPolicy();
                } else {
                    AWSMobileClient.getInstance().showSignIn(
                            MainActivity.this,
                            SignInUIOptions.builder()
                                    .nextActivity(null)
                                    .build(),
                            new Callback<UserStateDetails>() {
                                @Override
                                public void onResult(UserStateDetails result) {
                                    Log.d(TAG, "onResult: " + result.getUserState());
                                    switch (result.getUserState()) {
                                        case SIGNED_IN:
                                            Log.i(TAG, "logged in!");
                                            attachPolicy();
                                            break;
                                        case SIGNED_OUT:
                                            Log.i(TAG, "onResult: User did not choose to sign-in");
                                            break;
                                        default:
                                            AWSMobileClient.getInstance().signOut();
                                            break;
                                    }
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.e(TAG, "onError: ", e);
                                }
                            }
                    );
                }
            }
        });
        requestPermissions(new String[]{Manifest.
                permission.ACCESS_FINE_LOCATION}, 1);
    }

    private void attachPolicy() {
        AWSIotClient awsIotClient = new AWSIotClient(AWSMobileClient.getInstance());
        awsIotClient.setRegion(Region.getRegion(ProxyConfig.region));

        AttachPolicyRequest attachPolicyRequest = new AttachPolicyRequest()
                .withPolicyName(ProxyConfig.policy)
                .withTarget(AWSMobileClient.getInstance().getIdentityId());
        awsIotClient.attachPolicy(attachPolicyRequest);
    }
}
