/*
 * Copyright (c) 2021 Victor Antonovich <v.antonovich@gmail.com>
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
import android.hardware.usb.UsbInterface;

import com.github.ykc3.android.usbi2c.UsbI2cManager;

import java.io.IOException;

import static com.github.ykc3.android.usbi2c.UsbI2cManager.UsbDeviceIdentifier;

/**
 * The FT232H is a single channel USB 2.0 Hi-Speed (480Mb/s) to UART/FIFO IC.
 * The FT232H has the Multi-Protocol Synchronous Serial Engine (MPSSE) to simplify
 * synchronous serial protocol (USB to JTAG, I2C, SPI (MASTER) or bit-bang) design.
 * <p>
 * <a href="http://www.ftdichip.com/Support/Documents/DataSheets/ICs/DS_FT232H.pdf">Data Sheet</a>
 * <p>
 * <a href="http://www.ftdichip.com/Support/Documents/AppNotes/AN_135_MPSSE_Basics.pdf">MPSSE Basics</a>
 * <p>
 * <a href="http://www.ftdichip.com/Support/Documents/AppNotes/AN_108_Command_Processor_for_MPSSE_and_MCU_Host_Bus_Emulation_Modes.pdf">Command Processor for MPSSE and MCU Host Bus Emulation Modes</a>
 * <p>
 * <a href="https://ftdichip.com/wp-content/uploads/2020/08/AN_255_USB-to-I2C-Example-using-the-FT232H-and-FT201X-devices-1.pdf">USB to I2C Example using the FT232H and FT201X devices</a>
 * <p>
 */
public class Ft232hUsbI2cAdapter extends BaseUsbI2cAdapter {
    // Adapter name
    public static final String ADAPTER_NAME = "FT232H";

    // Nanoseconds in one millisecond
    private static final long NANOS_IN_MS = 1000000L;

    // FT232H control requests
    private static final int SIO_RESET_REQUEST = 0x00;
    private static final int SIO_SET_EVENT_CHAR_REQUEST = 0x06;
    private static final int SIO_SET_ERROR_CHAR_REQUEST = 0x07;
    private static final int SIO_SET_LATENCY_TIMER_REQUEST = 0x09;
    private static final int SIO_SET_BITMODE_REQUEST = 0x0B;

    // FT232H control request parameters
    private static final int SIO_RESET_SIO = 0;
    private static final int SIO_RESET_PURGE_RX = 1;
    private static final int SIO_RESET_PURGE_TX = 2;

    // FT232H bit modes
    private static final int BITMODE_RESET = 0x00;
    private static final int BITMODE_MPSSE = 0x02;

    // FT232H AD port I2C lines bit masks
    private static final int I2C_SCL_BIT   = 0x01;  // AD0
    private static final int I2C_SDA_O_BIT = 0x02;  // AD1
    private static final int I2C_SDA_I_BIT = 0x04;  // AD2

    // FT232H USB latency timer (in milliseconds)
    private static final int LATENCY_TIMER = 16;

    // FT232H min clock speed
    private static final int MIN_CLOCK_SPEED = 10000;
    // FT232H max clock speed
    private static final int MAX_CLOCK_SPEED = CLOCK_SPEED_HIGH;
    // FT232H internal bus clock speed
    private static final int BUS_CLOCK_SPEED = 30000000;

    // FT232H max transfer size
    private static final int MAX_TRANSFER_SIZE = 16384;

    // FT232H response packet header length
    private static final int READ_PACKET_HEADER_LENGTH = 2;

    private final byte[] readBuffer = new byte[MAX_TRANSFER_SIZE];

    private final Ft232Mpsse mpsse = new Ft232Mpsse();

    class Ft232Mpsse {
        // MPSSE commands
        private static final int MPSSE_WRITE_BYTES_NVE_MSB = 0x11;
        private static final int MPSSE_WRITE_BITS_NVE_MSB = 0x13;
        private static final int MPSSE_READ_BYTES_PVE_MSB = 0x20;
        private static final int MPSSE_READ_BITS_PVE_MSB = 0x22;
        private static final int MPSSE_SET_BITS_LOW = 0x80;
        private static final int MPSSE_LOOPBACK_END = 0x85;
        private static final int MPSSE_SET_TCK_DIVISOR = 0x86;
        private static final int MPSSE_SEND_IMMEDIATE = 0x87;
        private static final int MPSSE_DISABLE_CLK_DIV5 = 0x8A;
        private static final int MPSSE_ENABLE_CLK_3PHASE = 0x8C;
        private static final int MPSSE_DISABLE_CLK_ADAPTIVE = 0x97;
        private static final int MPSSE_DRIVE_ZERO = 0x9E;
        private static final int MPSSE_DUMMY_REQUEST = 0xAA;

