/**
 *  HA Heater (v.0.0.14)
 *
 *  Authors
 *   - fison67@nate.com
 *   - iquix@naver.com
 *  Copyright 2019-2021
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

metadata {
	definition (name: "HA Heater", namespace: "fison67", author: "fison67/iquix", mnmn: "SmartThingsCommunity", vid: "94d9a579-a027-31d5-b534-9da3833b1ffa") {
		capability "Thermostat"
		capability "Thermostat Mode"
		capability "Thermostat Heating Setpoint"
		capability "Temperature Measurement"	
		capability "Thermostat Operating State"
		capability "Refresh"
		capability "Actuator"
		capability "Sensor"
		attribute "lastCheckin", "Date"
		command "setStatus"
		command "setStatusMap"
	}
	
	tiles {
		multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4){
			tileAttribute ("device.thermostatMode", key: "PRIMARY_CONTROL") {
				attributeState "heat", label:'${name}', action:"off", icon:"st.thermostat.heat", backgroundColor:"#e86d13", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"heat", icon:"st.thermostat.heating-cooling-off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"off", icon:"st.thermostat.heat", backgroundColor:"#e86d13", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"heat", icon:"st.thermostat.heating-cooling-off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
				attributeState("default", label:'Last Update: ${currentValue}',icon: "st.Health & Wellness.health9")
			}			
		}
		controlTile("temperatureControl", "device.heatingSetpoint", "slider", sliderType: "HEATING", range:"(5..40)", height: 2, width: 3) {
			state "default", action:"setHeatingSetpoint", backgroundColor: "#E86D13"
		}
		valueTile("curTemp_label", "", decoration: "flat", width: 3, height: 1) {
			state "default", label:'Current\nTemp'
		}
		valueTile("temperature", "device.temperature", decoration: "flat", width: 3, height: 1) {
			state "default", label:'${currentValue}'
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
			state "default", label:"", action:"refresh", icon:"st.secondary.refresh"
		}
		valueTile("ha_url", "device.ha_url", width: 3, height: 1) {
			state "val", label:'${currentValue}', defaultState: true
		}
		valueTile("entity_id", "device.entity_id", width: 3, height: 1) {
			state "val", label:'${currentValue}', defaultState: true
		}
	}
}

def parse(String description) {
	log.debug "Parsing '${description}'"
}

def setStatus(String value){
	log.debug "setStatus called"
	refresh()
}

def setStatusMap(Map obj) {
	def stat = obj["state"]
	def attr = obj["attr"]
	log.debug "setStatusMap[${state.entity_id}] >> ${stat}, ${attr}"
	setEnv(stat, attr.temperature, attr.current_temperature, attr?.hvac_action)
}

def setHASetting(url, password, deviceId){
	state.app_url = url
	state.app_pwd = password
	state.entity_id = deviceId	
	sendEvent(name: "ha_url", value: state.app_url, displayed: false)
	sendEvent(name: "entity_id", value: state.entity_id, displayed: false)
	initialize()
}

def refresh(){
	state.hasSetStatusMap = true
	def options = [
		"method": "GET",
		"path": "/api/states/${state.entity_id}",
		"headers": [
			"HOST": parent._getServerURL(),
			"Authorization": "Bearer " + parent._getPassword(),
			"Content-Type": "application/json"
		]
	]
	sendCommand(options, callbackRefresh)
}

def setEnv(String statusValue, setpointValue, currentTemperatureValue, hvacActionValue = null){
	if(state.entity_id == null || state.HAsupportedModes == null){
		return
	}
	def _value = mapSTMode(statusValue)
	if (_value) {
		//sendEvent(name: "switch", value: (_value=="off")? "off" : "on", displayed: true)	
		sendEvent(name: "thermostatMode", value: _value, displayed: true)
	}
	sendEvent(name: "heatingSetpoint", value: ((setpointValue*2+0.5) as int)/2, unit: "C", displayed: true)
	sendEvent(name: "temperature", value: ((currentTemperatureValue*2+0.5) as int)/2, unit: "C", displayed: true)
	if (hvacActionValue) {
		sendEvent(name: "thermostatOperatingState", value: (hvacActionValue=="off") ? "idle" : hvacActionValue, displayed: true)
	} else {
		sendEvent(name: "thermostatOperatingState", value: ((_value=="heat")&&(currentTemperatureValue<setpointValue))? "heating" : "idle", displayed: true)
	}
	sendEvent(name: "lastCheckin", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: false)
}

def setHeatingSetpoint(temperature){
	processCommand("set_temperature", [ "entity_id": state.entity_id, "temperature": temperature ])
}

def setThermostatMode(mode){
	log.debug "setThermostatMode(${mode}) called"
	def _mode = mapHAMode(mode)
	if (_mode) {
		if (state.HAversion!="old") {	// HA version >= 0.96
			processCommand("set_hvac_mode", [ "entity_id": state.entity_id, "hvac_mode": _mode ])
		} else {	// HA version < 0.96
			processCommand("set_operation_mode", [ "entity_id": state.entity_id, "operation_mode": _mode ])
		}
	}
}

def heat(){
	setThermostatMode("heat")
}

def off(){
	setThermostatMode("off")
}

def on() {
	heat()
}

def initialize() {
	state.hasSetStatusMap = true
	def options = [
		"method": "GET",
		"path": "/api/states/${state.entity_id}",
		"headers": [
			"HOST": parent._getServerURL(),
			"Authorization": "Bearer " + parent._getPassword(),
			"Content-Type": "application/json"
		]
	]
	sendCommand(options, callbackInitialize)
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
	log.debug options
	sendCommand(options, null)
}

def callbackRefresh(physicalgraph.device.HubResponse hubResponse){
	def msg
	try {
		msg = parseLanMessage(hubResponse.description)
		def jsonObj = new JsonSlurper().parseText(msg.body)
		log.debug "Refresh[${state.entity_id}] >> mode: ${jsonObj.state}, heatingSetpoint: ${jsonObj.attributes.temperature}, currentTemp: ${jsonObj.attributes.current_temperature} hvacAction: ${jsonObj.attributes?.hvac_action}"
		setEnv(jsonObj.state, jsonObj.attributes.temperature, jsonObj.attributes.current_temperature, jsonObj.attributes?.hvac_action)
	} catch (e) {
		log.error "Exception caught while parsing data: "+e;
	}
}

def callbackInitialize(physicalgraph.device.HubResponse hubResponse){
	def msg, iState, iSetTemp, iCurTemp
	def STsupportedModes = ["auto","cool","eco","rush hour","emergency heat", "energysaveheat", "fanonly", "heat", "off"]
	def HAsupportedModes = []
	def supportedModes = []
	try {
		msg = parseLanMessage(hubResponse.description)
		def jsonObj = new JsonSlurper().parseText(msg.body)
		if (jsonObj.attributes.containsKey("operation_list")) {
			HAsupportedModes.addAll(jsonObj.attributes.operation_list)
			state.HAversion = "old"
			log.debug "Initialize[${state.entity_id}] HA version < 0.96 detected"
		} else if (jsonObj.attributes.containsKey("hvac_modes")) {
			HAsupportedModes.addAll(jsonObj.attributes.hvac_modes)	
			state.HAversion = "new"
			log.debug "Initialize[${state.entity_id}] HA version >= 0.96 detected"
		}
		iState = jsonObj.state
		iSetTemp = jsonObj.attributes.temperature
		iCurTemp = jsonObj.attributes.current_temperature
		iHvacAction = jsonObj.attributes?.hvac_action
	} catch (e) {
		log.error "Initialize : Exception caught while parsing data: "+e;
	}
	for (m in HAsupportedModes) {
		def m_map = mapSTMode(m)
		if(STsupportedModes.count(m_map) > 0) {
			supportedModes << m_map
		}
	}
	state.HAsupportedModes = HAsupportedModes
	state.supportedModes = supportedModes
	setEnv(iState, iSetTemp, iCurTemp, iHvacAction)
	sendEvent(name: "supportedThermostatModes", value: supportedModes, displayed: false)
}

def updated() {
	initialize()
}

def sendCommand(options, _callback){
	def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: _callback])
	sendHubCommand(myhubAction)
}

def mapSTMode(ha_mode) {
	def ret = null
	Map mapping = ["on": "heat", "off": "off", "heat": "heat", "cool": "cool", "heat_cool": "auto", "auto": "auto", "dry": "dryair", "fan_only": "eco"]
	try {
		ret = mapping[ha_mode]
	} catch(e) {
	}
	return ret
}

def mapHAMode(st_mode) {
	def ret = null
	Map mapping = ["off": "off", "heat": "heat", "cool": "cool", "auto": "auto", "dryair": "dry", "eco": "fan_only", "fanonly": "fan_only"]
	try {
		ret = mapping[st_mode]
	} catch(e) {
	}
	ret = (state.HAsupportedModes.count("heat") == 0 && state.HAsupportedModes.count("on") > 0 && ret == "heat" ) ? "on" : ret	// for compatibility
	return ret
}