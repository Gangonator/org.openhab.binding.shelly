package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;
import static org.openhab.binding.shelly.internal.api.ShellyHttpApi.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.shelly.internal.ShellyHandlerFactory;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusLight;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusLightChannel;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi.ShellyDeviceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellyHandlerLight extends ShellyHandler {
    private final Logger  logger = LoggerFactory.getLogger(ShellyHandler.class);
    private ShellyHttpApi api;

    /**
     * @param thing                 The thing passed by the HandlerFactory
     * @param handlerFactory        Handler Factory instance (will be used for event handler registration)
     * @param networkAddressService instance of NetworkAddressService to get access to the OH default ip settings
     */
    public ShellyHandlerLight(Thing thing, ShellyHandlerFactory handlerFactory,
            NetworkAddressService networkAddressService) {
        super(thing, handlerFactory, networkAddressService);
    }

    @Override
    public void initialize() {
        logger.debug("Thing is using class {}", this.getClass());
        super.initialize();
        api = super.getShellyApi();
    }

    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        if (command instanceof RefreshType) {
            super.handleCommand(channelUID, command);
            return;
        }

        ShellyDeviceProfile profile = super.getProfile();
        if (profile == null) {
            logger.debug("Light not yet initialized!");
            return;
        }
        try {
            String groupName = channelUID.getGroupId();
            Integer lightId = 0;
            if (groupName.startsWith(CHANNEL_GROUP_LIGHT_CHANNEL)) {
                lightId = Integer.parseInt(StringUtils.substringAfter(groupName, CHANNEL_GROUP_LIGHT_CHANNEL));
            }
            logger.info("Execute command {} on channel {}, lightId={}", command.toString(), channelUID.getAsString(), lightId);
            switch (channelUID.getIdWithoutGroup()) {
                default: // non-bulb commands will be handled by the generic handler
                    super.handleCommand(channelUID, command);
                    break;

                case CHANNEL_LIGHT_COLOR_MODE:
                    logger.info("Select color mode {}", command.toString());
                    api.setLightSetting(SHELLY_API_MODE, (OnOffType) command == OnOffType.ON ? SHELLY_MODE_COLOR : SHELLY_MODE_WHITE);
                    break;
                case CHANNEL_LIGHT_POWER:
                    logger.info("Switch light {}", command.toString());
                    api.setLightParm(lightId, SHELLY_LIGHT_TURN, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                    break;
                case CHANNEL_LIGHT_DEFSTATE:
                    logger.info("Default state for the bulb is {}", command.toString());
                    api.setLightParm(lightId, SHELLY_LIGHT_DEFSTATE, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                    break;
                case CHANNEL_COLOR_PICKER:
                    if (command instanceof HSBType) {
                        HSBType hsb = (HSBType) command;
                        if (hsb.getBrightness().intValue() == 0) {
                            logger.info("Requested brightness = 0 -> switch off the bulb");
                            api.setLightParm(lightId, SHELLY_LIGHT_TURN, SHELLY_API_OFF);  // switch the bulb off
                        } else {
                            if (profile.inColor) {
                                logger.info("Setting RGB colors to {}/{}/{} (sRGB={})",
                                        hsb.getRed().intValue(), hsb.getGreen().intValue(), hsb.getBlue().intValue(), hsb.getRGB());
                                Map<String, String> parms = new HashMap<String, String>();
                                parms.put(SHELLY_COLOR_RED, new Integer(hsb.getRed().intValue()).toString());
                                parms.put(SHELLY_COLOR_GREEN, new Integer(hsb.getGreen().intValue()).toString());
                                parms.put(SHELLY_COLOR_BLUE, new Integer(hsb.getBlue().intValue()).toString());
                                logger.debug("Color parameters: {}", parms.toString());
                                api.setLightParms(lightId, parms);
                            } else {
                                setColor(lightId, SHELLY_COLOR_BRIGHTNESS, hsb.getBrightness().intValue());
                            }
                        }
                    } else if (command instanceof PercentType) {
                        PercentType percent = (PercentType) command;
                        Integer brightness = SHELLY_MAX_BRIGHTNESS * percent.intValue();
                        api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, brightness.toString());
                    } else if (command instanceof OnOffType) {
                        api.setLightParm(lightId, SHELLY_LIGHT_TURN, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                    } else if (command instanceof IncreaseDecreaseType) {
                        Integer currentBrightness = (Integer) getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BRIGHTNESS);
                        Integer newBrightness;
                        if (command == IncreaseDecreaseType.DECREASE) {
                            newBrightness = Math.max(currentBrightness - SHELLY_DIM_STEPSIZE, 0);
                        } else {
                            newBrightness = Math.min(currentBrightness + SHELLY_DIM_STEPSIZE, SHELLY_MAX_BRIGHTNESS);
                        }
                        api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, newBrightness.toString());
                    }
                    break;

                case CHANNEL_COLOR_RED:
                    setColor(lightId, SHELLY_COLOR_RED, command, SHELLY_MAX_COLOR);
                    break;
                case CHANNEL_COLOR_GREEN:
                    setColor(lightId, SHELLY_COLOR_GREEN, command, SHELLY_MAX_COLOR);
                    break;
                case CHANNEL_COLOR_BLUE:
                    setColor(lightId, SHELLY_COLOR_BLUE, command, SHELLY_MAX_COLOR);
                    break;

                case CHANNEL_COLOR_WHITE:
                    setColor(lightId, SHELLY_COLOR_WHITE, command, SHELLY_MAX_COLOR);
                    break;
                case CHANNEL_COLOR_GAIN:
                    setColor(lightId, SHELLY_COLOR_GAIN, command, SHELLY_MAX_GAIN);
                    break;
                case CHANNEL_COLOR_TEMP:
                    setColor(lightId, SHELLY_COLOR_TEMP, command, MAX_COLOR_TEMPERATURE);
                    break;

                // only in white mode
                case CHANNEL_COLOR_BRIGHTNESS:
                    setColor(lightId, SHELLY_COLOR_BRIGHTNESS, command, SHELLY_MAX_BRIGHTNESS);
                    break;

                case CHANNEL_COLOR_EFFECT:
                    logger.info("Set color effect to {}", command.toString());
                    api.setLightParm(lightId, SHELLY_COLOR_EFFECT, ((DecimalType) command).toString());
                    break;
            }
            super.requestUpdates(1, true);
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})",
                    channelUID.toString(), e.getMessage(), e.getClass());
        }
    }

    @Override
    public void updateThingStatus() throws IOException {
        logger.trace("Updating bulb/rgw2 status");

        ShellyDeviceProfile profile = super.getProfile();
        if (profile == null) {
            logger.debug("ERROR: Light not yet initialized, but updating thing status!");
            return;
        }
        if (!profile.isLight) {
            logger.debug("ERROR: Device {} is not a light. but class ShellyHandlerLight is called!", profile.hostname);
            return;
        }

        logger.debug("Refreshing light status for {}", profile.hostname);
        ShellyStatusLight status = api.getLightStatus();

        // In white mode we have multiple channels
        logger.debug("Updating bulb/rgw2 {} in color mode (1 channel)", profile.hostname);
        int i = 0;
        for (ShellyStatusLightChannel channel : status.lights) {
            Integer channelId = i + 1;
            String groupName = CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
            logger.debug("Updating light channel {}.{}", profile.hostname, groupName);

            if (profile.inColor || profile.isBulb) {
                // In color mode we have 1 light
                ShellyStatusLightChannel light = status.lights.get(0);
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_LIGHT_COLOR_MODE, profile.inColor);
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_LIGHT_POWER, getBool(light.ison));
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_TIMER_AUTOON, getDouble(light.auto_on));
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_TIMER_AUTOOFF, getDouble(light.auto_off));
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_RELAY_OVERPOWER, getBool(light.overpower));

                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_RED, mkPercent(light.red, 0, 255));
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BLUE, getInteger(light.blue));
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GREEN, getInteger(light.green));
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_WHITE, getInteger(light.white));
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_GAIN, getInteger(light.gain));
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_TEMP, getInteger(light.temp));
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_EFFECT, getInteger(light.effect));
            }
            if (!profile.inColor || profile.isBulb) {
                updateChannel(groupName, CHANNEL_LIGHT_POWER, getBool(channel.ison));
                updateChannel(groupName, CHANNEL_COLOR_BRIGHTNESS, getInteger(channel.brightness));
            }
        }
        i++;
    }

    private PercentType mkPercent(Integer _value, Integer min, Integer max) {
        Integer value = _value != null ? _value : 0;
        value = value < min ? min : value;
        value = value > max ? max : value;
        Double percent = new Double(value) * 100.0 / new Double(max);
        logger.trace("Value converted from {} into {}%", value.toString(), percent.toString());
        return new PercentType(new BigDecimal(percent));
    }

    private void setColor(Integer lightId, String colorName, Integer value) throws IOException {
        ShellyDeviceProfile profile = super.getProfile();
        if (profile.mode.equals(SHELLY_MODE_COLOR) && colorName.equals(SHELLY_COLOR_BRIGHTNESS)) {
            logger.info("Light is in Color Mode, command ignored!");
            return;
        }
        if (profile.mode.equals(SHELLY_MODE_WHITE) && !colorName.equals(SHELLY_COLOR_BRIGHTNESS)) {
            logger.info("Light is in White Mode, command ignored!");
            return;
        }

        DecimalType currentValue = (DecimalType) super.getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, colorName);
        if ((currentValue != null) && (currentValue.intValue() == value)) {
            logger.trace("Color {} was not changed (value={}), skip API call to update", colorName, value.toString());
            return;
        }

        logger.info("Set color {} to {}", colorName, value.toString());
        api.setLightParm(lightId, colorName, value.toString());
    }

    private void setColor(Integer lightId, String colorName, Command command, Integer maxValue) throws IOException {
        DecimalType value = new DecimalType();
        if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            value = new DecimalType(maxValue * percent.intValue());
        } else {
            value = (DecimalType) command;
        }
        if (value.intValue() > maxValue) {
            logger.debug("Value for color {} is out of range: {}/{}, set to {}", colorName, value.intValue(), maxValue, maxValue);
            value = new DecimalType(maxValue);
        }
        setColor(lightId, colorName, value.intValue());
    }
}
