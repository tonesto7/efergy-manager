/**
*  Efergy Engage Energy
*
*  Copyright 2015 Anthony S.
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*  ---------------------------
*
*  ---------------------------
*/
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.time.TimeCategory
import groovy.time.TimeDuration

def devTypeVer() {"3.0.0"}
def versionDate() {"8-29-2016"}

metadata {
    definition (name: "Efergy Engage Elite - DEV", namespace: "tonesto7", author: "Anthony S.") {
        capability "Energy Meter"
        capability "Power Meter"
        capability "Polling"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"

        attribute "apiStatus", "string"
        attribute "devTypeVer", "string"

        command "poll"
        command "refresh"
    }

    tiles (scale: 2) {
        multiAttributeTile(name:"power", type:"generic", width:6, height:4, wordWrap: true) {
            tileAttribute("device.power", key: "PRIMARY_CONTROL") {
                attributeState "default", label: '${currentValue} W', icon: "https://dl.dropboxusercontent.com/s/vfxkm0hp6jsl56m/power_icon_bk.png",
                        foregroundColor: "#000000",
                        backgroundColors:[
                            [value: 1, color: "#00cc00"], //Light Green
                            [value: 2000, color: "#79b821"], //Darker Green
                            [value: 3000, color: "#ffa81e"], //Orange
                            [value: 4000, color: "#FFF600"], //Yellow
                            [value: 5000, color: "#fb1b42"] //Bright Red
                        ]
            }
            tileAttribute("todayUsage", key: "SECONDARY_CONTROL") {
                      attributeState "default", label: 'Today\'s Usage: ${currentValue}'
               }
          }

        valueTile("todayUsage", "device.todayUsage", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Today\'s Usage:\n${currentValue}'
        }

        valueTile("monthUsage", "device.monthUsage", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }

        valueTile("monthEst", "device.monthEst", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }

        valueTile("budgetPercentage", "device.budgetPercentage", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }

        valueTile("tariffRate", "device.tariffRate", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Tariff Rate:\n${currentValue}/kWH'
        }

        valueTile("hubStatus", "device.hubStatus", width: 2, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Hub Status:\n${currentValue}'
        }

        valueTile("hubVersion", "device.hubVersion", width: 2, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Hub Version:\n${currentValue}'
        }

        valueTile("readingUpdated", "device.readingUpdated", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label:'${currentValue}'
        }

        standardTile("refresh", "command.refresh", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        valueTile("devVer", "device.devVer", width: 4, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }
        htmlTile(name:"graphHTML", action: "getGraphHTML", width: 6, height: 8, whitelist: ["www.gstatic.com", "raw.githubusercontent.com", "cdn.rawgit.com"])

        main (["power"])
        details(["power", "todayUsage", "monthUsage", "monthEst", "budgetPercentage", "tariffRate", "readingUpdated", "refresh", "hubStatus", "hubVersion", "devVer", "graphHTML"])
    }
}

preferences {

}

mappings {
    path("/getGraphHTML") {action: [GET: "getGraphHTML"]}
}

// parse events into attributes
def parse(String description) {
    logWriter("Parsing '${description}'")
}

// refresh command
def refresh() {
    log.info "Refresh command received..."
    parent.refresh()
}

// Poll command
def poll() {
    log.info "Poll command received..."
    parent.refresh()
}

def generateEvent(Map eventData) {
    //log.trace("generateEvent Parsing data ${eventData}")
    try {
        //Logger("------------START OF API RESULTS DATA------------", "warn")
        if(eventData) {
            //log.debug "eventData: $eventData"
            state.timeZone = !location?.timeZone ? eventData?.tz : location?.timeZone
            state.monthName = eventData?.monthName
            state.currencySym = eventData?.currencySym
            debugOnEvent(eventData?.showLogging)
            //deviceVerEvent(eventData?.devVersion)
            updateReadingData(eventData?.readingData)
            updateUsageData(eventData?.usageData)
            updateTariffData(eventData?.tariffData)
            updateHubData(eventData?.hubData)
        }
        lastUpdatedEvent()
        getSomeData(true)
        return null
    }
    catch (ex) {
        log.error "generateEvent Exception: ${ex}"
    }
}

def updateUsageData(data) {
    def budgPercent
    logWriter("--------------UPDATE USAGE DATA-------------")
    logWriter("todayUsage: " + data?.todayUsage + "kWh")
    logWriter("todayCost: " + state.currencySym + data?.todayCost)
    logWriter("monthUsage: " + data?.monthUsage + " kWh")
    logWriter("monthCost: " + state.currencySym + data?.monthCost)
    logWriter("monthEst: " + state.currencySym + data?.monthEst)
    logWriter("monthBudget: " + state.currencySym + data?.monthBudget)

    sendEvent(name: "todayUsage", value: "${state.currencySym}${data?.monthCost} (${data?.todayUsage} kWH)", display: false, displayed: false)
    sendEvent(name: "monthUsage", value: "${state.monthName}\'s Usage:\n${state.currencySym}${data?.monthCost} (${data?.monthUsage} kWh)", display: false, displayed: false)
    sendEvent(name: "monthEst",   value: "${state.monthName}\'s Bill (Est.):\n${state.currencySym}${data?.monthEst}", display: false, displayed: false)

    if (data?.monthBudget > 0) {
        budgPercent = Math.round(Math.round(data?.monthCost?.toFloat()) / Math.round(data?.monthBudget?.toFloat()) * 100)
        sendEvent(name: "budgetPercentage", value: "Monthly Budget:\nUsed ${budgPercent}% (${state.currencySym}${data?.monthCost}) of ${state.currencySym}${data?.monthBudget} ", display: false, displayed: false)
    }
       else {
        budgPercent = 0
        log.debug "budgPerc: ${budgPercent}"
        sendEvent(name: "budgetPercentage", value: "Monthly Budget:\nBudget Not Set...", display: false, displayed: false)
    }
    logWriter("budget percentage: ${budgPercent}%")
    logWriter("")
}

def lastUpdatedEvent() {
    def now = new Date()
    def formatVal = "MMM d, yyyy - h:mm:ss a"
    def tf = new SimpleDateFormat(formatVal)
    tf.setTimeZone(state.timeZone)
    def lastDt = "${tf?.format(now)}"
    def lastUpd = device.currentState("lastUpdatedDt")?.value
    state?.lastUpdatedDt = lastDt?.toString()
    if(!lastUpd.equals(lastDt?.toString())) {
        logWriter("Last Parent Refresh time: (${lastDt}) | Previous Time: (${lastUpd})")
        sendEvent(name: 'lastUpdatedDt', value: lastDt?.toString(), displayed: false, isStateChange: true)
    }
}

def deviceVerEvent(ver) {
    def curData = device.currentState("devTypeVer")?.value.toString()
    def pubVer = ver ?: null
    def dVer = devVer() ?: null
    def newData = isCodeUpdateAvailable(pubVer, dVer) ? "${dVer}(New: v${pubVer})" : "${dVer}"
    state?.devTypeVer = newData
    state?.updateAvailable = isCodeUpdateAvailable(pubVer, dVer)
    if(!curData?.equals(newData)) {
        logWriter("UPDATED | Device Type Version is: (${newData}) | Original State: (${curData})")
        sendEvent(name: 'devTypeVer', value: newData, displayed: false)
    } else { logWriter("Device Type Version is: (${newData}) | Original State: (${curData})") }
}

def updateReadingData(data) {
    def newTime = Date.parse("MMM d,yyyy - h:mm:ss a", data?.readingUpdated).format("h:mm:ss a")
    def newDate = Date.parse("MMM d,yyyy - h:mm:ss a", data?.readingUpdated).format("MMM d,yyyy")

    logWriter("--------------UPDATE READING DATA-------------")
    logWriter("energy: " + data?.energyReading)
    logWriter("power: " + data?.powerReading)
    logWriter("readingUpdated: " + data?.readingUpdated)
    logWriter("")
    //Updates Device Readings to tiles
    sendEvent(name: "energy", unit: "kWh", value: data?.energyReading, displayed: false)
    sendEvent(name: "power", unit: "W", value: data?.powerReading)
    sendEvent(name: "readingUpdated", value: "Last Updated:\n${newDate}\n${newTime}", display: false, displayed: false)
}

def updateTariffData(data) {
    logWriter("--------------UPDATE TARIFF DATA-------------")
    logWriter("tariff rate: " + data?.tariffRate)
    logWriter("")
    //Updates Device Readings to tiles
    sendEvent(name: "tariffRate", value: data?.tariffRate, display: false, displayed: false)
}

// Get Status
def updateHubData(data) {
    logWriter("--------------UPDATE HUB DATA-------------")
    logWriter("hubVersion: " + data?.hubVersion)
    logWriter("hubStatus: " + data?.hubStatus)
    logWriter("hubName: " + data?.hubName)
    logWriter("")
    //Updates HubVersion and HubStatus Tiles
    sendEvent(name: "hubVersion", value: data?.hubVersion, display: false, displayed: false)
    sendEvent(name: "hubStatus", value: data?.hubStatus, display: false, displayed: false)
    sendEvent(name: "hubName", value: data?.hubName, display: false, displayed: false)
}

def getEnergy() {
    return !device.currentValue("energy") ? 0 : device.currentValue("energy")
}

def getPower() {
    return !device.currentValue("power") ? 0 : device.currentValue("power")
}

def debugOnEvent(debug) {
    def val = device.currentState("debugOn")?.value
    def dVal = debug ? "On" : "Off"
    state?.debugStatus = dVal
    state?.debug = debug.toBoolean() ? true : false
    if(!val.equals(dVal)) {
        log.debug("UPDATED | debugOn: (${dVal}) | Original State: (${val.toString().capitalize()})")
        sendEvent(name: 'debugOn', value: dVal, displayed: false)
    } else { logWriter("debugOn: (${dVal}) | Original State: (${val})") }
}

def getStateSize()      { return state?.toString().length() }
def getStateSizePerc()  { return (int) ((stateSize/100000)*100).toDouble().round(0) }

def getDataByName(String name) {
    state[name] ?: device.getDataValue(name)
}

def getDeviceStateData() {
    return getState()
}

def isCodeUpdateAvailable(newVer, curVer) {
    def result = false
    def latestVer
    def versions = [newVer, curVer]
    if(newVer != curVer) {
        latestVer = versions?.max { a, b ->
            def verA = a?.tokenize('.')
            def verB = b?.tokenize('.')
            def commonIndices = Math.min(verA?.size(), verB?.size())
            for (int i = 0; i < commonIndices; ++i) {
                if (verA[i]?.toInteger() != verB[i]?.toInteger()) {
                    return verA[i]?.toInteger() <=> verB[i]?.toInteger()
                }
            }
            verA?.size() <=> verB?.size()
        }
        result = (latestVer == newVer) ? true : false
    }
    //log.debug "type: $type | newVer: $newVer | curVer: $curVer | newestVersion: ${latestVer} | result: $result"
    return result
}

//Log Writer that all logs are channel through *It will only output these if Debug Logging is enabled under preferences
private def logWriter(value) {
    if (state.showLogging) {
        log.debug "${value}"
    }
}

def Logger(msg, type) {
    if(msg && type) {
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


/**************************************************************************
|										  HTML TILE RENDER FUNCTIONS										      |
***************************************************************************/

def getImgBase64(url,type) {
    try {
        def params = [
            uri: url,
            contentType: 'image/$type'
        ]
        httpGet(params) { resp ->
            if(resp.data) {
                def respData = resp?.data
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
                int len
                int size = 3072
                byte[] buf = new byte[size]
                while ((len = respData.read(buf, 0, size)) != -1)
                    bos.write(buf, 0, len)
                buf = bos.toByteArray()
                //log.debug "buf: $buf"
                String s = buf?.encodeBase64()
                //log.debug "resp: ${s}"
                return s ? "data:image/${type};base64,${s.toString()}" : null
            }
        }
    }
    catch (ex) {
        log.error "getImageBytes Exception:", ex
        exceptionDataHandler(ex.message, "getImgBase64")
    }
}

def getFileBase64(url,preType,fileType) {
    try {
        def params = [
            uri: url,
            contentType: '$preType/$fileType'
        ]
        httpGet(params) { resp ->
            if(resp.data) {
                def respData = resp?.data
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
                int len
                int size = 4096
                byte[] buf = new byte[size]
                while ((len = respData.read(buf, 0, size)) != -1)
                    bos.write(buf, 0, len)
                buf = bos.toByteArray()
                //log.debug "buf: $buf"
                String s = buf?.encodeBase64()
                //log.debug "resp: ${s}"
                return s ? "data:${preType}/${fileType};base64,${s.toString()}" : null
            }
        }
    }
    catch (ex) {
        log.error "getFileBase64 Exception:", ex
        exceptionDataHandler(ex.message, "getFileBase64")
    }
}

def getCSS(url = null){
    try {
        def params = [
            uri: !url ? cssUrl() : url?.toString(),
            contentType: 'text/css'
        ]
        httpGet(params)  { resp ->
            return resp?.data.text
        }
    }
    catch (ex) {
        log.error "getCss Exception:", ex
        exceptionDataHandler(ex.message, "getCSS")
    }
}

def getJS(url){
    def params = [
        uri: url?.toString(),
        contentType: "text/plain"
    ]
    httpGet(params)  { resp ->
        return resp?.data.text
    }
}

def getCssData() {
    def cssData = null
    //def htmlInfo = state?.htmlInfo
    def htmlInfo
    state.cssData = null

    if(htmlInfo?.cssUrl && htmlInfo?.cssVer) {
        if(state?.cssData) {
            if (state?.cssVer?.toInteger() == htmlInfo?.cssVer?.toInteger()) {
                //log.debug "getCssData: CSS Data is Current | Loading Data from State..."
                cssData = state?.cssData
            } else if (state?.cssVer?.toInteger() < htmlInfo?.cssVer?.toInteger()) {
                log.debug "getCssData: CSS Data is Outdated | Loading Data from Source..."
                cssData = getFileBase64(htmlInfo.cssUrl, "text", "css")
                state.cssData = cssData
                state?.cssVer = htmlInfo?.cssVer
            }
        } else {
            log.debug "getCssData: CSS Data is Missing | Loading Data from Source..."
            cssData = getFileBase64(htmlInfo.cssUrl, "text", "css")
            state?.cssData = cssData
            state?.cssVer = htmlInfo?.cssVer
        }
    } else {
        log.debug "getCssData: No Stored CSS Info Data Found for Device... Loading for Static URL..."
        cssData = getFileBase64(cssUrl(), "text", "css")
    }
    return cssData
}

def getChartJsData() {
    def chartJsData = null
    chartJsData = getFileBase64(chartJsUrl(), "text", "javascript")
    return chartJsData
}

def cssUrl() { return "https://raw.githubusercontent.com/desertblade/ST-HTMLTile-Framework/master/css/smartthings.css" }
def chartJsUrl() { return "https://www.gstatic.com/charts/loader.js" }

def getImg(imgName) {
    return imgName ? "https://cdn.rawgit.com/tonesto7/efergy-manager/master/Images/Devices/$imgName" : ""
}

String getDataString(Integer seriesIndex) {
    //log.trace "getDataString ${seriesIndex}"
    def dataString = ""
    def dataTable = []
    switch (seriesIndex) {
        case 1:
            dataTable = state?.powerTableYesterday
            break
        case 2:
           dataTable = state?.powerTable
            break
        case 3:
            dataTable = state?.energyTable
            break
        case 4:
            dataTable = state?.energyTableYesterday
            break
    }

    def lastVal = 200

    //log.debug "getDataString ${seriesIndex} ${dataTable}"
    //log.debug "getDataString ${seriesIndex}"

    def lastAdded = false
    def dataArray
    def myval
    def myindex
    def lastdataArray = null

    dataTable.each() {
        myindex = seriesIndex

        dataArray = [[it[0],it[1],0],null,null,null,null]

        if (myindex == 3) {
            myval = it[2]
            if (myval == "idle") { myval = 0 }
            else { myval = 8 }
        } else { myval = it[2] }

        dataArray[myindex] = myval

        //reduce # of points to graph
        if (lastVal != myval) {
            lastAdded = true
            if (lastdataArray) {   //controls curves
                dataString += lastdataArray?.toString() + ","
            }
            lastdataArray = null
            lastVal = myval
            dataString += dataArray?.toString() + ","
        } else { lastAdded = false; lastdataArray = dataArray }
    }

    if (!lastAdded && dataString) {
        dataArray[myindex] = myval
        dataString += dataArray?.toString() + ","
    }

    if (dataString == "") {
        dataArray = [[0,0,0],null,null,null,null]
        dataArray[myindex] = 0
        dataString += dataArray?.toString() + ","
    }
    //log.debug "${dataString}"
    return dataString
}

def tgetSomeOldData(val) {
    log.trace "tgetSomeOldData ${val}"
    def type = val?.type?.value
    def attributestr  = val?.attributestr?.value
    def gfloat = val?.gfloat?.value
    def devpoll = val?.devpoll?.value
    log.trace "calling getSomeOldData ( ${type}, ${attributestr}, ${gfloat}, ${devpoll})"
    getSomeOldData(type, attributestr, gfloat, devpoll)
}

def getSomeOldData(type, attributestr, gfloat, devpoll = false, nostate = true) {
    log.trace "getSomeOldData ( ${type}, ${attributestr}, ${gfloat}, ${devpoll})"

//    if (devpoll && (!state?."${type}TableYesterday" || !state?."${type}Table")) {
//        runIn( 66, "tgetSomeOldData", [data: [type:type, attributestr:attributestr, gfloat:gfloat, devpoll:false]])
//        return
//    }

    def startOfToday = timeToday("00:00", location.timeZone)
    def newValues
    def dataTable = []

    if (( nostate || state?."${type}TableYesterday" == null) && attributestr ) {
        log.trace "Querying DB for yesterday's ${type} data…"
        def yesterdayData = device.statesBetween("${attributestr}", startOfToday - 1, startOfToday, [max: 100])
        log.debug "got ${yesterdayData.size()}"
        if (yesterdayData.size() > 0) {
            while ((newValues = device.statesBetween("${attributestr}", startOfToday - 1, yesterdayData.last().date, [max: 100])).size()) {
                log.debug "got ${newValues.size()}"
                yesterdayData += newValues
            }
        }
        log.debug "got ${yesterdayData.size()}"
        dataTable = []
        yesterdayData.reverse().each() {
            if (gfloat) { dataTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.floatValue]) }
            else { dataTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.stringValue]) }
        }
        log.debug "finished ${dataTable}"
        if (!nostate) {
            state."${type}TableYesterday" = dataTable
        }
    }

    if ( nostate || state?."${type}Table" == null) {
        log.trace "Querying DB for today's ${type} data…"
        def todayData = device.statesSince("${attributestr}", startOfToday, [max: 100])
        log.debug "got ${todayData.size()}"
        if (todayData.size() > 0) {
            while ((newValues = device.statesBetween("${attributestr}", startOfToday, todayData.last().date, [max: 100])).size()) {
                log.debug "got ${newValues.size()}"
                todayData += newValues
            }
        }
        log.debug "got ${todayData.size()}"
        dataTable = []
        todayData.reverse().each() {
            if (gfloat) { dataTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.floatValue]) }
            else { dataTable.add([it.date.format("H", location.timeZone),it.date.format("m", location.timeZone),it.stringValue]) }
        }
        log.debug "finished ${dataTable}"
        if (!nostate) {
            state."${type}Table" = dataTable
        }
    }
}

