/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;
import static org.openhab.binding.shelly.internal.api.ShellyHttpApi.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.shelly.internal.ShellyConfiguration;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyShortStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusSensor;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi.ShellyDeviceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ShellyHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Hans-Jörg Merk - Initial contribution
 * @author Markus Michels - Completely refactored
 */
public class ShellyHandler extends BaseThingHandler implements ShellyDeviceListener {
    private final Logger                logger           = LoggerFactory.getLogger(ShellyHandler.class);

    private final NetworkAddressService networkAddressService;
    private final ShellyHandlerFactory  handlerFactory;
    private ShellyConfiguration         config;
    private ShellyHttpApi               api;
    private ShellyDeviceProfile         profile;

    private ScheduledFuture<?>          statusJob;
    private int                         skipUpdate       = 0;
    private int                         scheduledUpdates = 0;
    private int                         skipCount        = UPDATE_SKIP_COUNT;
    private int                         skipRefresh      = 0;
    private int                         refreshCount     = UPDATE_SETTINGS_INTERVAL / UPDATE_STATUS_INTERVAL;
    private boolean                     refreshSettings  = false;
    private boolean                     channelCache     = false;
    protected boolean                   lockUpdates      = false;

    private String                      thingName        = "";
    private Map<String, Object>         channelData      = new HashMap<>();

    /**
     * @param handlerFactory        Handler Factory instance (will be used for event handler registration)
     * @param networkAddressService instance of NetworkAddressService to get access to the OH default ip settings
     */
    @SuppressWarnings("deprecation")
    public ShellyHandler(Thing thing, ShellyHandlerFactory handlerFactory,
            NetworkAddressService networkAddressService) {
        super(thing);
        this.handlerFactory = handlerFactory;
        this.networkAddressService = networkAddressService;
    }

    @Override
    public void initialize() {
        config = getConfigAs(ShellyConfiguration.class);
        if (config.updateInterval == 0) {
            config.updateInterval = UPDATE_STATUS_INTERVAL * UPDATE_SKIP_COUNT;
        }
        if (config.updateInterval < UPDATE_MIN_DELAY) {
            config.updateInterval = UPDATE_MIN_DELAY / UPDATE_STATUS_INTERVAL;
        }
        skipCount = config.updateInterval / UPDATE_STATUS_INTERVAL;

        config.localIp = networkAddressService.getPrimaryIpv4HostAddress();
        handlerFactory.registerDeviceListener(this);
        api = new ShellyHttpApi(config);

        // Example for background initialization:
        scheduler.schedule(() -> {
            try {
                logger.info("Device config: ipAddress={}, http user/password={}/{}, update interval={}, low battery threshold={}%",
                        config.deviceIp, config.userId, config.password.isEmpty() ? "" : "***", config.updateInterval, config.lowBattery);
                initializeThing();
            } catch (RuntimeException | IOException e) {
                logger.info("Unable to initialize thing {}: {}, retrying later", getThing().getLabel(), e.getMessage());
                // even this initialization failed we start the status update
                // the updateJob will then try to auto-initialize the thing
                // in this case the thing stays in status INITIALIZING
            } finally {
                // start initialization even the initial request to get settings failed
                if (statusJob == null || statusJob.isCancelled()) {
                    statusJob = scheduler.scheduleWithFixedDelay(this::updateStatus, 2,
                            UPDATE_STATUS_INTERVAL, TimeUnit.SECONDS);
                    Validate.notNull(statusJob, "statusJob must not be null");
                    logger.debug("Update status job started, interval={}*{}={}sec.", skipCount, UPDATE_STATUS_INTERVAL,
                            skipCount * UPDATE_STATUS_INTERVAL);
                }
            }

        }, 2, TimeUnit.SECONDS);
    }

