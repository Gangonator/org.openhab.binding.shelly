/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.shelly.internal.coap;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.SHELLY_MAX_ROLLER_POS;
import static org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.Validate;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.EmptyMessage;
import org.eclipse.californium.core.coap.MessageObserverAdapter;
import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.interceptors.MessageInterceptor;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.UdpMulticastConnector;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_Descr_P;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_Descr_act;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_Descr_blk;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_Descr_sen;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_DevDescription;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_GenericSensorList;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_Sensor;
import org.openhab.binding.shelly.internal.coap.ShellyCoapJSon.CoIoT_SensorTypeAdapter;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link ShellyCoapListener} handles the coap registration and events.
 *
 * @author Markus Michels - Initial contribution
 * @author Hans-Jörg Merk - Initial contribution
 */
public class ShellyCoapListener implements CoapHandler, MessageInterceptor {
    private final Logger                   logger                       = LoggerFactory.getLogger(ShellyCoapListener.class);

    private final ShellyHandler            thingHandler;
    private final ShellyThingConfiguration config;
    private final GsonBuilder              gsonBuilder;
    private final Gson                     gson;

    public static int                      CoIoT_PORT                   = 5683;
    public static String                   COAP_MULTICAST_ADDRESS       = "224.0.1.187";

    public static final String             COLOIT_URI_BASE              = "/cit/";
    public static final String             COLOIT_URI_DEVDESC           = COLOIT_URI_BASE + "d";
    public static final String             COLOIT_URI_DEVSTATUS         = COLOIT_URI_BASE + "s";

    public static final int                COIOT_OPTION_GLOBAL_DEVID    = 3332;
    public static final int                COIOT_OPTION_STATUS_VALIDITY = 3412;
    public static final int                COIOT_OPTION_STATUS_SERIAL   = 3420;

    public static final byte[]             EMPTY_BYTE                   = new byte[0];

    // private static final int COAP_PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
    InetAddress                            localAddr;
    InetSocketAddress                      localPort;
    InetAddress                            deviceAddr;
    // private CoapClient client1;
    private CoapClient                     statusClient;
    private CoapEndpoint                   statusEndpoint;
    private UdpMulticastConnector          statusConnector;
    private Request                        reqDescription;
    private Request                        reqStatus;
    private int                            lastSerial                   = -1;
    private Map<String, CoIoT_Descr_blk>   elementMap                   = new HashMap<String, CoIoT_Descr_blk>();
    private Map<String, CoIoT_Descr_sen>   sensorMap                    = new HashMap<String, CoIoT_Descr_sen>();

    /** A Store containing data about observation. */
    // private final ObservationStore observationStore;

    protected class ShellyStatusListener extends CoapResource {

        private ShellyCoapListener listener;

        public ShellyStatusListener(ShellyCoapListener listener) {
            super("cit", true);
            this.add(new CoapResource("s"));
            getAttributes().setTitle("ShellyCoapListener");
            this.listener = listener;
        }

        @Override
        public void handleGET(CoapExchange exchange) {
            Response response = new Response(ResponseCode.CONTENT);
            response.setOptions(exchange.getRequestOptions());
            response.setPayload(exchange.getRequestPayload());
            listener.processResponse(response);
        }

    }

    public ShellyCoapListener(ShellyHandler thingHandler, ShellyThingConfiguration config) {

        this.thingHandler = thingHandler;
        this.config = config;

        gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(CoIoT_GenericSensorList.class, new CoIoT_SensorTypeAdapter());
        gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();

    }

