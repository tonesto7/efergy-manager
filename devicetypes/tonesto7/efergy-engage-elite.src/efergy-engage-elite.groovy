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
def versionDate() {"8-30-2016"}

metadata {
    definition (name: "Efergy Engage Elite", namespace: "tonesto7", author: "Anthony S.") {
        capability "Energy Meter"
        capability "Power Meter"
        capability "Polling"
        capability "Refresh"
        capability "Actuator"
        capability "Sensor"

        attribute "maxPowerReading", "string"
        attribute "minPowerReading", "string"
        attribute "readingUpdated", "string"
        attribute "apiStatus", "string"
        attribute "devTypeVer", "string"

        command "poll"
        command "refresh"
    }

    tiles (scale: 2) {
        multiAttributeTile(name:"powerMulti", type:"generic", width:6, height:4) {
            tileAttribute("device.power", key: "PRIMARY_CONTROL") {
                attributeState "power", label: '${currentValue}W', unit: "W", icon: "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/images/power_icon_bk.png",
                        foregroundColor: "#000000",
                        backgroundColors:[
                            [value: 1, color: "#00cc00"], //Light Green
                            [value: 2000, color: "#79b821"], //Darker Green
                            [value: 3000, color: "#ffa81e"], //Orange
                            [value: 4000, color: "#FFF600"], //Yellow
                            [value: 5000, color: "#fb1b42"] //Bright Red
                        ]
            }
            tileAttribute("todayUsage_str", key: "SECONDARY_CONTROL") {
                      attributeState "default", label: 'Today\'s Usage: ${currentValue}'
               }
          }

        valueTile("todayUsage_str", "device.todayUsage_str", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Today\'s Usage:\n${currentValue}'
        }

        valueTile("monthUsage_str", "device.monthUsage_str", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }

        valueTile("monthEst_str", "device.monthEst_str", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }

        valueTile("budgetPercentage_str", "device.budgetPercentage_str", width: 3, height: 1, decoration: "flat", wordWrap: true) {
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

        valueTile("readingUpdated_str", "device.readingUpdated_str", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label:'${currentValue}'
        }

        standardTile("refresh", "command.refresh", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        valueTile("devVer", "device.devTypeVer", width: 4, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Device Type Version:\nv${currentValue}'
        }
        htmlTile(name:"graphHTML", action: "getGraphHTML", width: 6, height: 8, whitelist: ["www.gstatic.com", "raw.githubusercontent.com", "cdn.rawgit.com"])

        main (["powerMulti"])
        details(["powerMulti", "todayUsage_str", "monthUsage_str", "monthEst_str", "budgetPercentage_str", "tariffRate", "readingUpdated_str", "graphHTML", "refresh"])
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
        if(eventData) {
            //log.debug "eventData: $eventData"
            //clearHistory()
            state.timeZone = !location?.timeZone ? eventData?.tz : location?.timeZone
            state?.monthName = eventData?.monthName
            state?.currencySym = eventData?.currencySym
            debugOnEvent(eventData?.debug ? true : false)
            deviceVerEvent(eventData?.latestVer.toString())
            updateAttributes(eventData?.readingData, eventData?.usageData, eventData?.tariffData, eventData?.hubData)
            handleData(eventData?.readingData, eventData?.usageData)
            apiStatusEvent(eventData?.apiIssues)
            if(!eventData?.hubData?.hubTsHuman) { lastCheckinEvent(null) }
            else { lastCheckinEvent(eventData?.hubTsHuman) }
        }
        lastUpdatedEvent()
        return null
    }
    catch (ex) {
        log.error "generateEvent Exception: ${ex}", ex
    }
}



def clearHistory() {
    log.trace "Clearing History..."
    state?.energyTable = null
    state?.energyTableYesterday = null
    state?.pTable = null
    state?.powerTableYesterday = null
}

private handleData(readingData, usageData) {
    //log.trace "handleData ($power, $energy)"
    try {
        def currentDay = new Date().format("dd",location?.timeZone)
        if(state?.currentDay == null) { state?.currentDay = currentDay }
        def currentEnergy = usageData?.todayUsage
        def currentPower = readingData?.powerReading

        logWriter("currentDay: $currentDay")
        logWriter("currentDay(state): ${state?.currentDay}")
        logWriter("currentPower: $currentPower")
        logWriter("currentEnergy: $currentEnergy")

        state.lastPower = currentPower
        logWriter("lastPower: ${state?.lastPower}")
        def previousPower = state?.lastPower ?: currentPower
        logWriter("previousPower: $previousPower")
        def powerChange = currentPower.toInteger() - previousPower.toInteger()
        logWriter("powerChange: $powerChange")

        if (state.maxPowerReading <= currentPower) {
            state.maxPowerReading = currentPower
            sendEvent(name: "maxPowerReading", value: currentPower, unit: "kWh", description: "Highest Power Reading is $currentPower kWh", display: false, displayed: false)
            logWriter("maxPowerReading: ${state?.maxPowerReading}W")
        }
        if (state.minPowerReading >= currentPower) {
            state.minPowerReading = currentPower
            sendEvent(name: "minPowerReading", value: currentPower, unit: "kWh", description: "Lowest Power Reading is $currentPower kWh", display: false, displayed: false)
            logWriter("minPowerReading: ${state?.minPowerReading}W")
        }
        if(state?.pTable == null) {
            //runIn(66, "getPastData", [overwrite: false])
            //Attempts to populate the power table with previous power events from today
            //runIn(23, "getTodaysData", [overwrite: false])

            state?.pTable = []
            state?.energyTable = []
            addNewData(currentPower, currentEnergy)
        }

        def pTable = state?.pTable
        def eTable = state?.energyTable


        if (state?.powerTableYesterday?.size() == 0) {
            state.powerTableYesterday = pTable
            state.energyTableYesterday = eTable
        }

        if (!state?.currentDay || state.currentDay != currentDay) {
            state?.minPowerReading = currentPower
            state?.maxPowerReading = currentPower
            state.currentDay = currentDay
            state.powerTableYesterday = pTable
            state.energyTableYesterday = eTable
            state.pTable = []
            state.energyTable = []
            state.lastPower = 0
        }
        addNewData(currentPower, currentEnergy)
    } catch (ex) {
        log.error "handleData Exception:", ex
    }
}

def addNewData(pow, ener) {
    def pTable = state?.pTable
    def eTable = state?.energyTable
    log.debug "_______Adding New Data_______"
    log.debug "PowerTable Size (Before): ${pTable.size()}"
    log.debug "PowerTable Size (State Before): ${state?.pTable.size()}"

    def newDate = new Date()
    if(pow && pTable != null) {
        log.debug "pTable (before): ${pTable}"
        pTable?.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),pow.toInteger()])
        log.debug "pTable (after): ${pTable}"
    }
    if(ener && eTable != null) {
        eTable?.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),ener.toDouble()])
    }
    state.pTable = pTable
    state.energyTable = eTable
    log.debug "PowerTable Size (After): ${pTable.size()}"
    log.debug "PowerTable Size (State After): ${state?.pTable.size()}"
    log.debug "__________________________________"
}

