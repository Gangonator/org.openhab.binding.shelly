/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.handler;

import java.util.Map;

/**
 * {@link DeviceUpdateListener} can register on the {@link TradfriGatewayHandler} to be informed about details about devices.
 *
 * @author Markus Michels - Initial contribution
 */
public interface ShellyDeviceListener {

    /**
     * This method is called when new device information is received.
     */
    public void onEvent(String deviceName, String deviceIndex, String eventType, Map<String, String[]> parameters, String data);
}
