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

import com.github.ykc3.android.usbi2c.UsbI2cManager;

import java.io.IOException;

import static com.github.ykc3.android.usbi2c.UsbI2cManager.UsbDeviceIdentifier;

/**
 * The Silicon Labs CP2112 chip is a USB HID device which provides an
 * SMBus controller for talking to slave devices.
 * <p>
 * <a href="https://www.silabs.com/documents/public/data-sheets/cp2112-datasheet.pdf">Data Sheet</a>
 * <p>
 * <a href="https://www.silabs.com/documents/public/application-notes/an495-cp2112-interface-specification.pdf">Programming Interface Specification</a>
 */
public class Cp2112UsbI2cAdapter extends HidUsbI2cAdapter {
    // Adapter name
    public static final String ADAPTER_NAME = "CP2112";

    // CP2112 report IDs
    private static final int REPORT_ID_SMBUS_CONFIG = 0x06;
    private static final int REPORT_ID_DATA_READ_REQUEST = 0x10;
    private static final int REPORT_ID_DATA_WRITE_READ_REQUEST = 0x11;
    private static final int REPORT_ID_DATA_READ_FORCE_SEND = 0x12;
    private static final int REPORT_ID_DATA_READ_RESPONSE = 0x13;
    private static final int REPORT_ID_DATA_WRITE_REQUEST = 0x14;
    private static final int REPORT_ID_TRANSFER_STATUS_REQUEST = 0x15;
    private static final int REPORT_ID_TRANSFER_STATUS_RESPONSE = 0x16;
    private static final int REPORT_ID_CANCEL_TRANSFER = 0x17;

    // CP2112 SMBus configuration size (including ReportID)
    private static final int SMBUS_CONFIG_SIZE = 14;

    // CP2112 SMBus configuration: Clock Speed
    private static final int SMBUS_CONFIG_CLOCK_SPEED_OFFSET = 1;
    // CP2112 SMBus configuration: Retry Time
    private static final int SMBUS_CONFIG_RETRY_TIME_OFFSET = 12;

    // CP2112 max I2C data write length (single write transfer)
    private static final int MAX_DATA_WRITE_LENGTH = 61;

    // CP2112 max I2C data read length (multiple read transfers)
    private static final int MAX_DATA_READ_LENGTH = 512;

    private static final int NUM_DRAIN_DATA_REPORTS_RETRIES = 10;

    // CP2112 max data transfer status reads while waiting for transfer completion
    private static final int NUM_TRANSFER_STATUS_RETRIES = 10;

    // CP2112 transfer status codes
    private static final int TRANSFER_STATUS_IDLE = 0x00;
    private static final int TRANSFER_STATUS_BUSY = 0x01;
    private static final int TRANSFER_STATUS_COMPLETE = 0x02;
    private static final int TRANSFER_STATUS_ERROR = 0x03;

    // CP2112 min clock speed
    private static final int MIN_CLOCK_SPEED = 10000;
    // CP2112 max clock speed
    private static final int MAX_CLOCK_SPEED = CLOCK_SPEED_HIGH;

