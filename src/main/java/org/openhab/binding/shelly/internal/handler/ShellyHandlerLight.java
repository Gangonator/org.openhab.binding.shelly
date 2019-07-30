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
                    handleColorPicker(profile, lightId, command);
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
                    setColor(lightId, SHELLY_COLOR_GAIN, command, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN);
                    break;
                case CHANNEL_COLOR_TEMP:
                    setColor(lightId, SHELLY_COLOR_TEMP, command, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE);
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

    private void handleColorPicker(ShellyDeviceProfile profile, Integer lightId, Command command) throws IOException {
        if (command instanceof HSBType) {
            HSBType hsb = (HSBType) command;
            logger.debug("HSB-Info={}, Hue={}, getRGB={}, toRGB={}/{}/{}", hsb.toString(), hsb.getHue(), String.format("0x%08X", hsb.getRGB()),
                    hsb.toRGB()[0], hsb.toRGB()[1], hsb.toRGB()[2]);
            Integer red = getColorFromHSB(hsb.getRed()); // new Double((hsb.getRed().floatValue() * SATURATION_FACTOR)).intValue();
            Integer blue = getColorFromHSB(hsb.getBlue()); // new Double((hsb.getBlue().floatValue() * SATURATION_FACTOR)).intValue();
            Integer green = getColorFromHSB(hsb.getGreen()); // new Double((hsb.getGreen().floatValue() * SATURATION_FACTOR)).intValue();
            Integer gain = getColorFromHSB(hsb.getSaturation(), GAIN_FACTOR); // new Double((hsb.getSaturation().floatValue() * SHELLY_MAX_GAIN /
                                                                              // 100)).intValue();
            Integer brightness = getColorFromHSB(hsb.getGreen(), BRIGHTNESS_FACTOR);  // new Double((hsb.getBrightness().floatValue() *
                                                                                      // SHELLY_MAX_BRIGHTNESS / 100)).intValue();
            Integer white = getColorfromChannel(buildControlGroupName(profile, lightId + 1), SHELLY_COLOR_WHITE);  // not supplied by HSB, read from
                                                                                                                   // channel
            logger.trace("Color settings converted to RGB:{}/{}/{}, white={}, saturantion/gain={}, brightness={}",
                    red, green, blue, white, gain, brightness);

            // Build commands for the API
            Map<String, String> parms = new HashMap<String, String>();
            parms.put(SHELLY_LIGHT_TURN, SHELLY_API_ON);
            if (profile.inColor) {
                parms.put(SHELLY_COLOR_RED, red.toString());
                parms.put(SHELLY_COLOR_GREEN, green.toString());
                parms.put(SHELLY_COLOR_BLUE, blue.toString());
                parms.put(SHELLY_COLOR_WHITE, white.toString());
                parms.put(SHELLY_COLOR_GAIN, gain.toString());
            }
            parms.put(SHELLY_COLOR_BRIGHTNESS, brightness.toString());
            logger.debug("Set new color parameters: {}", parms.toString());
            api.setLightParms(lightId, parms);

            /*
             * if (!profile.inColor || profile.isBulb) {
             * logger.debug("Update channels: {}", parms.toString());
             * super.updateChannel(CHANNEL_GROUP_COLOR_CONTROL, SHELLY_COLOR_BRIGHTNESS,
             * mkPercent(brightness, SHELLY_MIN_BRIGHTNESS, SHELLY_MAX_BRIGHTNESS));
             * }
             */
        } else if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            if (!profile.inColor) {
                logger.info("Set brightness to {}%", percent);
                Integer brightness = SHELLY_MAX_BRIGHTNESS * percent.intValue();
                api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, brightness.toString());
            }
        } else if (command instanceof OnOffType) {
            logger.info("SSwitch light {}", command);
            api.setLightParm(lightId, SHELLY_LIGHT_TURN, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
        } else if (command instanceof IncreaseDecreaseType) {
            if (!profile.inColor) {
                logger.info("{} brightness by {}", command.toString(), SHELLY_DIM_STEPSIZE);
                Double percent = ((PercentType) getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BRIGHTNESS)).doubleValue();
                Integer currentBrightness = percent.intValue() * SHELLY_MAX_BRIGHTNESS;
                Integer newBrightness;
                if (command == IncreaseDecreaseType.DECREASE) {
                    newBrightness = Math.max(currentBrightness - SHELLY_DIM_STEPSIZE, 0);
                } else {
                    newBrightness = Math.min(currentBrightness + SHELLY_DIM_STEPSIZE, SHELLY_MAX_BRIGHTNESS);
                }
                logger.info("New brightness={}", newBrightness.toString());
                api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, newBrightness.toString());
            }
        }
    }

    private Integer getColorfromChannel(String group, String channel) {
        Double percent = ((PercentType) super.getChannelValue(group, channel)).doubleValue();
        return ((Double) (percent.doubleValue() * SHELLY_MAX_COLOR)).intValue();
    }

    private Integer getColorFromHSB(PercentType colorPercent) {
        return getColorFromHSB(colorPercent, new Double(SATURATION_FACTOR));
    }

    private Integer getColorFromHSB(PercentType colorPercent, Integer factor) {
        return getColorFromHSB(colorPercent, new Double(factor));
    }

    private Integer getColorFromHSB(PercentType colorPercent, Double factor) {
        return new Double(colorPercent.doubleValue() * factor).intValue();
    }

    @Override
    public void updateThingStatus() throws IOException {

        ShellyDeviceProfile profile = super.getProfile();
        if (profile == null) {
            logger.debug("ERROR: Light not yet initialized, but updating thing status!");
            return;
        }
        if (!profile.isLight) {
            logger.debug("ERROR: Device {} is not a light. but class ShellyHandlerLight is called!", profile.hostname);
            return;
        }

        ShellyStatusLight status = api.getLightStatus();
        logger.debug("Updating bulb/rgw2 status for {}, in {} mode, {} channel(s)", profile.hostname, profile.mode, status.lights.size());

        // In white mode we have multiple channels
        int i = 0;
        for (ShellyStatusLightChannel channel : status.lights) {
            Integer channelId = i + 1;
            String controlGroup = buildControlGroupName(profile, channelId);

            logger.debug("Updating light channels {}.{}", profile.hostname, controlGroup);

            // The bulb has a combined channel set for color or white mode
            // The RGBW2 uses 2 different thing types: color=1 channel, white=4 channel
            if (profile.isBulb) {
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_LIGHT_COLOR_MODE, profile.inColor);
            }

            // Channel control/timer
            ShellyStatusLightChannel light = status.lights.get(i);
            updateChannel(controlGroup, CHANNEL_LIGHT_POWER, getBool(light.ison));
            updateChannel(controlGroup, CHANNEL_TIMER_AUTOON, getDouble(light.auto_on));
            updateChannel(controlGroup, CHANNEL_TIMER_AUTOOFF, getDouble(light.auto_off));
            updateChannel(controlGroup, CHANNEL_RELAY_OVERPOWER, getBool(light.overpower));

            if (profile.inColor || profile.isBulb) {
                String colorGroup = CHANNEL_GROUP_COLOR_CONTROL;
                logger.debug("Updating light channels {}.{}", profile.hostname, colorGroup);

                PercentType percentRed = mkPercent(light.red);
                PercentType percentGreen = mkPercent(light.green);
                PercentType percentBlue = mkPercent(light.blue);
                PercentType percentWhite = mkPercent(light.white);
                PercentType percentGain = mkPercent(light.gain, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN);

                logger.trace("Update channels for {}: RGBW={}/{}/{}, in %:{}%/{}%/{}%, white={}/{}%, gain={}/{}% (brightness={})",
                        colorGroup,
                        light.red, light.green, light.blue, percentRed, percentGreen, percentBlue,
                        light.white, percentWhite, light.gain, percentGain, light.brightness);
                updateChannel(colorGroup, CHANNEL_COLOR_RED, percentRed);
                updateChannel(colorGroup, CHANNEL_COLOR_GREEN, percentGreen);
                updateChannel(colorGroup, CHANNEL_COLOR_BLUE, percentBlue);
                updateChannel(colorGroup, CHANNEL_COLOR_WHITE, percentWhite);
                updateChannel(colorGroup, CHANNEL_COLOR_GAIN, percentGain);

                updateChannel(colorGroup, CHANNEL_COLOR_EFFECT, getInteger(light.effect));

                logger.trace("update {}.color picker", colorGroup);
                HSBType hsb = HSBType.fromRGB(light.red, light.green, light.blue);
                updateChannel(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_PICKER, hsb);
            }
            if (!profile.inColor || profile.isBulb) {
                String colorGroup = CHANNEL_GROUP_WHITE_CONTROL;

                PercentType percentTemp = mkPercent(light.temp, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE);
                PercentType percentBright = mkPercent((profile.inColor && !profile.isBulb) ? SHELLY_MAX_BRIGHTNESS : light.brightness,
                        SHELLY_MIN_BRIGHTNESS, SHELLY_MAX_BRIGHTNESS);

                logger.debug("Updating light channels {}.{}: brightness={}/{}%, color-temp={}/{}%", profile.hostname, colorGroup,
                        light.brightness, percentBright, light.temp, percentTemp);
                updateChannel(controlGroup, CHANNEL_COLOR_BRIGHTNESS, percentBright);
                updateChannel(controlGroup, CHANNEL_COLOR_TEMP, percentTemp);
            }
            i++;
        }
    }

    private String buildControlGroupName(ShellyDeviceProfile profile, Integer channelId) {
        return profile.isBulb || profile.inColor ? CHANNEL_GROUP_LIGHT_CONTROL
                : CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
    }

    private PercentType mkPercent(Integer value) {
        return mkPercent(value, 0, SHELLY_MAX_COLOR);
    }

    private PercentType mkPercent(Integer _value, Integer min, Integer max) {
        int range = max - min;
        int value = _value != null ? _value.intValue() : 0;
        value = value < min ? min : value;
        value = value > max ? max : value;
        int percent = 0;
        if (range > 0) {
            percent = (value * 100) / range;
        }
        logger.trace("Value converted from {} into {}%", value, percent);
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

        Integer col = (Integer) super.getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, colorName);
        if ((col != null) && (col == value)) {
            logger.trace("Color {} was not changed (value={}), skip API call to update", colorName, value.toString());
            return;
        }

        logger.info("Set color {} for channel {}, to {}", colorName, lightId, value.toString());
        api.setLightParm(lightId, colorName, value.toString());
    }

    private void setColor(Integer lightId, String colorName, Command command, Integer maxValue) throws IOException {
        setColor(lightId, colorName, command, 0, maxValue);
    }

    private void setColor(Integer lightId, String colorName, Command command, Integer minValue, Integer maxValue) throws IOException {
        DecimalType value = new DecimalType();
        if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            value = new DecimalType(maxValue * percent.intValue());
            logger.trace("Value for {} is in %: {}%={}", colorName, percent, value);
        } else if (command instanceof DecimalType) {
            value = (DecimalType) command;
            logger.trace("Value for {} is a number: {}%={}", colorName, value);
        } else {
            logger.debug("Invalid valu for {}e: {} / type {}", colorName, value.toString(), value.getClass());
        }
        if (value.intValue() > maxValue) {
            logger.debug("Value for color {} is out of range: {}/{}, set to {}", colorName, value.intValue(), maxValue, maxValue);
            value = new DecimalType(maxValue);
        }
        setColor(lightId, colorName, value.intValue());
    }
}
