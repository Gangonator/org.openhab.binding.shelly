
# Shelly Binding (org.openhab.binding.shelly)

This openHAB 2 Binding implements control for the Shelly series of devices. This includes sending commands to the devices as well as reding the device status and sensor data.

Author: Markus Michels (markus7017)
Check  https://community.openhab.org/t/shelly-binding/56862/65 for more information, questions and contributing ideas. Any comment is welcome!

---

## Releases of this binding

--- branches
Latest **snapshot** (work in progress): https://github.com/markus7017/org.openhab.binding.shelly/tree/snapshot
Latest **stable** release (master):     https://github.com/markus7017/org.openhab.binding.shelly/tree/master
**Previous** releases beta1:            https://github.com/markus7017/org.openhab.binding.shelly/tree/beta1

### 2.4.1 release notes (stable)
+ Roller: Shutter control channel is now "control", channel "turn"  are removed (use control instead) as well as and "calibration"
+ Roller new channel "rollerpos" has the position based on 100=open, 0=closed (vs. "control" with 0=open and 100=closed, e.g. for a vertical slider in HABpanel)
+ new thing config options to enable/disable setting of relay event urls
+ output binding version and build timestamp into the logfile
+ support for basic auth added (handled userid/password protected device)
+ config options on the binding and the thing level for basic http auth
+ ignore UP/DOWN command while roller is moving
* fix: process roller command STOP again
* fix: don't turn light back on after set color with OnOffType.OFF
* fix: recover HT when not available on initial thing initialization
* fix: various problems in the xml definition fixed (duplicate entries)

Please delete and re-discover all things!

### beta 1-pre2 release notes
+ full support of RGBW2 (color + white mode,  verified) - thanks @Igi
+ full support for Bulb (verified) - thanks @MHerbst
+ full support for Sense (verified) - thanks @MHerbst
+ full support for Shelly 4 Pro (verified)
+ new channel fullColor (preset for Red, Green, Blue, Yellow, White or RGB value string)
+ Settings refresh every minute to catch changes by the Web UI or the App
+ refresh poller position for 30s to catch the new status when the roller stops moving
+ trigger channels for relay and HT events
+ channel calibrating gets filled, roller positioning will be blocked when calibration is active
+ compute total power consumption in kw/h
+ decimal numbers in the channel defintion for Watts etc.
* adapt roller psotion: In OH it's 0..100 for Shelly 100..0 
* fixed power meters for all devices
* fixed refresh handling in several situations
* fixed flipping switches (turn on in Paper UI, immediately flips back)

---

Before installing this as an update:
- delete all Shelly things in PaperUI: Inbox and Configuration.Things
- stop OH
- make sure the JSON DB files have no left-overs on Shelly definitions, delete the entries
- run "openhab-cli clean-cache"
- maybe empty your log

general:
- copy the jar into you OH's Inbox folder.
- start OH, wait until initialized
- run the thing discovery from the Inbox

Check the README, it's not yet complete, but already describes most of the channels.
As always: feedback welcome

---

Contibutors:
@Igi:     lot of testing around RGBW2 and in general!
@mherbst: supported Bulb and Sense testing
@hmerck:  some initial work

Thanks guys supporting the community.

---

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
| shellyht         | Shelly Sensor (temp+humidity)                          | needs to be verified                                     |
| shellyplug-s     | Shelly Plug                                            | fully supported                                          |
| shellyplug       | Shelly Plug                                            | fully supported                                          |
| shellyrgbw2      | Shelly RGB Controller                                  | fully supported                                          |
| shellybulb       | Shelly Bulb in Color or WHite Mode                     | fully supported                                          |
| shellysense      | Shelly Motion and IR Controller                        | fully supported                                          |
| shelly4pro       | Shelly 4x Relay Switch                                 | fully supported                                          |
| shellysmoke      | Shelly Sensor (temp+humidity)                          | should get discovered, but no special handling yet       |

Feedback is welcome any time. Leave some comments in the forum.

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

The binding uses mDNS to discovery the Shelly devices. They periodically announce their presence, which can be used by the binding to find them on the local network and fetch their IP address. The binding will then use the Shelly http api to discover device capabilities, read status and control the device. In addition event callbacks will be used to support battery powered devices.