    public void start() {
        try {
            // Send device discovery to get Device Description
            reqDescription = sendRequest(reqDescription, config.deviceIp, COLOIT_URI_DEVDESC, Type.CON);

            if (statusClient == null) {
                NetworkConfig nc = NetworkConfig.getStandard();
                localAddr = InetAddress.getByName(config.localIp);
                localPort = new InetSocketAddress(CoIoT_PORT);
                deviceAddr = InetAddress.getByName(config.deviceIp);

                // Join the Multicast group on the selected network interface
                statusConnector = new UdpMulticastConnector(localAddr, localPort, CoAP.MULTICAST_IPV4);   // bind UDP listener
                statusEndpoint = new CoapEndpoint.Builder().setNetworkConfig(nc).setConnector(statusConnector).build();
                statusEndpoint.addInterceptor(this);
                statusClient = new CoapClient(completeUrl(config.deviceIp, COLOIT_URI_DEVSTATUS))
                        .setTimeout((long) SHELLY_API_TIMEOUT)
                        .useNONs()
                        .setEndpoint(statusEndpoint);

                // CoapServer server = new CoapServer(nc, CoIoT_PORT);
                // server.addEndpoint(statusEndpoint);
                // server.add(new ShellyStatusListener(this));
                // server.start();
            }

        } catch (Throwable e) {
            logger.warn("Coap Exception: {} ({})", e.getMessage(), e.getClass());
        }
    }

    @Override
    public void onLoad(CoapResponse response) {
        logger.warn("onLoad()");
    }

    @Override
    public void onError() {
        logger.warn("OBSERVING FAILED");
    }

    /**
     * Callback will be called by the interceptor when a request has been received from the Connector
     *
     * @param request Coap Request Packet
     */
    @SuppressWarnings("deprecation")
    @Override
    public void receiveRequest(Request request) {
        String srcAddr = request.getSourceContext().getPeerAddress().toString();
        if (request.getSourceContext().getPeerAddress().toString().contains(config.deviceIp)) {
            logger.trace("receiveRequest() for {}", srcAddr);
            Response response = Response.createResponse(request, ResponseCode.CONTENT);
            response.setSourceContext(request.getSourceContext());
            response.setMID(request.getMID());
            response.setOptions(request.getOptions());
            response.setPayload(request.getPayload());
            processResponse(response);
        }
    }

    /**
     * Process an inbound Response (or mapped Request)
     *
     * @param response The Response packet
     */
    protected void processResponse(Response response) {
        String payload = "";

        String devId = "";
        String uri = "";
        int validity = 0;
        int serial = 0;
        try {
            logger.debug("CoIoT Message from {}: Code {}, MID={}",
                    response.getSourceContext().getPeerAddress(), response.getCode(), response.getMID());
            logger.trace("  {}", response.toString());

            if (response.isCanceled() || response.isDuplicate() || response.isRejected()) {
                logger.debug("Packet was canceled, rejected or is a duplicate -> discarded");
                return;
            }

            if (response.getCode() == ResponseCode.CONTENT) {
                List<Option> options = response.getOptions().asSortedList();
                int i = 0;
                while (i < options.size()) {
                    Option o = options.get(i);
                    switch (o.getNumber()) {
                        case OptionNumberRegistry.URI_PATH:
                            uri = COLOIT_URI_BASE + o.getStringValue();
                            break;
                        case COIOT_OPTION_GLOBAL_DEVID:
                            devId = o.getStringValue();
                            break;
                        case COIOT_OPTION_STATUS_VALIDITY:
                            validity = o.getIntegerValue();
                            break;
                        case COIOT_OPTION_STATUS_SERIAL:
                            serial = o.getIntegerValue();
                            if (serial == lastSerial) {
                                logger.debug("CoIoT-{}: Serial {} was already processed, ignore update", devId, serial);
                                return;
                            }
                            break;
                        default:
                            logger.debug("COAP option {} rcevied: {}", o.getNumber(), o.getValue());
                    }
                    i++;
                }

                payload = response.getPayloadString();
                if (uri.equalsIgnoreCase(COLOIT_URI_DEVDESC) || (uri.isEmpty() && payload.contains(CoIoT_Tag_blk))) {
                    handleDeviceDescription(devId, payload);
                } else if (uri.equalsIgnoreCase(COLOIT_URI_DEVSTATUS) || (uri.isEmpty() && payload.contains(CoIoT_Tag_Generic))) {
                    handleStatusUpdate(devId, payload, serial);
                }
            } else {
                // error handling
                logger.warn("Shelly CoIoT: Unknown Response Code {} received, payload={}", response.getCode(), response.getPayloadString());
            }

            if (reqStatus == null) {
                /*
                 * Observe Status Updates
                 */
                reqStatus = sendRequest(reqStatus, config.deviceIp, COLOIT_URI_DEVSTATUS, Type.NON);
            }
        } catch (RuntimeException | IOException e) {
            logger.debug("{}: Unable to process CoIoT Message: {} ({}); payload={}", devId, e.getMessage(), e.getClass(), payload);
            lastSerial = -1;
        }
    }

