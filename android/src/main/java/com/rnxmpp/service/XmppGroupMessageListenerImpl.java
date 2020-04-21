package com.rnxmpp.service;

import android.util.Log;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.MessageListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public class XmppGroupMessageListenerImpl implements com.rnxmpp.service.XmppGroupMessageListener, MessageListener {

    XmppServiceListener xmppServiceListener;
    Logger logger;

    public XmppGroupMessageListenerImpl(XmppServiceListener xmppServiceListener, Logger logger) {
        this.xmppServiceListener = xmppServiceListener;
        this.logger = logger;
    }

    public void processMessage(Message message) {
        Log.d("ReactNative", "Rx Group Message : " + message.getStanzaId());

        this.xmppServiceListener.onMessage(message);
    }

}


