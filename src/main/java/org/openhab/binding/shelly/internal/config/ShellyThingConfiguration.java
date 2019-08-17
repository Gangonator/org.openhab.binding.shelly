/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.config;

/**
 * The {@link ShellyThingConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Hans-Jörg - Initial contribution
 * @author Markus Michels - refactored
 */
public class ShellyThingConfiguration {
    public String  localIp            = "";     // local ip addresses used to create callback url

    public String  deviceIp           = "";     // ip address of thedevice
    public int     updateInterval     = 60;     // schedule interval for the update job
    public float   lowBattery         = 20;     // threshold for battery value

    public String  userId             = "";     // userid for http basic auth
    public String  password           = "";     // password for http basic auth

    public boolean eventsRelayButton  = false;  // true: register for Relay btn_xxx events
    public boolean eventsRelaySwitch  = true;   // true: register for de vice out_xxx events
    public boolean eventsSensorReport = true;   // true: register for sensor events
}
