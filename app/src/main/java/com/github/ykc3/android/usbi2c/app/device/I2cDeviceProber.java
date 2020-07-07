/*
 * Copyright (c) 2020 Victor Antonovich <v.antonovich@gmail.com>
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

package com.github.ykc3.android.usbi2c.app.device;

import com.github.ykc3.android.usbi2c.UsbI2cDevice;
import com.github.ykc3.android.usbi2c.app.device.driver.Bme280Driver;
import com.github.ykc3.android.usbi2c.app.device.driver.I2cDeviceDriver;

import java.util.ArrayList;
import java.util.List;

public class I2cDeviceProber {
    private final List<I2cDeviceDriver> i2cDeviceDrivers;

    public I2cDeviceProber(List<I2cDeviceDriver> i2cDeviceDrivers) {
        this.i2cDeviceDrivers = i2cDeviceDrivers;
    }

    public I2cDeviceProber() {
        this(getDefaultI2cDeviceDriverList());
    }

    public static List<I2cDeviceDriver> getDefaultI2cDeviceDriverList() {
        List<I2cDeviceDriver> driverList = new ArrayList<>();
        driverList.add(new Bme280Driver());
        return driverList;
    }

    public I2cDeviceDriver findDriver(UsbI2cDevice device) {
        for (I2cDeviceDriver driver: i2cDeviceDrivers) {
            if (driver.isRelatedAddress(device.getAddress()) && driver.isDetected(device)) {
                return driver;
            }
        }
        return null;
    }
}
