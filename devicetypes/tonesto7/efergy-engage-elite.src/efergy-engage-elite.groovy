/**
*  Efergy Engage Energy
*
*  Copyright 2016 Anthony S.
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
*/

import java.text.SimpleDateFormat

def devTypeVer() {"3.1.3"}
def versionDate() {"12-20-2016"}

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
        attribute "maxEnergyReading", "string"
        attribute "minEnergyReading", "string"
        attribute "dayPowerAvg", "string"
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
        valueTile("tariffRate_str", "device.tariffRate_str", width: 3, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: '${currentValue}'
        }
        valueTile("hubStatus", "device.hubStatus", width: 2, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Hub Status:\n${currentValue}'
        }

        valueTile("pwrMin", "device.minPowerReading", width: 2, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Power (Min):\n${currentValue}W'
        }
        valueTile("pwrAvg", "device.dayPowerAvg", width: 2, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Power (Avg):\n${currentValue}W'
        }
        valueTile("pwrMax", "device.maxPowerReading", width: 2, height: 1, decoration: "flat", wordWrap: true) {
            state "default", label: 'Power (Max):\n${currentValue}W'
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
        htmlTile(name:"graphHTML", action: "getGraphHTML", width: 6, height: 12, whitelist: ["www.gstatic.com", "raw.githubusercontent.com", "cdn.rawgit.com"])

        main (["powerMulti"])
        details(["powerMulti", "todayUsage_str", "monthUsage_str", "monthEst_str", "budgetPercentage_str", "tariffRate_str", "readingUpdated_str", "pwrMin", "pwrAvg", "pwrMax", "graphHTML", "refresh"])
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

void generateEvent(Map eventData) {
    //log.trace("generateEvent Parsing data ${eventData}")
    try {
        if(eventData) {
            //log.debug "eventData: $eventData"
            //state.timeZone = !location.timeZone ? eventData?.tz : location.timeZone <<<  This is causing stack overflow errors for the platform
            state?.monthName = eventData?.monthName
            state?.currency = eventData?.currency
            debugOnEvent(eventData?.debug ? true : false)
            deviceVerEvent(eventData?.latestVer.toString())
            updateAttributes(eventData?.readingData, eventData?.usageData, eventData?.tariffData, eventData?.hubData)
            handleData(eventData?.readingData, eventData?.usageData)
            apiStatusEvent(eventData?.apiIssues)
            lastCheckinEvent(eventData?.hubData?.hubTsHuman)
        }
        lastUpdatedEvent()
        //return null
    }
    catch (ex) {
        log.error "generateEvent Exception:", ex
    }
}

def clearHistory() {
    log.trace "Clearing History..."
    state?.energyVal = null
    state?.powerTable = null
    state?.powerTableYesterday = null
    state?.energyTable = null
    state?.energyTableYesterday = null
    state.lastRecordDt = null
}

private handleData(readingData, usageData) {
    //log.trace "handleData ($localTime, $power, $energy)"
    //clearHistory()
    state?.lastRecordDt = null
    try {
        def today = new Date()
        def currentHour = today.format("HH", location.timeZone) as Integer
        def currentDay = today.format("dd", location.timeZone) //1...31
        def currentDayNum = today.format("u", location.timeZone) as Integer // 1 = Monday,... 7 = Sunday
        def currentMonth = today.format("MM", location.timeZone)
        def currentYear = today.format("YYYY", location.timeZone) as Integer
        if(state?.currentDay == null) { state?.currentDay = currentDay }
        if(state?.currentDayNum == null) { state?.currentDayNum = currentDayNum }
        if(state?.currentYear == null) { state?.currentYear = currentYear }
        if(state?.currentMonth == null) { state?.currentMonth = currentMonth }
        def currentEnergy = usageData?.todayUsage
        def currentPower = readingData?.powerReading

        logWriter("currentDay: $currentDay | (state): ${state?.currentDay}")
        logWriter("currentDayNum: $currentDayNum | (state): ${state?.currentDayNum}")
        logWriter("currentMonth: $currentMonth | (state): ${state?.currentMonth}")
        logWriter("currentYear: $currentYear | (state): ${state?.currentYear}")

        logWriter("currentPower: $currentPower")
        logWriter("currentEnergy: $currentEnergy")

        state.lastPower = currentPower
        logWriter("lastPower: ${state?.lastPower}")
        def previousPower = state?.lastPower ?: currentPower
        logWriter("previousPower: $previousPower")
        def powerChange = (currentPower.toInteger() - previousPower.toInteger())
        logWriter("powerChange: $powerChange")

        if (state.maxPowerReading <= currentPower) {
            state.maxPowerReading = currentPower
            sendEvent(name: "maxPowerReading", value: currentPower, unit: "W", description: "Highest Power Reading is $currentPower W", display: false, displayed: false)
            logWriter("maxPowerReading: ${state?.maxPowerReading}W")
        }
        if (state.minPowerReading >= currentPower) {
            state.minPowerReading = currentPower
            sendEvent(name: "minPowerReading", value: currentPower, unit: "W", description: "Lowest Power Reading is $currentPower W", display: false, displayed: false)
            logWriter("minPowerReading: ${state?.minPowerReading}W")
        }
        if (state.maxEnergyReading <= currentEnergy) {
            state.maxEnergyReading = currentEnergy
            sendEvent(name: "maxEnergyReading", value: currentEnergy, unit: "kWh", description: "Highest Day Energy Consumption is $currentEnergy kWh", display: false, displayed: false)
            logWriter("maxEnergyReading: ${state?.maxEnergyReading}W")
        }
        if (state.minPowerReading >= currentEnergy) {
            state.minPowerReading = currentEnergy
            sendEvent(name: "minEnergyReading", value: currentEnergy, unit: "kWh", description: "Lowest Day Energy Consumption is $currentEnergy kWh", display: false, displayed: false)
            logWriter("minEnergyReading: ${state?.minEnergyReading} kWh")
        }

        if(!state?.powerTable) {
            state?.powerTable = []
            //state?.energyTable = []
            state?.dayMinPowerTable = []
            state?.dayMaxPowerTable = []
            //state?.dayMinEnergyTable = []
            //state?.dayMaxEnergyTable = []
            state?.dailyPowerAvgTable = []
        }
        if(!state?.powerTableYesterday || !state?.energyTableYesterday) {
            state.powerTableYesterday = []
            //state.energyTableYesterday = []
        }

        def powerTable = state.powerTable
        //def energyTable = state.energyTable
        if (!state?.currentDay || (state.currentDay != currentDay)) {
            log.debug "currentDay ($currentDay) is != to State (${state?.currentDay})"
            state.powerTableYesterday = powerTable
            //state.energyTableYesterday = energyTable
            handleNewDay(currentPower, currentEnergy)
            powerTable = []
            //energyTable = energyTable ? [] : null
            state.currentDay = currentDay
            state.currentDayNum = currentDayNum
            if(currentDay == 1) {
                log.debug "new week"
                handleNewWeek()
            }
        }
        if (!state?.currentMonth || (state.currentMonth != currentMonth && currentHour == 0)) {
            log.debug "currentMonth ($currentMonth) is != to State (${state?.currentMonth})"

            handleNewMonth()
            state.currentMonth = currentMonth
        }

        if (currentPower > 0 || powerTable?.size() != 0) {
            def newDate = new Date()
            if(getLastRecUpdSec() >= 117 || state?.lastRecordDt == null ) {
                collectEnergy(currentEnergy)
                powerTable.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),currentPower, getCurrentEnergy()])
                //energyTable.add([newDate.format("H", location.timeZone),newDate.format("m", location.timeZone),currentEnergy])
                log.debug "powerTable: ${powerTable}"
                state.powerTable = powerTable
            	//state.energyTable = energyTable
                state.lastRecordDt = getDtNow()
                //log.debug "powerTable: $powerTable"
                //log.debug "energyTable: $energyTable"
                def dPwrAvg = getDayPowerAvg()
                if(dPwrAvg != null) {
                    sendEvent(name: "dayPowerAvg", value: dPwrAvg, unit: "W", description: "Average Power Reading today was $dPwrAvg W", display: false, displayed: false)
                }
            }
        }
    } catch (ex) {
        log.error "handleData Exception:", ex
    }
}

