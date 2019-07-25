/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link ShellyBindingConstants} class defines common constants, which are used across the whole binding.
 *
 * @author Hans-Jörg Merk - Initial contribution
 */
@NonNullByDefault
public class ShellyBindingConstants {

    private static final String           BINDING_ID                   = "shelly";

    // List of all Thing Type UIDs
    public static final ThingTypeUID      THING_TYPE_SHELLY1           = new ThingTypeUID(BINDING_ID, "shelly1");
    public static final ThingTypeUID      THING_TYPE_SHELLY1PM         = new ThingTypeUID(BINDING_ID, "shelly1pm");
    public static final ThingTypeUID      THING_TYPE_SHELLY2_RELAY     = new ThingTypeUID(BINDING_ID, "shelly2-relay");
    public static final ThingTypeUID      THING_TYPE_SHELLY2_ROLLER    = new ThingTypeUID(BINDING_ID, "shelly2-roller");
    public static final ThingTypeUID      THING_TYPE_SHELLY25_RELAY    = new ThingTypeUID(BINDING_ID, "shelly25-relay");
    public static final ThingTypeUID      THING_TYPE_SHELLY25_ROLLER   = new ThingTypeUID(BINDING_ID, "shelly25-roller");
    public static final ThingTypeUID      THING_TYPE_SHELLY4PRO        = new ThingTypeUID(BINDING_ID, "shelly4pro");
    public static final ThingTypeUID      THING_TYPE_SHELLYPLUG        = new ThingTypeUID(BINDING_ID, "shellyplug");
    public static final ThingTypeUID      THING_TYPE_SHELLYPLUGS       = new ThingTypeUID(BINDING_ID, "shellyplugs");
    public static final ThingTypeUID      THING_TYPE_SHELLYBULB        = new ThingTypeUID(BINDING_ID, "shellybulb");
    public static final ThingTypeUID      THING_TYPE_SHELLYHT          = new ThingTypeUID(BINDING_ID, "shellyht");
    public static final ThingTypeUID      THING_TYPE_SHELLYSENSE       = new ThingTypeUID(BINDING_ID, "shellysense");
    public static final ThingTypeUID      THING_TYPE_SHELLYSMOKE       = new ThingTypeUID(BINDING_ID, "shellysmoke");
    public static final ThingTypeUID      THING_TYPE_SHELLYRGBW2_COLOR = new ThingTypeUID(BINDING_ID, "shellyrgbw2-color");
    public static final ThingTypeUID      THING_TYPE_SHELLYRGBW2_WHITE = new ThingTypeUID(BINDING_ID, "shellyrgbw2-while");
    public static final ThingTypeUID      THING_TYPE_SHELLYSIMULATOR   = new ThingTypeUID(BINDING_ID, "shelly-simulator");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS   = Collections
            .unmodifiableSet(Stream.of(THING_TYPE_SHELLY1, THING_TYPE_SHELLY1PM,
                    THING_TYPE_SHELLY2_RELAY, THING_TYPE_SHELLY2_ROLLER, THING_TYPE_SHELLY25_RELAY, THING_TYPE_SHELLY25_ROLLER,
                    THING_TYPE_SHELLY4PRO,
                    THING_TYPE_SHELLYPLUG, THING_TYPE_SHELLYPLUGS,
                    THING_TYPE_SHELLYBULB, THING_TYPE_SHELLYRGBW2_COLOR, THING_TYPE_SHELLYRGBW2_WHITE,
                    THING_TYPE_SHELLYHT, THING_TYPE_SHELLYSENSE, THING_TYPE_SHELLYSMOKE,
                    THING_TYPE_SHELLYSIMULATOR)
                    .collect(Collectors.toSet()));

    public static final int               UPDATE_STATUS_INTERVAL       = 3; // check for updates every x sec
    public static final int               UPDATE_SKIP_COUNT            = 20; // update every x triggers or when a key was pressed
    public static final int               UPDATE_MIN_DELAY             = 15; // update every x triggers or when a key was pressed

    // List of all Configuration Properties
    public static final String            CONFIG_DEVICEIP              = "deviceIp";
    public static final String            CONFIG_UPDATE_INTERVAL       = "updateInterval";
    public static final String            CONFIG_UPDATE_HTTP_USER      = "userId";
    public static final String            CONFIG_UPDATE_HTTP_PWD       = "password";

