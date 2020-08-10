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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.awsiotproxy.CustomizedThing;
import com.amazonaws.awsiotproxy.ESP32CustomizedThing;
import com.amazonaws.awsiotproxy.MyDummyCustomizedThing;
import com.amazonaws.awsiotproxy.ProxyConfig;
import com.amazonaws.awsiotproxy.R;
import com.amazonaws.mobile.client.AWSMobileClient;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import software.amazon.freertos.amazonfreertossdk.AmazonFreeRTOSConstants;

import static com.amazonaws.mobile.auth.core.internal.util.ThreadUtils.runOnUiThread;


public class DevicesFragment extends Fragment {

    private ArrayList<CustomizedThing> mDevicesList = new ArrayList<>();
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mDevicesRecyclerView;
    private DevicesRecyclerViewAdapter mDevicesRecyclerViewAdapter;
    private ArrayList<ThingChangeListener> mListeners = new ArrayList<>();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private CustomizedThing.OnConnectionStateListener mOnConnectionStateListener;

    public static DevicesFragment newInstance() {
        return new DevicesFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.devices_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mDevicesRecyclerView = getView().findViewById(R.id.devicesRecyclerView);
        mDevicesRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mDevicesRecyclerViewAdapter = new DevicesRecyclerViewAdapter(mDevicesList);
        mDevicesRecyclerView.setAdapter(mDevicesRecyclerViewAdapter);
        mDevicesRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mBluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mSwipeRefreshLayout = getView().findViewById(R.id.devices);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivity(enableBtIntent);
                    mSwipeRefreshLayout.setRefreshing(false);
                    return;
                }
                new BleScanTask((DevicesFragment) getFragmentManager().getFragments().get(0)).execute();
            }
        });
        mOnConnectionStateListener = new CustomizedThing.OnConnectionStateListener() {
            @Override
            public void onMqttConnectionStateChanged(CustomizedThing thing, AmazonFreeRTOSConstants.MqttConnectionState state) {
                DevicesRecyclerViewAdapter.ViewHolder viewHolder = getViewHolderByThingId(thing.getThingId());
                if (state == AmazonFreeRTOSConstants.MqttConnectionState.MQTT_Connected) {
                    if (viewHolder != null) {
                        viewHolder.progressBar.setVisibility(View.INVISIBLE);
                        viewHolder.deviceNameTextView.setBackgroundColor(Color.GREEN);
                    }
                }
                if (state == AmazonFreeRTOSConstants.MqttConnectionState.MQTT_Disconnected) {
                    if (viewHolder != null) {
                        viewHolder.progressBar.setVisibility(View.INVISIBLE);
                        viewHolder.deviceNameTextView.setBackgroundColor(ContextCompat.getColor(getContext(), android.R.color.transparent));
                        thing.disconnectFromIoT();
                    }
                }
            }

            @Override
            public void onThingConnectionStateChanged(CustomizedThing thing, CustomizedThing.ThingConnectionState state) {
                if (state == CustomizedThing.ThingConnectionState.Thing_Connected) {
                    makeToast("Thing Connected, Connecting to AWS");
                    thing.connectToIoT();
                }
                if (state == CustomizedThing.ThingConnectionState.Thing_Disconnected) {
                    makeToast("Thing Disconnected, Disconnecting from AWS");
                    thing.disconnectFromIoT();
                }
            }
        };
        /**
         * Here we created a dummy devices
         */
        MyDummyCustomizedThing dummyThing = new MyDummyCustomizedThing(ProxyConfig.thingId,
                ProxyConfig.brokerEndpoint,
                AWSMobileClient.getInstance(), getActivity());
        dummyThing.setOnConnectionStateListener(mOnConnectionStateListener);
        mDevicesList.add(dummyThing);
        makeToast("Swipe down to scan BLE devices");
    }

    public void addThingChangeListener(ThingChangeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeThingChangeListener(ThingChangeListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private void notifyThingChangeListener(CustomizedThing thing) {
        for (ThingChangeListener listener : mListeners) {
            if (listener != null) {
                listener.onDevicesChanged(thing);
            }
        }
    }

    private void addNewEsp32(BluetoothDevice bluetoothDevice) {
        for (CustomizedThing existThing : mDevicesList) {
            if (bluetoothDevice.getName().equals(existThing.getThingId())) {
                return;
            }
        }
        ESP32CustomizedThing newThing = new ESP32CustomizedThing(bluetoothDevice.getName(),
                ProxyConfig.brokerEndpoint, AWSMobileClient.getInstance(), getContext(), bluetoothDevice);
        newThing.setOnConnectionStateListener(mOnConnectionStateListener);
        mDevicesList.add(newThing);
        mDevicesRecyclerViewAdapter.notifyDataSetChanged();
    }

    private DevicesRecyclerViewAdapter.ViewHolder getViewHolderByThingId(final String thingId) {
        int pos = 0;
        for (pos = 0; pos < mDevicesList.size(); ++pos) {
            if (mDevicesList.get(pos).getThingId() == thingId) {
                return (DevicesRecyclerViewAdapter.ViewHolder) mDevicesRecyclerView.findViewHolderForAdapterPosition(pos);
            }
        }
        return null;
    }

    private void makeToast(final String msg) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    public class DevicesRecyclerViewAdapter extends RecyclerView.Adapter<DevicesRecyclerViewAdapter.ViewHolder> {

        private ArrayList<CustomizedThing> mDevicesList;

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView deviceNameTextView;
            ProgressBar progressBar;

            public ViewHolder(View view) {
                super(view);
                deviceNameTextView = view.findViewById(R.id.deviceNameTextView);
                progressBar = view.findViewById(R.id.progressBar);
            }
        }

        public DevicesRecyclerViewAdapter(ArrayList<CustomizedThing> devicesList) {
            this.mDevicesList = devicesList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.devices_list_layout, parent, false);
            ViewHolder viewHolder = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CustomizedThing device = mDevicesList.get(mDevicesRecyclerView.getChildLayoutPosition(v));
                    if (device.getThingConnectionState() != CustomizedThing.ThingConnectionState.Thing_Connected) {
                        makeToast("Connecting To Thing");
                        ((ViewHolder) mDevicesRecyclerView.getChildViewHolder(v)).progressBar.setVisibility(View.VISIBLE);
                        device.connectToThing();
                    } else {
                        makeToast("Disconnecting From Thing");
                        device.disconnectFromThing();
                    }
                    notifyThingChangeListener(device);
                }
            });
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.deviceNameTextView.setText(mDevicesList.get(position).getThingId());
            holder.progressBar.setVisibility(View.INVISIBLE);
        }

        @Override
        public int getItemCount() {
            return mDevicesList.size();
        }
    }

    private static class BleScanTask extends AsyncTask<String, String, String> {

        private WeakReference<DevicesFragment> mFragmentReference;
        private static final int SCAN_PERIOD_MS = 3000;
        private ScanCallback mScanCallback;

        public BleScanTask(DevicesFragment fragment) {
            super();
            mFragmentReference = new WeakReference<>(fragment);
            mScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if (result != null) {
                        mFragmentReference.get().addNewEsp32(result.getDevice());
                    }
                }
            };
        }

        @Override
        protected String doInBackground(String... strings) {
            ParcelUuid parcelUuidMask = ParcelUuid.fromString(ESP32CustomizedThing.SERVICE_UUID);
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter.Builder filterBuilder = new ScanFilter.Builder().setServiceUuid(parcelUuidMask);
            filters.add(filterBuilder.build());
            ScanSettings.Builder settingBuilder = new ScanSettings.Builder();
            mFragmentReference.get().mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settingBuilder.build(), mScanCallback);
            try {
                Thread.sleep(SCAN_PERIOD_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mFragmentReference.get().mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            mFragmentReference.get().mSwipeRefreshLayout.setRefreshing(false);
        }
    }

}
