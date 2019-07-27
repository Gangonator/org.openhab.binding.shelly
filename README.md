# Shelly binding for openHAB V2

This binding implements control and monitoring for the Shelly serries of devices.

The binding is still under development. If you want to become a beta tester switch to the lastest branch.
Status: Most devices are supported, but new versions could still change the channel structure, so I keep the build in alpha mode.

Current build: **alpha 6**

- new: support for Shelly1-PM (verified)
- new: Bulb+RGBW2 gets discovered and initialized, still working on control and status updates
- new: auto-on/off timers are supported for relays
- new: property wifiRssi, indicates the WiFi signal strength, maxPower when reported by device
- new: a event callback will auto-initialize the device when it's could not be initialized so far, this is useful for battery powered devices, which were offline when OH starts
- new: refresh device settings after a channel command, start caching channel data after 10s
- fix: NPE on bulb discovery fixed
- fix: Event callbacks might cause a NPE depending on the URL parameters
- fix: clear channel data on (re-)initialization
- change: check all API response data to be filled avoiding NPEs, more strict type checking
- change: thing status updates for Bulb refactored
- change: use system's pre-define channel definitions where applicable

Bulb and RGBW2 integration are still work-in-progress, @igi and @mherbstare helping.
No status on Smoke - nobody seems to have one. Sense is not yet started.

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
