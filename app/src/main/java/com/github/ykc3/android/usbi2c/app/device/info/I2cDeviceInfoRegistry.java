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

package com.github.ykc3.android.usbi2c.app.device.info;

import android.content.res.Resources;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * I2C device info registry class.
 */
public class I2cDeviceInfoRegistry {
    private final List<I2cDeviceInfo> deviceInfos;

    private I2cDeviceInfoRegistry(List<I2cDeviceInfo> deviceInfos) {
        this.deviceInfos = deviceInfos;
    }

    public static I2cDeviceInfoRegistry createFromJson(String deviceDatabaseJson) {
        Gson gson = new Gson();
        I2cDeviceInfo[] i2cDeviceInfoArray = gson.fromJson(deviceDatabaseJson, I2cDeviceInfo[].class);
        return new I2cDeviceInfoRegistry(Arrays.asList(i2cDeviceInfoArray));
    }

    public static I2cDeviceInfoRegistry createFromResource(Resources resources,
                                                           int deviceDatabaseResourceId) throws IOException {
        InputStream resourceReader = resources.openRawResource(deviceDatabaseResourceId);

        Writer writer = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceReader, "UTF-8"))) {
            String line = reader.readLine();
            while (line != null) {
                writer.write(line);
                line = reader.readLine();
            }
        }

        return createFromJson(writer.toString());
    }

    public List<I2cDeviceInfo> findDevicesByAddress(int address) {
        List<I2cDeviceInfo> result = new ArrayList<>();
        for (I2cDeviceInfo deviceInfo : deviceInfos) {
            if (deviceInfo.getAddresses().contains(address)) {
                result.add(deviceInfo);
            }
        }
        return result;
    }

    public I2cDeviceInfo findDeviceByPartNumber(String partNumber) {
        for (I2cDeviceInfo deviceInfo : deviceInfos) {
            if (deviceInfo.getPartNumber().equals(partNumber)) {
                return deviceInfo;
            }
        }
        return null;
    }
}
