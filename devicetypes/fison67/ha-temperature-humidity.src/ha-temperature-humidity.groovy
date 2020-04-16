/**
 *  HA Temperature & Humidity Sensor (v.0.0.1)
 *
 *  Authors
 *   - fison67@nate.com
 *   - iquix@naver.com
 *  Copyright 2019
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
	definition (name: "HA Temperature Humidity", namespace: "fison67", author: "fison67/iquix", ocfDeviceType: "oic.d.thermostat") {
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		capability "Battery"
		capability "Refresh"
		attribute "lastCheckin", "Date"
        attribute "entity_id", "String"
        attribute "ha_url", "String"
		command "setStatus"
        command "setStatusMap"
	}
    
	simulator {
	}

	tiles(scale: 2) {
        multiAttributeTile(name:"temperature", type:"generic", width:6, height:4) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("temperature", label:'${currentValue}°',
                    backgroundColors:[
						// Celsius color set
                        [value: 0, color: "#153591"], [value: 7, color: "#1e9cbb"], [value: 15, color: "#90d2a7"], [value: 23, color: "#44b621"], [value: 28, color: "#f1d801"], [value: 35, color: "#d04e00"], [value: 37, color: "#bc2323"]
                    ]
                )
            }
        }        
        valueTile("temperature2", "device.temperature", inactiveLabel: false) {
            state "temperature", label:'${currentValue}°', icon:"st.Weather.weather2",
            backgroundColors:[
                // Celsius color set
                [value: 0, color: "#153591"], [value: 7, color: "#1e9cbb"], [value: 15, color: "#90d2a7"], [value: 23, color: "#44b621"], [value: 28, color: "#f1d801"], [value: 35, color: "#d04e00"], [value: 37, color: "#bc2323"]
            ]
        }
        
        valueTile("humidity", "device.humidity", width: 2, height: 2, unit: "%") {
            state("val", label:'${currentValue}%', defaultState: true, 
            	backgroundColors:[
                    [value: 10, color: "#153591"],
                    [value: 30, color: "#1e9cbb"],
                    [value: 40, color: "#90d2a7"],
                    [value: 50, color: "#44b621"],
                    [value: 60, color: "#f1d801"],
                    [value: 80, color: "#d04e00"],
                    [value: 90, color: "#bc2323"]
                ]
            )
        }
                
        valueTile("battery", "device.battery", width: 2, height: 2) {
            state "val", label:'${currentValue}%', defaultState: true
        }		
        valueTile("humi", "title", decoration: "flat", inactiveLabel: false, width: 2, height: 1) {
	    	state "default", label:'Humidity'
        }		
        valueTile("bat", "title", decoration: "flat", inactiveLabel: false, width: 2, height: 1) {
	    	state "default", label:'Battery'
        }		
        valueTile("lastcheckin", "device.lastCheckin", inactiveLabel: false, decoration:"flat", width: 2, height: 3) {
        	state "lastcheckin", label:'Last Event:\n ${currentValue}'
        }
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh", icon:"st.secondary.refresh"
        }
		valueTile("ha_url", "device.ha_url", width: 3, height: 1) {
			state "val", label:'${currentValue}', defaultState: true
		}
		valueTile("entity_id", "device.entity_id", width: 3, height: 1) {
			state "val", label:'${currentValue}', defaultState: true
		}
        main("temperature2")
        details(["temperature", "humi", "bat", "lastcheckin", "humidity",  "battery", "refresh", "ha_url", "entity_id"])
	}
}

def parse(String description) {
	log.debug "Parsing '${description}'"
}

def setStatus(String value){
	log.debug "setStatus ${value}"
	refresh()
}

def setStatusMap(Map obj){
	def stat = obj["state"]
    def attr = obj["attr"]
	log.debug "setStatusMap ${stat}, ${attr}"
	setEnv(stat, attr.temperature, attr.battery)
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
	log.debug 'refresh called'
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

def setEnv(humValue, tempValue, batValue){
	if(state.entity_id == null){
		return
	}
	log.debug "Status[${state.entity_id}] >> humidity: ${humValue}, temperature: ${tempValue}, battery: ${batValue}"

	sendEvent(name: "humidity", value: humValue)
	sendEvent(name: "temperature", value: tempValue, unit: "C")
	sendEvent(name: "battery", value: batValue)
	sendEvent(name: "lastCheckin", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone), displayed: false)
}

def initialize() {
	state.hasSetStatusMap = true
	refresh()
}

def updated() {
	initialize()
}


def callbackRefresh(physicalgraph.device.HubResponse hubResponse){
	def msg
	try {
		msg = parseLanMessage(hubResponse.description)
		def jsonObj = new JsonSlurper().parseText(msg.body)
        log.debug "callback refresh called ${jsonObj}"
		setEnv(jsonObj.state, jsonObj.attributes.temperature, jsonObj.attributes.battery)
	} catch (e) {
		log.error "Exception caught while parsing data: "+e;
	}
}


def sendCommand(options, _callback){
	def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: _callback])
	sendHubCommand(myhubAction)
}