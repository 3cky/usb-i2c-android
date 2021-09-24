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
    /** Standard clock speed (100 kbit/s) */
    int CLOCK_SPEED_STANDARD = 100000;
    /** Fast clock speed (400 kbit/s) */
    int CLOCK_SPEED_FAST = 400000;
    /** Fast plus clock speed (1 Mbit/s) */
    int CLOCK_SPEED_FAST_PLUS = 1000000;
    /** High clock speed (3.4 Mbit/s) */
    int CLOCK_SPEED_HIGH = 3400000;

    /**
     * Get I2C adapter name.
     *
     * @return I2C adapter name
     */
    String getName();

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
     * Open I2C adapter for communicating to connected I2C devices.
     *
     * @throws IOException in case of I/O error
     * @throws IllegalStateException if I2C adapter is already opened
     */
    void open() throws IOException;

    /**
     * Get reference {@link UsbI2cDevice} connected to this I2C adapter.
     *
     * @param address I2C device address
     * @return reference to I2C device connected to this I2C adapter
     * @throws IllegalStateException if I2C adapter is not opened or closed
     */
    UsbI2cDevice getDevice(int address);

    /**
     * Check I2C bus clock speed is supported by adapter.
     * UsbI2cAdapter.SPEED_STANDARD is guaranteed to be supported by all adapters.
     *
     * @param speed I2C bus clock speed value to check (in bit/s)
     * @return true if speed is supported by I2C adapter, false if not supported
     * @since 1.2
     */
    boolean isClockSpeedSupported(int speed);

    /**
     * Set I2C bus clock speed. Default is UsbI2cAdapter.SPEED_STANDARD.
     *
     * @param speed I2C bus clock speed value to set (in bit/s)
     * @throws IllegalArgumentException if this I2C bus clock speed is not supported by adapter
     * @throws IOException in case of I/O error
     * @since 1.2
     */
    void setClockSpeed(int speed) throws IOException;
}