def collectEnergy(val) {
    if (!state?.energyVal) {
        state?.energyVal = val
        return
    }
    def enerVal = state?.energyVal
    log.debug "collectEnergy: val: $val | Last: ${enerVal}"
    def res = (val.toDouble() - enerVal.toDouble()).round(2)
    log.debug "res: $res | = ${(enerVal.toDouble() + res)}"
    if(res > val) {
        state?.energyList = (enerVal.toDouble() + res)
    }
}

def getCurrentEnergy() {
    if(state?.energyVal) {
        return state?.energyVal ?: 0
    }
}

def getLastRecUpdSec() { return !state?.lastRecordDt ? 100000 : GetTimeDiffSeconds(state?.lastRecordDt, "getLastRecUpdSec")?.toInteger() }

private handleNewDay(curPow, curEner) {
    def dayMinPowerTable = state?.dayMinPowerTable
    def dayMaxPowerTable = state?.dayMaxPowerTable
    //def dayMinEnergyTable = state?.dayMinEnergyTable
    //def dayMaxEnergyTable = state?.dayMaxEnergyTable

    dayMinPowerTable.add(state?.minPowerReading)
    dayMaxPowerTable.add(state?.maxPowerReading)
    //dayMinEnergyTable.add(state?.minEnergyReading)
    //dayMaxEnergyTable.add(state?.maxEnergyReading)

    state?.minPowerReading = curPow
    state?.maxPowerReading = curPow
    //state?.minEnergyReading = curEner
    //state?.maxEnergyReading = curEner

    state?.dayMinPowerTable = dayMinPowerTable
    state?.dayMaxPowerTable = dayMaxPowerTable
    //state?.dayMinEnergyTable = dayMinEnergyTable
    //state?.dayMaxEnergyTable = dayMaxEnergyTable
    state?.dayMinPowerTable = []
    state?.dayMaxPowerTable = []
    // state?.dayMinEnergyTable = []
    // state?.dayMaxEnergyTable = []
    state?.dayPowerAvgTable = []
    
    def dailyPowerAvgTable = state?.dailyPowerAvgTable
    def dPwrAvg = getDayPowerAvg()
    if(dPwrAvg != null) {
        dailyPowerAvgTable.add(dPwrAvg)
    }
    state?.dailyPowerAvgTable = dailyPowerAvgTable

    state.lastPower = 0
}

