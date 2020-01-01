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
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import com.github.ykc3.android.usbi2c.UsbI2cDevice;
import com.github.ykc3.android.usbi2c.UsbI2cManager;

import java.io.IOException;

import static com.github.ykc3.android.usbi2c.UsbI2cManager.UsbDeviceIdentifier;

/**
 * The Silicon Labs CP2112 chip is a USB HID device which provides an
 * SMBus controller for talking to slave devices.
 * <p>
 * Data Sheet:
 * http://www.silabs.com/Support%20Documents/TechnicalDocs/CP2112.pdf
 * Programming Interface Specification:
 * https://www.silabs.com/documents/public/application-notes/an495-cp2112-interface-specification.pdf
 */
public class UsbI2cCp2112Adapter extends UsbI2cBaseAdapter {
    // HID feature request GET_REPORT code
    private static final int HID_FEATURE_REQUEST_REPORT_GET = 0x01;
    // HID feature request SET_REPORT code
    private static final int HID_FEATURE_REQUEST_REPORT_SET = 0x09;
    // HID feature request INPUT report type
    private static final int HID_FEATURE_REQUEST_REPORT_TYPE_INPUT = 0x100;
    // HID feature request OUTPUT report type
    private static final int HID_FEATURE_REQUEST_REPORT_TYPE_OUTPUT = 0x200;

    // USB Interrupt IN/OUT packet max size
    private static final int MAX_INTERRUPT_TRANSFER_SIZE = 64;

    private final byte[] buffer = new byte[MAX_INTERRUPT_TRANSFER_SIZE];

    // CP2112 report IDs
    private static final byte REPORT_ID_SMBUS_CONFIG = 0x06;
    private static final byte REPORT_ID_DATA_READ_REQUEST = 0x10;
    private static final byte REPORT_ID_DATA_WRITE_READ_REQUEST = 0x11;
    private static final byte REPORT_ID_DATA_READ_FORCE_SEND = 0x12;
    private static final byte REPORT_ID_DATA_READ_RESPONSE = 0x13;
    private static final byte REPORT_ID_DATA_WRITE_REQUEST = 0x14;
    private static final byte REPORT_ID_TRANSFER_STATUS_REQUEST = 0x15;
    private static final byte REPORT_ID_TRANSFER_STATUS_RESPONSE = 0x16;
    private static final byte REPORT_ID_CANCEL_TRANSFER = 0x17;

    // CP2112 SMBus configuration size
    private static final int SMBUS_CONFIG_SIZE = 13;

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

    private UsbEndpoint usbReadEndpoint;
    private UsbEndpoint usbWriteEndpoint;

    class UsbI2cCp2112Device extends UsbI2cBaseDevice {
        UsbI2cCp2112Device(int address) {
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

    public UsbI2cCp2112Adapter(UsbI2cManager manager, UsbDevice usbDevice) {
        super(manager, usbDevice);
    }

    @Override
    public UsbI2cDevice getDevice(int address) {
        return new UsbI2cCp2112Device(address);
    }

    @Override
    protected void openDevice(UsbDevice usbDevice) throws IOException {
        if (usbDevice.getInterfaceCount() == 0) {
            throw new IOException("No interfaces found for device: " + usbDevice);
        }

        UsbInterface usbInterface = usbDevice.getInterface(0);
        if (usbInterface.getEndpointCount() < 2) {
            throw new IOException("No endpoints found for device: " + usbDevice);
        }

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint usbEndpoint = usbInterface.getEndpoint(i);
            if (usbEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (usbEndpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    usbReadEndpoint = usbEndpoint;
                } else {
                    usbWriteEndpoint = usbEndpoint;
                }
            }
        }

        if (usbReadEndpoint == null || usbWriteEndpoint == null) {
            throw new IOException("No read or write HID endpoint found for device: " + usbDevice);
        }

        setupAdapter();
    }

    private void setupAdapter() throws IOException {
        getHidFeatureReport(REPORT_ID_SMBUS_CONFIG, buffer, SMBUS_CONFIG_SIZE + 1); // reserve one byte for Report ID
        // Strip first byte (Report ID)
        System.arraycopy(buffer, 1, buffer, 0, SMBUS_CONFIG_SIZE);
        buffer[SMBUS_CONFIG_SIZE - 1] = 0x01; // Retry Time (number of retries, default 0 - no limit)
        sendHidFeatureReport(REPORT_ID_SMBUS_CONFIG, buffer, SMBUS_CONFIG_SIZE);
        // Drain all stale data reports
        drainPendingDataReports();
    }

    private void checkWriteDataLength(int length) {
        checkDataLength(length, MAX_DATA_WRITE_LENGTH);
    }

    private void checkReadDataLength(int length) {
        checkDataLength(length, MAX_DATA_READ_LENGTH);
    }

