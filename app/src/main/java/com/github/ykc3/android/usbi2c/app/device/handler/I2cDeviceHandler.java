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

package com.github.ykc3.android.usbi2c.app.device.handler;

import com.github.ykc3.android.usbi2c.UsbI2cDevice;

import java.io.IOException;

/**
 * Specific I2C device handler.
 */
public interface I2cDeviceHandler {
    /**
     * Check this I2C device is supported by this handler.
     * @param device I2C device to check
     * @return true if device supported by this handler, false otherwise
     */
    boolean isDeviceSupported(UsbI2cDevice device);

    /**
     * Get supported I2C device part number.
     * @param device device supported I2C device
     * @return device part number
     * @throws IOException in case of device I/O error
     */
    String getDevicePartNumber(UsbI2cDevice device) throws IOException;

    /**
     * Get supported I2C device description.
     * @param device supported I2C device
     * @return device description
     * @throws IOException in case of device I/O error
     */
    String getDeviceDescription(UsbI2cDevice device) throws IOException;
}
