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

package com.github.ykc3.android.usbi2c.adapter;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import com.github.ykc3.android.usbi2c.UsbI2cManager;

import static com.github.ykc3.android.usbi2c.UsbI2cManager.UsbDeviceIdentifier;

import java.io.IOException;

/**
 * CH341 is a USB bus convert chip, providing UART, printer port, parallel and synchronous serial with
 * 2-wire or 4-wire through USB bus.
 * <p>
 * <a href="https://www.anok.ceti.pl/download/ch341ds1.pdf">Data Sheet</a>
 */
public class Ch341UsbI2cAdapter extends BaseUsbI2cAdapter {
    // Adapter name
    public static final String ADAPTER_NAME = "CH341";

    private static final int CH341_I2C_LOW_SPEED = 0;      // low speed - 20kHz
    private static final int CH341_I2C_STANDARD_SPEED = 1; // standard speed - 100kHz
    private static final int CH341_I2C_FAST_SPEED = 2;     // fast speed - 400kHz
    private static final int CH341_I2C_HIGH_SPEED = 3;     // high speed - 750kHz

    private static final int CH341_CMD_I2C_STREAM = 0xAA;

    private static final int CH341_CMD_I2C_STM_STA = 0x74;
    private static final int CH341_CMD_I2C_STM_STO = 0x75;
    private static final int CH341_CMD_I2C_STM_OUT = 0x80;
    private static final int CH341_CMD_I2C_STM_IN = 0xC0;
    private static final int CH341_CMD_I2C_STM_SET = 0x60;
    private static final int CH341_CMD_I2C_STM_END = 0x00;

    // CH341 max transfer size
    private static final int MAX_TRANSFER_SIZE = 32;

    private final byte[] writeBuffer = new byte[MAX_TRANSFER_SIZE];
    private final byte[] readBuffer = new byte[MAX_TRANSFER_SIZE];

    class Ch341UsbI2cDevice extends BaseUsbI2cDevice {
        Ch341UsbI2cDevice(int address) {
            super(address);
        }

        @Override
        protected void deviceReadReg(int reg, byte[] buffer, int length) throws IOException {
            readRegData(address, reg, buffer, length);
        }

        @Override
        protected void deviceRead(byte[] buffer, int length) throws IOException {
            readData(address, buffer, length);
        }

        @Override
        protected void deviceWrite(byte[] buffer, int length) throws IOException {
            writeData(address, buffer, length);
        }
    }


    public Ch341UsbI2cAdapter(UsbI2cManager i2cManager, UsbDevice usbDevice) {
        super(i2cManager, usbDevice);
    }

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    protected BaseUsbI2cDevice getDeviceImpl(int address) {
        return new Ch341UsbI2cDevice(address);
    }

