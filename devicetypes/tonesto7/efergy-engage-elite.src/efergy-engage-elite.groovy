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
                attributeState "power", label: '${currentValue}', unit: "W",
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

        valueTile("devVer", "device.devVer", width: 4, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }
        htmlTile(name:"graphHTML", action: "getGraphHTML", width: 6, height: 8, whitelist: ["www.gstatic.com", "raw.githubusercontent.com", "cdn.rawgit.com"])

        main (["powerMulti"])
        details(["powerMulti", "todayUsage_str", "monthUsage_str", "monthEst_str", "budgetPercentage_str", "tariffRate", "readingUpdated_str", "refresh", "hubStatus", "hubVersion", "devVer", "graphHTML"])
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
            debugOnEvent(eventData?.showLogging)
            deviceVerEvent(eventData?.devVersion)
            handleData(eventData?.readingData, eventData?.usageData, eventData?.tariffData, eventData?.hubData)
        }
        lastUpdatedEvent()
        return null
    }
    catch (ex) {
        log.error "generateEvent Exception: ${ex}", ex
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
      dataTable = state.powerTable
      break
  }
  dataTable.each() {
    def dataArray = [[it[0],it[1],0],null,null,null,null]
    dataArray[seriesIndex] = it[2]
    dataString += dataArray.toString() + ","
  }
  return dataString
}

def clearHistory() {
    log.trace "Clearing History..."
    state?.energyTable = null
    state?.energyTableYesterday = null
    state?.powerTable = null
    state?.powerTableYesterday = null
}

