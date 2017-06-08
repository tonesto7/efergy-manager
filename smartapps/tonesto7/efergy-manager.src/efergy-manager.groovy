/*
********************************************************************************************|
|    Application Name: Efergy Manager 3.0                                                   |
|    Author: Anthony S. (@tonesto7)                                                         |
|    Copyright 2016 Anthony S.                                                              |
|                                                                                           |
|  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file|
|  except in compliance with the License. You may obtain a copy of the License at:           |
|                                                                                           |
|      http://www.apache.org/licenses/LICENSE-2.0                                           |
|                                                                                           |
|  Unless required by applicable law or agreed to in writing, software distributed under    |
|  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY  |
|  KIND, either express or implied. See the License for the specific language governing     |
|  permissions and limitations under the License.                                           |
|                                                                                           |
|                                                                                           |
*********************************************************************************************
*/

import groovy.time.*
import java.text.SimpleDateFormat
import org.joda.time.DateTime;

definition(
	name: "${textAppName()}",
	namespace: "${textNamespace()}",
	author: "${textAuthor()}",
	description: "${textDesc()}",
	category: "Convenience",
	iconUrl:   "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/images/efergy_128.png",
	iconX2Url: "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/images/efergy_256.png",
	iconX3Url: "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/images/efergy_512.png",
	singleInstance: false,
	oauth: true)

{
	appSetting "wattVision_api_id"
	appSetting "wattVision_api_key"
	appSetting "wattVision_sensor_id"
}
/* THINGS TO-DO..........
	Add offline Hub handling to verify that the hub is online instead of generating errors.
*/

def appVersion() { "3.2.0" }
def appVerDate() { "6-8-2017" }
def appVerInfo() {
	def str = ""
	str += "V3.2.0 (June 8th, 2017):"
	str += "\n▔▔▔▔▔▔▔▔▔▔▔"
	str += "\n • Updated: Rebuilt data collection from Efergy"
    str += "\n • Added: Send your energy and power data to WattVision"
	str += "\n • Added: Lot's of Bug Fixes and Cleanups"
	str += "\n • Added: Notifications are now available for missed polls and app/device updates"

	str += "V3.1.0 (November 1st, 2016):"
	str += "\n▔▔▔▔▔▔▔▔▔▔▔"
	str += "\n • Fixed: Rebuilt graph data structure so prevent the confusing overlaying colors"
    str += "\n • Fixed: Graph resetting day based on UTC time"
	str += "\n • Added: Power Tiles for Min, Avg, Max"
	str += "\n • Added: Started works on collecting more data for day, week, month, year (This will be visualized soon)"

    str += "\n\nV3.0.2 (October 11th, 2016):"
	str += "\n▔▔▔▔▔▔▔▔▔▔▔"
	str += "\n • Fixed issue #4 where manager kept opening to login screen"
    str += "\n • Fixed graph overlay issue #3 and hopefully fixed #1 with the graph not displaying"

	str += "\n\nV3.0.1 (August 30th, 2016):"
	str += "\n▔▔▔▔▔▔▔▔▔▔▔"
	str += "\n • Alpha re-write."

	return str
}

include 'asynchttp_v1'

preferences {
	page(name: "startPage")
	page(name: "loginPage")
	page(name: "mainPage")
	page(name: "prefsPage")
	page(name: "debugPrefPage")
	page(name: "notifPrefPage")
	page(name: "setNotificationTimePage")
	page(name: "uninstallPage")
	page(name: "hubInfoPage")
	page(name: "readingInfoPage")
	page(name: "infoPage")
	page(name: "changeLogPage")
	page(name: "savePage")
}

def startPage() {
	if (atomicState.appInstalled == null) { atomicState.appInstalled = false }
	if (atomicState?.cleanupComplete == null) { atomicState?.cleanupComplete = false }
	if (atomicState?.pushTested == null) { atomicState.pushTested = false }
	if (atomicState?.currencySym == null) { atomicState.currencySym = "\$" }
	if (location?.timeZone?.ID.contains("America/")) { atomicState.currencySym = "\$" }

    if (atomicState?.efergyAuthToken != null) { return mainPage() }
	else { return loginPage() }
}

/* Efergy Login Page */
def loginPage() {
	if(atomicState?.efergyAuthToken != null) { return mainPage() }
	else {
		return dynamicPage(name: "loginPage", nextPage: mainPage, uninstall: false, install: false) {
			section("") {
				href "changeLogPage", title: "", description: "${appInfoDesc()}", image: getAppImg("efergy_512.png")
			}
			section("Efergy Login Page") {
				paragraph "Please enter your https://engage.efergy.com login credentials to generate you Authentication Token and install the device automatically for you."
				input("username", "email", title: "Username", description: "Efergy Username (email address)")
				input("password", "password", title: "Password", description: "Efergy Password")
				LogAction("login status: ${atomicState?.loginStatus} - ${atomicState?.loginDesc}", "info", true)
				if (atomicState?.loginStatus != null && atomicState?.loginDesc != null && atomicState?.loginStatus != "ok") {
					paragraph "${atomicState?.loginDesc}... Please try again!!!"
				}
			}
		}
	}
}

/* Preferences */
def mainPage() {
	if (!atomicState?.efergyAuthToken) { getAuthToken() }
	getCurrency()
	getApiData()
	updateWebStuff(true)
	if (atomicState?.loginStatus.toString() != "ok") { return loginPage() }
	def setupComplete = (!atomicState.appInstalled) ? false : true

	dynamicPage(name: "mainPage", uninstall: false, install: true) {
		if (atomicState?.efergyAuthToken) {
			section("") {
				href "changeLogPage", title: "", description: "${appInfoDesc()}", image: getAppImg("efergy_512.png", true)
			}
			if(setupComplete) {
				if(atomicState?.energyInfoData?.hubData && atomicState?.readingData) {
					section("Efergy Hub:") {
						href "hubInfoPage", title:"View Hub Info", description: "Tap to view more...", image: getAppImg("St_hub.png")
						def rStr = ""
						rStr += atomicState?.readingData?.readingUpdated ? "Last Reading:\n${atomicState?.readingData?.readingUpdated}" : ""
						rStr += atomicState?.readingData?.powerReading ? "${atomicState?.readingData?.readingUpdated ? "\n" : ""}Power Reading: (${atomicState?.readingData?.powerReading}W)" : ""
						rStr += "\n\nTap to view more..."
						href "readingInfoPage", title:"View Energy Data", description: rStr, state: (atomicState?.readingData?.readingUpdated ? "complete" : null),
								image: getAppImg("power_meter.png")
					}
				}
				if(wattvisionOk()) {
					section("WattVision Integration:") {
						input("updateWattVision", "bool", title: "Send Power Data to WattVision API?", description: "")
					}
				}
				section("Preferences:") {
					def descStr = ""
					def sz = descStr.size()
					descStr += getAppNotifConfDesc() ?: ""
					if(descStr.size() != sz) { descStr += "\n\n"; sz = descStr.size() }
					descStr += getAppDebugDesc() ?: ""
					if(descStr.size() != sz) { descStr += "\n\n"; sz = descStr.size() }
					def prefDesc = (descStr != "") ? "${descStr}Tap to Modify..." : "Tap to Configure..."
					href "prefsPage", title: "Preferences", description: prefDesc, state: (descStr ? "complete" : ""), image: getAppImg("settings_icon.png")
				}
			} else {
				section("") {
					paragraph "Tap Done to complete the install..."
				}
			}

			section("Info") {
				href "infoPage", title: "Info and Instructions", description: "Tap to view...", image: getAppImg("info.png")
			}
			section("") {
				href "uninstallPage", title: "Uninstall this App", description: "Tap to Remove...", image: getAppImg("uninstall_icon.png")
			}
		}

		if (!atomicState.efergyAuthToken) {
			section() {
				paragraph "Authentication Token is Missing... Please login again!!!"
				href "loginPage", title:"Login to Efergy", description: "Tap to loging..."
			}
		}
	}
}

