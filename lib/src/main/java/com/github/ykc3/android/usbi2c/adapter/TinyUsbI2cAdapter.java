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

package com.github.ykc3.android.usbi2c.adapter;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;

import com.github.ykc3.android.usbi2c.UsbI2cDevice;
import com.github.ykc3.android.usbi2c.UsbI2cManager;
import static com.github.ykc3.android.usbi2c.UsbI2cManager.UsbDeviceIdentifier;

import java.io.IOException;

/**
 * Cheap and simple I2C to USB interface (<a href="https://github.com/harbaum/I2C-Tiny-USB">github project</a>).
 */
public class TinyUsbI2cAdapter extends BaseUsbI2cAdapter {
    // Adapter name
    public static final String ADAPTER_NAME = "TinyUSB";

    // Commands via USB, must match command ids in the firmware
    private static final int CMD_ECHO = 0;
    private static final int CMD_GET_FUNC = 1;
    private static final int CMD_SET_DELAY = 2;
    private static final int CMD_GET_STATUS = 3;

    private static final int CMD_I2C_IO = 4;
    private static final int CMD_I2C_IO_BEGIN = 1;
    private static final int CMD_I2C_IO_END = (1 << 1);

    private static final int USB_RECIP_INTERFACE = 0x01;

    class TinyUsbI2cDevice extends BaseUsbI2cDevice {
        private final byte[] regBuffer = new byte[1];

        TinyUsbI2cDevice(int address) {
            super(address);
        }

        @Override
        protected void deviceReadReg(int reg, byte[] buffer, int length) throws IOException {
            checkDataLength(length, buffer.length);
            regBuffer[0] = (byte) reg;
            usbWrite(CMD_I2C_IO | CMD_I2C_IO_BEGIN, 0, address, regBuffer, 1);
            usbRead(CMD_I2C_IO | CMD_I2C_IO_END, I2C_M_RD, address, buffer, length);
        }

        @Override
        protected void deviceRead(byte[] buffer, int length) throws IOException {
            checkDataLength(length, buffer.length);
            usbRead(CMD_I2C_IO | CMD_I2C_IO_BEGIN | CMD_I2C_IO_END, I2C_M_RD, address,
                    buffer, length);
        }

        @Override
        protected void deviceWrite(byte[] buffer, int length) throws IOException {
            checkDataLength(length, buffer.length);
            usbWrite(CMD_I2C_IO | CMD_I2C_IO_BEGIN | CMD_I2C_IO_END, 0, address,
                    buffer, length);
        }
    }

    public TinyUsbI2cAdapter(UsbI2cManager manager, UsbDevice usbDevice) {
        super(manager, usbDevice);
    }

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    public TinyUsbI2cDevice getDeviceImpl(int address) {
        return new TinyUsbI2cDevice(address);
    }

    private void usbRead(int cmd, int value, int index, byte[] data, int len) throws IOException {
        controlTransfer(UsbConstants.USB_TYPE_VENDOR | USB_RECIP_INTERFACE | UsbConstants.USB_DIR_IN,
                cmd, value, index, data, len);
    }

    private void usbWrite(int cmd, int value, int index, byte[] data, int len) throws IOException {
        controlTransfer(UsbConstants.USB_TYPE_VENDOR | USB_RECIP_INTERFACE | UsbConstants.USB_DIR_OUT,
                cmd, value, index, data, len);
    }

    public static UsbDeviceIdentifier[] getSupportedUsbDeviceIdentifiers() {
        return new UsbDeviceIdentifier[] {
                new UsbDeviceIdentifier(0x403, 0xc631)
        };
    }
}
