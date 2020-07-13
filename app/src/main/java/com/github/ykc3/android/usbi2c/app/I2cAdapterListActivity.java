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

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.ykc3.android.usbi2c.UsbI2cAdapter;
import com.github.ykc3.android.usbi2c.UsbI2cManager;
import com.github.ykc3.android.usbi2c.app.view.CustomRecyclerView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * An activity representing a list of UsbI2cAdapters. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link I2cDeviceListActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class I2cAdapterListActivity extends AppCompatActivity {
    private final String TAG = I2cAdapterListActivity.class.getSimpleName();

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean isTwoPane;

    private UsbManager usbManager;
    private UsbI2cManager usbI2cManager;

    private CustomRecyclerView recyclerView;
    private RecyclerViewAdapter recyclerViewAdapter;

    SwipeRefreshLayout adapterListRefreshLayout;

    private PendingIntent usbPermissionIntent;

    private static final String ACTION_USB_PERMISSION =
            "com.github.ykc3.android.usbi2c.app.USB_PERMISSION";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        showI2cDeviceListPane(context, usbDevice);
                    }
                    else {
                        // TODO handle this
                        Log.d(TAG, "permission denied for device " + usbDevice);
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                    || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                refreshAdapterList();
            }
        }
    };

    public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
        private List<UsbI2cAdapter> items = new ArrayList<>();;

        private final View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UsbI2cAdapter item = (UsbI2cAdapter) view.getTag();
                usbManager.requestPermission(item.getUsbDevice(), usbPermissionIntent);
            }
        };

        void updateItems(List<UsbI2cAdapter> items) {
            this.items.clear();
            this.items.addAll(items);
            notifyDataSetChanged();
        }

        void clearItems() {
            items.clear();
            notifyDataSetChanged();
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.adapter_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
            UsbDevice usbDevice = items.get(position).getUsbDevice();
            holder.adapterIdView.setText(String.format("%04x:%04x", usbDevice.getVendorId(),
                    usbDevice.getProductId()));
            String adapterName = usbDevice.getProductName();
            if (adapterName == null || adapterName.isEmpty()) {
                adapterName = usbDevice.getDeviceName();
            }
            holder.adapterNameView.setText(adapterName);

            holder.itemView.setTag(items.get(position));
            holder.itemView.setOnClickListener(onClickListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView adapterIdView;
            final TextView adapterNameView;

            ViewHolder(View view) {
                super(view);
                adapterIdView = view.findViewById(R.id.adapter_id);
                adapterNameView = view.findViewById(R.id.adapter_name);
            }
        }
    }

    SwipeRefreshLayout.OnRefreshListener onRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            recyclerViewAdapter.clearItems();
            recyclerView.showEmptyView(false);
            final Snackbar snackbar = Snackbar.make(adapterListRefreshLayout,
                    R.string.adapter_scan_info, Snackbar.LENGTH_INDEFINITE);
            snackbar.show();
            new Handler().postDelayed(new Runnable() {
                @Override public void run() {
                    scanI2cAdapters();
                    recyclerView.showEmptyView(recyclerView.isEmpty());
                    adapterListRefreshLayout.setRefreshing(false);
                    snackbar.dismiss();
                }
            }, 1000);
        }
    };

    protected void showI2cDeviceListPane(Context context, UsbDevice usbDevice) {
        if (isTwoPane) {
            Bundle arguments = new Bundle();
            arguments.putParcelable(I2cDeviceListFragment.KEY_USB_DEVICE, usbDevice);
            I2cDeviceListFragment fragment = new I2cDeviceListFragment();
            fragment.setArguments(arguments);
            this.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.adapter_device_list_container, fragment)
                    .commit();
        } else {
            Intent intent = new Intent(context, I2cDeviceListActivity.class);
            intent.putExtra(I2cDeviceListFragment.KEY_USB_DEVICE, usbDevice);
            context.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adapter_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        if (findViewById(R.id.device_list_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            isTwoPane = true;
        }

        recyclerView = findViewById(R.id.adapter_list);
        recyclerViewAdapter = new RecyclerViewAdapter();
        recyclerView.setAdapter(recyclerViewAdapter);
        recyclerView.setEmptyView(findViewById(R.id.empty_adapter_list));
        recyclerView.showEmptyView(false);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        assert usbManager != null;

        usbI2cManager = UsbI2cManager.create(usbManager).build();

        usbPermissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter usbReceiverFilter = new IntentFilter(ACTION_USB_PERMISSION);
        usbReceiverFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbReceiverFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, usbReceiverFilter);

        adapterListRefreshLayout = findViewById(R.id.adapter_list_refresh);
        adapterListRefreshLayout.setOnRefreshListener(onRefreshListener);

        refreshAdapterList();
    }

    void refreshAdapterList() {
        if (adapterListRefreshLayout != null) {
            adapterListRefreshLayout.post(new Runnable() {
                @Override public void run() {
                    adapterListRefreshLayout.setRefreshing(true);
                    onRefreshListener.onRefresh();
                }
            });
        }
    }

    void scanI2cAdapters() {
        List<UsbI2cAdapter> items = usbI2cManager.getAdapters();
        recyclerViewAdapter.updateItems(items);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
    }
}