def getSomeData(devpoll = false) {
    //log.trace "getSomeData ${app}"

// hackery to test getting old data
    def tryNum = 1
    if (state.eric != tryNum ) {
        if (devpoll) {
            runIn( 33, "getSomeData", [overwrite: true])
            return
        }

        runIn( 33, "getSomeData", [overwrite: true])
        state.eric = tryNum

        state.powerTableYesterday = null
        state.energyTableYesterday = null

        state.powerTable = null
        state.energyTable = null

        state.remove("powerTableYesterday")
        state.remove("energyTableYesterday")

        state.remove("today")
        state.remove("powerTable")
        state.remove("energyTable")

        return
    } else {
        //getSomeOldData("temperature", "temperature", true, devpoll)
        //getSomeOldData("operatingState", "thermostatOperatingState", false, devpoll)
    }

    def todayDay = new Date().format("dd",location.timeZone)

    if (state?.powerTable == null) {

    // these are commented out as the platform continuously times out
        //getSomeOldData("temperature", "temperature", true, devpoll)
        //getSomeOldData("operatingState", "thermostatOperatingState", false, devpoll)

        state.powerTable = []
        state.energyTable = []
        addNewData()
    }

    def powerTable = state?.powerTable
    def energyTable = state?.energyTable

    if (state?.powerTableYesterday?.size() == 0) {
        state.powerTableYesterday = powerTable
        state.energyTableYesterday = energyTable
    }

    if (!state?.today || state.today != todayDay) {
        state.today = todayDay
        state.powerTableYesterday = powerTable
        state.energyTableYesterday = energyTable

        state.powerTable = []
        state.energyTable = []
    }
    addNewData()
}

