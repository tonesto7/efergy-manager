/*
|*******************************************************************************************|
|    Application Name: Efergy Manager 3.0                                                   |
|    Author: Anthony S. (@tonesto7)                                                        |
|                                                                                           |
|*******************************************************************************************|
*/

import groovy.json.*
import groovy.time.*
import java.text.SimpleDateFormat

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

/* THINGS TO-DO..........
    Add offline Hub handling to verify that the hub is online instead of generating errors.
*/

def appVersion() { "3.0.1" }
def appVerDate() { "8-30-2016" }
def appVerInfo() {
    def str = ""

    str += "V3.0.1 (August 30th, 2016):"
    str += "\n▔▔▔▔▔▔▔▔▔▔▔"
    str += "\n • Alpha re-write."

    return str
}

preferences {
    page(name: "startPage")
    page(name: "loginPage")
    page(name: "mainPage")
    page(name: "prefsPage")
    page(name: "notifPrefPage")
    page(name: "uninstallPage")
    page(name: "hubInfoPage", content: "hubInfoPage", refreshTimeout:5)
    page(name: "readingInfoPage", content: "readingInfoPage", refreshTimeout:5)
    page(name: "infoPage")
    page(name: "changeLogPage")
    page(name: "savePage")
}

def startPage() {
    if(!atomicState.appInstalled) { atomicState.appInstalled = false }
    if(!atomicState?.showLogging) { atomicState?.showLogging = false }
    if(!atomicState?.cleanupComplete) { atomicState?.cleanupComplete = false }
    if (location?.timeZone?.ID.contains("America/")) { atomicState.currencySym = "\$" }
    if (atomicState.efergyAuthToken) { return mainPage() }
    else { return loginPage() }
}

/* Efergy Login Page */
def loginPage() {
    return dynamicPage(name: "loginPage", nextPage: mainPage, uninstall: false, install: false) {
        section("") {
            href "changeLogPage", title: "", description: "${appInfoDesc()}", image: getAppImg("efergy_512.png")
        }
        section("Efergy Login Page") {
            paragraph "Please enter your https://engage.efergy.com login credentials to generate you Authentication Token and install the device automatically for you."
            input("username", "email", title: "Username", description: "Efergy Username (email address)")
            input("password", "password", title: "Password", description: "Efergy Password")
            log.debug "login status: ${atomicState.loginStatus} - ${atomicState.loginDesc}"
            if (atomicState.loginStatus != null && atomicState.loginDesc != null && atomicState.loginStatus != "ok") {
                paragraph "${atomicState.loginDesc}... Please try again!!!"
            }
        }
    }
}

