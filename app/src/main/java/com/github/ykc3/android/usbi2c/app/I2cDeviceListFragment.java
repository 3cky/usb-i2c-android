/*
 * Copyright (c) 2019 Victor Antonovich <v.antonovich@gmail.com>
 *
 * This work is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This work is distributed in the hope that it will be useful, but
 * without any warranty; without even the implied warranty of merchantability
 * or fitness for a particular purpose. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package com.github.ykc3.android.usbi2c.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.os.Bundle;

import com.github.ykc3.android.usbi2c.app.device.info.I2cDeviceInfo;
import com.github.ykc3.android.usbi2c.app.device.info.I2cDeviceInfoRegistry;
import com.github.ykc3.android.usbi2c.app.view.CustomRecyclerView;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.ykc3.android.usbi2c.UsbI2cAdapter;
import com.github.ykc3.android.usbi2c.UsbI2cDevice;
import com.github.ykc3.android.usbi2c.UsbI2cManager;

import com.github.ykc3.android.usbi2c.app.device.handler.I2cDeviceHandler;
import com.github.ykc3.android.usbi2c.app.device.handler.I2cDeviceHandlerRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A fragment representing a {@link com.github.ykc3.android.usbi2c.UsbI2cDevice} list screen.
 * This fragment is either contained in a {@link I2cAdapterListActivity}
 * in two-pane mode (on tablets) or a {@link I2cDeviceListActivity}
 * on handsets.
 */
public class I2cDeviceListFragment extends Fragment {
    private final String TAG = I2cDeviceListFragment.class.getSimpleName();

    static final String KEY_USB_DEVICE = "com.github.ykc3.android.usbi2c.app.USB_DEVICE";

    private static final int MIN_I2C_ADDRESS = 0x03;
    private static final int MAX_I2C_ADDRESS = 0x77;

    private UsbDevice usbDevice;

    private UsbI2cManager usbI2cManager;

    private I2cDeviceHandlerRegistry i2cDeviceHandlerRegistry;
    private I2cDeviceInfoRegistry i2cDeviceInfoRegistry;

    private CustomRecyclerView recyclerView;
    private RecyclerViewAdapter recyclerViewAdapter;

    private SwipeRefreshLayout deviceListRefreshLayout;

    private AsyncTask<UsbDevice, Integer, Integer> scanI2cDevicesTask;

    private static class DeviceItem {
        final int deviceAddress;
        final List<I2cDeviceInfo> candidateDeviceInfos;
        final boolean isDeviceRecognized;
        final I2cDeviceInfo recognizedDeviceInfo;
        final String deviceDescriptor;

        DeviceItem(int deviceAddress, List<I2cDeviceInfo> candidateDeviceInfos,
                   boolean isDeviceRecognized, I2cDeviceInfo recognizedDeviceInfo,
                   String deviceDescriptor) {
            this.deviceAddress = deviceAddress;
            this.candidateDeviceInfos = candidateDeviceInfos;
            this.isDeviceRecognized = isDeviceRecognized;
            this.recognizedDeviceInfo = recognizedDeviceInfo;
            this.deviceDescriptor = deviceDescriptor;
        }

        String getDeviceHexAddress() {
            return String.format("0x%02x", deviceAddress);
        }

        String getDeviceInfo() {
            StringBuilder builder = new StringBuilder();
            if (isDeviceRecognized && recognizedDeviceInfo != null) {
                builder.append(recognizedDeviceInfo.getPartNumber());
                if (deviceDescriptor != null && !deviceDescriptor.isEmpty()) {
                    builder.append(" (");
                    builder.append(deviceDescriptor);
                    builder.append(")");
                }
            } else if (candidateDeviceInfos != null && !candidateDeviceInfos.isEmpty()) {
                for (I2cDeviceInfo deviceInfo : candidateDeviceInfos) {
                    builder.append(deviceInfo.getPartNumber());
                    builder.append(' ');
                }
            }
            return (builder.length() > 0) ? builder.toString() : null;
        }