        // MPSSE error response
        private static final int MPSSE_ERROR = 0xFA;

        // Number of writes to port to ensure port state is steady
        private static final int PORT_WRITE_STEADY_COUNT = 4;

        private final byte[] buffer = new byte[MAX_TRANSFER_SIZE];
        private int index;

        public void bufferClear() {
            index = 0;
        }

        private void bufferCheck(int length) {
            if (index + length > buffer.length) {
                throw new IllegalArgumentException(String.format("MPSSE buffer overflow: " +
                                "requested %d, available %d", length, buffer.length - index));
            }
        }

        public void bufferWrite() throws IOException {
            try {
                dataWrite(buffer, index);
            } finally {
                bufferClear();
            }
        }

        public void checkEnabled() throws IOException {
            bufferClear();
            buffer[index++] = (byte) MPSSE_DUMMY_REQUEST;
            bufferWrite();
            dataRead(buffer, 2);
            if ((buffer[0] & 0xFF) != MPSSE_ERROR || (buffer[1] & 0xFF) != MPSSE_DUMMY_REQUEST) {
                throw new IOException("MPSSE is not enabled");
            }
        }

        public void i2cInit(int clockSpeed) {
            bufferCheck(10);
            // Configure clock mode for I2C
            buffer[index++] = (byte) MPSSE_DISABLE_CLK_DIV5;
            buffer[index++] = (byte) MPSSE_DISABLE_CLK_ADAPTIVE;
            buffer[index++] = (byte) MPSSE_ENABLE_CLK_3PHASE;
            // Configure drive-zero (open drain) mode for I2C pins
            buffer[index++] = (byte) MPSSE_DRIVE_ZERO;
            buffer[index++] = (byte) (I2C_SCL_BIT | I2C_SDA_O_BIT | I2C_SDA_I_BIT); // port AD
            buffer[index++] = 0; // port AC
            // Disable loopback
            buffer[index++] = (byte) MPSSE_LOOPBACK_END;
            // Set I2c speed clock divisor
            int divisor = ((2 * BUS_CLOCK_SPEED / clockSpeed) - 2) / 3;
            buffer[index++] = (byte) MPSSE_SET_TCK_DIVISOR;
            buffer[index++] = (byte) (divisor);
            buffer[index++] = (byte) (divisor >> 8);
        }

        public void i2cPortConfig(int levels) {
            bufferCheck(3);
            buffer[index++] = (byte) MPSSE_SET_BITS_LOW;
            buffer[index++] = (byte) levels; // bits set are driven to high level
            buffer[index++] = (byte) ~I2C_SDA_I_BIT; // bits set are outputs
        }

        public void i2cIdle() {
            // Drive both SDA and SCL outputs to high level
            i2cPortConfig(0xFF);
        }

        public void i2cStart() {
            // Bring SDA low while SCL is high
            for (int i = 0; i < PORT_WRITE_STEADY_COUNT; i++) {
                i2cPortConfig(~I2C_SDA_O_BIT);
            }
            // Bring both SDA and SCL low
            for (int i = 0; i < PORT_WRITE_STEADY_COUNT; i++) {
                i2cPortConfig(~(I2C_SDA_O_BIT | I2C_SCL_BIT));
            }
        }

        public void i2cSendByteAndReadAck(byte value) {
            bufferCheck(4);
            buffer[index++] = MPSSE_WRITE_BYTES_NVE_MSB; // clock bytes out MSB first on clock falling edge
            buffer[index++] = 0x00; //
            buffer[index++] = 0x00; // 0x0000 - send one byte
            buffer[index++] = value; // byte value to send
            i2cPortConfig(~I2C_SCL_BIT); // put into transfer idle state: SCK low, SDA high
            bufferCheck(2);
            buffer[index++] = MPSSE_READ_BITS_PVE_MSB; // clock bits in MSB first on clock rising edge
            buffer[index++] = 0x00; // clock in one bit (ACK)
        }

