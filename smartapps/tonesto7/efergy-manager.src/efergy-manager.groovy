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
    iconUrl:   "https://dl.dropboxusercontent.com/s/daakzncm7zdzc4w/efergy_128.png",
    iconX2Url: "https://dl.dropboxusercontent.com/s/ysqycalevj2rvtp/efergy_256.png",
    iconX3Url: "https://dl.dropboxusercontent.com/s/56740lxra2qkqix/efergy_512.png",
    singleInstance: false,
    oauth: true)

/* THINGS TO-DO..........
    Add offline Hub handling to verify that the hub is online instead of generating errors.
*/

def appVersion() { "3.0.0" }
def appVerDate() { "6-24-2016" }
def appVerInfo() {
    def str = ""

    str += "V3.0.0 (June 24th, 2016):"
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
    if(!state.appInstalled) { state.appInstalled = false }
    if(!state.showLogging) { state.showLogging = false }
    if (location?.timeZone?.ID.contains("America/")) { state.currencySym = "\$" }
    if (state.efergyAuthToken) { return mainPage() }
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
            log.debug "login status: ${state.loginStatus} - ${state.loginDesc}"
            if (state.loginStatus != null && state.loginDesc != null && state.loginStatus != "ok") {
                paragraph "${state.loginDesc}... Please try again!!!"
            }
        }
    }
}

/* Preferences */
def mainPage() {
    if (!state.efergyAuthToken) { getAuthToken() } 
    if (!state.pushTested) { state.pushTested = false }
    if (!state.currencySym) { state.currencySym = "\$" }
    getCurrency()
    def isDebug = state.showLogging ? true : false
    def notif = recipients ? true : false
    if (state.loginStatus != "ok") { return loginPage() }
    def showUninstall = state.appInstalled

    dynamicPage(name: "mainPage", uninstall: showUninstall, install: true) {
        if (state.efergyAuthToken) {
            section("") {
                href "changeLogPage", title: "", description: "${appInfoDesc()}", image: getAppImg("efergy_512.png", true)
            }
            section("Efergy Hub:") { 
                href "hubInfoPage", title:"View Hub Info", description: "Tap to view more...", image: getAppImg("St_hub.png")
                href "readingInfoPage", title:"View Reading Data", description: "Last Reading: \n${state.readingUpdated}\n\nTap to view more...", image: getAppImg("power_meter.png")
            }
            
            section("Preferences:") {
                href "prefsPage", title: "App Preferences", description: "Tap to configure.\n\nDebug Logging: ${isDebug.toString().capitalize()}\nNotifications: ${notif.toString().capitalize()}", image: getAppImg("settings_icon.png")
            }
            
            section(" ", mobileOnly: true) {
                //App Details and Licensing Page
                href "infoPage", title:"App Info and Licensing", description: "Name: ${textAppName()}\nParent App: ${parent.appName()}\nCreated by: Anthony S.\n${textVersion()} (${textModified()})\nTimeZone: ${location.timeZone.ID}\nCurrency: ${getCurrency()}\n\nTap to view more...", 
                image: getAppImg("efergy_128.png")
            }
        }
        
        if (!state.efergyAuthToken) {
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
               state.currencySym = currencySym
        }
        
        section("Notifications:"){	
            input("recipients", "contact", title: "Send notifications to", required: false, submitOnChange: true, image: getAppImg("notification_icon.png")) {
                input "phone", "phone", title: "Warn with text message (optional)", description: "Phone Number", required: false, submitOnChange: true
            }
            if(recipients) { 
                if((settings.recipients != recipients && recipients) || !state.pushTested) {
                    sendNotify("Push Notification Test Successful... Test is successful") 
                    state.pushTested = true
                }
                else { state.pushTested = true }
            }
            else { state.pushTested = false }
        }
        
        // Set Notification Recipients  
        if (location.contactBookEnabled && recipients) {
            section("Notify Values...", hidden: true, hideable: true) { 
                input "notifyAfterMin", "number", title: "Send Notification after (X) minutes of no updates", required: false, defaultValue: "60", submitOnChange: true
                   input "notifyDelayMin", "number", title: "Only Send Notification every (x) minutes...", required: false, defaultValue: "50", submitOnChange: true
                   state.notifyAfterMin = notifyAfterMin
                   state.notifyDelayMin = notifyDelayMin         
            }
        }
        
        section("Debug Logging:"){
            paragraph "This can help you when you are having issues with data not updating\n** This option generates alot of Log Entries!!! Only enable for troubleshooting **"
            paragraph "FYI... Enabling this also enables logging in the Child Device as well"
            input "showLogging", "bool", title: "Enable Debug Logging", required: false, displayDuringSetup: false, defaultValue: false, submitOnChange: true, image: getAppImg("log_icon.png")
            if(showLogging && !state.showLogging) { 
                   state.showLogging = true
                   log.info "Debug Logging Enabled!!!"
               }
            if(!showLogging && state.showLogging){ 
                   state.showLogging = false 
                   log.info "Debug Logging Disabled!!!"
               }
        }
        refresh()
    }
}

