/**
 *  HA Fan (v.0.0.3-iquix patch for xiaomi_miio_fan custom_component)
 *
 *  Authors
 *   - fison67@nate.com / iquix@naver.com
 *  Copyright 2020
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
 */
 

import groovy.json.JsonSlurper

metadata {
	definition (name: "HA Fan", namespace: "fison67", author: "fison67/iquix", ocfDeviceType: "oic.d.fan") {
        capability "Switch"						
		capability "Fan Speed"
        capability "Switch Level"
		capability "Refresh"
        capability "Actuator"
        capability "Fan Oscillation Mode"

		command "low"
		command "medium"
		command "high"
		command "max"
		command "raiseFanSpeed"
		command "lowerFanSpeed"

        attribute "lastCheckin", "Date"
         
	}


	simulator { }
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
				attributeState "VALUE_UP", action: "raiseFanSpeed"
				attributeState "VALUE_DOWN", action: "lowerFanSpeed"
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
    setEnv(map.state, map?.attr["speed"], map?.attr["oscillating"])
}

def setEnv(stat, speed, oscillating) {
	log.debug "setEnv(${stat}, ${speed}, ${oscillating})"
	def level
 	setStatus(stat)
    if (speed != null){
    	if (speed == "Level 1") {
        	level = 1
        } else if (speed == "Level 2"){
        	level = 35
        }else if (speed == "Level 3"){
        	level = 70
        }else if (speed == "Level 4"){
        	level = 100
		}else{
        	level = 0
        }
    	sendEvent(name: "level", value:level)
        sendEvent(name: "fanSpeed", value: getFanSpeedValue(level))
    }
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
	if (speed as Integer == 0) {
		off()
        sendEvent(name: "fanSpeed", value: device.currentValue("fanSpeed"), displayed: true)
	} else if (speed as Integer == 1) {
		low()
	} else if (speed as Integer == 2) {
		medium()
	} else if (speed as Integer == 3) {
		high()
	} else if (speed as Integer == 4) {
		max()
	}
}

def raiseFanSpeed() {
	setFanSpeed(Math.min((device.currentValue("fanSpeed") as Integer) + 1, 4))
}

def lowerFanSpeed() {
	setFanSpeed(Math.max((device.currentValue("fanSpeed") as Integer) - 1, 0))
}

def low() {
	setLevel(1)
}

def medium() {
	setLevel(35)
}

def high() {
	setLevel(70)
}

def max() {
	setLevel(100)
}

def setLevel(level){
	def speed
    if (level == 0) {
    	off()
    } else if(0 < level && level <= 25){
    	speed = "Level 1"
    }else if(25 < level && level <= 50){
    	speed = "Level 2"
    }else if(50 < level && level <= 75){
    	speed = "Level 3"
	}else{
    	speed = "Level 4"
    }
    sendEvent(name: "level", value: level) 
    sendEvent(name: "fanSpeed", value: getFanSpeedValue(level), displayed: true)
	processCommand("set_speed", [ "entity_id": state.entity_id, "speed":speed ])
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
        setEnv(jsonObj.state, jsonObj?.attributes?.speed, jsonObj?.attributes?.oscillating)
    } catch (e) {
        log.error "Exception caught while parsing data: "+e;
    }
}

def updated() {
	sendEvent(name: "fanOscillationMode", value: "fixed", displayed: false)
}

def sendCommand(options, _callback){
	def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: _callback])
    sendHubCommand(myhubAction)
}

def getFanSpeedValue(rawLevel) {
	if (rawLevel == 0 ) {
    	return 0
	} else if (0 < rawLevel && rawLevel <= 25) {
		return 1
	} else if (25 < rawLevel && rawLevel <= 50) {
		return 2
	} else if (50 < rawLevel && rawLevel <= 75) {
		return 3
	} else if (75 < rawLevel && rawLevel <= 100) {
		return 4
	}
}