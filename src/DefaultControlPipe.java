/*
 * Copyright (c) 1999 - 2001, International Business Machines Corporation.
 * All Rights Reserved.
 *
 * This software is provided and licensed under the terms and conditions
 * of the Common Public License:
 * http://oss.software.ibm.com/developerworks/opensource/license-cpl.html
 */

import java.util.*;

import javax.usb.*;
import javax.usb.util.*;

/**
 * Show some example communication via the Default Control Pipe (DCP).
 * <p>
 * This class shows how to perform some basic standard I/O using
 * a device's DCP.  This uses the first non-hub device found.
 * This class should not be used except by other example code.
 */
public class DefaultControlPipe
{
	public static void main(String argv[])
	{
		UsbHub virtualRootUsbHub = ShowTopology.getVirtualRootUsbHub();
		List allUsbDevices = FindUsbDevice.getAllUsbDevices(virtualRootUsbHub);
		List usbHubs = FindUsbDevice.getUsbDevicesWithDeviceClass(virtualRootUsbHub, UsbConst.HUB_CLASSCODE);

		System.out.println("Found " + allUsbDevices.size() + " devices total.");
		System.out.println("Found " + usbHubs.size() + " hubs.");

		allUsbDevices.removeAll(usbHubs);

		System.out.println("Found " + allUsbDevices.size() + " non-hub devices.");

		if (0 < allUsbDevices.size()) {
			/* We'll just use the first non-hub device. */
			UsbDevice usbDevice = (UsbDevice)allUsbDevices.get(0);

			/* Show how to communicate using the StandardRequest utility class. */
			showStandardRequestCommunication(usbDevice);

			/* Show how to communicate using UsbControlIrp objects. */
			showUsbControlIrpCommunication(usbDevice);
		} else {
			System.out.println("No non-hub devices were found.");
		}
	}

	/**
	 * Show how to communicate using UsbControlIrp objects.
	 * @param usbDevice The UsbDevice to use.
	 */
	public static void showUsbControlIrpCommunication(UsbDevice usbDevice)
	{
		/* UsbControlIrps have 2 parts: a header, or 'setup packet' in
		 * low-level USB terms, and data buffer.  The header tells the
		 * device what control action the communication is requesting.
		 * These particular values will perform a get-device-descriptor request.
		 */
		byte bmRequestType =
			UsbConst.REQUESTTYPE_DIRECTION_IN | UsbConst.REQUESTTYPE_TYPE_STANDARD | UsbConst.REQUESTTYPE_RECIPIENT_DEVICE;
		byte bRequest = UsbConst.REQUEST_GET_DESCRIPTOR;
		short wValue = UsbConst.DESCRIPTOR_TYPE_DEVICE << 8;
		short wIndex = 0;
		/* For this specific case, where we are getting a device descriptor,
		 * 256 bytes is enough; device descriptors are fixed-length.
		 */
		byte[] buffer = new byte[256];

		/* All communication on the DCP (and all control-type pipes) is
		 * done using UsbControlIrp objects.  There are 3 different ways to
		 * create a UsbControlIrp object:
		 * (1) Call the UsbDevice.createUsbControlIrp() method.
		 * (2) Use the javax.usb.util.DefaultUsbControlIrp class.
		 * (3) Define your own class that implements the UsbControlIrp interface.
		 * The first way is the best (but least flexible) as it produces a
		 * UsbControlIrp that may be optimized to whatever javax.usb implementation
		 * you are currently using; however you are limited to only those
		 * methods provided by the UsbControlIrp interface.
		 * The second way is more flexible, as you can use all the methods
		 * defined by the DefaultUsbControlIrp class, but it may not
		 * be as optimized to the implementation (i.e. the javax.usb
		 * implementation may have to do more work to process it).
		 * The third way is the most flexible as you can put whatever
		 * you want into your class; however this way will very likely
		 * require more processing and/or memory by the javax.usb
		 * implementation.  A combination of the second and third
		 * options is also possible, where you create your own class
		 * that extends the DefaultUsbControlIrp class.
		 * For this example we will use the first option, as it is the easiest.
		 */
		UsbControlIrp usbControlIrp = usbDevice.createUsbControlIrp(bmRequestType, bRequest, wValue, wIndex);
		usbControlIrp.setData(buffer);

		if (!sendUsbControlIrp(usbDevice, usbControlIrp))
			return;

		/* This is the number of bytes actually received from the device.
		 * If the data direction was out (host-to-device), this would
		 * be the number of bytes actually sent to the device.  For the
		 * input (device-to-host) case, this may be less than the length of
		 * the provided buffer (providing the irp is set to accept short packets).
		 * For the output case, this should never be less than the size
		 * of the provided buffer (but you may want to check anyway to be sure!).
		 */
		int length = usbControlIrp.getActualLength();

		/* The device descriptor is binary, as specified by the USB spec.
		 * We're not going to parse it here, but we can print it out.
		 */
		System.out.println("Got device descriptor (length " + length + ") :");
		System.out.println(UsbUtil.toHexString(" 0x", buffer, length));

		/* Now let's try getting the current configuration number. */
		bmRequestType =
			UsbConst.REQUESTTYPE_DIRECTION_IN | UsbConst.REQUESTTYPE_TYPE_STANDARD | UsbConst.REQUESTTYPE_RECIPIENT_DEVICE;
		bRequest = UsbConst.REQUEST_GET_CONFIGURATION;
		wValue = 0;
		wIndex = 0;
		/* The current configuration number will be returned in this byte. */
		buffer = new byte[1];

		usbControlIrp = usbDevice.createUsbControlIrp(bmRequestType, bRequest, wValue, wIndex);
		usbControlIrp.setData(buffer);

		if (!sendUsbControlIrp(usbDevice, usbControlIrp))
			return;

		length = usbControlIrp.getActualLength();

		/* If we didn't get 1 byte, something went wrong... */
		if (1 > length)
			System.out.println("Got no data during submission!");
		else
			System.out.println("Got current configuration number : " + UsbUtil.unsignedInt(buffer[0]));
	}

