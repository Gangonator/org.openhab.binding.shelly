package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;

import java.io.IOException;

import org.apache.commons.lang.Validate;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.openhab.binding.shelly.internal.ShellyHandlerFactory;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsDimmer;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsEMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsLight;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyShortStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusSensor;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi.ShellyDeviceProfile;

public class ShellyUpdater {

    /**
     * Update Relay/Roller channels
     *
     * @param th      Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status  Last ShellySettingsStatus
     *
     * @throws IOException
     */
    public static void updateRelays(ShellyHandler th, ShellyDeviceProfile profile, ShellySettingsStatus status)
            throws IOException {
        if (profile.hasRelays && !profile.isRoller && !profile.isDimmer) {
            th.logger.trace("{}: Updating {} relay(s)", th.thingName, profile.numRelays);
            int i = 0;
            ShellyStatusRelay rstatus = th.api.getRelayStatus(i);
            if (rstatus != null) {
                for (ShellyShortStatusRelay relay : rstatus.relays) {
                    if ((relay.is_valid == null) || relay.is_valid) {
                        Integer r = i + 1;
                        String groupName = profile.numRelays == 1 ? CHANNEL_GROUP_RELAY_CONTROL
                                : CHANNEL_GROUP_RELAY_CONTROL + r.toString();
                        th.updateChannel(groupName, CHANNEL_RELAY_OUTPUT, getOnOff(relay.ison));
                        th.updateChannel(groupName, CHANNEL_RELAY_OVERPOWER, getOnOff(relay.overpower));
                        th.updateChannel(groupName, CHANNEL_TIMER_ACTIVE, getOnOff(relay.has_timer));
                        ShellySettingsRelay rsettings = profile.settings.relays.get(i);
                        if (rsettings != null) {
                            th.updateChannel(groupName, CHANNEL_TIMER_AUTOON, getDecimal(rsettings.auto_on));
                            th.updateChannel(groupName, CHANNEL_TIMER_AUTOOFF, getDecimal(rsettings.auto_off));
                        }
                    }
                    i++;
                }
            }
        }
        if (profile.hasRelays && profile.isRoller && (status.rollers != null)) {
            th.logger.trace("{}: Updating {} rollers", th.thingName, profile.numRollers);
            int i = 0;
            for (ShellySettingsRoller roller : status.rollers) {
                if (roller.is_valid) {
                    ShellyControlRoller control = th.api.getRollerStatus(i);
                    Integer relayIndex = i + 1;
                    String groupName = profile.numRollers == 1 ? CHANNEL_GROUP_ROL_CONTROL
                            : CHANNEL_GROUP_ROL_CONTROL + relayIndex.toString();
                    // updateChannel(groupName, CHANNEL_ROL_CONTROL_CONTROL, new
                    // StringType(getString(control.state)));
                    if (getString(control.state).equals(SHELLY_ALWD_ROLLER_TURN_STOP)) { // only valid in stop state
                        th.updateChannel(groupName, CHANNEL_ROL_CONTROL_CONTROL,
                                new PercentType(SHELLY_MAX_ROLLER_POS - getInteger(control.current_pos)));
                        th.updateChannel(groupName, CHANNEL_ROL_CONTROL_POS,
                                new PercentType(getInteger(control.current_pos)));
                        th.scheduledUpdates = 1; // one more poll and then stop
                    }
                    th.updateChannel(groupName, CHANNEL_ROL_CONTROL_DIR, getStringType(control.last_direction));
                    th.updateChannel(groupName, CHANNEL_ROL_CONTROL_STOPR, getStringType(control.stop_reason));
                    th.updateChannel(groupName, CHANNEL_ROL_CONTROL_OVERT, getOnOff(control.overtemperature));

                    i = i + 1;
                }
            }
        }
    }

