/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.api;

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
import org.openhab.binding.shelly.internal.api.ShellyApiJson.Shelly2_Settings;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRelay;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellyControlRoller;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsDevice;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsStatus;
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
    public static final String SHELLY_URL_SETTINGSSENSOR_SETURL = SHELLY_URL_SETTINGS + "?{1}={2}";

    public static final String SHELLY_URL_CONTROL_RELEAY        = "/relay";
    public static final String SHELLY_URL_CONTROL_ROLLER        = "/roller";

    public static final String SHELLY_BTNT_MOMENTARY            = "momentary";
    public static final String SHELLY_BTNT_TOGGLE               = "toggle";
    public static final String SHELLY_BTNT_EDGE                 = "edge";
    public static final String SHELLY_BTNT_DETACHED             = "detached";
    public static final String SHELLY_STATE_LAST                = "last";
    public static final String SHELLY_STATE_STOP                = "stop";
    public static final String SHELLY_INP_MODE_OPENCLOSE        = "openclose";
    public static final String SHELLY_DEVMODE_RELAY             = "relay";
    public static final String SHELLY_OBSTMODE_DISABLED         = "disabled";
    public static final String SHELLY_SAFETYM_WHILEOPENING      = "while_opening";
    public static final String SHELLY_ALWD_TRIGGER_NONE         = "none";
    public static final String SHELLY_ALWD_ROLLER_TURN_OPEN     = "open";
    public static final String SHELLY_ALWD_ROLLER_TURN_CLOSE    = "close";
    public static final String SHELLY_ALWD_ROLLER_TURN_STOP     = "stop";

    public static final String SHELLY_CALLBACK_URI              = "/shelly/event";
    public static final String CONTENT_TYPE_XML                 = "text/xml; charset=UTF-8";
    public static final String CHARSET_UTF8                     = "utf-8";

    public static final String OPENHAB_HTTP_PORT                = "OPENHAB_HTTP_PORT";
    public static final String OPENHAB_DEF_PORT                 = "8080";

    public static final String HTTP_GET                         = "GET";
    public static final String HTTP_PUT                         = "PUT";
    public static final String HTTP_POST                        = "POST";
    public static final String HTTP_DELETE                      = "DELETE";
    public static int          SHELLY_API_TIMEOUT               = 5000;

    private final Logger       logger                           = LoggerFactory.getLogger(ShellyHandler.class);
    private String             localIp                          = "";
    private String             deviceIp                         = "";
    private String             localPort                        = OPENHAB_DEF_PORT;

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

        Gson gson = new Gson();
        return gson.fromJson(json, ShellySettingsDevice.class);
    }

    public Shelly2_Settings getSettings() throws IOException {
        String json = request(SHELLY_URL_SETTINGS, null);
        logger.debug("Shelly settings info : {}", json);
        Gson gson = new Gson();
        return gson.fromJson(json, Shelly2_Settings.class);
    }

    public ShellySettingsStatus gerStatus() throws IOException {
        String result = request(SHELLY_URL_STATUS, null);
        logger.debug("Shelly status info : {}", result);
        Gson gson = new Gson();
        return gson.fromJson(result, ShellySettingsStatus.class);
    }

    public ShellyControlRelay getRelayStatus(Integer relayIndex) throws IOException {
        String result = request(SHELLY_URL_CONTROL_RELEAY + "/" + relayIndex.toString(), null);
        Gson gson = new Gson();
        return gson.fromJson(result, ShellyControlRelay.class);
    }

    public void setRelayTurn(Integer relayIndex, String turnMode) throws IOException {
        request(SHELLY_URL_CONTROL_RELEAY + "/" + relayIndex.toString() + "?turn=" + turnMode.toLowerCase(), null);
    }

    public void setRelayTimer(Integer relayIndex, Integer timer) throws IOException {
        request(SHELLY_URL_CONTROL_RELEAY + "/" + relayIndex.toString() + "?timer=" + timer.toString(), null);
    }

    public ShellyControlRoller getRollerStatus(Integer rollerIndex) throws IOException {
        String result = request(SHELLY_URL_CONTROL_ROLLER + "/" + rollerIndex.toString(), null);
        Gson gson = new Gson();
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
        request(buildEventUrl(relayIndex, "btn_on_url", eventUrl), null);
        request(buildEventUrl(relayIndex, "btn_off_url", eventUrl), null);
        request(buildEventUrl(relayIndex, "out_on_url", eventUrl), null);
        request(buildEventUrl(relayIndex, "out_off_url", eventUrl), null);
    }

    public ShellyStatusSensor getSensorStatus() throws IOException {
        String result = request(SHELLY_URL_STATUS, null);
        Gson gson = new Gson();
        return gson.fromJson(result, ShellyStatusSensor.class);
    }

    public void setSensorEventUrls(String deviceName) throws IOException {
        String eventUrl = "http://" + localIp + ":" + localPort + SHELLY_CALLBACK_URI + "/" + deviceName + "/sensordata";
        String setUrl = MessageFormat.format(SHELLY_URL_SETTINGSSENSOR_SETURL, "report_url", urlEncode(eventUrl));
        request(setUrl, null);
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
        } catch (

        IOException e) {
            throw new IOException(
                    "Shelly API call failed, url=" + url + ", response=" + httpResponse + ", input=" + postData);
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

}
