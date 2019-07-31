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

    private static class CurrentColors {
        private final Logger logger       = LoggerFactory.getLogger(CurrentColors.class);

        Integer              red          = 0;
        Integer              green        = 0;
        Integer              blue         = 0;
        Integer              white        = 0;
        PercentType          percentRed   = new PercentType(0);
        PercentType          percentGreen = new PercentType(0);
        PercentType          percentBlue  = new PercentType(0);
        PercentType          percentWhite = new PercentType(0);

        void setRGBW(int red, int green, int blue, int white) {
            logger.trace("setRGBW(): setting rgb+white to {}/{}/{}/{}", red, green, blue, white);
            setRed(red);
            setGreen(green);
            setBlue(blue);
            setWhite(white);
        }

        void setRed(int value) {
            logger.trace("   setting red={}", value);
            red = value;
            percentRed = toPercent(red);
        }

        void setGreen(int value) {
            logger.trace("   setting green={}", value);
            green = value;
            percentGreen = toPercent(green);
        }

        void setBlue(int value) {
            logger.trace("   setting blue={}", value);
            blue = value;
            percentBlue = toPercent(blue);
        }

        void setWhite(int value) {
            logger.trace("   setting white={}", value);
            white = value;
            percentWhite = toPercent(white);
        }

        Integer     gain              = 0;
        Integer     brightness        = 0;
        Integer     temp              = 0;
        PercentType percentGain       = new PercentType(0);
        PercentType percentBrightness = new PercentType(0);
        PercentType percentTemp       = new PercentType(0);

        void setBrightness(int value) {
            logger.trace("   setting brightness={}", value);
            brightness = value;
            percentBrightness = toPercent(brightness, SHELLY_MIN_BRIGHTNESS, SHELLY_MAX_BRIGHTNESS);
        }

        void setGain(int value) {
            logger.trace("   setting gain={}", value);
            gain = value;
            percentGain = toPercent(gain, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN);
        }

        void setTemp(int value) {
            logger.trace("   setting temp={}", value);
            temp = value;
            percentTemp = toPercent(temp, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE);
        }

        public HSBType toHSB() {
            logger.trace("toHSB(): create HSB from {}/{}/{}", red, green, blue);
            return HSBType.fromRGB(red, green, blue);
        }

        private static PercentType toPercent(Integer value) {
            return toPercent(value, 0, SHELLY_MAX_COLOR);
        }

        private static PercentType toPercent(Integer _value, Integer min, Integer max) {
            int range = max - min;
            int value = _value != null ? _value.intValue() : 0;
            value = value < min ? min : value;
            value = value > max ? max : value;
            int percent = 0;
            if (range > 0) {
                percent = (value * 100) / range;
            }
            // logger.trace("Value converted from {} into {}%", value, percent);
            return new PercentType(new BigDecimal(percent));
        }
    }

    Map<Integer, CurrentColors> channelColors = new HashMap<Integer, CurrentColors>();

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
            Integer lightId = getLightIdFromGroup(groupName);
            CurrentColors col = getCurrentColors(lightId);

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
                    handleColorPicker(profile, lightId, col, command);
                    break;
                case CHANNEL_COLOR_RED:
                    col.setRed(setColor(lightId, SHELLY_COLOR_RED, command, SHELLY_MAX_COLOR));
                    break;
                case CHANNEL_COLOR_GREEN:
                    col.setGreen(setColor(lightId, SHELLY_COLOR_GREEN, command, SHELLY_MAX_COLOR));
                    break;
                case CHANNEL_COLOR_BLUE:
                    col.setBlue(setColor(lightId, SHELLY_COLOR_BLUE, command, SHELLY_MAX_COLOR));
                    break;

                case CHANNEL_COLOR_WHITE:
                    col.setWhite(setColor(lightId, SHELLY_COLOR_WHITE, command, SHELLY_MAX_COLOR));
                    break;
                case CHANNEL_COLOR_GAIN:
                    col.setGain(setColor(lightId, SHELLY_COLOR_GAIN, command, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN));
                    break;
                case CHANNEL_COLOR_TEMP:
                    col.setTemp(setColor(lightId, SHELLY_COLOR_TEMP, command, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE));
                    break;

                // only in white mode
                case CHANNEL_COLOR_BRIGHTNESS:
                    col.setBrightness(setColor(lightId, SHELLY_COLOR_BRIGHTNESS, command, SHELLY_MAX_BRIGHTNESS));
                    break;

                case CHANNEL_COLOR_EFFECT:
                    logger.info("Set color effect {}", command.toString());
                    Integer effect = ((DecimalType) command).intValue();
                    if ((effect < SHELLY_MIN_EFFECT) || (effect > SHELLY_MAX_EFFECT)) {
                        logger.info("Invalid value for effect: {}", effect);
                    } else {
                        logger.info("Selecting effect #{}", effect);
                        api.setLightParm(lightId, SHELLY_COLOR_EFFECT, effect.toString());
                    }
                    break;
            }
            super.requestUpdates(1, true);
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})",
                    channelUID.toString(), e.getMessage(), e.getClass());
        }
    }

    private void handleColorPicker(ShellyDeviceProfile profile, Integer lightId, CurrentColors col, Command command) throws IOException {
        if (command instanceof HSBType) {
            HSBType hsb = (HSBType) command;
            logger.debug("HSB-Info={}, Hue={}, getRGB={}, toRGB={}/{}/{}", hsb.toString(), hsb.getHue(), String.format("0x%08X", hsb.getRGB()),
                    hsb.toRGB()[0], hsb.toRGB()[1], hsb.toRGB()[2]);
            col.setRed(getColorFromHSB(hsb.getRed())); // new Double((hsb.getRed().floatValue() * SATURATION_FACTOR)).intValue();
            col.setBlue(getColorFromHSB(hsb.getBlue())); // new Double((hsb.getBlue().floatValue() * SATURATION_FACTOR)).intValue();
            col.setGreen(getColorFromHSB(hsb.getGreen())); // new Double((hsb.getGreen().floatValue() * SATURATION_FACTOR)).intValue();
            col.setGain(getColorFromHSB(hsb.getSaturation(), GAIN_FACTOR)); // new Double((hsb.getSaturation().floatValue() * SHELLY_MAX_GAIN /
                                                                            // 100)).intValue();
            col.setBrightness(getColorFromHSB(hsb.getBrightness(), BRIGHTNESS_FACTOR));  // new Double((hsb.getBrightness().floatValue() *
            // white and temp are not part of the HSB color scheme

            logger.trace("Color settings converted to RGB:{}/{}/{}, white={}, saturantion/gain={}, brightness={}",
                    col.red, col.green, col.blue, col.white, col.gain, col.brightness);

            // Build commands for the API
            Map<String, String> parms = new HashMap<String, String>();
            parms.put(SHELLY_LIGHT_TURN, SHELLY_API_ON);
            if (profile.inColor) {
                parms.put(SHELLY_COLOR_RED, col.red.toString());
                parms.put(SHELLY_COLOR_GREEN, col.green.toString());
                parms.put(SHELLY_COLOR_BLUE, col.blue.toString());
                // parms.put(SHELLY_COLOR_WHITE, col.white.toString());
                parms.put(SHELLY_COLOR_GAIN, col.gain.toString());
            }
            // parms.put(SHELLY_COLOR_BRIGHTNESS, col.brightness.toString());
            // parms.put(SHELLY_COLOR_TEMP, col.temp.toString());

            logger.debug("Set new color parameters: {}", parms.toString());
            api.setLightParms(lightId, parms);
        } else if (command instanceof PercentType) {
            if (!profile.inColor) {
                col.brightness = SHELLY_MAX_BRIGHTNESS * ((PercentType) command).intValue();
                logger.info("Set brightness to {}%", col.brightness);
                api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, col.brightness.toString());
            }
        } else if (command instanceof OnOffType) {
            logger.info("Switch light {}", command);
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
                col.brightness = newBrightness;
            }
        }

        updateCurrentColors(lightId, col);
    }

    private CurrentColors getCurrentColors(Integer lightId) {
        CurrentColors col = channelColors.get(lightId);
        if (col == null) {
            col = new CurrentColors();  // create a new entry
            logger.debug("CurrentColors entry created for lightId {}", lightId.toString());
        }
        logger.debug("Colors loaded for lightId {}: RGBW={}/{}/{}/{}, Sat/Gain={}, Bright={}, Temp={} ",
                lightId.toString(), col.red, col.green, col.blue, col.white, col.gain, col.brightness, col.temp);
        return col;
    }

    private void updateCurrentColors(Integer lightId, CurrentColors col) {
        if (channelColors.get(lightId) == null) {
            channelColors.put(lightId, col);
        } else {
            channelColors.replace(lightId, col);
        }
        logger.debug("CurrentColors updated for lightId {}: RGBW={}/{}/{}/{}, Sat/Gain={}, Bright={}, Temp={} ",
                lightId.toString(), col.red, col.green, col.blue, col.white, col.gain, col.brightness, col.temp);
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
        int lightId = 0;
        for (ShellyStatusLightChannel light : status.lights) {
            Integer channelId = lightId + 1;
            logger.trace("Updating lightId {}/{}", lightId, channelId.toString());
            String controlGroup = buildControlGroupName(profile, channelId);

            logger.debug("Updating light channels {}.{}", profile.hostname, controlGroup);

            // The bulb has a combined channel set for color or white mode
            // The RGBW2 uses 2 different thing types: color=1 channel, white=4 channel
            if (profile.isBulb) {
                updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_LIGHT_COLOR_MODE, profile.inColor);
            }

            // Channel control/timer
            // ShellyStatusLightChannel light = status.lights.get(i);
            updateChannel(controlGroup, CHANNEL_LIGHT_POWER, getBool(light.ison));
            updateChannel(controlGroup, CHANNEL_TIMER_AUTOON, getDouble(light.auto_on));
            updateChannel(controlGroup, CHANNEL_TIMER_AUTOOFF, getDouble(light.auto_off));
            updateChannel(controlGroup, CHANNEL_RELAY_OVERPOWER, getBool(light.overpower));

            CurrentColors col = getCurrentColors(lightId);
            if (col == null) {
                logger.warn("Unable to load last colors for lightId {}", lightId);
                continue;
            }
            if (profile.inColor || profile.isBulb) {
                String colorGroup = CHANNEL_GROUP_COLOR_CONTROL;
                logger.debug("Updating light channels {}.{}: red={}, green={}, blue={}, white={}, gain={}", profile.hostname, colorGroup, light.red,
                        light.green,
                        light.blue, light.white, light.gain);
                col.setRGBW(light.red, light.green, light.blue, light.white);
                col.setGain(light.gain);
                // col.setTemp(light.temp);

                logger.trace("Update channels for {}: RGBW={}/{}/{}, in %:{}%/{}%/{}%, white={}/{}%, gain={}/{}% (brightness={}/{}%)", colorGroup,
                        col.red, col.green, col.blue, col.percentRed, col.percentGreen, col.percentBlue,
                        col.white, col.percentWhite, col.gain, col.percentGain, col.brightness, col.percentBrightness);
                updateChannel(colorGroup, CHANNEL_COLOR_RED, col.percentRed);
                updateChannel(colorGroup, CHANNEL_COLOR_GREEN, col.percentGreen);
                updateChannel(colorGroup, CHANNEL_COLOR_BLUE, col.percentBlue);
                updateChannel(colorGroup, CHANNEL_COLOR_WHITE, col.percentWhite);
                updateChannel(colorGroup, CHANNEL_COLOR_GAIN, col.percentGain);

                updateChannel(colorGroup, CHANNEL_COLOR_EFFECT, getInteger(light.effect));

                logger.trace("update {}.color picker", colorGroup);
                updateChannel(colorGroup, CHANNEL_COLOR_PICKER, col.toHSB());
            }
            if (!profile.inColor || profile.isBulb) {
                String colorGroup = CHANNEL_GROUP_WHITE_CONTROL;
                col.setBrightness(light.brightness);
                col.setTemp(light.temp);

                logger.debug("Updating light channels {}.{}: brightness={}/{}%, color-temp={}/{}%", profile.hostname, colorGroup,
                        light.brightness, col.percentBrightness, light.temp, col.percentTemp);
                updateChannel(controlGroup, CHANNEL_COLOR_BRIGHTNESS, col.percentBrightness);
                updateChannel(controlGroup, CHANNEL_COLOR_TEMP, col.percentTemp);
            }

            lightId++;
        }
    }

    private Integer getColorfromChannel(String group, String channel) {
        Double percent = ((PercentType) super.getChannelValue(group, channel)).doubleValue();
        return ((Double) (percent.doubleValue() * SHELLY_MAX_COLOR)).intValue();
    }

    private Integer getColorFromHSB(PercentType colorPercent) {
        return getColorFromHSB(colorPercent, new Double(SATURATION_FACTOR));
    }

    private Integer getColorFromHSB(PercentType colorPercent, Double factor) {
        return new Double(colorPercent.doubleValue() * factor).intValue();
    }

    private Integer setColor(Integer lightId, String colorName, Integer value) throws IOException, IllegalArgumentException {
        ShellyDeviceProfile profile = super.getProfile();
        if (profile.mode.equals(SHELLY_MODE_COLOR) && colorName.equals(SHELLY_COLOR_BRIGHTNESS)) {
            throw new IllegalArgumentException("Light is in Color Mode, command ignored!");
        }
        if (profile.mode.equals(SHELLY_MODE_WHITE) && !colorName.equals(SHELLY_COLOR_BRIGHTNESS)) {
            throw new IllegalArgumentException("Light is in White Mode, command ignored!");
        }

        Integer col = (Integer) super.getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, colorName);
        if ((col != null) && (col == value)) {
            throw new IllegalArgumentException("Color " + colorName + " was not changed (value=" + value.toString() + "), skip API call to update");
        }

        logger.info("Set color {} for channel {}, to {}", colorName, lightId, value.toString());
        api.setLightParm(lightId, colorName, value.toString());
        return value.intValue();
    }

    private Integer setColor(Integer lightId, String colorName, Command command, Integer minValue, Integer maxValue)
            throws IOException, IllegalArgumentException {
        DecimalType value = new DecimalType();
        if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            value = new DecimalType(maxValue * percent.intValue());
            logger.trace("Value for {} is in %: {}%={}", colorName, percent, value);
        } else if (command instanceof DecimalType) {
            value = (DecimalType) command;
            logger.trace("Value for {} is a number: {}", colorName, value);
        } else {
            throw new IllegalArgumentException("Invalid value for " + colorName + ": " + value.toString() + " / type " + value.getClass());
        }
        if (value.intValue() < minValue) {
            throw new IllegalArgumentException("Value " + value.intValue() + " for color " + colorName + " is out of range (< min)");
        }
        if (value.intValue() > maxValue) {
            throw new IllegalArgumentException("Value " + value.intValue() + " for color " + colorName + " is out of range (> max)");
        }
        return setColor(lightId, colorName, value.intValue());
    }

    private Integer setColor(Integer lightId, String colorName, Command command, Integer maxValue) throws IOException, IllegalArgumentException {
        return setColor(lightId, colorName, command, 0, maxValue);
    }

    private static String buildControlGroupName(ShellyDeviceProfile profile, Integer channelId) {
        return profile.isBulb || profile.inColor ? CHANNEL_GROUP_LIGHT_CONTROL
                : CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
    }

    private static Integer getLightIdFromGroup(String groupName) {
        if (groupName.startsWith(CHANNEL_GROUP_LIGHT_CHANNEL)) {
            return Integer.parseInt(StringUtils.substringAfter(groupName, CHANNEL_GROUP_LIGHT_CHANNEL)) - 1;
        }
        return 0; // only 1 light, e.g. bulb or rgbw2 in color mode
    }

}
