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
 * Example driver for USB mice.
 * <p>
 * This shows how to get events from a USB mouse.
 * This class should not be used except by other example code.
 * <p>
 * WARNING : The code in the class is intended for example use <i>only</i>!
 * Some functionality (e.g. HID usage detection) is simplified for example
 * purposes, and should not be reproduced in non-example code.  Also
 * this code is designed to be example code, not robust well-written code,
 * and so the design should not be duplicated in normal code.
 * @author Dan Streetman
 */
public class MouseDriver
{
	public static void main(String argv[])
	{
		UsbHub virtualRootUsbHub = ShowTopology.getVirtualRootUsbHub();
		List usbInterfaces = FindUsbInterface.getUsbInterfacesWithInterfaceClass(virtualRootUsbHub, HID_CLASS);

		System.out.println("Found " + usbInterfaces.size() + " HID-type interfaces.");

		/* Each object in the list is a UsbInterface of class HID. */
		for (int i=0; i<usbInterfaces.size(); i++) {
			UsbInterface usbInterface = (UsbInterface)usbInterfaces.get(i);

			boolean isMouse = false;

			/* Check the UsbInterface by its SubClass and Protocol */
			if (checkUsbInterfaceIsMouse(usbInterface)) {
				System.out.println("Found mouse by SubClass/Protocol");
				isMouse = true;
			}

			/* Check the UsbInterface by its Usage Page and Usage ID */
			if (checkHidInterface(usbInterface, HID_MOUSE_USAGE_PAGE, HID_MOUSE_USAGE_ID)) {
				System.out.println("Found mouse by Usage Page/Usage ID");
				isMouse = true;
			}

			/* Really, both of the above checks should agree. */
			if (isMouse) {
				System.out.println("Found HID mouse.");

				driveHidMouse(usbInterface);
			}
		}
	}

	/**
	 * Drive the HID mouse until the user aborts.
	 * @param usbInterface The UsbInterface for the mouse.
	 */
	public static void driveHidMouse(UsbInterface usbInterface)
	{
		/* We have to claim the interface to communicate with this mouse. */
		try {
			usbInterface.claim();
		} catch ( UsbException uE ) {
			/* If we can't claim the interface, that means someone else is
			 * using the interface (probably some other non-Java program).
			 * This is likely due to a native mouse driver, which you
			 * need to move out of the way (in an OS-specific way) before
			 * you can use javax.usb to communicate with the device.
			 */
			System.out.println("Could not claim interface to drive HID mouse : " + uE.getMessage());
			return;
		}

		/* This is a list of all this interface's endpoints. */
		List usbEndpoints = usbInterface.getUsbEndpoints();

		UsbEndpoint usbEndpoint = null;

		for (int i=0; i<usbEndpoints.size(); i++) {
			usbEndpoint = (UsbEndpoint)usbEndpoints.get(i);

			/* A HID mouse uses an interrupt-type in-direction endpoint for movement events.
			 * This endpoint is required by the HID spec.  The HID spec does not
			 * prohibit multiple interrupt-type in-direction endpoints per HID interface,
			 * but this is rarely done in practice, as the HID spec presumes there is only
			 * one endpoint of this type present per HID interface.  We use the first found.
			 * See the HID spec for more details.
			 */
			if (UsbConst.ENDPOINT_TYPE_INTERRUPT == usbEndpoint.getType() && UsbConst.ENDPOINT_DIRECTION_IN == usbEndpoint.getDirection())
				break;
			else
				usbEndpoint = null;
		}

		/* If the endpoint is null, we didn't find any endpoints we can use; this device does not
		 * meet the HID spec (it is fundamentally broken!).
		 */
		if (null == usbEndpoint) {
			System.out.println("This HID interface does not have the required interrupt-in endpoint.");
			return;
		}

		UsbPipe usbPipe = usbEndpoint.getUsbPipe();

		/* We need to open the endpoint's pipe. */
		try {
			usbPipe.open();
		} catch ( UsbException uE ) {
			/* If we couldn't open the pipe, we can't talk to the HID interface.
			 * This is not a usualy condition, so error recovery needs to look at
			 * the specific error to determine what to do now.
			 * We will just bail out.
			 */
			System.out.println("Could not open endpoint to communicate with HID mouse : " + uE.getMessage());
			try { usbInterface.release(); }
			catch ( UsbException uE2 ) { /* FIXME - define why this might happen. */ }
			return;
		}

		HidMouseRunnable hmR = new HidMouseRunnable(usbPipe);
		Thread t = new Thread(hmR);

		System.out.println("Driving HID mouse, move mouse to see movement events.");
		System.out.println("Press Enter when done.");

		t.start();

		try {
			/* This just waits for Enter to get pressed. */
			System.in.read();
		} catch ( Exception e ) {
			System.out.println("Exception while waiting for Enter : " + e.getMessage());
		}

		hmR.stop();

		try {
			usbPipe.close();
			usbInterface.release();
		} catch ( UsbException uE ) { /* FIXME - define why this might happen. */ }

		System.out.println("Done driving HID mouse.");
	}