def wattvisionOk() {
	return (appSettings?.wattVision_api_id && appSettings?.wattVision_api_key && appSettings?.wattVision_sensor_id) ? true : false
}

//Defines the Preference Page
def prefsPage () {
	dynamicPage(name: "prefsPage", title: "Application Preferences", install: false, uninstall: false) {
		section("Currency Selection:"){
			   input(name: "currencySym", type: "enum", title: "Select your Currency Symbol", options: ["\$", "£", "€"], defaultValue: "\$", submitOnChange: true, image: getAppImg("currency_icon.png"))
			   atomicState.currencySym = currencySym
		}
		// Set Notification Recipients
		section("Notifications:") {
			href "notifPrefPage", title: "Notifications", description: (getAppNotifConfDesc() ? "${getAppNotifConfDesc()}\n\nTap to modify..." : "Tap to configure..."), state: (getAppNotifConfDesc() ? "complete" : null),
					image: getAppImg("notification_icon.png")
		}

		section("Logging:") {
			def dbgDesc = getAppDebugDesc()
			href "debugPrefPage", title: "Logging", description: (dbgDesc ? "${dbgDesc ?: ""}\n\nTap to modify..." : "Tap to configure..."), state: ((isAppDebug() || isChildDebug()) ? "complete" : null),
					image: getAppImg("log.png")
		}
		poll()
	}
}

