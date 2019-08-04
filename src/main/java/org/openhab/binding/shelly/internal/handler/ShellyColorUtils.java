package org.openhab.binding.shelly.internal.handler;

import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;

import java.math.BigDecimal;

import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.PercentType;

public class ShellyColorUtils {

    // private final Logger logger = LoggerFactory.getLogger(ShellyColorUtils.class);

    public ShellyColorUtils() {

    }

    public ShellyColorUtils(ShellyColorUtils col) {
        setRed(col.red);
        setGreen(col.green);
        setBlue(col.blue);
        setWhite(col.white);
        setGain(col.gain);
        setBrightness(col.brightness);
        setTemp(col.temp);
    }

    Integer     red          = 0;
    Integer     green        = 0;
    Integer     blue         = 0;
    Integer     white        = 0;
    PercentType percentRed   = new PercentType(0);
    PercentType percentGreen = new PercentType(0);
    PercentType percentBlue  = new PercentType(0);
    PercentType percentWhite = new PercentType(0);

    void setRGBW(int red, int green, int blue, int white) {
        // logger.trace("setRGBW(): setting rgb+white to {}/{}/{}/{}", red, green, blue, white);
        setRed(red);
        setGreen(green);
        setBlue(blue);
        setWhite(white);
    }

    void setRed(int value) {
        // logger.trace(" setting red={}", value);
        red = value;
        percentRed = toPercent(red);
    }

    void setGreen(int value) {
        // logger.trace(" setting green={}", value);
        green = value;
        percentGreen = toPercent(green);
    }

    void setBlue(int value) {
        // logger.trace(" setting blue={}", value);
        blue = value;
        percentBlue = toPercent(blue);
    }

    void setWhite(int value) {
        // logger.trace(" setting white={}", value);
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
        // logger.trace(" setting brightness={}", value);
        brightness = value;
        percentBrightness = toPercent(brightness, SHELLY_MIN_BRIGHTNESS, SHELLY_MAX_BRIGHTNESS);
    }

    void setGain(int value) {
        // logger.trace(" setting gain={}", value);
        gain = value;
        percentGain = toPercent(gain, SHELLY_MIN_GAIN, SHELLY_MAX_GAIN);
    }

    void setTemp(int value) {
        // logger.trace(" setting temp={}", value);
        temp = value;
        percentTemp = toPercent(temp, MIN_COLOR_TEMPERATURE, MAX_COLOR_TEMPERATURE);
    }

    Integer effect = 0;

    void setEffect(int value) {
        // logger.trace(" setting effect={}", value);
        effect = value;
    }

    public HSBType toHSB() {
        // logger.trace("toHSB(): create HSB from {}/{}/{}", red, green, blue);
        return HSBType.fromRGB(red, green, blue);
    }

    private PercentType toPercent(Integer value) {
        return toPercent(value, 0, SHELLY_MAX_COLOR);
    }

    private PercentType toPercent(Integer _value, Integer min, Integer max) {
        Double range = max.doubleValue() - min.doubleValue();
        Double value = _value != null ? _value.doubleValue() : 0;
        value = value < min ? min.doubleValue() : value;
        value = value > max ? max.doubleValue() : value;
        Double percent = 0.0;
        if (range > 0) {
            percent = new Double(Math.round(value * 100.0 / range));
        }
        // logger.trace("Value converted from {} into {}%", value.intValue(), percent);
        return new PercentType(new BigDecimal(percent));
    }
}
