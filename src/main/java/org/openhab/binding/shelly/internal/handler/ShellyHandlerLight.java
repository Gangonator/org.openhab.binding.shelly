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

        Integer effect = 0;

        void setEffect(int value) {
            logger.trace("   setting effect={}", value);
            effect = value;
        }

        public HSBType toHSB() {
            logger.trace("toHSB(): create HSB from {}/{}/{}", red, green, blue);
            return HSBType.fromRGB(red, green, blue);
        }

        private PercentType toPercent(Integer value) {
            return toPercent(value, 0, SHELLY_MAX_COLOR);
        }

        private PercentType toPercent(Integer _value, Integer min, Integer max) {
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
                    col.setEffect(((DecimalType) command).intValue());
                    if ((col.effect < SHELLY_MIN_EFFECT) || (col.effect > SHELLY_MAX_EFFECT)) {
                        logger.info("Invalid value for effect: {}", col.effect);
                    } else {
                        logger.info("Selecting effect #{}", col.effect);
                        api.setLightParm(lightId, SHELLY_COLOR_EFFECT, col.effect.toString());
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
            if (hsb.getHue() == new DecimalType(360)) {
                logger.debug("need to fix the Hue value (360->0)");
                HSBType fixHue = new HSBType(new DecimalType(0), hsb.getSaturation(), hsb.getBrightness());
                hsb = fixHue;
            }

            col.setRed(getColorFromHSB(hsb.getRed())); // new Double((hsb.getRed().floatValue() * SATURATION_FACTOR)).intValue();
            col.setBlue(getColorFromHSB(hsb.getBlue())); // new Double((hsb.getBlue().floatValue() * SATURATION_FACTOR)).intValue();
            col.setGreen(getColorFromHSB(hsb.getGreen())); // new Double((hsb.getGreen().floatValue() * SATURATION_FACTOR)).intValue();
            col.setBrightness(getColorFromHSB(hsb.getBrightness(), BRIGHTNESS_FACTOR));  // new Double((hsb.getBrightness().floatValue() *
            // white, gain and temp are not part of the HSB color scheme

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
                // parms.put(SHELLY_COLOR_GAIN, col.gain.toString());
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
                super.updateChannel(CHANNEL_GROUP_LIGHT_CONTROL, CHANNEL_LIGHT_COLOR_MODE, profile.inColor);
            }

            // Channel control/timer
            // ShellyStatusLightChannel light = status.lights.get(i);
            super.updateChannel(controlGroup, CHANNEL_LIGHT_POWER, getBool(light.ison));
            super.updateChannel(controlGroup, CHANNEL_TIMER_AUTOON, getDouble(light.auto_on));
            super.updateChannel(controlGroup, CHANNEL_TIMER_AUTOOFF, getDouble(light.auto_off));
            super.updateChannel(controlGroup, CHANNEL_RELAY_OVERPOWER, getBool(light.overpower));

            CurrentColors col = getCurrentColors(lightId);
            if (col == null) {
                logger.warn("Unable to load last colors for lightId {}", lightId);
                continue;
            }
            if (profile.inColor || profile.isBulb) {
                col.setRGBW(getInteger(light.red), getInteger(light.green), getInteger(light.blue), getInteger(light.white));
                col.setGain(getInteger(light.gain));
                col.setEffect(getInteger(light.effect));
                updateColorChannels(profile, channelId, col);
            }
            if (!profile.inColor || profile.isBulb) {
                col.setBrightness(light.brightness);
                col.setTemp(light.temp);
                updateWhiteChannels(profile, channelId, col);
            }

            // continue with next light
            lightId++;
        }
    }

    private void updateColorChannels(ShellyDeviceProfile profile, Integer channelId, CurrentColors col) {
        String colorGroup = CHANNEL_GROUP_COLOR_CONTROL;
        logger.trace("Update channels for {}: RGBW={}/{}/{}, in %:{}%/{}%/{}%, white={}%, gain={}%", colorGroup,
                col.red, col.green, col.blue, col.percentRed, col.percentGreen, col.percentBlue, col.percentWhite, col.percentGain);
        super.updateChannel(colorGroup, CHANNEL_COLOR_RED, col.percentRed);
        super.updateChannel(colorGroup, CHANNEL_COLOR_GREEN, col.percentGreen);
        super.updateChannel(colorGroup, CHANNEL_COLOR_BLUE, col.percentBlue);
        super.updateChannel(colorGroup, CHANNEL_COLOR_WHITE, col.percentWhite);
        super.updateChannel(colorGroup, CHANNEL_COLOR_GAIN, col.percentGain);
        super.updateChannel(colorGroup, CHANNEL_COLOR_EFFECT, col.effect);

        logger.trace("update {}.color picker", colorGroup);
        super.updateChannel(colorGroup, CHANNEL_COLOR_PICKER, col.toHSB());
    }

    private void updateWhiteChannels(ShellyDeviceProfile profile, Integer channelId, CurrentColors col) {
        String whiteGroup = buildWhiteGroupName(profile, channelId);

        if (profile.isBulb) {
            logger.trace("Update {} channels: brightness={}, tempo={}", whiteGroup, col.percentBrightness, col.percentTemp);
            super.updateChannel(whiteGroup, CHANNEL_COLOR_TEMP, col.percentTemp);
            super.updateChannel(whiteGroup, CHANNEL_COLOR_BRIGHTNESS, col.percentBrightness);
        } else {
            logger.trace("Update {} channels: brightness={}", whiteGroup, col.percentBrightness);
            super.updateChannel(whiteGroup, CHANNEL_COLOR_BRIGHTNESS, col.percentBrightness);

        }
        logger.trace("update {}.color picker", whiteGroup);
        super.updateChannel(whiteGroup, CHANNEL_COLOR_PICKER, col.toHSB());
    }

    /*
     * private Integer getColorfromChannel(String group, String channel) {
     * Double percent = ((PercentType) super.getChannelValue(group, channel)).doubleValue();
     * return ((Double) (percent.doubleValue() * SHELLY_MAX_COLOR)).intValue();
     * }
     */

    private Integer getColorFromHSB(PercentType colorPercent) {
        return getColorFromHSB(colorPercent, new Double(SATURATION_FACTOR));
    }

    private Integer getColorFromHSB(PercentType colorPercent, Double factor) {
        Double value = colorPercent.doubleValue() * factor;
        logger.trace("convert {}% into {}/{} (factor={})", colorPercent.toString(), value.toString(), value.intValue(), factor.toString());
        return value.intValue();
    }

    private Integer setColor(Integer lightId, String colorName, Command command, Integer minValue, Integer maxValue)
            throws IOException, IllegalArgumentException {
        DecimalType value = new DecimalType();
        if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            Double v = new Double(maxValue) * percent.doubleValue() / 100.0;
            value = new DecimalType(v);
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

    private Integer setColor(Integer lightId, String colorName, Integer value) throws IOException, IllegalArgumentException {
        ShellyDeviceProfile profile = super.getProfile();
        Integer col = (Integer) super.getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, colorName);
        if ((col != null) && (col == value)) {
            logger.debug("Color {} was not changed (value={}), skip API call to update", colorName, value.toString());
        }

        logger.info("Set color {} for channel {}, to {}", colorName, lightId, value.toString());
        api.setLightParm(lightId, colorName, value.toString());
        return value;
    }

    private Integer setColor(Integer lightId, String colorName, Command command, Integer maxValue) throws IOException, IllegalArgumentException {
        return setColor(lightId, colorName, command, 0, maxValue);
    }

    private static String buildControlGroupName(ShellyDeviceProfile profile, Integer channelId) {
        return profile.isBulb || profile.inColor ? CHANNEL_GROUP_LIGHT_CONTROL
                : CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
    }

    private static String buildWhiteGroupName(ShellyDeviceProfile profile, Integer channelId) {
        return profile.isBulb && !profile.inColor ? CHANNEL_GROUP_WHITE_CONTROL
                : CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
    }

    private static Integer getLightIdFromGroup(String groupName) {
        if (groupName.startsWith(CHANNEL_GROUP_LIGHT_CHANNEL)) {
            return Integer.parseInt(StringUtils.substringAfter(groupName, CHANNEL_GROUP_LIGHT_CHANNEL)) - 1;
        }
        return 0; // only 1 light, e.g. bulb or rgbw2 in color mode
    }

}