def readingInfoPage () {
    if (!state.hubName) { refresh() }
    return dynamicPage(name: "readingInfoPage", install: false) {
         section ("Efergy Reading Information") {
            paragraph "Current Power Reading: " + state.powerReading
            paragraph "Current Energy Reading: " + state.powerReading
            paragraph "Tariff Rate: " + state.currencySym + state.tariffRate
            paragraph "Today's Usage: " + state.currencySym + state.todayCost + " (${state.todayUsage} kWH"
            paragraph "${state.monthName} Usage: " + state.currencySym + state.monthCost + " (${state.monthUsage} kWH"
            paragraph "Month Cost Estimate: " + state.currencySym + state.monthBudget
        }
    }
}

def hubInfoPage () {
    if (!state.hubName) { refresh() }
    return dynamicPage(name: "hubInfoPage", install: false) {
         section ("Efergy Hub Information") {
            paragraph "Hub Name: " + state.hubName
            paragraph "Hub ID: " + state.hubId
            paragraph "Hub Mac Address: " + state.hubMacAddr
            paragraph "Hub Status: " + state.hubStatus
            paragraph "Hub Data TimeStamp: " + state.hubTsHuman
            paragraph "Hub Type: " + state.hubType
            paragraph "Hub Firmware: " + state.hubVersion
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
    state.appInstalled = true
    parent.efergyAppInst(true)
    sendNotificationEvent("${textAppName()} - ${appVersion()} (${appVerDate()}) installed...")
    log.info "${textAppName()} - ${appVersion()} (${appVerDate()}) installed..."
    initialize() 
}

def updated() { 
    if (!state.appInstalled) { state.appInstalled = true }
    sendNotificationEvent("${textAppName()} - ${appVersion()} (${appVerDate()}) updated...")
    log.info "${textAppName()} - ${appVersion()} (${appVerDate()}) updated..."
    unsubscribe()
    initialize() 
}

def uninstalled() {
    parent.efergyAppInst(false)
    unschedule()
    removeChildDevices(getChildDevices())
}
    
def initialize() {    
    refresh()
    addDevice()	
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
private addDevice() {
    try {
        def dni = "Efergy Engage|" + state.hubMacAddr
        state.dni = dni
        def d = getChildDevice(dni)
        if(!d) {
            d = addChildDevice("tonesto7", "Efergy Engage Elite - DEV", dni, null, [name:"Efergy Engage Elite - DEV", label: "Efergy Engage Elite - DEV", completedSetup: true])
            d.take()
            logWriter("Successfully Created Child Device: ${d.displayName} (${dni})")
        } 
        else {
            logWriter("Device already created")
        }
    } catch (ex) {
        log.error "addDevice exception: ${ex}"
    }
}

private removeChildDevices(delete) {
    try {
        delete.each {
            deleteChildDevice(it.deviceNetworkId)
            log.info "Successfully Removed Child Device: ${it.displayName} (${it.deviceNetworkId})"
        }
    } catch (ex) { logWriter("There was an error (${ex}) when trying to delete the child device") }
}

//Sends updated reading data to the Child Device
def updateDeviceData() {
    try {
        logWriter(" ")
        logWriter("--------------Sending Data to Device--------------")

        def devData = ["usageData":atomicState?.usageData, "tarrifData":atomicState?.tarrifData, "readingData":atomicState?.readingData, "hubData":atomicState?.hubData, "monthName":state?.monthName.toString(), "showLogging":state?.showLogging, 
                        "currencySym":state?.currencySym?.toString(), "tz":getTimeZone()]
        //LogTrace("UpdateChildData >> Thermostat id: ${devId} | data: ${tData}")

        getAllChildDevices().each { 
            it?.generateEvent(devData) //parse received message from parent
        }
    } catch (ex) {
        log.error "updateDeviceData exception: ${ex}"
    }
}

// refresh command
def refresh() {
    GetLastRefrshSec()
    if (state.efergyAuthToken) {
        if (state?.timeSinceRfsh > 30) {
            logWriter("")	
            log.info "Refreshing Efergy Energy data from engage.efergy.com"
            getDayMonth()
            getApiData()
            //If any people have been added for notification then it will check to see if it should notify
            if (recipients) { checkForNotify() }
   
            updateDeviceData()
            logWriter("")
            runIn(27, "refresh")
        }
        else if (state?.timeSinceRfsh > 360 || !state?.timeSinceRfsh) { checkSchedule() }
    }
}

//Create Refresh schedule to refresh device data (Triggers roughly every 30 seconds)
private addSchedule() {
    //schedule("1/1 * * * * ?", "refresh") //Runs every 30 seconds to Refresh Data
    schedule("0 0/1 * * * ?", "refresh") //Runs every 1 minute to make sure that data is accurate
    //runIn(30, "refresh")
    //runIn(60, "refresh")
    runIn(130, "GetLastRefrshSec")
    //schedule("0 0/1 * 1/1 * ? *", "GetLastRefrshSec") //Runs every 1 minute to make sure that data is accurate
    runEvery5Minutes("checkSchedule")
    //runEvery30Minutes("checkSchedule")
}

def checkSchedule() {
    logWriter("Check Schedule has ran!")	
    GetLastRefrshSec()
    def timeSince = state.timeSinceRfsh ?: null 
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
            state?.loginStatus = "ok"
            state?.loginDesc = resp?.data?.desc
            state?.efergyAuthToken = resp?.data?.token
        }
        else { 
            state.loginStatus = resp?.data?.status
            state.loginDesc = resp?.data?.desc
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
        state.monthName = month
        state.dayOfWeek = day
    } 
}

def getCurrency() {
    def unitName = ""
    switch (state.currencySym) {
        case '$':
            unitName = "US Dollar (\$)"
            state.centSym = "¢" 
        break
        case '£':
            unitName = "British Pound (£)"
            state.centSym = "p"
        break
        case '€':
            unitName = "Euro Dollar (€)"
            state.centSym = "¢" 
        break
        default:
            unitName = "unknown"
            state.centSym = "¢"
        break
    }
    return unitName
}

//Checks for Time passed since last update and sends notification if enabled
def checkForNotify() {
    if(!state.notifyDelayMin) { state.notifyDelayMin = 50 }
    if(!state.notifyAfterMin) { state.notifyAfterMin = 60 }
    //logWriter("Delay X Min: " + state.notifyDelayMin)
    //logWriter("After X Min: " + state.notifyAfterMin)
    def delayVal = state.notifyDelayMin * 60
    def notifyVal = state.notifyAfterMin * 60
    def timeSince = GetLastRefrshSec()
    
    if ((state.lastNotifySeconds == null && state.lastNotified == null) || (state.lastNotifySeconds == null || state.lastNotified == null)) {
        state.lastNotifySeconds = 0
        state.lastNotified = "Mon Jan 01 00:00:00 2000"
        logWriter("Error getting last Notified: ${state.lastNotified} - (${state.lastNotifySeconds} seconds ago)")
        return
    }
    
    else if (state.lastNotifySeconds && state.lastNotified) {
        state.lastNotifySeconds = GetTimeDiffSeconds(state.lastNotified)
        logWriter("Last Notified: ${state.lastNotified} - (${state.lastNotifySeconds} seconds ago)")
    }

    if (timeSince > delayVal) {
        if (state.lastNotifySeconds < notifyVal){
            logWriter("Notification was sent ${state.lastNotifySeconds} seconds ago.  Waiting till after ${notifyVal} seconds before sending Notification again!")
            return
        }
        else {
            state.lastNotifySeconds = 0
            NotifyOnNoUpdate(timeSince)
        }
    }
}

//Sends the actual Push Notification
def NotifyOnNoUpdate(Integer timeSince) {
    def now = new Date()
    def notifiedDt = new SimpleDateFormat("EE MMM dd HH:mm:ss yyyy")
    state.lastNotified = notifiedDt.format(now)
        
    def message = "Something is wrong!!! Efergy Device has not updated in the last ${timeSince} seconds..."
    sendNotify(message)
}

private def sendNotify(msg) {
    if (location.contactBookEnabled && recipients) {
        sendNotificationToContacts(msg, recipients)
    } else {
        logWriter("contact book not enabled")
        if (phone) {
            sendSms(phone, msg)
        }
    }
}

def GetLastRefrshSec() {
    state.timeSinceRfsh = GetTimeDiffSeconds(state.hubTsHuman)
    logWriter("TimeSinceRefresh: ${state.timeSinceRfsh} seconds")
    runIn(130, "GetLastRefrshSec")
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
    catch (e) {
        log.debug "Exception in GetTimeDiffSeconds: ${e}"
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
    state.hubName = hubName
}

def getApiData() {
    atomicState?.usageData = getUsageData()
    atomicState?.tarrifData = getTariffData()
    atomicState?.readingData = getReadingData()
    atomicState?.hubData = getHubData()
}

// Get extended energy metrics
def getUsageData() {
    try {
        def usageData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getEstCombined")
        if (usageData) {
            
            //Sends extended metrics to tiles
            state.todayUsage = "${estUseResp?.data?.day_kwh.estimate}"
            state.todayCost = "${estUseResp?.data?.day_tariff?.estimate}"
            state.monthUsage = "${estUseResp?.data?.month_kwh?.previousSum}"
            state.monthCost = "${estUseResp?.data?.month_tariff?.previousSum}"
            state.monthEst = "${estUseResp?.data?.month_tariff?.estimate}"
            state.monthBudget = "${estUseResp?.data?.month_budget}"
            
            //Show Debug logging if enabled in preferences
            logWriter(" ")
            logWriter("-------------------ESTIMATED USAGE DATA-------------------")
            logWriter("Http Usage Response: ${estUseResp?.data}")
            logWriter("TodayUsage: Today\'s Usage: ${state?.currencySym}${estUseResp?.data?.day_tariff?.estimate} (${estUseResp?.data?.day_kwh?.estimate} kWh)")
            logWriter("MonthUsage: ${state?.monthName} Usage ${state?.currencySym}${estUseResp?.data?.month_tariff?.previousSum} (${estUseResp?.data.month_kwh?.previousSum} kWh)")
            logWriter("MonthEst: ${state?.monthName}\'s Cost (Est.) ${state?.currencySym}${estUseResp?.data?.month_tariff?.estimate}")
            logWriter("${state?.monthName}\'s Budget ${state?.currencySym}${estUseResp?.data?.month_budget}")
        }
        return usageData    
    }
    catch (e) { log.error "getUsageData Exception: ${e}" }
}

// Get tariff energy metrics
def getTariffData() {
    try {
        def tarrifData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getTariff")
        if(tarrifData) {
            def tariffRate = tarrifData?.data?.tariff?.plan?.plan?.planDetail?.rate.toString().replaceAll("\\[|\\{|\\]|\\}", "")
            //Sends extended metrics to tiles
            state.tariffRate = "${tariffRate}${state.centSym}"
            //Show Debug logging if enabled in preferences
            logWriter(" ")
            logWriter("-------------------TARIFF RATE DATA-------------------")
            logWriter("Tariff Rate: ${state.tariffRate}")
        }
        return tarrifData
    }
    catch (ex) { log.error "getTariffData Exception: ${ex}" }
}

def getEfergyData(url, pathStr) {
    log.trace "getEfergyData($url, $pathStr)"
    try {
        def params = [
            uri: url,
            path: pathStr,
            query: ["token": state.efergyAuthToken],
            contentType: 'application/json'
        ]
        httpGet(params) { resp ->
            if(resp.data) {
                log.debug "getEfergyData Response: ${resp?.data}"
                return resp?.data
            }
        }
    } catch (ex) {
        log.error "getEfergyData Exception: ${ex}"
    }
}

/* Get the sensor reading
****  Json Returned: {"cid":"PWER","data":[{"1440023704000":0}],"sid":"123456","units":"kWm","age":142156},{"cid":"PWER_GAC","data":[{"1440165858000":1343}],"sid":"123456","units":null,"age":2}
*/
private def getReadingData() {
    try {
        def today = new Date()
        def tf = new SimpleDateFormat("MMM d,yyyy - h:mm:ss a")
            tf.setTimeZone(location?.timeZone)
        def tf2 = new SimpleDateFormat("MMM d,yyyy - h:mm:ss a")
        def cidVal = "" 
        def cidData = [{}]
        def cidUnit = ""
        def timeVal
        def cidReading
        def cidReadingAge
        def readingUpdated
        def readingData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getCurrentValuesSummary")
        if(readingData) {
            //Converts http response data to list
            def cidList = new JsonSlurper().parseText(readingData)
            
            //Search through the list for age to determine Cid Type
            for (rec in cidList) { 
                if (rec.age || rec?.age == 0) { 
                    cidVal = rec?.cid 
                    cidData = rec?.data
                    cidReadingAge = rec?.age
                    if(rec?.units != null) {}
                        cidUnit = rec?.units
                    break 
                 }
            }
            
             //Convert data: values to individual strings
            for (item in cidData[0]) {
                 timeVal =  item?.key
                cidReading = item?.value
            }
            
            //Converts timeVal string to long integer
            def longTimeVal = timeVal?.toLong()

            //Save Cid Type to device state
            state.cidType = cidVal
            
            //Save Cid Unit to device state
            state.cidUnit = cidUnit
            
            //Formats epoch time to Human DateTime Format
            if (longTimeVal) { 
                readingUpdated = "${tf.format(longTimeVal)}"
            }

            //Save last Cid reading value to device state
            if (cidReading) {
                state.powerReading = cidReading	
                state.energyReading = cidReading.toInteger() 
            }

            //state.powerVal = cidReading
            state.readingUpdated = "${readingUpdated}"
            state.readingDt = readingUpdated
            
            //Show Debug logging if enabled in preferences
            logWriter(" ")	
            logWriter("-------------------USAGE READING DATA-------------------")
            logWriter("HTTP Status Response: " + respData)	/*<------Uncomment this line to log the Http response */
            logWriter("Cid Type: " + state.cidType)
            logWriter("Cid Unit: " + cidUnit)
            logWriter("Timestamp: " + timeVal)
            logWriter("reading: " + cidReading)
            logWriter("Last Updated: " + readingUpdated)
            logWriter("Reading Age: " + cidReadingAge)
            logWriter("Current Month: ${state.monthName}")
            logWriter("Day of Week: ${state.dayOfWeek}")
        }
        return readingData
    }
    catch (ex) { 
        log.error "getReadingData Exception: ${ex}" 
    }
}

// Returns Hub Device Status Info 
private def getHubData() {
    def hubId = ""
    def hubMacAddr = ""
    def hubStatus = ""
    def hubTsHuman
    def hubType = ""
    def hubVersion = ""
    def statusList
    def hubData = getEfergyData("https://engage.efergy.com", "/mobile_proxy/getStatus")  
    if (hubData) {
        def respData = hubData
        //Converts http response data to list
        statusList = new JsonSlurper().parseText(respData)
        
        hubId = statusList?.hid
        hubMacAddr = statusList?.listOfMacs.mac
        hubStatus = statusList?.listOfMacs.status
        hubTsHuman = statusList?.listOfMacs.tsHuman
        hubType = statusList?.listOfMacs.type
        hubVersion = statusList?.listOfMacs.version
        
        //Save info to device state store
        state.hubId = hubId
        state.hubMacAddr = hubMacAddr.toString().replaceAll("\\[|\\{|\\]|\\}", "")
        state.hubStatus = hubStatus.toString().replaceAll("\\[|\\{|\\]|\\}", "")
        state.hubTsHuman = hubTsHuman.toString().replaceAll("\\[|\\{|\\]|\\}", "")
        state.hubType = hubType.toString().replaceAll("\\[|\\{|\\]|\\}", "")
        state.hubVersion = hubVersion.toString().replaceAll("\\[|\\{|\\]|\\}", "")
        state.hubName = getHubName(hubType)
        
        //Show Debug logging if enabled in preferences
        logWriter(" ")	
        logWriter("-------------------HUB DEVICE DATA-------------------")
        //logWriter("HTTP Status Response: " + respData)
        logWriter("Hub ID: " + state.hubId)
        logWriter("Hub Mac: " + state.hubMacAddr)
        logWriter("Hub Status: " + state.hubStatus)
        logWriter("Hub TimeStamp: " + state.hubTsHuman)
        logWriter("Hub Type: " + state.hubType)
        logWriter("Hub Firmware: " + state.hubVersion)
        logWriter("Hub Name: " + state.hubName)
    }
    return hubData
}    

def getTimeZone() { 
    def tz = null
    if (location?.timeZone) { tz = location?.timeZone }
    //else { tz = TimeZone.getTimeZone(getNestTimeZone()) }
    if(!tz) { log.warn("getTimeZone: Hub TimeZone is not found ...") }
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
def GetTimeDiffSeconds(lastDate) {
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
        log.error "GetTimeDiffSeconds Exception: ${ex}"
        return 10000
    }
}


//Log Writer that all logs are channel through *It will only output these if Debug Logging is enabled under preferences
private def logWriter(value) {
    if (state.showLogging) {
        log.debug "${value}"
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