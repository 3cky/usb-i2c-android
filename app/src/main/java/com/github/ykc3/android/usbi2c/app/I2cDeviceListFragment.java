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
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.os.Bundle;

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
import com.github.ykc3.android.usbi2c.app.device.driver.I2cDeviceDriver;
import com.github.ykc3.android.usbi2c.app.device.I2cDeviceProber;

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

    private I2cDeviceProber i2cDeviceProber;

    private CustomRecyclerView recyclerView;
    private RecyclerViewAdapter recyclerViewAdapter;

    private SwipeRefreshLayout deviceListRefreshLayout;

    private AsyncTask<UsbDevice, Integer, Integer> scanI2cDevicesTask;

    public static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
        private List<Item> items = new ArrayList<>();

        private final View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO
            }
        };

        private static class Item {
            final int deviceAddress;
            final String deviceInfo;

            Item(int deviceAddress, String deviceInfo) {
                this.deviceAddress = deviceAddress;
                this.deviceInfo = deviceInfo;
            }
        }

        RecyclerViewAdapter() {
        }

        void addItem(int deviceAddress, String deviceInfo) {
            items.add(new Item(deviceAddress, deviceInfo));
            notifyItemInserted(items.size() - 1);
        }

        void clearItems() {
            items.clear();
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
            Item item = items.get(position);
            holder.deviceAddressView.setText(String.format("0x%02x", item.deviceAddress));
            holder.deviceInfoView.setText(item.deviceInfo);

            holder.itemView.setTag(items.get(position));
            holder.itemView.setOnClickListener(onClickListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
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
                        final int foundI2cAddress = i2cAddress;
                        final String deviceInfo = getI2cDeviceInfo(fragment.i2cDeviceProber, i2cDevice);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                fragment.recyclerViewAdapter.addItem(foundI2cAddress, deviceInfo);
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

        String getI2cDeviceInfo(I2cDeviceProber i2cDeviceProber, UsbI2cDevice i2cDevice) {
            I2cDeviceDriver deviceDriver = i2cDeviceProber.findDriver(i2cDevice);
            if (deviceDriver != null) {
                try {
                    return deviceDriver.getInfo(i2cDevice);
                } catch (IOException e) {
                    Log.e(TAG,"can't get device info", e);
                }
            }
            return fragment.getString(R.string.unknown_device);
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

        i2cDeviceProber = new I2cDeviceProber();

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