If you are using password protected Shelly devices you need to configure userid and password. You could configure these settings in two ways
1. Global Default: Go to PaperUI:Configuration:Addons:Shelly Binding and edit the configuration. Those will be used when now settings are given on the thing level.
2. Edit the thing configuration. 

Important: The IP address shouldn't change after the device is added as a new thing in openHAB. This could be achieved by
- assigning a static IP address or
- use DHCP and setup the router to assign always the same ip address to the device
You need to re-discover the device if there is a reason why the ip address changed.

New devices could be discovered an added to the openHAB system by using Paper UI's Inbox. Running a manual discovery should show up all devices on your local network. 

There seems to be an issue between OH mDNS implementation and Shelly so that initially the binding is not able to catch the thing’s ip address (in this case the event reports 0.0.0.0 as ip address - this will be ignored) or devices don’t show up all the time. To fix this you need to run the manual discovery multiple times until you see all your devices. Make sure to wakeup battery powered devices (press the button inside the device) so they show up on the network.

## Binding Configuration

The binding has some global configuration options. Go to PaperUI:Configuration:Addons:Shelly Binding to edit those.

| Parameter      |Description                                                    |Mandantory|Default                                         |
|----------------|---------------------------------------------------------------|----------|------------------------------------------------|
| defaultUserId  |Default userid for http authentication when not set in thing   |    no    |admin                                           |
| defaultPassword|Default password for http authentication when not set in thing |    no    |adnub                                           |


### Thing Configuration

| Parameter      |Description                                                 |Mandantory|Default                                            |
|----------------|------------------------------------------------------------|----------|---------------------------------------------------|
| deviceIp       |IP address of the Shelly device, usually auto-discovered    |    yes   |none                                               |
| userId         |The userid used for http authentication*                    |    no    |none                                               |
| password       |Password for http authentication*                           |    no    |none                                               |
| lowBattery     |Threshold for battery level. Set alert when level is below. |    no    |20 (=20%), only for battery powered devices        |
| updateInterval |Interval for the background status check in seconds.        |    no    |1h for battery powered devices, 60s for all others |

## Channels

### Shelly 1 (thing-type: shelly1)

|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|relay     |output       |Switch   |r/w      |Controls the relay's output channel (on/off)                                     |
|          |overpower    |Switch   |yes      |ON: The relay detected an overpower condition, output was turned OFF             |
|          |event        |Trigger  |yes      |Relay #1: Triggers an event when posted by the device                            |
|          |             |         |         |          the payload includes the event type and value as a J                   |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                               |

### Shelly 2 - relay mode thing-type: shelly2-relay)

|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|relay1    |output       |Switch   |r/w      |Relay #1: Controls the relay's output channel (on/off)                           |
|          |overpower    |Switch   |yes      |Relay #1: ON: The relay detected an overpower condition, output was turned OFF   |
|          |autoOn       |Number   |r/w      |Relay #1: Sets a  timer to turn the device ON after every OFF command; in seconds|
|          |autoOff      |Number   |r/w      |Relay #1: Sets a  timer to turn the device OFF after every ON command; in seconds|
|          |timerActive  |Switch   |yes      |Relay #1: ON: An auto-on/off timer is active                                     |
|          |event        |Trigger  |yes      |Relay #1: Triggers an event when posted by the device                            |
|          |             |         |         |          the payload includes the event type and value as a J                   |
|relay2    |output       |Switch   |r/w      |Relay #2: Controls the relay's output channel (on/off)                           |
|          |overpower    |Switch   |yes      |Relay #2: ON: The relay detected an overpower condition, output was turned OFF   |
|          |trigger      |Trigger  |yes      |Relay #2: An OH trigger channel, see description below                           |
|          |autoOn       |Number   |r/w      |Relay #2: Sets a  timer to turn the device ON after every OFF command; in seconds|
|          |autoOff      |Number   |r/w      |Relay #2: Sets a  timer to turn the device OFF after every ON command; in seconds|
|          |timerActive  |Switch   |yes      |Relay #2: ON: An auto-on/off timer is active                                     |
|          |event        |Trigger  |yes      |Relay #2: Triggers an event when posted by the device                            |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                               |
|          |lastPower1   |Number   |yes      |Energy consumption in Watts for a round minute, 1 minute  ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 2 minutes ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 3 minutes ago                    |
|          |totalWatts   |Number   |yes      |Total energy consumption in Watts since the device powered up (reset on restart) |
|          |timestamp    |String   |yes      |Timestamp of the last measurement                                                |