def debugPrefPage() {
	dynamicPage(name: "debugPrefPage", install: false) {
		section ("Application Logs") {
			input (name: "appDebug", type: "bool", title: "Show App Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("log.png"))
			if (settings?.appDebug) {
				LogAction("Debug Logs are Enabled...", "info", false)
			}
			else { LogAction("Debug Logs are Disabled...", "info", false) }
		}
		section ("Child Device Logs") {
			input (name: "childDebug", type: "bool", title: "Show Device Logs in the IDE?", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("log.png"))
			if (settings?.childDebug) { LogAction("Device Debug Logs are Enabled...", "info", false) }
			else { LogAction("Device Debug Logs are Disabled...", "info", false) }
		}
	}
}

def notifPrefPage() {
	dynamicPage(name: "notifPrefPage", install: false) {
		def sectDesc = !location.contactBookEnabled ? "Enable push notifications below..." : "Select People or Devices to Receive Notifications..."
		section(sectDesc) {
			if(!location.contactBookEnabled) {
				input(name: "usePush", type: "bool", title: "Send Push Notitifications", required: false, defaultValue: false, submitOnChange: true, image: getAppImg("notification_icon.png"))
			} else {
				input(name: "recipients", type: "contact", title: "Send notifications to", required: false, submitOnChange: true, image: getAppImg("recipient_icon.png")) {
					input ("phone", "phone", title: "Phone Number to send SMS to...", required: false, submitOnChange: true, image: getAppImg("notification_icon.png"))
				}
			}
		}

		if (settings?.recipients || settings?.phone || settings?.usePush) {
			if(settings?.recipients && !atomicState?.pushTested) {
				sendMsg("Info", "Push Notification Test Successful... Notifications have been Enabled for ${textAppName()}")
				atomicState.pushTested = true
			} else { atomicState.pushTested = true }

			section(title: "Time Restrictions") {
				href "setNotificationTimePage", title: "Silence Notifications...", description: (getNotifSchedDesc() ?: "Tap to configure..."), state: (getNotifSchedDesc() ? "complete" : null), image: getAppImg("quiet_time_icon.png")
			}
			section("Missed Poll Notification:") {
				input (name: "sendMissedPollMsg", type: "bool", title: "Send Missed Poll Messages?", defaultValue: true, submitOnChange: true, image: getAppImg("late_icon.png"))
				if(sendMissedPollMsg == null || sendMissedPollMsg) {
					def misPollNotifyWaitValDesc = !misPollNotifyWaitVal ? "Default: 15 Minutes" : misPollNotifyWaitVal
					input (name: "misPollNotifyWaitVal", type: "enum", title: "Time Past the missed Poll?", required: false, defaultValue: 900, metadata: [values:notifValEnum()], submitOnChange: true)
					if(misPollNotifyWaitVal) {
						atomicState.misPollNotifyWaitVal = !misPollNotifyWaitVal ? 900 : misPollNotifyWaitVal.toInteger()
						if (misPollNotifyWaitVal.toInteger() == 1000000) {
							input (name: "misPollNotifyWaitValCust", type: "number", title: "Custom Missed Poll Value in Seconds", range: "60..86400", required: false, defaultValue: 900, submitOnChange: true)
							if(misPollNotifyWaitValCust) { atomicState?.misPollNotifyWaitVal = misPollNotifyWaitValCust ? misPollNotifyWaitValCust.toInteger() : 900 }
						}
					} else { atomicState.misPollNotifyWaitVal = !misPollNotifyWaitVal ? 900 : misPollNotifyWaitVal.toInteger() }

					def misPollNotifyMsgWaitValDesc = !misPollNotifyMsgWaitVal ? "Default: 1 Hour" : misPollNotifyMsgWaitVal
					input (name: "misPollNotifyMsgWaitVal", type: "enum", title: "Delay before sending again?", required: false, defaultValue: 3600, metadata: [values:notifValEnum()], submitOnChange: true)
					if(misPollNotifyMsgWaitVal) {
						atomicState.misPollNotifyMsgWaitVal = !misPollNotifyMsgWaitVal ? 3600 : misPollNotifyMsgWaitVal.toInteger()
						if (misPollNotifyMsgWaitVal.toInteger() == 1000000) {
							input (name: "misPollNotifyMsgWaitValCust", type: "number", title: "Custom Msg Wait Value in Seconds", range: "60..86400", required: false, defaultValue: 3600, submitOnChange: true)
							if(misPollNotifyMsgWaitValCust) { atomicState.misPollNotifyMsgWaitVal = misPollNotifyMsgWaitValCust ? misPollNotifyMsgWaitValCust.toInteger() : 3600 }
						}
					} else { atomicState.misPollNotifyMsgWaitVal = !misPollNotifyMsgWaitVal ? 3600 : misPollNotifyMsgWaitVal.toInteger() }
				}
			}
			section("App and Device Updates:") {
				input (name: "sendAppUpdateMsg", type: "bool", title: "Send for Updates...", defaultValue: true, submitOnChange: true, image: getAppImg("update_icon.png"))
				if(sendMissedPollMsg == null || sendAppUpdateMsg) {
					def updNotifyWaitValDesc = !updNotifyWaitVal ? "Default: 2 Hours" : updNotifyWaitVal
					input (name: "updNotifyWaitVal", type: "enum", title: "Send reminders every?", required: false, defaultValue: 7200, metadata: [values:notifValEnum()], submitOnChange: true)
					if(updNotifyWaitVal) {
						atomicState.updNotifyWaitVal = !updNotifyWaitVal ? 7200 : updNotifyWaitVal.toInteger()
						if (updNotifyWaitVal.toInteger() == 1000000) {
							input (name: "updNotifyWaitValCust", type: "number", title: "Custom Missed Poll Value in Seconds", range: "30..86400", required: false, defaultValue: 7200, submitOnChange: true)
							if(updNotifyWaitValCust) { atomicState.updNotifyWaitVal = updNotifyWaitValCust ? updNotifyWaitValCust.toInteger() : 7200 }
						}
					} else { atomicState.updNotifyWaitVal = !updNotifyWaitVal ? 7200 : updNotifyWaitVal.toInteger() }
				}
			}
		} else { atomicState.pushTested = false }
	}
}

def setNotificationTimePage() {
	dynamicPage(name: "setNotificationTimePage", title: "Prevent Notifications\nDuring these Days, Times or Modes", uninstall: false) {
		def timeReq = (settings["qStartTime"] || settings["qStopTime"]) ? true : false
		section() {
			input "qStartInput", "enum", title: "Starting at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("start_time_icon.png")
			if(settings["qStartInput"] == "A specific time") {
				input "qStartTime", "time", title: "Start time", required: timeReq, image: getAppImg("start_time_icon.png")
			}
			input "qStopInput", "enum", title: "Stopping at", options: ["A specific time", "Sunrise", "Sunset"], defaultValue: null, submitOnChange: true, required: false, image: getAppImg("stop_time_icon.png")
			if(settings?."qStopInput" == "A specific time") {
				input "qStopTime", "time", title: "Stop time", required: timeReq, image: getAppImg("stop_time_icon.png")
			}
			input "quietDays", "enum", title: "Only on these days of the week", multiple: true, required: false, image: getAppImg("day_calendar_icon.png"),
					options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
			input "quietModes", "mode", title: "When these Modes are Active", multiple: true, submitOnChange: true, required: false, image: getAppImg("mode_icon.png")
		}
	}
}

def infoPage () {
	dynamicPage(name: "infoPage", title: "Help, Info and Instructions", install: false) {
		section("About this App:") {
			paragraph appInfoDesc(), image: getAppImg("efergy_256.png", true)
		}
		section("Donations:") {
			href url: textDonateLink(), style:"external", required: false, title:"Donations",
				description:"Tap to Open in Mobile Browser...", state: "complete", image: getAppImg("donate_icon.png")
		}
		section("Created by:") {
			paragraph "Anthony S. (@tonesto7)", state: "complete"
		}
		section("App Revision History:") {
			href "changeLogPage", title: "View App Change Log Info", description: "Tap to View...", image: getAppImg("change_log_icon.png")
		}
		section("Licensing Info:") {
			paragraph "${textCopyright()}\n${textLicense()}"
		}
	}
}

def changeLogPage () {
	dynamicPage(name: "changeLogPage", title: "View Change Info", install: false) {
		section("App Revision History:") {
			paragraph appVerInfo()
		}
	}
}

def uninstallPage() {
	dynamicPage(name: "uninstallPage", title: "Uninstall", uninstall: true) {
		section("") {
			if(parent) {
				paragraph "This will uninstall the ${app?.label} Automation!!!"
			} else {
				paragraph "This will uninstall the App, All Automation Apps and Child Devices.\n\nPlease make sure that any devices created by this app are removed from any routines/rules/smartapps before tapping Remove."
			}
		}
	}
}

def getAppNotifConfDesc() {
	def str = ""
	str += pushStatus() ? "Notifications:" : ""
	str += (pushStatus() && settings?.recipients) ? "\n • Contacts: (${settings?.recipients?.size()})" : ""
	str += (pushStatus() && settings?.usePush) ? "\n • Push Messages: Enabled" : ""
	str += (pushStatus() && sms) ? "\n • SMS: (${sms?.size()})" : ""
	str += (pushStatus() && settings?.phone) ? "\n • SMS: (${settings?.phone?.size()})" : ""
	str += (pushStatus() && getNotifSchedDesc()) ? "\n${getNotifSchedDesc()}" : ""
	return pushStatus() ? "${str}" : null
}

def getRecipientsNames(val) {
	def n = ""
	def i = 0
	if(val) {
		val?.each { r ->
			i = i + 1
			n += "${(i == val?.size()) ? "${r}" : "${r},"}"
		}
	}
	return n?.toString().replaceAll("\\,", "\n")
}

def getRecipientDesc() {
	return ((settings?."NotifRecips") || (settings?."NotifPhones" || settings?."NotifUsePush")) ? "${getRecipientsNames(settings?."NotifRecips")}" : null
}

def getInputToStringDesc(inpt, addSpace = null) {
	def cnt = 0
	def str = ""
	if(inpt) {
		inpt.sort().each { item ->
			cnt = cnt+1
			str += item ? (((cnt < 1) || (inpt?.size() > 1)) ? "\n      ${item}" : "${addSpace ? "      " : ""}${item}") : ""
		}
	}
	//log.debug "str: $str"
	return (str != "") ? "${str}" : null
}

def getNotifSchedDesc() {
	def sun = getSunriseAndSunset()
	//def schedInverted = settings?."DmtInvert"
	def startInput = settings?.qStartInput
	def startTime = settings?.qStartTime
	def stopInput = settings?.qStopInput
	def stopTime = settings?.qStopTime
	def dayInput = settings?.quietDays
	def modeInput = settings?.quietModes
	def notifDesc = ""
	def getNotifTimeStartLbl = ( (startInput == "Sunrise" || startInput == "Sunset") ? ( (startInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (startTime ? time2Str(startTime) : "") )
	def getNotifTimeStopLbl = ( (stopInput == "Sunrise" || stopInput == "Sunset") ? ( (stopInput == "Sunset") ? epochToTime(sun?.sunset.time) : epochToTime(sun?.sunrise.time) ) : (stopTime ? time2Str(stopTime) : "") )
	notifDesc += (getNotifTimeStartLbl && getNotifTimeStopLbl) ? " • Silent Time: ${getNotifTimeStartLbl} - ${getNotifTimeStopLbl}" : ""
	def days = getInputToStringDesc(dayInput)
	def modes = getInputToStringDesc(modeInput)
	notifDesc += days ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl) ? "\n" : ""} • Silent Day${isPluralString(dayInput)}: ${days}" : ""
	notifDesc += modes ? "${(getNotifTimeStartLbl || getNotifTimeStopLbl || days) ? "\n" : ""} • Silent Mode${isPluralString(modeInput)}: ${modes}" : ""
	return (notifDesc != "") ? "${notifDesc}" : null
}

def isPluralString(obj) {
	return (obj?.size() > 1) ? "(s)" : ""
}

def getAppDebugDesc() {
	def str = ""
	str += isAppDebug() ? "App Debug: (${debugStatus()})" : ""
	str += isChildDebug() && str != "" ? "\n" : ""
	str += isChildDebug() ? "Device Debug: (${deviceDebugStatus()})" : ""
	return (str != "") ? "${str}" : null
}

def readingInfoPage () {
	if (!atomicState?.energyInfoData?.hubData?.hubName) { poll() }
	return dynamicPage(name: "readingInfoPage", refreshTimeout:10, install: false, uninstall: false) {
		 section ("Efergy Reading Information") {
			def rData = atomicState?.readingData
			def tData = atomicState?.energyInfoData?.tarrifData
			if(rData) {
				paragraph "Current Power Reading: " + rData?.powerReading + "W", state: "complete"
				paragraph "Current Energy Reading: " + rData?.energyReading + "kWh", state: "complete"
			}
			if(tData) {
				paragraph "Tariff Rate: " + atomicState?.currencySym + tData?.tariffRate, state: "complete"
				paragraph "Today's Usage: " + atomicState?.currencySym + tData?.todayCost + " (${tData?.todayUsage} kWH", state: "complete"
				paragraph "${atomicState?.monthName} Usage: " + tData?.currencySym + tData?.monthCost + " (${tData?.monthUsage} kWH", state: "complete"
				paragraph "Month Cost Estimate: " + tData?.currencySym + tData?.monthBudget, state: "complete"
			}
		}
	}
}

def hubInfoPage () {
	if (!atomicState?.energyInfoData?.hubData) { poll() }
	return dynamicPage(name: "hubInfoPage", refreshTimeout:10, install: false, uninstall: false) {
		def hdata = atomicState?.energyInfoData?.hubData
		 section ("Efergy Hub Information") {
			if(hdata) {
				paragraph "Hub Name: " + hdata?.hubName
				paragraph "Hub ID: " + hdata?.hubId
				paragraph "Hub Mac Address: " + hdata?.hubMacAddr
				paragraph "Hub Status: " + hdata?.hubStatus
				paragraph "Hub Data TimeStamp: " + hdata?.hubTsHuman
				paragraph "Hub Type: " + hdata?.hubType
				paragraph "Hub Firmware: " + hdata?.hubVersion
			} else {
				paragraph "There is No Data to Show at the Moment..."
			}
		}
	}
}

def installed() {
	atomicState.appInstalled = true
	sendNotificationEvent("${textAppName()} - ${appVersion()} (${appVerDate()}) installed...")
	log.info "${textAppName()} - ${appVersion()} (${appVerDate()}) installed..."
	initialize()
}

def updated() {
	if (!atomicState.appInstalled) { atomicState.appInstalled = true }
	sendNotificationEvent("${textAppName()} - ${appVersion()} (${appVerDate()}) updated...")
	log.info "${textAppName()} - ${appVersion()} (${appVerDate()}) updated..."
	unsubscribe()
	unsubscribe()
	initialize()
}

def uninstalled() {
	unschedule()
	unsubscribe()
	addRemoveDevices(true)
}

def initialize() {
	poll()
	addRemoveDevices()
	addSchedule()
	evtSubscribe()
	poll()
}

def onAppTouch(event) {
	updated()
}

// poll command
def poll() {
	getLastRefreshSec()
	if (atomicState?.efergyAuthToken) {
		if (atomicState?.timeSinceRfsh > 30) {
			LogAction("","info", false)
			log.info "Getting Latest Energy Data from Efergy API"
			getDayMonth()
			getApiData()
			def pwr = atomicState?.readingData?.powerReading
			def ener = atomicState?.energyInfoData?.usageData?.todayUsage
			if(wattvisionOk() && settings?.updateWattVision && pwr && ener) {
				sendToWattVision(pwr, ener)
			}
			updateDeviceData()
			LogAction("", "info", false)
			runIn(27, "checkSchedule")
		}
		else if (atomicState?.timeSinceRfsh > 360 || !atomicState?.timeSinceRfsh) { checkSchedule() }
	}
	if(!atomicState?.cleanupComplete || cleanupVer() != atomicState?.cleanupVer) {
		runIn(5, "stateCleanup", [overwrite: false])
	}
	updateWebStuff()
	notificationCheck() //Checks if a notification needs to be sent for a specific event
}

//Create schedule to poll for device data (Triggers roughly every 30 seconds)
private addSchedule() {
	//schedule("1/1 * * * * ?", "poll") //Runs every 30 seconds to Refresh Data
	schedule("0 0/1 * * * ?", "poll") //Runs every 1 minute to make sure that data is accurate
	//runIn(27, "checkSchedule")
	//runIn(130, "checkSchedule")
}

private checkSchedule() {
	LogAction("Check Schedule has ran!","trace", false)
	getLastRefreshSec()
	def timeSince = atomicState.timeSinceRfsh ?: null
	if (timeSince > 360) {
		log.warn "Polling Issue | It's been $timeSince seconds since last refresh... Re-initializing Schedule... Polling Will Occur in 30 Seconds"
		addSchedule()
		return
	}
	else if (!timeSince) {
		log.warn "Hub TimeStamp Value was null..."
		log.debug "Re-initializing schedule... Data should resume refreshing in 30 seconds"
		addSchedule()
		return
	}
	else {
		poll()
	}
}

//subscribes to the various location events and uses them to poll the data if the scheduler gets stuck
private evtSubscribe() {
	subscribe(app, onAppTouch)
	subscribe(location, "sunrise", poll)
	subscribe(location, "sunset", poll)
	subscribe(location, "mode", poll)
	subscribe(location, "sunriseTime", poll)
	subscribe(location, "sunsetTime", poll)
}

//Creates the child device if it not already there
private addRemoveDevices(uninst=false) {
	try {
		def devsInUse = []
		def dni = "Efergy Engage|${atomicState?.energyInfoData?.hubData?.hubMacAddr}"
		def d = getChildDevice(dni)
		if(!uninst) {
			if(!d) {
				d = addChildDevice(textNamespace(), childDevName(), dni, null, [name: childDevName(), label: childDevName(), completedSetup: true])
				d.take()
				LogAction("Successfully Created Child Device: ${d.displayName} (${dni})", "info", true)
				devsInUse += dni
			}
			else {
				LogAction("Device already created", "info", true)
			}
			//def delete
			//delete = getChildDevices().findAll { !devsInUse?.toString()?.contains(it?.deviceNetworkId) }
			//if(delete?.size() > 0) {
			//	LogAction("Removing ${delete.size()} device...", "warn", true)
			//	delete.each { deleteChildDevice(it.deviceNetworkId) }
			//}
		} else {
			app.getChildDevices(true).each {
				deleteChildDevice(it.deviceNetworkId)
				log.info "Successfully Removed Child Device: ${it.displayName} (${it.deviceNetworkId})"
			}
		}
	} catch (ex) {
		log.error "addRemoveDevices exception:", ex
	}
}

//Sends updated reading data to the Child Device
def updateDeviceData() {
	LogAction("updateDeviceData...", "trace", false)
	try {
		def enerData = atomicState?.energyInfoData
		def readData = atomicState?.readingData
		def api = !apiIssues() ? false : true
		def dbg = !settings?.childDebug ? false : true
		def devs = app.getChildDevices(true)
		if(devs?.size() > 0) {
			LogAction(" ", "trace", false)
			LogAction("--------------Sending Data to Device--------------", "trace", false)
			if(enerData && readData) {
				def devData = [
						"usageData":enerData?.usageData,
						"tariffData":enerData?.tariffData,
						"readingData":readData,
						"hubData":enerData?.hubData,
						"monthName":atomicState?.monthName.toString(),
						"debug":dbg,
						"currency":["dollar":atomicState?.currencySym.toString(), "cent":atomicState?.centSym.toString()],
						"latestVer":latestDevVer()?.ver?.toString(),
						"apiIssues":api
				]
				devs?.each { dev ->
					atomicState?.devVer = it?.devVer() ?: ""
					dev?.generateEvent(devData) //parse received message from parent
				}
			} else {
				if(enerData == null) {
					log.warn("updateDeviceData:  Missing energyInfoData.  Skipping Device Update...")
				}
				if(readData == null) {
					log.warn("updateDeviceData:  Missing ReadingData.  Skipping Device Update...")
				}
			}
		} else {
			log.warn("There aren't any devices installed.  Skipping Update...")
		}
	} catch (ex) {
		log.error "updateDeviceData exception:", ex
	}
}

def sendToWattVision(watts, watthours) {
	//{"sensor_id":"XXXXXX","api_id":"XXXXXX","api_key":"XXXXXX","watts":1003}' http://www.wattvision.com/api/v0.2/elec
	try {
		if(wattvisionOk() && settings?.updateWattVision && watts) {
			def params = [
		        uri: "http://www.wattvision.com",
		        path: "/api/v0.2/elec",
		        contentType: "application/json",
				body: [
					"sensor_id":appSettings?.wattVision_sensor_id,
					"api_id":appSettings?.wattVision_api_id,
					"api_key":appSettings?.wattVision_api_key,
					"watts":watts,
					"watthours":watthours
				]
		    ]
			log.info "Sending Data to WattVision | Power: (${watts}W) | Energy: (${watthours} kWh)"
		    asynchttp_v1.post(wattVisionResponse, params)
		}
	} catch (ex) {
		log.error "sendToWattVision error: ", ex
	}
}

def wattVisionResponse(response, data) {
	if(response?.hasError()) {
    	log.error "WattVision Error Response: ${response.data}"
	}
}

def apiIssues() {
	def result = state?.apiIssuesList.toString().contains("true") ? true : false
	if(result) {
		LogAction("Efergy API Issues Detected... (${getDtNow()})", "warn", true)
	}
	return result
}

def apiIssueEvent(issue) {
	def list = state?.apiIssuesList ?: []
	//log.debug "listIn: $list (${list?.size()})"
	def listSize = 3
	if(list?.size() < listSize) {
		list.push(issue)
	}
	else if (list?.size() > listSize) {
		def nSz = (list?.size()-listSize) + 1
		//log.debug ">listSize: ($nSz)"
		def nList = list?.drop(nSz)
		//log.debug "nListIn: $list"
		nList?.push(issue)
		//log.debug "nListOut: $nList"
		list = nList
	}
	else if (list?.size() == listSize) {
		def nList = list?.drop(1)
		nList?.push(issue)
		list = nList
	}

	if(list) { state?.apiIssuesList = list }
	//log.debug "listOut: $list"
}


// Get Efergy Authentication Token
private getAuthToken() {
	try {
		def closure = {
			resp ->
			log.debug("Auth Response: ${resp?.data}")
			if (resp?.data?.status == "ok") {
				atomicState?.loginStatus = "ok"
				atomicState?.loginDesc = resp?.data?.desc
				atomicState?.efergyAuthToken = resp?.data?.token
			}
			else {
				atomicState.loginStatus = resp?.data?.status
				atomicState.loginDesc = resp?.data?.desc
				return
			}
		}
		def params = [
			uri: "https://engage.efergy.com",
			path: "/mobile/get_token",
			query: ["username": settings.username, "password": settings.password, "device": "website"],
			contentType: 'application/json'
			]
		httpGet(params, closure)
		poll()
	} catch (ex) {
		log.error "getAuthToken Exception:", ex
	}
}

//Converts Today's DateTime into Day of Week and Month Name ("September")
def getDayMonth() {
	def month = new SimpleDateFormat("MMMM").format(new Date())
	def day = new SimpleDateFormat("EEEE").format(new Date())
	if (month && day) {
		atomicState.monthName = month
		atomicState.dayOfWeek = day
	}
}

def getCurrency() {
	def unitName = ""
	switch (atomicState.currencySym) {
		case '$':
			unitName = "US Dollar (\$)"
			atomicState.centSym = "¢"
		break
		case '£':
			unitName = "British Pound (£)"
			atomicState.centSym = "p"
		break
		case '€':
			unitName = "Euro Dollar (€)"
			atomicState.centSym = "¢"
		break
		default:
			unitName = "unknown"
			atomicState.centSym = "¢"
		break
	}
	return unitName
}

def debugStatus() { return !settings?.appDebug ? "Off" : "On" }
def deviceDebugStatus() { return !settings?.childDebug ? "Off" : "On" }
def isAppDebug() { return !settings?.appDebug ? false : true }
def isChildDebug() { return !settings?.childDebug ? false : true }

/************************************************************************************************
|								Push Notification Functions										|
*************************************************************************************************/
def pushStatus() { return (settings?.recipients || settings?.phone || settings?.usePush) ? (settings?.usePush ? "Push Enabled" : "Enabled") : null }
def getLastMsgSec() { return !atomicState?.lastMsgDt ? 100000 : GetTimeDiffSeconds(atomicState?.lastMsgDt, "getLastMsgSec").toInteger() }
def getLastUpdMsgSec() { return !atomicState?.lastUpdMsgDt ? 100000 : GetTimeDiffSeconds(atomicState?.lastUpdMsgDt, "getLastUpdMsgSec").toInteger() }
def getLastMisPollMsgSec() { return !atomicState?.lastMisPollMsgDt ? 100000 : GetTimeDiffSeconds(atomicState?.lastMisPollMsgDt, "getLastMisPollMsgSec").toInteger() }
def getRecipientsSize() { return !settings.recipients ? 0 : settings?.recipients.size() }

def latestDevVer()    { return atomicState?.appData?.updater?.versions?.dev ?: "unknown" }
def getOk2Notify() { return ((settings?.recipients || settings?.usePush) && (daysOk(settings?.quietDays) && notificationTimeOk() && modesOk(settings?.quietModes))) }
def getLastDevicePollSec() { return !atomicState?.lastDevDataUpd ? 840 : GetTimeDiffSeconds(atomicState?.lastDevDataUpd, null, "getLastDevicePollSec").toInteger() }

def notificationCheck() {
	if(!getOk2Notify()) { return }
	missPollNotify(settings?.sendMissedPollMsg, (atomicState?.misPollNotifyMsgWaitVal.toInteger() ?: 3600))
	appUpdateNotify()
}

void missPollNotify(on, wait) {
	def missedPoll = getLastMisPollMsgSec() > wait ? true : false
	if(!on || !wait || !missedPoll) { return }
	if(missedPoll) {
		def msg = "\nThe app has not refreshed energy data in the last (${getLastDevicePollSec()}) seconds.\nPlease try refreshing data using device refresh button."
		sendMsg("${app.name} Polling Issue", msg)
		LogAction(msg.toString().replaceAll("\n", " "), "error", true)
		atomicState?.lastMisPollMsgDt = getDtNow()
	}
}

void appUpdateNotify() {
	def on = settings?.app
	def appUpd = isAppUpdateAvail()
	def devUpd = isDevUpdateAvail()
	if(getLastUpdMsgSec() > atomicState?.updNotifyWaitVal.toInteger()) {
		if(appUpd || devUpd) {
			def str = ""
			str += !appUpd ? "" : "\nManager App: v${atomicState?.appData?.updater?.versions?.app?.ver?.toString()}"
			str += !devUpd ? "" : "\nElite Device: v${atomicState?.appData?.updater?.versions?.dev?.ver?.toString()}"
			sendMsg("Info", "Efergy Manager Update(s) are Available:${str}...  \n\nPlease visit the IDE to Update your code...")
			atomicState?.lastUpdMsgDt = getDtNow()
		}
	}
}

def sendMsg(msgType, msg, people = null, sms = null, push = null, brdcast = null) {
	try {
		if(!getOk2Notify()) {
			LogAction("No Notifications will be sent during Quiet Time...", "info", true)
		} else {
			def newMsg = "${msgType}: ${msg}"
			def flatMsg = newMsg.toString().replaceAll("\n", " ")
			if(!brdcast) {
				def who = people ? people : settings?.recipients
				if (location.contactBookEnabled) {
					if(who) {
						sendNotificationToContacts(newMsg, who)
						atomicState?.lastMsg = newMsg
						atomicState?.lastMsgDt = getDtNow()
						LogAction("Push Message (${flatMsg}) Sent to Contacts ${who} at ${atomicState?.lastMsgDt}", "debug", true)
					}
				} else {
					LogAction("ContactBook is NOT Enabled on your SmartThings Account...", "warn", true)
					if (push) {
						sendPush(newMsg)
						atomicState?.lastMsg = newMsg
						atomicState?.lastMsgDt = getDtNow()
						LogAction("Push Message (${flatMsg}) Sent at ${atomicState?.lastMsgDt}", "debug", true)
					}
					else if (sms) {
						sendSms(sms, newMsg)
						atomicState?.lastMsg = newMsg
						atomicState?.lastMsgDt = getDtNow()
						LogAction("SMS Message (${flatMsg}) Sent at ${atomicState?.lastMsgDt}", "debug", true)
					}
				}
			} else {
				sendPushMessage(newMsg)
				LogAction("Broadcast Message (${flatMsg}) Sent at ${atomicState?.lastMsgDt}", "debug", true)
			}
		}
	} catch (ex) {
		log.error "sendMsg Exception:", ex
	}
}

def notificationTimeOk() {
//	try {
		def strtTime = null
		def stopTime = null
		def now = new Date()
		def sun = getSunriseAndSunset() // current based on geofence, previously was: def sun = getSunriseAndSunset(zipCode: zipCode)
		if(settings?.qStartTime && settings?.qStopTime) {
			if(settings?.qStartInput == "sunset") { strtTime = sun.sunset }
			else if(settings?.qStartInput == "sunrise") { strtTime = sun.sunrise }
			else if(settings?.qStartInput == "A specific time" && settings?.qStartTime) { strtTime = settings?.qStartTime }

			if(settings?.qStopInput == "sunset") { stopTime = sun.sunset }
			else if(settings?.qStopInput == "sunrise") { stopTime = sun.sunrise }
			else if(settings?.qStopInput == "A specific time" && settings?.qStopTime) { stopTime = settings?.qStopTime }
		} else { return true }
		if(strtTime && stopTime) {
			return timeOfDayIsBetween(strtTime, stopTime, new Date(), getTimeZone()) ? false : true
		} else { return true }
/*
	} catch (ex) {
		log.error "notificationTimeOk Exception:", ex
	}
*/
}

def daysOk(days) {
	if(days) {
		def dayFmt = new SimpleDateFormat("EEEE")
		if(getTimeZone()) { dayFmt.setTimeZone(getTimeZone()) }
		return days.contains(dayFmt.format(new Date())) ? false : true
	} else { return true }
}

def modesOk(modeEntry) {
	def res = true
	if(modeEntry) {
		modeEntry?.each { m ->
			if(m.toString() == location?.mode.toString()) { res = false }
		}
	}
	return res
}

def getLastWebUpdSec() { return !atomicState?.lastWebUpdDt ? 100000 : GetTimeDiffSeconds(atomicState?.lastWebUpdDt, "getLastWebUpdSec").toInteger() }

def updateWebStuff(now = false) {
	//log.trace "updateWebStuff..."
	if (!atomicState?.appData || (getLastWebUpdSec() > (3600*6))) {
		if(now) {
			getWebFileData()
		} else {
			if(canSchedule()) { runIn(45, "getWebFileData", [overwrite: true]) }  //This reads a JSON file from a web server with timing values and version numbers
		}
	}
}

def getWebFileData() {
	//log.trace "getWebFileData..."
	def params = [ uri: "https://raw.githubusercontent.com/tonesto7/efergy-manager/${gitBranch()}/resources/data/appParams.json", contentType: 'application/json' ]
	def result = false
	try {
		httpGet(params) { resp ->
			if(resp.data) {
				LogAction("Getting Latest Data from appParams.json File...", "info", true)
				atomicState?.appData = resp?.data
				atomicState?.lastWebUpdDt = getDtNow()
				updateHandler()
				broadcastCheck()
			}
			LogTrace("getWebFileData Resp: ${resp?.data}")
			result = true
		}
	}
	catch (ex) {
		if(ex instanceof groovyx.net.http.HttpResponseException) {
			   log.warn  "appParams.json file not found..."
		} else {
			log.error "getWebFileData Exception:", ex
		}
	}
	return result
}

def broadcastCheck() {
	if(atomicState?.isInstalled && atomicState?.appData.broadcast) {
		if(atomicState?.appData?.broadcast?.msgId != null && atomicState?.lastBroadcastId != atomicState?.appData?.broadcast?.msgId) {
			sendMsg(atomicState?.appData?.broadcast?.type.toString().capitalize(), atomicState?.appData?.broadcast?.message.toString(), null, null, null, true)
			atomicState?.lastBroadcastId = atomicState?.appData?.broadcast?.msgId
		}
	}
}

def updateHandler() {
	//log.trace "updateHandler..."
	if(atomicState?.isInstalled) {
		if(atomicState?.appData?.updater?.updateType.toString() == "critical" && atomicState?.lastCritUpdateInfo?.ver.toInteger() != atomicState?.appData?.updater?.updateVer.toInteger()) {
			sendMsg("Critical", "There are Critical Updates available for the Efergy Manager Application!!! Please visit the IDE and make sure to update the App and Device Code...")
			atomicState?.lastCritUpdateInfo = ["dt":getDtNow(), "ver":atomicState?.appData?.updater?.updateVer?.toInteger()]
		}
		if(atomicState?.appData?.updater?.updateMsg != "" && atomicState?.appData?.updater?.updateMsg != atomicState?.lastUpdateMsg) {
			if(getLastUpdateMsgSec() > 86400) {
				sendMsg("Info", "${atomicState?.updater?.updateMsg}")
				atomicState?.lastUpdateMsgDt = getDtNow()
			}
		}
	}
}

def isCodeUpdateAvailable(newVer, curVer, type) {
	def result = false
	def latestVer
	if(newVer && curVer) {
		def versions = [newVer, curVer]
		if(newVer != curVer) {
			latestVer = versions?.max { a, b ->
				def verA = a?.tokenize('.')
				def verB = b?.tokenize('.')
				def commonIndices = Math.min(verA?.size(), verB?.size())
				for (int i = 0; i < commonIndices; ++i) {
					//log.debug "comparing $numA and $numB"
					if(verA[i]?.toInteger() != verB[i]?.toInteger()) {
						return verA[i]?.toInteger() <=> verB[i]?.toInteger()
					}
				}
				verA?.size() <=> verB?.size()
			}
			result = (latestVer == newVer) ? true : false
		}
	}
	LogTrace("type: $type | newVer: $newVer | curVer: $curVer | newestVersion: ${latestVer} | result: $result")
	return result
}

def isAppUpdateAvail() {
	if(isCodeUpdateAvailable(atomicState?.appData?.updater?.versions?.app?.ver, appVersion(), "manager")) { return true }
	return false
}

def isDevUpdateAvail() {
	if(isCodeUpdateAvailable(atomicState?.appData?.updater?.versions?.dev?.ver, atomicState?.devVer, "dev")) { return true }
	return false
}

//Matches hubType to a full name
def getHubName(hubType) {
	def hubName = ""
	switch (hubType) {
		case 'EEEHub':
			hubName = "Efergy Engage Elite Hub"
			break
		default:
			hubName "unknown"
			break
	}
	return hubName
}

def getApiData() {
	getTzOffSet()
	getReadingData()
	getEnergyData()
}

def getTzOffSet() {
	def now = new Date().getTime()
	def val = location?.timeZone?.getOffset(now)
	//log.debug "val(before): $val"
	val = (val/1000/60)
	val = val > 0 ? (0 - val) : val.abs()
	//log.debug "val: $val"
	state?.tzOffsetVal = val ?: 0
}

private getEnergyData() {
	try {
		def data = [:]
		atomicState?.energyInfoData = [:]
		def tariffData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getTariff")
		def tdata = [:]
		if(tariffData[0]) {
			tdata["tariffUtility"] = tariffData?.tariff?.plan[0].utility[0] ?: null
			tdata["tariffName"] = tariffData?.tariff?.plan[0].name[0] ?: null
			tdata["tariffRate"] = tariffData?.tariff?.plan[0]?.plan[0]?.planDetail[0]?.rate[0] ?: null
			LogAction("TariffData: ${tdata}", "debug", false)
			data["tariffData"] = tdata
		}
		def tRate = tdata["tariffRate"].toDouble() ?: 0.0
		if(tRate instanceof Double && tRate > 0) { tRate = (tRate/100) }
		def todayUsage = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEnergy", ["period":"day"])
		def todayCost = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getForecast", ["period":"day", "dataType":"cost"])
		def weekUsage = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEnergy", ["period":"week"])
		//def usageCombined = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEstCombined")
		def monthUsage = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEnergy", ["period":"month"])
		def monthCost = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getForecast", ["period":"month", "dataType":"cost"])
		def yearUsage = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEnergy", ["period":"year"])

		def udata = [:]
		if(todayUsage) {
			udata["todayUsage"] = todayUsage?.sum ?: 0L
			udata["todayCost"] = todayCost?.day_tariff?.estimate ?: 0L
		}
		if(weekUsage) {
			udata["weekUsage"] = weekUsage?.sum ?: 0L
			udata["weekCost"] = tRate > 0 ? (calcEnergyCost(weekUsage?.sum, tRate) ?: null) : null
		}

		if(monthUsage || monthCost) {
			udata["monthUsage"] = monthUsage?.sum ?: OL
			udata["monthEst"] = monthCost?.month_tariff?.estimate ?: 0L
			udata["monthCost"] = monthCost?.month_tariff?.previousSum ?: 0L
			udata["monthBudget"] = monthCost?.month_budget ?: 0L
		}

		if(yearUsage) {
			udata["yearUsage"] = yearUsage?.sum ?: 0L
			udata["yearCost"] = tRate > 0 ? (calcEnergyCost(yearUsage?.sum, tRate) ?: null) : null
		}
		data["usageData"] = udata
		LogAction("UsageData: $udata", "trace", false)

		// Hub Data
		def hubData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getStatus")
		if(hubData) {
			def hdata = [:]
			hdata["hubId"] = hubData?.hid?.toString() ?: null
			hdata["hubMacAddr"] = hubData?.listOfMacs?.mac[0]?.toString() ?: null
			hdata["hubStatus"] = hubData?.listOfMacs?.status[0]?.toString() ?: null
			hdata["hubTs"] = hubData?.listOfMacs?.ts[0]?.toLong() ?: null
			hdata["hubTsDelta"] = hubData?.listOfMacs?.tsDelta[0] ?: null
			hdata["hubTsHuman"] = parseDt("E MMM dd HH:mm:ss yyyy", hubData?.listOfMacs?.tsHuman[0]?.toString(), false) ?: null
			hdata["hubTsLocal"] = parseDt("E MMM dd HH:mm:ss yyyy", hubData?.listOfMacs?.tsHuman[0]?.toString()) ?: null
			hdata["hubType"] = hubData?.listOfMacs?.type[0]?.toString() ?: null
			hdata["hubVersion"] = hubData?.listOfMacs?.version[0]?.toString() ?: null
			hdata["hubName"] = getHubName(hubData?.listOfMacs?.type[0].toString()) ?: null
			LogAction("HubData: $hdata", "trace", false)
			data["hubData"] = hdata
		}
		LogAction("getEnergyData: $data", "trace", false)
		atomicState?.energyInfoData = data
	}
	catch (ex) { log.error "getEnergyData Exception:", ex }
}

private getReadingData() {
	try {
		def today = new Date()
		def tf = new SimpleDateFormat("MMM d,yyyy - h:mm:ss a")
			tf.setTimeZone(location?.timeZone)
		def readingData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getCurrentValuesSummary")
		def rData = readingData[0]
		if(rData && (rData != atomicState?.lastReadingData)) {
			atomicState?.lastReadingData = rData
			def data = [:]
			atomicState?.readingData = [:]
			if (rData?.age) {
				   data["cidType"] = rData?.cid
				   data["age"] = rData?.age
				   if(rData?.units != null) { data["units"] = rData?.units }
			 }
			if(rData?.data[0]) {
				for (item in rData?.data[0]) {
					 if(item?.key) { data["readingUpdated"] = tf.format(item?.key.toLong()) ?: null }
					if(item?.value) {
						data["powerReading"] = item?.value?.toInteger() ?: null
						data["energyReading"] = (item?.value/1000).toDouble().round(2) ?: null
					}
				}
			}
			LogAction("ReadingData: ${data}", "trace", false)
			atomicState?.readingData = data
		}
	}
	catch (ex) { log.error "getReadingData Exception:", ex }
}

def getEfergyData(url, pathStr, extQuery=null) {
	LogAction("getEfergyData(Url: $url, Path: $pathStr, extQuery: $extQuery)", "trace", false)
	try {
		def q = ["token": atomicState.efergyAuthToken, "offset": state?.tzOffsetVal]
		if(extQuery && extQuery?.size()) {
			extQuery?.each { q << [it] }
		}
		def params = [
			uri: url,
			path: pathStr,
			query: q,
			contentType: 'application/json'
		]
		httpGet(params) { resp ->
			if(resp.data) {
				//log.debug "getEfergyData Response: ${resp?.data}"
				if(resp.status == 200) {
					atomicState?.lastDevDataUpd = getDtNow()
					apiIssueEvent(false)
					return resp?.data
				}
			}
		}
	} catch (ex) {
		apiIssueEvent(true)
		log.error "getEfergyData Exception:", ex
	}
}

def getTimeZone() {
	if (location.timeZone != null) {
		return location.timeZone
	} else { log.warn("getTimeZone: SmartThings TimeZone is not found on your account...") }
	return null
}

def formatDt(dt, tzChg=true) {
	def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
	if(tzChg) {
		if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
	}
	return tf?.format(dt)
}

def parseDt(pFormat, dt, tzFmt=true) {
	def result
	def newDt = Date.parse("$pFormat", dt)
	result = formatDt(newDt, tzFmt)
	//log.debug "parseDt Result: $result"
	return result
}

def getLastRefreshSec() {
	def ts = atomicState?.energyInfoData?.hubData?.hubTsHuman
	if(ts) {
		atomicState.timeSinceRfsh = GetTimeDiffSeconds(ts, "getLastRefreshSec")
		LogAction("TimeSinceRefresh: ${atomicState.timeSinceRfsh} seconds", "info", false)
	}
	runIn(130, "getLastRefreshSec")
}

//Returns time difference is seconds
def oldGetTimeDiffSeconds(String startDate) {
	try {
		def now = new Date()
		def startDt = new SimpleDateFormat("EE MMM dd HH:mm:ss yyyy").parse(startDate)
		def diff = now.getTime() - startDt.getTime()
		def diffSeconds = (int) (long) diff / 1000
		//def diffMinutes = (int) (long) diff / 60000
		return diffSeconds
	}
	catch (ex) {
		log.error "GetTimeDiffSeconds Exception:",ex
		return 10000
	}
}

def getDtNow() {
	def now = new Date()
	return formatDt(now)
}

//Returns time differences is seconds
def GetTimeDiffSeconds(lastDate, sender=null) {
	try {
		if(lastDate?.contains("dtNow")) { return 10000 }
		def now = new Date()
		def lastDt = Date.parse("E MMM dd HH:mm:ss z yyyy", lastDate)
		def start = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(lastDt)).getTime()
		def stop = Date.parse("E MMM dd HH:mm:ss z yyyy", formatDt(now)).getTime()
		def diff = (int) (long) (stop - start) / 1000
		return diff
	}
	catch (ex) {
		log.error "GetTimeDiffSeconds Exception: (${sender ? "$sender | " : ""}lastDate: $lastDate):", ex
		return 10000
	}
}

