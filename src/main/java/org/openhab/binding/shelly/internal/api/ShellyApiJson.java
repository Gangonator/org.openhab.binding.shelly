/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.openhab.binding.shelly.internal.api;

import java.util.ArrayList;

/**
 * Json/Gson mapping
 *
 * @author Markus Michels - Initial contribution
 *
 */
public class ShellyApiJson {

    public static final String SHELLY_API_ON  = "on";
    public static final String SHELLY_API_OFF = "off";

    public class ShellyCommonSettings {
        public Boolean reset; // Will perform a factory reset of the device
        public Boolean mqtt_enable; // Enable connecting to a MQTT broker
        public String  mqtt_server; // MQTT broker IP address and port, ex. 10.0.0.1:1883
        public String  mqtt_user; // MQTT username, leave empty to disable authentication
        public String  mqtt_pass; // MQTT password
        public Integer mqtt_reconnect_timeout_max; // maximum interval for reconnect attempts
        public Integer mqtt_reconnect_timeout_min; // minimum interval for reconnect attempts
        public Boolean mqtt_clean_session; // MQTT clean session flag
        public Integer mqtt_keep_alive; // MQTT keep alive period in seconds
        public Integer mqtt_max_qos; // Max value of QOS for MQTT packets
        public Boolean mqtt_retain; // MQTT retain flag
        public Integer mqtt_update_period; // Periodic update in seconds, 0 to disable
        public Boolean coiot_execute_enable; // Whether to allow execution of CoIoT commands
        public Boolean tzautodetect; // Set this to false if you want to set custom geolocation (lat and lng) or custom
                                     // timezone.
        public Double  lat; // Degrees latitude in decimal format, South is negative
        public Double  lng; // Degrees longitude in decimal format, between -180 and 180
        public String  timezone; // Timezone identifier, see https://api.shelly.cloud/timezone/tzlist
        public String  sntp_server; // Time server host to be used instead of the default time.google.com
    }

    public class ShellySettingsDevice {
        public String  type;
        public String  mac;
        public String  hostname;
        public String  fw;
        public Boolean auth;
        public Integer num_outputs;
        public Integer num_meters;
        public Integer num_rollers;
    }

    public class ShellySettingsWiFiAp {
        public Boolean enabled;
        public String  ssid;
        public String  key;
    }

    public class ShellySettingsWiFiNetwork {
        public Boolean      enabled;
        public String       ssid;

        public String       ipv4_method;
        public final String SHELLY_IPM_STATIC = "static";
        public final String SHELLY_IPM_DHCP   = "dhcp";

        public String       ip;
        public String       gw;
        public String       mask;
        public String       dns;
    }

    public class ShellySettingsMqtt {
        public Boolean enabled;
        public String  server;
        public String  user;
        public Double  reconnect_timeout_max;
        public Double  reconnect_timeout_min;
        public Boolean clean_session;
        public Integer keep_alive;
        public String  will_topic;
        public String  will_message;
        public Integer max_qos;
        public Boolean retain;
        public Integer update_period;
    }

    public class ShellySettingsSntp {
        public String server;
    }

    public class ShellySettingsLogin {
        public Boolean enabled;
        public Boolean unprotected;
        public String  username;
        public String  password;
    }

    public class ShellySettingsBuildInfo {
        public String build_id;
        public String build_timestamp;
        public String build_version;
    }

    public class ShellySettingsCloud {
        public Boolean enabled;
        public Boolean connected;
    }

    public class ShellySettingsHwInfo {
        public String  hw_revision;
        public Integer batch_id;
    }

    public class ShellySettingsScheduleRules {
    }

    public class ShellySettingsRelay {
        public String                          name;
        public Boolean                         ison;
        public Boolean                         has_timer;
        public Boolean                         overpower;
        public String                          default_state; // Accepted values: off, on, last, switch
        public String                          btn_type; // Accepted values: momentary, toggle, edge, detached - // see SHELLY_BTNT_xxx
        public Integer                         auto_on; // Automatic flip back timer, seconds. Will engage after turning Shelly1 OFF.
        public Integer                         auto_off; // Automatic flip back timer, seconds. Will engage after turning Shelly1 ON.
        public String                          btn_on_url; // URL to access when SW input is activated
        public String                          btn_off_url; // URL to access when SW input is deactivated
        public String                          out_on_url; // URL to access when output is activated
        public String                          out_off_url; // URL to access when output is deactivated
        public Boolean                         schedule;
        ArrayList<ShellySettingsScheduleRules> schedule_rules;
    }

