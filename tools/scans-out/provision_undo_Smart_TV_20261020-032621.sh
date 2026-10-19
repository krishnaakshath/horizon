#!/bin/sh
# Undo for tvtool provision on 192.168.1.38. Run: sh provision_undo_Smart_TV_20261020-032621.sh  (or: python3 -m tvtool rollback --host 192.168.1.38 --script provision_undo_Smart_TV_20261020-032621.sh)
adb -s 192.168.1.38:5555 shell settings put secure accessibility_enabled 1
adb -s 192.168.1.38:5555 shell settings put secure enabled_accessibility_services dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher.store REQUEST_INSTALL_PACKAGES default
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher SYSTEM_ALERT_WINDOW allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher GET_USAGE_STATS default
adb -s 192.168.1.38:5555 shell pm revoke dev.tvlauncher android.permission.ACCESS_FINE_LOCATION
adb -s 192.168.1.38:5555 shell pm revoke dev.tvlauncher.helper android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.1.38:5555 shell pm revoke dev.tvlauncher android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher.helper APP_ASSOC_START allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher.helper APP_AUTO_START allow
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher APP_ASSOC_START default
adb -s 192.168.1.38:5555 shell appops set dev.tvlauncher APP_AUTO_START allow
