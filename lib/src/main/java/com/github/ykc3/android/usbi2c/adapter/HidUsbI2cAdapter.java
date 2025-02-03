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

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import com.github.ykc3.android.usbi2c.UsbI2cManager;

import java.io.IOException;

/**
 * Base class for HID USB I2C adapters.
 */
abstract class HidUsbI2cAdapter extends BaseUsbI2cAdapter {
    // HID feature request GET_REPORT value
    private static final int HID_FEATURE_REQUEST_REPORT_GET = 0x01;
    // HID feature request SET_REPORT value
    private static final int HID_FEATURE_REQUEST_REPORT_SET = 0x09;
    // HID feature request report type
    private static final int HID_FEATURE_REQUEST_REPORT_TYPE = 0x03;

    // Max HID transfer size, in bytes
    protected static final int MAX_HID_TRANSFER_SIZE = 64;

    // HID data buffer
    protected final byte[] buffer = new byte[MAX_HID_TRANSFER_SIZE];

    public HidUsbI2cAdapter(UsbI2cManager manager, UsbDevice device) {
        super(manager, device);
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
        setBulkEndpoints(usbReadEndpoint, usbWriteEndpoint);

        configure();
    }

    private void checkReportId(int reportId) {
        if ((reportId & 0xff) != reportId) {
            throw new IllegalArgumentException("Invalid report ID: " + reportId);
        }
    }

    /**
     * Get HID feature report from USB device to data buffer.
     *
     * @param reportId feature report ID
     * @param data data buffer to read report into
     * @param length feature report data length to read
     * @throws IOException in case of I/O error
     */
    protected void getHidFeatureReport(int reportId, byte[] data, int length) throws IOException {
        checkReportId(reportId);
        controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_IN
                        | UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
                HID_FEATURE_REQUEST_REPORT_GET, (HID_FEATURE_REQUEST_REPORT_TYPE << 8) | reportId,
                0, data, length);
    }

    /**
     * Set HID feature report from data buffer to USB device.
     *
     * @param data     feature report data buffer
     * @param length   feature report data length to send
     * @throws IOException in case of I/O error
     */
    protected void setHidFeatureReport(byte[] data, int length) throws IOException {
        int reportId = data[0] & 0xff;
        controlTransfer(UsbConstants.USB_TYPE_CLASS | UsbConstants.USB_DIR_OUT
                        | UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
                HID_FEATURE_REQUEST_REPORT_SET, (HID_FEATURE_REQUEST_REPORT_TYPE << 8) | reportId,
                0, data, length);
    }

    /**
     * Read HID data report from USB device to data buffer.
     *
     * @param data data buffer to read report into
     * @param timeout read timeout in milliseconds
     * @throws IOException in case of data report read error or timeout
     */
    protected void getHidDataReport(byte[] data, int timeout) throws IOException {
        bulkRead(data, 0, data.length, timeout);
    }

    /**
     * Write HID data report from data buffer to USB device.
     *
     * @param data   data buffer to send report from
     * @param length data report length
     * @throws IOException in case of data report send error
     */
    protected void sendHidDataReport(byte[] data, int length) throws IOException {
        bulkWrite(data, length, BaseUsbI2cAdapter.USB_TIMEOUT_MILLIS);
    }
}
