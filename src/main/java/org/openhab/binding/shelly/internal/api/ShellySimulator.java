/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional information.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.shelly.internal.api;

import static org.openhab.binding.shelly.internal.api.ShellyHttpApi.*;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main OSGi service and HTTP servlet for MagentaTV NOTIFY.
 *
 * @author Markus Michels - Initial contribution
 */
@Component(service = HttpServlet.class, configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true)
public class ShellySimulator extends HttpServlet {
    private final Logger          logger = LoggerFactory.getLogger(ShellySimulator.class);
    private @Nullable HttpService httpService;

    @SuppressWarnings("null")
    @Activate
    protected void activate(Map<String, Object> config) {
        try {
            httpService.registerServlet(SHELLY_SIMULATOR_URI, this, null, httpService.createDefaultHttpContext());
            logger.info("Shelly Simulator started at '{}'", SHELLY_SIMULATOR_URI);
        } catch (ServletException | NamespaceException e) {
            logger.error("Shelly: Could not start SimulatorServlet: {} ({})", e.getMessage(), e.getClass());
        }
    }

    @SuppressWarnings("null")
    @Deactivate
    protected void deactivate() {
        httpService.unregister(SHELLY_SIMULATOR_URI);
        logger.info("Shelly: SimulatorServlet stopped");
    }

    /**
     *
     *
     * @param request
     * @param resp
     *
     * @throws ServletException, IOException
     */
    @SuppressWarnings("null")
    @Override
    protected void service(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String data = inputStreamToString(request);
        String output = "";

        try {
            String ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }
            Map<String, String[]> parameters = request.getParameterMap();
            logger.trace("CallbackServlet: {} Reqeust from {}:{}/{}?{}", request.getProtocol(), ipAddress, request.getRemotePort(), path,
                    parameters.toString());
            if (!path.toLowerCase().startsWith(SHELLY_CALLBACK_URI) || !path.contains("/event/shelly")) {
                logger.info("ERROR: CallbackServletreceived unknown request- path = {}, data={}", path, data);
                return;
            }

            if (path.contains("/settings")) {
                output = "{\"device\":{\"type\":\"SHBLB-1\",\"mac\":\"112233445566\",\"hostname\":\"shellybulb-445566\",\"num_outputs\":1},\"wifi_ap\":{\"enabled\":false,\"ssid\":\"shellybulb-445566\",\"key\":\"\"},\"wifi_sta\":{\"enabled\":true,\"ssid\":\"markus7017\",\"ipv4_method\":\"static\",\"ip\":\"127.0.0.1\",\"gw\":\"10.0.0.1\",\"mask\":\"255.255.255.0\",\"dns\":\10.0.0.2\"},\"wifi_sta1\":{\"enabled\":false,\"ssid\":null,\"ipv4_method\":\"dhcp\",\"ip\":null,\"gw\":null,\"mask\":null,\"dns\":null},\"mqtt\": {\"enable\":false,\"server\":\"10.10.10.10\",\"user\":\"\",\"reconnect_timeout_max\":60.000000,\"reconnect_timeout_min\":2.000000,\"clean_session\":true,\"keep_alive\":60,\"will_topic\":\"shellies/shellybulb-445566/online\",\"will_message\":\"false\",\"max_qos\":0,\"retain\":false,\"update_period\":30},\"sntp\": {\"server\":\"time.google.com\"},\"login\":{\"enabled\":false,\"unprotected\":false,\"username\":\"xxxx\",\"password\":\"xxxx\"},\"pin_code\":\"aXJ9eE\",\"coiot_execute_enable\":true,\"name\":\"\",\"fw\":\"20190711-084016/v1.5.0-hotfix4@3b4f7414\",\"build_info\":{\"build_id\":\"20190711-084016/v1.5.0-hotfix4@3b4f7414\",\"build_timestamp\":\"2019-07-11T08:40:16Z\",\"build_version\":\"1.0\"},\"cloud\":{\"enabled\":true,\"connected\":true},\"timezone\":\"Europe/Berlin\",\"lat\":51.394344,\"lng\":8.571319,\"tzautodetect\":false,\"time\":\"20:04\",\"hwinfo\": {\"hw_revision\":\"prod-1.3\",\"batch_id\":1},\"mode\":\"color\",\"lights\":[{\"ison\":false,\"red\":255,\"green\":167,\"blue\":16,\"white\":0,\"gain\":100,\"temp\":3243,\"brightness\":50,\"effect\":0,\"default_state\":\"on\",\"auto_on\":0.00,\"auto_off\":0.00,\"power\":0.00,\"schedule\":false,\"schedule_rules\":[]}]}";
            } else if (path.contains("/status")) {
                output = "{\"wifi_sta\":{\"connected\":true,\"ssid\":\"markus7017\",\"ip\":\"127.0.0.1\",\"rssi\":-70},\"cloud\":{\"enabled\":true,\"connected\":true},\"mqtt\":{\"connected\":false},\"time\":\"19:53\",\"serial\":116,\"has_update\":false,\"mac\":\"112233445566\",\"lights\":[{\"ison\":false,\"mode\":\"white\",\"red\":255,\"green\":167,\"blue\":16,\"white\":0,\"gain\":100,\"temp\":3000,\"brightness\":100,\"effect\":0}],\"meters\":[{\"power\":0.00,\"is_valid\":\"true\"}],\"update\":{\"status\":\"idle\",\"has_update\":false,\"new_version\":\"20190711-084016/v1.5.0-hotfix4@3b4f7414\",\"old_version\":\"20190711-084016/v1.5.0-hotfix4@3b4f7414\"},\"ram_total\":51040,\"ram_free\":37844,\"fs_size\":233681,\"fs_free\":171182,\"uptime\":1026899}";
            } else {
                output = "Invalid request: " + path;
            }
        } catch (RuntimeException e) {
            logger.info("ERROR: Exception processing simulator request: {} ({}), path={}, parameters={}, data='{}'",
                    e.getMessage(), e.getClass(), path, data, request.getParameterMap().toString(), data);
        } finally {
            resp.setCharacterEncoding(CHARSET_UTF8);
            resp.getWriter().write(output);
        }
    }

    @SuppressWarnings("resource")
    private String inputStreamToString(@Nullable HttpServletRequest request) throws IOException {
        @SuppressWarnings("null")
        Scanner scanner = new Scanner(request.getInputStream()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    @Reference
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
        logger.debug("httpService bound");
    }

    public void unsetHttpService(HttpService httpService) {
        this.httpService = null;
        logger.debug("httpService unbound");
    }
}