	/**
	 * Check if the HID-class UsbInterface is a boot-type USB mouse.
	 * @param usbInterface The HID-class UsbInterface to check.
	 * @return If the UsbInterface is a boot-type USB mouse.
	 */
	public static boolean checkUsbInterfaceIsMouse(UsbInterface usbInterface)
	{
		/* As specified in the HID spec, a boot-type USB mouse has the fields:
		 *   bInterfaceClass is HID
		 *   bInterfaceSubClass is Boot Interface
		 *   bInterfaceProtocol is Mouse
		 */
		UsbInterfaceDescriptor desc = usbInterface.getUsbInterfaceDescriptor();

		if (HID_SUBCLASS_BOOT_INTERFACE == desc.bInterfaceSubClass() && HID_PROTOCOL_MOUSE == desc.bInterfaceProtocol())
				return true;
		else
			return false;
	}

	/**
	 * Check the HID-class UsbInterface to see if matches the usagePage and usageID.
	 * @param usbInterface The HID-class UsbInterface to check.
	 * @return If the UsbInterface matches or not.
	 */
	public static boolean checkHidInterface(UsbInterface usbInterface, short usagePage, short usageID)
	{
		/* To check the usage, communication via the Default Control Pipe is required.
		 * Normally the DCP is not an exclusive-access pipe, but in this case
		 * the recipient of the communication is an interface.  So,
		 * the communication may fail if that UsbInterface has not been claim()ed.
		 * If you think that is a strange way to design things, go complain to the
		 * USB designers ;)
		 */
		try {
			usbInterface.claim();
		} catch ( UsbException uE ) {
			/* If claiming the interface fails, we will still try to check the usage.
			 * It may or may not work depending on how things are implemented lower down.
			 */
		}

		UsbDevice usbDevice = usbInterface.getUsbConfiguration().getUsbDevice();

		/* These fields perform a get-descriptor request for a HID Report-type descriptor. */
		byte bmRequestType = GET_REPORT_DESCRIPTOR_REQUESTTYPE;
		byte bRequest = GET_REPORT_DESCRIPTOR_REQUEST;
		short wValue = GET_REPORT_DESCRIPTOR_VALUE;
		short wIndex = UsbUtil.unsignedShort( usbInterface.getUsbInterfaceDescriptor().bInterfaceNumber() );

		UsbControlIrp getUsageIrp = usbDevice.createUsbControlIrp(bmRequestType, bRequest, wValue, wIndex);

		/* This is the buffer to place the descriptor in. */
		byte[] data = new byte[256];
		getUsageIrp.setData(data);

		try {
			/* This gets the Report-type descriptor (for this interface) from the device.
			 * This may throw a UsbException.
			 */
			usbDevice.syncSubmit(getUsageIrp);

			/* The usage is the first 4 bytes, so if we didn't get at least 4 bytes back,
			 * something is wrong.
			 */
			if (4 > getUsageIrp.getActualLength())
				return false;

			/* Check if the usage matches. */
			if (UsbUtil.toInt(data[0],data[1],data[2],data[3]) == UsbUtil.toInt(usagePage,usageID))
				return true;

			/* The usage didn't match. */
			return false;
		} catch ( UsbException uE ) {
			/* For whatever reason, we couldn't get the Report-type descriptor.
			 * So we assume this doesn't match; but good error recovery should examine the
			 * UsbException for the cause of the failure.
			 */
			return false;
		} finally {
			/* Make sure to try and release the interface. */
			try { usbInterface.release(); }
			catch ( UsbException uE ) { /* FIXME - define why this may happen */ }
		}
	}