def handleNewWeek() {
    def wkMinPowerTable = state?.wkMinPowerTable
    def wkMaxPowerTable = state?.wkMaxPowerTable
    // def wkMinEnergyTable = state?.wkMinEnergyTable
    // def wkMaxEnergyTable = state?.wkMaxEnergyTable
    def wkPowerAvgTable = state?.wkPowerAvgTable

    wkMinPowerTable.add(state?.dayMinPowerTable)
    wkMaxPowerTable.add(state?.dayMaxPowerTable)
    // wkMinEnergyTable.add(state?.dayMinEnergyTable)
    // wkMaxEnergyTable.add(state?.dayMaxEnergyTable)
    wlPowerAvgTable.add(state?.dayPowerAvgTable)

    state?.wkMinPowerTable = wkMinPowerTable
    state?.wkMaxPowerTable = wkMaxPowerTable
    // state?.wkMaxEnergyTable = wkMaxEnergyTable
    // state?.wkMinEnergyTable = wkMinEnergyTable
    state?.wkPowerAvgTable = wkPowerAvgTable

    state?.dayMinPowerTable = []
    state?.dayMaxPowerTable = []
    // state?.dayMinEnergyTable = []
    // state?.dayMaxEnergyTable = []
    state?.dayPowerAvgTable = []
}