        public void i2cWriteByteAndCheckAck(byte value) throws IOException {
            mpsse.i2cSendByteAndReadAck(value);
            mpsse.i2cReceiveImmediate();
            mpsse.bufferWrite();
            dataRead(buffer, 1);
            if ((buffer[0] & 0x01) != 0) {
                throw new IOException("NACK from slave");
            }
        }

        public void i2cReadByte(boolean isAck) {
            bufferCheck(6);
            buffer[index++] = MPSSE_READ_BYTES_PVE_MSB; // clock bytes in MSB first on clock rising edge
            buffer[index++] = 0x00;
            buffer[index++] = 0x00; // 0x0000 - receive one byte
            buffer[index++] = MPSSE_WRITE_BITS_NVE_MSB; // clock bits out MSB first on clock falling edge
            buffer[index++] = 0x00;// clock out one bit
            buffer[index++] = (byte) (isAck ? 0x00 : 0xFF);
            i2cPortConfig(~I2C_SCL_BIT); // put into transfer idle state: SCK low, SDA high
        }

        public void i2cReadBytes(int length) {
            for (int i = 0; i < length; i++) {
                i2cReadByte(i < (length - 1));
            }
        }

        public void i2cStop() {
            // Bring SDA low and keep SCL low
            for (int i = 0; i < PORT_WRITE_STEADY_COUNT; i++) {
                i2cPortConfig(~(I2C_SDA_O_BIT | I2C_SCL_BIT));
            }
            // Bring SCL high and keep SDA low
            for (int i = 0; i < PORT_WRITE_STEADY_COUNT; i++) {
                i2cPortConfig(~I2C_SDA_O_BIT);
            }
            // Bring SDA high while SCL is high
            for (int i = 0; i < PORT_WRITE_STEADY_COUNT; i++) {
                i2cPortConfig(0xFF);
            }
        }

        public void i2cReceiveImmediate() {
            bufferCheck(1);
            buffer[index++] = (byte) MPSSE_SEND_IMMEDIATE;
        }
    }

    class Ft232hUsbI2cDevice extends BaseUsbI2cDevice {
        Ft232hUsbI2cDevice(int address) {
            super(address);
        }

        @Override
        protected void deviceReadReg(int reg, byte[] buffer, int length) throws IOException {
            readRegData(address, reg, buffer, length);
        }

        @Override
        protected void deviceWrite(byte[] buffer, int length) throws IOException {
            writeData(address, buffer, length);
        }

        @Override
        protected void deviceRead(byte[] buffer, int length) throws IOException {
            readData(address, buffer, length);
        }
    }

    public Ft232hUsbI2cAdapter(UsbI2cManager manager, UsbDevice device) {
        super(manager, device);
    }

    @Override
    public String getName() {
        return ADAPTER_NAME;
    }

    @Override
    protected BaseUsbI2cDevice getDeviceImpl(int address) {
        return new Ft232hUsbI2cDevice(address);
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
        setBulkEndpoints(usbInterface.getEndpoint(0), usbInterface.getEndpoint(1));

        configure();
    }

    @Override
    public boolean isClockSpeedSupported(int speed) {
        return (speed >= MIN_CLOCK_SPEED && speed <= MAX_CLOCK_SPEED);
    }

    protected void configure() throws IOException {
        // Reset device
        commandWrite(SIO_RESET_REQUEST, SIO_RESET_SIO);
        // Set latency timer
        commandWrite(SIO_SET_LATENCY_TIMER_REQUEST, LATENCY_TIMER);
        // Drain RX and TX buffers
        commandWrite(SIO_RESET_REQUEST, SIO_RESET_PURGE_RX);
        commandWrite(SIO_RESET_REQUEST, SIO_RESET_PURGE_TX);
        // Disable event and error characters
        commandWrite(SIO_SET_EVENT_CHAR_REQUEST, 0);
        commandWrite(SIO_SET_ERROR_CHAR_REQUEST, 0);
        // Enable MPSSE mode
        commandWrite(SIO_SET_BITMODE_REQUEST, BITMODE_RESET << 8);
        commandWrite(SIO_SET_BITMODE_REQUEST, BITMODE_MPSSE << 8);
        // Check MPSSE mode is enabled
        mpsse.checkEnabled();
        // Init MPSSE for I2C
        mpsse.i2cInit(getClockSpeed());
        // Set I2C idle state
        mpsse.i2cIdle();
        // Write MPSSE commands to FT232H
        mpsse.bufferWrite();
    }

