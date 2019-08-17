
/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
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