    private void initializeThing() throws IOException {
        // Get the thing global settings and initialize device capabilities
        logger.info("Start initializing thing {}, ip address {}", getThing().getLabel(), config.deviceIp);
        channelData = new HashMap<>();  // clear any cached channels
        refreshSettings = false;
        lockUpdates = false;

        ShellyDeviceProfile p = api.getDeviceProfile(this.getThing().getThingTypeUID().getId());
        logger.info("Initializing device {}, type {}, Hardware: Rev: {}, batch {}; Firmware: {} / {} ({}); Thing Type={}",
                p.hostname, p.settings.device.type, p.hwRev, p.hwBatchId,
                p.fwVersion, p.fwDate, p.fwId, p.thingType);
        logger.debug(
                "Device {}: has relays: {}, is roller: {}, is Plug S: {},  is Bulb/RGBW2: {}, is HT/Smoke Sensor: {}, has is Sense: {}, Meter: {}, has Battery: {}, has LEDs: {}, numRelays={}, numRoller={}, numMeter={}",
                p.hostname, p.hasRelays, p.isRoller, p.isPlugS, p.isLight, p.isSensor, p.isSense, p.hasMeter, p.hasBattery, p.hasLed, p.numRelays,
                p.numRollers, p.numMeters);
        logger.debug("Shelly settings info for {} : {}", thingName, p.settingsJson);

        Map<String, String> properties = getThing().getProperties();
        Validate.notNull(properties, "properties must not be null!");
        thingName = (properties.get(PROPERTY_SERVICE_NAME) != null ? properties.get(PROPERTY_SERVICE_NAME) : p.hostname);
        thingName = thingName.toLowerCase();
        Validate.notNull(thingName, "thingName must not be null!");

        // update thing properties
        ShellySettingsStatus status = api.gerStatus();
        updateProperties(p, status);
        if (p.fwVersion.compareTo(SHELLY_API_MIN_FWVERSION) < 0) {
            logger.info("WARNING: Firmware of device {} too old, installed: {}/{} ({}), minimal {} is recommended",
                    thingName, p.fwVersion, p.fwDate, p.fwId, SHELLY_API_MIN_FWVERSION);
            logger.info(
                    "The binding was tested with Version 1.5+ only. Older versions might work, but doesn't support all features or lead into technical issues.");
            logger.info("You should consider to upgrade the device to v1.5.0 or newer!");
        }
        if (status.update.has_update) {
            logger.info("INFO: New firmware available for this device: current version: {}, new version: {}", status.update.old_version,
                    status.update.new_version);
        }

        String reqMode = StringUtils.substringAfter(p.thingType, "-");
        if (p.thingType.contains("-") && !p.mode.equals(reqMode)) {
            logger.info("Thing is not in {} mode, going offline. May run discovery to find the thing for the requested mode.");
        }

        api.setEventURLs(thingName);

        if (p.isSense) {
            logger.info("Sense stored key list loaded, {} entries.", p.irCodes.size());
        }

        profile = p; // all initialization done, so keep the profile
        requestUpdates(3, false); // request 3 updates in a row (during the furst 2+3*3 sec)
        logger.info("Thing {} successfully initialized.", thingName);
    }

    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (profile == null) {
                logger.info("Thing not yet initialized, command {} triggers initialization", command.toString());
                initializeThing();
            } else {
                profile = getProfile(false);
            }
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
                return;
            }

            // Process command
            String groupName = channelUID.getGroupId();
            Integer rIndex = 0;
            if (groupName.startsWith(CHANNEL_GROUP_RELAY_CONTROL) && groupName.length() > CHANNEL_GROUP_RELAY_CONTROL.length()) {
                rIndex = Integer.parseInt(StringUtils.substringAfter(channelUID.getGroupId(), CHANNEL_GROUP_RELAY_CONTROL)) - 1;
            } else if (groupName.startsWith(CHANNEL_GROUP_ROL_CONTROL) && groupName.length() > CHANNEL_GROUP_ROL_CONTROL.length()) {
                rIndex = Integer.parseInt(StringUtils.substringAfter(channelUID.getGroupId(), CHANNEL_GROUP_ROL_CONTROL)) - 1;
            }

            lockUpdates = true;
            switch (channelUID.getIdWithoutGroup()) {
                default:
                    logger.trace("Unknown command {} for device {}", channelUID.getAsString(), thingName);
                    return;
                case CHANNEL_RELAY_OUTPUT:
                    if (!profile.isRoller) {
                        // extract relay number of group name (relay0->0, relay1->1...)
                        api.setRelayTurn(rIndex, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                    } else {
                        logger.info("Shelly is in roller mode, channel command {} ignored", channelUID.toString());
                    }
                    break;
                case CHANNEL_ROL_CONTROL_POS:
                case CHANNEL_ROL_CONTROL_CONTROL:
                    logger.info("Roller command/position {}", command.toString());
                    boolean isControl = channelUID.getIdWithoutGroup().equals(CHANNEL_ROL_CONTROL_CONTROL);
                    Integer position = -1;

                    if (command instanceof UpDownType) {
                        ShellyControlRoller rstatus = api.getRollerStatus(rIndex);
                        if (!getString(rstatus.state).isEmpty() && !getString(rstatus.state).equals(SHELLY_ALWD_ROLLER_TURN_STOP)) {
                            logger.info("{}: Roller is already moving, ignore command {}", thingName, command.toString());
                            requestUpdates(1, false);
                            break;
                        }
                        if (UpDownType.UP.equals(command)) {
                            logger.info("{}: Open roller", thingName);
                            api.setRollerTurn(rIndex, SHELLY_ALWD_ROLLER_TURN_OPEN);
                            position = SHELLY_MAX_ROLLER_POS;

                        } else if (((command instanceof UpDownType) && UpDownType.DOWN.equals(command)) ||
                                ((command instanceof OnOffType) && OnOffType.OFF.equals(command))) {
                            logger.info("{}: Closing roller", thingName);
                            api.setRollerTurn(rIndex, SHELLY_ALWD_ROLLER_TURN_CLOSE);
                            position = SHELLY_MIN_ROLLER_POS;
                        }
                    } else if ((command instanceof StopMoveType) && StopMoveType.STOP.equals(command)) {
                        logger.info("{}: Stop roller", thingName);
                        api.setRollerTurn(rIndex, SHELLY_ALWD_ROLLER_TURN_STOP);
                    } else {
                        logger.info("Set roller to position {} (channel {}", command.toString(), channelUID.getIdWithoutGroup());
                        if (command instanceof PercentType) {
                            PercentType p = (PercentType) command;
                            position = p.intValue();
                        } else if (command instanceof DecimalType) {
                            DecimalType d = (DecimalType) command;
                            position = d.intValue();
                        } else {
                            throw new IllegalArgumentException("Invalid value type for roller control/posiution" + command.getClass().toString());
                        }

                        // take position from RollerShutter control and map to Shelly positon (OH: 0=closed, 100=open; Shelly 0=open, 100=closed)
                        // take position 1:1 from position channel
                        position = isControl ? SHELLY_MAX_ROLLER_POS - position : position;
                        validateRange("roller position", position, SHELLY_MIN_ROLLER_POS, SHELLY_MAX_ROLLER_POS);
                        logger.info("{}: Changing roller position to {}", thingName, position);
                        api.setRollerPos(rIndex, position);
                    }
                    if (position != -1) {
                        // make sure both are in sync
                        if (!isControl) {
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_CONTROL, new PercentType(SHELLY_MAX_ROLLER_POS - position));
                        } else {
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_POS, new PercentType(position));
                        }
                    }
                    // request updates the next 30sec to update roller position after it stopped
                    requestUpdates(45 / UPDATE_STATUS_INTERVAL, false);
                    break;
                case CHANNEL_TIMER_AUTOON:
                    logger.info("Set Auto-ON timer to {}", command.toString());
                    Validate.isTrue(command instanceof DecimalType, "Timer AutoOn: Invalid value type: " + command.getClass());
                    api.setTimer(rIndex, SHELLY_TIMER_AUTOON, ((DecimalType) command).doubleValue());
                    break;
                case CHANNEL_TIMER_AUTOOFF:
                    logger.info("Set Auto-OFF timer to {}", command.toString());
                    Validate.isTrue(command instanceof DecimalType, "Invalid value type");
                    api.setTimer(rIndex, SHELLY_TIMER_AUTOOFF, ((DecimalType) command).doubleValue());
                    break;
                case CHANNEL_LED_STATUS_DISABLE:
                    logger.info("Set STATUS LED disabled to {}", command.toString());
                    Validate.isTrue(command instanceof OnOffType, "Invalid value type");
                    api.setLedStatus(SHELLY_LED_STATUS_DISABLE, (OnOffType) command == OnOffType.ON);
                    break;
                case CHANNEL_LED_POWER_DISABLE:
                    logger.info("Set POWER LED disabled to {}", command.toString());
                    Validate.isTrue(command instanceof OnOffType, "Invalid value type");
                    api.setLedStatus(SHELLY_LED_POWER_DISABLE, (OnOffType) command == OnOffType.ON);
                    break;
                case CHANNEL_SENSE_KEY:
                    logger.info("Send key {}", command.toString());
                    api.sendIRKey(command.toString());
                    break;
            }

            requestUpdates(1, true);  // request an update and force a refresh of the settings
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})",
                    channelUID.toString(), e.getMessage(), e.getClass());
        } finally {
            lockUpdates = false;
        }
    }

    /**
     * Update device status and channels
     */
    protected void updateStatus() {
        try {
            skipUpdate++;
            if (lockUpdates) {
                logger.trace("Update locked, try on next cycle");
                return;
            }

            if ((skipUpdate % refreshCount == 0) && (profile != null) && (getThing().getStatus() == ThingStatus.ONLINE)) {
                refreshSettings |= !profile.hasBattery;
            }

            if (refreshSettings || (scheduledUpdates > 0) || (skipUpdate % skipCount == 0)) {
                if ((profile == null) || (getThing().getStatus() == ThingStatus.OFFLINE)) {
                    logger.info("Status update triggered thing initialization for device {}", thingName);
                    initializeThing();  // may fire an exception if initialization failed
                }

                // Get profile, if refreshSettings == true reload settings from device
                profile = getProfile(refreshSettings);

                logger.trace("Updating status for device {}", thingName);
                ShellySettingsStatus status;
                status = api.gerStatus();
                logger.debug("Shelly status info for {}: {}", thingName, status.json);

                // map status to channels
                if (profile.hasRelays && !profile.isRoller) {
                    logger.trace("{}: Updating {} relay(s)", thingName, profile.numRelays);
                    int i = 0;
                    ShellyStatusRelay rstatus = api.getRelayStatus(i);
                    if (rstatus != null) {
                        for (ShellyShortStatusRelay relay : rstatus.relays) {
                            if ((relay.is_valid == null) || relay.is_valid) {
                                Integer r = i + 1;
                                String groupName = profile.numRelays == 1 ? CHANNEL_GROUP_RELAY_CONTROL : CHANNEL_GROUP_RELAY_CONTROL + r.toString();
                                updateChannel(groupName, CHANNEL_RELAY_OUTPUT, getBool(relay.ison) ? OnOffType.ON : OnOffType.OFF);
                                updateChannel(groupName, CHANNEL_RELAY_OVERPOWER, getBool(relay.overpower));
                                updateChannel(groupName, CHANNEL_TIMER_ACTIVE, getBool(relay.has_timer) ? OnOffType.ON : OnOffType.OFF);
                                ShellySettingsRelay rsettings = profile.settings.relays.get(i);
                                if (rsettings != null) {
                                    updateChannel(groupName, CHANNEL_TIMER_AUTOON, getDouble(rsettings.auto_on));
                                    updateChannel(groupName, CHANNEL_TIMER_AUTOOFF, getDouble(rsettings.auto_off));
                                }
                            }
                            i++;
                        }
                    }
                }
                if (profile.hasRelays && profile.isRoller && (status.rollers != null)) {
                    logger.trace("{}: Updating {} rollers", thingName, profile.numRollers);
                    int i = 0;
                    for (ShellySettingsRoller roller : status.rollers) {
                        if (roller.is_valid) {
                            ShellyControlRoller control = api.getRollerStatus(i);
                            Integer relayIndex = i + 1;
                            String groupName = profile.numRollers == 1 ? CHANNEL_GROUP_ROL_CONTROL
                                    : CHANNEL_GROUP_ROL_CONTROL + relayIndex.toString();
                            // updateChannel(groupName, CHANNEL_ROL_CONTROL_CONTROL, getString(control.state));
                            if (getString(control.state).equals(SHELLY_ALWD_ROLLER_TURN_STOP)) { // only valid in stop state
                                updateChannel(groupName, CHANNEL_ROL_CONTROL_CONTROL,
                                        new PercentType(SHELLY_MAX_ROLLER_POS - getInteger(control.current_pos)));
                                updateChannel(groupName, CHANNEL_ROL_CONTROL_POS, new PercentType(getInteger(control.current_pos)));
                            }
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_DIR, getString(control.last_direction));
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_STOPR, getString(control.stop_reason));
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_OVERT, getBool(control.overtemperature));

                            i = i + 1;
                        }
                    }
                }

                if (profile.hasMeter && (status.meters != null)) {
                    if (!profile.isRoller) {
                        logger.trace("{}: Updating {} standard meters", thingName, profile.numMeters);

                        // In Relay mode we map eacher meter to the matching channel group
                        int m = 0;
                        for (ShellySettingsMeter meter : status.meters) {
                            Integer meterIndex = m + 1;
                            if (meter.is_valid || profile.isLight) {   // RGBW2-white doesn't report das flag correctly in white mode
                                String groupName = "";
                                if (profile.numMeters > 1) {
                                    groupName = CHANNEL_GROUP_METER + meterIndex.toString();
                                } else {
                                    groupName = CHANNEL_GROUP_METER;
                                }
                                updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, getDouble(meter.power));
                                if (meter.total != null) {
                                    Double kwh = getDouble(meter.total); // Watt/Min
                                    kwh = kwh / (60.0 * 1000.0);  // convert Watt/Min to kw/h
                                    updateChannel(groupName, CHANNEL_METER_TOTALWATTS, kwh);
                                }
                                if (meter.counters != null) {
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN1, getDouble(meter.counters[0]));
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN2, getDouble(meter.counters[1]));
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN3, getDouble(meter.counters[2]));
                                }
                                updateChannel(groupName, CHANNEL_METER_TIMESTAMP, ShellyHandlerFactory.convertTimestamp(getLong(meter.timestamp)));
                                m++;
                            }
                        }
                    } else {
                        // In Roller Mode we accumulate all meters to a single set of meters
                        logger.debug("{}: Updating roller meter", thingName);
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
                        updateChannel(groupName, CHANNEL_METER_LASTMIN1, lastMin1);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN2, lastMin2);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN3, lastMin3);

                        // convert totalWatts into kw/h
                        totalWatts = totalWatts / (60.0 * 10000.0);
                        updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, currentWatts);
                        updateChannel(groupName, CHANNEL_METER_TOTALWATTS, totalWatts);
                        updateChannel(groupName, CHANNEL_METER_TIMESTAMP, ShellyHandlerFactory.convertTimestamp(timestamp));
                    }
                }

                if (profile.hasLed) {
                    Validate.notNull(profile, "LED update: ShellyDeviceProfile must not be null!");
                    Validate.notNull(profile.settings.led_status_disable, "LED update: led_status_disable must not be null!");
                    Validate.notNull(profile.settings.led_power_disable, "LED update: led_power_disable must not be null!");
                    logger.debug("LED disabled status: status led: {}, powerLed: {}", profile.settings.led_status_disable,
                            profile.settings.led_power_disable);
                    updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_STATUS_DISABLE, getBool(profile.settings.led_status_disable));
                    updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_POWER_DISABLE, getBool(profile.settings.led_power_disable));
                }

                if (profile.isSensor || profile.hasBattery) {
                    logger.debug("{}: Updating sensor", thingName);
                    ShellyStatusSensor sdata = api.getSensorStatus();
                    if (sdata != null) {
                        if (getBool(sdata.tmp.is_valid)) {
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                                    getString(sdata.tmp.units).toUpperCase().equals(SHELLY_TEMP_CELSIUS) ? getDouble(sdata.tmp.tC)
                                            : getDouble(sdata.tmp.tF));
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TUNIT, getString(sdata.tmp.units));
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM, getDouble(sdata.hum.value));
                        }
                        if ((sdata.lux != null) && getBool(sdata.lux.is_valid)) {
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_LUX, getDouble(sdata.lux.value));
                        }
                        if (sdata.bat != null) {
                            logger.trace("{}: Updating battery", thingName);
                            updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL, getDouble(sdata.bat.value));
                            updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LOW,
                                    getDouble(sdata.bat.value) < config.lowBattery ? true : false);
                            if (sdata.bat.value != null) {  // no update for Sense
                                updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_VOLT, getDouble(sdata.bat.voltage));
                            }

                        }
                        if (profile.isSense) {
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION, getBool(sdata.motion));
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_CHARGER, getBool(sdata.charger));
                        }
                    }
                }

                // update thing status from specific thing handlers
                updateThingStatus();

                // update some properties
                updateProperties(profile, status);

                // If status update was successful the thing must be online
                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    logger.info("Thing {}({}) is now online", getThing().getLabel(), thingName);
                    updateStatus(ThingStatus.ONLINE);  // if API call was successful the thing must be online
                }

                if (scheduledUpdates > 0) {
                    --scheduledUpdates;
                    logger.debug("{} more updates requested", scheduledUpdates);
                }
                if (!channelCache && (scheduledUpdates == 0)) {
                    // logger.debug("Enabling channel cache for device {}", thingName);
                    // channelCache = true;
                }
            } else {
                // logger.trace("Update skipped {}/{}", (skipUpdate - 1) % skipCount, skipCount);
            }
        } catch (

        IOException e) {
            // http call failed: go offline except for battery devices, which might be in sleep mode
            // once the next update is successful the device goes back online
            if (e.getMessage().contains("Timeout")) {
                logger.info("Thing {} not reachable, update canceled!", getThing().getLabel());
            } else {
                logger.debug("Unable to update status for thing {}: {} ({})", getThing().getLabel(), e.getMessage(), e.getClass());
            }
            if (profile != null && !profile.isSensor) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        } catch (RuntimeException e) {
            logger.debug("Unable to update status for thing {}: {} ({})", getThing().getLabel(), e.getMessage(), e.getClass());
        }

    }

    /**
     * Device specific handlers are overriding this method to do additional stuff
     *
     * @throws IOException Communication problem on the API call
     */
    public void updateThingStatus() throws IOException {
        logger.trace("No secondary updates for device {}", thingName);
    }

    /**
     * Callback for device events
     *
     * @param deviceName device receiving the event
     * @param parameters parameters from the event url
     * @param data       the html input data
     */
    @Override
    public void onEvent(String deviceName, String deviceIndex, String type, Map<String, String[]> parameters, String data) {
        if (thingName.equals(deviceName)) {
            logger.debug("Event received for device {}: class={}, index={}, parameters={}", deviceName, type, deviceIndex,
                    parameters.toString());
            if (profile == null) {
                logger.info("Device {} is not yet initialized, event will trigger initialization", deviceName);
            }

            int i = 0;
            String payload = "{\"device\":\"" + deviceName + "\", \"class\":\"" + type + "\", \"index\":\"" + deviceIndex
                    + "\",\"parameters\":[";
            for (String key : parameters.keySet()) {
                if (i++ > 0) {
                    payload = payload + ", ";
                }
                String[] values = parameters.get(key);
                payload = payload + "{\"" + key + "\":\"" + values[0] + "\"}";
            }
            payload = payload + "]}";

            String channel = "";
            Integer rindex = !deviceIndex.isEmpty() ? Integer.parseInt(deviceIndex) + 1 : -1;
            if (type.equals(EVENT_TYPE_RELAY) && profile.hasRelays) {
                channel = profile.numRelays == 1 ? CHANNEL_GROUP_RELAY_CONTROL : CHANNEL_GROUP_RELAY_CONTROL + rindex.toString();
            }
            if (type.equals(EVENT_TYPE_ROLLER) && profile.hasRelays) {
                channel = profile.numRollers == 1 ? CHANNEL_GROUP_ROL_CONTROL : CHANNEL_GROUP_ROL_CONTROL + rindex.toString();
            }
            if (type.equals(EVENT_TYPE_SENSORDATA)) {
                channel = CHANNEL_GROUP_SENSOR;
            }
            Validate.isTrue(!channel.isEmpty(), "Unsupported event class: " + type);

            channel = channel + CHANNEL_GROUP_SEPARATOR + CHANNEL_EVENT_TRIGGER;
            logger.debug("Trigger {} event, channel {}, payload={}", type, channel, payload);
            triggerChannel(channel, payload);

            requestUpdates(scheduledUpdates > 0 ? 0 : 1, true);    // request update on next interval
        }
    }

    protected ShellyHttpApi getShellyApi() {
        return api;
    }

    protected ShellyDeviceProfile getDeviceProfile() {
        return profile;
    }

    protected boolean requestUpdates(int requestCount, boolean refreshSettings) {
        this.refreshSettings |= refreshSettings;
        if (refreshSettings) {
            logger.debug("Request a refresh of the settings for device {}", thingName);
        }
        if (scheduledUpdates < 10) {  // < 30s
            scheduledUpdates += requestCount;
            return true;
        }
        return false;
    }

    protected boolean updateChannel(String group, String channel, Object value) {
        if (value == null) {
            logger.trace("Update channel: value is null!");
            return false;
        }
        try {
            String channelId = mkChannelName(group, channel);
            Object current = channelData.get(channelId);
            // logger.trace("Predict channel {}.{} to become {} (type {}).", group, channel, value, value.getClass());
            if (!channelCache || (current == null) || !current.equals(value)) {
                if (value instanceof String) {
                    updateState(channelId, new StringType((String) value));
                }
                if (value instanceof Integer) {
                    Integer v = (Integer) value;
                    updateState(channelId, new DecimalType(v));
                }
                if (value instanceof Long) {
                    Long v = (Long) value;
                    updateState(channelId, new DecimalType(v));
                }
                if (value instanceof Double) {
                    Double v = (Double) value;
                    updateState(channelId, new DecimalType(v));
                }
                if (value instanceof Boolean) {
                    Boolean v = (Boolean) value;
                    updateState(channelId, v ? OnOffType.ON : OnOffType.OFF);
                }
                if (value instanceof OnOffType) {
                    OnOffType v = (OnOffType) value;
                    updateState(channelId, v);
                }
                if (value instanceof PercentType) {
                    PercentType v = (PercentType) value;
                    updateState(channelId, new PercentType(new BigDecimal(v.doubleValue())));
                }
                if (value instanceof HSBType) {
                    HSBType v = (HSBType) value;
                    updateState(channelId, v);
                }
                if (current == null) {
                    channelData.put(channelId, value);
                } else {
                    channelData.replace(channelId, value);
                }
                logger.trace("Channel {}.{} updated with {} (type {}).", group, channel, value, value.getClass());
                return true;
            }
        } catch (RuntimeException e) {
            logger.warn("Unable to update channel {}.{}#{} with {} (type {}): {} ({})", thingName, group, channel, value, value.getClass(),
                    e.getMessage(), e.getClass());
        }
        return false;

    }

    protected Object getChannelValue(String group, String channel) {
        String key = mkChannelName(group, channel);
        return channelData.get(key);
    }

    protected void updateProperties(ShellyDeviceProfile profile, ShellySettingsStatus status) {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(PROPERTY_MODE, profile.mode);
        properties.put(PROPERTY_TIME, status.time);
        properties.put(PROPERTY_UPTIME, status.uptime.toString() + "sec");
        if (status.wifi_sta != null) {
            properties.put(PROPERTY_WIFI_NETW, getString(status.wifi_sta.ssid));
            properties.put(PROPERTY_WIFI_RSSI, getInteger(status.wifi_sta.rssi).toString());
            properties.put(PROPERTY_WIFI_IP, getString(status.wifi_sta.ip));
        }
        properties.put(PROPERTY_UPDATE_STATUS, status.update.status);
        properties.put(PROPERTY_UPDATE_AVAILABLE, status.update.has_update ? "yes" : "no");
        properties.put(PROPERTY_UPDATE_CURR_VERS, status.update.old_version);
        properties.put(PROPERTY_UPDATE_NEWV_ERS, status.update.new_version);
        if (profile.settings.max_power != null) {
            properties.put(PROPERTY_MAX_POWER, getDouble(profile.settings.max_power).toString());
        }
        /*
         * if (profile.settings.dcpower != null) { Double maxPower = getDouble(profile.settings.dcpower); properties.put("dcPower",
         * maxPower.toString()); }
         */

        updateProperties(properties);
        logger.trace("{}: Properties updated", thingName);
    }

    protected static String mkChannelName(String group, String channel) {
        return group + "#" + channel;
    }

    protected ShellyDeviceProfile getProfile(boolean forceRefresh) throws IOException {
        refreshSettings |= forceRefresh;

        if (refreshSettings) {
            logger.trace("Refresh settings for device {}", thingName);
            profile = api.getDeviceProfile(this.getThing().getThingTypeUID().getId());
            refreshSettings = false;
        }

        return profile;

    }

    protected void validateRange(String name, Integer value, Integer min, Integer max) {
        Validate.isTrue((value >= min) && (value <= max), "Value " + name + " is out of range (" + min.toString() + "-" + max.toString() + ")");
    }

    @Override
    public void dispose() {
        logger.debug("Shutdown thing {}", thingName);
        try {
            if (statusJob != null) {
                statusJob.cancel(true);
                statusJob = null;
                logger.debug("Shelly statusJob topped");
            }
        } catch (Exception e) {
            logger.debug("Exception on dispose(): {} ({})", e.getMessage(), e.getClass());
        } finally {
            super.dispose();
        }
    }
}
