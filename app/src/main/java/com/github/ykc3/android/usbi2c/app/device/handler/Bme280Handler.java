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

import android.annotation.SuppressLint;

import com.github.ykc3.android.usbi2c.UsbI2cDevice;

import java.io.IOException;

/**
 * Bosch Sensortec BMP280/BME280 digital temperature/pressure/humidity sensor.
 * Based on https://github.com/ControlEverythingCommunity/BME280/blob/master/Java/BME280.java
 */
public class Bme280Handler extends AbstractI2cDeviceHandler {
    private static final String BMP280_PART_NUMBER = "BMP280";
    private static final String BME280_PART_NUMBER = "BME280";

    private static final int[] I2C_ADDRESSES = { 0x76, 0x77 };

    private static final int BMP280_CHIP_ID = 0x58;
    private static final int BME280_CHIP_ID = 0x60;

    private static final int REG_TEMP_PRESS_CALIB_DATA = 0x88;
    private static final int REG_DIG_H1 = 0xA1;
    private static final int REG_DIG_H2 = 0xE1;
    private static final int REG_CHIP_ID = 0xD0;
    private static final int REG_CTRL_HUM = 0xF2;
    private static final int REG_STATUS = 0xF3;
    private static final int REG_CTRL_MEAS = 0xF4;
    private static final int REG_CONFIG = 0xF5;
    private static final int REG_DATA = 0xF7;

    @Override
    protected int[] getDeviceAddresses() {
        return I2C_ADDRESSES;
    }

    @Override
    public String getDevicePartNumber(UsbI2cDevice device) throws IOException {
        return isHumiditySensorPresent(device) ? BME280_PART_NUMBER : BMP280_PART_NUMBER;
    }

    @Override
    protected boolean isDeviceRecognized(UsbI2cDevice device) {
        try {
            int chipId = readChipId(device);
            return chipId == BMP280_CHIP_ID || chipId == BME280_CHIP_ID;
        } catch (IOException ignored) {
        }
        return false;
    }

    private boolean isHumiditySensorPresent(UsbI2cDevice device) throws IOException {
        return readChipId(device) == BME280_CHIP_ID;
    }

    private byte readChipId(UsbI2cDevice device) throws IOException {
        return device.readRegByte(REG_CHIP_ID);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String getDeviceDescription(UsbI2cDevice device) throws IOException {
        boolean isHumiditySensorPresent = isHumiditySensorPresent(device);

        byte[] b1 = new byte[24];
        device.readRegBuffer(REG_TEMP_PRESS_CALIB_DATA, b1, 24);

        // Convert the data temp coefficients
        int dig_T1 = (b1[0] & 0xFF) + ((b1[1] & 0xFF) * 256);
        int dig_T2 = (b1[2] & 0xFF) + ((b1[3] & 0xFF) * 256);
        if (dig_T2 > 32767) {
            dig_T2 -= 65536;
        }
        int dig_T3 = (b1[4] & 0xFF) + ((b1[5] & 0xFF) * 256);
        if (dig_T3 > 32767) {
            dig_T3 -= 65536;
        }

        // Pressure coefficients
        int dig_P1 = (b1[6] & 0xFF) + ((b1[7] & 0xFF) * 256);
        int dig_P2 = (b1[8] & 0xFF) + ((b1[9] & 0xFF) * 256);
        if (dig_P2 > 32767) {
            dig_P2 -= 65536;
        }
        int dig_P3 = (b1[10] & 0xFF) + ((b1[11] & 0xFF) * 256);
        if (dig_P3 > 32767) {
            dig_P3 -= 65536;
        }
        int dig_P4 = (b1[12] & 0xFF) + ((b1[13] & 0xFF) * 256);
        if (dig_P4 > 32767) {
            dig_P4 -= 65536;
        }
        int dig_P5 = (b1[14] & 0xFF) + ((b1[15] & 0xFF) * 256);
        if (dig_P5 > 32767) {
            dig_P5 -= 65536;
        }
        int dig_P6 = (b1[16] & 0xFF) + ((b1[17] & 0xFF) * 256);
        if (dig_P6 > 32767) {
            dig_P6 -= 65536;
        }
        int dig_P7 = (b1[18] & 0xFF) + ((b1[19] & 0xFF) * 256);
        if (dig_P7 > 32767) {
            dig_P7 -= 65536;
        }
        int dig_P8 = (b1[20] & 0xFF) + ((b1[21] & 0xFF) * 256);
        if (dig_P8 > 32767) {
            dig_P8 -= 65536;
        }
        int dig_P9 = (b1[22] & 0xFF) + ((b1[23] & 0xFF) * 256);
        if (dig_P9 > 32767) {
            dig_P9 -= 65536;
        }

        int dig_H1 = 0, dig_H2 = 0, dig_H3 = 0, dig_H4 = 0, dig_H5 = 0, dig_H6 = 0;
        if (isHumiditySensorPresent) {
            // Read 1 byte of data from address 0xA1 (161)
            dig_H1 = (device.readRegByte(REG_DIG_H1) & 0xFF);

            // Read 7 bytes of data from address 0xE1 (225)
            device.readRegBuffer(REG_DIG_H2, b1, 7);

            // Convert the data humidity coefficients
            dig_H2 = (b1[0] & 0xFF) + ((b1[1] & 0xFF) * 256);
            if (dig_H2 > 32767) {
                dig_H2 -= 65536;
            }
            dig_H3 = b1[2] & 0xFF;
            dig_H4 = ((b1[3] & 0xFF) * 16) + (b1[4] & 0xF);
            if (dig_H4 > 2047) {
                dig_H4 -= 4096;
            }
            dig_H5 = ((b1[4] & 0xFF) / 16) + ((b1[5] & 0xFF) * 16);
            if (dig_H5 > 2047) {
                dig_H5 -= 4096;
            }
            dig_H6 = b1[6] & 0xFF;
            if (dig_H6 > 127) {
                dig_H6 -= 256;
            }
            // Select control humidity register
            // Humidity over sampling rate = 1
            device.writeRegByte(REG_CTRL_HUM, (byte) 0x01);
        }

        // Select control measurement register
        // Normal mode, temp and pressure over sampling rate = 1
        device.writeRegByte(REG_CTRL_MEAS, (byte) 0x27);
        // Select config register
        // Stand_by time = 1000 ms
        device.writeRegByte(REG_CONFIG, (byte) 0xA0);

        // Wait for conversion completion
        while ((device.readRegByte(REG_STATUS) & 0x08) != 0) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ie) {
                throw new IOException(ie);
            }
        }

