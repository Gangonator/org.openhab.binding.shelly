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
import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.shelly.internal.ShellyHandlerFactory;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi;
import org.openhab.binding.shelly.internal.api.ShellyHttpApi.ShellyDeviceProfile;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.config.ShellyThingConfiguration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class identifies Shelly devices by their mDNS service information.
 *
 * @author Hans-Jörg Merk - Initial contribution
 */
@Component(service = MDNSDiscoveryParticipant.class, immediate = true)
public class ShellyDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private final Logger               logger         = LoggerFactory.getLogger(ShellyDiscoveryParticipant.class);
    private ShellyBindingConfiguration bindingConfig  = new ShellyBindingConfiguration();
    private ShellyHandlerFactory       handlerFactory = null;
    private ShellyHttpApi              api;

    private static final String        SERVICE_TYPE   = "_http._tcp.local.";

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    /**
     * Called at the service activation.
     *
     * @param componentContext
     */
    @Activate
    protected void activate(ComponentContext componentContext) {
        logger.debug("Discoverxy service activated");
        bindingConfig.updateFromProperties(componentContext.getProperties());
    }

    @Modified
    protected void modified(ComponentContext componentContext) {
        logger.info("Binding config refreshed");
        bindingConfig.updateFromProperties(componentContext.getProperties());
    }

    @Override
    public DiscoveryResult createResult(ServiceInfo service) {
        if ((service == null) || !service.getName().startsWith("shelly")) {
            return null;
        }

        String name = service.getName().toLowerCase();
        String address = StringUtils.substringBetween(service.toString(), "/", ":80");
        if (address == null) {
            logger.warn("Discovered Shelly device {} doesn't have an IP address, service-info={}", name, service);
            return null;
        }

        logger.info("Shelly device discovered: IP-Adress={}, name={}", address, name);
        try {
            ShellyThingConfiguration config = new ShellyThingConfiguration();
            if (handlerFactory != null) {
                bindingConfig = handlerFactory.getBindingConfig();
            }
            config.deviceIp = address;
            config.userId = bindingConfig.defaultUserId;
            config.password = bindingConfig.defaultPassword;

            // Get device settings
            ShellyHttpApi api = new ShellyHttpApi(config);
            String thingType = StringUtils.substringBeforeLast(name, "-");
            Map<String, Object> properties = new HashMap<>(5);
            properties.put(PROPERTY_VENDOR, "Shelly");
            properties.put(CONFIG_DEVICEIP, address);
            addProperty(properties, PROPERTY_SERVICE_NAME, service.getName());

            ShellyDeviceProfile profile = null;
            try {
                profile = api.getDeviceProfile(thingType);
            } catch (IOException e) {
                if (e.getMessage().contains("401 Unauthorized")) {
                    logger.warn("Device {} ({}) reported 'Access defined' (userid/password mismatch).", name, address);
                    logger.info("You could set a default userid and passowrd in the binding config and re-discover devices");
                    logger.info("or you need to disable device protection (userid/password) in the Shelly App for device discovery.");
                    logger.info("Once the device is discoverd you could set the userid/password and re-enable device protection in the Shelly App.");
                } else {
                    logger.warn("Device discovery failed for device {}, IP {}: {} ({})", name, address, e.getMessage(), e.getClass());
                }
            }
            Validate.notNull(profile, "Unable to get device profile: ");

            logger.debug("Shelly settings : {}", profile.settingsJson);
            logger.trace("name={}, thingType={}, mode={}", name, profile.thingType, profile.mode.isEmpty() ? "n/a" : profile.mode);
            properties.put(PROPERTY_MODEL_ID, profile.thingType);
            properties.put(PROPERTY_MAC_ADDRESS, profile.mac);
            properties.put(PROPERTY_FIRMWARE_VERSION, profile.fwVersion + "/" + profile.fwDate + "(" + profile.fwId + ")");
            addProperty(properties, PROPERTY_HWREV, profile.hwRev);
            addProperty(properties, PROPERTY_HWBATCH, profile.hwBatchId);
            addProperty(properties, PROPERTY_MODE, profile.mode);
            addProperty(properties, PROPERTY_HOSTNAME, profile.hostname);
            addProperty(properties, PROPERTY_NUM_RELAYS, profile.numRelays.toString());
            addProperty(properties, PROPERTY_NUM_ROLLERS, profile.numRollers.toString());
            addProperty(properties, PROPERTY_NUM_METER, profile.numMeters.toString());
            if (profile.settings.light_sensor != null) {
                addProperty(properties, PROPERTY_LIGHT_SENSOR, profile.settings.light_sensor);

            }

            ThingUID thingUID = this.getThingUID(name, profile.mode);
            logger.info("Adding Shelly thing, UID={}", thingUID.getAsString());
            return DiscoveryResultBuilder.create(thingUID).withProperties(properties).withLabel(service.getName())
                    .withRepresentationProperty(name).build();
        } catch (RuntimeException e) {
            logger.warn("Device discovery failed for device {}, IP {}, service={}: {} ({})", name, address, service.getName(), e.getMessage(),
                    e.getClass());
        }

        return null;
    }

    private void addProperty(Map<String, Object> properties, String key, String value) {
        properties.put(key, value != null ? value : "");
    }

    @Override
    public ThingUID getThingUID(@Nullable ServiceInfo service) {
        logger.info("ServiceInfo {}", service);
        @SuppressWarnings("null")
        String name = service.getName().toLowerCase();
        return getThingUID(name, "");
    }

    public ThingUID getThingUID(String name, String mode) {
        String devid = StringUtils.substringAfterLast(name, "-");

        if (name.startsWith("shelly1pm")) {
            return new ThingUID(THING_TYPE_SHELLY1PM, devid);
        }
        if (name.startsWith("shelly1")) {
            return new ThingUID(THING_TYPE_SHELLY1, devid);
        }
        if (name.startsWith("shellyswitch25")) { // Shelly v2.5
            if (mode.equals(SHELLY_MODE_RELAY)) {
                return new ThingUID(THING_TYPE_SHELLY25_RELAY, devid);
            }
            if (mode.equals(SHELLY_MODE_ROLLER)) {
                return new ThingUID(THING_TYPE_SHELLY25_ROLLER, devid);
            }
        }
        if (name.startsWith("shellyswitch")) { // Shelly v2
            if (mode.equals(SHELLY_MODE_RELAY)) {
                return new ThingUID(THING_TYPE_SHELLY2_RELAY, devid);
            }
            if (mode.equals(SHELLY_MODE_ROLLER)) {
                return new ThingUID(THING_TYPE_SHELLY2_ROLLER, devid);
            }
        }
        if (name.startsWith("shelly4pro")) {
            return new ThingUID(THING_TYPE_SHELLY4PRO, devid);
        }
        if (name.startsWith("shellyplug-s")) {
            return new ThingUID(THING_TYPE_SHELLYPLUGS, devid);
        }
        if (name.startsWith("shellyplug")) {
            return new ThingUID(THING_TYPE_SHELLYPLUG, devid);
        }
        if (name.startsWith("shellybulb")) {
            return new ThingUID(THING_TYPE_SHELLYBULB, devid);
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
            if (mode.equals(SHELLY_MODE_COLOR)) {
                return new ThingUID(THING_TYPE_SHELLYRGBW2_COLOR, devid);
            }
            if (mode.equals(SHELLY_MODE_WHITE)) {
                return new ThingUID(THING_TYPE_SHELLYRGBW2_WHITE, devid);
            }
        }

        logger.info("Unsupported Shelly Device discovered: {} (mode {})", name, mode);
        return null;

    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void setShellyHandlerFactory(ShellyHandlerFactory handlerFactory) {
        this.handlerFactory = handlerFactory;
        logger.debug("Discovery: HandlerFactory bound");
    }

    public void unsetShellyHandlerFactory(ShellyHandlerFactory handlerFactory) {
        this.handlerFactory = null;
        logger.debug("Discovery: HandlerFactory unbound");
    }
}
