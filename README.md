# Shelly Binding (org.openhab.binding.shelly)

This openHAB 2 Binding implements control for the Shelly series of devices. This includes sending commands to the devices as well as reding the device status and sensor data.

Author: Markus Michels (markus7017)
Check  https://community.openhab.org/t/shelly-binding/56862/65 for more information, questions and contributing ideas. Any comment is welcome!

---

Release: alpha5, check master branch for stable release

Please note:
This is a beta release, it has bugs, requires manual install etc. Questions, feedback and contributions are welcome (e.g. improving this documentation).


Looking for contribution: If you are familar with HTML and CSS you are welcome to contribute a nice HABpanel widget. ;-)

---

## Supported Devices

## Supported Things

|Thing             |Type                                                    | Status                                                   |
|------------------|--------------------------------------------------------|----------------------------------------------------------|
| shelly1          | Shelly Single Relay Switch                             | fully supported                                          |
| shelly1pm        | Shelly Single Relay Switch with integrated Power Meter | fully supported                                          |
| shelly2-relay    | Shelly Double Relay Switch (Shelly2 and Shelly2.5)     | fully supported                                          |
| shelly2-roller   | Shelly2 in Roller Mode (Shelly2 and Shelly2.5)         | fully supported                                          |
| shellyht         | Shelly Sensor (temp+humidity)                          | fully supported, recovery handling needs to be optimized |
| shellyplug-s     | Shelly Plug                                            | fully supported                                          |
| shellybulb       | Shelly Bulb in Color or WHite Mode                     | work-in-progress, feedback open                          |
| shellyplug       | Shelly Plug                                            | should get discovered, but no special handling yet       |
| shelly4pro       | Shelly 4x Relay Switch                                 | should get discovered, but no special handling yet       |
| shellysmoke      | Shelly Sensor (temp+humidity)                          | should get discovered, but no special handling yet       |
| shellyrgbw2      | Shelly RGB Controller                                  | should get discovered, but no special handling yet       |
| shellysense      | Shelly Motion and IR Controller                        | should get discovered, but no special handling yet       |

Feedback is welcome anytime. Leave some comments in the forum.

Please let me know if you could support implementation and testing for Shelly RGBW2, 4 Pro, Bulb, Sense. 
Please send a PM to markus7017 If you encounter errors and include a TRACE log.


## Binding installation

### Firmware

To utilize all features the binding requires firmware version 1.5.0 or newer. This should be available for all devices.
Older versions work in general, but have impacts to functionality (e.g. no events for battery powered devices).

The binding displays a WARNING if the firmware is older. It also informs you when an update is available. Use the device's web ui or the Shelly App to perform the update.

###m Password protection

For now the binding doesn't support the http authentication. This is on the list. Please deactivate the authentication before discovering devices.


### Alpha/Beta versions

The binding is work in progress. You have to expect bugs etc. and each version might be incompatible to the existing thing defintion, which means no backware compatibility.

Channel definitions are subject to change with any alpha or beta release. Please make sure to **delete all Shelly things before updating*** the binding and clean out the JSON DB:
- delete all Shelly things from PaperUI's Inbox and Thing list
- stop OH
- run openhab-cli clean-cache
- check the JSON db files for shelly references, remove all entries
- copy the jar to the addons/ folder
- start OH, wait until everything is initialized
- run the device discovery

If you hit a problem make sure to post a TRACE log (or send PM) so I could look into the details.

### Instalation

As described above the binding will be installed by copying the jar into the addons folder of your OH installation. Once a stable state is reached the binding may become part of the openHAB 2.5 distribution, but this will take some time. The binding is developed an tested on OH version 2.4, but also runs on 2.5M1. Please post an info if you also verified compatibility to version 2.3. However, this release is not officially supported.


## Discovery

### General
The binding uses mDNS to discovery the Shelly devices. They periodically announce their presence, which can be used by the binding to find them on the local network and fetch their IP address. The binding will then use the Shelly http api to discover device capabilities, read status and control the device. In addition event callbacks will be used to support battery powered devices.

Important: The IP address shouldn't change after the device is added as a new thing in openHAB. This could be achieved by
- assigning a static IP address or
- use DHCP and setup the router to assign always the same ip address to the device
You need to re-discover the device if there is a reason why the ip address changed.

New devices could be discovered an added to the openHAB system by using Paper UI's Inbox. Running a manual discovery should show up all devices on your local network.

There seems to be an issue between OH mDNS implementation and Shelly so that initially the binding is not able to catch the thing’s ip address (in this case the event reports 0.0.0.0 as ip address - this will be ignored) or devices don’t show up all the time. To fix this you need to run the manual discovery multiple times until you see all your devices. Make sure to wakeup battery powered devices (press the button inside the device) so they show up on the network.

## Binding Configuration


### Thing Configuration

