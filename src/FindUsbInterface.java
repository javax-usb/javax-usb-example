/*
 * Copyright (c) 1999 - 2001, International Business Machines Corporation.
 * All Rights Reserved.
 *
 * This software is provided and licensed under the terms and conditions
 * of the Common Public License:
 * http://oss.software.ibm.com/developerworks/opensource/license-cpl.html
 */

import java.io.*;
import java.util.*;

import javax.usb.*;
import javax.usb.util.*;

/**
 * Find a specific UsbInterface.
 * <p>
 * This finds a specific UsbInterface based on some of the interface's properties.
 * This class should not be used except by other example code.
 * @author Dan Streetman
 */
public class FindUsbInterface
{
	public static void main(String argv[])
	{
		parseArgv(argv);

		UsbHub virtualRootUsbHub = ShowTopology.getVirtualRootUsbHub();
		List usbInterfaces = null;

		/**
		 * This will recursively search for all interfaces with the specified interface class.
		 */
		usbInterfaces = getUsbInterfacesWithInterfaceClass(virtualRootUsbHub, getInterfaceClass());

		System.out.print("Found " + usbInterfaces.size() + " interfaces with");
		System.out.print(" interface class 0x" + UsbUtil.toHexString(getInterfaceClass()));
		System.out.println("");
	}

	/**
	 * Get a List of all interfaces that match the specified interface class.
	 * @param usbDevice The UsbDevice to check.
	 * @param interfaceClass The interface class to match.
	 * @return A List of any matching UsbInterfaces(s).
	 */
	public static List getUsbInterfacesWithInterfaceClass(UsbDevice usbDevice, byte interfaceClass)
	{
		List list = new ArrayList();

		/* If the UsbDevice is not configured, there is not much we can do with it.
		 * We could examine all its fields/properties, but the only communication
		 * possible is a limited set of Requests on the Default Control Pipe.
		 * So for this example we'll ignore unconfigured devices.
		 * The OS USB stack normally configures all devices, so we shouldn't run into any
		 * unconfigured devices.
		 */
		if (usbDevice.isConfigured()) {
			/* This gets the active UsbConfiguration (only one config can be active)
			 * and from that gets all the UsbInterfaces.
			 */
			List ifaces = usbDevice.getActiveUsbConfiguration().getUsbInterfaces();

			for (int i=0; i<ifaces.size(); i++) {
				/* All objects in the List are guaranteed to be UsbInterface objects. */
				UsbInterface usbInterface = (UsbInterface)ifaces.get(i);

				/* See FindUsbDevice for notes about comparing unsigned numbers, note this is an unsigned byte. */
				if (interfaceClass == usbInterface.getUsbInterfaceDescriptor().bInterfaceClass())
					list.add(usbInterface);
			}
		}

		/* this is just normal recursion.  Nothing special. */
		if (usbDevice.isUsbHub()) {
			List devices = ((UsbHub)usbDevice).getAttachedUsbDevices();
			for (int i=0; i<devices.size(); i++)
				list.addAll(getUsbInterfacesWithInterfaceClass((UsbDevice)devices.get(i), interfaceClass));
		}

		return list;
	}

	/**
	 * Get an interface class.
	 * @return An interface class.
	 */
	public static byte getInterfaceClass() { return staticInterfaceClass; }

	/**
	 * Parse the parameters.
	 * @param argv The command-line parameters.
	 */
	public static void parseArgv(String argv[])
	{
		for (int i=0; i<argv.length; i++) {
			int equalsIndex = argv[i].indexOf('=');
			try {
				String key = argv[i].substring(0, equalsIndex);
				String value = argv[i].substring(equalsIndex+1);
				if (key.equals(INTERFACE_CLASS_KEY))
					staticInterfaceClass = (byte)Integer.decode(value).intValue();
				else {
					System.err.println("Unrecognized key \"" + key + "\"\n" + USAGE);
					System.exit(1);
				}
			} catch ( Exception e ) {
				System.err.println("Invalid key-value pair \"" + argv[i] + "\"\n" + USAGE);
				System.exit(1);
			}
		}
	}

	private static byte staticInterfaceClass = UsbConst.HUB_CLASSCODE; /* This will match all hubs. :) */

	private static final String INTERFACE_CLASS_KEY = "bInterfaceClass";

	private static final String KEYS =
		"\t" + INTERFACE_CLASS_KEY;

	private static final String USAGE =
		"Usage : java FindUsbInterface <key=value>\n" +
		"\n" +
		"\tvalid keys are:\n" + KEYS;
}