def addNewData() {
    def currentPower = getPower()
    def currentEnergy = getEnergy()

    def energyTable = state?.energyTable
    def powerTable = state?.powerTable

    // add latest coolSetpoint & temperature readings for the graph
    def newDate = new Date()
    energyTable?.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),currentEnergy])
    powerTable?.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),currentPower])

    state.energyTable = energyTable
    state.powerTable = powerTable
}

def getStartTime() {
    def startTime = 24
    if (state?.powerTable?.size()) { startTime = state?.powerTable?.min{it[0].toInteger()}[0].toInteger() }
    if (state?.powerTableYesterday?.size()) { startTime = Math.min(startTime, state?.powerTableYesterday?.min{it[0].toInteger()}[0].toInteger()) }
    log.trace "startTime ${startTime}"
    return startTime
}

def getMinVal() {
    def list = []
    if (state?.powerTableYesterday?.size() > 0) { list.add(state?.powerTableYesterday?.min { it[2] }[2].toInteger()) }
    if (state?.powerTable?.size() > 0) { list.add(state?.powerTable.min { it[2] }[2].toInteger()) }
    log.trace "getMinVal: ${list.min()} result: ${list}"
    return list?.min()
}

def getMaxVal() {
    def list = []
    if (state?.powerTableYesterday?.size() > 0) { list.add(state?.powerTableYesterday.max { it[2] }[2].toInteger()) }
    if (state?.powerTable?.size() > 0) { list.add(state?.powerTable.max { it[2] }[2].toInteger()) }
    log.trace "getMaxVal: ${list.max()} result: ${list}"
    return list?.max()
}
def getGraphHTML() {
    try {
        //log.debug "State Size: ${getStateSize()} (${getStateSizePerc()}%)"

        def updateAvail = !state.updateAvailable ? "" : "<h3>Device Update Available!</h3>"

        def chartHtml = (
                state.powerTable?.size() > 0 &&
                state.energyTable?.size() > 0 &&
                state.powerTableYesterday?.size() > 0) ? showChartHtml() : hideChartHtml()

        def html = """
        <!DOCTYPE html>
        <html>
            <head>
                <meta http-equiv="cache-control" content="max-age=0"/>
                <meta http-equiv="cache-control" content="no-cache"/>
                <meta http-equiv="expires" content="0"/>
                <meta http-equiv="expires" content="Tue, 01 Jan 1980 1:00:00 GMT"/>
                <meta http-equiv="pragma" content="no-cache"/>
                <meta name="viewport" content="width = device-width, user-scalable=no, initial-scale=1.0">
                <link rel="stylesheet prefetch" href="${getCssData()}"/>
                <script type="text/javascript" src="${getChartJsData()}"></script>
            </head>
            <body>
                ${updateAvail}

                ${chartHtml}

                <br></br>
                <table>
                <col width="40%">
                <col width="20%">
                <col width="40%">
                <thead>
                  <th>Network Status</th>
                  <th>Leaf</th>
                  <th>API Status</th>
                </thead>
                <tbody>
                  <tr>
                    <td>${state?.onlineStatus.toString()}</td>
                    <td><img src="${leafImg}" class="leafImg"></img></td>
                    <td>${state?.apiStatus}</td>
                  </tr>
                </tbody>
              </table>

              <p class="centerText">
                <a href="#openModal" class="button">More info</a>
              </p>

              <div id="openModal" class="topModal">
                <div>
                  <a href="#close" title="Close" class="close">X</a>
                  <table>
                    <tr>
                      <th>Firmware Version</th>
                      <th>Debug</th>
                      <th>Device Type</th>
                    </tr>
                    <td>${state?.softwareVer.toString()}</td>
                    <td>${state?.debugStatus}</td>
                    <td>${state?.devTypeVer.toString()}</td>
                    </tbody>
                  </table>
                  <table>
                    <thead>
                      <th>Nest Checked-In</th>
                      <th>Data Last Received</th>
                    </thead>
                    <tbody>
                      <tr>
                        <td class="dateTimeText">${state?.lastConnection.toString()}</td>
                        <td class="dateTimeText">${state?.lastUpdatedDt.toString()}</td>
                      </tr>
                  </table>
                </div>
              </div>
            </body>
        </html>
        """
        render contentType: "text/html", data: html, status: 200
    } catch (ex) {
        log.error "graphHTML Exception:", ex
        exceptionDataHandler(ex.message, "graphHTML")
    }

}