def handleNewMonth() {
    def monMinPowerTable = state?.monMinPowerTable
    def monMaxPowerTable = state?.monMaxPowerTable
    // def monMinEnergyTable = state?.monMinEnergyTable
    // def monMaxEnergyTable = state?.monMaxEnergyTable
    def monPowerAvgTable = state?.monPowerAvgTable

    monMinPowerTable.add(state?.wkMinPowerTable)
    monMaxPowerTable.add(state?.wkMaxPowerTable)
    // monMinEnergyTable.add(state?.wkMinEnergyTable)
    // monMaxEnergyTable.add(state?.wkMaxEnergyTable)
    monPowerAvgTable.add(state?.wkPowerAvgTable)

    state?.monMinPowerTable = monMinPowerTable
    state?.monMaxPowerTable = monMaxPowerTable
    // state?.monMinEnergyTable = monMinEnergyTable
    // state?.monMaxEnergyTable = monMaxEnergyTable
    state?.monPowerAvgTable = monPowerAvgTable

    state?.wkMinPowerTable = []
    state?.wkMaxPowerTable = []
    // state?.wkMinEnergyTable = []
    // state?.wkMaxEnergyTable = []
    state?.wkPowerAvgTable = []
}

def getDayElapSec() {
	Calendar c = Calendar.getInstance();
	long now = c.getTimeInMillis();
	c.set(Calendar.HOUR_OF_DAY, 0);
	c.set(Calendar.MINUTE, 0);
	c.set(Calendar.SECOND, 0);
	c.set(Calendar.MILLISECOND, 0);
	long passed = now - c.getTimeInMillis();
	return (long) passed / 1000;
}

def getDayPowerAvg() {
    try {
        def result = null
        if(state?.powerTable?.size() >= 2) {
            def avgTmp = []
            state?.powerTable?.each() {
                if(it[2] != null) {
                    avgTmp?.add(it[2])
                }
            }
            if(avgTmp?.size() >= 2) {
                result = getListAvg(avgTmp).toInteger()
            }
        }
        return result
    } catch (ex) {
        log.error "getDayPowerAvg Exception:", ex
    }
}

def getAverage(items) {
    def tmpAvg = []
    def tmpVal = 0
	if(!items) { return tmpVal }
	else if(items?.size() > 1) {
		tmpAvg = items
		if(tmpAvg && tmpAvg?.size() > 1) { tmpVal = (tmpAvg?.sum() / tmpAvg?.size()) }
	}
	return tmpVal
}

