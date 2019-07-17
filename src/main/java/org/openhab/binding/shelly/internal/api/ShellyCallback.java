/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
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
import org.openhab.binding.shelly.internal.ShellyHandlerFactory;
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
 * Main OSGi service and HTTP servlet for TelekomTV NOTIFY.
 *
 * @author Markus Michels - Initial contribution
 */
@Component(service = HttpServlet.class, configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true)
public class ShellyCallback extends HttpServlet {
    private static final long    serialVersionUID = 549582869577534569L;

    private final Logger         logger           = LoggerFactory.getLogger(ShellyCallback.class);

    private HttpService          httpService;
    private ShellyHandlerFactory handlerFactory;

    /**
     * OSGi activation callback.
     *
     * @param config Config properties
     */
    @Activate
    protected void activate(Map<String, Object> config) {
        try {
            httpService.registerServlet(SHELLY_CALLBACK_URI, this, null, httpService.createDefaultHttpContext());
            logger.info("Shelly: CallbackServlet started at '{}'", SHELLY_CALLBACK_URI);
        } catch (ServletException | NamespaceException e) {
            logger.error("Shelly: Could not start CallbackServlet: {} ({})", e.getMessage(), e.getClass());
        }
    }

    /**
     * OSGi de-activation callback.
     */
    @Deactivate
    protected void deactivate() {
        httpService.unregister(SHELLY_CALLBACK_URI);
        logger.info("Shelly: CallbackServlet stopped");
    }

    /**
     * Event callback handler
     */
    @SuppressWarnings("null")
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException {
        String data = inputStreamToString(request);
        String path = request.getRequestURI();
        try {
            String ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }
            logger.trace("CallbackServlet: Reqeust from {}:{}{} ({}, {})", ipAddress, request.getRemotePort(), path,
                    request.getRemoteHost(), request.getProtocol());
            if (!path.toLowerCase().startsWith(SHELLY_CALLBACK_URI) || !path.contains("/event/shelly")) {
                logger.info("ERROR: CallbackServletreceived unknown request- path = {}, data={}", path, data);
                return;
            }

            String deviceName = StringUtils.substringBetween(path, "/event/", "/");
            Map<String, String[]> parameters = request.getParameterMap();
            handlerFactory.onUpdateEvent(deviceName, parameters, data);

        } catch (RuntimeException e) {
            if (data != null) {
                logger.info("ERROR: Exception processing callback: {} ({}), path={}, data='{}'", e.getMessage(),
                        e.getClass(), path, data);
            } else {
                logger.info("ERROR: Exception processing callback: {} ({}), path={}", e.getMessage(), e.getClass(),
                        path);
            }
        } finally {
            setHeaders(resp);
            resp.getWriter().write("");
        }
    }

    @SuppressWarnings("resource")
    private String inputStreamToString(HttpServletRequest request) throws IOException {
        Scanner scanner = new Scanner(request.getInputStream()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    private void setHeaders(HttpServletResponse response) {
        response.setCharacterEncoding(CHARSET_UTF8);
        // response.setHeader("Access-Control-Allow-Origin", "*");
        // response.setHeader("Access-Control-Allow-Methods", "POST");
        // response.setHeader("Access-Control-Max-Age", "3600");
        // response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With,
        // Content-Type, Accept");
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void setShellyHandlerFactory(ShellyHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
        logger.debug("ShellyHandlerFactory bound to CallbackServlet");
    }

    public void unsetShellyHandlerFactory(ShellyHandlerFactory handlerFactory) {
        this.handlerFactory = null;
    }

    @Reference
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    public void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }
}
