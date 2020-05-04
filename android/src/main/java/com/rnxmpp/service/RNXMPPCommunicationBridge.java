package com.rnxmpp.service;

import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smackx.muc.RoomInfo;

import org.json.JSONException;
import org.json.JSONObject;

import com.rnxmpp.utils.Parser;

import java.text.SimpleDateFormat;
import java.util.Date;

import fr.arnaudguyon.xmltojsonlib.XmlToJson;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class RNXMPPCommunicationBridge implements XmppServiceListener {

    public static final String RNXMPP_ERROR =       "RNXMPPError";
    public static final String RNXMPP_LOGIN_ERROR = "RNXMPPLoginError";
    public static final String RNXMPP_MESSAGE =     "RNXMPPMessage";
    public static final String RNXMPP_ROSTER =      "RNXMPPRoster";
    public static final String RNXMPP_IQ =          "RNXMPPIQ";
    public static final String RNXMPP_PRESENCE =    "RNXMPPPresence";
    public static final String RNXMPP_CONNECT =     "RNXMPPConnect";
    public static final String RNXMPP_DISCONNECT =  "RNXMPPDisconnect";
    public static final String RNXMPP_LOGIN =       "RNXMPPLogin";
    public static final String RNXMPP_ROOMJOIN =       "RNXMPPRoomJoined";
    public static final String RNXMPP_INVITEDROOMJOIN = "RNXMPPInvitedRoomJoined";
    private SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private static String lastMessageId = "";

    ReactContext reactContext;

    public RNXMPPCommunicationBridge(ReactContext reactContext) {
        this.reactContext = reactContext;
    }

    @Override
    public void onError(Exception e) {
        sendEvent(reactContext, RNXMPP_ERROR, e.getLocalizedMessage());
    }

    @Override
    public void onLoginError(String errorMessage) {
        sendEvent(reactContext, RNXMPP_LOGIN_ERROR, errorMessage);
    }

    @Override
    public void onLoginError(Exception e) {
        this.onLoginError(e.getLocalizedMessage());
    }

    @Override
    public void onMessage(Message message) {
        try {

            //Block repeated messages with same id
            XmlToJson xmlToJson = new XmlToJson.Builder(message.toXML("message").toString()).build();
            JSONObject msgObj = xmlToJson.toJson().getJSONObject("message");

            Log.d("ReactNative", "Message JSON : "+msgObj.toString());




            String body = message.getBody();

            if(body != null && !body.isEmpty() && msgObj.has(("stanza-id") )) {

                JSONObject stanza = msgObj.getJSONObject("stanza-id");

                String msgId = (String) msgObj.getJSONObject("stanza-id").get("id");
                if (lastMessageId.equals(msgId)){
                    //Repeated message
                    return;
                }
                lastMessageId = msgId;

                String ts = "";
                if(msgObj.has("delay")){
                    String dtStr = (String) msgObj.getJSONObject("delay").get("stamp");
                    if (dtStr != null){
                        //Java time format can't parse the format "+00:00" which openfire is sending, so rip it off before milliseconds
                        if (dtStr.indexOf(".") > 0)
                            dtStr = dtStr.substring(0, dtStr.indexOf("."));
                        Date dt = iso8601Format.parse(dtStr);
                        ts = String.valueOf(dt.getTime());
                    }
                }

                WritableMap params = Arguments.createMap();
                params.putString("id", msgId);
                params.putString("body", body);
                params.putString("from", message.getFrom().toString());
                params.putString("to", message.getTo().toString());
                params.putString("timestamp", ts);
                params.putString("src", message.toXML("message").toString());
                sendEvent(reactContext, RNXMPP_MESSAGE, params);

            }else{
                Log.d("ReactNative", "Message doesn't have body and/or stanza");
            }


        }catch(Exception e){
            e.printStackTrace();
            Log.d("ReactNative", "Error in onMessage : "+e.getMessage());
        }
    }

    @Override
    public void onRosterReceived(Roster roster) {
        WritableArray rosterResponse = Arguments.createArray();
        for (RosterEntry rosterEntry : roster.getEntries()) {
            WritableMap rosterProps = Arguments.createMap();
            rosterProps.putString("username", rosterEntry.getUser());
            rosterProps.putString("displayName", rosterEntry.getName());
            WritableArray groupArray = Arguments.createArray();
            for (RosterGroup rosterGroup : rosterEntry.getGroups()) {
                groupArray.pushString(rosterGroup.getName());
            }
            rosterProps.putArray("groups", groupArray);
            rosterProps.putString("subscription", rosterEntry.getType().toString());
            rosterResponse.pushMap(rosterProps);
        }
        sendEvent(reactContext, RNXMPP_ROSTER, rosterResponse);
    }

    @Override
    public void onIQ(IQ iq) {
        try {
            XmlToJson xmlToJson = new XmlToJson.Builder(iq.toXML("iq").toString()).build();
            sendEvent(reactContext, RNXMPP_IQ, Parser.jsonToReact(xmlToJson.toJson().getJSONObject("iq")));
        }catch( JSONException e){
            Log.d("ReactNative", e.getMessage());
        }
    }

    @Override
    public void onPresence(Presence presence) {
        WritableMap presenceMap = Arguments.createMap();
        presenceMap.putString("type", presence.getType().toString());
        presenceMap.putString("from", presence.getFrom().toString());
        presenceMap.putString("status", presence.getStatus());
        presenceMap.putString("mode", presence.getMode().toString());
        sendEvent(reactContext, RNXMPP_PRESENCE, presenceMap);
    }

    @Override
    public void onConnnect(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_CONNECT, params);
    }

    @Override
    public void onDisconnect(Exception e) {
        if (e != null) {
            sendEvent(reactContext, RNXMPP_DISCONNECT, e.getLocalizedMessage());
        } else {
            sendEvent(reactContext, RNXMPP_DISCONNECT, null);
        }
    }

    @Override
    public void onLogin(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_LOGIN, params);
    }

    @Override
    public void onRoomJoined(RoomInfo info){
        Log.d("ReactNative", "Bridge: onRoomJoined name "+info.getName()+"  Localpart "+info.getRoom().getLocalpartOrNull()+"  Resource "+info.getRoom().getDomain());
        String roomName = info.getRoom().getLocalpart() + "@" + info.getRoom().getDomain();
        WritableMap params = Arguments.createMap();
        params.putString("roomId", roomName);
        params.putString("roomName", roomName);
        sendEvent(reactContext, RNXMPP_ROOMJOIN, params);
    }

    @Override
    public void onInvitedRoomJoined(RoomInfo info, String password, String reason){
        Log.d("ReactNative", "Bridge: onInvitedRoomJoined name "+info.getName()+"  Localpart "+info.getRoom().getLocalpartOrNull()+"  Resource "+info.getRoom().getDomain());
        String roomName = info.getRoom().getLocalpart() + "@" + info.getRoom().getDomain();
        WritableMap params = Arguments.createMap();
        //Sending occupants doesn't make sense, its better that the app checks for it afresh every time
        params.putString("roomId", roomName);
        params.putString("roomName", roomName);
        params.putString("password", password);
        params.putString("subject", info.getSubject());
        params.putString("reason", reason);
        sendEvent(reactContext, RNXMPP_INVITEDROOMJOIN, params);
    }


    void sendEvent(ReactContext reactContext, String eventName, @Nullable Object params) {
        reactContext
                .getJSModule(RCTNativeAppEventEmitter.class)
                .emit(eventName, params);
    }
}