    // Relay
    public static final String            CHANNEL_GROUP_RELAY_CONTROL  = "relay";
    public static final String            CHANNEL_RELAY_OUTPUT         = "output";
    public static final String            CHANNEL_RELAY_OVERPOWER      = "overpower";
    public static final String            CHANNEL_RELAY_TRIGGER        = "trigger";

    public static final String            CHANNEL_TIMER_AUTOON         = "autoOn";
    public static final String            CHANNEL_TIMER_AUTOOFF        = "autoOff";
    public static final String            CHANNEL_TIMER_ACTIVE         = "timerActive";

    // Roller
    public static final String            CHANNEL_GROUP_ROL_CONTROL    = "roller";
    public static final String            CHANNEL_ROL_CONTROL_TURN     = "turn";
    public static final String            CHANNEL_ROL_CONTROL_POS      = "position";
    public static final String            CHANNEL_ROL_CONTROL_TIMER    = "timer";
    public static final String            CHANNEL_ROL_CONTROL_STOPR    = "stopReason";
    public static final String            CHANNEL_ROL_CONTROL_DIR      = "direction";
    public static final String            CHANNEL_ROL_CONTROL_OVERT    = "overtemp";
    public static final String            CHANNEL_ROL_CONTROL_CALIB    = "calibrating";

    // Power meter
    public static final String            CHANNEL_GROUP_METER          = "meter";
    public static final String            CHANNEL_GROUP_METER1         = CHANNEL_GROUP_METER + "1";
    public static final String            CHANNEL_METER_CURRENTWATTS   = "currentWatts";
    public static final String            CHANNEL_METER_LASTMIN1       = "lastPower1";
    public static final String            CHANNEL_METER_LASTMIN2       = "lastPower2";
    public static final String            CHANNEL_METER_LASTMIN3       = "lastPower3";
    public static final String            CHANNEL_METER_TOTALWATTS     = "totalWatts";
    public static final String            CHANNEL_METER_MAXPOWER       = "maxPower";
    public static final String            CHANNEL_METER_TIMESTAMP      = "timestamp";

    public static final String            CHANNEL_GROUP_LED_CONTROL    = "led";
    public static final String            CHANNEL_LED_STATUS_DISABLE   = "statusLed";
    public static final String            CHANNEL_LED_POWER_DISABLE    = "powerLed";

    public static final String            CHANNEL_GROUP_SENSOR         = "sensor";
    public static final String            CHANNEL_SENSOR_TEMP          = "temperature";
    public static final String            CHANNEL_SENSOR_TUNIT         = "tempUnit";
    public static final String            CHANNEL_SENSOR_HUM           = "humidity";

    public static final String            CHANNEL_GROUP_BATTERY        = "battery";
    public static final String            CHANNEL_SENSOR_BAT_LEVEL     = "value";
    public static final String            CHANNEL_SENSOR_BAT_VOLT      = "voltage";

    public static final String            CHANNEL_GROUP_BULB_CONTROL   = "bulb";
    public static final String            CHANNEL_BULB_COLOR_MODE      = "colorMode";
    public static final String            CHANNEL_BULB_POWER           = "power";
    public static final String            CHANNEL_BULB_DEFSTATE        = "defaultState";

    public static final String            CHANNEL_GROUP_COLOR_CONTROL  = "color";
    public static final String            CHANNEL_COLOR_PICKER         = "color";
    public static final String            CHANNEL_COLOR_RED            = "red";
    public static final String            CHANNEL_COLOR_GREEN          = "green";
    public static final String            CHANNEL_COLOR_BLUE           = "blue";
    public static final String            CHANNEL_COLOR_WHITE          = "white";
    public static final String            CHANNEL_COLOR_GAIN           = "gain";
    public static final String            CHANNEL_COLOR_BRIGHTNESS     = "brightness";
    public static final String            CHANNEL_COLOR_TEMP           = "temperature";
    public static final String            CHANNEL_COLOR_EFFECT         = "effect";

    // color temperature: 3000 = warm, 4750 = white, 6565 = cold
    public static final int               MIN_COLOR_TEMPERATURE        = 3000;
    public static final int               MAX_COLOR_TEMPERATURE        = 6565;
    public static final int               COLOR_TEMPERATURE_RANGE      = MAX_COLOR_TEMPERATURE - MIN_COLOR_TEMPERATURE;
    public static final double            SATURATION_FACTOR            = 2.54;

}
