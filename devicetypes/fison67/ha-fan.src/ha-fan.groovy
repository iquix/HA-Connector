/**
 *  HA Fan (v.0.0.6-iquix patch for xiaomi_miio_fan custom_component)
 *
 *  Authors
 *   - fison67@nate.com / iquix@naver.com
 *  Copyright 2020
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */


import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
    definition (name: "HA Fan", namespace: "fison67", author: "fison67/iquix", ocfDeviceType: "oic.d.fan", mnmn: "SmartThingsCommunity", vid: "687ca757-1692-3c99-9821-686a5f6bad0c") {
        capability "Switch"						
        capability "Fan Speed"
        capability "Switch Level"
        capability "Fan Oscillation Mode"
        capability "Stateless Fanspeed Button"
        capability "Refresh"
        capability "Actuator"

        command "low"
        command "medium"
        command "high"
        command "max"

        attribute "lastCheckin", "Date"

    }
    preferences {
        input name: "baseValue", title:"HA On Value" , type: "string", required: true, defaultValue: "on"
    }

    tiles(scale: 2) {
        multiAttributeTile(name: "fanSpeed", type: "generic", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.fanSpeed", key: "PRIMARY_CONTROL") {
                attributeState "0", label: "off", action: "switch.on", icon: "st.thermostat.fan-off", backgroundColor: "#ffffff"
                attributeState "1", label: "level 1", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
                attributeState "2", label: "level 2", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
                attributeState "3", label: "level 3", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
                attributeState "4", label: "level 4", action: "switch.off", icon: "st.thermostat.fan-on", backgroundColor: "#00a0dc"
            }
            tileAttribute("device.fanSpeed", key: "VALUE_CONTROL") {
                attributeState "VALUE_UP", action: "fanspeedUp"
                attributeState "VALUE_DOWN", action: "fanspeedDown"
            }
        }

        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
        }
        main "fanSpeed"
        details(["fanSpeed", "refresh"])
    }
}

// parse events into attributes
def parse(String description) {
    log.debug "Parsing '${description}'"
}

def setHASetting(url, password, deviceId){
    state.app_url = url
    state.app_pwd = password
    state.entity_id = deviceId
    state.hasSetStatusMap = true

    sendEvent(name: "ha_url", value: state.app_url, displayed: false)
}

def setStatusMap(map){
    log.debug map
    setEnv(map.state, map?.attr["preset_mode"], map?.attr["percentage"], map?.attr["oscillating"])
}

def setEnv(stat, preset_mode, percentage, oscillating) {
    log.debug "setEnv(${stat}, ${preset_mode}, ${percentage}, ${oscillating})"
    setStatus(stat)
    sendEvent(name: "level", value: percentage)
    sendEvent(name: "fanSpeed", value: getFanSpeedValue(preset_mode))
    sendEvent(name: "fanOscillationMode", value: ("${oscillating}" == "true") ? "horizontal" : "fixed", displayed: true)
}

def setStatus(String value){
    if(state.entity_id == null){
        return
    }
    log.debug "Status[${state.entity_id}] >> ${value}"

    def now = new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
    def baseVal = baseValue
    if(baseVal == null){
        baseVal = "on"
    }
    def _value = (baseVal == value ? "on" : "off")

    if(device.currentValue("switch") != _value){
        sendEvent(name: (_value == "on" ? "lastOn" : "lastOff"), value: now, displayed: false )
    }
    sendEvent(name: "switch", value:_value, displayed: true)
    sendEvent(name: "lastCheckin", value: now, displayed: false)
    sendEvent(name: "entity_id", value: state.entity_id, displayed: false)
}

def refresh(){
    log.debug "refresh()"
    def options = [
        "method": "GET",
        "path": "/api/states/${state.entity_id}",
        "headers": [
            "HOST": parent._getServerURL(),
            "Authorization": "Bearer " + parent._getPassword(),
            "Content-Type": "application/json"
        ]
    ]
    sendCommand(options, callback)
}

def setFanSpeed(speed) {
    speed = speed as Integer
    if (speed == 0) {
        off()
        sendEvent(name: "fanSpeed", value: device.currentValue("fanSpeed"), displayed: false)
    } else if (speed >= 1 && speed <= 4) {
        processCommand("set_preset_mode", [ "entity_id": state.entity_id, "preset_mode": "Level ${speed}" ])
    }
}

def fanpeedUp() {
    setFanSpeed(Math.min((device.currentValue("fanSpeed") as Integer) + 1, 4))
}

def fanspeedDown() {
    setFanSpeed(Math.max((device.currentValue("fanSpeed") as Integer) - 1, 0))
}

def low() {
    setFanSpeed(1)
}

def medium() {
    setFanSpeed(2)
}

def high() {
    setFanSpeed(3)
}

def max() {
    setFanSpeed(4)
}

def setLevel(level){
    processCommand("set_percentage", [ "entity_id": state.entity_id, "percentage":level ])
    if (level > 0 && device.currentValue("switch") == "off") {
        on()
    }
}	

def on(){
    processCommand("turn_on", [ "entity_id": state.entity_id ])
}

def off(){
    processCommand("turn_off", [ "entity_id": state.entity_id ])
}

def setFanOscillationMode(mode) {
    log.debug "setFanOscillationMode(${mode})"
    if (mode != "horizontal" && mode != "fixed") {
        sendEvent(name: "fanOscillationMode", value: device.currentValue("fanOscillationMode"), displayed: false)
        return
    }
    def ha_mode = (mode == "horizontal") ? "true" : "false"
    processCommand("oscillate", [ "entity_id": state.entity_id, "oscillating": ha_mode ])	
}

def processCommand(command, body){
    def temp = state.entity_id.split("\\.")
    def options = [
        "method": "POST",
        "path": "/api/services/${temp[0]}/${command}",
        "headers": [
            "HOST": parent._getServerURL(),
            "Authorization": "Bearer " + parent._getPassword(),
            "Content-Type": "application/json"
        ],
        "body":body
    ]
    sendCommand(options, null)
}

def callback(physicalgraph.device.HubResponse hubResponse){
    def msg
    try {
        msg = parseLanMessage(hubResponse.description)
        def jsonObj = new JsonSlurper().parseText(msg.body)
        setEnv(jsonObj.state, jsonObj?.attributes?.preset_mode, jsonObj?.attributes?.percentage, jsonObj?.attributes?.oscillating)
    } catch (e) {
        log.error "Exception caught while parsing data: "+e;
    }
}

def installed() {
    sendEvent(name: "fanOscillationMode", value: "fixed", displayed: false)
    sendEvent(name: "supportedFanOscillationModes", value: JsonOutput.toJson(["fixed", "horizontal"]), displayed: false)
    sendEvent(name: "availableFanspeedButtons", value: JsonOutput.toJson(["fanspeedUp", "fanspeedDown"]), displayed: false)	
}

def sendCommand(options, _callback){
    def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: _callback])
    sendHubCommand(myhubAction)
}

def getFanSpeedValue(preset_mode) {
    switch(preset_mode) {
        case "off":
            return 0
        case "Level 1":
            return 1
        case "Level 2":
            return 2
        case "Level 3":
            return 3
        case "Level 4":
            return 4
    }
}