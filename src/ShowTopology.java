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

/**
 * Show the topology tree.
 * <p>
 * This class shows how to enumerate all USB-connected devices.
 * This class should not be used except by other example code.
 * @author Dan Streetman
 */
public class ShowTopology
{
	public static void main(String argv[])
	{
		UsbHub virtualRootUsbHub = getVirtualRootUsbHub();

		/* This method recurses through the topology tree, using
		 * the getAttachedUsbDevices() method.
		 */
		System.out.println("Using UsbHub.getAttachedUsbDevices() to show toplogy:");
		processUsingGetAttachedUsbDevices(virtualRootUsbHub, "");

		/* Let's go through the topology again, but using getUsbPorts()
		 * this time.
		 */
		System.out.println("Using UsbHub.getUsbPorts() to show toplogy:");
		processUsingGetUsbPorts(virtualRootUsbHub, "");
	}

	/**
	 * Get the virtual root UsbHub.
	 * @return The virtual root UsbHub.
	 */
	public static UsbHub getVirtualRootUsbHub()
	{
		UsbServices services = null;
		UsbHub virtualRootUsbHub = null;

		/* First we need to get the UsbServices.
		 * This might throw either an UsbException or SecurityException.
		 * A SecurityException means we're not allowed to access the USB bus,
		 * while a UsbException indicates there is a problem either in
		 * the javax.usb implementation or the OS USB support.
		 */
		try {
			services = UsbHostManager.getUsbServices();
		} catch ( UsbException uE ) {
			throw new RuntimeException("Error : " + uE.getMessage());
		} catch ( SecurityException sE ) {
			throw new RuntimeException("Error : " + sE.getMessage());
		}

		/* Now we need to get the virtual root UsbHub,
		 * everything is connected to it.  The Virtual Root UsbHub
		 * doesn't actually correspond to any physical device, it's
		 * strictly virtual.  Each of the devices connected to one of its
		 * ports corresponds to a physical host controller located in
		 * the system.  Those host controllers are (usually) located inside
		 * the computer, e.g. as a PCI board, or a chip on the mainboard,
		 * or a PCMCIA card.  The virtual root UsbHub aggregates all these
		 * host controllers.
		 *
		 * This also may throw an UsbException or SecurityException.
		 */
		try {
			virtualRootUsbHub = services.getRootUsbHub();
		} catch ( UsbException uE ) {
			throw new RuntimeException("Error : " + uE.getMessage());
		} catch ( SecurityException sE ) {
			throw new RuntimeException("Error : " + sE.getMessage());
		}

		return virtualRootUsbHub;
	}

	/**
	 * Process all devices in the system using getAttachedUsbDevices().
	 * Note that this accepts a UsbDevice, not a UsbHub, since UsbHubs are UsbDevices.
	 */
	public static void processUsingGetAttachedUsbDevices(UsbDevice usbDevice, String prefix)
	{
		UsbHub usbHub = null;

		/* If this is not a UsbHub, just display device and return. */
		if (!usbDevice.isUsbHub()) {
			System.out.println(prefix + "Device");
			return;
		} else {
			/* We know it's a hub, so cast it. */
			usbHub = (UsbHub)usbDevice;
		}

		if (usbHub.isRootUsbHub()) {
			/* This is the virtual root UsbHub. */
			System.out.println(prefix + "Virtual root UsbHub");
		} else {
			/* This is not the virtual root UsbHub. */
			System.out.println(prefix + "UsbHub");
		}

		/* Now let's process each of this hub's devices. */
		List attachedUsbDevices = usbHub.getAttachedUsbDevices();

		for (int i=0; i<attachedUsbDevices.size(); i++) {
			/* We know all objects in the list are UsbDevice objects; casting is safe. */
			UsbDevice device = (UsbDevice)attachedUsbDevices.get(i);

			/* Recursively handle this device. */
			processUsingGetAttachedUsbDevices(device, prefix+PREFIX);
		}
	}

	/**
	 * Process all devices in the system using getUsbPorts().
	 * Notice that this looks the same as using getAttachedUsbDevices()
	 * except this also displays UsbPorts that do not have a connected device.
	 */
	public static void processUsingGetUsbPorts(UsbDevice usbDevice, String prefix)
	{
		UsbHub usbHub = null;

		/* If this is not a UsbHub, just display device and return. */
		if (!usbDevice.isUsbHub()) {
			System.out.println(prefix + "Device");
			return;
		} else {
			/* We know it's a hub, so cast it. */
			usbHub = (UsbHub)usbDevice;
		}

		if (usbHub.isRootUsbHub()) {
			/* This is the virtual root UsbHub. */
			System.out.println(prefix + "Virtual root UsbHub");
		} else {
			/* This is not the virtual root UsbHub. */
			System.out.println(prefix + "UsbHub");
		}

		/* Now let's process each of this hub's ports. */
		List usbPorts = usbHub.getUsbPorts();

		for (int i=0; i<usbPorts.size(); i++) {
			/* We know all objects in the list are UsbPort objects; casting is safe. */
			UsbPort port = (UsbPort)usbPorts.get(i);

			/* If this doesn't have a device attached, just process the port. */
			if (!port.isUsbDeviceAttached()) {
				System.out.println(prefix+PREFIX + "UsbPort");
				continue;
			} else {
				/* There is a device attached, so we'll process it. */
				processUsingGetUsbPorts(port.getUsbDevice(), prefix+PREFIX);
			}
		}
	}

	public static final String PREFIX = "  ";

}
