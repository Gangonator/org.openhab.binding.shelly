/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.api;

import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.openhab.binding.shelly.internal.ShellyBindingConstants;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsBulb;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsDevice;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsGlobal;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsStatus;
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

    public static final String SHELLY_URL_SETTINGS_RELAY        = "/settings/relay";
    public static final String SHELLY_URL_SETTINGS_RELAY_SETURL = SHELLY_URL_SETTINGS_RELAY + "/{0}?{1}={2}";
    public static final String SHELLY_URL_STATUS_RELEAY         = "/status/relay";
    public static final String SHELLY_URL_CONTROL_RELEAY        = "/relay";

    public static final String SHELLY_URL_CONTROL_ROLLER        = "/roller";

    public static final String SHELLY_URL_SETTINGSSENSOR_SETURL = SHELLY_URL_SETTINGS + "?{0}={1}";
    public static final String SHELLY_URL_SETTINGS_BULB         = "/settings/light";
    public static final String SHELLY_URL_CONTROL_BULB          = "/light";

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

    public static final String OPENHAB_HTTP_PORT                = "OPENHAB_HTTP_PORT";
    public static final String OPENHAB_DEF_PORT                 = "8080";

    public static final String HTTP_GET                         = "GET";
    public static final String HTTP_PUT                         = "PUT";
    public static final String HTTP_POST                        = "POST";
    public static final String HTTP_DELETE                      = "DELETE";
    public static int          SHELLY_API_TIMEOUT               = 5000;

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
        public Boolean              isBulb; // true if it is a Shelly Bulb
        public Boolean              isBulbColor; // true if bulb is in color mode
        public Boolean              isSensor; // true for HT & Smoke
        public Boolean              isSmoke; // true for Smoke

        public Boolean              supportsActionUrls;  // true if the action urls are supported
        public Boolean              supportsSensorUrls; // true if sensor url is supported

    }

    private final Logger        logger    = LoggerFactory.getLogger(ShellyHandler.class);
    private String              localIp   = "";
    private String              deviceIp  = "";
    private String              localPort = OPENHAB_DEF_PORT;

    private ShellyDeviceProfile profile;
    private Gson                gson      = new Gson();

    public ShellyHttpApi(ShellyConfiguration config) {
        this.deviceIp = config.deviceIp;
        localIp = config.localIp;
        Map<String, String> env = System.getenv();
        String portEnv = env.get(OPENHAB_HTTP_PORT);
        localPort = (portEnv != null) ? portEnv : OPENHAB_DEF_PORT;

    }

    public ShellySettingsDevice getDevInfo() throws IOException {
        String json = request(SHELLY_URL_DEVINFO, null);
        logger.info("Shelly device info : {}", json);
        return gson.fromJson(json, ShellySettingsDevice.class);
    }

    public ShellyDeviceProfile getDeviceProfile(String _thingType) throws IOException {
        String thingType = _thingType;
        if ((_thingType == null) && (profile != null) && (profile.thingType != null)) {
            thingType = profile.thingType;
        }
        String json;
        json = request(SHELLY_URL_SETTINGS, null);
        profile = new ShellyDeviceProfile();
        profile.settingsJson = json;
        profile.settings = gson.fromJson(json, ShellySettingsGlobal.class);

        // General settings
        profile.mac = getString(profile.settings.device.mac);
        profile.hostname = profile.settings.device.hostname != null && !profile.settings.device.hostname.isEmpty()
                ? profile.settings.device.hostname.toLowerCase()
                : "shelly-" + profile.mac.toUpperCase().substring(6, 11);
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
        profile.isBulb = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYBULB.getId())
                || thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYRGBW2.getId());
        profile.isBulbColor = profile.isBulb && profile.isBulb && profile.mode.equalsIgnoreCase(SHELLY_MODE_COLOR);
        profile.isSmoke = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
        profile.isSensor = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
        profile.hasBattery = thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYHT.getId()) ||
                thingType.equalsIgnoreCase(ShellyBindingConstants.THING_TYPE_SHELLYSMOKE.getId());
        profile.maxPower = profile.settings.max_power != null ? profile.settings.max_power : 0;

        profile.numRollers = getInteger(profile.settings.device.num_rollers);
        profile.numRelays = !profile.isBulb ? getInteger(profile.settings.device.num_outputs) : 0;
        profile.numMeters = getInteger(profile.settings.device.num_meters);
        if ((profile.numMeters == 0) && (profile.numRelays > 0)) {
            profile.numMeters = 1; // Shelly 1 reports no meters, but has one
        }
        profile.hasMeter = (profile.numMeters > 0);
        if ((profile.numRelays > 0) && (profile.settings.relays == null)) {
            profile.numRelays = 0;
        }
        profile.hasRelays = profile.numRelays > 0;

        profile.supportsActionUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_BTN_ON);
        profile.supportsSensorUrls = profile.settingsJson.contains(SHELLY_API_EVENTURL_REPORT);
        return profile;
    }

    public void setEventURLs() throws IOException {
        if (profile.supportsActionUrls) {
            // set event URLs for Shelly2/4 Pro
            logger.trace("Check/set Action event URLs for Relay or Roller");
            int i = 0;
            for (ShellySettingsRelay relay : profile.settings.relays) {
                logger.info("Current settings for relay[{}]: btn_on_url={}/btn_off_url={}, out_on_url={}, out_off_url={}", i,
                        relay.btn_on_url, relay.btn_off_url, relay.out_on_url, relay.out_off_url);
                setRelayEventUrls(i, profile.hostname);
                i++;
            }
        }
        setSensorEventUrls(profile.hostname);

    }

    public ShellySettingsStatus gerStatus() throws IOException {
        String result = request(SHELLY_URL_STATUS, null);
        ShellySettingsStatus status = gson.fromJson(result, ShellySettingsStatus.class);
        status.json = result;
        return status;
    }

    public ShellyStatusRelay getRelayStatus(Integer relayIndex) throws IOException {
        String result = request(SHELLY_URL_STATUS_RELEAY + "/" + relayIndex.toString(), null);
        return gson.fromJson(result, ShellyStatusRelay.class);
    }

    public void setRelayTurn(Integer relayIndex, String turnMode) throws IOException {
        request(SHELLY_URL_CONTROL_RELEAY + "/" + relayIndex.toString() + "?turn=" + turnMode.toLowerCase(), null);
    }

    public ShellyControlRoller getRollerStatus(Integer rollerIndex) throws IOException {
        String result = request(SHELLY_URL_CONTROL_ROLLER + "/" + rollerIndex.toString() + "/pos", null);
        return gson.fromJson(result, ShellyControlRoller.class);
    }

    public void setRollerTurn(Integer relayIndex, String turnMode) throws IOException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=" + turnMode, null);
    }

    public void setRollerPos(Integer relayIndex, Integer position) throws IOException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?go=to_pos&roller_pos=" + position.toString(), null);
    }

    public void setRollerTimer(Integer relayIndex, Integer timer) throws IOException {
        request(SHELLY_URL_CONTROL_ROLLER + "/" + relayIndex.toString() + "?timer=" + timer.toString(), null);
    }

    public void setRelayEventUrls(Integer relayIndex, String deviceName) throws IOException {
        String eventUrl = "http://" + localIp + ":" + localPort + SHELLY_CALLBACK_URI + "/" + deviceName + "/relay/"
                + relayIndex.toString();
        request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_BTN_ON, eventUrl), null);
        request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_BTN_OFF, eventUrl), null);
        request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_SW_ON, eventUrl), null);
        request(buildEventUrl(relayIndex, SHELLY_API_EVENTURL_REPORT, eventUrl), null);
    }

    public ShellyStatusSensor getSensorStatus() throws IOException {
        String result = request(SHELLY_URL_STATUS, null);
        return gson.fromJson(result, ShellyStatusSensor.class);
    }

    public void setSensorEventUrls(String deviceName) throws IOException {
        if (profile.supportsSensorUrls) {
            // set event URL for HT (report_url)
            logger.trace("Check/set Sensor Reporting URL");

            String eventUrl = "http://" + localIp + ":" + localPort + SHELLY_CALLBACK_URI + "/" + deviceName + "/sensordata";
            String setUrl = MessageFormat.format(SHELLY_URL_SETTINGSSENSOR_SETURL, SHELLY_API_EVENTURL_REPORT, urlEncode(eventUrl));
            request(setUrl, null);
        }
    }

    public void setTimer(Integer index, String timerName, Double value) throws IOException {
        String type = SHELLY_CLASS_RELAY;
        if (profile.isRoller) {
            type = SHELLY_CLASS_ROLLER;
        } else if (profile.isBulb) {
            type = SHELLY_CLASS_LIGHT;
        }
        String uri = SHELLY_URL_SETTINGS + "/" + type + "/" + index + "?" + timerName + "=" + ((Integer) value.intValue()).toString();
        request(uri, null);
    }

    public void setLedStatus(String ledName, Boolean value) throws IOException {
        request(SHELLY_URL_SETTINGS + "?" + ledName + "=" + (value ? SHELLY_API_ON : SHELLY_API_OFF), null);
    }

    public ShellySettingsBulb getBulbSettings() throws IOException {
        String result = request(SHELLY_URL_SETTINGS_BULB, null);
        return gson.fromJson(result, ShellySettingsBulb.class);
    }

    public void setBulbSettings(Integer bulbIndex, String parm, String value) throws IOException {
        request(SHELLY_URL_SETTINGS_BULB + "/" + bulbIndex.toString() + "?" + parm + "=" + value, null);
    }

    public void setBulbParm(Integer bulbIndex, String parm, String value) throws IOException {
        request(SHELLY_URL_CONTROL_BULB + "/" + bulbIndex.toString() + "?" + parm + "=" + value, null);
    }

    /**
    *
    */
    public String request(String uri, String postData) throws IOException {
        String url = "http://" + deviceIp + uri;
        Properties httpHeader = initHttpHeader();
        String httpResponse = "ERROR";
        try {
            if (postData != null) {
                InputStream content = new ByteArrayInputStream(postData.getBytes(Charset.forName(CHARSET_UTF8)));
                httpResponse = HttpUtil.executeUrl(HTTP_POST, url, httpHeader, content,
                        "application/x-www-form-urlencoded", SHELLY_API_TIMEOUT);
            } else {
                httpResponse = HttpUtil.executeUrl(HTTP_GET, url, SHELLY_API_TIMEOUT);
            }
            // all api responses are returning the result in Json format. If we are getting something else it must
            // be an error message, e.g. http result code
            if (!httpResponse.startsWith("{")) {
                throw new IOException("ERROR: Unexpected http resonse: " + httpResponse + ", url=" + url);
            }

            return httpResponse;
        } catch (IOException e) {
            throw new IOException(
                    "Shelly API call failed on url=" + url + ", response=" + httpResponse + ": " + e.getMessage());
        }
    }

    private String buildEventUrl(Integer relayIndex, String parameter, String url) throws IOException {
        return MessageFormat.format(SHELLY_URL_SETTINGS_RELAY_SETURL, relayIndex.toString(), parameter,
                urlEncode(url + "?type=" + StringUtils.substringBefore(parameter, "_url")));
    }

    private Properties initHttpHeader() {
        Properties httpHeader = new Properties();
        // httpHeader.setProperty(HEADER_USER_AGENT, OAUTH_USER_AGENT);
        // httpHeader.setProperty(HEADER_ACCEPT, "*/*");
        // httpHeader.setProperty(HEADER_LANGUAGE, "en-us");
        // httpHeader.setProperty(HEADER_CACHE_CONTROL, "no-cache");

        // httpHeader.setProperty("Content-Type", "application/x-www-form-urlencoded");
        return httpHeader;
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