/* Preferences */
def mainPage() {
    if (!atomicState.efergyAuthToken) { getAuthToken() }
    if (!atomicState.pushTested) { atomicState.pushTested = false }
    if (!atomicState.currencySym) { atomicState.currencySym = "\$" }
    getCurrency()
    getApiData()
    updateWebStuff(true)
    def isDebug = settings?.showLogging ? true : false
    def notif = recipients ? true : false
    if (atomicState.loginStatus != "ok") { return loginPage() }
    def showUninstall = atomicState.appInstalled

    dynamicPage(name: "mainPage", uninstall: showUninstall, install: true) {
        if (atomicState.efergyAuthToken) {
            section("") {
                href "changeLogPage", title: "", description: "${appInfoDesc()}", image: getAppImg("efergy_512.png", true)
            }
            section("Efergy Hub:") {
                href "hubInfoPage", title:"View Hub Info", description: "Tap to view more...", image: getAppImg("St_hub.png")
                href "readingInfoPage", title:"View Reading Data", description: "Last Reading: \n${atomicState?.readingData?.readingUpdated}\n\nTap to view more...", image: getAppImg("power_meter.png")
            }

            section("Preferences:") {
                href "prefsPage", title: "App Preferences", description: "Tap to configure.\n\nDebug Logging: ${isDebug.toString().capitalize()}\nNotifications: ${notif.toString().capitalize()}", image: getAppImg("settings_icon.png")
            }

            section(" ") {
                href "infoPage", title:"App Info and Licensing", description: "Name: ${textAppName()}\nCreated by: Anthony S.\n${textVersion()} (${textModified()})\nTimeZone: ${location.timeZone.ID}\nCurrency: ${getCurrency()}\n\nTap to view more...",
                image: getAppImg("efergy_128.png")
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

//Defines the Preference Page
def prefsPage () {
    dynamicPage(name: "prefsPage", install: false) {
        section () {
            paragraph "App and Locale Preferences", image: getAppImg("settings_icon.png")
        }
        section("Currency Selection:"){
               input(name: "currencySym", type: "enum", title: "Select your Currency Symbol", options: ["\$", "£", "€"], defaultValue: "\$", submitOnChange: true,
                   image: getAppImg("currency_icon.png"))
               atomicState.currencySym = currencySym
        }
        // Set Notification Recipients
        section("Notifications:") {
			href "notifPrefPage", title: "Notifications", description: (getAppNotifConfDesc() ? "${getAppNotifConfDesc()}\n\nTap to modify..." : "Tap to configure..."), state: (getAppNotifConfDesc() ? "complete" : null),
					image: getAppImg("notification_icon.png")
		}

        section("Debug Logging:"){
            paragraph "This can help you when you are having issues with data not updating\n** This option generates alot of Log Entries!!! Only enable for troubleshooting **"
            paragraph "FYI... Enabling this also enables logging in the Child Device as well"
            input "showLogging", "bool", title: "Enable Debug Logging", required: false, displayDuringSetup: false, defaultValue: false, submitOnChange: true, image: getAppImg("log_icon.png")
            if(settings?.showLogging && !atomicState?.showLogging) {
           		atomicState.showLogging = true
           		log.info "Debug Logging Enabled!!!"
           	}
        	if(!settings?.showLogging && atomicState?.showLogging){
           		atomicState.showLogging = false
           		log.info "Debug Logging Disabled!!!"
           	}
        }
        refresh()
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
	def pName = getAutoType()
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
			paragraph appInfoDesc(), image: getAppImg("nest_manager%402x.png", true)
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
	def startInput = settings?."qStartInput"
	def startTime = settings?."qStartTime"
	def stopInput = settings?."qStopInput"
	def stopTime = settings?."qStopTime"
	def dayInput = settings?."quietDays"
	def modeInput = settings?."quietModes"
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

def readingInfoPage () {
    if (!atomicState?.hubData?.hubName) { refresh() }
    return dynamicPage(name: "readingInfoPage", refreshInterval: 15, install: false) {
         section ("Efergy Reading Information") {
            def rData = atomicState?.readingData
            def tData = atomicState?.tarrifData
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
    if (!atomicState?.hubData) { refresh() }
    return dynamicPage(name: "hubInfoPage", install: false) {
         section ("Efergy Hub Information") {
            if(atomicState?.hubData) {
                paragraph "Hub Name: " + atomicState?.hubData?.hubName
                paragraph "Hub ID: " + atomicState?.hubData?.hubId
                paragraph "Hub Mac Address: " + atomicState?.hubData?.hubMacAddr
                paragraph "Hub Status: " + atomicState?.hubData?.hubStatus
                paragraph "Hub Data TimeStamp: " + atomicState?.hubData?.hubTsHuman
                paragraph "Hub Type: " + atomicState?.hubData?.hubType
                paragraph "Hub Firmware: " + atomicState?.hubData?.hubVersion
            } else {
                paragraph "Nothing To Show..."
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
    initialize()
}

def uninstalled() {
    unschedule()
    addRemoveDevices(true)
}

def initialize() {
    refresh()
    addRemoveDevices()
    addSchedule()
    evtSubscribe()
    refresh()
}

def onAppTouch(event) {
    refresh()
}

//subscribes to the various location events and uses them to refresh the data if the scheduler gets stuck
private evtSubscribe() {
    subscribe(app, onAppTouch)
    subscribe(location, "sunrise", refresh)
    subscribe(location, "sunset", refresh)
    subscribe(location, "mode", refresh)
    subscribe(location, "sunriseTime", refresh)
    subscribe(location, "sunsetTime", refresh)
}

//Creates the child device if it not already there
private addRemoveDevices(uninst=false) {
    try {
        def devsInUse = []
        def dni = "Efergy Engage|" + atomicState?.hubData?.hubMacAddr
        def d = getChildDevice(dni)
        if(!uninst) {
            if(!d) {
                d = addChildDevice("tonesto7", "Efergy Engage Elite", dni, null, [name:"Efergy Engage Elite", label: "Efergy Engage Elite", completedSetup: true])
                d.take()
                LogAction("Successfully Created Child Device: ${d.displayName} (${dni})", "info", true)
                devsInUse += dni
            }
            else {
                LogAction("Device already created", "info", true)
            }
            def delete
            delete = getChildDevices().findAll { !devsInUse?.toString()?.contains(it?.deviceNetworkId) }
    		if(delete?.size() > 0) {
    			LogAction("Deleting: ${delete}, Removing ${delete.size()} devices", "debug", true)
    			delete.each { deleteChildDevice(it.deviceNetworkId) }
    		}
        } else {
            getChildDevices().each {
                deleteChildDevice(it.deviceNetworkId)
                log.info "Successfully Removed Child Device: ${it.displayName} (${it.deviceNetworkId})"
            }
        }
    } catch (ex) {
        log.error "addRemoveDevices exception: ${ex}", ex
    }
}

//Sends updated reading data to the Child Device
def updateDeviceData() {
    LogAction("updateDeviceData...", "trace", false)
    try {
        def api = !apiIssues() ? false : true
        def dbg = !settings?.showLogging ? false : true
        def devs = getAllChildDevices()
        if(devs?.size() > 0) {
            LogAction(" ", "trace", false)
            LogAction("--------------Sending Data to Device--------------", "trace", false)
            if(atomicState?.usageData && atomicState?.tariffData && atomicState?.readingData && atomicState?.hubData) {
                def devData = [
                        "usageData":atomicState?.usageData,
                        "tariffData":atomicState?.tariffData,
                        "readingData":atomicState?.readingData,
                        "hubData":atomicState?.hubData,
                        "monthName":atomicState?.monthName.toString(),
                        "debug":dbg,
                        "currencySym":atomicState?.currencySym?.toString(),
                        "tz":getTimeZone(),
                        "latestVer":latestDevVer()?.ver?.toString(),
                        "apiIssues":api
                ]
                devs?.each { dev ->
                    atomicState?.devVer = it?.devVer() ?: ""
                    dev?.generateEvent(devData) //parse received message from parent
                }
            } else {
                log.warn("updateDeviceData:  Missing required data.  Skipping Device Update...")
            }
        } else {
            log.warn("There aren't any devices installed.  Skipping Update...")
        }
    } catch (ex) {
        log.error "updateDeviceData exception: ${ex}"
    }
}

// refresh command
def refresh() {
    getLastRefreshSec()
    if (atomicState?.efergyAuthToken) {
        if (atomicState?.timeSinceRfsh > 30) {
            LogAction("","info", false)
            log.info "Refreshing Efergy Energy data from engage.efergy.com"
            getDayMonth()
            getApiData()

            updateDeviceData()
            LogAction("", "info", false)
            runIn(27, "refresh")
        }
        else if (atomicState?.timeSinceRfsh > 360 || !atomicState?.timeSinceRfsh) { checkSchedule() }
    }
    if(!atomicState?.cleanupComplete && (cleanupVer() != atomicState?.cleanupVer)) {
        runIn(15, "stateCleanup", [overwrite: false])
    }
    updateWebStuff()
    //notificationCheck() //Checks if a notification needs to be sent for a specific event
}

//Create Refresh schedule to refresh device data (Triggers roughly every 30 seconds)
private addSchedule() {
    //schedule("1/1 * * * * ?", "refresh") //Runs every 30 seconds to Refresh Data
    schedule("0 0/1 * * * ?", "refresh") //Runs every 1 minute to make sure that data is accurate
    //runIn(30, "refresh")
    //runIn(60, "refresh")
    runIn(130, "getLastRefreshSec")
    //schedule("0 0/1 * 1/1 * ? *", "getLastRefreshSec") //Runs every 1 minute to make sure that data is accurate
    runEvery5Minutes("checkSchedule")
    //runEvery30Minutes("checkSchedule")
}

def checkSchedule() {
    LogAction("Check Schedule has ran!","trace", false)
    getLastRefreshSec()
    def timeSince = atomicState.timeSinceRfsh ?: null
    if (timeSince > 360) {
        log.warn "It has been more than 5 minutes since last refresh!!!"
        log.debug "Scheduling Issue found... Re-initializing schedule... Data should resume refreshing in 30 seconds"
        addSchedule()
        return
    }
    else if (!timeSince) {
        log.warn "Hub TimeStamp Value was null..."
        log.debug "Re-initializing schedule... Data should resume refreshing in 30 seconds"
        addSchedule()
        return
    }
}

def apiIssues() {
	def result = state?.apiIssuesList.toString().contains("true") ? true : false
	if(result) {
		LogAction("Nest API Issues Detected... (${getDtNow()})", "warn", true)
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
    refresh()
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

//Checks for Time passed since last update and sends notification if enabled
def checkForNotify() {
    if(!atomicState.notifyDelayMin) { atomicState.notifyDelayMin = 50 }
    if(!atomicState.notifyAfterMin) { atomicState.notifyAfterMin = 60 }
    //LogAction("Delay X Min: " + atomicState?.notifyDelayMin, "info", true)
    //LogAction("After X Min: " + atomicState?.notifyAfterMin, "info", true)
    def delayVal = atomicState?.notifyDelayMin * 60
    def notifyVal = atomicState?.notifyAfterMin * 60
    def timeSince = getLastRefreshSec()

    if ((!atomicState?.lastNotifySeconds && !atomicState?.lastNotified) || (!atomicState?.lastNotifySeconds || !atomicState?.lastNotified)) {
        atomicState.lastNotifySeconds = 0
        atomicState.lastNotified = "Mon Jan 01 00:00:00 2000"
        LogAction("Error getting last Notified: ${atomicState?.lastNotified} - (${atomicState?.lastNotifySeconds} seconds ago)", "error", true)
        return
    }

    else if (atomicState?.lastNotifySeconds && atomicState?.lastNotified) {
        atomicState.lastNotifySeconds = getTimeDiffSeconds(atomicState?.lastNotified)
        LogAction("Last Notified: ${atomicState?.lastNotified} - (${atomicState?.lastNotifySeconds} seconds ago)", "info", true)
    }

    if (timeSince && timeSince > delayVal) {
        if (atomicState?.lastNotifySeconds < notifyVal){
            LogAction("Notification was sent ${atomicState?.lastNotifySeconds} seconds ago.  Waiting till after ${notifyVal} seconds before sending Notification again!", "info", true)
            return
        }
        else {
            atomicState.lastNotifySeconds = 0
            notifyOnNoUpdate(timeSince)
        }
    }
}

/************************************************************************************************
|								Push Notification Functions										|
*************************************************************************************************/
def pushStatus() { return (settings?.recipients || settings?.phone || settings?.usePush) ? (settings?.usePush ? "Push Enabled" : "Enabled") : null }
def getLastMsgSec() { return !atomicState?.lastMsgDt ? 100000 : getTimeDiffSeconds(atomicState?.lastMsgDt).toInteger() }
def getLastUpdMsgSec() { return !atomicState?.lastUpdMsgDt ? 100000 : getTimeDiffSeconds(atomicState?.lastUpdMsgDt).toInteger() }
def getLastMisPollMsgSec() { return !atomicState?.lastMisPollMsgDt ? 100000 : getTimeDiffSeconds(atomicState?.lastMisPollMsgDt).toInteger() }
def getRecipientsSize() { return !settings.recipients ? 0 : settings?.recipients.size() }

def latestDevVer()    { return atomicState?.appData?.updater?.versions?.dev ?: "unknown" }
def getOk2Notify() { return (daysOk(settings?."quietDays") && notificationTimeOk() && modesOk(settings?."quietModes")) }
def isMissedPoll() { return (getLastDevicePollSec() > atomicState?.misPollNotifyWaitVal.toInteger()) ? true : false }

def notificationCheck() {
	if((settings?.recipients || settings?.usePush) && getOk2Notify()) {
		if (sendMissedPollMsg) { missedPollNotify() }
		if (sendAppUpdateMsg && !appDevType()) { appUpdateNotify() }
	}
}

def missedPollNotify() {
	if(isMissedPoll()) {
		if(getOk2Notify() && (getLastMisPollMsgSec() > atomicState?.misPollNotifyMsgWaitVal.toInteger())) {
			sendMsg("Warning", "${app.name} has not refreshed data in the last (${getLastDevicePollSec()}) seconds.  Please try refreshing manually.")
			atomicState?.lastMisPollMsgDt = getDtNow()
		}
	}
}

def appUpdateNotify() {
	def appUpd = isAppUpdateAvail()
	def devUpd = isProtUpdateAvail()
	if((appUpd || devUpd) && (getLastUpdMsgSec() > atomicState?.updNotifyWaitVal.toInteger())) {
		def str = ""
		str += !appUpd ? "" : "\nManager App: v${atomicState?.appData?.updater?.versions?.app?.ver?.toString()}"
		str += !devUpd ? "" : "\nElite Device: v${atomicState?.appData?.updater?.versions?.dev?.ver?.toString()}"
		sendMsg("Info", "Efergy Manager Update(s) are Available:${str}...  \n\nPlease visit the IDE to Update your code...")
		atomicState?.lastUpdMsgDt = getDtNow()
	}
}

//Sends the actual Push Notification
def notifyOnNoUpdate(Integer timeSince) {
    def now = new Date()
    def notifiedDt = new SimpleDateFormat("EE MMM dd HH:mm:ss yyyy")
    atomicState.lastNotified = notifiedDt?.format(now)

    def message = "Something is wrong!!! Efergy Device has not updated in the last ${timeSince} seconds..."
    sendNotify(message)
}

def sendMsg(msgType, msg, people = null, sms = null, push = null, brdcast = null) {
	try {
		if(!getOk2Notify()) {
			LogAction("No Notifications will be sent during Quiet Time...", "info", true)
		} else {
			def newMsg = "${msgType}: ${msg}"
			if(!brdcast) {
				def who = people ? people : settings?.recipients
				if (location.contactBookEnabled) {
					if(who) {
						sendNotificationToContacts(newMsg, who)
						atomicState?.lastMsg = newMsg
						atomicState?.lastMsgDt = getDtNow()
						LogAction("Push Message Sent: ${atomicState?.lastMsgDt}", "debug", true)
					}
				} else {
					LogAction("ContactBook is NOT Enabled on your SmartThings Account...", "warn", true)
					if (push) {
						sendPush(newMsg)
						atomicState?.lastMsg = newMsg
						atomicState?.lastMsgDt = getDtNow()
						LogAction("Push Message Sent: ${atomicState?.lastMsgDt}", "debug", true)
					}
					else if (sms) {
						sendSms(sms, newMsg)
						atomicState?.lastMsg = newMsg
						atomicState?.lastMsgDt = getDtNow()
						LogAction("SMS Message Sent: ${atomicState?.lastMsgDt}", "debug", true)
					}
				}
			} else {
				sendPushMessage(newMsg)
				LogAction("Broadcast Message Sent: ${newMsg} - ${atomicState?.lastMsgDt}", "debug", true)
			}
		}
	} catch (ex) {
		log.error "sendMsg Exception:", ex
	}
}

def getLastWebUpdSec() { return !atomicState?.lastWebUpdDt ? 100000 : getTimeDiffSeconds(atomicState?.lastWebUpdDt).toInteger() }

def updateWebStuff(now = false) {
	//log.trace "updateWebStuff..."
	if (!atomicState?.appData || (getLastWebUpdSec() > (3600*4))) {
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

def getLastRefreshSec() {
    if(atomicState?.hubData?.hubTsHuman) {
        atomicState.timeSinceRfsh = getTimeDiffSeconds(atomicState?.hubData?.hubTsHuman)
        LogAction("TimeSinceRefresh: ${atomicState.timeSinceRfsh} seconds", "info", false)
    }
    runIn(130, "getLastRefreshSec")
}

//Returns time difference is seconds
def oldgetTimeDiffSeconds(String startDate) {
    try {
        def now = new Date()
        def startDt = new SimpleDateFormat("EE MMM dd HH:mm:ss yyyy").parse(startDate)
        def diff = now.getTime() - startDt.getTime()
        def diffSeconds = (int) (long) diff / 1000
        //def diffMinutes = (int) (long) diff / 60000
        return diffSeconds
    }
    catch (ex) {
        log.error "getTimeDiffSeconds Exception: ${ex}",ex
        return 10000
    }
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
    getUsageData()
    getTariffData()
    getReadingData()
    getHubData()
}

def getDtNow() {
	def now = new Date()
	return formatDt(now)
}
// Get extended energy metrics
def getUsageData() {
    try {
        def usageData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEstCombined")
        if(usageData && (usageData != atomicState?.lastUsageData)) {
            atomicState?.lastUsageData = usageData
            def data = [:]
            atomicState?.usageData = [:]
            data["todayUsage"] = usageData?.day_kwh.estimate ?: null
            data["todayCost"] = usageData?.day_tariff?.estimate ?: null
            data["monthUsage"] = usageData?.month_kwh?.previousSum ?: null
            data["monthCost"] = usageData?.month_tariff?.previousSum ?: null
            data["monthEst"] = usageData?.month_tariff?.estimate ?: null
            data["monthBudget"] = usageData?.month_budget ?: null
            LogAction("UsageData: $data", "trace", false)
            atomicState?.usageData = data
        }
    }
    catch (ex) { log.error "getUsageData Exception: ${ex}", ex }
}

// Get tariff energy metrics
def getTariffData() {
    try {
        def tariffData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getTariff")
        def tData = tariffData[0]
        if(tData && (tData != atomicState?.lastTariffData)) {
            atomicState?.lastTariffData = tData
            def data = [:]
            atomicState?.tariffData = [:]
            data["tariffUtility"] = tData?.tariff?.plan[0].utility.toString() ?: null
            data["tariffName"] = tData?.tariff?.plan[0].name.toString() ?: null
            data["tariffRate"] = tData?.tariff?.plan[0]?.plan[0]?.planDetail[0]?.rate ?: null
            LogAction("TariffData: ${data}", "debug", false)
            atomicState?.tariffData = data
        }
    }
    catch (ex) { log.error "getTariffData Exception: ${ex}", ex }
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
    catch (e) { log.error "getReadingData Exception: ${e}", ex }
}

// Returns Hub Device Status Info
private getHubData() {
    try {
        def hubData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getStatus")
        if(hubData && (hubData != atomicState?.lastHubData)) {
            atomicState?.lastHubData = hubData
            def data = [:]
            atomicState?.hubData = [:]
            data["hubId"] = hubData?.hid?.toString() ?: null
            data["hubMacAddr"] = hubData?.listOfMacs?.mac[0]?.toString() ?: null
            data["hubStatus"] = hubData?.listOfMacs?.status[0]?.toString() ?: null
            data["hubTsHuman"] = hubData?.listOfMacs?.tsHuman[0]?.toString() ?: null
            data["hubType"] = hubData?.listOfMacs?.type[0]?.toString() ?: null
            data["hubVersion"] = hubData?.listOfMacs?.version[0]?.toString() ?: null
            data["hubName"] = getHubName(hubData?.listOfMacs?.type[0].toString()) ?: null
            LogAction("HubData: $data", "trace", false)
            atomicState?.hubData = data
        }
    } catch (ex) { log.error "getHubData Exception: ${ex}", ex }
}

def getEfergyData(url, pathStr) {
    LogAction("getEfergyData($url, $pathStr)", "trace", false)
    try {
        def params = [
            uri: url,
            path: pathStr,
            query: ["token": atomicState.efergyAuthToken],
            contentType: 'application/json'
        ]
        httpGet(params) { resp ->
            if(resp.data) {
                //log.debug "getEfergyData Response: ${resp?.data}"
                apiIssueEvent(false)
                return resp?.data
            }
        }
    } catch (ex) {
        apiIssueEvent(false)
        log.error "getEfergyData Exception: ${ex}"
    }
}

def getTimeZone() {
    def tz = null
    if (location?.timeZone) { tz = location?.timeZone }
    if(!tz) { log.warn("getTimeZone: SmartThings TimeZone is not found on your account...") }
    return tz
}

def formatDt(dt) {
    def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
    if(getTimeZone()) { tf.setTimeZone(getTimeZone()) }
    else {
        log.warn "SmartThings TimeZone is not found or is not set... Please Try to open your ST location and Press Save..."
    }
    return tf.format(dt)
}

//Returns time differences is seconds
def getTimeDiffSeconds(lastDate) {
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
        log.error "getTimeDiffSeconds Exception: ${ex}"
        return 10000
    }
}
def cleanupVer() { return 1 }

def stateCleanup() {
    state.remove("cidType")
    state.remove("cidUnit")
    state.remove("energyReading")
    state.remove("hubId")
    state.remove("hubMacAddr")
    state.remove("hubName")
    state.remove("hubStatus")
    state.remove("hubTsHuman")
    state.remove("hubType")
    state.remove("hubVersion")
    state.remove("monthBudget")
    state.remove("monthCost")
    state.remove("monthEst")
    state.remove("monthUsage")
    state.remove("readingDt")
    state.remove("readingUpdated")
    state.remove("tariffRate")
    state.remove("todayCost")
    state.remove("todayUsage")

    atomicState?.cleanupComplete = true
    atomicState?.cleanupVer = cleanupVer()
}

def LogTrace(msg) {
    def trOn = advAppDebug ? true : false
    if(trOn) { Logger(msg, "trace") }
}

def LogAction(msg, type = "debug", showAlways = false) {
	def isDbg = (settings?.showLogging == true) ? true : false
	if(showAlways) { Logger(msg, type) }
	else if (isDbg && !showAlways) { Logger(msg, type) }
}

def Logger(msg, type) {
	if(msg&&type) {
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
	else { log.error "Logger Error - type: ${type} | msg: ${msg}" }
}

def getAppImg(imgName, on = null) 	{ return "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/images/$imgName" }

///////////////////////////////////////////////////////////////////////////////
/******************************************************************************
*                Application Help and License Info Variables                  *
*******************************************************************************/
///////////////////////////////////////////////////////////////////////////////
private def appName() 		{ return "Efergy Manager${appDevName()}" }
private def appAuthor() 	{ return "Anthony S." }
private def appNamespace() 	{ return "tonesto7" }
private def gitBranch()     { return "master" }
private def appDevType()    { return false }
private def appDevName()    { return appDevType() ? " (Dev)" : "" }
private def appInfoDesc() 	{ return "${textAppName()}\n• ${textVersion()}\n• ${textModified()}" }
private def textAppName()   { return "${appName()}" }
private def textVersion()   { return "Version: ${appVersion()}" }
private def textModified()  { return "Updated: ${appVerDate()}" }
private def textAuthor()    { return "${appAuthor()}" }
private def textNamespace() { return "${appNamespace()}" }
private def textVerInfo()   { return "${appVerInfo()}" }
private def textDonateLink(){ return "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=2CJEVN439EAWS" }
private def stIdeLink()     { return "https://graph.api.smartthings.com" }
private def textCopyright() { return "Copyright© 2016 - Anthony S." }
private def textDesc()      { return "This app will handle the connection to Efergy Servers and generate an API token and create the energy device. It will also update the data automatically for you every 30 seconds" }
private def textHelp()      { return "" }
private def textLicense() {
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