def getListAvg(itemList, rnd=0) {
	//log.debug "itemList: ${itemList}"
	def avgRes = 0.0
	def iCnt = itemList?.size()
	if(iCnt >= 1) {
		if(iCnt > 1) {
			avgRes = (itemList?.sum().toDouble() / iCnt.toDouble()).toDouble()
		} else { itemList?.each { avgRes = avgRes + it.toDouble() } }
	}
	//log.debug "[getIntListAvg] avgRes: $avgRes"
	return avgRes.round(rnd)
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
    logWriter("todayCost: " + state?.currency?.dollar + uData?.todayCost)
    logWriter("monthUsage: " + uData?.monthUsage + " kWh")
    logWriter("monthCost: " + state?.currency?.dollar + uData?.monthCost)
    logWriter("monthEst: " + state?.currency?.dollar + uData?.monthEst)
    logWriter("monthBudget: " + state?.currency?.dollar + uData?.monthBudget)

    sendEvent(name: "todayUsage_str", value: "${state?.currency?.dollar}${uData?.todayCost} (${uData?.todayUsage} kWH)", display: false, displayed: false)
    sendEvent(name: "monthUsage_str", value: "${state?.monthName}\'s Usage:\n${state?.currency?.dollar}${uData?.monthCost} (${uData?.monthUsage} kWh)", display: false, displayed: false)
    sendEvent(name: "monthEst_str",   value: "${state?.monthName}\'s Bill (Est.):\n${state?.currency?.dollar}${uData?.monthEst}", display: false, displayed: false)
    sendEvent(name: "todayUsage", value: uData?.todayUsage, unit: state?.currency?.dollar, display: false, displayed: false)
    sendEvent(name: "monthUsage", value: uData?.monthUsage, unit: state?.currency?.dollar, display: false, displayed: false)
    sendEvent(name: "monthEst",   value: uData?.monthEst, unit: state?.currency?.dollar, display: false, displayed: false)

    if (uData?.monthBudget > 0) {
        budgPercent = Math.round(Math.round(uData?.monthCost?.toFloat()) / Math.round(uData?.monthBudget?.toFloat()) * 100)
        sendEvent(name: "budgetPercentage_str", value: "Monthly Budget:\nUsed ${budgPercent}% (${state?.currency?.dollar}${uData?.monthCost}) of ${state?.currency?.dollar}${uData?.monthBudget} ", display: false, displayed: false)
        sendEvent(name: "budgetPercentage", value: budgPercent, unit: "%", description: "Percentage of Budget User is (${budgPercent}%)", display: false, displayed: false)
    } else {
        budgPercent = 0
        sendEvent(name: "budgetPercentage_str", value: "Monthly Budget:\nBudget Not Set...", display: false, displayed: false)
    }
    logWriter("Budget Percentage: ${budgPercent}%")
    logWriter("")

    //Tariff Info
    logWriter("--------------UPDATE TARIFF DATA-------------")
    logWriter("tariff rate: " + tData?.tariffRate + state?.currency?.cent.toString())
    logWriter("")
    sendEvent(name: "tariffRate", value: tData?.tariffRate, unit: state?.currency?.cent.toString(), description: "Tariff Rate is ${tData?.tariffRate}${state?.currency?.cent.toString()}/kWh", display: false, displayed: false)
    sendEvent(name: "tariffRate_str", value: "Tariff Rate:\n${tData?.tariffRate}${state?.currency?.cent}/kWh", description: "Tariff Rate is ${tData?.tariffRate}${state?.currency?.cent.toString()}/kWh", display: false, displayed: false)

    //Updates Hub INFO Tiles
    logWriter("--------------UPDATE HUB DATA-------------")
    logWriter("hubVersion: " + hData?.hubVersion)
    logWriter("hubStatus: " + hData?.hubStatus)
    logWriter("hubName: " + hData?.hubName)
    logWriter("")
    state.hubStatus = (hData?.hubStatus == "on") ? "Active" : "InActive"
    state.hubVersion = hData?.hubVersion
    state.hubName = hData?.hubName
    sendEvent(name: "hubVersion", value: hData?.hubVersion, display: false, displayed: false)
    sendEvent(name: "hubStatus", value: hData?.hubStatus, display: false, displayed: false)
    sendEvent(name: "hubName", value: hData?.hubName, display: false, displayed: false)
}

def lastCheckinEvent(checkin) {
    //log.trace "lastCheckinEvent($checkin)..."
    def formatVal = "MMM d, yyyy - h:mm:ss a"
    def tf = new SimpleDateFormat(formatVal)
        tf.setTimeZone(location.timeZone)
    def lastConn = checkin ? "${tf?.format(Date.parse("E MMM dd HH:mm:ss z yyyy", checkin))}" : "Not Available"
    def lastChk = device.currentState("lastConnection")?.value
    state?.lastConnection = lastConn?.toString()
    if(!lastChk.equals(lastConn?.toString())) {
        logWriter("UPDATED | Last Hub Check-in was: (${lastConn}) | Original State: (${lastChk})")
        sendEvent(name: 'lastConnection', value: lastConn?.toString(), displayed: false, isStateChange: true)
    } else { logWriter("Last Hub Check-in was: (${lastConn}) | Original State: (${lastChk})") }
}

