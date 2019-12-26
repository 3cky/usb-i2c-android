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

package com.github.ykc3.android.usbi2c;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.github.ykc3.android.usbi2c.adapter.UsbI2cTinyAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UsbI2cManager {
    private final UsbManager usbManager;

    private final List<Class<? extends UsbI2cAdapter>> usbI2cAdapters;

    public static class UsbDeviceIdentifier {
        private final int vendorId;
        private final int productId;

        public UsbDeviceIdentifier(int vendorId, int productId) {
            this.vendorId = vendorId;
            this.productId = productId;
        }

        public int getVendorId() {
            return vendorId;
        }

        public int getProductId() {
            return productId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UsbDeviceIdentifier that = (UsbDeviceIdentifier) o;
            return vendorId == that.vendorId && productId == that.productId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(vendorId, productId);
        }
    }

    private UsbI2cManager(UsbManager usbManager,
                          List<Class<? extends UsbI2cAdapter>> usbI2cAdapters) {
        this.usbManager = usbManager;
        this.usbI2cAdapters = usbI2cAdapters;
    }

    /**
     * Create {@link Builder} object for building new {@link UsbI2cManager}
     *
     * @param usbManager {@link UsbManager} reference
     * @return new Builder
     */
    public static Builder create(UsbManager usbManager) {
        return new Builder(usbManager);
    }

    /**
     * Get list of supported {@link UsbI2cAdapter}s.
     *
     * @return mutable list of supported USB I2C adapters
     */
    public static List<Class<? extends UsbI2cAdapter>> getSupportedUsbI2cAdapters() {
        List<Class<? extends UsbI2cAdapter>> usbI2cAdapters = new ArrayList<>();
        usbI2cAdapters.add(UsbI2cTinyAdapter.class);
        return usbI2cAdapters;
    }

    public UsbManager getUsbManager() {
        return usbManager;
    }

    /**
     * Get list of all supported {@link UsbI2cAdapter}s currently connected to USB port.
     *
     * @return list of all connected USB I2C adapters
     */
    public List<UsbI2cAdapter> getAdapters() {
        List<UsbI2cAdapter> adapters = new ArrayList<>();
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            UsbI2cAdapter adapter = getAdapter(device);
            if (adapter != null) {
                adapters.add(adapter);
            }
        }
        return Collections.unmodifiableList(adapters);
    }

    /**
     * Get {@link UsbI2cAdapter} for given {@link UsbDevice}.
     *
     * @param device USB device
     * @return reference to USB I2C adapter supporting this USB device or null if no
     * USB I2C adapter supporting this USB device was found
     */
    public UsbI2cAdapter getAdapter(UsbDevice device) {
        UsbDeviceIdentifier deviceUsbIdentifier = new UsbDeviceIdentifier(device.getVendorId(),
                device.getProductId());

        for (Class<? extends UsbI2cAdapter> adapterClass : usbI2cAdapters) {
            final Method method;
            try {
                method = adapterClass.getMethod("getSupportedUsbDeviceIdentifiers");
            } catch (SecurityException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }

            final UsbDeviceIdentifier[] adapterUsbDeviceIdentifiers;
            try {
                adapterUsbDeviceIdentifiers = (UsbDeviceIdentifier[]) method.invoke(null);
            } catch (IllegalArgumentException | IllegalAccessException
                    | InvocationTargetException e) {
                throw new RuntimeException(e);
            }

            for (UsbDeviceIdentifier adapterUsbDeviceIdentifier : adapterUsbDeviceIdentifiers) {
                if (!adapterUsbDeviceIdentifier.equals(deviceUsbIdentifier)) {
                    continue;
                }
                try {
                    final Constructor<? extends UsbI2cAdapter> ctor =
                            adapterClass.getConstructor(UsbI2cManager.class, UsbDevice.class);
                    return ctor.newInstance(this, device);
                } catch (NoSuchMethodException | IllegalArgumentException | InstantiationException |
                        IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return null;
    }

    public static class Builder {
        private final UsbManager usbManager;

        private List<Class<? extends UsbI2cAdapter>> usbI2cAdapters;

        Builder(UsbManager usbManager) {
            this.usbManager = usbManager;
        }

        public Builder setAdapters(List<Class<? extends UsbI2cAdapter>> usbI2cAdapters) {
            this.usbI2cAdapters = usbI2cAdapters;
            return this;
        }

        public UsbI2cManager build() {
            if (usbI2cAdapters == null) {
                usbI2cAdapters = getSupportedUsbI2cAdapters();
            }
            return new UsbI2cManager(usbManager, usbI2cAdapters);
        }
    }
}