    /**
     * Process a CoIoT device description message. This includes definitions on device units (Relay0, Relay1, Sensors etc.) as well as a definition of
     * sensors and actors.
     * This information needs to be stored allowing to map ids from status updates to the device units and matching the correct thing channel.
     *
     * @param payload Device desciption in JSon format, example:
     *                {"blk":[{"I":0,"D":"Relay0"}],"sen":[{"I":112,"T":"Switch","R":"0/1","L":0}],"act":[{"I":211,"D":"Switch","L":0,"P":[{"I":2011,"D":"ToState","R":"0/1"}]}]}
     *
     * @param devId   The device id reported in the CoIoT message.
     */
    private void handleDeviceDescription(String devId, String payload) {
        // Device description
        // payload = StringUtils.substringBefore(payload, "}]}]}") + "}]}]}";
        logger.debug("CoIoT: Device Description for {}: {}", devId, payload);

        // Decode Json
        CoIoT_DevDescription descr = gson.fromJson(payload, CoIoT_DevDescription.class);
        int i;
        for (i = 0; i < descr.blk.size(); i++) {
            CoIoT_Descr_blk blk = descr.blk.get(i);
            logger.debug("    id={}: {}", blk.I, blk.D);
            if (!elementMap.containsKey(blk.I)) {
                elementMap.put(blk.I, blk);
            } else {
                elementMap.replace(blk.I, blk);
            }
        }
        logger.debug("{}: Adding {} sensor definitions", devId, descr.sen.size());
        if (descr.sen != null) {
            for (i = 0; i < descr.sen.size(); i++) {
                CoIoT_Descr_sen sen = descr.sen.get(i);

                // Shelly1: reports a null description and type = Switch (instead of S) -> work around
                if ((sen.D == null) && sen.T.equals("Switch")) {
                    sen.D = "Relay0";
                    sen.T = "S";
                }

                logger.debug("    id {}: {}, Type={}, Range={}, Links={}", sen.I, sen.D, sen.T, sen.R, sen.L);
                if (!sensorMap.containsKey(sen.I)) {
                    sensorMap.put(sen.I, sen);
                } else {
                    sensorMap.replace(sen.I, sen);
                }
            }
        }
        if (descr.act != null) {
            logger.debug("  Device has {} actors", descr.act.size());
            for (i = 0; i < descr.act.size(); i++) {
                CoIoT_Descr_act act = descr.act.get(i);
                logger.debug("    id={}: {}, Links={}", act.I, act.D, act.L);
                for (int p = 0; p < act.P.size(); p++) {
                    CoIoT_Descr_P pinfo = act.P.get(p);
                    logger.debug("  P[{}]: {}, Range={}", pinfo.I, pinfo.D, pinfo.R);
                }
            }
        }

        // Save to thing properties
        thingHandler.updateProperties(PROPERTY_COAP_DESCR, payload);
    }