    /**
     * Update Relay/Roller channels
     *
     * @param th      Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status  Last ShellySettingsStatus
     *
     * @throws IOException
     */
    public static void updateDimmers(ShellyHandler th, ShellyDeviceProfile profile, ShellySettingsStatus status)
            throws IOException {
        if (profile.isDimmer) {
            int i = 0;

            Validate.notNull(status.lights, "status.lights must not be null!");
            Validate.notNull(status.tmp, "status.tmp must not be null!");
            th.logger.trace("{}: Updating {} dimmers(s)", th.thingName, status.lights.size());
            if (status.tmp.is_valid) {
                th.updateChannel(CHANNEL_GROUP_DIMMER_STATUS, CHANNEL_DIMMER_TEMP, getDecimal(status.tmp.tC));
                th.updateChannel(CHANNEL_GROUP_DIMMER_STATUS, CHANNEL_DIMMER_OVERTEMP, getOnOff(status.overtemperature));
            }
            th.updateChannel(CHANNEL_GROUP_DIMMER_STATUS, CHANNEL_DIMMER_LOAD_ERROR, getOnOff(status.loaderror));
            th.updateChannel(CHANNEL_GROUP_DIMMER_STATUS, CHANNEL_DIMMER_OVERLOAD, getOnOff(status.overload));

            for (ShellySettingsLight dimmer : status.lights) {
                Integer r = i + 1;
                String groupName = profile.numRelays <= 1 ? CHANNEL_GROUP_DIMMER_CONTROL
                        : CHANNEL_GROUP_DIMMER_CONTROL + r.toString();
                th.updateChannel(groupName, CHANNEL_DIMMER_OUTPUT, getOnOff(dimmer.ison));
                th.updateChannel(groupName, CHANNEL_DIMMER_BRIGHTNESS,
                        new PercentType(getInteger(dimmer.brightness)));

                ShellySettingsDimmer dsettings = profile.settings.dimmers.get(i);
                if (dsettings != null) {
                    th.updateChannel(groupName, CHANNEL_TIMER_AUTOON, getDecimal(dsettings.auto_on));
                    th.updateChannel(groupName, CHANNEL_TIMER_AUTOOFF, getDecimal(dsettings.auto_off));
                }
                i++;
            }
        }
    }

