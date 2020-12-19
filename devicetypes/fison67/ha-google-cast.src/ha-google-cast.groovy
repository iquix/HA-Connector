/**
 *  HA Google Cast (v.0.0.4)
 *
 *  Authors
 *   - iquix@naver.com
 *   - fison67@nate.com
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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

metadata {
	definition (name: "HA Google Cast", namespace: "fison67", author: "fison67/iquix", vid: "generic-music-player") {
		capability "Actuator"
		capability "Music Player"
		capability "Media Playback"
		capability "Audio Volume"
		capability "Audio Mute"
		capability "Audio Track Data"
		capability "Speech Synthesis"
		capability "Audio Notification"
		capability "Notification"		
		capability "Refresh"
	}

	preferences {
		input name: "tts_service", title:"TTS Service", description: "Select TTS service that you setup in Home Assistant" , type: "enum", options: ["google_translate_say", "google_cloud_say", "watson_tts_say"], defaultValue: "google_translate_say"
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

def setHASetting(url, password, deviceId) {
	state.app_url = url
	state.app_pwd = password
	state.entity_id = deviceId
	state.hasSetStatusMap = true

	sendEvent(name: "ha_url", value: state.app_url, displayed: false)
}

def setStatus(status) {
	refresh()
}

def setStatusMap(Map obj) {
	refresh()
}

private setEnv(playstate, volume_level, is_volume_muted, media_title, media_artist, media_content_id) {
	log.debug "setEnv(${playstate}, ${volume_level}, ${is_volume_muted}, ${media_title}, ${media_artist}, ${media_content_id})"
 	sendEvent(name: "playbackStatus", value: (playstate == "playing" || playstate == "paused") ? playstate : "stopped", displayed: "true")
	sendEvent(name: "status", value: (playstate == "playing" || playstate == "paused") ? playstate : "stopped", displayed: "true")
	if (volume_level != null) {
		sendEvent(name: "volume", value: Math.round(volume_level*100), displayed: "true")
		sendEvent(name: "level", value: Math.round(volume_level*100))
	}
	sendEvent(name: "mute", value: ("${is_volume_muted}"=="true") ? "muted" : "unmuted", displayed: "true")
	Map trackData = [:]
	if (media_title != null) {
		trackData.title = media_title
		trackData.artist = media_artist
		sendEvent(name: "trackDescription", value: (media_artist ? "${media_artist} - ":"") + media_title )
	} else if (media_content_id != null) {
		def fname = media_content_id.split("\\/")[-1]
		trackData.title = fname
		sendEvent(name: "trackDescription", value: fname)
	} else {
		sendEvent(name: "trackDescription", value: null)
	}
	
   	sendEvent(name: "trackData", value: new groovy.json.JsonOutput().toJson(trackData))
   	sendEvent(name: "audioTrackData", value: new groovy.json.JsonOutput().toJson(trackData))
}

def play() {
	log.debug "play"
	processCommand("media_play", [ "entity_id": state.entity_id])
}

def pause() {
	log.debug "pause"
	processCommand("media_pause", [ "entity_id": state.entity_id])
}

def stop() {
	log.debug "stop"
	processCommand("media_stop", [ "entity_id": state.entity_id])
}

def nextTrack() {
	log.debug "nextTrack"
	processCommand("media_next_track", [ "entity_id": state.entity_id])
}

def previousTrack() {
	log.debug "previousTrack"
	processCommand("media_previous_track", [ "entity_id": state.entity_id])
}

def speak(String text) {
	log.debug "speak(${text})"
	processCommand(TTS, JsonOutput.toJson([ "entity_id": state.entity_id, "message":text]))
}

def playTrackAndResume(uri, level){
	log.debug "playTrackAndResume(${uri}, ${level})"
	playTrack(uri, level)
}

def playTrackAndRestore(uri, level){
	log.debug "playTrackAndRestore(${uri}, ${level})"
	playTrack(uri, level)
}

def playTrack(uri, level){
	log.debug "playTrack(${uri}, ${level})"
	setVolume(level)
	playURI(uri)
}

def playTrack(uri){
	log.debug "playTrack(${arg})"
	playURI(uri)
}

def deviceNotification(notification) {
	log.debug "deviceNotification(${notification})"
	speak(notification)
}

def mute() {
	setMute("muted")
}

def unmute() {
	setMute("unmuted")
}

def setMute(muteState) {
	log.debug "setMute(${muteState})"
	processCommand("volume_mute", [ "entity_id": state.entity_id, "is_volume_muted":(muteState=="muted")?"true":"false"])
}

def setVolume(level) {
	log.debug "setVolume(${level})"
	if (!(level >= 0 && level <= 100 )) {
		log.debug "volume should be in range 0~100"
		return
	}
	processCommand("volume_set", [ "entity_id": state.entity_id, "volume_level": level/100])
}

def setLevel(level) {
	setVolume(level)
}

def refresh() {
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

def playURI(u) {
	log.debug "playURI(${u})"
	processCommand("play_media", JsonOutput.toJson([ "entity_id": state.entity_id, "media_content_type": "music", "media_content_id":u]))
}

def installed() {
	state.hasSetStatusMap = true
	sendEvent(name: "supportedPlaybackCommands", value: JsonOutput.toJson(["play","pause","stop"]), displayed: false)
	refresh()
}

private processCommand(command, body){
	def temp = state.entity_id.split("\\.")
	if (command[-4..-1] == "_say") {
		temp[0] = "tts"
	}
	def options = [
	 	"method": "POST",
		"path": "/api/services/${temp[0]}/${command}",
		"headers": [
			"HOST": parent._getServerURL(),
			"Authorization": "Bearer " + parent._getPassword(),
			"Content-Type": "application/json;charset=utf-8"
		],
		"body":body
	]
	log.debug options
	sendCommand(options, null)
}

def callback(physicalgraph.device.HubResponse hubResponse){
	def msg
	try {
		msg = parseLanMessage(hubResponse.description)
		def jsonObj = new JsonSlurper().parseText(msg.body)
		setEnv(jsonObj.state, jsonObj?.attributes?.volume_level, jsonObj?.attributes?.is_volume_muted, jsonObj?.attributes?.media_title, jsonObj?.attributes?.media_artist, jsonObj?.attributes?.media_content_id)
	} catch (e) {
		log.error "Exception caught while parsing data: "+e;
	}
}

private sendCommand(options, _callback){
	def myhubAction = new physicalgraph.device.HubAction(options, null, [callback: _callback])
	sendHubCommand(myhubAction)
}

private getTTS() {
	tts_service ?: "google_translate_say"
}