    @Override
    protected void init(UsbDevice usbDevice) throws IOException {
        if (usbDevice.getInterfaceCount() == 0) {
            throw new IOException("No interfaces found for device: " + usbDevice);
        }

        UsbInterface usbInterface = usbDevice.getInterface(0);
        if (usbInterface.getEndpointCount() < 2) {
            throw new IOException("No endpoints found for device: " + usbDevice);
        }

        UsbEndpoint usbReadEndpoint = null, usbWriteEndpoint = null;
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint usbEndpoint = usbInterface.getEndpoint(i);
            if (usbEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    usbReadEndpoint = usbEndpoint;
                } else {
                    usbWriteEndpoint = usbEndpoint;
                }
            }
        }
        if (usbReadEndpoint == null || usbWriteEndpoint == null) {
            throw new IOException("No read or write bulk endpoint found for device: " + usbDevice);
        }
        setBulkEndpoints(usbReadEndpoint, usbWriteEndpoint);

        configure();
    }

    protected int getClockSpeedConstant(int speed) {
        switch (speed) {
            case 20000:
                return CH341_I2C_LOW_SPEED;
            case CLOCK_SPEED_STANDARD:
                return CH341_I2C_STANDARD_SPEED;
            case CLOCK_SPEED_FAST:
                return CH341_I2C_FAST_SPEED;
            case 750000:
                return CH341_I2C_HIGH_SPEED;
        }

        return -1;
    }

    @Override
    public boolean isClockSpeedSupported(int speed) {
        return (getClockSpeedConstant(speed) >= 0);
    }

    protected void configure() throws IOException {
        writeBuffer[0] = (byte) CH341_CMD_I2C_STREAM;
        writeBuffer[1] = (byte) (CH341_CMD_I2C_STM_SET | getClockSpeedConstant(getClockSpeed()));
        writeBuffer[2] = CH341_CMD_I2C_STM_END;
        writeBulkData(writeBuffer, 3);
    }

    protected void checkDataLength(int length, int bufferDataLength) {
        super.checkDataLength(length, Math.min(MAX_TRANSFER_SIZE - 6, bufferDataLength));
    }

    private void writeData(int address, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        int i = 0;
        writeBuffer[i++] = (byte) CH341_CMD_I2C_STREAM;
        writeBuffer[i++] = CH341_CMD_I2C_STM_STA; // START condition
        writeBuffer[i++] = (byte) (CH341_CMD_I2C_STM_OUT | (length + 1)); // data length + 1
        writeBuffer[i++] = getAddressByte(address, false);
        System.arraycopy(data, 0, writeBuffer, i, length);
        i += length;
        writeBuffer[i++] = CH341_CMD_I2C_STM_STO; // STOP condition
        writeBuffer[i++] = CH341_CMD_I2C_STM_END; // end of transaction
        writeBulkData(writeBuffer, i);
    }

    private void readData(int address, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        checkDevicePresence(address); // to avoid weird phantom devices in scan results
        int i = 0;
        writeBuffer[i++] = (byte) CH341_CMD_I2C_STREAM;
        writeBuffer[i++] = CH341_CMD_I2C_STM_STA; // START condition
        writeBuffer[i++] = (byte) (CH341_CMD_I2C_STM_OUT | 1); // zero data length + 1
        writeBuffer[i++] = getAddressByte(address, true);
        if (length > 0) {
            for (int j = 0; j < length - 1; j++) {
                writeBuffer[i++] = (byte) (CH341_CMD_I2C_STM_IN | 1);
            }
            writeBuffer[i++] = (byte) CH341_CMD_I2C_STM_IN;
        }
        writeBuffer[i++] = CH341_CMD_I2C_STM_STO; // STOP condition
        writeBuffer[i++] = CH341_CMD_I2C_STM_END; // end of transaction
        transferBulkData(writeBuffer, i, data, length);
    }

    private void readRegData(int address, int reg, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        // Write register number
        int i = 0;
        writeBuffer[i++] = (byte) CH341_CMD_I2C_STREAM;
        writeBuffer[i++] = CH341_CMD_I2C_STM_STA; // START condition
        writeBuffer[i++] = (byte) (CH341_CMD_I2C_STM_OUT | 2); // reg data length + 1
        writeBuffer[i++] = getAddressByte(address, false);
        writeBuffer[i++] = (byte) reg;
        writeBulkData(writeBuffer, i);
        // Read register data
        i = 0;
        writeBuffer[i++]= (byte) CH341_CMD_I2C_STREAM;
        writeBuffer[i++] = CH341_CMD_I2C_STM_STA; // START condition
        writeBuffer[i++] = (byte) (CH341_CMD_I2C_STM_OUT | 1); // zero data length + 1
        writeBuffer[i++] = getAddressByte(address, true);
        if (length > 0) {
            for (int j = 0; j < length - 1; j++) {
                writeBuffer[i++] = (byte) (CH341_CMD_I2C_STM_IN | 1);
            }
            writeBuffer[i++] = (byte) CH341_CMD_I2C_STM_IN;
        }
        writeBuffer[i++] = CH341_CMD_I2C_STM_STO; // STOP condition
        writeBuffer[i++] = CH341_CMD_I2C_STM_END; // end of transaction
        transferBulkData(writeBuffer, i, data, length);
    }

    private void checkDevicePresence(int address) throws IOException {
        int i = 0;
        writeBuffer[i++] = (byte) CH341_CMD_I2C_STREAM;
        writeBuffer[i++] = CH341_CMD_I2C_STM_STA; // START condition
        writeBuffer[i++] = (byte) CH341_CMD_I2C_STM_OUT;
        writeBuffer[i++] = getAddressByte(address, true);
        writeBuffer[i++] = CH341_CMD_I2C_STM_STO; // STOP condition
        writeBuffer[i++] = CH341_CMD_I2C_STM_END; // end of transaction
        int res = transferBulkData(writeBuffer, i, writeBuffer, 1);
        if (res <= 0 || (writeBuffer[0] & 0x80) != 0) {
            throw new IOException(String.format("No device present at address 0x%02x", address));
        }
    }

    /**
     * Write request and read response, if needed.
     *
     * @param writeData data buffer to write data
     * @param writeLength data length to write
     * @param readData data buffer to read data
     * @param readLength data length to read (can be zero)
     * @return actual length of read data
     * @throws IOException in case of data read/write error or timeout
     */
    private int transferBulkData(byte[] writeData, int writeLength,
                                 byte[] readData, int readLength) throws IOException {
        writeBulkData(writeData, writeLength);
        if (readLength > 0) {
            readLength = readBulkData(readBuffer, MAX_TRANSFER_SIZE);
            System.arraycopy(readBuffer, 0, readData, 0, readLength);
        }
        return readLength;
    }

    private int readBulkData(byte[] data, int length) throws IOException {
        return bulkRead(data, 0, length, USB_TIMEOUT_MILLIS);
    }

    private void writeBulkData(byte[] data, int length) throws IOException {
        bulkWrite(data, length, USB_TIMEOUT_MILLIS);
    }

    public static UsbDeviceIdentifier[] getSupportedUsbDeviceIdentifiers() {
        return new UsbDeviceIdentifier[] {
                new UsbDeviceIdentifier(0x1a86, 0x5512)
        };
    }
}