        String getDevicePage() {
            StringBuilder builder = new StringBuilder("https://i2cdevices.org/");
            if (isDeviceRecognized && recognizedDeviceInfo != null) {
                builder.append("devices/");
                builder.append(recognizedDeviceInfo.getPartNumber().toLowerCase());
            } else {
                builder.append("addresses/");
                builder.append(getDeviceHexAddress());
            }
            return builder.toString();
        }
    }

    private final View.OnClickListener onDeviceItemClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (view.getTag() == null) {
                return;
            }
            DeviceItem clickedDeviceItem = (DeviceItem) view.getTag();
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(clickedDeviceItem.getDevicePage()));
            I2cDeviceListFragment.this.startActivity(browserIntent);
        }
    };

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
        private List<DeviceItem> deviceItems = new ArrayList<>();

        void addItem(DeviceItem item) {
            deviceItems.add(item);
            notifyItemInserted(deviceItems.size() - 1);
        }

        void clearItems() {
            deviceItems.clear();
            notifyDataSetChanged();
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.device_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            DeviceItem deviceItem = deviceItems.get(position);
            holder.deviceAddressView.setText(deviceItem.getDeviceHexAddress());
            String deviceInfo = deviceItem.getDeviceInfo();
            holder.deviceInfoView.setText(deviceInfo != null ? deviceInfo :
                    getString(R.string.unknown_device));

            holder.itemView.setTag(deviceItems.get(position));
            holder.itemView.setOnClickListener(onDeviceItemClickListener);
        }

        @Override
        public int getItemCount() {
            return deviceItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView deviceAddressView;
            final TextView deviceInfoView;

            ViewHolder(View view) {
                super(view);
                deviceAddressView = view.findViewById(R.id.device_address);
                deviceInfoView = view.findViewById(R.id.device_info);
            }
        }
    }

    static class ScanI2cDevicesTask extends AsyncTask<UsbDevice, Integer, Integer> {
        private final String TAG = ScanI2cDevicesTask.class.getSimpleName();

        private final I2cDeviceListFragment fragment;

        private Snackbar snackbar;

        ScanI2cDevicesTask(I2cDeviceListFragment fragment) {
            this.fragment = fragment;
        }

        @Override
        protected Integer doInBackground(UsbDevice... usbDevices) {
            try (UsbI2cAdapter usbI2cAdapter = fragment.usbI2cManager.getAdapter(usbDevices[0])) {
                usbI2cAdapter.setClockSpeed(UsbI2cAdapter.CLOCK_SPEED_STANDARD);
                usbI2cAdapter.open();
                byte[] buf = new byte[1];
                for (int i2cAddress = MIN_I2C_ADDRESS; i2cAddress <= MAX_I2C_ADDRESS; i2cAddress++) {
                    try {
                        UsbI2cDevice i2cDevice = usbI2cAdapter.getDevice(i2cAddress);
                        i2cDevice.read(buf, 1); // IOException if no device at this address
                        Log.d(TAG, String.format("found I2C device at 0x%02x", i2cAddress));
                        Activity activity = fragment.getActivity();
                        if (activity == null) {
                            break;
                        }
                        final DeviceItem deviceItem = getDeviceItem(i2cDevice);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fragment.recyclerViewAdapter.addItem(deviceItem);
                            }
                        });
                    } catch (IOException e) {
                        Log.d(TAG, String.format("no I2C device found at 0x%02x (%s)",
                                i2cAddress, e.getMessage()));
                    }
                    if (isCancelled()) {
                        break;
                    }
                    publishProgress(i2cAddress);
                }
            } catch (Exception e) {
                // TODO Show scan error message
                Log.e(TAG, "scan error", e);
            }
            return null;
        }

        DeviceItem getDeviceItem(UsbI2cDevice i2cDevice) {
            boolean isDeviceRecognized = false;
            List<I2cDeviceInfo> candidateDeviceInfos = null;
            I2cDeviceInfo recognizedDeviceInfo = null;
            String deviceDescriptor = null;

            // First trying to find handler for concrete I2C device with given address
            I2cDeviceHandler deviceHandler = fragment.i2cDeviceHandlerRegistry.findDeviceHandler(
                    i2cDevice);
            if (deviceHandler != null) {
                isDeviceRecognized = true;
                recognizedDeviceInfo = fragment.i2cDeviceInfoRegistry.findDeviceByPartNumber(
                        deviceHandler.getDevicePartNumber());
                try {
                    // Handler found, trying to use it to get I2C device info
                    deviceDescriptor = deviceHandler.getDeviceDescriptor(i2cDevice);
                } catch (IOException e) {
                    Log.e(TAG,"can't get device info", e);
                }
            } else {
                // No device handler found, check for list of all known I2C devices w/ this address
                if (fragment.i2cDeviceInfoRegistry != null) {
                    candidateDeviceInfos = fragment.i2cDeviceInfoRegistry.findDevicesByAddress(
                            i2cDevice.getAddress());
                }
            }

            return new DeviceItem(i2cDevice.getAddress(), candidateDeviceInfos, isDeviceRecognized,
                    recognizedDeviceInfo, deviceDescriptor);
        }

        @Override
        protected void onPreExecute() {
            fragment.recyclerView.showEmptyView(false);
            fragment.recyclerViewAdapter.clearItems();
            snackbar = Snackbar.make(fragment.deviceListRefreshLayout,
                    fragment.getResources().getString(R.string.device_scan_info, MIN_I2C_ADDRESS),
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.cancel, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            fragment.cancelScanI2cDevices();
                        }
                    });
            snackbar.show();
        }

        private void dismissUiProgressItems() {
            new Handler().postDelayed(new Runnable() {
                @Override public void run() {
                    fragment.recyclerView.showEmptyView(fragment.recyclerView.isEmpty());
                    fragment.deviceListRefreshLayout.setRefreshing(false);
                    snackbar.dismiss();
                }
            }, isCancelled() ? 0 : 1000);
        }

        @Override
        protected void onPostExecute(Integer result) {
            dismissUiProgressItems();
        }

        @Override
        protected void onCancelled() {
            dismissUiProgressItems();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (fragment.isAdded()) {
                snackbar.setText(fragment.getResources().getString(R.string.device_scan_info,
                        values[values.length-1]));
            }
        }
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public I2cDeviceListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity activity = this.getActivity();
        assert activity != null;

        Bundle arguments = getArguments();
        assert arguments != null;

        if (!arguments.containsKey(KEY_USB_DEVICE)) {
            throw new AssertionError("No USB device found to scan");
        }

        usbDevice = arguments.getParcelable(KEY_USB_DEVICE);

        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        assert usbManager != null;

        usbI2cManager = UsbI2cManager.create(usbManager).build();

        i2cDeviceHandlerRegistry = new I2cDeviceHandlerRegistry();

        try {
            i2cDeviceInfoRegistry = I2cDeviceInfoRegistry.createFromResource(getResources(),
                    R.raw.devices);
        } catch (IOException e) {
            Log.e(TAG, "Can't create I2C device info registry", e);
        }