def getMonthStartEpoch() {
	//log.debug "$beginningOfLastMonth | $endOfLastMonth"
}

def notifValEnum(allowCust = true) {
	def valsC = [
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes", 1800:"30 Minutes",
		3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours", 1000000:"Custom"
	]
	def vals = [
		60:"1 Minute", 300:"5 Minutes", 600:"10 Minutes", 900:"15 Minutes", 1200:"20 Minutes", 1500:"25 Minutes",
		1800:"30 Minutes", 3600:"1 Hour", 7200:"2 Hours", 14400:"4 Hours", 21600:"6 Hours", 43200:"12 Hours", 86400:"24 Hours"
	]
	return allowCust ? valsC : vals
}

def cleanupVer() { return 2 }
def stateCleanup() {
	def items = [
		"cidType","cidUnit","energyReading","hubId","hubMacAddr","hubName","hubStatus","hubTsHuman","hubType","hubVersion","monthBudget",
		"monthCost","monthEst","monthUsage","readingDt","readingUpdated","tariffRate","todayCost","todayUsage", "hubData","lastHubData",
		"lastUsageData", "usageData", "lastTariffData", "tarrifData"
	]
	try {
		getState().each { item ->
			if(item?.key in items) {
				state.remove(item.key.toString())
			}
		}
		atomicState?.cleanupVer = cleanupVer()
		atomicState?.cleanupComplete = true
	} catch (ex) {
		atomicState?.cleanupComplete = false
	}
}

