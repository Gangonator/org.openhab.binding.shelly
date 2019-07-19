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
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.shelly.internal.ShellyBindingConstants;
import org.openhab.binding.shelly.internal.ShellyHandlerFactory;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsBulb;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsGlobal;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsMeter;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyShortStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusSensor;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.config.ShellyConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ShellyHandler} is responsible for handling commands, which are sent to one of the channels.
 *
 * @author Hans-Jörg Merk - Initial contribution
 */
public class ShellyHandler extends BaseThingHandler implements ShellyDeviceListener {

    private final Logger                logger         = LoggerFactory.getLogger(ShellyHandler.class);

    private final NetworkAddressService networkAddressService;
    private final ShellyHandlerFactory  handlerFactory;
    private ShellyConfiguration         config;
    private ShellyHttpApi               api;

    // private CoapClient client;
    // private CoapEndpoint endPoint;

    private ScheduledFuture<?>          statusJob;
    private int                         skipUpdate     = 0;
    private int                         requestUpdates = 1;
    private int                         skipCount      = UPDATE_SKIP_COUNT;

    private String                      deviceName     = "";
    private boolean                     isRoller       = false;  // true for Shelly2 in roller mode
    private boolean                     isSensor       = false;  // true for HT
    private boolean                     hasMeter       = false;
    private boolean                     hasBattery     = false;
    private boolean                     isPlugS        = false;  // Shelly PlugS has some additional features
    private boolean                     isSmoke        = false;  // Shelly Smoke Detector
    private boolean                     isBulb         = false;
    private boolean                     isBulbColor    = false;
    private Map<String, Object>         channelData    = new HashMap<>();