	public static final byte HID_CLASS = 0x03;
	public static final byte HID_SUBCLASS_BOOT_INTERFACE = 0x01;
	public static final byte HID_PROTOCOL_MOUSE = 0x02;
	public static final short HID_MOUSE_USAGE_PAGE = 0x0501;
	public static final short HID_MOUSE_USAGE_ID = 0x0902;

	public static final byte HID_DESCRIPTOR_TYPE_REPORT = 0x22;

	public static final byte GET_REPORT_DESCRIPTOR_REQUESTTYPE =
		UsbConst.REQUESTTYPE_DIRECTION_IN | UsbConst.REQUESTTYPE_TYPE_STANDARD | UsbConst.REQUESTTYPE_RECIPIENT_INTERFACE;
	public static final byte GET_REPORT_DESCRIPTOR_REQUEST =
		UsbConst.REQUEST_GET_DESCRIPTOR;
	public static final short GET_REPORT_DESCRIPTOR_VALUE =
		HID_DESCRIPTOR_TYPE_REPORT << 8;

	/**
	 * Class to listen in a dedicated Thread for mouse movement events.
	 * <p>
	 * This really could be used for any HID device.
	 */
	public static class HidMouseRunnable implements Runnable
	{
		/* This pipe must be the HID interface's interrupt-type in-direction endpoint's pipe. */
		public HidMouseRunnable(UsbPipe pipe) { usbPipe = pipe; }

		public void run()
		{
			/* This buffer will be filled (at least partially) with data events.
			 * Note that its size is that of the endpoint's maximum packet size.
			 * For interrupt pipes (at least, in-direction interrupt pipes),
			 * provided buffers are almost always the exact maximum packet size,
			 * as a smaller size might truncate data, and a larger size would
			 * delay receiving of each data event, possibly indefinitely.
			 * See the USB specification on interrupt pipes for details on
			 * why this is the case.
			 */
			byte[] buffer = new byte[UsbUtil.unsignedInt(usbPipe.getUsbEndpoint().getUsbEndpointDescriptor().wMaxPacketSize())];

			/* The syncSubmit method, if using a byte[] parameter, returns the number of
			 * bytes that the device actually provided.  Usually this is the same
			 * as the size of the provided buffer, but that is not always the case.
			 */
			int length = 0;

			while (running) {
				/* Until we provide a data buffer, this endpoint will never
				 * communicate any data.  Once this buffer is submitted,
				 * the endpoint will be polled (by system hardware) until it provides
				 * some data.  Then, the system will stop polling it until
				 * another data buffer is provided.  If another buffer has been
				 * queued, the system will continue polling using that buffer.
				 */
				try {
					/* This is synchronous, meaning our Thread will
					 * block until the data buffer has been filled by the device.
					 * For non-blocking submission, the asynchronous method should be used.
					 * Note that interrupt-in (and bulk-in) pipe submissions may
					 * block indefinitely!  Control-type and isochronous-type submissions
					 * will complete (or fail) in a finite amount of time; the USB spec
					 * arbitrarily sets this time limit at 5 seconds, but YMMV with
					 * various implementations.  Interrupt-out (and bulk-out) shouldn't
					 * block indefinitely.
					 */
					length = usbPipe.syncSubmit(buffer);
				} catch ( UsbException uE ) {
					/* If we're _not_ running, this exception was probably generated
					 * because the in-progress submission was aborted.
					 * It's expected and ok to ignore in that case.
					 */
					if (running) {
						/* Either we couldn't submit a data buffer, or there was
						 * an error during the data transmission.  This usually means something has
						 * gone wrong with the pipe/endpoint, interface, and/or device.
						 * What exactly that error is should be indicated by the exception,
						 * and the application should try to fix it if possible.
						 * We will just bail out here.
						 */
						System.out.println("Unable to submit data buffer to HID mouse : " + uE.getMessage());
						break;
					}
				}

				if (running) {
					System.out.print("Got " + length + " bytes of data from HID mouse :");
					for (int i=0; i<length; i++)
						System.out.print(" 0x" + UsbUtil.toHexString(buffer[i]));
					System.out.println("");
				}
			}
		}

		/**
		 * Stop/abort listening for data events.
		 */
		public void stop()
		{
			running = false;
			usbPipe.abortAllSubmissions();
		}

		public boolean running = true;
		public UsbPipe usbPipe = null;
	}
}