def updateAttributes(rData, uData, tData, hData) {
    //log.trace "updateAttributes( $rData, $uData, $tData, $hData )"
    def readDate = Date.parse("MMM d,yyyy - h:mm:ss a", rData?.readingUpdated).format("MMM d,yyyy")
    def readTime = Date.parse("MMM d,yyyy - h:mm:ss a", rData?.readingUpdated).format("h:mm:ss a")

    logWriter("--------------UPDATE READING DATA-------------")
    logWriter("energy: " + uData?.todayUsage)
    logWriter("power: " + rData?.powerReading)
    logWriter("readingUpdated: " + rData?.readingUpdated)
    logWriter("")
    //Updates Device Readings to tiles
    sendEvent(name: "energy", unit: "kWh", value: uData?.todayUsage, description: "Energy Value is ${uData?.todayUsage} kWh", display: false, displayed: false)
    sendEvent(name: "power", unit: "W", value: rData?.powerReading, description: "Power Value is ${rData?.energyReading} W", display: false, displayed: false)
    sendEvent(name: "readingUpdated", value: rData?.readingUpdated, description: "Reading Updated at ${rData?.reading}", display: false, displayed: false)
    sendEvent(name: "readingUpdated_str", value: "Last Updated:\n${readDate}\n${readTime}", display: false, displayed: false)

    //UPDATES USAGE INFOR
    def budgPercent
    logWriter("--------------UPDATE USAGE DATA-------------")
    logWriter("todayUsage: " + uData?.todayUsage + "kWh")
    logWriter("todayCost: " + state?.currencySym + uData?.todayCost)
    logWriter("monthUsage: " + uData?.monthUsage + " kWh")
    logWriter("monthCost: " + state?.currencySym + uData?.monthCost)
    logWriter("monthEst: " + state?.currencySym + uData?.monthEst)
    logWriter("monthBudget: " + state?.currencySym + uData?.monthBudget)

    sendEvent(name: "todayUsage_str", value: "${state?.currencySym}${uData?.todayCost} (${uData?.todayUsage} kWH)", display: false, displayed: false)
    sendEvent(name: "monthUsage_str", value: "${state?.monthName}\'s Usage:\n${state?.currencySym}${uData?.monthCost} (${uData?.monthUsage} kWh)", display: false, displayed: false)
    sendEvent(name: "monthEst_str",   value: "${state?.monthName}\'s Bill (Est.):\n${state?.currencySym}${uData?.monthEst}", display: false, displayed: false)
    sendEvent(name: "todayUsage", value: uData?.todayUsage, unit: state?.currencySym, display: false, displayed: false)
    sendEvent(name: "monthUsage", value: uData?.monthUsage, unit: state?.currencySym, display: false, displayed: false)
    sendEvent(name: "monthEst",   value: uData?.monthEst, unit: state?.currencySym, display: false, displayed: false)

    if (uData?.monthBudget > 0) {
        budgPercent = Math.round(Math.round(uData?.monthCost?.toFloat()) / Math.round(uData?.monthBudget?.toFloat()) * 100)
        sendEvent(name: "budgetPercentage_str", value: "Monthly Budget:\nUsed ${budgPercent}% (${state?.currencySym}${uData?.monthCost}) of ${state?.currencySym}${uData?.monthBudget} ", display: false, displayed: false)
        sendEvent(name: "budgetPercentage", value: budgPercent, unit: "%", description: "Percentage of Budget User is (${budgPercent}%)", display: false, displayed: false)
    } else {
        budgPercent = 0
        sendEvent(name: "budgetPercentage_str", value: "Monthly Budget:\nBudget Not Set...", display: false, displayed: false)
    }
    logWriter("Budget Percentage: ${budgPercent}%")
    logWriter("")

    //Tariff Info
    logWriter("--------------UPDATE TARIFF DATA-------------")
    logWriter("tariff rate: " + tData?.tariffRate)
    logWriter("")
    sendEvent(name: "tariffRate", value: tData?.tariffRate, unit: state?.currencySym, description: "Tariff Rate is ${state?.currencySym}${tData?.tariffRate}", display: false, displayed: false)

    //Updates Hub INFO Tiles
    logWriter("--------------UPDATE HUB DATA-------------")
    logWriter("hubVersion: " + hData?.hubVersion)
    logWriter("hubStatus: " + hData?.hubStatus)
    logWriter("hubName: " + hData?.hubName)
    logWriter("")
    state.hubStatus = hData?.hubStatus
    state.hubVersion = hData?.hubVersion
    sendEvent(name: "hubVersion", value: hData?.hubVersion, display: false, displayed: false)
    sendEvent(name: "hubStatus", value: hData?.hubStatus, display: false, displayed: false)
    sendEvent(name: "hubName", value: hData?.hubName, display: false, displayed: false)
}

