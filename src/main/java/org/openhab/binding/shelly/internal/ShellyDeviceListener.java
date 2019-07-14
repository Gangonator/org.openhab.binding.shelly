/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.shelly.internal;

import java.util.Map;

/**
 * {@link DeviceUpdateListener} can register on the {@link TradfriGatewayHandler} to be
 * informed about details about devices.
 *
 * @author Markus Michels - Initial contribution
 */
public interface ShellyDeviceListener {

    /**
     * This method is called when new device information is received.
     */
    public void onUpdateEvent(String deviceName, Map<String, String[]> parameters, String data);
}