    public class ShellySettingsRoller {
        public Double                          maxtime;
        public Double                          maxtime_open;
        public Double                          maxtime_close;
        public String                          default_state; // see SHELLY_STATE_xxx
        public Boolean                         swap;
        public Boolean                         swap_inputs;
        public String                          input_mode; // see SHELLY_INP_MODE_OPENCLOSE
        public String                          button_type; // // see SHELLY_BTNT_xxx
        public Integer                         btn_reverse;
        public String                          state;
        public Double                          power;
        public Boolean                         is_valid;
        public Boolean                         safety_switch;
        public Boolean                         schedule;
        ArrayList<ShellySettingsScheduleRules> schedule_rules;
        public String                          obstacle_mode; // SHELLY_OBSTMODE_
        public String                          obstacle_action; // see SHELLY_STATE_xxx
        public Integer                         obstacle_power;
        public Integer                         obstacle_delay;
        public String                          safety_mode; // see SHELLY_SAFETYM_xxx
        public String                          safety_action; // see SHELLY_STATE_xxx
        public String                          safety_allowed_on_trigger; // see SHELLY_ALWD_TRIGGER_xxx
        public Integer                         off_power;
        public Boolean                         positioning;
    }

    public class ShellySettingsMeter {
        public Double   power;
        public Boolean  is_valid;
        public Long     timestamp;
        public Double[] counters = { 0.0, 0.0, 0.0 };
        public Long     total;
    }

    public class ShellySettingsUpdate {
        public String  status;
        public Boolean has_update;
        public String  new_version;
        public String  old_version;
    }

    public class ShellySettingsGlobal {
        // https://shelly-api-docs.shelly.cloud/#shelly1pm-settings
        public ShellySettingsDevice           device;
        public ShellySettingsWiFiAp           wifi_ap;
        public ShellySettingsWiFiNetwork      wifi_sta;
        public ShellySettingsWiFiNetwork      wifi_sta1;
        public ShellySettingsMqtt             mqtt;
        public ShellySettingsSntp             sntp;
        public ShellySettingsLogin            login;
        public String                         pin_code;
        public Boolean                        coiot_execute_enable;
        public String                         name;
        public String                         fw;
        ShellySettingsBuildInfo               build_info;
        ShellySettingsCloud                   cloud;
        public String                         timezone;
        public Double                         lat;
        public Double                         lng;
        public Boolean                        tzautodetect;
        public String                         time;
        public ShellySettingsHwInfo           hwinfo;
        public String                         mode;
        public Integer                        max_power;
        public ArrayList<ShellySettingsRelay> relays;
        public Boolean                        led_status_disable; // PlugS only Disable LED indication for network status
        public Boolean                        led_power_disable;  // PlugS only Disable LED indication for network status

        public String                         reset; // Submitting a non-empty value will reset settings for the output to factory defaults.
    }

    public static final String SHELLY_MODE_RELAY  = "relay";  // Relay: relay mode
    public static final String SHELLY_MODE_ROLLER = "roller"; // Relay: roller mode
    public static final String SHELLY_MODE_COLOR  = "color";  // Bulb: color mode
    public static final String SHELLY_MODE_WHITE  = "white";  // Bulb: white mode

    public class ShellySettingsAttributes {
        public String device_type; // Device model identifier
        public String device_mac; // MAC address of the device in hexadecimal
        public String wifi_ap; // WiFi access poInteger configuration, see /settings/ap for details
        public String wifi_sta; // WiFi client configuration. See /settings/sta for details
        public String login; // credentials used for HTTP Basic authentication for the REST interface. If enabled is
                             // true clients must include an Authorization: Basic ... HTTP header with valid credentials
                             // when performing TP requests.
        public String name; // unique name of the device.
        public String fw; // current FW version
    }

    public class ShellySettingsStatus {
        public ShellySettingsWiFiNetwork wifi_sta;

        class mqtt {
            public Boolean connected;
        }

        public String                          time;
        public Integer                         serial;
        public Boolean                         has_update;
        public String                          mac;
        public ArrayList<ShellySettingsRelay>  relays;
        public ArrayList<ShellySettingsRoller> rollers;
        public ArrayList<ShellySettingsMeter>  meters;
        public ShellySettingsUpdate            update;
        public Long                            ram_total;
        public Long                            ram_free;
        public Long                            fs_size;
        public Long                            fs_free;
        public Long                            uptime;
    }