        // Read 8 bytes of data from address 0xF7 (247)
        // pressure msb1, pressure msb, pressure lsb, temp msb1, temp msb, temp lsb, humidity lsb, humidity msb
        byte[] data = new byte[8];
        device.readRegBuffer(REG_DATA, data, isHumiditySensorPresent ? 8 : 6);

        // Convert pressure and temperature data to 19-bits
        long adc_p = (((long) (data[0] & 0xFF) * 65536) +
                ((long) (data[1] & 0xFF) * 256) + (long) (data[2] & 0xF0)) / 16;
        long adc_t = (((long) (data[3] & 0xFF) * 65536) +
                ((long) (data[4] & 0xFF) * 256) + (long) (data[5] & 0xF0)) / 16;

        // Temperature offset calculations
        double var1 = (((double) adc_t) / 16384.0 - ((double) dig_T1) / 1024.0) * ((double) dig_T2);
        double var2 = ((((double) adc_t) / 131072.0 - ((double) dig_T1) / 8192.0) *
                (((double) adc_t) / 131072.0 - ((double) dig_T1) / 8192.0)) * ((double) dig_T3);
        double t_fine = (long) (var1 + var2);
        double temp = (var1 + var2) / 5120.0;

        // Pressure offset calculations
        var1 = (t_fine / 2.0) - 64000.0;
        var2 = var1 * var1 * ((double) dig_P6) / 32768.0;
        var2 = var2 + var1 * ((double) dig_P5) * 2.0;
        var2 = (var2 / 4.0) + (((double) dig_P4) * 65536.0);
        var1 = (((double) dig_P3) * var1 * var1 / 524288.0 + ((double) dig_P2) * var1) / 524288.0;
        var1 = (1.0 + var1 / 32768.0) * ((double) dig_P1);
        double p = 1048576.0 - (double) adc_p;
        p = (p - (var2 / 4096.0)) * 6250.0 / var1;
        var1 = ((double) dig_P9) * p * p / 2147483648.0;
        var2 = p * ((double) dig_P8) / 32768.0;
        double pressure = (p + (var1 + var2 + ((double) dig_P7)) / 16.0) / 100;

        double humidity = 0.;
        if (isHumiditySensorPresent) {
            // Convert the humidity data
            long adc_h = ((long) (data[6] & 0xFF) * 256 + (long) (data[7] & 0xFF));
            // Humidity offset calculations
            double var_H = (t_fine - 76800.0);
            var_H = (adc_h - (dig_H4 * 64.0 + dig_H5 / 16384.0 * var_H)) * (dig_H2 / 65536.0 *
                    (1.0 + dig_H6 / 67108864.0 * var_H * (1.0 + dig_H3 / 67108864.0 * var_H)));
            humidity = var_H * (1.0 - dig_H1 * var_H / 524288.0);
            if (humidity > 100.0) {
                humidity = 100.0;
            } else if (humidity < 0.0) {
                humidity = 0.0;
            }
        }

        return isHumiditySensorPresent
                ? String.format("%.1f°C/%.1f%% RH/%.1f hPa", temp, humidity, pressure)
                : String.format("%.1f°C/%.1f hPa", temp, pressure);
    }
}