def lastUpdatedEvent() {
    def now = new Date()
    def formatVal = "MMM d, yyyy - h:mm:ss a"
    def tf = new SimpleDateFormat(formatVal)
    tf.setTimeZone(location.timeZone)
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
    //log.debug "debugStatus: ${state?.debugStatus}"
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
    //log.debug "devTypeVer: ${state?.devTypeVer}"
    state?.updateAvailable = isCodeUpdateAvailable(pubVer, dVer)
    if(!curData?.equals(newData)) {
        logWriter("UPDATED | Device Type Version is: (${newData}) | Original State: (${curData})")
        sendEvent(name: 'devTypeVer', value: newData, displayed: false)
    } else { logWriter("Device Type Version is: (${newData}) | Original State: (${curData})") }
}

def apiStatusEvent(issue) {
    def curStat = device.currentState("apiStatus")?.value
    def newStat = issue ? "Problems" : "Good"
    state?.apiStatus = newStat
    //log.debug "apiStatus: ${state?.apiStatus}"
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
    if(!tz) { log.warn("getTimeZone: SmartThings TimeZone is not found on your account...") }
    return tz
}

def formatDt(dt) {
    def tf = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
    if(location.timeZone) { tf.setTimeZone(location.timeZone) }
    else { log.warn "SmartThings TimeZone is not found or is not set... Please Try to open your ST location and Press Save..." }
    return tf?.format(dt)
}

