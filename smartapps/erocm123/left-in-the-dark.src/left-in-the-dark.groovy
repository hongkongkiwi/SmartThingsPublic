/**
 *  
 *	Left in the Dark
 *  
 *	Author: Eric Maycock (erocm123)
 *	email: erocmail@gmail.com
 *	Date: 2016-03-10
 *  
 *	This SmartApp is meant to solve the following issue: If you are still in a room and an 
 *  automation has turned off the lights because of motion inactivity. Instead of getting
 *  out your phone to turn them back on, you can just move around to get the lights back on.
 *   
 *  The App has a couple things to prevent false positives:
 *  1) It has to be an automation that triggers the light being turned off. If you turn it
 *     off at the switch or manually through the app, then it won't activate.
 *  2) There is a configurable timeout. This timeout starts once the light is turned off.
 *     The default is 60 seconds, so if motion is activated more than 60 seconds after the
 *     lights are turned off, then it won't turn them back on.
 *
 *  The App is meant to be configured with light & motion sensor "pairs". The app refers to 
 *  the "pairs" as "rooms" as I figured that would be the most common use case, but it is 
 *  really just an "area" of a home.
 *
 *  2016-03-12: Initial release.
 */
 


definition(
    name: "Left in the Dark",
    namespace: "erocm123",
    author: "Eric Maycock (erocm123)",
    description: "Don't be left in the dark if motion inactivity has turned off a light with you still in the room.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/MyApps/Cat-MyApps.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/MyApps/Cat-MyApps@2x.png"
)

preferences {
	page(name: "configureApp")
	page(name: "configureRoom")
	page(name: "timeIntervalInput", title: "Only during a certain time") {
		section {
			input "starting", "time", title: "Starting", required: false
			input "ending", "time", title: "Ending", required: false
		}
	}
}

def configureApp() {
	dynamicPage(name: "configureApp", nextPage: null, uninstall: configured(), install: true) {
        section {
        paragraph "This app will let you \"wave\" at a motion sensor to turn on selected lights. Useful if a \"motion inactive\" event has left you in the dark. It will only trigger if it detects motion x (configurable in settings) seconds after the light is turned off."
        }
        section("Settings") {
                input "numberOfSeconds", "number", title: "Seconds after lights turn off to have motion turn them back on.", required: true, value: 60, defaultValue: 60, submitOnChange: false
                input "numberOfRooms", "enum", title: "Number of rooms?", required: true, value: 1, defaultValue: 1, submitOnChange: true, options: [
                1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24]
                def configDescription = ""
                for (int i = 1; i <= (numberOfRooms as Integer); i++){
                   configDescription = ""
                   if (settings["lights_${i}"] == null && settings["motion_${i}"] == null) {
                      configDescription = "Click to configure"
                   } else {
                      if (settings["lights_${i}"] != null) { 
                      settings["lights_${i}"].each {
                         configDescription += "${it.displayName}, "
                      }
                      }
                      if (settings["motion_${i}"] != null) {
                      settings["motion_${i}"].each {
                         configDescription += "${it.displayName}, "
                      }
                      }
                      configDescription = configDescription.substring(0, configDescription.length() - 2)
                      }
                      href "configureRoom", title:"Configure Room $i", description:"$configDescription", params: [room: i]
                }
            }
        
        
		section(title: "More Settings", hidden: hideOptionsSection(), hideable: true) {
            def timeLabel = timeIntervalLabel()
            href "timeIntervalInput", title: "Only during a certain time", description: timeLabel ?: "Tap to set", state: timeLabel ? "complete" : null
            input "days", "enum", title: "Only on certain days of the week", multiple: true, required: false,
				options: ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            input "modes", "mode", title: "Only when mode is", multiple: true, required: false
            input "debugBool", "boolean", title: "Enable Debug Logging", required: false, value: false
		}
        section([title:"Available Options", mobileOnly:true]) {
			label title:"Assign a name for your app (optional)", required:false
		}
	}
}

def configureRoom(params) {
    if (params.room != null) state.currentRoom = params.room.toInteger() //log.debug "$params.pbutton"
    dynamicPage(name: "configureRoom", title: "Choose the lights and motion sensors that will be used in this room.",
	uninstall: configured(), getButtonSections(state.currentRoom))
}

