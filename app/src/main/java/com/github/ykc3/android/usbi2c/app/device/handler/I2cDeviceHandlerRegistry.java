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

package com.github.ykc3.android.usbi2c.app.device.handler;

import com.github.ykc3.android.usbi2c.UsbI2cDevice;

import java.util.ArrayList;
import java.util.List;

public class I2cDeviceHandlerRegistry {
    private final List<I2cDeviceHandler> deviceHandlers;

    public I2cDeviceHandlerRegistry(List<I2cDeviceHandler> deviceHandlers) {
        this.deviceHandlers = deviceHandlers;
    }

    public I2cDeviceHandlerRegistry() {
        this(getDefaultI2cDeviceHandlerList());
    }

    public static List<I2cDeviceHandler> getDefaultI2cDeviceHandlerList() {
        List<I2cDeviceHandler> deviceHandlers = new ArrayList<>();
        deviceHandlers.add(new Bme280Handler());
        return deviceHandlers;
    }

    public I2cDeviceHandler findDeviceHandler(UsbI2cDevice device) {
        for (I2cDeviceHandler handler: deviceHandlers) {
            if (handler.isAddressRelated(device.getAddress())
                    && handler.isDeviceSupported(device)) {
                return handler;
            }
        }
        return null;
    }
}