### Shelly 2 - roller mode thing-type: shelly2-roller)

|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|roller    |control      |String   |r/w      |can be open (0%), stop, or close (100%); could also handle ON (open) and OFF (close) |
|          |rollerpos     |Number   |r/w     |Roller position: 100=open...0=closed; gets updated when the roller stopped       {
|          |direction    |String   |yes      |Last direction: open or close                                                    |
|          |stopReason   |String   |yes      |Last stop reasons: normal, safety_switch or obstacle                             |
|          |calibrating  |Switch   |yes      |ON: Roller is in calibration mode, OFF: normal mode (no calibration)             |
|          |event        |Trigger  |yes      |Relay #1: Triggers an event when posted by the device, e,g, btn_up or btn_down   |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                               |
|          |lastPower1   |Number   |yes      |Energy consumption in Watts for a round minute, 1 minute  ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 2 minutes ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 3 minutes ago                    |
|          |totalWatts   |Number   |yes      |Total energy consumption in Watts since the device powered up (reset on restart) |
|          |timestamp    |String   |yes      |Timestamp of the last measurement                                                |

### Shelly 2.5 - relay mode (thing-type:shelly25-relay) 

The Shelly 2.5 includes 2 meters, one for each channel. Refer to Shelly 2 channel layout, the 2nd meter is represented by channel group "meter2" with the same channels like "meter1".

### Shelly 2.5 - roller mode (thing-type: shelly25-roller)

The Shelly 2.5 includes 2 meters, one for each channel. However, it doesn't make sense to differ power consumption for the roller moving up vs. moving down. For this the binding aggregates the power consumption of both relays and includes the values in "meter1". See channel description for Shelly 2 in roller mode.

### Shelly4 Pro
|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|relay1    |             |         |         |See group relay1 for Shelly 2                                                    |
|relay2    |             |         |         |See group relay1 for Shelly 2                                                    |
|relay3    |             |         |         |See group relay1 for Shelly 2                                                    |
|relay4    |             |         |         |See group relay1 for Shelly 2                                                    |
|meter1    |             |         |         |See group meter1 for Shelly 2                                                    |
|meter2    |             |         |         |See group meter1 for Shelly 2                                                    |
|meter3    |             |         |         |See group meter1 for Shelly 2                                                    |
|meter4    |             |         |         |See group meter1 for Shelly 2                                                    |

### Shelly Plug-S (thing-type: shellyplug-s)
|Group     |Channel      |Type     |read-only|Desciption                                                                       |
|----------|-------------|---------|---------|---------------------------------------------------------------------------------|
|relay     |output       |Switch   |r/w      |Relay #1: Controls the relay's output channel (on/off)                           |
|          |overpower    |Switch   |yes      |Relay #1: ON: The relay detected an overpower condition, output was turned OFF   |
|          |autoOn       |Number   |r/w      |Relay #1: Sets a  timer to turn the device ON after every OFF command; in seconds|
|          |autoOff      |Number   |r/w      |Relay #1: Sets a  timer to turn the device OFF after every ON command; in seconds|
|          |timerActive  |Switch   |yes      |Relay #1: ON: An auto-on/off timer is active                                     |
|          |event        |Trigger  |yes      |Relay #1: Triggers an event when posted by the device                            |
|          |             |         |         |          the payload includes the event type and value as a J                   |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                               |
|          |lastPower1   |Number   |yes      |Energy consumption in Watts for a round minute, 1 minute  ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 2 minutes ago                    |
|          |lastPower2   |Number   |yes      |Energy consumption in Watts for a round minute, 3 minutes ago                    |
|          |totalWatts   |Number   |yes      |Total energy consumption in Watts since the device powered up (reset on restart) |
|          |timestamp    |String   |yes      |Timestamp of the last measurement                                                |
|led       |statusLed    |Switch   |r/w      |ON: Status LED is disabled, OFF: LED enabled                                     |
|          |powerLed     |Switch   |r/w      |ON: Power LED is disabled, OFF: LED enabled                                      |

### Shelly HT (thing-type: shellyht)
|Group     |Channel      |Type     |read-only|Desciption                                                             |
|----------|-------------|---------|---------|-----------------------------------------------------------------------|
|sensors   |temperature  |Number   |yes      |Temperature, unit is reported by tempUnit                              |
|          |tempUnit     |Number   |yes      |Unit for temperature value: C for Celsius or F for Fahrenheit          |
|          |humidity     |Number   |yes      |Relative humidity in %                                                 |
|battery   |batteryLevel |Number   |yes      |Battery Level in %                                                     |
|          |batteryAlert |Switch   |yes      |Low battery alert                                                      |
|          |batteryVoltage|Switch  |yes      |Voltage of the battery                                                 |


### Shelly Bulb (thing-type: shellybulb)
|Group     |Channel      |Type     |read-only|Desciption                                                             |
|----------|-------------|---------|---------|-----------------------------------------------------------------------|
|control   |power        |Switch   |r/w      |Switch light ON/OFF                                                    |
|          |mode         |Switch   |r/w      |Color mode: color or white                                             |
|          |autoOn       |Number   |r/w      |Sets a  timer to turn the device ON after every OFF; in sec            |
|          |autoOff      |Number   |r/w      |Sets a  timer to turn the device OFF after every ON: in sec            |
|          |timerActive  |Switch   |yes      |ON: An auto-on/off timer is active                                     |
|color     |             |         |         |Color settings: only valid in COLOR mode                               |
|          |hsb          |HSB      |r/w      |Represents the color picker (HSBType), control r/g/b, bight not white  |
|          |fullColor    |String   |r/w      |Set Red / Green / Blue / Yellow / White mode and switch mode           |
|          |             |         |r/w      |Valid settings: "red", "green", "blue", "yellow", "white" or "r,g,b,w" | 
|          |red          |Dimmer   |r/w      |Red brightness: 0..100% or 0..255 (control only the red channel)       |
|          |green        |Dimmer   |r/w      |Green brightness: 0..100% or 0..255 (control only the red channel)     |
|          |blue         |Dimmer   |r/w      |Blue brightness: 0..100% or 0..255 (control only the red channel)      |
|          |white        |Dimmer   |r/w      |White brightness: 0..100% or 0..255 (control only the red channel)     |
|          |gain         |Dimmer   |r/w      |Gain setting: 0..100%     or 0..100                                    |
|          |effect       |Number   |r/w      |Puts the light into effect mode: 0..6)                                 |
|          |             |         |         |  0=No effect, 1=Meteor Shows, 2=Gradual Change, 3=Breath              |
|          |             |         |         |  4=Flash, 5=On/Off Gradual, 6=Red/Green Change                        |
|white     |             |         |         |Color settings: only valid in WHITE mode                               |
|          |temperature  |Dimmer   |         |Color temperature: 0..100% for 3000..6500K                             |
|          |brightness   |Dimmer   |         |Brightness: 0..100% or 0..100                                          |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                     |
 