def lastCheckinEvent(checkin) {
    //log.trace "lastCheckinEvent()..."
    def formatVal = "MMM d, yyyy - h:mm:ss a"
    def tf = new SimpleDateFormat(formatVal)
    tf.setTimeZone(getTimeZone())
    def lastConn = checkin ? "${tf?.format(Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", checkin))}" : "Not Available"
    def lastChk = device.currentState("lastConnection")?.value
    state?.lastConnection = lastConn?.toString()
    if(!lastChk.equals(lastConn?.toString())) {
        log.debug("UPDATED | Last Hub Check-in was: (${lastConn}) | Original State: (${lastChk})")
        sendEvent(name: 'lastConnection', value: lastConn?.toString(), displayed: false, isStateChange: true)
    } else { logWriter("Last Hub Check-in was: (${lastConn}) | Original State: (${lastChk})") }
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

def deviceVerEvent(ver) {
    def curData = device.currentState("devTypeVer")?.value.toString()
    def pubVer = ver ?: null
    def dVer = devTypeVer() ?: null
    def newData = isCodeUpdateAvailable(pubVer, dVer) ? "${dVer}(New: v${pubVer})" : "${dVer}"
    state?.devTypeVer = newData
    state?.updateAvailable = isCodeUpdateAvailable(pubVer, dVer)
    if(!curData?.equals(newData)) {
        logWriter("UPDATED | Device Type Version is: (${newData}) | Original State: (${curData})")
        sendEvent(name: 'devTypeVer', value: newData, displayed: false)
    } else { logWriter("Device Type Version is: (${newData}) | Original State: (${curData})") }
}

