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
package org.openhab.binding.shelly.internal.config;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.lang.Validate;

/**
 * The {@link ShellyBindingConfiguration} class contains fields mapping binding configuration parameters.
 *
 * @author Markus Michels - Initial contribution
 */
public class ShellyBindingConfiguration {
    // Binding Configuration Properties
    public static final String CONFIG_DEF_HTTP_USER = "defaultUserId";
    public static final String CONFIG_DEF_HTTP_PWD  = "defaultPassword";
    public static final String CONFIG_COAP_PORT     = "coapPort";

    public String              defaultUserId        = "";      // default for http basic user id
    public String              defaultPassword      = "";      // default for http basic auth password

    public int                 coapPort             = 5684;   // default Coap port

    public void updateFromProperties(Map<String, Object> properties) {
        Validate.notNull(properties);

        Dictionary<String, Object> dict = new Hashtable<String, Object>();
        properties.forEach((k, v) -> dict.put(k, v));
        updateFromProperties(dict);
    }

    public void updateFromProperties(Dictionary<String, Object> properties) {
        Validate.notNull(properties);
        defaultUserId = getProperty(properties, CONFIG_DEF_HTTP_USER);
        defaultPassword = getProperty(properties, CONFIG_DEF_HTTP_PWD);
        String value = getProperty(properties, CONFIG_COAP_PORT);
        if (!value.isEmpty()) {
            coapPort = Integer.parseInt(value);
        }
    }

    private String getProperty(Dictionary<String, Object> properties, String key) {
        String s = (String) properties.get(key);
        return s != null ? s : "";
    }

}
