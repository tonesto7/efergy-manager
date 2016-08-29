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

def appVersion() { "3.0.0" }
def appVerDate() { "6-24-2016" }
def appVerInfo() {
    def str = ""

    str += "V3.0.0 (August 29th, 2016):"
    str += "\n▔▔▔▔▔▔▔▔▔▔▔"
    str += "\n • Alpha re-write."

    return str
}

preferences {
    page(name: "startPage")
    page(name: "loginPage")
    page(name: "mainPage")
    page(name: "prefsPage")
    page(name: "hubInfoPage", content: "hubInfoPage", refreshTimeout:5)
    page(name: "readingInfoPage", content: "readingInfoPage", refreshTimeout:5)
    page(name: "infoPage")
    page(name: "changeLogPage")
    page(name: "savePage")
}

def startPage() {
    if(!atomicState.appInstalled) { atomicState.appInstalled = false }
    if(!atomicState?.showLogging) { atomicState?.showLogging = false }
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
    runTest()
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

            section(" ", mobileOnly: true) {
                //App Details and Licensing Page
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

        section("Notifications:"){
            input("recipients", "contact", title: "Send notifications to", required: false, submitOnChange: true, image: getAppImg("notification_icon.png")) {
                input "phone", "phone", title: "Warn with text message (optional)", description: "Phone Number", required: false, submitOnChange: true
            }
            if(recipients) {
                if((settings.recipients != recipients && recipients) || !atomicState.pushTested) {
                    sendNotify("Push Notification Test Successful... Test is successful")
                    atomicState.pushTested = true
                }
                else { atomicState.pushTested = true }
            }
            else { atomicState.pushTested = false }
        }

        // Set Notification Recipients
        if (location.contactBookEnabled && recipients) {
            section("Notify Values...", hidden: true, hideable: true) {
                input "notifyAfterMin", "number", title: "Send Notification after (X) minutes of no updates", required: false, defaultValue: "60", submitOnChange: true
                   input "notifyDelayMin", "number", title: "Only Send Notification every (x) minutes...", required: false, defaultValue: "50", submitOnChange: true
                   atomicState.notifyAfterMin = notifyAfterMin
                   atomicState.notifyDelayMin = notifyDelayMin
            }
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

def changeLogPage () {
    dynamicPage(name: "changeLogPage", title: "View Change Info", install: false) {
        section("App Revision History:") {
            paragraph appVerInfo()
        }
    }
}

//Defines the Help Page
def infoPage () {
    dynamicPage(name: "infoPage", install: false) {
        section() {
            paragraph "App Details and Licensing", image: getAppImg("info_icon.png")
        }
        section("About This App:") {
            paragraph "Name: ${textAppName()}\nCreated by: Anthony S.\n${textVersion()}\n${textModified()}\nGithub: @tonesto7\n\n${textDesc()}",
                image: getAppImg("efergy_128.png")
        }
        section("App Revision History:") {
            paragraph appVerInfo()
        }
        section("Licensing Info:") {
            paragraph "${textCopyright()}\n${textLicense()}"
        }
    }
}

/* Initialization */
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
        def dni = "Efergy Engage - DEV|" + atomicState?.hubData?.hubMacAddr
        def d = getChildDevice(dni)
        if(!uninst) {
            if(!d) {
                d = addChildDevice("tonesto7", "Efergy Engage Elite - DEV", dni, null, [name:"Efergy Engage Elite - DEV", label: "Efergy Engage Elite - DEV", completedSetup: true])
                d.take()
                Logger("Successfully Created Child Device: ${d.displayName} (${dni})")
                devsInUse += dni
            }
            else {
                Logger("Device already created")
            }
            def delete
            delete = getChildDevices().findAll { !devsInUse?.toString()?.contains(it?.deviceNetworkId) }
    		if(delete?.size() > 0) {
    			Logger("Deleting: ${delete}, Removing ${delete.size()} devices", "debug")
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
    Logger("updateDeviceData...", "trace")
    try {
        def devs = getAllChildDevices()
        if(devs?.size() > 0) {
            Logger(" ")
            Logger("--------------Sending Data to Device--------------")
            if(atomicState?.usageData && atomicState?.tariffData && atomicState?.readingData && atomicState?.hubData) {
                def devData = [
                        "usageData":atomicState?.usageData,
                        "tariffData":atomicState?.tariffData,
                        "readingData":atomicState?.readingData,
                        "hubData":atomicState?.hubData,
                        "monthName":atomicState?.monthName.toString(),
                        "showLogging":settings?.showLogging,
                        "currencySym":atomicState?.currencySym?.toString(),
                        "tz":getTimeZone()
                ]
                devs?.each { dev ->
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
            Logger("","info")
            log.info "Refreshing Efergy Energy data from engage.efergy.com"
            getDayMonth()
            getApiData()
            //If any people have been added for notification then it will check to see if it should notify
            if (recipients) { checkForNotify() }

            updateDeviceData()
            Logger("", "info")
            runIn(27, "refresh")
        }
        else if (atomicState?.timeSinceRfsh > 360 || !atomicState?.timeSinceRfsh) { checkSchedule() }
    }
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
    Logger("Check Schedule has ran!")
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

// Get Efergy Authentication Token
private def getAuthToken() {
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
    //Logger("Delay X Min: " + atomicState?.notifyDelayMin)
    //Logger("After X Min: " + atomicState?.notifyAfterMin)
    def delayVal = atomicState?.notifyDelayMin * 60
    def notifyVal = atomicState?.notifyAfterMin * 60
    def timeSince = getLastRefreshSec()

    if ((!atomicState?.lastNotifySeconds && !atomicState?.lastNotified) || (!atomicState?.lastNotifySeconds || !atomicState?.lastNotified)) {
        atomicState.lastNotifySeconds = 0
        atomicState.lastNotified = "Mon Jan 01 00:00:00 2000"
        Logger("Error getting last Notified: ${atomicState?.lastNotified} - (${atomicState?.lastNotifySeconds} seconds ago)")
        return
    }

    else if (atomicState?.lastNotifySeconds && atomicState?.lastNotified) {
        atomicState.lastNotifySeconds = getTimeDiffSeconds(atomicState?.lastNotified)
        Logger("Last Notified: ${atomicState?.lastNotified} - (${atomicState?.lastNotifySeconds} seconds ago)")
    }

    if (timeSince && timeSince > delayVal) {
        if (atomicState?.lastNotifySeconds < notifyVal){
            Logger("Notification was sent ${atomicState?.lastNotifySeconds} seconds ago.  Waiting till after ${notifyVal} seconds before sending Notification again!")
            return
        }
        else {
            atomicState.lastNotifySeconds = 0
            notifyOnNoUpdate(timeSince)
        }
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

private def sendNotify(msg) {
    if (location.contactBookEnabled && recipients) {
        sendNotificationToContacts(msg, recipients)
    } else {
        Logger("contact book not enabled")
        if (phone) {
            sendSms(phone, msg)
        }
    }
}

def getLastRefreshSec() {
    if(atomicState?.hubData?.hubTsHuman) {
        atomicState.timeSinceRfsh = getTimeDiffSeconds(atomicState?.hubData?.hubTsHuman)
        Logger("TimeSinceRefresh: ${atomicState.timeSinceRfsh} seconds")
    }
    runIn(130, "getLastRefreshSec")
}

//Returns time difference is seconds
def getTimeDiffSeconds(String startDate) {
    try {
        def now = new Date()
        def startDt = new SimpleDateFormat("EE MMM dd HH:mm:ss yyyy").parse(startDate)
        def diff = now.getTime() - startDt.getTime()
        def diffSeconds = (int) (long) diff / 1000
        //def diffMinutes = (int) (long) diff / 60000
        return diffSeconds
    }
    catch (ex) {
        log.debug "getTimeDiffSeconds Exception: ${ex}"
        return 10000
    }
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


def runTest() {
    //log.trace "runTest..."
    getApiData()
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

            Logger("UsageData: $data", "trace")
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
            Logger("TariffData: ${data}", "trace")
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
            Logger("ReadingData: ${data}", "trace")
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
            Logger("HubData: $data", "trace")
            atomicState?.hubData = data
        }
    } catch (ex) { log.error "getHubData Exception: ${ex}", ex }
}

def getEfergyData(url, pathStr) {
    //log.trace "getEfergyData($url, $pathStr)"
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
                return resp?.data
            }
        }
    } catch (ex) {
        log.error "getEfergyData Exception: ${ex}"
    }
}

def getTimeZone() {
    def tz = null
    if (location?.timeZone) { tz = location?.timeZone }
    //else { tz = TimeZone.getTimeZone(getNestTimeZone()) }
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

def LogTrace(msg) {
    def trOn = advAppDebug ? true : false
    if(trOn) { Logger(msg, "trace") }
}

def LogAction(msg, type = "debug", showAlways = false) {
    try {
        def isDbg = parent ? ((atomicState?.showDebug || showDebug)  ? true : false) : (appDebug ? true : false)
        if(showAlways) { Logger(msg, type) }

        else if (isDbg && !showAlways) { Logger(msg, type) }
    } catch (ex) {
        log.error("LogAction Exception: ${ex}")
    }
}

def Logger(msg, type = "") {
    if(msg && type && settings?.showLogging) {
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
