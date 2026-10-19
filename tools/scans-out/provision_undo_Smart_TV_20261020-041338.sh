#!/bin/sh
# Undo for tvtool provision on 192.168.1.38. Run: sh provision_undo_Smart_TV_20261020-041338.sh  (or: python3 -m tvtool rollback --host 192.168.1.38 --script provision_undo_Smart_TV_20261020-041338.sh)
adb -s 192.168.1.38:5555 shell settings put secure accessibility_enabled 0
adb -s 192.168.1.38:5555 shell settings put secure enabled_accessibility_services dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher SYSTEM_ALERT_WINDOW allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher.helper APP_ASSOC_START allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher.helper APP_AUTO_START allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher APP_ASSOC_START allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher APP_AUTO_START allow