//        CollapsingToolbarLayout appBarLayout = activity.findViewById(R.id.toolbar_layout);
//        if (appBarLayout != null) {
//            appBarLayout.setTitle(usbDevice.getProductName());
//        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.device_list, container, false);

        recyclerView = rootView.findViewById(R.id.device_list);
        recyclerViewAdapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(recyclerViewAdapter);
        recyclerView.setEmptyView(rootView.findViewById(R.id.empty_device_list));
        recyclerView.showEmptyView(false);

        deviceListRefreshLayout = rootView.findViewById(R.id.device_list_refresh);
        final SwipeRefreshLayout.OnRefreshListener onRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                scanI2cDevices();
            }
        };
        deviceListRefreshLayout.setOnRefreshListener(onRefreshListener);

        deviceListRefreshLayout.post(new Runnable() {
            @Override public void run() {
                deviceListRefreshLayout.setRefreshing(true);
                onRefreshListener.onRefresh();
            }
        });

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelScanI2cDevices();
    }

    protected void scanI2cDevices() {
        Activity activity = this.getActivity();
        assert activity != null;

        scanI2cDevicesTask = new ScanI2cDevicesTask(this);
        scanI2cDevicesTask.execute(usbDevice);
    }

    protected void cancelScanI2cDevices() {
        if (scanI2cDevicesTask != null) {
            scanI2cDevicesTask.cancel(false);
        }
    }
}