def LogTrace(msg) {
	def trOn = advAppDebug ? true : false
	if(trOn) { Logger(msg, "trace") }
}

def LogAction(msg, type = "debug", showAlways = false) {
	def isDbg = (settings?.appDebug == true) ? true : false
	if(showAlways) { Logger(msg, type) }
	else if (isDbg && !showAlways) { Logger(msg, type) }
}

def Logger(msg, type) {
	if(msg) {
		def labelstr = ""
		switch(type) {
			case "debug":
				log.debug "${msg}"
				break
			case "info":
				log.info "${msg}"
				break
			case "trace":
				log.trace "${msg}"
				break
			case "error":
				log.error "${msg}"
				break
			case "warn":
				log.warn "${msg}"
				break
			default:
				log.debug "${msg}"
				break
		}
	}
}

def getAppImg(imgName, on = null) 	{ return "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/images/$imgName" }

///////////////////////////////////////////////////////////////////////////////
/******************************************************************************
*                Application Help and License Info Variables                  *
*******************************************************************************/
///////////////////////////////////////////////////////////////////////////////
def appName() 		{ return "Efergy Manager" }
def childDevName()  { return "Efergy Engage Elite" }
def appAuthor() 	{ return "Anthony S." }
def appNamespace() 	{ return "tonesto7" }
def gitBranch()     { return "master" }
def appInfoDesc() 	{ return "${textAppName()}\n• ${textVersion()}\n• ${textModified()}" }
def textAppName()   { return "${appName()}" }
def textVersion()   { return "Version: ${appVersion()}" }
def textModified()  { return "Updated: ${appVerDate()}" }
def textAuthor()    { return "${appAuthor()}" }
def textNamespace() { return "${appNamespace()}" }
def textVerInfo()   { return "${appVerInfo()}" }
def textDonateLink(){ return "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=M88RC5J59BHTJ" }
def stIdeLink()     { return "https://graph.api.smartthings.com" }
def textCopyright() { return "Copyright© 2017 - Anthony S." }
def textDesc()      { return "This app will handle the connection to Efergy Servers and generate an API token and create the energy device. It will also update the data automatically for you every 30 seconds" }
def textHelp()      { return "" }
def textLicense() {
	return "Licensed under the Apache License, Version 2.0 (the 'License'); "+
		"you may not use this file except in compliance with the License. "+
		"You may obtain a copy of the License at"+
		"\n\n"+
		"    http://www.apache.org/licenses/LICENSE-2.0"+
		"\n\n"+
		"Unless required by applicable law or agreed to in writing, software "+
		"distributed under the License is distributed on an 'AS IS' BASIS, "+
		"WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. "+
		"See the License for the specific language governing permissions and "+
		"limitations under the License."
}