    /**
     * Process CoIoT status update message. If a status update is received, but the device description has not been received yet a GET is send to
     * query
     * device description.
     *
     * @param devId   device id included in the status packet
     * @param payload Coap payload (Json format), example:
     *                {"G":[[0,112,0]]}
     * @param serial  Serial for this request. If this the the same as last serial the update was already sent and processed so this one gets ignored.
     * @throws IOException Exception on sending GET for device description.
     */
    private void handleStatusUpdate(String devId, String payload, int serial) throws IOException {
        // payload = StringUtils.substringBefore(payload, "]]}") + "]]}";
        logger.debug("CoIoT: {}: Sensor data {}", devId, payload);
        Validate.notNull(elementMap, "elementMap must not be null!");
        if (elementMap.size() == 0) {
            // send discovery packet
            lastSerial = -1;
            reqDescription = sendRequest(reqDescription, config.deviceIp, COLOIT_URI_DEVDESC, Type.CON);

            // try to uses description from last initialization
            String savedDescr = thingHandler.getProperty(PROPERTY_COAP_DESCR);
            if (savedDescr.isEmpty()) {
                logger.debug("Device description not yet received, ignore device update");
                return;
            }

            // simulate received device description to create element table
            handleDeviceDescription(devId, savedDescr);
            logger.debug("Device description restored");
        }

        // Parse Json,
        CoIoT_GenericSensorList list = gson.fromJson(payload, CoIoT_GenericSensorList.class);
        Validate.notNull(list, "sensor list must not be empty!");
        Map<String, State> updates = new HashMap<String, State>();

        if (list.G == null) {
            logger.debug("{}: Sensor list is empty! Payload: {}", devId, payload);
            return;
        }

        ShellyDeviceProfile profile = thingHandler.getProfile(false);
        if (profile == null) {
            logger.debug("Thing not initialized yet, skip update");
            return;
        }

        logger.debug("{}: {} status update received", devId, list.G.size());
        for (int i = 0; i < list.G.size(); i++) {
            CoIoT_Sensor s = list.G.get(i);
            CoIoT_Descr_sen sen = sensorMap.get(s.index);
            if (sen != null) {
                // find matching sensor definition from device description, use the Link ID as index
                CoIoT_Descr_blk element = elementMap.get(sen.L);
                logger.debug("  Sensor[{}]: Index={}, Value={} ({}, Type={}, Range={}, Link={}: {})",
                        i, s.index, s.value, sen.D, sen.T, sen.R, sen.L, element != null ? element.D : "n/a");

                // Process status information and convert into channel updates
                String type = (element != null ? element.D : "").toLowerCase();
                Integer rIndex = Integer.parseInt(sen.L) + 1;

                switch (sen.T /* CoIoT_STypes.valueOf(sen.T) */) {
                    case "T" /* Temperature */:
                        Validate.isTrue(type.contains("sensors"), "Temp update for non-sensor");
                        updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_TEMP), new DecimalType(s.value));
                        break;
                    case "H" /* Humidity */:
                        Validate.isTrue(type.contains("sensors"), "Humidity update for non-sensor");
                        updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_HUM), new DecimalType(s.value));
                        break;
                    case "B" /* BatteryLevel */:
                        Validate.isTrue(type.contains("sensors"), "BatLevel update for non-sensor");
                        updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_BAT_LEVEL), new DecimalType(s.value));
                        break;
                    case "M" /* Motion */:
                        Validate.isTrue(type.contains("sensors"), "Motion update for non-sensor");
                        updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_MOTION),
                                s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                        break;
                    case "L" /* Luminosity */:
                        Validate.isTrue(type.contains("sensors"), "Luminosity update for non-sensor");
                        updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_SENSOR, CHANNEL_SENSOR_LUX), new DecimalType(s.value));
                        break;

                    case "W" /* Watt */:
                        String rGroup = profile.numMeters == 1 ? CHANNEL_GROUP_METER : CHANNEL_GROUP_METER + rIndex;
                        updates.put(ShellyHandler.mkChannelName(rGroup, CHANNEL_METER_CURRENTWATTS), new DecimalType(s.value));
                        break;

                    case "S" /* CatchAll */:
                        switch (sen.D.toLowerCase()) {
                            case "state":
                            case "relay0": // Shelly1
                                String mGroup = profile.numRelays == 1 ? CHANNEL_GROUP_RELAY_CONTROL : CHANNEL_GROUP_RELAY_CONTROL + rIndex;
                                updates.put(ShellyHandler.mkChannelName(mGroup, CHANNEL_RELAY_OUTPUT),
                                        s.value == 1 ? OnOffType.ON : OnOffType.OFF);
                                break;
                            case "position":
                                updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_ROL_CONTROL, CHANNEL_ROL_CONTROL_POS),
                                        new PercentType(new BigDecimal(SHELLY_MAX_ROLLER_POS - s.value)));
                                updates.put(ShellyHandler.mkChannelName(CHANNEL_GROUP_ROL_CONTROL, CHANNEL_ROL_CONTROL_POS),
                                        new PercentType(new BigDecimal(s.value)));
                                break;
                        }
                        break;

                    default:
                        logger.debug("Sensor data for type {} not processed, value={}", sen.T.toString(), s.value);
                        break;
                }
            } else {
                logger.warn("Update for unknown sensor[{}]: Index={}, Value={}", i, s.index, s.value);
            }
        }

        if (updates.size() > 0) {
            logger.debug("CoIoT-{}: Process {} channel updates", devId, updates.size());
            int i = 0;
            for (Map.Entry<String, State> u : updates.entrySet()) {
                logger.debug("  Update[{}] channel {}, value={}", i, u.getKey(), u.getValue());
                thingHandler.updateChannel(u.getKey(), u.getValue(), true);
                i++;
            }
        }
        lastSerial = serial;
    }

    /**
     * Send a new request (Discovery to get Device Description). Before a pending request will be canceled.
     *
     * @param request   The current request (this will be canceled an a new one will be created)
     * @param ipAddress Device's IP address
     * @param uri       The URI we are calling (CoIoT = /cit/d or /cit/s)
     * @param con       true: send as CON, false: send as NON
     * @return new packet
     *
     * @throws IOException
     */
    private Request sendRequest(Request request, String ipAddress, String uri, Type con) throws IOException {
        if ((request != null) && !request.isCanceled()) {
            request.cancel();
        }

        lastSerial = -1;
        return newRequest(ipAddress, uri, con).send();
    }

    /**
     * Allocate a new Request structure. A message observer will be added to get the callback when a response has been received.
     *
     * @param ipAddress IP address of the device
     * @param uri       URI to be addressed
     * @param uri       The URI we are calling (CoIoT = /cit/d or /cit/s)
     * @param con       true: send as CON, false: send as NON
     * @return new packet
     * @throws IOException
     */
    private Request newRequest(String ipAddress, String uri, Type con) throws IOException {
        // We need to build our own Request to set an empty Token
        Request request = new Request(Code.GET, con);
        request.setURI(completeUrl(ipAddress, uri));
        request.setToken(EMPTY_BYTE);

        /*
         * OptionSet optionSet = new OptionSet();
         * optionSet.addOption(new Option(OptionNumberRegistry.OBSERVE, 1));
         * optionSet.addOption(new Option(COIOT_OPTION_GLOBAL_DEVID, "SHSW-1#25A73E#1")); // "SHSW-21#559F55#1"));
         * optionSet.addOption(new Option(COIOT_OPTION_STATUS_VALIDITY, 0x007));
         * optionSet.addOption(new Option(COIOT_OPTION_STATUS_SERIAL, serialCount++));
         * optionSet.setIfNoneMatch(false);
         * request.setOptions(optionSet);
         */

        request.addMessageObserver(new MessageObserverAdapter() {
            @Override
            public void onResponse(Response response) {
                processResponse(response);
            }

            @Override
            public void onCancel() {
            }

            @Override
            public void onTimeout() {
                logger.debug("COAP Request timeout");
            }

        });
        return request;

    }

    /**
     * Cancel pending requests and shutdown the client
     */
    public void stop() {
        if ((reqDescription != null) && !reqDescription.isCanceled()) {
            reqDescription.cancel();
            reqDescription = null;
        }
        if ((reqStatus != null) && !reqStatus.isCanceled()) {
            reqStatus.cancel();
            reqStatus = null;
        }
        if (statusClient != null) {
            statusClient.shutdown();
            statusClient = null;
        }
    }

    public void dispose() {
        stop();
    }

    private String completeUrl(String ipAddress, String uri) {
        return "coap://" + ipAddress + ":" + CoIoT_PORT + uri;

    }

    /*
     * Unused Callbacks
     */

    @Override
    public void receiveResponse(Response response) {
        logger.debug("receiveResponse()");
    }

    @Override
    public void receiveEmptyMessage(EmptyMessage message) {

    }

    @Override
    public void sendRequest(Request request) {

    }

    @Override
    public void sendResponse(Response response) {

    }

    @Override
    public void sendEmptyMessage(EmptyMessage message) {

    }

}