    private void checkReadDataLength(int length, int dataBufferLength) {
        checkDataLength(length, Math.min(readBuffer.length
                - READ_PACKET_HEADER_LENGTH, dataBufferLength));
    }

    private void readRegData(int address, int reg, byte[] data, int length) throws IOException {
        checkReadDataLength(length, data.length);
        mpsse.bufferClear();
        mpsse.i2cIdle();
        mpsse.i2cStart();
        mpsse.i2cWriteByteAndCheckAck(getAddressByte(address, false));
        mpsse.i2cWriteByteAndCheckAck((byte) reg);
        mpsse.i2cIdle();
        mpsse.i2cStart();
        mpsse.i2cWriteByteAndCheckAck(getAddressByte(address, true));
        if (length > 0) {
            mpsse.i2cReadBytes(length);
            mpsse.i2cReceiveImmediate();
            mpsse.bufferWrite();
        }
        dataRead(readBuffer, length);
        if (length > 0) {
            System.arraycopy(readBuffer, 0, data, 0, length);
        }
        mpsse.i2cStop();
        mpsse.bufferWrite();
    }

    private void readData(int address, byte[] data, int length) throws IOException {
        checkReadDataLength(length, data.length);
        mpsse.bufferClear();
        mpsse.i2cIdle();
        mpsse.i2cStart();
        mpsse.i2cWriteByteAndCheckAck(getAddressByte(address, true));
        if (length > 0) {
            mpsse.i2cReadBytes(length);
            mpsse.i2cReceiveImmediate();
            mpsse.bufferWrite();
        }
        dataRead(readBuffer, length);
        if (length > 0) {
            System.arraycopy(readBuffer, 0, data, 0, length);
        }
        mpsse.i2cStop();
        mpsse.bufferWrite();
    }

    private void writeData(int address, byte[] data, int length) throws IOException {
        checkDataLength(length, data.length);
        mpsse.bufferClear();
        mpsse.i2cIdle();
        mpsse.i2cStart();
        mpsse.i2cWriteByteAndCheckAck(getAddressByte(address, false));
        for (int i = 0; i < length; i++) {
            mpsse.i2cWriteByteAndCheckAck(data[i]);
        }
        mpsse.i2cStop();
        mpsse.bufferWrite();
    }

    /**
     * Write command to FT232H.
     *
     * @param cmd command to write
     * @param value command value
     * @throws IOException in case of command write error
     */
    private void commandWrite(int cmd, int value) throws IOException {
        controlTransfer(UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_OUT,
                cmd, value, 1, null, 0);
    }

    private static long getMillis() {
        return System.nanoTime() / NANOS_IN_MS;
    }

    /**
     * Read data from FT232H to data buffer until an
     * expected number of bytes is returned.
     *
     * @param data data buffer to read data
     * @param length data length to read
     * @throws IOException in case of data read error or timeout
     */
    private void dataRead(byte[] data, int length) throws IOException {
        int read = 0;
        int offset = 0;
        long startTimestamp = getMillis();
        int timeoutRemain;
        while (true) {
            timeoutRemain = (int) (USB_TIMEOUT_MILLIS - (getMillis() - startTimestamp));
            if (timeoutRemain <= 0) {
                throw new IOException("Data read timeout");
            }
            int readOffset = offset + read;
            read += bulkRead(data, readOffset, data.length - readOffset, timeoutRemain);
            if (read >= READ_PACKET_HEADER_LENGTH) {
                int dataRead = read - READ_PACKET_HEADER_LENGTH;
                if (dataRead > 0) {
                    // Cut out packet header
                    System.arraycopy(data, offset + READ_PACKET_HEADER_LENGTH, data,
                            offset, dataRead);
                }
                read = 0;
                offset += dataRead;
                if (offset >= length) {
                    break;
                }
            }
            try {
                Thread.sleep(LATENCY_TIMER / 2);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Write data from data buffer to FT232H.
     *
     * @param data data buffer to write data
     * @param length data length to write
     * @throws IOException in case of data write error
     */
    protected void dataWrite(byte[] data, int length) throws IOException {
        bulkWrite(data, length, USB_TIMEOUT_MILLIS);
    }

   public static UsbDeviceIdentifier[] getSupportedUsbDeviceIdentifiers() {
        return new UsbDeviceIdentifier[] {
                new UsbDeviceIdentifier(0x403, 0x6014)
        };
    }
}
