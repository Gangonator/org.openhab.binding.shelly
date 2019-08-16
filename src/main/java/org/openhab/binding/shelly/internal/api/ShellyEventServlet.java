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

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.shelly.internal.handler.ShellyHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main OSGi service and HTTP servlet for MagentaTV NOTIFY.
 *
 * @author Markus Michels - Initial contribution
 * @author Gaël L'hopital - derived from Netatmo binding's servlet
 */
@Component(service = HttpServlet.class, configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true)
public class ShellyEventServlet extends HttpServlet {
    private static final long              serialVersionUID = 549582869577534569L;
    private final Logger                   logger           = LoggerFactory.getLogger(ShellyEventServlet.class);

    private @Nullable HttpService          httpService;
    private @Nullable ShellyHandlerFactory handlerFactory;

    @SuppressWarnings("null")
    @Activate
    protected void activate(Map<String, Object> config) {
        try {
            httpService.registerServlet(SHELLY_CALLBACK_URI, this, null, httpService.createDefaultHttpContext());
            logger.info("Shelly: CallbackServlet started at '{}'", SHELLY_CALLBACK_URI);
        } catch (ServletException | NamespaceException e) {
            logger.error("Shelly: Could not start CallbackServlet: {} ({})", e.getMessage(), e.getClass());
        }
    }

    @SuppressWarnings("null")
    @Deactivate
    protected void deactivate() {
        httpService.unregister(SHELLY_CALLBACK_URI);
        logger.info("Shelly: CallbackServlet stopped");
    }

    /**
     * Notify servlet handler (will be called by jetty
     *
     * Format of SOAP message: <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"> <e:property>
     * <uniqueDeviceID>1C18548DAF7DE9BC231249DB28D2A650</uniqueDeviceID> </e:property> <e:property> <messageBody>X-pairingCheck:5218C0AA</messageBody>
     * </e:property> </e:propertyset>
     *
     * Format of event message: <?xml version="1.0"?> <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"> <e:property>
     * <STB_Mac>AC6FBB61B1E5</STB_Mac> </e:property> <e:property> <STB_playContent>{&quot;new_play_mode&quot;:0,&quot;playBackState&quot;:1,&
     * quot;mediaType&quot;:1,&quot;mediaCode&quot;:&quot;3682&quot;}</ STB_playContent> </e:property> </e:propertyset>
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
        String data = inputStreamToString(request);
        String path = request.getRequestURI().toLowerCase();
        String deviceName = "";
        String index = "";
        String type = "";

        try {
            String ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }
            Map<String, String[]> parameters = request.getParameterMap();
            logger.debug("CallbackServlet: {} Request from {}:{}{}?{}", request.getProtocol(), ipAddress, request.getRemotePort(), path,
                    parameters.toString());
            if (!path.toLowerCase().startsWith(SHELLY_CALLBACK_URI) || !path.contains("/event/shelly")) {
                logger.info("ERROR: CallbackServletreceived unknown request- path = {}, data={}", path, data);
                return;
            }

            // URL looks like
            // <ip address>:<remote port>/shelly/event/shellyrelay-XXXXXX/relay/n?xxxxx or
            // <ip address>:<remote port>/shelly/event/shellyrelay-XXXXXX/roller/n?xxxxx or
            // <ip address>:<remote port>/shelly/event/shellyht-XXXXXX/sensordata?hum=53,temp=26.50
            deviceName = StringUtils.substringBetween(path, "/event/", "/").toLowerCase();
            if (path.contains("/" + EVENT_TYPE_RELAY + "/") || path.contains("/" + EVENT_TYPE_ROLLER + "/")) {
                index = StringUtils.substringAfterLast(path, "/").toLowerCase();
                type = StringUtils.substringBetween(path, deviceName + "/", "/" + index);
            } else {
                index = "";
                type = StringUtils.substringAfterLast(path, "/").toLowerCase();
            }
            logger.trace("Process event of type type={} for device {}, index={}", type, deviceName, index);
            handlerFactory.onEvent(deviceName, index, type, parameters, data);

        } catch (RuntimeException e) {
            logger.info("ERROR: Exception processing callback: {} ({}), path={}, data='{}'; deviceName={}, index={}, type={}, parameters={}",
                    e.getMessage(), e.getClass(), path, data, deviceName, index, type, request.getParameterMap().toString());
        } finally {
            setHeaders(resp);
            resp.getWriter().write("");
        }
    }

    private void setHeaders(HttpServletResponse response) {
        response.setCharacterEncoding(CHARSET_UTF8);
        // response.setHeader("Access-Control-Allow-Origin", "*");
        // response.setHeader("Access-Control-Allow-Methods", "POST");
        // response.setHeader("Access-Control-Max-Age", "3600");
        // response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With,
        // Content-Type, Accept");
    }

    @SuppressWarnings("resource")
    private String inputStreamToString(@Nullable HttpServletRequest request) throws IOException {
        @SuppressWarnings("null")
        Scanner scanner = new Scanner(request.getInputStream()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void setShellyHandlerFactory(ShellyHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
        logger.debug("HandlerFactory bound");
    }

    public void unsetShellyHandlerFactory(ShellyHandlerFactory handlerFactory) {
        this.handlerFactory = null;
        logger.debug("HandlerFactory unbound");
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
