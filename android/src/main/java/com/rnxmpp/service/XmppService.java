package com.rnxmpp.service;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public interface XmppService {

    @ReactMethod
    void trustHosts(ReadableArray trustedHosts);

    @ReactMethod
    void connect(String jid, String password, String authMethod, String hostname, Integer port, String xmppDomainName, Promise promise);

    @ReactMethod
    void sendRoomInvite(String roomJid, String to, String reason);

    @ReactMethod
    void joinRoom(String mucJid, String userNickname, String password, String historyFrom);

    @ReactMethod
    void sendRoomMessage(String roomJid, String password, String text);

    @ReactMethod
    void leaveRoom(String mucJid);

    @ReactMethod
    void message(String text, String to, String thread);

    @ReactMethod
    void presence(String to, String type);

    @ReactMethod
    void removeRoster(String to);

    @ReactMethod
    void disconnect();

    @ReactMethod
    void fetchRoster();

    @ReactMethod
    void sendStanza(String stanza);


}