    class Cp2112UsbI2cDevice extends BaseUsbI2cDevice {
        Cp2112UsbI2cDevice(int address) {
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

    public Cp2112UsbI2cAdapter(UsbI2cManager manager, UsbDevice usbDevice) {
        super(manager, usbDevice);
    }

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    protected BaseUsbI2cDevice getDeviceImpl(int address) {
        return new Cp2112UsbI2cDevice(address);
    }

    @Override
    protected void init(UsbDevice usbDevice) throws IOException {
        super.init(usbDevice);
        // Drain all stale data reports
        drainPendingDataReports();
    }

    protected void configure() throws IOException {
        // Get current config
        getHidFeatureReport(REPORT_ID_SMBUS_CONFIG, buffer, SMBUS_CONFIG_SIZE);

        // Clock Speed (in Hertz, default 0x000186A0 - 100 kHz)
        int clockSpeed = getClockSpeed();
        buffer[SMBUS_CONFIG_CLOCK_SPEED_OFFSET]     = (byte) (clockSpeed >> 24);
        buffer[SMBUS_CONFIG_CLOCK_SPEED_OFFSET + 1] = (byte) (clockSpeed >> 16);
        buffer[SMBUS_CONFIG_CLOCK_SPEED_OFFSET + 2] = (byte) (clockSpeed >> 8);
        buffer[SMBUS_CONFIG_CLOCK_SPEED_OFFSET + 3] = (byte) clockSpeed;

        // Retry Time (number of retries, default 0 - no limit)
        buffer[SMBUS_CONFIG_RETRY_TIME_OFFSET]     = 0x00;
        buffer[SMBUS_CONFIG_RETRY_TIME_OFFSET + 1] = 0x01;

        setHidFeatureReport(buffer, SMBUS_CONFIG_SIZE);
    }

    @Override
    public boolean isClockSpeedSupported(int speed) {
        return (speed >= MIN_CLOCK_SPEED && speed <= MAX_CLOCK_SPEED);
    }

    private void checkWriteDataLength(int length, int bufferLength) {
        checkDataLength(length, Math.min(MAX_DATA_WRITE_LENGTH, bufferLength));
    }

    private void checkReadDataLength(int length, int bufferLength) {
        checkDataLength(length, Math.min(MAX_DATA_READ_LENGTH, bufferLength));
    }

    private void writeData(int address, byte[] data, int length) throws IOException {
        checkWriteDataLength(length, data.length);
        buffer[0] = REPORT_ID_DATA_WRITE_REQUEST;
        buffer[1] = getAddressByte(address, false);
        buffer[2] = (byte) length;
        System.arraycopy(data, 0, buffer, 3, length);
        sendHidDataReport(buffer, length + 3);
        waitTransferComplete();
    }

    private void readData(int address, byte[] data, int length) throws IOException {
        checkReadDataLength(length, data.length);
        buffer[0] = REPORT_ID_DATA_READ_REQUEST;
        buffer[1] = getAddressByte(address, false); // read bit is not required
        buffer[2] = (byte) (length >> 8);
        buffer[3] = (byte) length;
        sendHidDataReport(buffer, 4);
        waitTransferComplete();
        readDataFully(data, length);
    }

    private void readRegData(int address, int reg, byte[] data, int length) throws IOException {
        checkReadDataLength(length, data.length);
        buffer[0] = REPORT_ID_DATA_WRITE_READ_REQUEST;
        buffer[1] = getAddressByte(address, false); // read bit is not required
        buffer[2] = (byte) (length >> 8);
        buffer[3] = (byte) length;
        buffer[4] = 0x01; // number of bytes in target address (register ID)
        buffer[5] = (byte) reg;
        sendHidDataReport(buffer, 6);
        waitTransferComplete();
        readDataFully(data, length);
    }

    private void readDataFully(byte[] data, int length) throws IOException {
        int totalReadLen = 0;
        while (totalReadLen < length) {
            sendForceReadDataRequest(length - totalReadLen);

            getHidDataReport(buffer, USB_TIMEOUT_MILLIS);

            if (buffer[0] != REPORT_ID_DATA_READ_RESPONSE) {
                throw new IOException(String.format("Unexpected data read report ID: 0x%02x",
                        buffer[0]));
            }

            if (buffer[1] == TRANSFER_STATUS_ERROR) {
                throw new IOException(String.format("Data read status error, condition: 0x%02x",
                        buffer[2]));
            }

            int lastReadLen = buffer[2] & 0xff;
            if (lastReadLen > length - totalReadLen) {
                throw new IOException(String.format("Too many data read: " +
                        "%d byte(s), expected: %d byte(s)", lastReadLen, length - totalReadLen));
            }

            System.arraycopy(buffer, 3, data, totalReadLen, lastReadLen);

            totalReadLen += lastReadLen;
        }
    }

    private void waitTransferComplete() throws IOException {
        int tryNum = 1;
        while (tryNum++ <= NUM_TRANSFER_STATUS_RETRIES) {
            sendTransferStatusRequest();

            getHidDataReport(buffer, USB_TIMEOUT_MILLIS);

            if (buffer[0] != REPORT_ID_TRANSFER_STATUS_RESPONSE) {
                throw new IOException(String.format("Unexpected transfer status report ID: 0x%02x",
                        buffer[0]));
            }

            switch (buffer[1]) {
                case TRANSFER_STATUS_BUSY:
                    continue;
                case TRANSFER_STATUS_COMPLETE:
                    return;
                default:
                    throw new IOException(String.format("Invalid transfer status: 0x%02x",
                            buffer[1]));
            }
        }
        // Retries limit was reached and TRANSFER_STATUS_COMPLETE status is not reached
        cancelTransfer();
        throw new IOException("Transfer retries limit reached");
    }

    private void sendForceReadDataRequest(int length) throws IOException {
        buffer[0] = REPORT_ID_DATA_READ_FORCE_SEND;
        buffer[1] = (byte) ((length >> 8) & 0xff);
        buffer[2] = (byte) length;
        sendHidDataReport(buffer, 3);
    }

    private void sendTransferStatusRequest() throws IOException {
        buffer[0] = REPORT_ID_TRANSFER_STATUS_REQUEST;
        buffer[1] = 0x01;
        sendHidDataReport(buffer, 2);
    }

    private void cancelTransfer() throws IOException {
        buffer[0] = REPORT_ID_CANCEL_TRANSFER;
        buffer[1] = 0x01;
        sendHidDataReport(buffer, 2);
    }

    private void drainPendingDataReports() throws IOException {
        int tryNum = 1;
        while (tryNum++ <= NUM_DRAIN_DATA_REPORTS_RETRIES) {
            try {
                getHidDataReport(buffer, 5);
            } catch (IOException e) {
                break;
            }
        }
        if (tryNum >= NUM_DRAIN_DATA_REPORTS_RETRIES) {
            throw new IOException("Can't drain pending data reports");
        }
        cancelTransfer();
    }

    public static UsbDeviceIdentifier[] getSupportedUsbDeviceIdentifiers() {
        return new UsbDeviceIdentifier[] {
                new UsbDeviceIdentifier(0x10c4, 0xea90)
        };
    }
}
