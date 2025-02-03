/*
 * Copyright (c) 2025 Victor Antonovich <v.antonovich@gmail.com>
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

import com.github.ykc3.android.usbi2c.UsbI2cManager;

import static com.github.ykc3.android.usbi2c.UsbI2cManager.UsbDeviceIdentifier;

import java.io.IOException;

/**
 * The FT260 HID-class USB to UART/I2C bridge IC.
 * <p>
 * <a href="https://www.ftdichip.com/Support/Documents/DataSheets/ICs/DS_FT260.pdf">Data Sheet</a>
 * <p>
 * <a href="https://ftdichip.com/wp-content/uploads/2020/08/AN_394_User_Guide_for_FT260.pdf">User Guide</a>
 */
public class Ft260UsbI2cAdapter extends HidUsbI2cAdapter {
    // Adapter name
    private static final String ADAPTER_NAME = "FT260";

    // FT260 report IDs
    private static final int REPORT_ID_CHIP_VERSION = 0xA0;
    private static final int REPORT_ID_SYSTEM_SETTINGS = 0xA1;
    private static final int REPORT_ID_I2C_STATUS = 0xC0;
    private static final int REPORT_ID_I2C_READ_REQUEST = 0xC2;
    private static final int REPORT_ID_I2C_DATA_MIN = 0xD0;
    private static final int REPORT_ID_I2C_DATA_MAX = 0xDE;

    // FT260 requests
    private static final int REQUEST_I2C_RESET = 0x20;
    private static final int REQUEST_I2C_SET_CLOCK_SPEED = 0x22;

    // FT260 report sizes (in bytes, including ReportID)
    private static final int REPORT_SIZE_CHIP_VERSION = 13;
    private static final int REPORT_SIZE_SYSTEM_STATUS = 25;
    private static final int REPORT_SIZE_I2C_STATUS = 5;

    // FT260 chip version report field offsets
    private static final int CHIP_VERSION_PART_NUMBER_MAJOR_OFFSET = 1;
    private static final int CHIP_VERSION_PART_NUMBER_MINOR_OFFSET = 2;

    // FT260 system status report field offsets
    private static final int SYSTEM_STATUS_CHIP_MODE_OFFSET = 1;

    // FT260 I2C status report field offsets
    private static final int I2C_STATUS_BUS_STATUS_OFFSET = 1;

    // FT260 I2C read request flags
    private static final int I2C_READ_REQUEST_FLAG_NONE = 0x00;
    private static final int I2C_READ_REQUEST_FLAG_START = 0x02;
    private static final int I2C_READ_REQUEST_FLAG_REPEATED_START = 0x03;
    private static final int I2C_READ_REQUEST_FLAG_STOP = 0x04;
    private static final int I2C_READ_REQUEST_FLAG_START_STOP = 0x06;
    private static final int I2C_READ_REQUEST_FLAG_START_STOP_REPEATED = 0x07;

    // FT260 max data transfer status reads while waiting for transfer completion
    private static final int CHECK_TRANSFER_STATUS_MAX_RETRIES = 10;

    // FT260 I2C status flags
    private static final int I2C_STATUS_FLAG_CONTROLLER_BUSY = 0x01;
    private static final int I2C_STATUS_FLAG_ERROR = 0x02;

    // FT260 min clock speed (60 kHz)
    private static final int MIN_CLOCK_SPEED = 60000;
    // FT260 max clock speed (3.4 MHz)
    private static final int MAX_CLOCK_SPEED = CLOCK_SPEED_HIGH;

    // FT260 max data transfer size
    private static final int MAX_DATA_TRANSFER_SIZE = 60;

    class Ft260UsbI2cDevice extends BaseUsbI2cDevice {
        Ft260UsbI2cDevice(int address) {
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

    public Ft260UsbI2cAdapter(UsbI2cManager manager, UsbDevice device) {
        super(manager, device);
    }

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }


    @Override
    protected BaseUsbI2cDevice getDeviceImpl(int address) {
        return new Ft260UsbI2cDevice(address);
    }

    @Override
    protected void init(UsbDevice usbDevice) throws IOException {
        probe();
        super.init(usbDevice);
    }

    private void probe() throws IOException {
        // Check chip code
        getHidFeatureReport(REPORT_ID_CHIP_VERSION, buffer, REPORT_SIZE_CHIP_VERSION);
        if (buffer[CHIP_VERSION_PART_NUMBER_MAJOR_OFFSET] != 0x02
                || buffer[CHIP_VERSION_PART_NUMBER_MINOR_OFFSET] != 0x60) {
            throw new IOException(String.format("Unknown chip code: %02x%02x",
                    buffer[CHIP_VERSION_PART_NUMBER_MAJOR_OFFSET],
                    buffer[CHIP_VERSION_PART_NUMBER_MINOR_OFFSET]));
        }
        // Check I2C is enabled
        getHidFeatureReport(REPORT_ID_SYSTEM_SETTINGS, buffer, REPORT_SIZE_SYSTEM_STATUS);
        // I2C is enabled if DCNF0/DCNF1 pins are both set to 0 or DCNF0 is set to 1
        if (buffer[SYSTEM_STATUS_CHIP_MODE_OFFSET] != 0
                && (buffer[SYSTEM_STATUS_CHIP_MODE_OFFSET] & 0x01) == 0) {
            throw new IOException("I2C interface is not enabled by FT260 DCNF0/DCNF1 pins");
        }
    }

    @Override
    public boolean isClockSpeedSupported(int speed) {
        return (speed >= MIN_CLOCK_SPEED && speed <= MAX_CLOCK_SPEED);
    }