### Shelly RGBW2 in Color Mode
|Group     |Channel      |Type     |read-only|Desciption                                                             |
|----------|-------------|---------|---------|-----------------------------------------------------------------------|
|control   |power        |Switch   |r/w      |Switch light ON/OFF                                                    |
|          |autoOn       |Number   |r/w      |Sets a  timer to turn the device ON after every OFF command; in seconds|
|          |autoOff      |Number   |r/w      |Sets a  timer to turn the device OFF after every ON command; in seconds|
|          |timerActive  |Switch   |yes      |ON: An auto-on/off timer is active                                     |
|light     |color        |Color    |r/w      |Color picker (HSBType)                                                 |
|          |fullColor    |String   |r/w      |Set Red / Green / Blue / Yellow / White mode and switch mode           | 
|          |             |         |r/w      |Valid settings: "red", "green", "blue", "yellow", "white" or "r,g,b,w" | 
|          |effect       |Number   |r/w      |Select a special effect                                                | 
|          |red          |Number   |r/w      |red brightness 0..255, use this only when not using the color picker   |
|          |green        |Number   |r/w      |green brightness 0..255, use this only when not using the color picker |
|          |blue         |Number   |r/w      |blue brightness 0..255, use this only when not using the color picker  |
|          |white        |Number   |r/w      |white brightness 0..255, use this only when not using the color picker |
|          |gain         |Number   |r/w      |gain 0..255, use this only when not using the color picker             |
|          |temperature  |Number   |r/w      |color temperature (K): 3000..6500                                      |
|meter     |currentWatts |Number   |yes      |Current power consumption in Watts                                     |

