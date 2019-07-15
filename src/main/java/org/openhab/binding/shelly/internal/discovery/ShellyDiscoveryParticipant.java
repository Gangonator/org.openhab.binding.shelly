/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal.discovery;

import static org.eclipse.smarthome.core.thing.Thing.*;
import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;
import static org.openhab.binding.shelly.internal.api.ShellyApiJson.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.shelly.internal.api.ShellyApiJson.ShellySettingsGlobal;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.config.ShellyConfiguration;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class identifies Shelly devices by their mDNS service information.
 *
 * @author Hans-Jörg Merk - Initial contribution
 */
@Component(service = MDNSDiscoveryParticipant.class, immediate = true)
public class ShellyDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private final Logger        logger       = LoggerFactory.getLogger(ShellyDiscoveryParticipant.class);
    private ShellyHttpApi       api;

    private static final String SERVICE_TYPE = "_http._tcp.local.";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo service) {
        if ((service == null) || !service.getName().startsWith("shelly")) {
            return null;
        }

        String name = service.getName().toLowerCase();
        String address = StringUtils.substringBetween(service.toString(), "/", ":80");
        if (address != null) {
            logger.info("Shelly device discovered: IP-Adress={}, name={}", address, name);
            try {
                ShellyConfiguration config = new ShellyConfiguration();
                config.deviceIp = address;
                ShellyHttpApi api = new ShellyHttpApi(config);

                // Get device settings
                ShellySettingsGlobal settings = api.getSettings();

                Map<String, Object> properties = new HashMap<>(5);
                properties.put(PROPERTY_VENDOR, "Shelly");
                properties.put(PROPERTY_MODEL_ID, settings.device.type);
                properties.put(PROPERTY_MAC_ADDRESS, settings.device.mac);
                properties.put(PROPERTY_FIRMWARE_VERSION, settings.device.fw);
                properties.put(CONFIG_DEVICEIP, address);
                addProperty(properties, "name", name);
                addProperty(properties, "hostname", settings.device.hostname);
                addProperty(properties, "mode", settings.mode);
                addProperty(properties, "numRollers", settings.device.num_rollers.toString());
                addProperty(properties, "numMeters", settings.device.num_meters.toString());
                addProperty(properties, "numOutputs", settings.device.num_outputs.toString());

                ThingUID thingUID = getThingUID(name, settings.mode.toLowerCase());
                return DiscoveryResultBuilder.create(thingUID).withProperties(properties).withLabel(service.getName())
                        .withRepresentationProperty(name).build();
            } catch (RuntimeException | IOException e) {
                logger.error("Device discovery failed: {}", e.getMessage());
            }

        } else {
            logger.warn("Discovered Shelly device doesn't have an IP address: {}", service);
        }
        return null;

    }

    private void addProperty(Map<String, Object> properties, String key, String value) {
        properties.put(key, value != null ? value : "");
    }

    @Override
    public ThingUID getThingUID(@Nullable ServiceInfo service) {
        logger.info("ServiceInfo {}", service);
        String name = service.getName().toLowerCase();
        return getThingUID(name, null);
    }

    public ThingUID getThingUID(String name, String mode) {
        String devid = StringUtils.substringAfter(name, "-");

        if (name.startsWith("shelly1pm")) {
            return new ThingUID(THING_TYPE_SHELLY1, devid);
        }
        if (name.startsWith("shelly1")) {
            return new ThingUID(THING_TYPE_SHELLY1, devid);
        }
        if (name.startsWith("shellyswitch") || // Shelly v2
                name.startsWith("shellyswitch25")) { // Shelly v2.5
            if (mode.equals(SHELLY_MODE_RELAY)) {
                return new ThingUID(THING_TYPE_SHELLY2_RELAY, devid);
            }
            if (mode.equals(SHELLY_MODE_ROLLER)) {
                return new ThingUID(THING_TYPE_SHELLY2_ROLLER, devid);
            }
        }
        if (name.startsWith("shellys4pro")) {
            return new ThingUID(THING_TYPE_SHELLY4PRO, devid);
        }
        if (name.startsWith("shellyplug-s")) {
            return new ThingUID(THING_TYPE_SHELLYPLUG, devid);
        }
        if (name.startsWith("shellyplug")) {
            return new ThingUID(THING_TYPE_SHELLYPLUG, devid);
        }
        if (name.startsWith("shellybulb")) {
            if (mode.equals(SHELLY_MODE_COLOR)) {
                return new ThingUID(THING_TYPE_SHELLYBULB_COLOR, devid);
            }
            if (mode.equals(SHELLY_MODE_WHITE)) {
                return new ThingUID(THING_TYPE_SHELLYBULB_WHITE, devid);
            }
        }
        if (name.startsWith("shellysense")) {
            return new ThingUID(THING_TYPE_SHELLYSENSE, devid);
        }
        if (name.startsWith("shellyht")) {
            return new ThingUID(THING_TYPE_SHELLYHT, devid);
        }
        if (name.startsWith("shellysmoke")) {
            return new ThingUID(THING_TYPE_SHELLYSMOKE, devid);
        }
        if (name.startsWith("shellyrgbw2")) {
            return new ThingUID(THING_TYPE_SHELLYRGW2, devid);
        }

        logger.info("Unsupported Shelly Device discovered: {}", name);
        return null;

    }
}
