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

package com.github.ykc3.android.usbi2c;

import java.io.IOException;

/**
 * Slave I2C device connected to I2C bus and accessible through {@link UsbI2cAdapter}.
 */
public interface UsbI2cDevice {

    /**
     * Get I2C device address.
     *
     * @return I2C device address
     */
    int getAddress();

    /**
     * Read data from the device into buffer.
     *
     * @param buffer buffer to read data into
     * @param length number of bytes to read, may not be larger than the buffer size
     * @throws IOException in case of I/O error
     */
    void read(byte[] buffer, int length) throws IOException;

    /**
     * Read a byte from a given register.
     *
     * @param reg the register to read from (0x00-0xFF)
     * @return the value read from the device
     * @throws IOException in case of I/O error
     */
    byte readRegByte(int reg) throws IOException;

    /**
     * Read two consecutive register values as a 16-bit little-endian word.
     * The first register address corresponds to the least significant byte (LSB) in the word,
     * followed by the most significant byte (MSB).
     *
     * @param reg the first register to read from (0x00-0xFF)
     * @return the value read from the device
     * @throws IOException in case of I/O error
     */
    short readRegWord(int reg) throws IOException;

    /**
     * Read multiple consecutive register values as an array.
     *
     * @param reg the start register to read from (0x00-0xFF)
     * @param buffer buffer to read data into
     * @param length number of bytes to read, may not be larger than the buffer size
     * @throws IOException in case of I/O error
     */
    void readRegBuffer(int reg, byte[] buffer, int length) throws IOException;

    /**
     * Write data to the device.
     *
     * @param buffer data to write
     * @param length number of bytes to write, may not be larger than the buffer size
     * @throws IOException in case of I/O error
     */
    void write(byte[] buffer, int length) throws IOException;

    /**
     * Write a byte to a given register.
     *
     * @param reg the register to write to (0x00-0xFF)
     * @param data value to write
     * @throws IOException in case of I/O error
     */
    void writeRegByte(int reg, byte data) throws IOException;

    /**
     * Write two consecutive register values as a 16-bit little-endian word.
     * The first register address corresponds to the least significant byte (LSB) in the word,
     * followed by the most significant byte (MSB).
     *
     * @param reg the first register to write to (0x00-0xFF)
     * @param data value to write
     * @throws IOException in case of I/O error
     */
    void writeRegWord(int reg, short data) throws IOException;

    /**
     * Write multiple consecutive register values from an array.
     *
     * @param reg the start register to write to (0x00-0xFF)
     * @param buffer data to write
     * @param length number of bytes to write, may not be larger than the buffer size
     * @throws IOException in case of I/O error
     */
    void writeRegBuffer(int reg, byte[] buffer, int length) throws IOException;
}
