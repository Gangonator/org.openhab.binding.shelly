/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.shelly.internal;

import static org.openhab.binding.shelly.internal.ShellyBindingConstants.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.lang.Validate;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.net.NetworkAddressService;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.shelly.internal.config.ShellyBindingConfiguration;
import org.openhab.binding.shelly.internal.handler.ShellyDeviceListener;
import org.openhab.binding.shelly.internal.handler.ShellyHandler;
import org.openhab.binding.shelly.internal.handler.ShellyHandlerLight;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ShellyHandlerFactory} is responsible for creating things and thing handlers.
 *
 * @author Hans-Jörg Merk - Initial contribution
 * @author Markus Michels - refactored
 */
@Component(service = { ThingHandlerFactory.class, ShellyHandlerFactory.class }, immediate = true, configurationPid = "binding.shelly")
public class ShellyHandlerFactory extends BaseThingHandlerFactory {
    private final Logger                    logger                     = LoggerFactory.getLogger(ShellyHandlerFactory.class);

    private NetworkAddressService           networkAddressService;
    private final Set<ShellyDeviceListener> deviceListeners            = new CopyOnWriteArraySet<>();

    private static final Set<ThingTypeUID>  SUPPORTED_THING_TYPES_UIDS = ShellyBindingConstants.SUPPORTED_THING_TYPES_UIDS;
    private static boolean                  initialized                = false;
    ShellyBindingConfiguration              bindingConfig              = new ShellyBindingConfiguration();

    /**
     * Activate the bundle: save properties
     *
     * @param componentContext
     * @param configProperties set of properties from cfg (use same names as in
     *                         thing config)
     */
    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> configProperties) {
        super.activate(componentContext);
        logger.debug("Activate Shelly HandlerFactory");
        Validate.notNull(configProperties);
        bindingConfig.updateFromProperties(configProperties);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        if (!initialized) {
            Bundle bundle = this.getBundleContext().getBundle();
            Dictionary<String, String> d = bundle.getHeaders();
            Version v = bundle.getVersion();
            logger.info("{} Version {}.{}.{} ({}.jar, {})", d.get("Bundle-Name"), v.getMajor(), v.getMinor(), v.getMicro(),
                    bundle.getSymbolicName(), convertTimestamp(bundle.getLastModified() / 1000));
            initialized = true;
        }
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (thingTypeUID.getId().equals(THING_TYPE_SHELLYBULB.getId()) ||
                thingTypeUID.getId().equals(THING_TYPE_SHELLYRGBW2_COLOR.getId())
                || thingTypeUID.getId().equals(THING_TYPE_SHELLYRGBW2_WHITE.getId())) {
            logger.debug("Create new thing using ShellyHandlerLight");
            return new ShellyHandlerLight(thing, this, bindingConfig, networkAddressService);
        }
        if (SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)) {
            logger.debug("Create new thing using ShellyHandlerGeneric");
            return new ShellyHandler(thing, this, bindingConfig, networkAddressService);
        }

        return null;
    }

    public void onEvent(String deviceName, String deviceIndex, String eventType, Map<String, String[]> parameters, String data) {
        try {
            logger.trace("Dispatch event to device handler {}", deviceName);
            deviceListeners.forEach(listener -> listener.onEvent(deviceName, deviceIndex, eventType, parameters, data));
        } catch (RuntimeException e) {
            logger.warn("ERROR: Exception processing callback: {} ({}), deviceName={}, type={}, index={}, parameters={}, data='{}'",
                    e.getMessage(), e.getClass(), deviceName, eventType, deviceIndex, parameters.toString(), data);

        }
    }

    /**
     * Registers a listener, which is informed about device details.
     *
     * @param listener the listener to register
     */
    public void registerDeviceListener(ShellyDeviceListener listener) {
        this.deviceListeners.add(listener);
    }

    /**
     * Unregisters a given listener.
     *
     * @param listener the listener to unregister
     */
    public void unregisterDeviceListener(ShellyDeviceListener listener) {
        this.deviceListeners.remove(listener);
    }

    public static String convertTimestamp(Long timestamp) {
        Object o = timestamp;
        if (o == null) {
            return "";
        }

        Date date = new java.util.Date(timestamp * 1000L);
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        String result = sdf.format(date);
        return !result.contains("1970-01-01") ? result : "n/a";
    }

    public ShellyBindingConfiguration getBindingConfig() {
        return bindingConfig;
    }

    @Reference
    protected void setNetworkAddressService(NetworkAddressService networkAddressService) {
        this.networkAddressService = networkAddressService;
    }

    protected void unsetNetworkAddressService(NetworkAddressService networkAddressService) {
        this.networkAddressService = null;
    }

}