    @Override
    protected void configure() throws IOException {
        // Reset I2C master
        resetI2cMaster();
        // Set clock speed (in kHz)
        int clockSpeedKHz = getClockSpeed() / 1000;
        int i = 0;
        buffer[i++] = (byte) REPORT_ID_SYSTEM_SETTINGS;
        buffer[i++] = REQUEST_I2C_SET_CLOCK_SPEED;
        buffer[i++] = (byte) clockSpeedKHz;
        buffer[i++] = (byte) (clockSpeedKHz >> 8);
        setHidFeatureReport(buffer, i);
    }

    private void writeData(int address, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        // Write data by chunks up to MAX_DATA_TRANSFER_SIZE
        int sentBytes = 0;
        while (sentBytes < length) {
            int chunkLength = Math.min(length - sentBytes, MAX_DATA_TRANSFER_SIZE);
            int reportId = REPORT_ID_I2C_DATA_MIN + (chunkLength - 1) / 4;
            // Send write request
            int flag = I2C_READ_REQUEST_FLAG_NONE;
            if (sentBytes > 0) {
                if (sentBytes + chunkLength == length) {
                    flag = I2C_READ_REQUEST_FLAG_STOP;
                }
            } else if (chunkLength < length) {
                flag = I2C_READ_REQUEST_FLAG_START;
            } else {
                flag = I2C_READ_REQUEST_FLAG_START_STOP;
            }
            int i = 0;
            buffer[i++] = (byte) reportId;
            buffer[i++] = (byte) address;
            buffer[i++] = (byte) flag;
            buffer[i++] = (byte) length;
            System.arraycopy(data, sentBytes, buffer, i, chunkLength);
            sendHidDataReport(buffer, i + chunkLength);
            sentBytes += chunkLength;
            // Check transfer status
            checkTransferStatus();
        }
    }

    private void readData(int address, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        // Send read request
        int i = 0;
        buffer[i++] = (byte) REPORT_ID_I2C_READ_REQUEST;
        buffer[i++] = (byte) address;
        buffer[i++] = (byte) I2C_READ_REQUEST_FLAG_START_STOP;
        buffer[i++] = (byte) length;
        buffer[i++] = (byte) (length >> 8);
        sendHidDataReport(buffer, i);
        // Read response
        readDataFully(data, length);
        // Check transfer status
        checkTransferStatus();
    }

    private void readRegData(int address, int reg, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        // Send register address write request
        int i = 0;
        buffer[i++] = (byte) REPORT_ID_I2C_DATA_MIN;
        buffer[i++] = (byte) address;
        buffer[i++] = (byte) I2C_READ_REQUEST_FLAG_START;
        buffer[i++] = 0x01; // number of bytes in target address (register ID)
        buffer[i++] = (byte) reg;
        sendHidDataReport(buffer, i);
        // Check register address write transfer status
        checkTransferStatus();
        // Send data read request
        i = 0;
        buffer[i++] = (byte) REPORT_ID_I2C_READ_REQUEST;
        buffer[i++] = (byte) address;
        buffer[i++] = (byte) I2C_READ_REQUEST_FLAG_START_STOP_REPEATED;
        buffer[i++] = (byte) length;
        buffer[i++] = (byte) (length >> 8);
        sendHidDataReport(buffer, i);
        // Register data read response
        readDataFully(data, length);
        // Check data read transfer status
        checkTransferStatus();
    }

    private void readDataFully(byte[] data, int length) throws IOException {
        int readLength = 0;

        while (readLength < length) {
            getHidDataReport(buffer, USB_TIMEOUT_MILLIS);

            int reportId = buffer[0] & 0xff;
            if (reportId < REPORT_ID_I2C_DATA_MIN || reportId > REPORT_ID_I2C_DATA_MAX) {
                throw new IOException(String.format("Unexpected data read report ID: 0x%02x",
                        reportId));
            }

            int bufferLength = buffer[1] & 0xff;
            if (bufferLength > length - readLength) {
                throw new IOException(String.format("Too long data to read: " +
                        "%d byte(s), expected: %d byte(s)", bufferLength, length - readLength));
            }

            System.arraycopy(buffer, 2, data, readLength, bufferLength);

            readLength += bufferLength;
        }
    }

    private void checkTransferStatus() throws IOException {
        int tryNum = 1;

        while (tryNum++ <= CHECK_TRANSFER_STATUS_MAX_RETRIES) {
            int status = getTransferStatus();

            if ((status & I2C_STATUS_FLAG_CONTROLLER_BUSY) != 0) {
                continue;
            }

            if ((status & I2C_STATUS_FLAG_ERROR) != 0) {
                throw new IOException(String.format("Transfer error, status: 0x%02x", status));
            }

            // Transfer is complete and no error was occurred
            return;
        }

        // Can't check transfer status because of timeout
        resetI2cMaster();
        throw new IOException("Can't check transfer status: reties limit was reached");
    }

    private int getTransferStatus() throws IOException {
        getHidFeatureReport(REPORT_ID_I2C_STATUS, buffer, REPORT_SIZE_I2C_STATUS);
        return buffer[I2C_STATUS_BUS_STATUS_OFFSET];
    }

    private void resetI2cMaster() throws IOException {
        int i = 0;
        buffer[i++] = (byte) REPORT_ID_SYSTEM_SETTINGS;
        buffer[i++] = REQUEST_I2C_RESET;
        setHidFeatureReport(buffer, i);
    }

    public static UsbDeviceIdentifier[] getSupportedUsbDeviceIdentifiers() {
        return new UsbDeviceIdentifier[] {
                new UsbDeviceIdentifier(0x403, 0x6030)
        };
    }
}