def apiStatusEvent(issue) {
    def curStat = device.currentState("apiStatus")?.value
    def newStat = issue ? "issue" : "ok"
    state?.apiStatus = newStat
    if(!curStat.equals(newStat)) {
        log.debug("UPDATED | API Status is: (${newStat.toString().capitalize()}) | Original State: (${curStat.toString().capitalize()})")
        sendEvent(name: "apiStatus", value: newStat, descriptionText: "API Status is: ${newStat}", displayed: true, isStateChange: true, state: newStat)
    } else { logWriter("API Status is: (${newStat}) | Original State: (${curStat})") }
}

def getEnergy() { return !device.currentValue("energy") ? 0 : device.currentValue("energy") }
def getPower() { return !device.currentValue("power") ? 0 : device.currentValue("power") }
def getStateSize() { return state?.toString().length() }
def getStateSizePerc() { return (int) ((stateSize/100000)*100).toDouble().round(0) }
def getDataByName(String name) { state[name] ?: device.getDataValue(name) }
def getDeviceStateData() { return getState() }

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

def getTimeZone() {
    def tz = null
    if (location?.timeZone) { tz = location?.timeZone }
    if(!tz) { log.warn "getTimeZone: TimeZone is not found ..." }
    return tz
}

//Log Writer that all logs are channel through *It will only output these if Debug Logging is enabled under preferences
private def logWriter(value) {
    if (state.debug) {
        log.debug "${value}"
    }
}