| Parameter      | Description                                                      |Mandantory| Default                                            |
|----------------|------------------------------------------------------------------|----------|----------------------------------------------------|
| deviceIp       | IP address of the Shelly device, usually auto-discovered         |    yes   | none                                               |
| updateInterval | Interval for the background status check in seconds.             |    no    | 1h for battery powered devices, 60s for all others |
| userId         | The userid used for http authentication*                         |    no    | none                                               |
| password       | Password for http authentication*                                |    no    | none                                               |

## Channels

### Shelly 1 (thing-type: shelly1)

|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|relay1    |output       |Switch   |r/w      |Controls the relay's output channel (on/off)                                     |
|          |overpower    |Switch   |yes      |ON: The relay detected an overpower condition, output was turned OFF             |
|          |trigger      |Trigger  |yes      |An OH trigger channel, see description below                                     |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                               |

### Shelly 2 - relay mode thing-type: shelly2-relay)

|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|relay1    |output       |Switch   |r/w      |Relay #1: Controls the relay's output channel (on/off)                           |
|          |overpower    |Switch   |yes      |Relay #1: ON: The relay detected an overpower condition, output was turned OFF   |
|          |trigger      |Trigger  |yes      |Relay #1: An OH trigger channel, see description below                           |
|relay2    |output       |Switch   |r/w      |Relay #2: Controls the relay's output channel (on/off)                           |
|          |overpower    |Switch   |yes      |Relay #2: ON: The relay detected an overpower condition, output was turned OFF   |
|          |trigger      |Trigger  |yes      |Relay #2: An OH trigger channel, see description below                           |
|meter1    |currentWatts |Number   |yes      |Current power consumption in Watts                                               |
|          |lastPower1   |Number   |yes      |Energy consumption in Watts for a round minute, 1 minute  ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 2 minutes ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 3 minutes ago                    |
|          |totalWatts   |Number   |yes      |Total energy consumption in Watts since the device powered up (reset on restart) |
|          |timestamp    |String   |yes      |Timestamp of the last measurement                                                |

### Shelly 2 - roller mode thing-type: shelly2-roller)

|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|roller1   |turn         |Switch   |r/w      |ON: Turn roller into open mode, OFF:  Turn roller into close mode                |
|          |position     |Number   |r/w      |Position of the roller, 0=open...100=closed; gets updated when the roller stopped|
|          |direction    |String   |yes      |Last direction: open or close                                                    |
|          |stopReason   |String   |yes      |Last stop reasons: normal, safety_switch or obstacle                             |
|          |calibrating  |Switch   |yes      |ON: Roller is in calibration mode, OFF: normal mode (no calibration)             |
|meter1    |currentWatts |Number   |yes      |Current power consumption in Watts                                               |
|          |lastPower1   |Number   |yes      |Energy consumption in Watts for a round minute, 1 minute  ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 2 minutes ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 3 minutes ago                    |
|          |totalWatts   |Number   |yes      |Total energy consumption in Watts since the device powered up (reset on restart) |
|          |timestamp    |String   |yes      |Timestamp of the last measurement                                                |

### Shelly 2.5 - relay mode (thing-type:shelly25-relay) 

The Shelly 2.5 includes 2 meters, one for each channel. Refer to Shelly 2 channel layout, the 2nd meter is represented by channel group "meter2" with the same channels like "meter1".

### Shelly 2.5 - roller mode (thing-type: shelly25-roller)

The Shelly 2.5 includes 2 meters, one for each channel. However, it doesn't make sense to differ power consumption for the roller moving up vs. moving down. For this the binding aggregates the power consumption of both relays and includes the values in "meter1". See channel description for Shelly 2 in roller mode.

### Shelly Plug-S (thing-type: shellyplug-s)

### Shelly HT (thing-type: shellyht)

### Shelly Bulb (thing-type: shellybulb)

### Shelly RGBW2 in Color Mode
|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|roller1   |turn         |Switch   |r/w      |ON: Turn roller into open mode, OFF:  Turn roller into close mode                |


### Other devices

The thing definiton fo the following devices is primarily. If you have one of those devices send a PM to marks7017 and we could work on the implementation/testing.
- thing-type: shellysmoke
- thing-type: shellysense
- thing-type: shellyplug
- thing-type: shelly4pro


## Full Example

Note: PaperUI is recommended, if you want to use text files make sure to replace the thing id from you channel definition 

* .things

* .items


* .sitemap

* .rules

* reading colors from color picker:
import org.openhab.core.library.types.*
 
var HSBType hsbValue
var int redValue
var int greenValue
var int blueValue
var String RGBvalues

rule "color" // The unique name for the rule
when
    Item ShellyColor changed
then
    var HSBType hsbValue = ShellyColor.state as HSBType
    var int redValue = hsbValue.red.intValue
    var int greenValue = hsbValue.green.intValue
    var int blueValue = hsbValue.blue.intValue
end