	/**
	 * Send the UsbControlIrp to the UsbDevice on the DCP.
	 * @param usbDevice The UsbDevice.
	 * @param usbControlIrp The UsbControlIrp.
	 * @return If the submission was successful.
	 */
	public static boolean sendUsbControlIrp(UsbDevice usbDevice, UsbControlIrp usbControlIrp)
	{
		try {
			/* This will block until the submission is complete.
			 * Note that submissions (except interrupt and bulk in-direction)
			 * will not block indefinitely, they will complete or fail within
			 * a finite amount of time.  See MouseDriver.HidMouseRunnable for more details.
			 */
			usbDevice.syncSubmit(usbControlIrp);
			return true;
		} catch ( UsbException uE ) {
			/* The exception sould indicate the reason for the failure.
			 * For this example, we'll just stop trying.
			 */
			System.out.println("DCP submission failed : " + uE.getMessage());
			return false;
		}
	}

	/**
	 * Show how to communicate using the StandardRequest utility class.
	 * @param usbDevice The UsbDevice to use.
	 */
	public static void showStandardRequestCommunication(UsbDevice usbDevice)
	{
		/* This is the easier way to perform standard requests.
		 * Let's get the device descriptor first.
		 */
		byte type = UsbConst.DESCRIPTOR_TYPE_DEVICE;
		byte index = 0;
		short langid = 0;
		/* This is big enough for device descriptors, but keep in mind
		 * that if this is not big enough for a request, no error
		 * will be returned, the buffer will just be completely filled.
		 * Most transfers provide some way to determine how large the buffer
		 * needs to be; e.g. all descriptors have a length field.
		 */
		byte[] buffer = new byte[256];

		try {
			int length = StandardRequest.getDescriptor(usbDevice, type, index, langid, buffer);

			/* The device descriptor is binary, as specified by the USB spec.
			 * We're not going to parse it here, but we can print it out.
			 */
			System.out.println("Got device descriptor (length " + length + ") :");
			System.out.println(UsbUtil.toHexString(" 0x", buffer, length));
		} catch ( UsbException uE ) {
			System.out.println("Couldn't get device descriptor : " + uE.getMessage());
		}

		/* Now we'll get the current configuration number. */
		try {
			byte configuration = StandardRequest.getConfiguration(usbDevice);

			System.out.println("Got current configuration number : " + UsbUtil.unsignedInt(configuration));
		} catch ( UsbException uE ) {
			System.out.println("Couldn't get current configuration number : " + uE.getMessage());
		}
	}


}