### Shelly RGBW2 in White Mode
|control   |autoOn       |Number   |r/w      |Sets a  timer to turn the device ON after every OFF command; in seconds|
|          |autoOff      |Number   |r/w      |Sets a  timer to turn the device OFF after every ON command; in seconds|
|          |timerActive  |Switch   |yes      |ON: An auto-on/off timer is active                                     |
|channel1  |power        |Switch   |r/w      |Channel 1: Turn channel on/off                                         |
|          |brightness   |Number   |r/w      |Channel 1: Brightness: 0..100                                          |
|channel2  |power        |Switch   |r/w      |Channel 2: Turn channel on/off                                         |
|          |brightness   |Number   |r/w      |Channel 2: Brightness: 0..100                                          |
|channel3  |power        |Switch   |r/w      |Channel 3: Turn channel on/off                                         |
|          |brightness   |Number   |r/w      |Channel 3: Brightness: 0..100                                          |
|channel4  |power        |Switch   |r/w      |Channel 4: Turn channel on/off                                         |
|          |brightness   |Number   |r/w      |Channel 4: Brightness: 0..100                                          |
|meter1    |currentWatts |Number   |yes      |Channel 1: Current power consumption in Watts                          |
|meter2    |currentWatts |Number   |yes      |Channel 2: Current power consumption in Watts                          |
|meter3    |currentWatts |Number   |yes      |Channel 3: Current power consumption in Watts                          |
|meter4    |currentWatts |Number   |yes      |Channel 4: Current power consumption in Watts                          |

Please note that the settings of channel group color are only valid in color mode and vice versa for white mode.
The current firmware doesn't support the timestamp report for the meters. In thise case "n/a" is returned. Maybe an upcoming firmware release adds this attribute, then the correct value is returned;


### Shelly Sense
|Group     |Channel      |Type     |read-only|Desciption                                                             |
|----------|-------------|---------|---------|-----------------------------------------------------------------------|
|control   |key          |String   |r/w      |Send a IR key to the sense. There a 3 different types supported        |
|          |             |         |         |Stored key: send the key code defined by the App , e.g. 123_1_up       |
|          |             |         |         |Pronto hex: send a Pronto Code in ex format, e.g. 0000 006C 0022 ...   |
|          |             |         |         |Pronto base64: in base64 format, will be send 1:1 to the Sense         |
|          |motionTime   |Number   |r/w      |Define the number of seconds when the Sense should report motion       |
|          |motionLED    |Switch   |r/w      |Control the motion LED: ON when motion is detected or OFF              |
|          |charger      |Switch   |yes      |ON: charger connected, OFF: charger not connected.                     |
|sensors   |temperature  |Number   |yes      |Temperature, unit is reported by tempUnit                              |
|          |tempUnit     |Number   |yes      |Unit for temperature value: C for Celsius or F for Fahrenheit          |
|          |humidity     |Number   |yes      |Relative humidity in %                                                 |
|          |lux          |Number   |yes      |Brightness in Lux                                                      |
|          |motion       |Switch   |yes      |ON: Motion detected, OFF: No motion (check also motionTimer)           |
|battery   |batteryLevel |Number   |yes      |Battery Level in %                                                     |
|          |batteryAlert |Switch   |yes      |Low battery alert                                                      |



### Other devices

The thing definiton fo the following devices is primarily. If you have one of those devices send a PM to marks7017 and we could work on the implementation/testing.
- thing-type: shellysmoke
- thing-type: shellysense
- thing-type: shellyplug


## Full Example

Note: PaperUI is recommended, if you want to use text files make sure to replace the thing id from you channel definition 

* .things

* .items


* .sitemap

* .rules

reading colors from color picker:
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