    public ShellyHandler(Thing thing, ShellyHandlerFactory handlerFactory,
            NetworkAddressService networkAddressService) {
        super(thing);
        this.handlerFactory = handlerFactory;
        this.networkAddressService = networkAddressService;
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        logger.debug("Start initializing!");
        config = getConfigAs(ShellyConfiguration.class);
        if (config.updateInterval == 0) {
            config.updateInterval = 15;
        }
        skipCount = config.updateInterval / UPDATE_STATUS_INTERVAL;

        config.localIp = networkAddressService.getPrimaryIpv4HostAddress();
        handlerFactory.registerDeviceListener(this);
        api = new ShellyHttpApi(config);

        // Example for background initialization:
        scheduler.schedule(() -> {
            try {
                ShellySettingsGlobal settings = api.getSettings();
                deviceName = settings.device.hostname != null && !settings.device.hostname.isEmpty() ? settings.device.hostname.toLowerCase()
                        : "shelly-" + settings.device.mac.toUpperCase().substring(6, 11);
                String thingType = this.getThing().getThingTypeUID().getId();
                String mode = settings.mode != null ? settings.mode : "";
                String fwDate = StringUtils.substringBefore(settings.fw, "/");
                String fwVersion = StringUtils.substringBetween(settings.fw, "/", "@");
                String fwId = StringUtils.substringAfter(settings.fw, "@");
                logger.info("Initializing device {}, type {}, Firmware: {} / {} ({}); Thing Type=", deviceName, settings.device.type, fwVersion,
                        fwDate, fwId, thingType);

                isRoller = mode.equals(SHELLY_MODE_ROLLER);
                hasMeter = ((settings.relays != null) || (settings.device.num_meters > 0));  // Shelly1 has a meter, nevertheless numMeters is null!
                hasBattery = thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId()) ||
                        thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
                isPlugS = thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYPLUGS.getId());
                isBulb = thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYBULB_COLOR.getId())
                        || thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYBULB_WHITE.getId());
                isSmoke = thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
                isSensor = thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId()) ||
                        thingType.equals(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
                logger.debug("Device is a roller: {}, a Plug S: {},  Bulb: {}, HT or Smoke Sensor: {}, has a Meter: {}, has a Battery: {}",
                        isRoller ? "yes" : "no", isPlugS ? "yes" : "no", isBulb ? "yes" : "no",
                        isSensor ? "yes" : "no", hasMeter ? "yes" : "no", hasBattery ? "yes" : "no");

                // update thing properties
                logger.debug("Update thing properties");
                ShellySettingsStatus status = api.gerStatus();
                Configuration configuration = this.getConfig();
                configuration.put("time", status.time);
                configuration.put("uptime", status.uptime.toString() + "sec");
                configuration.put("mode", mode);
                configuration.put("updateStaus", status.update.status);
                configuration.put("updateAvailable", status.update.has_update ? "yes" : "no");
                configuration.put("oldVersion", status.update.old_version);
                configuration.put("newVersion", status.update.new_version);
                this.updateConfiguration(configuration);

                logger.trace("Firmware check ({})", settings.fw);
                if (fwVersion.compareTo("v1.5.0") < 0) {
                    logger.info("WARNING: Firmware too old");
                    logger.info(
                            "The binding was tested with Version 1.5+ only. Older versions might work, but doesn't support all features or lead into technical issues.");
                    logger.info("You should consider to upgrade the device to v1.5.0 or newer!");
                }
                if (status.update.has_update) {
                    logger.info("INFO: New firmware available for this device: current version: {}, new version: {}", status.update.old_version,
                            status.update.new_version);
                }

                String thingUID = this.getThing().getThingTypeUID().getAsString();
                String reqMode = StringUtils.substringAfter(thingUID, "-");
                if (thingUID.contains("-") && !mode.equals(reqMode)) {
                    logger.info("Thing is not in {} mode, going offline. May run discovery to find the thing for the requested mode.");
                    ;
                }

                logger.debug("Set event URLs if device supports them");
                if (settings.relays != null) {
                    // set event URLs for Shelly2/4 Pro
                    logger.trace("Set Switch/Out event URLs for Relay or Roller");
                    int i = 0;
                    for (ShellySettingsRelay relay : settings.relays) {
                        logger.info("Relay[{}]: btn_on_url={}/btn_off_url={}, out_on_url={}, out_off_url={}", i,
                                relay.btn_on_url, relay.btn_off_url, relay.out_on_url, relay.out_off_url);
                        api.setRelayEventUrls(i, deviceName);
                        i++;
                    }
                }
                if (isSensor) {
                    // set event URL for HT (report_url)
                    logger.trace("Set Sensor Reporting URL");
                    api.setSensorEventUrls(deviceName);
                }
            } catch (RuntimeException | IOException e) {
                logger.info("Unable to get status from Shelly: {}", e.getMessage());
            }

            /*
             * // InetSocketAddress address = new InetSocketAddress(config.localIp, 50000); client = new CoapClient("coap://" + config.deviceIp +
             * "/cit/s"); // endPoint = new CoapEndpoint(address); endPoint = new CoapEndpoint(NetworkConfig.getStandard()); // // if
             * (!endPoint.isStarted()) { try { endPoint.start(); logger.info("Started set client endpoint " + endPoint.getAddress()); } catch //
             * (IOException e) { logger.error("Could not set and start client endpoint", e); } } // client.setEndpoint(endPoint); client.useNONs();
             * @SuppressWarnings("unused") CoapObserveRelation relation = client.observe(new CoapHandler() {
             * @Override public void onLoad(@Nullable CoapResponse response) { String content = response.getResponseText();
             * logger.info("NOTIFICATION: {}" + content); }
             * @Override public void onError() { logger.error("CoapOBSERVING FAILED"); } }); client.get(new CoapHandler() {
             * @Override public void onLoad(@Nullable CoapResponse response) { String content = response.getResponseText();
             * logger.info("NOTIFICATION: {}" + content); }
             * @Override public void onError() { logger.error("CoapREAD FAILED"); } }); logger.info("Start COAP Observer"); OptionSet optionSet = new
             * OptionSet(); optionSet.addOption(new Option(3332, "COIOT_OPTION_GLOBAL_DEVID")); optionSet.addOption(new Option(3412,
             * "COIOT_OPTION_STATUS_VALIDITY")); optionSet.addOption(new Option(3420, "COIOT_OPTION_STATUS_SERIAL")); // ---- optionSet is not used!
             * --- Request request = new Request(Code.GET, Type.NON); // request.setObserve(); request.setURI("coap://192.168.6.81:5683/cit/d");
             * request.setOptions(optionSet); // tclient.setTimeout(10000); // String content1 = tclient.get().getResponseText(); //
             * logger.info("Shelly device: {}", content1); CoapClient tclient = new CoapClient("coap://192.168.6.81:5683/cit/d"); tclient.advanced(new
             * CoapHandler() {
             * @Override public void onLoad(@Nullable CoapResponse response) { String content = response.getResponseText();
             * logger.info("NOTIFICATION: {}" + content); }
             * @Override public void onError() { logger.error("CoapREAD FAILED"); } }, request);
             */

            requestUpdates = 3; // request 3 updates in a row (during the furst 2+3*3 sec)

            if (statusJob == null || statusJob.isCancelled()) {
                statusJob = scheduler.scheduleWithFixedDelay(this::updateStatus, 2,
                        UPDATE_STATUS_INTERVAL, TimeUnit.SECONDS);
            }

        }, 2, TimeUnit.SECONDS);

        // logger.debug("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            } else {
                // command

                String groupName = channelUID.getGroupId();
                Integer relayIndex = 0;
                Integer rollerIndex = 0;
                if (groupName.startsWith(CHANNEL_GROUP_RELAY_CONTROL) && groupName.length() > CHANNEL_GROUP_RELAY_CONTROL.length()) {
                    relayIndex = Integer.parseInt(StringUtils.substringAfter(channelUID.getGroupId(), CHANNEL_GROUP_RELAY_CONTROL)) - 1;
                }
                if (groupName.startsWith(CHANNEL_GROUP_ROL_CONTROL) && groupName.length() > CHANNEL_GROUP_ROL_CONTROL.length()) {
                    rollerIndex = Integer.parseInt(StringUtils.substringAfter(channelUID.getGroupId(), CHANNEL_GROUP_ROL_CONTROL)) - 1;
                }
                switch (channelUID.getIdWithoutGroup()) {
                    case CHANNEL_RELAY_OUTPUT:
                        if (!isRoller) {
                            // extract relay number of group name (relay0->0, relay1->1...)
                            api.setRelayTurn(relayIndex, (OnOffType) command == OnOffType.ON ? "ON" : "OFF");
                        } else {
                            logger.info("Shelly is in roller mode, channel command {} ignored", channelUID.toString());
                        }
                        break;
                    case CHANNEL_RELAY_TIMER:
                        Integer timer = Integer.parseInt(command.toString());
                        if (!isRoller) {
                            logger.info("Set relay timer to {}", timer);
                            api.setRelayTimer(relayIndex, timer);
                        } else {
                            logger.info("Set roller timer to {}", timer);
                            api.setRollerTimer(relayIndex, timer);
                        }
                        break;

                    case CHANNEL_ROL_CONTROL_TURN:
                        String turn = command.toString().toLowerCase();
                        if (turn.equals("on")) {
                            turn = SHELLY_ALWD_ROLLER_TURN_OPEN;
                        }
                        if (turn.equals("off")) {
                            turn = SHELLY_ALWD_ROLLER_TURN_CLOSE;
                        }
                        logger.info("Set roller turn mode to {}", turn);
                        api.setRollerTurn(rollerIndex, turn);
                        break;
                    case CHANNEL_ROL_CONTROL_POS:
                        logger.info("Setting roller to position {}%", command.toString());
                        api.setRollerPos(rollerIndex, Integer.parseInt(command.toString()));
                        break;

                    case CHANNEL_LED_STATUS_DISABLE:
                        logger.info("Set STATUS LED disabled to {}", command.toString());
                        api.setLedStatus(SHELLY_LED_STATUS_DISABLE, (OnOffType) command == OnOffType.ON);
                        break;
                    case CHANNEL_LED_POWER_DISABLE:
                        logger.info("Set POWER LED disabled to {}", command.toString());
                        api.setLedStatus(SHELLY_LED_POWER_DISABLE, (OnOffType) command == OnOffType.ON);
                        break;

                    case CHANNEL_BULB_POWER:
                        logger.info("Switch bulb {}", command.toString());
                        api.setBulbParm(0, SHELLY_BULB_TURN, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                        break;
                    case CHANNEL_BULB_TIMER:
                        logger.info("Sez flip-back timer to {}", command.toString());
                        api.setBulbParm(0, SHELLY_BULB_TIMER, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                        break;
                    case CHANNEL_BULB_DEFSTATE:
                        logger.info("Default state for the bulb is {}", command.toString());
                        api.setBulbSettings(0, SHELLY_BULB_DEFSTATE, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                        break;
                    case CHANNEL_BULB_AUTOON:
                        logger.info("Set auto-on to {}", command.toString());
                        api.setBulbSettings(0, SHELLY_BULB_AUTOON, ((DecimalType) command).toString());
                        break;

                    case CHANNEL_COLOR_RED:
                        logger.info("Set color red to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_RED, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_BLUE:
                        logger.info("Set color blue to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_BLUE, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_GREEN:
                        logger.info("Set color green to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_GREEN, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_WHITE:
                        logger.info("Set color white to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_WHITE, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_GAIN:
                        logger.info("Set color gain to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_GAIN, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_BRIGHTNESS:
                        logger.info("Set colorbrightmess to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_BRIGHTNESS, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_TEMP:
                        logger.info("Set color temp to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_TEMP, ((DecimalType) command).toString());
                        break;
                    case CHANNEL_COLOR_EFFECT:
                        logger.info("Set color effect to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_EFFECT, ((DecimalType) command).toString());
                        break;
                }
                updateStatus();
            }
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})", channelUID.toString(), e.getMessage(), e.getClass());
        }

    }

    @Override
    public void onUpdateEvent(@Nullable String deviceName, @Nullable Map<String, String[]> parameters,
            @Nullable String data) {
        if (!this.deviceName.equals(deviceName)) {
            // not ours
            return;
        }
        requestUpdates++;
        // updateStatus();
    }

    /**
     * Returns the coap endpoint that can be used within coap clients.
     *
     * @return the coap endpoint
     *
     *         public @Nullable CoapEndpoint getEndpoint() { return endPoint; }
     */

    /**
     * Device device status events
     *
     * @return
     */
    public void updateStatus() {
        try {
            if ((requestUpdates > 0) || (skipUpdate++ % skipCount == 0)) {
                logger.trace("Updating device status for device {}", deviceName);
                ShellySettingsStatus status = api.gerStatus();

                // map status to channels
                if ((status.relays != null) && !isRoller) {
                    int i = 0;
                    ShellyStatusRelay rstatus = api.getRelayStatus(i);
                    for (ShellyShortStatusRelay relay : rstatus.relays) {
                        if ((relay.is_valid == null) || relay.is_valid) {
                            Integer r = i + 1;
                            String groupName = CHANNEL_GROUP_RELAY_CONTROL + r.toString();
                            updateChannel(groupName, CHANNEL_RELAY_OUTPUT, relay.ison ? OnOffType.ON : OnOffType.OFF);
                            // updateChannel(groupName, CHANNEL_RELAY_TIMER, relay.has_timer ? relay.
                            updateChannel(groupName, CHANNEL_RELAY_OVERPOWER, relay.overpower);
                        }
                        i++;
                    }
                }

                if (isRoller && (status.rollers != null)) {
                    int i = 0;
                    for (ShellySettingsRoller roller : status.rollers) {
                        if (roller.is_valid) {
                            ShellyControlRoller control = api.getRollerStatus(i);
                            Integer relayIndex = i + 1;
                            String groupName = CHANNEL_GROUP_ROL_CONTROL + relayIndex.toString();
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_TURN, control.state);
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_POS, control.current_pos);
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_DIR, control.last_direction);
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_STOPR, control.stop_reason);
                            updateChannel(groupName, CHANNEL_ROL_CONTROL_OVERT, control.overtemperature);
                            i = i + 1;
                        }
                    }
                }

                if (hasMeter && (status.meters != null)) {
                    if (!isRoller) {
                        // In Relay mode we map eacher meter to the matching channel group
                        int m = 0;
                        for (ShellySettingsMeter meter : status.meters) {
                            Integer meterIndex = m + 1;
                            if (meter.is_valid) {
                                String groupName = CHANNEL_GROUP_METER + meterIndex.toString();
                                updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, meter.power);
                                if (meter.total != null) {
                                    updateChannel(groupName, CHANNEL_METER_TOTALWATTS, meter.total);
                                }
                                if (meter.counters != null) {
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN1, meter.counters[0]);
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN2, meter.counters[1]);
                                    updateChannel(groupName, CHANNEL_METER_LASTMIN3, meter.counters[2]);
                                }
                                updateChannel(groupName, CHANNEL_METER_TIMESTAMP, convertTimestamp(meter.timestamp));
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
                                currentWatts += meter.power;
                                if (meter.total != null) {
                                    totalWatts += meter.total;
                                }
                                if (meter.counters != null) {
                                    lastMin1 += meter.counters[0];
                                    lastMin1 += meter.counters[1];
                                    lastMin1 += meter.counters[2];
                                }
                                if (meter.timestamp > timestamp) {
                                    timestamp = meter.timestamp;
                                }
                            }
                        }

                        updateChannel(groupName, CHANNEL_METER_CURRENTWATTS, currentWatts);
                        updateChannel(groupName, CHANNEL_METER_TOTALWATTS, totalWatts);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN1, lastMin1);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN2, lastMin2);
                        updateChannel(groupName, CHANNEL_METER_LASTMIN3, lastMin3);
                        updateChannel(groupName, CHANNEL_METER_TIMESTAMP, convertTimestamp(timestamp));
                    }
                }

                if (isPlugS) {
                    ShellySettingsGlobal settings = api.getSettings();
                    updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_STATUS_DISABLE, settings.led_status_disable ? OnOffType.ON : OnOffType.OFF);
                    updateChannel(CHANNEL_GROUP_LED_CONTROL, CHANNEL_LED_POWER_DISABLE, settings.led_power_disable ? OnOffType.ON : OnOffType.OFF);
                    updateChannel(CHANNEL_GROUP_METER1, CHANNEL_METER_MAXPOWER, settings.max_power);
                }

                if (isBulb) {
                    ShellySettingsBulb settings = api.getBulbSettings();
                    updateChannel(CHANNEL_GROUP_BULB_CONTROL, CHANNEL_BULB_POWER, settings.ison ? OnOffType.ON : OnOffType.OFF);
                    updateChannel(CHANNEL_GROUP_BULB_CONTROL, CHANNEL_BULB_DEFSTATE, settings.default_state);
                    updateChannel(CHANNEL_GROUP_BULB_CONTROL, CHANNEL_BULB_AUTOON, settings.auto_on);
                    updateChannel(CHANNEL_GROUP_BULB_CONTROL, CHANNEL_BULB_AUTOOFF, settings.auto_off);
                    updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_EFFECT, settings.effect);
                    if (isBulbColor) {
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_RED, settings.red);
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BLUE, settings.blue);
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GREEN, settings.green);
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_WHITE, settings.white);
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GAIN, settings.gain);
                    } else {
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BRIGHTNESS, settings.brightness);
                        updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_TEMP, settings.temp);
                    }
                }

                if (isSensor) {
                    ShellyStatusSensor sdata = api.getSensorStatus();
                    if (sdata.tmp.is_valid) {
                        updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP,
                                sdata.tmp.units.toUpperCase().equals(SHELLY_TEMP_CELSIUS) ? sdata.tmp.tC : sdata.tmp.tF);
                        updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TUNIT, sdata.tmp.units);
                        updateChannel(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM, sdata.hum.value);
                    }

                    if (hasBattery) {
                        updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_LEVEL, sdata.bat.value);
                        updateChannel(CHANNEL_GROUP_BATTERY, CHANNEL_SENSOR_BAT_VOLT, sdata.bat.voltage);
                    }
                }

                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    logger.info("Thing {} is online", deviceName);
                    updateStatus(ThingStatus.ONLINE);  // if API call was successful the thing must be online
                }

                if (requestUpdates > 0) {
                    --requestUpdates;
                    logger.trace("{} more updates requested", requestUpdates);
                }
            } else

            {
                // logger.trace("Update skipped {}/{}", (skipUpdate - 1) % skipCount, skipCount);
            }
        } catch (RuntimeException | IOException e) {
            logger.debug("Unable to update the device status: {}", e.getMessage());
        }
    }

    private boolean updateChannel(String group, String channel, Object value) {
        if (value == null) {
            return false;
        }
        String fullName = group + "#" + channel;
        Object current = channelData.get(fullName);
        if ((current == null) || !current.equals(value)) {
            logger.trace("Updating channel {}.{} with {} (type {})", group, channel, value, value.getClass());
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
            return true;
        }
        return false;

    }

    private String convertTimestamp(Long timestamp) {
        Object o = timestamp;
        if (o == null) {
            return "n/a";
        }

        Date date = new java.util.Date(timestamp * 1000L);
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        return sdf.format(date);
    }

    @Override
    public void dispose() {
        logger.debug("Shutdown thing");
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
