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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.shelly.internal.ShellyHandlerFactory;
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
import org.openhab.binding.shelly.internal.config.ShellyConfiguration;
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

    private String                      thingName        = "";
    private Map<String, Object>         channelData      = new HashMap<>();

    /**
     * @param handlerFactory        Handler Factory instance (will be used for event handler registration)
     * @param networkAddressService instance of NetworkAddressService to get access to the OH default ip settings
     */
    public ShellyHandler(Thing thing, ShellyHandlerFactory handlerFactory,
            NetworkAddressService networkAddressService) {
        super(thing);
        this.handlerFactory = handlerFactory;
        this.networkAddressService = networkAddressService;
        thingName = this.getThing().getUID().getThingTypeId() + "-" + this.getThing().getUID().getId();
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
        channelData = new HashMap<>();

        // Example for background initialization:
        scheduler.schedule(() -> {
            try {
                initializeThing();
            } catch (RuntimeException | IOException e) {
                logger.info("Unable to initialize thing {}: {}, retrying later", getThing().getLabel(), e.getMessage());
                // even this initialization failed we start the status update
                // the updateJob will then try to auto-initialize the thing
                // in this case the thing stays in status INITIALIZING
            }

        }, 2, TimeUnit.SECONDS);
    }

    private void initializeThing() throws IOException {
        // Get the thing global settings and initialize device capabilities
        logger.debug("Start initializing thing {}, ip address {}", getThing().getLabel(), config.deviceIp);
        ShellyDeviceProfile p = api.getDeviceProfile(this.getThing().getThingTypeUID().getId());
        logger.info("Initializing device {}, type {}, Hardware: Rev: {}, batch {}; Firmware: {} / {} ({}); Thing Type={}",
                p.hostname, p.settings.device.type, p.hwRev, p.hwBatchId,
                p.fwVersion, p.fwDate, p.fwId, p.thingType);
        logger.debug(
                "Device is has relays: {}, is roller: {}, is Plug S: {},  is Bulb: {}, is HT/Smoke Sensor: {}, has Meter: {}, has Battery: {}, has LEDs: {}",
                p.hasRelays, p.isRoller, p.isPlugS, p.isBulb, p.isSensor, p.hasMeter, p.hasBattery, p.hasLed);
        logger.debug("Shelly settings info for {} : {}", thingName, p.settingsJson);

        // update thing properties
        ShellySettingsStatus status = api.gerStatus();
        updateProperties(p, status);
        if (p.fwVersion.compareTo(SHELLY_API_MIN_FWVERSION) < 0) {
            logger.info("WARNING: Firmware of device {} too old, installed: {}/{} ({}), minimal {} is recommended",
                    p.hostname, p.fwVersion, p.fwDate, p.fwId, SHELLY_API_MIN_FWVERSION);
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

        api.setEventURLs();

        profile = p; // all initialization done, so keep the profile
        thingName = p.hostname;
        requestUpdates(3); // request 3 updates in a row (during the furst 2+3*3 sec)
        logger.info("Thing {} successfully initialized.", thingName);

        if (statusJob == null || statusJob.isCancelled()) {
            statusJob = scheduler.scheduleWithFixedDelay(this::updateStatus, 2,
                    UPDATE_STATUS_INTERVAL, TimeUnit.SECONDS);
            logger.debug("Update status job started, interval={}*{}={}sec.", skipCount, UPDATE_STATUS_INTERVAL,
                    skipCount * UPDATE_STATUS_INTERVAL);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
                return;
            }
            if (profile == null) {
                logger.info("Thing not yet initialized, command {} triggers initialization", command.toString());
                initializeThing();
            }

            // Process command
            String groupName = channelUID.getGroupId();
            Integer rIndex = 0;
            if (groupName.startsWith(CHANNEL_GROUP_RELAY_CONTROL) && groupName.length() > CHANNEL_GROUP_RELAY_CONTROL.length()) {
                rIndex = Integer.parseInt(StringUtils.substringAfter(channelUID.getGroupId(), CHANNEL_GROUP_RELAY_CONTROL)) - 1;
            }
            if (groupName.startsWith(CHANNEL_GROUP_ROL_CONTROL) && groupName.length() > CHANNEL_GROUP_ROL_CONTROL.length()) {
                rIndex = Integer.parseInt(StringUtils.substringAfter(channelUID.getGroupId(), CHANNEL_GROUP_ROL_CONTROL)) - 1;
            }
            switch (channelUID.getIdWithoutGroup()) {
                case CHANNEL_RELAY_OUTPUT:
                    if (!profile.isRoller) {
                        // extract relay number of group name (relay0->0, relay1->1...)
                        api.setRelayTurn(rIndex, (OnOffType) command == OnOffType.ON ? "ON" : "OFF");
                    } else {
                        logger.info("Shelly is in roller mode, channel command {} ignored", channelUID.toString());
                    }
                    break;
                case CHANNEL_ROL_CONTROL_TURN:
                    String turn = command.toString().toLowerCase();
                    if (turn.equalsIgnoreCase("on")) {
                        turn = SHELLY_ALWD_ROLLER_TURN_OPEN;
                    }
                    if (turn.equalsIgnoreCase("off")) {
                        turn = SHELLY_ALWD_ROLLER_TURN_CLOSE;
                    }
                    logger.info("Set roller turn mode to {}", turn);
                    api.setRollerTurn(rIndex, turn);
                    break;
                case CHANNEL_ROL_CONTROL_POS:
                    logger.info("Setting roller to position {}%", command.toString());
                    api.setRollerPos(rIndex, Integer.parseInt(command.toString()));
                    break;
                case CHANNEL_TIMER_AUTOON:
                    api.setTimer(rIndex, SHELLY_TIMER_AUTOON, ((DecimalType) command).doubleValue());
                    break;
                case CHANNEL_TIMER_AUTOOFF:
                    api.setTimer(rIndex, SHELLY_TIMER_AUTOOFF, ((DecimalType) command).doubleValue());
                    break;
                case CHANNEL_LED_STATUS_DISABLE:
                    logger.info("Set STATUS LED disabled to {}", command.toString());
                    api.setLedStatus(SHELLY_LED_STATUS_DISABLE, (OnOffType) command == OnOffType.ON);
                    break;
                case CHANNEL_LED_POWER_DISABLE:
                    logger.info("Set POWER LED disabled to {}", command.toString());
                    api.setLedStatus(SHELLY_LED_POWER_DISABLE, (OnOffType) command == OnOffType.ON);
                    break;
            }
            requestUpdates(1);
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})",
                    channelUID.toString(), e.getMessage(), e.getClass());
        }
    }

    /**
     * Update device status and channels
     */
    protected void updateStatus() {
        try {
            if ((scheduledUpdates > 0) || (skipUpdate++ % skipCount == 0)) {
                if ((profile == null) || (getThing().getStatus() == ThingStatus.OFFLINE)) {
                    logger.debug("Status update triggers thing initialization for device {}", thingName);
                    initializeThing();  // may fire an exception if initialization failed
                }

                logger.trace("Updating status for device {}", thingName);
                ShellySettingsStatus status = api.gerStatus();
                logger.debug("Shelly status info for {}: {}", thingName, status.json);

                // map status to channels
                if (profile.hasRelays && !profile.isRoller) {
                    int i = 0;
                    ShellyStatusRelay rstatus = api.getRelayStatus(i);
                    for (ShellyShortStatusRelay relay : rstatus.relays) {
                        if ((relay.is_valid == null) || relay.is_valid) {
                            Integer r = i + 1;
                            String groupName = CHANNEL_GROUP_RELAY_CONTROL + r.toString();
                            updateChannel(groupName, CHANNEL_RELAY_OUTPUT, getBool(relay.ison) ? OnOffType.ON : OnOffType.OFF);
                            updateChannel(groupName, CHANNEL_RELAY_OVERPOWER, getBool(relay.overpower));
                            if (relay.has_timer != null) {
                                updateChannel(groupName, CHANNEL_TIMER_ACTIVE, getBool(relay.has_timer) ? OnOffType.ON : OnOffType.OFF);
                                ShellySettingsRelay rsettings = profile.settings.relays.get(i);
                                if (rsettings != null) {
                                    updateChannel(groupName, CHANNEL_TIMER_AUTOON, getDouble(rsettings.auto_on));
                                    updateChannel(groupName, CHANNEL_TIMER_AUTOOFF, getDouble(rsettings.auto_off));
                                }
                            }
                        }
                        i++;
                    }
                }
                if (profile.hasRelays && profile.isRoller) {
                    int i = 0;
                    for (ShellySettingsRoller roller : status.rollers) {
                        if (roller.is_valid) {
                            ShellyControlRoller control = api.getRollerStatus(i);
                            Integer relayIndex = i + 1;
                            String groupName = CHANNEL_GROUP_ROL_CONTROL + relayIndex.toString();
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_TURN, getString(control.state));
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_POS, getInteger(control.current_pos));
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_DIR, getString(control.last_direction));
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_STOPR, getString(control.stop_reason));
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_OVERT, getBool(control.overtemperature));
                            i = i + 1;
                        }
                    }
                }

                if (profile.hasMeter) {
                    if (!profile.isRoller) {
                        // In Relay mode we map eacher meter to the matching channel group
                        int m = 0;
                        for (ShellySettingsMeter meter : status.meters) {
                            Integer meterIndex = m + 1;
                            if (meter.is_valid) {
                                String groupName = !profile.isBulb ? CHANNEL_GROUP_METER + meterIndex.toString() : CHANNEL_GROUP_METER;
                                updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, getDouble(meter.power));
                                if (meter.total != null) {
                                    updateChannel(groupName, CHANNEL_METER_TOTALWATTS, getDouble(meter.total));
                                }
                                if (meter.counters != null) {
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN1, getDouble(meter.counters[0]));
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN2, getDouble(meter.counters[1]));
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN3, getDouble(meter.counters[2]));
                                }
                                updateChannel(groupName, CHANNEL_METER_TIMESTAMP, convertTimestamp(getLong(meter.timestamp)));
                                m++;
                            }
                        }
                    } else {
                        // In Roller Mode we accumulate all meters to a single set of meters
                        Double currentWatts = 0.0;
                        Double totalWatts = 0.0;
                        Double lastMin1 = 0.0;
                        Double lastMin2 = 0.0;
                        Double lastMin3 = 0.0;
                        Long timestamp = 0l;
                        String groupName = CHANNEL_GROUP_METER + "1";
                        for (ShellySettingsMeter meter : status.meters) {
                            if (meter.is_valid) {
                                currentWatts += getDouble(meter.power);
                                totalWatts += getDouble(meter.total);
                                if (meter.counters != null) {
                                    lastMin1 += getDouble(meter.counters[0]);
                                    lastMin1 += getDouble(meter.counters[1]);
                                    lastMin1 += getDouble(meter.counters[2]);
                                }
                                if (getLong(meter.timestamp) > timestamp) {
                                    timestamp = getLong(meter.timestamp);
                                }
                            }
                        }

                        updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, currentWatts);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN1, lastMin1);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN2, lastMin2);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN3, lastMin3);
                        updateChannel(groupName, CHANNEL_METER_TOTALWATTS, totalWatts);
                        updateChannel(groupName, CHANNEL_METER_TIMESTAMP, convertTimestamp(timestamp));
                    }
                }
                if (profile.settings.max_power != null) {
                    updateChannel(CHANNEL_GROUP_METER1, CHANNEL_METER_MAXPOWER, getDouble(profile.maxPower));
                }

                if (profile.hasLed) {
                    ShellyDeviceProfile profile = api.getDeviceProfile(null);
                    updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_STATUS_DISABLE, getBool(profile.settings.led_status_disable));
                    updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_POWER_DISABLE, getBool(profile.settings.led_power_disable));
                }

                if (profile.isSensor || profile.hasBattery) {
                    ShellyStatusSensor sdata = api.getSensorStatus();
                    if (profile.isSensor) {
                        if (sdata.tmp.is_valid) {
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                                    sdata.tmp.units.toUpperCase().equals(SHELLY_TEMP_CELSIUS) ? getDouble(sdata.tmp.tC) : getDouble(sdata.tmp.tF));
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TUNIT, getString(sdata.tmp.units));
                            updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM, getDouble(sdata.hum.value));
                        }
                    }

                    if (profile.hasBattery) {
                        updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL, getDouble(sdata.bat.value));
                        updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_VOLT, getDouble(sdata.bat.voltage));
                    }
                }

                // update thing status from specific thing handlers
                updateThingStatus();

                // update some properties
                updateProperties(profile, status);

                // If status update was successful the thing must be online
                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    logger.info("Thing {}({} is online", getThing().getLabel(), profile.hostname);
                    updateStatus(ThingStatus.ONLINE);  // if API call was successful the thing must be online
                }

                if (scheduledUpdates > 0) {
                    --scheduledUpdates;
                    logger.trace("{} more updates requested", scheduledUpdates);
                }
            } else {
                // logger.trace("Update skipped {}/{}", (skipUpdate - 1) % skipCount, skipCount);
            }
        } catch (IOException e) {
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

    }

    /**
     * Callback for device events
     *
     * @param deviceName device receiving the event
     * @param parameters parameters from the event url
     * @param data       the html input data
     */
    @SuppressWarnings("null")
    @Override
    public void onUpdateEvent(String deviceName, String deviceIndex, String eventClass, Map<String, String[]> parameters, String data) {
        if (thingName.equals(deviceName)) {
            logger.debug("Event received for device {}: class={}, index={}, parameters={}", deviceName, eventClass, deviceIndex,
                    parameters.toString());
            if (profile == null) {
                logger.debug("Device {} is not yet initialized, event triggers initialization", deviceName);
            }
            requestUpdates(1);    // request update on next interval
        }
        if (profile == null) {
            logger.debug("Device {} is not yet initialized, event triggers initialization", deviceName);
        }
    }

    protected ShellyHttpApi getShellyApi() {
        return api;
    }

    protected ShellyDeviceProfile getDeviceProfile() {
        return profile;
    }

    protected boolean requestUpdates(int requestCount) {
        if (scheduledUpdates < 10) {  // < 30s
            scheduledUpdates += requestCount;
            return true;
        }
        return false;
    }

    protected boolean updateChannel(String group, String channel, Object value) {
        if (value == null) {
            return false;
        }
        try {
            String fullName = mkChannelName(group, channel);
            Object current = channelData.get(fullName);
            if ((current == null) || !current.equals(value)) {
                if (value instanceof String) {
                    updateState(fullName, new StringType((String) value));
                }
                if (value instanceof Integer) {
                    Integer v = (Integer) value;
                    updateState(fullName, new DecimalType(v));
                }
                if (value instanceof Long) {
                    Long v = (Long) value;
                    updateState(fullName, new DecimalType(v));
                }
                if (value instanceof Double) {
                    Double v = (Double) value;
                    updateState(fullName, new DecimalType(v));
                }
                if (value instanceof OnOffType) {
                    OnOffType v = (OnOffType) value;
                    updateState(fullName, v);
                }
                if (value instanceof Boolean) {
                    Boolean v = (Boolean) value;
                    updateState(fullName, v ? OnOffType.ON : OnOffType.OFF);
                }
                if (current == null) {
                    channelData.put(fullName, value);
                } else {
                    channelData.replace(fullName, value);
                }
                logger.trace("Channel {}.{} updated with {} (type {}).", group, channel, value, value.getClass());
                return true;
            }
        } catch (RuntimeException e) {
            logger.debug("Unable to update channel {}.{}#{} with {} (type {})", thingName, group, channel, value, value.getClass());
        }
        return false;

    }

    protected Object getChannelValue(String group, String channel) {
        String key = mkChannelName(group, channel);
        return channelData.get(key);
    }

    protected void updateProperties(ShellyDeviceProfile profile, ShellySettingsStatus status) {
        Map<String, String> properties = new HashMap<String, String>();
        if (status.wifi_sta != null) {
            properties.put("wifiNetwork", getString(status.wifi_sta.ssid));
            properties.put("networkIp", getString(status.wifi_sta.ip));
        }
        properties.put("time", status.time);
        properties.put("uptime", status.uptime.toString() + "sec");
        properties.put("mode", profile.mode);
        properties.put("updateStaus", status.update.status);
        properties.put("updateAvailable", status.update.has_update ? "yes" : "no");
        properties.put("oldVersion", status.update.old_version);
        properties.put("newVersion", status.update.new_version);
        updateProperties(properties);
    }

    protected static String mkChannelName(String group, String channel) {
        return group + "#" + channel;
    }

    protected static String convertTimestamp(Long timestamp) {
        Object o = timestamp;
        if (o == null) {
            return "";
        }

        Date date = new java.util.Date(timestamp * 1000L);
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return sdf.format(date);
    }

    @Override
    public void dispose() {
        logger.debug("Shutdown thing {}", thingName);
        try {
            if (statusJob != null) {
                statusJob.cancel(true);
            }
        } catch (Exception e) {
            logger.debug("Exception on dispose(): {} ({})", e.getMessage(), e.getClass());
        } finally {
            super.dispose();
        }
    }
}