def showChartHtml() {
    def minval = getMinVal()
    def minstr = "minValue: ${minval},"

    def maxval = getMaxVal()
    def maxstr = "maxValue: ${maxval},"

    def differ = maxval - minval
    if (differ > (maxval/4) || differ < (wantMetric() ? 10:20) ) {
        minstr = "minValue: ${(minval - (wantMetric() ? 10:20))},"
        if (differ < (wantMetric() ? 10:20) ) {
            maxstr = "maxValue: ${(maxval + (wantMetric() ? 10:20))},"
        }
    }

    def data = """
    <script type="text/javascript">
        google.charts.load('current', {packages: ['corechart']});
        google.charts.setOnLoadCallback(drawGraph);
        function drawGraph() {
            var data = new google.visualization.DataTable();
            data.addColumn('timeofday', 'time');
            data.addColumn('number', 'Power (Y)');
            data.addColumn('number', 'Power (T)');
            data.addRows([
                ${getDataString(1)}
                ${getDataString(2)}
            ]);
            var options = {
            width: '100%',
            height: '100%',
                hAxis: {
                    format: 'H:mm',
                    minValue: [${getStartTime()},0,0],
                    slantedText: true,
                    slantedTextAngle: 30
                },
                series: {
                    0: {targetAxisIndex: 1, type: 'area', color: '#FFC2C2', lineWidth: 1},
                    1: {targetAxisIndex: 1, type: 'area', color: '#FF0000'},
                },
                vAxes: {
                    0: {
                        title: 'Power (W)',
                        format: 'decimal',
                        ${minstr}
                        ${maxstr}
                        textStyle: {color: '#FF0000'},
                        titleTextStyle: {color: '#FF0000'}
                    }
                },
                legend: {
                    position: 'bottom',
                    maxLines: 4,
                    textStyle: {color: '#000000'}
                },
                chartArea: {
                    left: '12%',
                    right: '18%',
                    top: '3%',
                    bottom: '20%',
                    height: '85%',
                    width: '100%'
                }
            };
            var chart = new google.visualization.ComboChart(document.getElementById('chart_div'));
            chart.draw(data, options);
        }
      </script>
      <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Event History</h4>
      <div id="chart_div" style="width: 100%; height: 225px;"></div>
    """
    return data
}

def hideChartHtml() {
    def data = """
    <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Event History</h4>
    <br></br>
    <div class="centerText">
      <p>Waiting for more data to be collected...</p>
      <p>This may take at least 24 hours</p>
    </div>
    """
    return data
}