    public class ShellyControlRelay {
        // https://shelly-api-docs.shelly.cloud/#shelly1-1pm-settings-relay-0
        public Boolean is_valid;
        public Boolean has_timer; // Whether a timer is currently armed for this channel
        public Boolean overpower; // Shelly1PM only if maximum allowed power was exceeded

        public String  turn; // Accepted values are on and off. This will turn ON/OFF the respective output channel when request is sent .
        public Integer timer; // A one-shot flip-back timer in seconds.
    }

    public class ShellyControlRoller {
        public Long    roller_pos; // number Desired position in percent
        public Long    duration; // If specified, the motor will move for this period in seconds. If missing, the value of
                                 // maxtime in /settings/roller/N will be used.
        public String  state; // One of stop, open, close
        public Long    power; // Current power consumption in Watts
        public Boolean is_valid;// If the power meter functions properly
        public Boolean safety_switch; // Whether the safety input is currently triggered
        public Boolean overtemperature;
        public String  stop_reason; // Last cause for stopping: normal, safety_switch, obstacle
        public String  last_direction; // Last direction of motion, open or close
        public Boolean calibrating;
        public Boolean positioning; // true when calibration was performed
        public Long    current_pos; // current position 0..100, 100=open
    }

    public class ShellySettingsSensor {
        public String  temperature_units; // Either'C'or'F'
        public Double  temperature_threshold; // Temperature delta (in configured degree units) which triggers an update
        public Integer humidity_threshold; // RH delta in % which triggers an update
        public Integer sleep_mode_period; // Periodic update period in hours, between 1 and 24
        public String  report_url; // URL gets posted on updates with sensor data
    }

    public class ShellyStatusSensor {
        // https://shelly-api-docs.shelly.cloud/#h-amp-t-settings
        public class _tmp {
            public Double  value; // Temperature in configured unites
            public String  units; // 'C' or 'F'
            public Double  tC; // temperature in deg C
            public Double  tF; // temperature in deg F
            public Boolean is_valid; // whether the internal sensor is operating properly
        }

        public class _hum {
            public Double value; // relative humidity in %
        }

        public class _bat {
            public Double value; // estimated remaining battery capacity in %
            public Double voltage; // battery voltage
        };

        public _tmp     tmp;
        public _hum     hum;
        public _bat     bat;
        public String[] act_reasons; // list of reasons which woke up the device
    }

    public class ShellySettingsSmoke {
        public String  temperature_units; // Either 'C' or 'F'
        public Double  temperature_threshold; // Temperature delta (in configured degree units) which triggers an update
        public Integer sleep_mode_period; // Periodic update period in hours, between 1 and 24
    }

    public static final String SHELLY_TEMP_CELSIUS    = "C";
    public static final String SHELLY_TEMP_FAHRENHEIT = "F";

    public class ShellySettingsBulb {
        public Boolean ison; // Whether the bulb is on or off
        public Integer red; // red brightness, 0..255, applies in mode="color"
        public Integer green; // green brightness, 0..255, applies in mode="color"
        public Integer blue; // blue brightness, 0..255, applies in mode="color"
        public Integer white; // white brightness, 0..255, applies in mode="color"
        public Integer gain; // gain for all channels, 0..100, applies in mode="color"
        public Integer temp; // color temperature in K, 3000..6500, applies in mode="white"
        public Integer brightness; // brightness, 0..100, applies in mode="white"
        public Integer effect; // Currently applied effect, description: 0: Off, 1: Meteor Shower, 2: Gradual Change, 3: Breath,
                               // 4: Flash, 5: On/Off Gradual, 6: Red/Green Change
        public String  default_state; // one of on, off or last
        public Integer auto_on; // see above
        public Integer auto_off; // see above
    }

    public static final String SHELLY_BULB_TURN        = "turn";
    public static final String SHELLY_BULB_DEFSTATE    = "def_state";
    public static final String SHELLY_BULB_TIMER       = "timer";
    public static final String SHELLY_BULB_AUTOON      = "auto_on";
    public static final String SHELLY_BULB_AUTOOFF     = "aut_off";

    public static final String SHELLY_COLOR_RED        = "red";
    public static final String SHELLY_COLOR_BLUE       = "blue";
    public static final String SHELLY_COLOR_GREEN      = "green";
    public static final String SHELLY_COLOR_WHITE      = "white";
    public static final String SHELLY_COLOR_GAIN       = "gain";
    public static final String SHELLY_COLOR_BRIGHTNESS = "brightness";
    public static final String SHELLY_COLOR_TEMP       = "temp";
    public static final String SHELLY_COLOR_EFFECT     = "effect";

}
