/*
 *  Copyright (C) 2016 Pascal Bodin
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.systev.municserver;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.stream.JsonParsingException;

/**
 * 
 * Methods of this class are called by the POST handler: they
 * should not be blocking. 
 * 
 * They must be called in this order:
 * - create a DecodeJson object
 * - call getNextArrayObject() method until false is returned 
 *   - call getPayloadType() method on every returned object
 *   - depending on payload type, call getPresence(), getMessage() or getTrack()
 * - call closeJson()
 *
 */
public class DecodeJson {
	
	public enum PayloadType {
		PRESENCE, MESSAGE, TRACK, ERROR
	};
	
	// Strings defining type of JSON payload.
	private final static String VAL_PRESENCE = "presence";
	private final static String VAL_MESSAGE = "message";
	private final static String VAL_TRACK = "track";
	
	public final static long BAD_VAL = -1;
	public final static String BAD_STRING = "*";
	
	// Reader used to parse the JSON document.
	private JsonReader jsonReader;
	// Top-level JSON array.
	private JsonArray jsonArray;
	// Index to next array object.
	private int objectIndex;
	// Current object.
	private JsonObject jsonObject;
	
	private Logger logger;
	
	private final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	
	/**
	 * 
	 * @param jsonDocument
	 */
	public DecodeJson(String jsonDocument, Logger logger) {
		
		jsonReader = Json.createReader(new StringReader(jsonDocument));
		this.logger = logger;
		objectIndex = 0;
		logger.fine("DecodeJson - document to parse: " + jsonDocument);
		
	}
	
	/**
	 * 
	 * @return false if an object can't be read, true otherwise
	 */
	public boolean getNextArrayObject() {
		
		if (objectIndex == 0) {
			// First call: create jsonArray.
			try {
				jsonArray = jsonReader.readArray();
			} catch (JsonParsingException|IllegalStateException e) {
				logger.severe("**** error **** no array found");
				return false;
			} catch (JsonException e) {
				// In another catch statement, otherwise hides JsonParsingException.
				logger.severe("**** error **** no array found");
				return false;
			}
		}
		// Get current object.
		try {
			jsonObject = jsonArray.getJsonObject(objectIndex);
			objectIndex++;
			return true;
		} catch (IndexOutOfBoundsException e) {
			// No more object to parse.
			return false;
		} catch (ClassCastException e) {
			logger.severe("**** error **** can't parse next object");
			return false;
		}
		
	}
	
	/**
	 * 
	 * @return
	 */
	public PayloadType getPayloadType() {
		
		JsonObject metaObject;
		
        try {
        	// Try to get the meta object, to know payload type.
        	metaObject = jsonObject.getJsonObject("meta");
        	if (metaObject == null) {
        		logger.severe("getPayloadType: no meta object");
        		return PayloadType.ERROR;
        	}
        	// At this stage, we have a meta object. Check event key.
        	// If not present, a NullPointerException is triggered.
        	String val = metaObject.getString("event");
        	if (val.contentEquals(VAL_MESSAGE)) {
        		logger.fine("getPayloadType: messqge");
        		return PayloadType.MESSAGE;
        	}
        	if (val.contentEquals(VAL_PRESENCE)) {
        		logger.fine("getPayloadType: presence");
        		return PayloadType.PRESENCE;
        	}
        	if (val.contentEquals(VAL_TRACK)) {
        		logger.fine("getPayloadType: track");
        		return PayloadType.TRACK;
        	}
        	// At this stage, unknown type of payload.
        	logger.severe("getPayloadType: unknown type of event - " + val);
        	return PayloadType.ERROR;
        } catch (JsonParsingException|IllegalStateException|ClassCastException|NullPointerException e) {
        	logger.severe("getPayloadType: " + e.getMessage());
        	return PayloadType.ERROR;
        } catch (JsonException e) {
        	// Separate catch for JsonException otherwise it hides JsonParsingException.
        	logger.severe("getPayloadType: " + e.getMessage());
        	return PayloadType.ERROR;
        }
		
	}
	
	/**
	 * Presence.id set to BAD_VAL if can't be read.
	 * Presence.connection_id set to BAD_VAL if can't be read.
	 * Presence.id_str set to BAD_STRING if can't be read.
	 * Presence.time set to January 1, 1970, 00:00:00 if can't be read.
	 * @return null if presence data can't be read
	 */
	public Presence getPresence() {
		
		Presence presence;
		JsonObject payloadObject;
		JsonNumber jsonNumber;
		JsonString jsonString;
		long val;
		String str;
		Date date;
		
		try {
			// Get payload object.
			payloadObject = jsonObject.getJsonObject("payload");
        	if (payloadObject == null) {
        		logger.severe("getPresence: no payload object");
        		return null;
        	}
		} catch (ClassCastException e) {
			logger.severe("getPresence: " + e.getMessage());
			return null;
		}
		
    	// At this stage, we have a payload object. Extract data.
    	presence = new Presence();
    	// id.
    	val = -1;
    	try {
    		jsonNumber = payloadObject.getJsonNumber("id");
    		if (jsonNumber != null) {
    			val = jsonNumber.longValue();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setId(val);
    	// connection_id.
    	val = -1;
    	try {
    		jsonNumber = payloadObject.getJsonNumber("connection_id");
    		if (jsonNumber != null) {
    			val = jsonNumber.longValue();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setConnection_id(val);
    	// id_str.
    	str = BAD_STRING;
    	try {
    		jsonString = payloadObject.getJsonString("id_str");
    		if (jsonString != null) {
    			str = jsonString.getString();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setId_str(str);
    	// connection_id_str
    	str = BAD_STRING;
    	try {
    		jsonString = payloadObject.getJsonString("connection_id_str");
    		if (jsonString != null) {
    			str = jsonString.getString();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setConnection_id_str(str);
    	// asset.
    	str = BAD_STRING;
    	try {
    		jsonString = payloadObject.getJsonString("asset");
    		if (jsonString != null) {
    			str = jsonString.getString();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setAsset(str);
    	// time.
    	str = BAD_STRING;
    	try {
    		jsonString = payloadObject.getJsonString("time");
    		if (jsonString != null) {
    			str = jsonString.getString();
    		}
    	} catch (ClassCastException e) {
    	}
    	date = new Date(0);
    	if (!str.contentEquals(BAD_STRING)) {
    		try {
    			date = dateFormat.parse(str);
    		} catch (ParseException e) {
    		}
    	}
    	presence.setTime(date);
    	// type.
    	str = BAD_STRING;
    	try {
    		jsonString = payloadObject.getJsonString("type");
    		if (jsonString != null) {
    			str = jsonString.getString();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setType(str);
    	// reason.
    	str = BAD_STRING;
    	try {
    		jsonString = payloadObject.getJsonString("reason");
    		if (jsonString != null) {
    			str = jsonString.getString();
    		}
    	} catch (ClassCastException e) {
    	}
    	presence.setReason(str);
		
		return presence;
	}
	
	/**
	 * 
	 */
	public void closeJson() {
		
		jsonReader.close();
		
	}

}