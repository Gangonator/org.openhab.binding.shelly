package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;
import static org.openhab.binding.shelly.internal.api.ShellyHttpApi.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
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
    private final Logger           logger        = LoggerFactory.getLogger(ShellyHandler.class);
    private ShellyHttpApi          api;

    Map<Integer, ShellyColorUtils> channelColors = new HashMap<Integer, ShellyColorUtils>();

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
            ShellyColorUtils col = getCurrentColors(lightId);
            boolean updated = false;

            logger.info("Execute command {} on channel {}, lightId={}", command.toString(), channelUID.getAsString(), lightId);
            switch (channelUID.getIdWithoutGroup()) {
                default: // non-bulb commands will be handled by the generic handler
                    super.handleCommand(channelUID, command);
                    break;

                case CHANNEL_LIGHT_COLOR_MODE:
                    logger.info("Select color mode {}", command.toString());
                    Validate.isTrue(command instanceof OnOffType, "Invalid value for color mode (ON or OFF): {}", command.toString());
                    api.setLightSetting(SHELLY_API_MODE, (OnOffType) command == OnOffType.ON ? SHELLY_MODE_COLOR : SHELLY_MODE_WHITE);
                    break;
                case CHANNEL_LIGHT_POWER:
                    logger.info("Switch light {}", command.toString());
                    Validate.isTrue(command instanceof OnOffType, "Invalid value for power (ON or OFF): {}", command.toString());
                    api.setLightParm(lightId, SHELLY_LIGHT_TURN, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                    break;

                case CHANNEL_COLOR_PICKER:
                    updated |= handleColorPicker(profile, lightId, col, command);
                    break;
                case CHANNEL_COLOR_RED:
                    col.setRed(setColor(lightId, SHELLY_COLOR_RED, command, SHELLY_MAX_COLOR));
                    updated = true;
                    break;
                case CHANNEL_COLOR_GREEN:
                    col.setGreen(setColor(lightId, SHELLY_COLOR_GREEN, command, SHELLY_MAX_COLOR));
                    updated = true;
                    break;
                case CHANNEL_COLOR_BLUE:
                    col.setBlue(setColor(lightId, SHELLY_COLOR_BLUE, command, SHELLY_MAX_COLOR));
                    updated = true;
                    break;
                case CHANNEL_COLOR_WHITE:
                    col.setWhite(setColor(lightId, SHELLY_COLOR_WHITE, command, SHELLY_MAX_COLOR));
                    updated = true;
                    break;
                case CHANNEL_COLOR_GAIN:
                    col.setGain(setColor(lightId, SHELLY_COLOR_GAIN, command, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN));
                    updated = true;
                    break;
                case CHANNEL_COLOR_BRIGHTNESS:  // only in white mode
                    col.setBrightness(setColor(lightId, SHELLY_COLOR_BRIGHTNESS, command, SHELLY_MAX_BRIGHTNESS));
                    updated = true;
                    break;
                case CHANNEL_COLOR_TEMP:
                    col.setTemp(setColor(lightId, SHELLY_COLOR_TEMP, command, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE));
                    updated = true;
                    break;

                case CHANNEL_COLOR_EFFECT:
                    logger.info("Set color effect {}", command.toString());
                    Integer effect = ((DecimalType) command).intValue();
                    validateRange("effect", effect, SHELLY_MIN_EFFECT, SHELLY_MAX_EFFECT);
                    col.setEffect(effect.intValue());
                    updated = true;
                    break;
            }

            if (updated) {
                updateColors(profile, lightId, col);
            }

            super.requestUpdates(1, true);
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})",
                    channelUID.toString(), e.getMessage(), e.getClass());
        }
    }

    private boolean handleColorPicker(ShellyDeviceProfile profile, Integer lightId, ShellyColorUtils col, Command command) throws IOException {
        boolean updated = false;
        if (command instanceof HSBType) {
            HSBType hsb = (HSBType) command;

            logger.debug("HSB-Info={}, Hue={}, getRGB={}, toRGB={}/{}/{}", hsb.toString(), hsb.getHue(), String.format("0x%08X", hsb.getRGB()),
                    hsb.toRGB()[0], hsb.toRGB()[1], hsb.toRGB()[2]);
            if (hsb.toString().contains("360,")) {
                logger.debug("need to fix the Hue value (360->0)");
                HSBType fixHue = new HSBType(new DecimalType(0), hsb.getSaturation(), hsb.getBrightness());
                hsb = fixHue;
            }

            col.setRed(getColorFromHSB(hsb.getRed())); // new Double((hsb.getRed().floatValue() * SATURATION_FACTOR)).intValue();
            col.setBlue(getColorFromHSB(hsb.getBlue())); // new Double((hsb.getBlue().floatValue() * SATURATION_FACTOR)).intValue();
            col.setGreen(getColorFromHSB(hsb.getGreen())); // new Double((hsb.getGreen().floatValue() * SATURATION_FACTOR)).intValue();
            col.setBrightness(getColorFromHSB(hsb.getBrightness(), BRIGHTNESS_FACTOR));  // new Double((hsb.getBrightness().floatValue() *
            // white, gain and temp are not part of the HSB color scheme

            updated = true;
        } else if (command instanceof PercentType) {
            if (!profile.inColor) {
                col.brightness = SHELLY_MAX_BRIGHTNESS * ((PercentType) command).intValue();
                updated = true;
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
                col.brightness = newBrightness;
                updated = true;
            }
        }
        return updated;
    }

    private ShellyColorUtils getCurrentColors(Integer lightId) {
        ShellyColorUtils col = channelColors.get(lightId);
        if (col == null) {
            col = new ShellyColorUtils();  // create a new entry
            logger.debug("Colors entry created for lightId {}", lightId.toString());
        } else {
            logger.debug("Colors loaded for lightId {}: RGBW={}/{}/{}/{}, gain={}, brightness={}, color temp={} ",
                    lightId.toString(), col.red, col.green, col.blue, col.white, col.gain, col.brightness, col.temp);
        }
        return col;
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

            ShellyColorUtils col = getCurrentColors(lightId);
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

    private void updateColorChannels(ShellyDeviceProfile profile, Integer channelId, ShellyColorUtils col) {
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

    private void updateWhiteChannels(ShellyDeviceProfile profile, Integer channelId, ShellyColorUtils col) {
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

    private Integer setColor(Integer lightId, String colorName, Command command, Integer minValue, Integer maxValue)
            throws IOException, IllegalArgumentException {
        DecimalType value = new DecimalType();
        validateRange(colorName, value.intValue(), minValue, maxValue);

        if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            Double v = new Double(maxValue) * percent.doubleValue() / 100.0;
            value = new DecimalType(v.intValue());
            logger.trace("Value for {} is in %: {}%={}", colorName, percent, value);
        } else if (command instanceof DecimalType) {
            value = (DecimalType) command;
            logger.trace("Value for {} is a number: {}", colorName, value);
        } else {
            throw new IllegalArgumentException("Invalid value for " + colorName + ": " + value.toString() + " / type " + value.getClass());
        }
        return value.intValue();
    }

    private Integer setColor(Integer lightId, String colorName, Command command, Integer maxValue) throws IOException, IllegalArgumentException {
        return setColor(lightId, colorName, command, 0, maxValue);
    }

    private void updateColors(ShellyDeviceProfile profile, Integer lightId, ShellyColorUtils newCol) throws IOException {
        boolean updated = false;
        Integer channelId = lightId + 1;
        ShellyColorUtils col = getCurrentColors(lightId);
        Validate.notNull(col, "Unable to load current colors!");

        logger.debug("Update color settings for channel {}: RGB {}/{}/{}, white={}, gain={}, brightness={}",
                channelId, col.red, col.green, col.blue, col.white, col.gain, col.brightness);
        if (profile.inColor) {
            if (!col.red.equals(newCol.red) || !col.green.equals(newCol.green) || !col.blue.equals(newCol.blue)) {
                logger.info("Setting RGB to {}/{}/{}", newCol.red, newCol.green, newCol.blue);
                Map<String, String> parms = new HashMap<String, String>();
                parms.put(SHELLY_LIGHT_TURN, SHELLY_API_ON);
                parms.put(SHELLY_COLOR_RED, newCol.red.toString());
                parms.put(SHELLY_COLOR_GREEN, newCol.green.toString());
                parms.put(SHELLY_COLOR_BLUE, newCol.blue.toString());
                parms.put(SHELLY_COLOR_WHITE, newCol.white.toString());
                api.setLightParms(lightId, parms);
                updated |= true;
            }
        }

        if (!col.gain.equals(newCol.gain)) {
            logger.info("Setting gain to {}", newCol.gain);
            api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, newCol.brightness.toString());
            updated |= true;
        }
        if ((profile.isBulb || !profile.inColor) && !col.brightness.equals(newCol.brightness)) {
            logger.info("Setting brightness to {}", newCol.brightness);
            api.setLightParm(lightId, SHELLY_COLOR_BRIGHTNESS, newCol.brightness.toString());
            updated |= true;
        }
        if ((profile.isBulb || !profile.inColor) && !col.temp.equals(newCol.temp)) {
            logger.info("Setting color temp to {}", newCol.temp);
            api.setLightParm(lightId, SHELLY_COLOR_TEMP, newCol.temp.toString());
            updated |= true;
        }

        if (col.effect.equals(newCol.effect)) {
            logger.info("Setting effect to {}", newCol.effect);
            api.setLightParm(lightId, SHELLY_COLOR_EFFECT, newCol.effect.toString());
            updated |= true;
        }

        if (updated) {
            updateCurrentColors(lightId, newCol);
        }
    }

    private void updateCurrentColors(Integer lightId, ShellyColorUtils col) {
        if (channelColors.get(lightId) == null) {
            channelColors.put(lightId, col);
        } else {
            channelColors.replace(lightId, col);
        }
        logger.debug("Colors updated for lightId {}: RGBW={}/{}/{}/{}, Sat/Gain={}, Bright={}, Temp={} ",
                lightId.toString(), col.red, col.green, col.blue, col.white, col.gain, col.brightness, col.temp);
    }

    private Integer getColorFromHSB(PercentType colorPercent) {
        return getColorFromHSB(colorPercent, new Double(SATURATION_FACTOR));
    }

    private Integer getColorFromHSB(PercentType colorPercent, Double factor) {
        Double value = new Double(Math.round(colorPercent.doubleValue() * factor));
        logger.trace("convert {}% into {}/{} (factor={})", colorPercent.toString(), value.toString(), value.intValue(), factor.toString());
        return value.intValue();
    }

    private static Integer getLightIdFromGroup(String groupName) {
        if (groupName.startsWith(CHANNEL_GROUP_LIGHT_CHANNEL)) {
            return Integer.parseInt(StringUtils.substringAfter(groupName, CHANNEL_GROUP_LIGHT_CHANNEL)) - 1;
        }
        return 0; // only 1 light, e.g. bulb or rgbw2 in color mode
    }

    private static String buildControlGroupName(ShellyDeviceProfile profile, Integer channelId) {
        return profile.isBulb || profile.inColor ? CHANNEL_GROUP_LIGHT_CONTROL
                : CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
    }

    private static String buildWhiteGroupName(ShellyDeviceProfile profile, Integer channelId) {
        return profile.isBulb && !profile.inColor ? CHANNEL_GROUP_WHITE_CONTROL
                : CHANNEL_GROUP_LIGHT_CHANNEL + channelId.toString();
    }

    private void validateRange(String name, Integer value, Integer min, Integer max) {
        Validate.isTrue((value >= min) && (value <= max), "Value " + name + " is out of range (" + min.toString() + "-" + max.toString() + ")");
    }

}
