package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;

import java.io.IOException;

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
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellyHandlerBulb extends ShellyHandler {
    private final Logger  logger = LoggerFactory.getLogger(ShellyHandler.class);
    private ShellyHttpApi api;
    ShellyDeviceProfile   profile;

    /**
     * @param thing                 The thing passed by the HandlerFactory
     * @param handlerFactory        Handler Factory instance (will be used for event handler registration)
     * @param networkAddressService instance of NetworkAddressService to get access to the OH default ip settings
     */
    public ShellyHandlerBulb(Thing thing, ShellyHandlerFactory handlerFactory,
            NetworkAddressService networkAddressService) {
        super(thing, handlerFactory, networkAddressService);
    }

    @Override
    public void initialize() {
        super.initialize();
        api = super.getShellyApi();
    }

    @SuppressWarnings("null")
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);
        if (command instanceof RefreshType) {
            return;
        }

        profile = super.getDeviceProfile();
        if (profile == null) {
            return;
        }

        try {
            if (profile.isBulbColor) {
                String groupName = channelUID.getGroupId();
                switch (channelUID.getIdWithoutGroup()) {
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
                    case CHANNEL_BULB_AUTOOFF:
                        logger.info("Set auto-on to {}", command.toString());
                        api.setBulbSettings(0, SHELLY_BULB_AUTOOFF, ((DecimalType) command).toString());
                        break;

                    case CHANNEL_COLOR_PICKER:
                        if (command instanceof HSBType) {
                            HSBType hsb = (HSBType) command;
                            if (hsb.getBrightness().intValue() == 0) {
                                logger.info("Requested brightness = 0 -> switch off the bulb");
                                api.setBulbParm(0, SHELLY_BULB_TURN, SHELLY_API_OFF);  // switch the bulb off
                            } else {
                                if (profile.isBulbColor) {
                                    logger.info("Setting RGB colors to {}/{}/{} (sRGB={})}",
                                            hsb.getRed().intValue(), hsb.getGreen().intValue(), hsb.getBlue().intValue(), hsb.getRGB());
                                    setBulbColor(SHELLY_COLOR_RED, hsb.getRed().intValue());
                                    setBulbColor(SHELLY_COLOR_GREEN, hsb.getGreen().intValue());
                                    setBulbColor(SHELLY_COLOR_BLUE, hsb.getBlue().intValue());
                                } else {
                                    setBulbColor(SHELLY_COLOR_BRIGHTNESS, hsb.getBrightness().intValue());
                                    setBulbColor(SHELLY_COLOR_GAIN, hsb.getSaturation().intValue());
                                }
                            }
                        } else if (command instanceof PercentType) {
                            PercentType percent = (PercentType) command;
                            Integer brightness = SHELLY_MAX_BRIGHTNESS * percent.intValue();
                            api.setBulbParm(0, SHELLY_COLOR_BRIGHTNESS, brightness.toString());
                        } else if (command instanceof OnOffType) {
                            api.setBulbParm(0, SHELLY_BULB_TURN, (OnOffType) command == OnOffType.ON ? SHELLY_API_ON : SHELLY_API_OFF);
                        } else if (command instanceof IncreaseDecreaseType) {
                            Integer currentBrightness = (Integer) getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, CHANNEL_COLOR_BRIGHTNESS);
                            Integer newBrightness;
                            if (command == IncreaseDecreaseType.DECREASE) {
                                newBrightness = Math.max(currentBrightness - SHELLY_DIM_STEPSIZE, 0);
                            } else {
                                newBrightness = Math.min(currentBrightness + SHELLY_DIM_STEPSIZE, SHELLY_MAX_BRIGHTNESS);
                            }
                            api.setBulbParm(0, SHELLY_COLOR_BRIGHTNESS, newBrightness.toString());
                        }
                        break;

                    case CHANNEL_COLOR_RED:
                        setBulbColor(SHELLY_COLOR_RED, command, SHELLY_MAX_COLOR);
                        break;
                    case CHANNEL_COLOR_GREEN:
                        setBulbColor(SHELLY_COLOR_GREEN, command, SHELLY_MAX_COLOR);
                        break;
                    case CHANNEL_COLOR_BLUE:
                        setBulbColor(SHELLY_COLOR_BLUE, command, SHELLY_MAX_COLOR);
                        break;
                    case CHANNEL_COLOR_WHITE:
                        setBulbColor(SHELLY_COLOR_WHITE, command, SHELLY_MAX_COLOR);
                        break;
                    case CHANNEL_COLOR_GAIN:
                        setBulbColor(SHELLY_COLOR_GAIN, command, SHELLY_MAX_GAIN);
                        break;
                    case CHANNEL_COLOR_BRIGHTNESS:
                        setBulbColor(SHELLY_COLOR_BRIGHTNESS, command, SHELLY_MAX_BRIGHTNESS);
                        break;
                    case CHANNEL_COLOR_TEMP:
                        setBulbColor(SHELLY_COLOR_TEMP, command, MAX_COLOR_TEMPERATURE);
                        break;
                    case CHANNEL_COLOR_EFFECT:
                        logger.info("Set color effect to {}", command.toString());
                        api.setBulbParm(0, SHELLY_COLOR_EFFECT, ((DecimalType) command).toString());
                        break;
                }
                super.requestUpdates(1);
            }
        } catch (RuntimeException | IOException e) {
            logger.info("ERROR: Unable to process command for channel {}: {} ({})",
                    channelUID.toString(), e.getMessage(), e.getClass());
        }
    }

    private void setBulbColor(String colorName, Integer value) throws IOException {
        DecimalType currentValue = (DecimalType) super.getChannelValue(CHANNEL_GROUP_COLOR_CONTROL, colorName);
        if ((currentValue != null) && new DecimalType(value).equals(currentValue.intValue())) {
            logger.trace("Color {} was not changed (value={}), skip API call to update", colorName, value.toString());
            return;
        }

        logger.info("Set color {} to {}", colorName, value.toString());
        api.setBulbParm(0, colorName, value.toString());
    }

    private void setBulbColor(String colorName, Command command, Integer maxValue) throws IOException {
        DecimalType value = new DecimalType();
        if (command instanceof PercentType) {
            PercentType percent = (PercentType) command;
            Integer p = percent.intValue();
            value = new DecimalType(maxValue * percent.intValue());
        } else {
            value = (DecimalType) command;
        }
        if (value.intValue() > maxValue) {
            logger.debug("Value for color {} is out of range: {}/{}, set to {}", colorName, value.intValue(), maxValue, maxValue);
            value = new DecimalType(maxValue);
        }
        setBulbColor(colorName, value.intValue());
    }
}
