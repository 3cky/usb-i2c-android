/*
 * Copyright (c) 2022 Victor Antonovich <v.antonovich@gmail.com>
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

import com.github.ykc3.android.usbi2c.UsbI2cDevice;

public abstract class AbstractI2cDeviceHandler implements I2cDeviceHandler {
   @Override
   public boolean isDeviceSupported(UsbI2cDevice device) {
      return isDeviceAddressMatched(device.getAddress()) && isDeviceRecognized(device);
   }

   private boolean isDeviceAddressMatched(int address) {
      for (int addr: getDeviceAddresses()) {
         if (addr == address) {
            return true;
         }
      }
      return false;
   }

   protected abstract int[] getDeviceAddresses();

   protected abstract boolean isDeviceRecognized(UsbI2cDevice device);
}