private handleData(readingData, usageData, tariffData, hubData) {
    //log.trace "handleData ($power, $energy)"
    try {
        def curDayNum = new Date().format("dd",location?.timeZone)
        if(state?.todayNum == null) { state?.todayNum = curDayNum }
        def energyToday = usageData?.todayUsage
        def currentPower = readingData?.powerReading

        logWriter("--------handleData START-------")
        logWriter("curDayNum: $curDayNum")
        logWriter("todayNum(state): ${state?.todayNum}")
        logWriter("energyToday: $energyToday")
        logWriter("currentPower: $currentPower")
        logWriter("powerTable(state): ${state?.powerTable}")
        logWriter("energyTable(state): ${state?.energyTable}")

        def powerTable
      def energyTable
        if(state?.powerTable) { powerTable = state?.powerTable }
        if(state?.energyTable) { energyTable = state?.energyTable }

        updateAttributes(readingData, usageData, tariffData, hubData)

      if (state.todayNum != curDayNum) {
        state?.minPowerReading = currentPower
            state?.maxPowerReading = currentPower
        state.todayNum = curDayNum
        state.powerTableYesterday = powerTable
        state.energyTableYesterday = energyTable
        powerTable = powerTable ? [] : null
        energyTable = energyTable ? [] : null
        state.lastPower = 0
      }

        state.lastPower = currentPower
        logWriter("lastPower: ${state?.lastPower}")
      def previousPower = (state?.lastPower != null) ? state?.lastPower : currentPower
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

      if (state?.powerTableYesterday == null || state?.energyTableYesterday == null || powerTable == null || energyTable == null) {
        if (state?.powerTableYesterday == null || state?.energyTableYesterday == null) {
          runIn(7, "getPastData", [overwrite: false])
        }
        if (powerTable == null || energyTable == null) {
          runIn(17, "getTodaysData", [overwrite: false])
        }
      }
      // add latest power & energy readings for the graph
      if (currentPower > 0 || powerTable?.size() != 0) {
        def newDate = new Date()
            powerTable.add([newDate?.format("H", location?.timeZone),newDate.format("m", location?.timeZone),currentPower])
        energyTable.add([newDate?.format("H", location?.timeZone),newDate?.format("m", location?.timeZone),energyToday])
            state.powerTable = powerTable
            state.energyTable = energyTable
      }

        logWriter("powerTable(OUT): $powerTable")
        logWriter("energyTable(OUT): $energyTable")
        logWriter("------handleData END------")
    } catch (ex) {
        log.error "handleData Exception:", ex
    }
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

    if (data?.monthBudget > 0) {
        budgPercent = Math.round(Math.round(uData?.monthCost?.toFloat()) / Math.round(uData?.monthBudget?.toFloat()) * 100)
        sendEvent(name: "budgetPercentage_str", value: "Monthly Budget:\nUsed ${budgPercent}% (${state?.currencySym}${uData?.monthCost}) of ${state?.currencySym}${uData?.monthBudget} ", display: false, displayed: false)
        sendEvent(name: "budgetPercentage", value: budgPercent, unit: "%", description: "Budget Percentage is ${budgPercent}%", display: false, displayed: false)
    }
       else {
        budgPercent = 0
        log.debug "budgPerc: ${budgPercent}"
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

    sendEvent(name: "hubVersion", value: hData?.hubVersion, display: false, displayed: false)
    sendEvent(name: "hubStatus", value: hData?.hubStatus, display: false, displayed: false)
    sendEvent(name: "hubName", value: hData?.hubName, display: false, displayed: false)
}

private getPastData() {
    def startOfToday = timeToday("00:00", location?.timeZone)
    def newValues
    log.trace "Querying DB for yesterday's data…"
    def dataTable = []
    def powerData = device.statesBetween("power", startOfToday - 1, startOfToday, [max: 500]) // 24h in 5min intervals should be more than sufficient…
    // work around a bug where the platform would return less than the requested number of events (as June 2016, only 50 events are returned)
    log.debug "yesterdays powerData: ${powerData.size()}"
    if (powerData?.size()) {
        while ((newValues = device.statesBetween("power", startOfToday - 1, powerData?.last().date, [max: 500]))?.size()) {
            powerData += newValues
        }
        powerData?.reverse().each() {
            dataTable.add([it?.date.format("H", location.timeZone),it?.date.format("m", location?.timeZone),it?.integerValue])
        }
    }
    state.powerTableYesterday = dataTable
    dataTable = []
    def energyData = device.statesBetween("energy", startOfToday - 1, startOfToday, [max: 500])
    log.debug "yesterdays energyData: ${energyData.size()}"
    if (energyData?.size()) {
        while ((newValues = device.statesBetween("energy", startOfToday - 1, energyData?.last().date, [max: 500]))?.size()) {
            energyData += newValues
        }
        // we drop the first point after midnight (0 energy) in order to have the graph scale correctly
        energyData?.reverse().drop(1).each() {
            dataTable.add([it?.date.format("H", location?.timeZone),it?.date.format("m", location?.timeZone),it?.floatValue])
        }
    }
    state.energyTableYesterday = dataTable
}

private getTodaysData() {
    def startOfToday = timeToday("00:00", location?.timeZone)
    def newValues
    log.trace "Querying DB for today's data…"
    def powerTable = []
    def powerData = device.statesSince("power", startOfToday, [max: 500])
    log.debug "powerData: ${powerData.size()}"
    if (powerData.size()) {
        while ((newValues = device.statesBetween("power", startOfToday, powerData?.last().date, [max: 500]))?.size()) {
            powerData += newValues
        }
        powerData?.reverse().each() {
            powerTable.add([it?.date.format("H", location?.timeZone),it?.date.format("m", location?.timeZone),it?.integerValue])
        }
    }
    def energyTable = []
    def energyData = device.statesSince("energy", startOfToday, [max: 500])
    log.debug "energyData: ${energyData.size()}"
    if (energyData?.size()) {
        while ((newValues = device.statesBetween("energy", startOfToday, energyData?.last()?.date, [max: 500]))?.size()) {
            energyData += newValues
        }
        energyData?.reverse().drop(1).each() {
            energyTable.add([it?.date.format("H", location?.timeZone),it?.date.format("m", location?.timeZone),it?.floatValue])
        }
    }
    state.powerTable = powerTable
    state.energyTable = energyTable
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
    def dVer = devVer() ?: null
    def newData = isCodeUpdateAvailable(pubVer, dVer) ? "${dVer}(New: v${pubVer})" : "${dVer}"
    state?.devTypeVer = newData
    state?.updateAvailable = isCodeUpdateAvailable(pubVer, dVer)
    if(!curData?.equals(newData)) {
        logWriter("UPDATED | Device Type Version is: (${newData}) | Original State: (${curData})")
        sendEvent(name: 'devTypeVer', value: newData, displayed: false)
    } else { logWriter("Device Type Version is: (${newData}) | Original State: (${curData})") }
}

def getEnergy() {
    return !device.currentValue("energy") ? 0 : device.currentValue("energy")
}

def getPower() {
    return !device.currentValue("power") ? 0 : device.currentValue("power")
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


/*************************************************************
|                  HTML TILE RENDER FUNCTIONS              	 |
**************************************************************/

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
        def updateAvail = !state.updateAvailable ? "" : "<h3>Device Update Available!</h3>"
        def chartHtml = (state.powerTable?.size() > 0 && state.energyTable?.size() > 0) ? showChartHtml() : hideChartHtml()

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
                      <th>Hub Version</th>
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
        height: 240,
        hAxis: {
          format: 'H:mm',
          minValue: [${getStartTime()},0,0],
          slantedText: false
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
          position: 'none'
        },
        chartArea: {
          width: '72%',
          height: '85%'
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