def Logger(msg, type="debug") {
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

/*************************************************************
|                  HTML TILE RENDER FUNCTIONS                   |
**************************************************************/

def getSomeOldData(type, attributestr, gfloat, devpoll = false, nostate = true) {
    log.trace "getSomeOldData ( ${type}, ${attributestr}, ${gfloat}, ${devpoll})"

    //if (devpoll && (!state?."${type}TableYesterday" || !state?."${type}Table")) {
    //runIn( 66, "tgetSomeOldData", [data: [type:type, attributestr:attributestr, gfloat:gfloat, devpoll:false]])
    //return
    //}

    def startOfToday = timeToday("00:00", location.timeZone)
    def newValues
    def dataTable = []

    if (( nostate || state?."${type}TableYesterday" == null) && attributestr ) {
        log.trace "Querying DB for yesterday's ${type} data…"
        def yesterdayData = device.statesBetween("${attributestr}", startOfToday - 1, startOfToday, [max: 100])
        log.debug "got ${yesterdayData.size()}"
        if (yesterdayData.size() > 0) {
            while ((newValues = device.statesBetween("${attributestr}", startOfToday - 1, yesterdayData.last().date, [max: 100])).size()) {
                //log.debug "got ${newValues.size()}"
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
                //log.debug "got ${newValues.size()}"
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

private getPastData() {
    if(state?.powerTableYesterday == null) {
        state?.powerTableYesterday = []
        def startOfToday = timeToday("00:00", location?.timeZone)
        def newValues
        log.trace "Querying DB for Yesterday's Power data…"
        def dataTable = []
        def powerData = device.statesBetween("power", startOfToday - 1, startOfToday, [max: 100])
        log.debug "yesterdays powerData: ${powerData?.size()}"
        if (powerData?.size()) {
            /*while ((newValues = device.statesBetween("power", startOfToday - 1, powerData?.last().date, [max: 100]))?.size()) {
                powerData += newValues
            }*/
            powerData?.reverse().each() {
                dataTable.add([it?.date.format("H", location.timeZone),it?.date.format("m", location?.timeZone),it?.integerValue])
            }
        }
        state.powerTableYesterday = dataTable
    }
}

private getTodaysData() {
    if(state?.pTable == null) {
        getSomeOldData("p", "power" , false)
    }
}

String getDataString(Integer seriesIndex) {
    def dataString = ""
    def dataTable = []
    switch (seriesIndex) {
        case 1:
            dataTable = state.energyTableYesterday
            break
        case 2:
            dataTable = state.powerTableYesterday
            break
        case 3:
            dataTable = state.energyTable
            break
        case 4:
            dataTable = state.pTable
            break
    }
    dataTable.each() {
        def dataArray = [[it[0],it[1],0],null,null,null,null]
        dataArray[seriesIndex] = it[2]
        dataString += dataArray.toString() + ","
    }
    return dataString
}

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
    cssData = getFileBase64(cssUrl(), "text", "css")
    return cssData
}

def getChartJsData() {
    def chartJsData = null
    chartJsData = getFileBase64(chartJsUrl(), "text", "javascript")
    return chartJsData
}

def cssUrl() { return "https://raw.githubusercontent.com/desertblade/ST-HTMLTile-Framework/master/css/smartthings.css" }

def chartJsUrl() { return "https://www.gstatic.com/charts/loader.js" }

def getImg(imgName) { return imgName ? "https://cdn.rawgit.com/tonesto7/efergy-manager/master/Images/Devices/$imgName" : "" }

def getStartTime() {
    def startTime = 24
    if (state?.pTable?.size()) { startTime = state?.pTable?.min{it[0].toInteger()}[0].toInteger() }
    if (state?.powerTableYesterday?.size()) { startTime = Math.min(startTime, state?.powerTableYesterday?.min{it[0].toInteger()}[0].toInteger()) }
    log.trace "startTime ${startTime}"
    return startTime
}

def getMinVal() {
    def list = []
    if (state?.powerTableYesterday?.size() > 0) { list.add(state?.powerTableYesterday?.min { it[2] }[2].toInteger()) }
    if (state?.pTable?.size() > 0) { list.add(state?.pTable.min { it[2] }[2].toInteger()) }
    log.trace "getMinVal: ${list.min()} result: ${list}"
    return list?.min()
}

def getMaxVal() {
    def list = []
    if (state?.powerTableYesterday?.size() > 0) { list.add(state?.powerTableYesterday.max { it[2] }[2].toInteger()) }
    if (state?.pTable?.size() > 0) { list.add(state?.pTable.max { it[2] }[2].toInteger()) }
    log.trace "getMaxVal: ${list.max()} result: ${list}"
    return list?.max()
}

def getGraphHTML() {
    try {
        def updateAvail = !state.updateAvailable ? "" : "<h3>Device Update Available!</h3>"
        def chartHtml = (state.pTable?.size() > 0) ? showChartHtml() : hideChartHtml()

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
                <col width="49%">
                <col width="49%">
                <thead>
                  <th>Hub Status</th>
                  <th>API Status</th>
                </thead>
                <tbody>
                  <tr>
                    <td>${state?.hubStatus}</td>
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
                      <th>Hub Version</th>
                      <th>Debug</th>
                      <th>Device Type</th>
                    </tr>
                    <td>${state?.hubVersion.toString()}</td>
                    <td>${state?.debugStatus}</td>
                    <td>${state?.devTypeVer.toString()}</td>
                    </tbody>
                  </table>
                  <table>
                    <thead>
                      <th>Hub Checked-In</th>
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
    }

}

def showChartHtml() {
    def data = """
    <script type="text/javascript">
        google.charts.load('current', {packages: ['corechart']});
        google.charts.setOnLoadCallback(drawGraph);
        function drawGraph() {
      var data = new google.visualization.DataTable();
      data.addColumn('timeofday', 'time');
      data.addColumn('number', 'Energy (Yesterday)');
      data.addColumn('number', 'Power (Yesterday)');
      data.addColumn('number', 'Energy (Today)');
      data.addColumn('number', 'Power (Today)');
      data.addRows([
        ${getDataString(1)}
        ${getDataString(2)}
        ${getDataString(3)}
        ${getDataString(4)}
      ]);
      var options = {
        fontName: 'San Francisco, Roboto, Arial',
        width: '100%',
        height: '100%',
        hAxis: {
          format: 'H:mm',
          minValue: [${getStartTime()},0,0],
          slantedText: true,
          slantedTextAngle: 30
        },
        series: {
          0: {targetAxisIndex: 1, color: '#FFC2C2', lineWidth: 1},
          1: {targetAxisIndex: 0, color: '#D1DFFF', lineWidth: 1},
          2: {targetAxisIndex: 1, color: '#FF0000'},
          3: {targetAxisIndex: 0, color: '#004CFF'}
        },
        vAxes: {
          0: {
            title: 'Power (W)',
            format: 'decimal',
            textStyle: {color: '#004CFF'},
            titleTextStyle: {color: '#004CFF'}
          },
          1: {
            title: 'Energy (kWh)',
            format: 'decimal',
            textStyle: {color: '#FF0000'},
            titleTextStyle: {color: '#FF0000'}
          }
        },
        legend: {
          position: 'bottom',
          maxLines: 4
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
      var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));
      chart.draw(data, options);
    }
      </script>
      <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Usage History</h4>
      <div id="chart_div" style="width: 100%; height: 225px;"></div>
    """
    return data
}

def hideChartHtml() {
    def data = """
    <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Usage History</h4>
    <br></br>
    <div class="centerText">
      <p>Waiting for more data to be collected...</p>
      <p>This may take at least 24 hours</p>
    </div>
    """
    return data
}


def cssData() {
    def data = """
    body {
        font-family: 'San Francisco', 'Roboto', 'Arial';
        margin:0;
        font-size: 3.9vw;
        }

        div {
        margin:1px;
        }

        .container {
        position: relative;
        width: 100%;
        }

        .hideable{
        transition: all ease 2s;
        }

        fieldset {
        border: 1px solid #c0c0c0;
        margin: 0 2px;
        padding: 0.35em 0.625em 0.75em;
        }

        h1, h2, h3, h4, h5, h6 {
        padding:0px;
        margin:0px;
        }

        .topBorder {
        border-top: 2px solid #00a1db;
        }

        .bottomBorder {
        border-bottom: 2px solid #808080;
        }

        h1 {
        font-size: 6vw;
        width: 100%;
        text-align: center;
        font-weight: normal;
        }

        h2 {
        font-size: 9vw;
        text-align: center;
        margin-left: auto;
        margin-right: auto;
        font-weight: normal;
        }


        h3, h3 a {
        font-size: 6vw;
        font-weight: bold;
        text-align: center;
        background: #B74C4C;
        color: #f5f5f5;
        }

        h4 {
        font-size: 4vw;
        font-weight: bold;
        text-align: center;
        background: #00a1db;
        color: #f5f5f5;
        }

        .centerText {
        text-align: center;
        }

        hr {
        background: #00a1db;
        width: 100%;
        height: 1px;
        }

        .topModal, .bottomModal {
        position: fixed;
        font-size: 3vw;
        top: 0;
        right: 0;
        bottom: 0;
        left: 0;
        background: rgba(0,0,0,0.5);
        z-index: 99999;
        opacity:0;
        -webkit-transition: opacity 400ms ease-in;
        -moz-transition: opacity 400ms ease-in;
        transition: opacity 400ms ease-in;
        pointer-events: none;
        }

        .topModal:target, .bottomModal:target {
        opacity:1;
        pointer-events: auto;
        }

        .topModal > div {
        width: 65%;
        position: relative;
        margin: 10% auto;
        padding: 5px 20px 13px 20px;
        border-radius: 10px;
        background: #fff;
        }

        .bottomModal > div {
        width: 65%;
        position: relative;
        margin:  75% auto;
        padding: 5px 20px 13px 20px;
        border-radius: 10px;
        background: #fff;
        }

        .close {
        background: #606061;
        color: #FFFFFF;
        line-height: 25px;
        position: absolute;
        right: -12px;
        text-align: center;
        top: -10px;
        width: 24px;
        text-decoration: none;
        font-weight: bold;
        -webkit-border-radius: 12px;
        -moz-border-radius: 12px;
        border-radius: 12px;
        -moz-box-shadow: 1px 1px 3px #000;
        -webkit-box-shadow: 1px 1px 3px #000;
        box-shadow: 1px 1px 3px #000;
        }

        .close:hover { background: #00d9ff; }

        table {
        border: none;
        border-radius: 3px;
        width:100%;
        -webkit-border-radius: 3px;
        -moz-border-radius: 3px;
        }

        th, td {
        box-shadow: inset 0 0px rgba(0, 0, 0, 0.25), inset 0 0px rgba(0, 0, 0, 0.25);
        padding: 4px;
        }

        th {
        -webkit-font-smoothing: antialiased;
        color: #f5f5f5;
        text-shadow: 0 0 1px rgba(0, 0, 0, 0.1);
        -webkit-border-radius: 2px;
        -moz-border-radius: 2px;
        background: #00a1db;
        }

        td {
        color: grey;
        text-shadow: 0 0 1px rgba(255, 255, 255, 0.1);
        text-align: center;
        }

        tr {
        -webkit-transition: background 0.3s, box-shadow 0.3s;
        -moz-transition: background 0.3s, box-shadow 0.3s;
        transition: background 0.3s, box-shadow 0.3s;
        }

        .dateTimeText {
        font-size: 3.4vw;
        }

        .battImg {
        width:30px; height:15px;
        }

        .alarmImg {
        vertical-align: top;
        width:60px; height:60px;
        }

        .leafImg {
        width: 25px;
        height: 25px;
        }

        .column,
        .columns {
        width: 100%;
        float: left;
        box-sizing: border-box;
        }

        .one.column,
        .one.columns                    { width: 4.66666666667%; }
        .two.columns                    { width: 13.3333333333%; }
        .three.columns                  { width: 22%;            }
        .four.columns                   { width: 32.6666666667%; }
        .five.columns                   { width: 39.3333333333%; }
        .six.columns                    { width: 48%;            }
        .seven.columns                  { width: 56.6666666667%; }
        .eight.columns                  { width: 65.3333333333%; }
        .nine.columns                   { width: 74.0%;          }
        .ten.columns                    { width: 82.6666666667%; }
        .eleven.columns                 { width: 91.3333333333%; }
        .twelve.columns                 { width: 100%; margin-left: 0; }


        /* Offsets */
        .offset-by-one.column,
        .offset-by-one.columns          { margin-left: 8.66666666667%; }
        .offset-by-two.column,
        .offset-by-two.columns          { margin-left: 17.3333333333%; }
        .offset-by-three.column,
        .offset-by-three.columns        { margin-left: 26%;            }
        .offset-by-four.column,
        .offset-by-four.columns         { margin-left: 34.6666666667%; }
        .offset-by-five.column,
        .offset-by-five.columns         { margin-left: 43.3333333333%; }
        .offset-by-six.column,
        .offset-by-six.columns          { margin-left: 52%;            }
        .offset-by-seven.column,
        .offset-by-seven.columns        { margin-left: 60.6666666667%; }
        .offset-by-eight.column,
        .offset-by-eight.columns        { margin-left: 69.3333333333%; }
        .offset-by-nine.column,
        .offset-by-nine.columns         { margin-left: 78.0%;          }
        .offset-by-ten.column,
        .offset-by-ten.columns          { margin-left: 86.6666666667%; }
        .offset-by-eleven.column,
        .offset-by-eleven.columns       { margin-left: 95.3333333333%; }


        .container:after,
        .row:after,
        .u-cf {
        content: "";
        display: table;
        clear: both; }

        .row {
            width:100%;
            margin:2px;
        }

        .red{
            background-color:#db3a00;
        }

        .yellow{
            background-color:#dba800;
        }

    """
    return data
}