//Returns time differences is seconds
def GetTimeDiffSeconds(lastDate, sender=null) {
    //log.trace "GetTimeDiffSeconds($lastDate, $sendera)"
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

def getDtNow() {
	def now = new Date()
	return formatDt(now)
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
|                  HTML TILE RENDER FUNCTIONS                |
**************************************************************/
String getDataString(Integer seriesIndex) {
	def dataString = ""
	def dataTable = []
	switch (seriesIndex) {
		case 1:
			dataTable = state.powerTableYesterday
			break
		case 2:
			dataTable = state.powerTable
			break
	}
	dataTable.each() {
		def dataArray = [[it[0],it[1],0],null,null]
		dataArray[seriesIndex] = it[2]
		dataString += dataArray.toString() + ","
	}
	return dataString
}

def getFileB64(url,preType,fileType) {
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
        log.error "getFileB64 Exception:", ex
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
    try {
        def params = [
            uri: url?.toString(),
            contentType: "text/plain"
        ]
        httpGet(params)  { resp ->
            return resp?.data.text
        }
    } catch (ex) {
        log.error "getJS Exception: ", ex
    }
}

def getCssData() {
    def cssData = null
    cssData = getFileB64(cssUrl(), "text", "css")
    return cssData
}

def getChartJsData() {
    def chartJsData = null
    chartJsData = getFileB64(chartJsUrl(), "text", "javascript")
    return chartJsData
}

def cssUrl() { return "https://raw.githubusercontent.com/tonesto7/efergy-manager/master/resources/style.css" }//"https://dl.dropboxusercontent.com/s/bg3o43vntlvqi5n/efergydevice.css" }

def chartJsUrl() { return "https://www.gstatic.com/charts/loader.js" }

def getImg(imgName) { return imgName ? "https://cdn.rawgit.com/tonesto7/efergy-manager/master/Images/Devices/$imgName" : "" }

def getStartTime() {
	def startTime = 24
	if (state?.powerTable.size()) { startTime = state?.powerTable?.min{it[0].toInteger()}[0].toInteger() }
	if (state?.powerTableYesterday.size()) { startTime = Math.min(startTime, state?.powerTableYesterday?.min{it[0].toInteger()}[0].toInteger())	}
	return startTime
    LogAction("startTime ${startTime}", "trace")
}

def getMinVal(Integer item) {
    def list = []
    if (state?.usageTableYesterday?.size() > 0) { list.add(state?.usageTableYesterday?.min { it[item] }[item].toInteger()) }
    if (state?.usageTable?.size() > 0) { list.add(state?.usageTable.min { it[item] }[item].toInteger()) }
    //log.trace "getMinVal: ${list.min()} result: ${list}"
    return list?.min()
}

def getMaxVal(Integer item) {
    def list = []
    if (state?.usageTableYesterday?.size() > 0) { list.add(state?.usageTableYesterday.max { it[item] }[item].toInteger()) }
    if (state?.usageTable?.size() > 0) { list.add(state?.usageTable.max { it[item] }[item].toInteger()) }
    //log.trace "getMaxVal: ${list.max()} result: ${list}"
    return list?.max()
}

def getGraphHTML() {
    try {
        def updateAvail = !state?.updateAvailable ? "" : """<h3 style="background: #ffa500;">Device Update Available!</h3>"""
        def chartHtml = (state?.powerTable.size() > 0) ? showChartHtml() : hideChartHtml()
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
              <table>
                <tr>
                  <th>Hub Name</th>
                </tr>
                <td>${state?.hubName}</td>
                </tbody>
              </table>
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
            </body>
        </html>
        """
        render contentType: "text/html", data: html, status: 200
    } catch (ex) {
        log.error "graphHTML Exception:", ex
    }
}

def showChartHtml() {
    try {
        def data = """
        <script type="text/javascript">
          google.charts.load('current', {packages: ['corechart']});
          google.charts.setOnLoadCallback(drawGraph);
          function drawGraph() {
          var data = new google.visualization.DataTable();
          data.addColumn('timeofday', 'time');
          data.addColumn('number', 'Power (Yesterday)');
          data.addColumn('number', 'Power (Today)');
          data.addRows([
            ${getDataString(1)}
            ${getDataString(2)}
          ]);
          var options = {
            fontName: 'San Francisco, Roboto, Arial',
            width: '100%',
            height: '100%',
            animation: {
              duration: 2500,
              startup: true,
              easing: 'inAndOut'
            },
            hAxis: {
              format: 'H:mm',
              minValue: [${getStartTime()},0,0],
              slantedText: true,
              slantedTextAngle: 30
            },
            series: {
              0: {targetAxisIndex: 0, color: '#fcd4a2', lineWidth: 1, visibleInLegend: false},
              1: {targetAxisIndex: 0, color: '#F8971D'}
            },
            vAxes: {
              0: {
                title: 'Power Used (W)',
                format: 'decimal',
                textStyle: {color: '#F8971D'},
                titleTextStyle: {color: '#F8971D'}
              }

            },
            legend: {
              position: 'none',
              maxLines: 4
            },
            chartArea: {
              left: '12%',
              right: '15%',
              top: '5%',
              bottom: '15%',
              height: '100%',
              width: '100%'
            }
          };
          var chart = new google.visualization.AreaChart(document.getElementById('chart_div'));
          chart.draw(data, options);
        }
          </script>
          <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #8CC63F; color: #f5f5f5;">Usage History</h4>
          <div id="chart_div" style="width: 100%; height: 225px;"></div>
        """
        return data
    } catch (ex) {
        log.error "showChartHtml Exception:", ex
    }
}

def hideChartHtml() {
    try {
        def data = """
        <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #8CC63F; color: #f5f5f5;">Usage History</h4>
        <br></br>
        <div class="centerText">
          <p>Waiting for more data to be collected...</p>
          <p>This may take a little while...</p>
        </div>
        """
        return data
    } catch (ex) {
        log.error "showChartHtml Exception:", ex
    }
}
