/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.api;

import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.openhab.binding.shelly.internal.ShellyBindingConstants;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.SellySendKeyList;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySenseKeyCode;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsDevice;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsGlobal;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsLight;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsStatus;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusLight;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyStatusSensor;
import org.openhab.binding.shelly.internal.config.ShellyConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Wrapper for the Shelly http api
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyHttpApi {
    public static final String SHELLY_API_MIN_FWVERSION         = "v1.5.0";

    public static final String SHELLY_NULL_URL                  = "null";
    public static final String SHELLY_URL_DEVINFO               = "/shelly";
    public static final String SHELLY_URL_STATUS                = "/status";
    public static final String SHELLY_URL_SETTINGS              = "/settings";
    public static final String SHELLY_URL_SETTINGS_AP           = "/settings/ap";
    public static final String SHELLY_URL_SETTINGS_STA          = "/settings/sta";
    public static final String SHELLY_URL_SETTINGS_LOGIN        = "/settings/sta";
    public static final String SHELLY_URL_SETTINGS_CLOUD        = "/settings/cloud";
    public static final String SHELLY_URL_LIST_IR               = "/ir/list";
    public static final String SHELLY_URL_SEND_IR               = "/ir/emit";

    public static final String SHELLY_URL_SETTINGS_RELAY        = "/settings/relay";
    public static final String SHELLY_URL_SETTINGS_RELAY_SETURL = SHELLY_URL_SETTINGS_RELAY + "/{0}?{1}={2}";
    public static final String SHELLY_URL_STATUS_RELEAY         = "/status/relay";
    public static final String SHELLY_URL_CONTROL_RELEAY        = "/relay";

    public static final String SHELLY_URL_CONTROL_ROLLER        = "/roller";

    public static final String SHELLY_URL_SETTINGSSENSOR_SETURL = SHELLY_URL_SETTINGS + "?{0}={1}";
    public static final String SHELLY_URL_SETTINGS_LIGHT        = "/settings/light";
    public static final String SHELLY_URL_CONTROL_LIGHT         = "/light";

    public static final String SHELLY_BTNT_MOMENTARY            = "momentary";
    public static final String SHELLY_BTNT_TOGGLE               = "toggle";
    public static final String SHELLY_BTNT_EDGE                 = "edge";
    public static final String SHELLY_BTNT_DETACHED             = "detached";
    public static final String SHELLY_STATE_LAST                = "last";
    public static final String SHELLY_STATE_STOP                = "stop";
    public static final String SHELLY_INP_MODE_OPENCLOSE        = "openclose";
    public static final String SHELLY_OBSTMODE_DISABLED         = "disabled";
    public static final String SHELLY_SAFETYM_WHILEOPENING      = "while_opening";
    public static final String SHELLY_ALWD_TRIGGER_NONE         = "none";
    public static final String SHELLY_ALWD_ROLLER_TURN_OPEN     = "open";
    public static final String SHELLY_ALWD_ROLLER_TURN_CLOSE    = "close";
    public static final String SHELLY_ALWD_ROLLER_TURN_STOP     = "stop";

    public static final String SHELLY_CALLBACK_URI              = "/shelly/event";
    public static final String SHELLY_SIMULATOR_URI             = "/shelly/simulator";
    public static final String CONTENT_TYPE_XML                 = "text/xml; charset=UTF-8";
    public static final String CHARSET_UTF8                     = "utf-8";

    public static final String SHELLY_IR_CODET_STORED           = "stored";
    public static final String SHELLY_IR_CODET_PRONTO           = "pronto";
    public static final String SHELLY_IR_CODET_PRONTO_HEX       = "pronto_hex";

    public static final String OPENHAB_HTTP_PORT                = "OPENHAB_HTTP_PORT";
    public static final String OPENHAB_DEF_PORT                 = "8080";

    public static final String HTTP_GET                         = "GET";
    public static final String HTTP_PUT                         = "PUT";
    public static final String HTTP_POST                        = "POST";
    public static final String HTTP_DELETE                      = "DELETE";
    public static int          SHELLY_API_TIMEOUT               = 35000;

    public class ShellyDeviceProfile {
        public String               thingType;

        public String               settingsJson;
        public ShellySettingsGlobal settings;

        public String               hostname;
        public String               mode;

        public String               hwRev;
        public String               hwBatchId;
        public String               mac;
        public String               fwId;
        public String               fwVersion;
        public String               fwDate;

        public Double               maxPower;
        public Integer              numMeters;
        public Integer              numRelays;
        public Integer              numRollers;

        public Boolean              hasRelays; // true if it has at least 1 power meter
        public Boolean              hasMeter; // true if it has at least 1 power meter
        public Boolean              hasBattery; // true if battery device
        public Boolean              hasLed; // true if battery device
        public Boolean              isRoller;  // true for Shelly2 in roller mode
        public Boolean              isPlugS;  // true if it is a Shelly Plug S
        public Boolean              isLight; // true if it is a Shelly Bulb/RGBW2
        public Boolean              isBulb; // true pnly if it is a Bulb
        public Boolean              isSense; // true if thing is a Shelly Sense
        public Boolean              inColor; // true if bulb/rgbw2 is in color mode
        public Boolean              isSensor; // true for HT & Smoke
        public Boolean              isSmoke; // true for Smoke

        public Map<String, String>  irCodes; // Sense: list of stored IR codes

        public Boolean              supportsActionUrls;  // true if the action urls are supported
        public Boolean              supportsSensorUrls; // true if sensor url is supported

    }

    private final Logger          logger    = LoggerFactory.getLogger(ShellyHandler.class);
    protected ShellyConfiguration config;
    private String                localPort = OPENHAB_DEF_PORT;
    public String                 thingName;

    private ShellyDeviceProfile   profile;
    private Gson                  gson      = new Gson();

    public ShellyHttpApi(ShellyConfiguration config) {
        this.config = config;
        Map<String, String> env = System.getenv();
        String portEnv = env.get(OPENHAB_HTTP_PORT);
        localPort = (portEnv != null) ? portEnv : OPENHAB_DEF_PORT;
        thingName = "";
    }

    public ShellySettingsDevice getDevInfo() throws IOException {
        String json = request(SHELLY_URL_DEVINFO);
        logger.info("Shelly device info : {}", json);
        return gson.fromJson(json, ShellySettingsDevice.class);
    }

    public ShellyDeviceProfile getDeviceProfile(String _thingType) throws IOException {
        String thingType = _thingType;
        if ((_thingType == null) && (profile != null) && (profile.thingType != null)) {
            thingType = profile.thingType;
        }
        String json;
        json = request(SHELLY_URL_SETTINGS);
        profile = new ShellyDeviceProfile();
        profile.settingsJson = json;
        profile.settings = gson.fromJson(json, ShellySettingsGlobal.class);

        // General settings
        profile.mac = getString(profile.settings.device.mac);
        profile.hostname = profile.settings.device.hostname != null && !profile.settings.device.hostname.isEmpty()
                ? profile.settings.device.hostname.toLowerCase()
                : "shelly-" + profile.mac.toUpperCase().substring(6, 11);
        thingName = profile.hostname;
        profile.mode = getString(profile.settings.mode) != null ? getString(profile.settings.mode).toLowerCase() : "";
        profile.hwRev = profile.settings.hwinfo != null ? getString(profile.settings.hwinfo.hw_revision) : "";
        profile.hwBatchId = profile.settings.hwinfo != null ? getString(profile.settings.hwinfo.batch_id.toString()) : "";
        profile.fwDate = getString(StringUtils.substringBefore(profile.settings.fw, "/"));
        profile.fwVersion = getString(StringUtils.substringBetween(profile.settings.fw, "/", "@"));
        profile.fwId = getString(StringUtils.substringAfter(profile.settings.fw, "@"));

        // Shelly1 has a meter, nevertheless numMeters is null!
        profile.thingType = thingType;
        profile.isRoller = profile.mode.equalsIgnoreCase(SHELLY_MODE_ROLLER);
        profile.isPlugS = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYPLUGS.getId());
        profile.hasLed = profile.isPlugS;
        profile.isBulb = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYBULB.getId());
        profile.isLight = profile.isBulb ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYRGBW2_COLOR.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYRGBW2_WHITE.getId());
        profile.inColor = profile.isLight && profile.mode.equalsIgnoreCase(SHELLY_MODE_COLOR);
        profile.isBulb = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYBULB.getId());
        profile.isSmoke = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
        profile.isSense = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSENSE.getId());
        profile.isSensor = profile.isSense || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSENSE.getId());
        profile.hasBattery = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSENSE.getId());
        profile.maxPower = profile.settings.max_power != null ? profile.settings.max_power : 0;

        profile.numRollers = getInteger(profile.settings.device.num_rollers);
        profile.numRelays = !profile.isLight ? getInteger(profile.settings.device.num_outputs) : 0;
        profile.numMeters = getInteger(profile.settings.device.num_meters);
        if ((profile.numMeters == 0) && (profile.numRelays > 0)) {
            profile.numMeters = 1; // Shelly 1 reports no meters, but has one
        }
        if (profile.isLight && (profile.numMeters == 0)) {
            logger.debug("Get number of meters from light status");
            ShellyStatusLight status = getLightStatus();
            profile.numMeters = status.meters != null ? status.meters.size() : 0;
        }
        profile.hasMeter = (profile.numMeters > 0);
        if ((profile.numRelays > 0) && (profile.settings.relays == null)) {
            profile.numRelays = 0;
        }
        profile.hasRelays = profile.numRelays > 0;

        profile.irCodes = profile.isSense ? getIRCodeList() : new HashMap<String, String>();

        profile.supportsActionUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_BTN_ON);
        profile.supportsSensorUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_REPORT);
        return profile;
    }

    public void setEventURLs(String deviceName) throws IOException {
        if (profile.supportsActionUrls) {
            // set event URLs for Shelly2/4 Pro
            logger.trace("Check/set Action event URLs for Relay or Roller for device {}", thingName);
            int i = 0;
            for (ShellySettingsRelay relay : profile.settings.relays) {
                logger.info("Current settings for relay[{}]: btn_on_url={}/btn_off_url={}, out_on_url={}, out_off_url={}", i,
                        relay.btn_on_url, relay.btn_off_url, relay.out_on_url, relay.out_off_url);
                setRelayEventUrls(i, deviceName);
                i++;
            }
        }
        setSensorEventUrls(deviceName);
    }

    public ShellySettingsStatus gerStatus() throws IOException {
        String result = request(SHELLY_URL_STATUS);
        ShellySettingsStatus status = gson.fromJson(result, ShellySettingsStatus.class);
        status.json = result;
        return status;
    }

    public ShellyStatusRelay getRelayStatus(Integer relayIndex) throws IOException {
        String result = request(SHELLY_URL_STATUS_RELEAY + "/" + relayIndex.toString());
        return gson.fromJson(result, ShellyStatusRelay.class);
    }

    public void setRelayTurn(Integer relayIndex, String turnMode) throws IOException {
        request(SHELLY_URL_CONTROL_RELEAY + "/" + relayIndex.toString() + "?turn=" + turnMode.toLowerCase());
    }

    public ShellyControlRoller getRollerStatus(Integer rollerIndex) throws IOException {
        String result = request(SHELLY_URL_CONTROL_ROLLER + "/" + rollerIndex.toString() + "/pos");
        return gson.fromJson(result, ShellyControlRoller.class);
    }

    public void setRollerTurn(Integer relayIndex, String turnMode) throws IOException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=" + turnMode);
    }

    public void setRollerPos(Integer relayIndex, Integer position) throws IOException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=to_pos&roller_pos=" + position.toString());
    }

    public void setRollerTimer(Integer relayIndex, Integer timer) throws IOException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?timer=" + timer.toString());
    }

    public void setRelayEventUrls(Integer relayIndex, String deviceName) throws IOException {
        String eventUrl = "http://" + config.localIp + ":" + localPort + SHELLY_CALLBACK_URI + "/" + deviceName + "/relay/"
                + relayIndex.toString();
        if (config.eventsRelayButton) {
            request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_BTN_ON, eventUrl));
            request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_BTN_OFF, eventUrl));
        }
        if (config.eventsRelaySwitch) {
            request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_SW_ON, eventUrl));
            request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_SW_OFF, eventUrl));
        }
    }

    public ShellyStatusSensor getSensorStatus() throws IOException {
        ShellyStatusSensor status = gson.fromJson(request(SHELLY_URL_STATUS), ShellyStatusSensor.class);
        if (profile.isSense) {
            // complete reported data
            status.tmp.tC = status.tmp.units.equals(SHELLY_TEMP_CELSIUS) ? status.tmp.value : 0;
            status.tmp.tF = status.tmp.units.equals(SHELLY_TEMP_FAHRENHEIT) ? status.tmp.value : 0;
        }
        return status;
    }

    public void setSensorEventUrls(String deviceName) throws IOException {
        if (profile.supportsSensorUrls && config.eventsSensorReport) {
            // set event URL for HT (report_url)
            logger.trace("Check/set Sensor Reporting URL");

            String eventUrl = "http://" + config.localIp + ":" + localPort + SHELLY_CALLBACK_URI + "/" + deviceName + "/sensordata";
            String setUrl = MessageFormat.format(SHELLY_URL_SETTINGSSENSOR_SETURL, SHELLY_API_EVENTURL_REPORT, urlEncode(eventUrl));
            request(setUrl);
        }
    }

    public void setTimer(Integer index, String timerName, Double value) throws IOException {
        String type = SHELLY_CLASS_RELAY;
        if (profile.isRoller) {
            type = SHELLY_CLASS_ROLLER;
        } else if (profile.isLight) {
            type = SHELLY_CLASS_LIGHT;
        }
        String uri = SHELLY_URL_SETTINGS + "/" + type + "/" + index + "?" + timerName + "=" + ((Integer) value.intValue()).toString();
        request(uri);
    }

    public void setLedStatus(String ledName, Boolean value) throws IOException {
        request(SHELLY_URL_SETTINGS + "?" + ledName + "=" + (value ? SHELLY_API_TRUE : SHELLY_API_FALSE));
    }

    public ShellySettingsLight getLightSettings() throws IOException {
        String result = request(SHELLY_URL_SETTINGS_LIGHT);
        return gson.fromJson(result, ShellySettingsLight.class);
    }

    public ShellyStatusLight getLightStatus() throws IOException {
        String result = request(SHELLY_URL_STATUS);
        return gson.fromJson(result, ShellyStatusLight.class);
    }

    public void setLightSetting(String parm, String value) throws IOException {
        request(SHELLY_URL_SETTINGS + "?" + parm + "=" + value);
    }

    public void setLightMode(String mode) throws IOException {
        if (!mode.isEmpty() && !profile.mode.equals(mode)) {
            setLightSetting(SHELLY_API_MODE, mode);
            profile.mode = mode;
            profile.inColor = profile.isLight && profile.mode.equalsIgnoreCase(SHELLY_MODE_COLOR);
        }
    }

    public void setLightParm(Integer lightIndex, String parm, String value) throws IOException {
        request("/" + profile.mode + "/" + lightIndex.toString() + "?" + parm + "=" + value);
    }

    public void setLightParms(Integer lightIndex, Map<String, String> parameters) throws IOException {
        String url = "/" + profile.mode + "/" + lightIndex.toString() + "?";
        int i = 0;
        for (String key : parameters.keySet()) {
            if (i > 0) {
                url = url + "&";
            }
            url = url + key + "=" + parameters.get(key);
            i++;
        }
        request(url);
    }

    public Map<String, String> getIRCodeList() throws IOException {
        // String result = request(SHELLY_URL_LIST_IR);
        String result = "[[\"1_231_pwr\",\"tv(231) - Power\"],[\"1_231_chdwn\",\"tv(231) - Channel Down\"],[\"1_231_chup\",\"tv(231) - Channel Up\"], [\"1_231_voldwn\",\"tv(231) - Volume Down\"],[\"1_231_volup\",\"tv(231) - Volume Up\"],[\"1_231_mute\",\"tv(231) - Mute\"],[\"1_231_menu\",\"tv(231) - Menu\"],[\"1_231_inp\",\"tv(231) - Input\"],[\"1_231_info\",\"tv(231) - Info\"],[\"1_231_left\",\"tv(231) - Left\"],[\"1_231_up\",\"tv(231) - Up\"],[\"1_231_right\",\"tv(231) - Right\"],[\"1_231_ok\",\"tv(231) - OK\"],[\"1_231_down\",\"tv(231) - Down\"],[\"1_231_back\",\"tv(231) - Back\"],[\"6_546_pwr\",\"receiver(546) - Power\"],[\"6_546_voldwn\",\"receiver(546) - Volume Down\"],[\"6_546_volup\",\"receiver(546) - Volume Up\"],[\"6_546_mute\",\"receiver(546) - Mute\"],[\"6_546_menu\",\"receiver(546) - Menu\"],[\"6_546_info\",\"receiver(546) - Info\"],[\"6_546_left\",\"receiver(546) - Left\"],[\"6_546_up\",\"receiver(546) - Up\"],[\"6_546_right\",\"receiver(546) - Right\"],[\"6_546_ok\",\"receiver(546) - OK\"],[\"6_546_down\",\"receiver(546) - Down\"],[\"6_546_back\",\"receiver(546) - Back\"]]";

        String key_list = StringUtils.substringAfter(result, "[");
        key_list = StringUtils.substringBeforeLast(key_list, "]");
        key_list = key_list.replaceAll(java.util.regex.Pattern.quote("\",\""), "\", \"name\": \"");
        key_list = key_list.replaceAll(java.util.regex.Pattern.quote("["), "{ \"id\":");
        key_list = key_list.replaceAll(java.util.regex.Pattern.quote("]"), "} ");
        String json = "{\"key_codes\" : [" + key_list + "] }";

        SellySendKeyList codes = gson.fromJson(json, SellySendKeyList.class);
        Map<String, String> list = new HashMap<String, String>();
        for (ShellySenseKeyCode key : codes.key_codes) {
            list.put(key.id, key.name);
        }
        return list;
    }

    public void sendIRKey(String keyCode) throws IOException, IllegalArgumentException {
        String type = "";
        if (profile.irCodes.containsKey(keyCode)) {
            type = SHELLY_IR_CODET_STORED;
        } else if ((keyCode.length() > 4) && keyCode.contains(" ")) {
            type = SHELLY_IR_CODET_PRONTO;
        } else {
            type = SHELLY_IR_CODET_PRONTO_HEX;
        }
        String url = SHELLY_URL_SEND_IR + "?type=" + type;
        if (type.equals(SHELLY_IR_CODET_STORED)) {
            url = url + "&" + "id=" + keyCode;
        } else if (type.equals(SHELLY_IR_CODET_PRONTO)) {
            String code = Base64.getEncoder().encodeToString(keyCode.getBytes());
            Validate.notNull(code, "Unable to BASE64 encode the pronto code: " + keyCode);
            url = url + "&" + SHELLY_IR_CODET_PRONTO + "=" + code;
        } else if (type.equals(SHELLY_IR_CODET_PRONTO_HEX)) {
            url = url + "&" + SHELLY_IR_CODET_PRONTO_HEX + "=" + keyCode;
        }
        request(url);
    }

    public void setSenseSetting(String setting, String value) throws IOException {
        request(SHELLY_URL_SETTINGS + "?" + setting + "=" + value);
    }

    /**
    *
    */
    public String request(String uri) throws IOException {
        String url = "http://" + config.deviceIp + uri;
        String httpResponse = "ERROR";
        // boolean acquired = false;
        try {
            logger.trace("HTTP GET for {}: {}", thingName, url);
            httpResponse = HttpUtil.executeUrl(HTTP_GET, url, SHELLY_API_TIMEOUT);
            Validate.notNull(httpResponse, "httpResponse must not be null");
            // all api responses are returning the result in Json format. If we are getting something else it must
            // be an error message, e.g. http result code
            if (!httpResponse.startsWith("{") && !httpResponse.startsWith("[")) {
                throw new IOException("ERROR from " + thingName + ": Unexpected http resonse: " + httpResponse + ", url=" + url);
            }

            logger.trace("HTTP response from {}: {}", thingName, httpResponse);
            return httpResponse;
        } catch (IOException e) {
            throw new IOException(
                    "Shelly API call failed on url=" + url + ", response=" + httpResponse + ": " + e.getMessage() + " - " + e.getClass());
        }
    }

    private String buildEventUrl(Integer relayIndex, String parameter, String url) throws IOException {
        return MessageFormat.format(SHELLY_URL_SETTINGS_RELAY_SETURL, relayIndex.toString(), parameter,
                urlEncode(url + "?type=" + StringUtils.substringBefore(parameter, "_url")));
    }

    private String urlEncode(String input) throws IOException {
        try {
            return URLEncoder.encode(input, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IOException(
                    "Unsupported encoding format: " + StandardCharsets.UTF_8.toString() + ", input=" + input);
        }
    }

    static public String getString(String value) {
        return value != null ? value : "";
    }

    static public Integer getInteger(Object value) {
        return (value != null ? (Integer) value : 0);
    }

    static public Long getLong(Object value) {
        return (value != null ? (Long) value : 0);
    }

    static public Double getDouble(Object value) {
        return (value != null ? (Double) value : 0);
    }

    static public Boolean getBool(Object value) {
        return (value != null ? (Boolean) value : false);
    }
}
