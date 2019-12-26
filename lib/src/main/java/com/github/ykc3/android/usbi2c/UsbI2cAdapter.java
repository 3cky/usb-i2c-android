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

import java.io.IOException;

/**
 * I2C adapter connected to USB bus.
 */
public interface UsbI2cAdapter extends AutoCloseable {
    /**
     * Get I2C adapter identifier string.
     *
     * @return I2C adapter identifier string
     */
    String getId();

    /**
     * Get reference to underlying {@link UsbDevice} for this I2C adapter.
     *
     * @return reference to UsbDevice
     */
    UsbDevice getUsbDevice();

    /**
     * Get reference {@link UsbI2cDevice} connected to this I2C adapter.
     *
     * @param address I2C device address
     * @return reference to I2C device connected to this I2C adapter
     */
    UsbI2cDevice getDevice(int address);

    /**
     * Open I2C adapter for communicating to connected I2C devices.
     *
     * @throws IOException in case of I/O error
     */
    void open() throws IOException;
}
