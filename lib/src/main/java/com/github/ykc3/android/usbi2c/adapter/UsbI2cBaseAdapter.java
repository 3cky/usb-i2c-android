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

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;

import com.github.ykc3.android.usbi2c.UsbI2cAdapter;
import com.github.ykc3.android.usbi2c.UsbI2cDevice;
import com.github.ykc3.android.usbi2c.UsbI2cManager;

import java.io.IOException;

abstract class UsbI2cBaseAdapter implements UsbI2cAdapter {
    // Linux kernel flags
    static final int I2C_M_RD = 0x01; // read data, from slave to master

    private static final int USB_TIMEOUT_MILLIS = 2000;

    private final UsbI2cManager i2cManager;
    private final UsbDevice usbDevice;

    private UsbDeviceConnection usbDeviceConnection;

    protected abstract class UsbI2cBaseDevice implements UsbI2cDevice {
        final int address;

        UsbI2cBaseDevice(int address) {
            this.address = (address & 0x7f);
        }

        @Override
        public int getAddress() {
            return address;
        }

        @Override
        public byte readRegByte(int reg) throws IOException {
            byte[] buffer = new byte[1];
            readRegBuffer(reg, buffer, buffer.length);
            return buffer[0];
        }

        @Override
        public short readRegWord(int reg) throws IOException {
            byte[] buffer = new byte[2];
            readRegBuffer(reg, buffer, buffer.length);
            return (short) ((buffer[0] & 0xFF) | (buffer[1] << 8));
        }

        @Override
        public void writeRegByte(int reg, byte data) throws IOException {
            write(new byte[]{(byte) reg, data});
        }

        @Override
        public void writeRegWord(int reg, short data) throws IOException {
            write(new byte[]{(byte) reg, (byte) data, (byte) (data >>> 8)});
        }

        @Override
        public void writeRegBuffer(int reg, byte[] buffer, int length) throws IOException {
            byte[] data = new byte[length + 1];
            data[0] = (byte) reg;
            System.arraycopy(buffer, 0, data, 1, length);
            write(data);
        }

        protected void write(byte[] data) throws IOException {
            write(data, data.length);
        }
    }

    UsbI2cBaseAdapter(UsbI2cManager i2cManager, UsbDevice usbDevice) {
        this.i2cManager = i2cManager;
        this.usbDevice = usbDevice;
    }

    @Override
    public String getId() {
        return usbDevice.getDeviceName();
    }

    @Override
    public void open() throws IOException {
        if (usbDeviceConnection != null) {
            throw new IllegalStateException("Already opened");
        }

        usbDeviceConnection = i2cManager.getUsbManager().openDevice(usbDevice);

        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface usbDeviceInterface = usbDevice.getInterface(i);
            if (!usbDeviceConnection.claimInterface(usbDeviceInterface, true)) {
                throw new IOException("Can't claim interface");
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (usbDeviceConnection != null) {
            for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                UsbInterface usbDeviceInterface = usbDevice.getInterface(i);
                usbDeviceConnection.releaseInterface(usbDeviceInterface);
            }
            usbDeviceConnection.close();
        }
    }

    @Override
    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    final void controlTransfer(int requestType, int request, int value,
                               int index, byte[] data, int length) throws IOException {
        int result = usbDeviceConnection.controlTransfer(requestType, request, value,
                index, data, length, USB_TIMEOUT_MILLIS);
        if (result != length) {
            throw new IOException(String.format("controlTransfer(requestType: 0x%x, " +
                            "request: 0x%x, value: 0x%x, index: 0x%x, length: %d) failed: %d",
                    requestType, request, value, index, length, result));
        }
    }
}
