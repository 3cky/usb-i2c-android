# usb-i2c-android

[![](https://jitpack.io/v/3cky/usb-i2c-android.svg)](https://jitpack.io/#3cky/usb-i2c-android)

This is a library for communication with [I²C](https://en.wikipedia.org/wiki/I%C2%B2C) devices on Android using USB I²C adapters connected to the
[Android USB Host (OTG)](http://developer.android.com/guide/topics/connectivity/usb/host.html). 
No root access or special kernel drivers are required.

## Supported adapters

* [I2C-Tiny-USB](https://github.com/harbaum/I2C-Tiny-USB)
* [Silicon Labs CP2112](http://www.silabs.com/Support%20Documents/TechnicalDocs/CP2112.pdf)
* [Qinheng Microelectronics CH341](http://www.wch-ic.com/products/CH341.html)

## Usage

Add jitpack.io repository to your root build.gradle:
```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
Add library to dependencies:
```gradle
dependencies {
    implementation 'com.github.3cky:usb-i2c-android:1.1.0'
}
```

Add USB host feature usage to your app manifest:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="...">
    <uses-feature android:name="android.hardware.usb.host" />
    ...
</manifest>
```

Example code:

```java
import com.github.ykc3.android.usbi2c.UsbI2cAdapter;
import com.github.ykc3.android.usbi2c.UsbI2cDevice;
import com.github.ykc3.android.usbi2c.UsbI2cManager;
...

// Get Android UsbManager
UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

// Find all connected I²C adapters
UsbI2cManager usbI2cManager = UsbI2cManager.create(usbManager).build();
List<UsbI2cAdapter> i2cAdapters = usbI2cManager.getAdapters();
if (i2cAdapters.isEmpty()) {
    return;
}

// Get first adapter
UsbI2cAdapter i2cAdapter = i2cAdapters.get(0);

// Request USB access permission
usbManager.requestPermission(i2cAdapter.getUsbDevice(), usbPermissionIntent);
...
// USB permission intent handler called with success result

// Open adapter
i2cAdapter.open();

// Get device with I²C address 0x42
UsbI2cDevice i2cDevice = i2cAdapter.getDevice(0x42);

// Read device register 0x01.
// Throws java.lang.IOException if device is not connected or I/O error caused 
byte value = i2cDevice.readRegByte(0x01);

// Close adapter
i2cAdapter.close();
```

## Sample app

For simple I²C device scanner sample app, check an `app` module. 

## License

This library is licensed under *LGPL Version 2.1*.  Please see LICENSE.txt for the
complete license.