    /**
     * Update Meter channel
     *
     * @param th      Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status  Last ShellySettingsStatus
     */
    public static void updateMeters(ShellyHandler th, ShellyDeviceProfile profile, ShellySettingsStatus status) {

        if (profile.hasMeter && ((status.meters != null) || (status.emeters != null))) {
            if (!profile.isRoller) {
                th.logger.trace("{}: Updating {} {}meter(s)", th.thingName, profile.numMeters, !profile.isEMeter ? "standard " : "e-");

                // In Relay mode we map eacher meter to the matching channel group
                int m = 0;
                if (!profile.isEMeter) {
                    for (ShellySettingsMeter meter : status.meters) {
                        Integer meterIndex = m + 1;
                        if (getBool(meter.is_valid) || profile.isLight) { // RGBW2-white doesn't report das flag
                                                                          // correctly in white mode
                            String groupName = "";
                            if (profile.numMeters > 1) {
                                groupName = CHANNEL_GROUP_METER + meterIndex.toString();
                            } else {
                                groupName = CHANNEL_GROUP_METER;
                            }
                            th.updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, getDecimal(meter.power));
                            if (meter.total != null) {
                                th.updateChannel(groupName, CHANNEL_METER_TOTALKWH,
                                        getDecimal(getDouble(meter.total) / (60.0 * 1000.0))); // convert
                                // Watt/Min to
                                // kw/h
                            }
                            if (meter.counters != null) {
                                th.updateChannel(groupName, CHANNEL_METER_LASTMIN1, getDecimal(meter.counters[0]));
                                th.updateChannel(groupName, CHANNEL_METER_LASTMIN2, getDecimal(meter.counters[1]));
                                th.updateChannel(groupName, CHANNEL_METER_LASTMIN3, getDecimal(meter.counters[2]));
                            }
                            th.updateChannel(groupName, CHANNEL_METER_TIMESTAMP,
                                    new StringType(ShellyHandlerFactory.convertTimestamp(getLong(meter.timestamp))));
                            m++;
                        }
                    }
                } else {
                    for (ShellySettingsEMeter emeter : status.emeters) {
                        Integer meterIndex = m + 1;
                        if (emeter.is_valid) {
                            String groupName = "";
                            if (profile.numMeters > 1) {
                                groupName = CHANNEL_GROUP_METER + meterIndex.toString();
                            } else {
                                groupName = CHANNEL_GROUP_METER;
                            }
                            if (emeter.is_valid) {
                                // convert Watt/Hour tok w/h
                                th.updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, getDecimal(emeter.power));
                                th.updateChannel(groupName, CHANNEL_METER_TOTALKWH,
                                        new DecimalType(getDouble(emeter.total) / 1000));
                                th.updateChannel(groupName, CHANNEL_EMETER_TOTALRET,
                                        new DecimalType(getDouble(emeter.total_returned) / 1000));
                                th.updateChannel(groupName, CHANNEL_EMETER_REACTWATTS, getDecimal(emeter.reactive));
                                th.updateChannel(groupName, CHANNEL_EMETER_VOLTAGE, getDecimal(emeter.voltage));
                            }
                            m++;
                        }
                    }
                }
            } else {
                // In Roller Mode we accumulate all meters to a single set of meters
                th.logger.debug("{}: Updating roller meter", th.thingName);
                Double currentWatts = 0.0;
                Double totalWatts = 0.0;
                Double lastMin1 = 0.0;
                Double lastMin2 = 0.0;
                Double lastMin3 = 0.0;
                Long timestamp = 0l;
                String groupName = CHANNEL_GROUP_METER;
                for (ShellySettingsMeter meter : status.meters) {
                    if (meter.is_valid) {
                        currentWatts += getDouble(meter.power);
                        totalWatts += getDouble(meter.total);
                        if (meter.counters != null) {
                            lastMin1 += getDouble(meter.counters[0]);
                            lastMin2 += getDouble(meter.counters[1]);
                            lastMin3 += getDouble(meter.counters[2]);
                        }
                        if (getLong(meter.timestamp) > timestamp) {
                            timestamp = getLong(meter.timestamp);
                        }
                    }
                }
                th.updateChannel(groupName, CHANNEL_METER_LASTMIN1, new DecimalType(lastMin1));
                th.updateChannel(groupName, CHANNEL_METER_LASTMIN2, new DecimalType(lastMin2));
                th.updateChannel(groupName, CHANNEL_METER_LASTMIN3, new DecimalType(lastMin3));

                // convert totalWatts into kw/h
                totalWatts = totalWatts / (60.0 * 10000.0);
                th.updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, new DecimalType(currentWatts));
                th.updateChannel(groupName, CHANNEL_METER_TOTALKWH, new DecimalType(totalWatts));
                th.updateChannel(groupName, CHANNEL_METER_TIMESTAMP,
                        new StringType(ShellyHandlerFactory.convertTimestamp(timestamp)));
            }
        }
    }

    /**
     * Update LED channels
     *
     * @param th      Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status  Last ShellySettingsStatus
     */
    public static void updateLed(ShellyHandler th, ShellyDeviceProfile profile, ShellySettingsStatus status) {
        if (profile.hasLed) {
            Validate.notNull(profile.settings.led_status_disable, "LED update: led_status_disable must not be null!");
            Validate.notNull(profile.settings.led_power_disable, "LED update: led_power_disable must not be null!");
            th.logger.debug("{}: LED disabled status: powerLed: {}, : statusLed{}",
                    th.thingName, getBool(profile.settings.led_power_disable),
                    getBool(profile.settings.led_status_disable));
            th.updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_STATUS_DISABLE,
                    getOnOff(profile.settings.led_status_disable));
            th.updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_POWER_DISABLE,
                    getOnOff(profile.settings.led_power_disable));
        }
    }

    /**
     * Update Sensor channel
     *
     * @param th      Thing Handler instance
     * @param profile ShellyDeviceProfile
     * @param status  Last ShellySettingsStatus
     *
     * @throws IOException
     */
    public static void updateSensors(ShellyHandler th, ShellyDeviceProfile profile, ShellySettingsStatus status)
            throws IOException {

        if (profile.isSensor || profile.hasBattery) {
            th.logger.debug("{}: Updating sensor", th.thingName);
            ShellyStatusSensor sdata = th.api.getSensorStatus();
            if (sdata != null) {
                if (getBool(sdata.tmp.is_valid)) {
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                            getString(sdata.tmp.units).toUpperCase().equals(SHELLY_TEMP_CELSIUS)
                                    ? getDecimal(sdata.tmp.tC)
                                    : getDecimal(sdata.tmp.tF));
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TUNIT, getStringType(sdata.tmp.units));
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM, getDecimal(sdata.hum.value));
                }
                if ((sdata.lux != null) && getBool(sdata.lux.is_valid)) {
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_LUX, getDecimal(sdata.lux.value));
                }
                if (sdata.flood != null) {
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_FLOOD, getOnOff(sdata.flood));
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_RAIN_MODE, getOnOff(sdata.rain_sensor));
                }
                if (sdata.bat != null) {
                    th.logger.trace("{}: Updating battery", th.thingName);
                    th.updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL, getDecimal(sdata.bat.value));
                    th.updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LOW,
                            getDouble(sdata.bat.value) < th.config.lowBattery ? OnOffType.ON : OnOffType.OFF);
                    if (sdata.bat.value != null) { // no update for Sense
                        th.updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_VOLT, getDecimal(sdata.bat.voltage));
                    }
                }
                if (profile.isSense) {
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION, getOnOff(sdata.motion));
                    th.updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_CHARGER, getOnOff(sdata.charger));
                }
                if (sdata.act_reasons != null) {
                    String message = "";
                    for (int i = 0; i < sdata.act_reasons.length; i++) {
                        message = "[" + i + "]: " + sdata.act_reasons[i];
                    }
                    th.logger.debug("Last activate reasons: {}", message);
                }
            }
        }
    }

    /*
     * Helper functions
     */

    public static String mkChannelId(String group, String channel) {
        return group + "#" + channel;
    }

    public static String getString(String value) {
        return value != null ? value : "";
    }

    public static Integer getInteger(Integer value) {
        return (value != null ? (Integer) value : 0);
    }

    public static Long getLong(Long value) {
        return (value != null ? (Long) value : 0);
    }

    public static Double getDouble(Double value) {
        return (value != null ? (Double) value : 0);
    }

    public static Boolean getBool(Boolean value) {
        return (value != null ? (Boolean) value : false);
    }

    // as State

    public static StringType getStringType(String value) {
        return new StringType(value != null ? value : "");
    }

    public static DecimalType getDecimal(Double value) {
        return new DecimalType((value != null ? value : 0));
    }

    public static DecimalType getDecimal(Integer value) {
        return new DecimalType((value != null ? value : 0));
    }

    public static DecimalType getDecimal(Long value) {
        return new DecimalType((value != null ? value : 0));
    }

    public static OnOffType getOnOff(Boolean value) {
        return (value != null ? value ? OnOffType.ON : OnOffType.OFF : OnOffType.OFF);
    }

    public static void validateRange(String name, Integer value, Integer min, Integer max) {
        Validate.isTrue((value >= min) && (value <= max),
                "Value " + name + " is out of range (" + min.toString() + "-" + max.toString() + ")");
    }

}