    private void checkDataLength(int length, int maxLength) {
        if (length < 1 || length > maxLength) {
            throw new IllegalArgumentException(String.format("Invalid data length: %d (min 1, max %d)",
                    length, maxLength));
        }
    }

    private void writeData(int address, byte[] data, int length) throws IOException {
        checkWriteDataLength(length);
        buffer[0] = REPORT_ID_DATA_WRITE_REQUEST;
        buffer[1] = (byte) (address << 1);
        buffer[2] = (byte) length;
        System.arraycopy(data, 0, buffer, 3, length);
        sendHidDataReport(buffer, length + 3, USB_TIMEOUT_MILLIS);
        waitTransferComplete();
    }

    private void readData(int address, byte[] data, int length) throws IOException {
        checkReadDataLength(length);
        buffer[0] = REPORT_ID_DATA_READ_REQUEST;
        buffer[1] = (byte) (address << 1);
        buffer[2] = (byte) ((length >> 8) & 0xff);
        buffer[3] = (byte) length;
        sendHidDataReport(buffer, 4, USB_TIMEOUT_MILLIS);
        waitTransferComplete();
        readDataFully(data, length);
    }

    private void readRegData(int address, int reg, byte[] data, int length) throws IOException {
        checkReadDataLength(length);
        buffer[0] = REPORT_ID_DATA_WRITE_READ_REQUEST;
        buffer[1] = (byte) (address << 1);
        buffer[2] = (byte) ((length >> 8) & 0xff);
        buffer[3] = (byte) length;
        buffer[4] = 0x01; // number of bytes in target address (register ID)
        buffer[5] = (byte) reg;
        sendHidDataReport(buffer, 6, USB_TIMEOUT_MILLIS);
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
        sendHidDataReport(buffer, 3, USB_TIMEOUT_MILLIS);
    }

    private void sendTransferStatusRequest() throws IOException {
        buffer[0] = REPORT_ID_TRANSFER_STATUS_REQUEST;
        buffer[1] = 0x01;
        sendHidDataReport(buffer, 2, USB_TIMEOUT_MILLIS);
    }

    private void cancelTransfer() throws IOException {
        buffer[0] = REPORT_ID_CANCEL_TRANSFER;
        buffer[1] = 0x01;
        sendHidDataReport(buffer, 2, USB_TIMEOUT_MILLIS);
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

    private void checkReportId(int reportId) {
        if ((reportId & 0xff) != reportId) {
            throw new IllegalArgumentException("Invalid report ID: " + reportId);
        }
    }

    /**
     * Read HID feature report from USB device to data buffer.
     *
     * @param reportId feature report ID
     * @param data data buffer to read report into
     * @param length feature report data length to read
     * @throws IOException in case of I/O error
     */
    private void getHidFeatureReport(int reportId, byte[] data, int length) throws IOException {
        checkReportId(reportId);
        controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_IN
                        | UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
                HID_FEATURE_REQUEST_REPORT_GET, reportId | HID_FEATURE_REQUEST_REPORT_TYPE_OUTPUT,
                0, data, length);
    }

    /**
     * Send HID feature report from data buffer to USB device.
     *
     * @param reportId feature report ID
     * @param data feature report data buffer
     * @param length feature report data length to send
     * @throws IOException in case of I/O error
     */
    private void sendHidFeatureReport(int reportId, byte[] data, int length) throws IOException {
        checkReportId(reportId);
        controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_OUT
                        | UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
                HID_FEATURE_REQUEST_REPORT_SET, reportId | HID_FEATURE_REQUEST_REPORT_TYPE_INPUT,
                0, data, length);
    }

    /**
     * Read HID data report from USB device to data buffer.
     *
     * @param data data buffer to read report into
     * @param timeout read timeout in msecs
     * @return length of read data report
     * @throws IOException in case of data report read error or timeout
     */
    private int getHidDataReport(byte[] data, int timeout) throws IOException {
        int res = usbDeviceConnection.bulkTransfer(usbReadEndpoint, data, data.length, timeout);
        if (res < 0) {
            throw new IOException("Get HID data report error, result: " + res);
        }
        return res;
    }

    /**
     * Send HID data report from data buffer to USB device.
     *
     * @param data data buffer to send report from
     * @param length data report length
     * @param timeout send timeout in msecs
     * @throws IOException in case of data report send error
     */
    private void sendHidDataReport(byte[] data, int length, int timeout) throws IOException {
        int res = usbDeviceConnection.bulkTransfer(usbWriteEndpoint, data, length, timeout);
        if (res < 0) {
            throw new IOException("Send HID data report error, result: " + res);
        }
    }

    public static UsbDeviceIdentifier[] getSupportedUsbDeviceIdentifiers() {
        return new UsbDeviceIdentifier[]{
                new UsbDeviceIdentifier(0x10c4, 0xea90)
        };
    }
}