def getButtonSections(buttonNumber) {
	return {
		section("Lights") {
			input "lights_${buttonNumber}", "capability.switch", multiple: true, required: false, submitOnChange: false
		}
        section("Motion Sensor") {
			input "motion_${buttonNumber}", "capability.motionSensor", multiple: true, required: false, submitOnChange: false
		}

	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
    for (int i = 1; i <= (numberOfRooms as Integer); i++){
       if (settings["lights_${i}"] != null) { 
          subscribe(settings["lights_${i}"], "switch.off", lightEvent)
       }
       if (settings["motion_${i}"] != null) {
          subscribe(settings["motion_${i}"], "motion.active", motionEvent)
       }
    }
}

def configured() {
	
}

def lightEvent(evt){
if (allOk) {
   if (debugBool) log.debug "name: $evt.displayName id: $evt.deviceId source: $evt.source"
   def roomNumber
   for (int i = 1; i <= (numberOfRooms as Integer); i++){
       if (settings["lights_${i}"].find{it.id == evt.deviceId}) { 
          roomNumber = i
       }
    }
    if (roomNumber) {
       def myEvents = settings["lights_${roomNumber}"].find{it.id == evt.deviceId}.eventsSince(new Date(now() - 3000), [all:true, max: 5]).findAll{(it.source as String) == "APP_COMMAND"}
       if (myEvents) {
          if (debugBool) log.debug "A SmartApp triggered these events: ${myEvents?.source}"
          roomTrue(roomNumber)
          if (settings.numberOfSeconds == null || settings.numberOfSeconds == "") settings.numberOfSeconds = 60
          runIn(settings.numberOfSeconds, "roomFalse$roomNumber")
       }
    }
}
}

def motionEvent(evt){
if (allOk) {
   if (debugBool) log.debug "name: $evt.displayName id: $evt.deviceId source: $evt.source"
   def roomNumber
   for (int i = 1; i <= (numberOfRooms as Integer); i++){
       if (settings["motion_${i}"].find{it.id == evt.deviceId}) roomNumber = i
   }
   if (roomNumber) {
      if (state."room${roomNumber}" && state."room${roomNumber}".toBoolean() == true) {
          if (debugBool) log.debug "We need to turn the lights on" 
          settings["lights_${roomNumber}"].on()
      } else {
          if (debugBool) log.debug "No need to turn the lights on"
      }
   }
}
}

def roomTrue(roomNumber){
   state."room${roomNumber}" = true
   if (debugBool) log.debug "Setting room$roomNumber to ${state."room${roomNumber}"}"
}

def roomFalse(roomNumber){
   state."room${roomNumber}" = false
   if (debugBool) log.debug "Setting room$roomNumber to ${state."room${roomNumber}"}"
}

// execution filter methods
private getAllOk() {
	modeOk && daysOk && timeOk
}

private getModeOk() {
	def result = !modes || modes.contains(location.mode)
	if (debugBool) if (!result) log.trace "modeOk = $result"
	result
}

private getDaysOk() {
	def result = true
	if (days) {
		def df = new java.text.SimpleDateFormat("EEEE")
		if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("America/New_York"))
		}
		def day = df.format(new Date())
		result = days.contains(day)
	}
	if (debugBool) if (!result) log.trace "daysOk = $result"
	result
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting).time
		def stop = timeToday(ending).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
	if (debugBool) if (!result) log.trace "timeOk = $result"
	result
}

private hhmm(time, fmt = "h:mm a")
{
	def t = timeToday(time, location.timeZone)
	def f = new java.text.SimpleDateFormat(fmt)
	f.setTimeZone(location.timeZone ?: timeZone(time))
	f.format(t)
}

private hideOptionsSection() {
	(starting || ending || days || modes) ? false : true
}

private timeIntervalLabel() {
	(starting && ending) ? hhmm(starting) + "-" + hhmm(ending, "h:mm a z") : ""
}

def roomTrue1() {
   roomTrue(1)
}

def roomFalse1() {
   roomFalse(1)
}

def roomTrue2() {
   roomTrue(2)
}

def roomFalse2() {
   roomFalse(2)
}

def roomTrue3() {
   roomTrue(3)
}

def roomFalse3() {
   roomFalse(3)
}

def roomTrue4() {
   roomTrue(4)
}

def roomFalse4() {
   roomFalse(4)
}

def roomTrue5() {
   roomTrue(5)
}

def roomFalse5() {
   roomFalse(5)
}

def roomTrue6() {
   roomTrue(6)
}

def roomFalse6() {
   roomFalse(6)
}

def roomTrue7() {
   roomTrue(7)
}

def roomFalse7() {
   roomFalse(7)
}

def roomTrue8() {
   roomTrue(8)
}

def roomFalse8() {
   roomFalse(8)
}

def roomTrue9() {
   roomTrue(9)
}

def roomFalse9() {
   roomFalse(9)
}

def roomTrue10() {
   roomTrue(10)
}

def roomFalse10() {
   roomFalse(10)
}

def roomTrue11() {
   roomTrue(11)
}

def roomFalse11() {
   roomFalse(11)
}

def roomTrue12() {
   roomTrue(12)
}

def roomFalse12() {
   roomFalse(12)
}

def roomTrue13() {
   roomTrue(13)
}

def roomFalse13() {
   roomFalse(13)
}

def roomTrue14() {
   roomTrue(14)
}

def roomFalse14() {
   roomFalse(14)
}

def roomTrue15() {
   roomTrue(15)
}

def roomFalse15() {
   roomFalse(15)
}

def roomTrue16() {
   roomTrue(16)
}

def roomFalse16() {
   roomFalse(16)
}

def roomTrue17() {
   roomTrue(17)
}

def roomFalse17() {
   roomFalse(17)
}

def roomTrue18() {
   roomTrue(18)
}

def roomFalse18() {
   roomFalse(18)
}

def roomTrue19() {
   roomTrue(19)
}

def roomFalse19() {
   roomFalse(19)
}

def roomTrue20() {
   roomTrue(20)
}

def roomFalse20() {
   roomFalse(20)
}

def roomTrue21() {
   roomTrue(21)
}

def roomFalse21() {
   roomFalse(21)
}

def roomTrue22() {
   roomTrue(22)
}

def roomFalse22() {
   roomFalse(22)
}

def roomTrue23() {
   roomTrue(23)
}

def roomFalse23() {
   roomFalse(23)
}

def roomTrue24() {
   roomTrue(24)
}

def roomFalse24() {
   roomFalse(24)
}