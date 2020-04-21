package com.rnxmpp.service;

import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;

import org.jivesoftware.smackx.muc.RoomInfo;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.ping.packet.Ping;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import android.os.AsyncTask;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;


import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService, ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener {
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
    XmppGroupMessageListenerImpl groupMessageListner;

    XMPPTCPConnection connection;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String userName;
    String password;

    public XmppServiceSmackImpl(XmppServiceListener xmppServiceListener) {
        this.xmppServiceListener = xmppServiceListener;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for(int i = 0; i < trustedHosts.size(); i++){
            this.trustedHosts.add(trustedHosts.getString(i));
        }
    }

    @Override
    public void connect(String jid, String password, String authMethod, String hostname, Integer port, String xmppDomainName, Promise promise) {
        Log.d("ReactNative", "Logging In : "+jid);
        
        final String[] jidParts = jid.split("@");
        final String[] serviceNameParts = jidParts[1].indexOf("/") > -1 ? jidParts[1].split("/") : new String[] {jidParts[1]};

        final String userId = jidParts[0];
        final String serviceName = serviceNameParts[0];
        final String resource = serviceNameParts.length>1 ? serviceNameParts[1] : Long.toHexString(Double.doubleToLongBits(Math.random()));
        XMPPTCPConnectionConfiguration.Builder confBuilder = null;
        try {
            InetAddress addr = InetAddress.getByName(hostname);
            confBuilder = XMPPTCPConnectionConfiguration.builder()
                    .setXmppDomain(xmppDomainName)
                    .setHostAddress(addr)
                    .setPort(port)
                    .setUsernameAndPassword(userId, password)
                    .setConnectTimeout(3000)
                    .setResource(Resourcepart.from(resource))
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.required);
            this.userName = userId;
            this.password = password;
            if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))){
                confBuilder.setSecurityMode(SecurityMode.disabled);
                TLSUtils.disableHostnameVerificationForTlsCertificates(confBuilder);
                try {
                    TLSUtils.acceptAllCertificates(confBuilder);
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    e.printStackTrace();
                }
            }
        }catch(XmppStringprepException | UnknownHostException e){
            Log.d("ReactNative", "Error while creating Domain Bare Jid : ", e);
            promise.reject(e);
            return;
        }

        Log.d("ReactNative", "Connection Configured...");

        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        connection = new XMPPTCPConnection(connectionConfiguration);

        ReconnectionManager.getInstanceFor(connection).enableAutomaticReconnection();



        connection.setReplyTimeout(10000);

        //connection.addAsyncStanzaListener(this, new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class)));
        connection.addAsyncStanzaListener(this, stanza -> true);
        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        final MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(connection);
        mucManager.addInvitationListener((xmppConnection, muc, fromRoomJID, reason, password1, message, invite) -> {
            try{
                Log.d("ReactNative","Invitation Received from "+ fromRoomJID.toString()+" : "+invite);

                if (mucManager.getJoinedRooms().contains(fromRoomJID.asEntityBareJid())){
                    Log.d("ReactNative","Already  Joined as "+userId);
                    return;
                }
                Log.d("ReactNative","Joining as "+userId);

                muc.join(Resourcepart.from(userId), password1);

                groupMessageListner = new XmppGroupMessageListenerImpl(XmppServiceSmackImpl.this.xmppServiceListener, logger);
                muc.addMessageListener(groupMessageListner);

                RoomInfo info = mucManager.getRoomInfo(muc.getRoom());

                XmppServiceSmackImpl.this.xmppServiceListener.onInvitedRoomJoined(info, password1);

            }catch(Exception e){
                e.printStackTrace();
                Log.d("ReactNative", "Error while receiving MUC Invite : "  + e);
                for(int i=0; i<e.getStackTrace().length;i++){
                    Log.d("ReactNative", e.getStackTrace()[i].toString());
                }
            }
        });


        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect();
                    Log.d("ReactNative", "Connected : "+connection.isConnected());
                    connection.login();
                    Log.d("ReactNative", "Logged In : "+connection.isAuthenticated()+"  "+connection.getUser());

                    PingManager pingManager = PingManager.getInstanceFor(connection);
                    pingManager.setPingInterval(60);
                    pingManager.pingMyServer();


                } catch (XMPPException | SmackException | IOException | InterruptedException e) {
                    Log.d("ReactNative", "Could not login for user " + jidParts[0], e);
                    if (e instanceof SASLErrorException){
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    }else{
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {

            }
        }.execute();


        promise.resolve(jidParts[0]+"@"+serviceName+"/"+resource);

    }

    @Override
    public void sendRoomInvite(String roomJid, String to, String reason){
        Log.d("ReactNative","Impl: Inviting "+to+" to room "+roomJid);
        try{

            MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
            MultiUserChat muc = manager.getMultiUserChat(JidCreate.entityBareFrom(roomJid));

            Log.d("ReactNative","Inviting..");
            muc.invite(JidCreate.entityBareFrom(to), reason);
            Log.d("ReactNative","Invited..");
            
        } catch (SmackException.NotConnectedException | XmppStringprepException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not invite "+to+" to the chat room", e);
        }
    }

    @Override
    public void joinRoom(String roomJid, String userNickname, String password, String historyFrom) {
        Log.d("ReactNative","Impl: Joining "+roomJid+" as "+userNickname+" with pwd "+password+"  history from "+historyFrom);
        try {
            if (historyFrom == null)
                historyFrom = "0";

            Long timestamp =  Long.parseLong(historyFrom);

            MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
            MultiUserChat muc = manager.getMultiUserChat(JidCreate.entityBareFrom(roomJid));

            MucEnterConfiguration.Builder mecb = muc.getEnterConfigurationBuilder(Resourcepart.from(userNickname));
            if (timestamp > 5000L){
                mecb.requestHistorySince(new Date(timestamp));
            } else {
                mecb.requestNoHistory();
            }
            MucEnterConfiguration mucEnterConfig = mecb.withPassword(password).build();
            muc.join(mucEnterConfig);

            //muc.join(Resourcepart.from(userNickname), password);


            Log.d("ReactNative","Joined.. "+muc.isJoined());

            XmppGroupMessageListenerImpl groupMessageListner = new XmppGroupMessageListenerImpl(this.xmppServiceListener, logger);
            muc.addMessageListener(groupMessageListner);

            /*if (muc.isJoined()) {
                muc.changeAvailabilityStatus("Available", Presence.Mode.available);
                RoomInfo info = manager.getRoomInfo(muc.getRoom());
                this.xmppServiceListener.onRoomJoined(info);
            }*/

        } catch (SmackException.NotConnectedException | XMPPException.XMPPErrorException |
                SmackException.NoResponseException | XmppStringprepException |
                InterruptedException | MultiUserChatException.NotAMucServiceException e) {
            Log.d("ReactNative","Could not join chat room : " +  e);
        }
    }

    @Override
    public void sendRoomMessage(String roomJid, String password, String text) {

        Log.d("ReactNative","Sending Groupchat message to "+roomJid);
        try {

            MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(connection);
            MultiUserChat muc = mucManager.getMultiUserChat(JidCreate.entityBareFrom(roomJid));

            /*if (!muc.isJoined()){
                try {
                    Log.d("ReactNative", "sendRoomMessage: Rejoining "+roomJid+" with nick " + this.userName+" and pwd "+password);

                    muc.join(Resourcepart.from(this.userName), password);
                    Thread.sleep(2000);
                    muc.changeAvailabilityStatus("Available", Presence.Mode.available);
                    Thread.sleep(2000);
                }catch(XMPPException.XMPPErrorException ee){
                    Log.d("ReactNative", "sendRoomMessage: Could not rejoin room "+roomJid+" : " + ee.getMessage());
                }
            }*/
            /*final Message chatMessage = new Message();
            chatMessage.setType(Message.Type.groupchat);
            chatMessage.setBody(text);
            //String room = muc.getRoom();
            chatMessage.setTo(muc.getRoom());
            */
            muc.sendMessage(text);

        } catch ( SmackException | XmppStringprepException | InterruptedException e) {
            Log.d("ReactNative", "Could not send group message : " + e);
        }
    }

    @Override
    public void leaveRoom(String roomJid) {
        try {
            MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(connection);
            MultiUserChat muc = mucManager.getMultiUserChat(JidCreate.entityBareFrom(roomJid));


            muc.leave();
            muc.removeMessageListener(groupMessageListner);
        } catch (SmackException | XmppStringprepException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not leave chat room", e);
        }
    }

    @Override
    public void message(String text, String to, String thread) {
        String chatIdentifier = (thread == null ? to : thread);

        try {
        ChatManager chatManager = ChatManager.getInstanceFor(connection);
        Chat chat = chatManager.getThreadChat(chatIdentifier);
        if (chat == null) {
            EntityBareJid toUserjid = JidCreate.entityBareFrom(to);
            if (thread == null){
                chat = chatManager.createChat(toUserjid, this);
            }else{
                chat = chatManager.createChat(toUserjid, thread, this);
            }
        }

            chat.sendMessage(text);
        } catch (SmackException | InterruptedException | XmppStringprepException e) {
            logger.log(Level.WARNING, "Could not send message", e);
        }
    }

    @Override
    public void presence(String to, String type) {
        try {

            connection.sendStanza(new Presence(Presence.Type.fromString(type), type, 1, Presence.Mode.fromString(type)));
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send presence", e);
        }
    }

    @Override
    public void removeRoster(String to) {
        try {
            Roster roster = Roster.getInstanceFor(connection);
            RosterEntry rosterEntry = roster.getEntry(JidCreate.entityBareFrom(to));
            if (rosterEntry != null){

                roster.removeEntry(rosterEntry);
            }
        } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException |
                XMPPException.XMPPErrorException | SmackException.NoResponseException |
                XmppStringprepException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not remove roster entry: " + to);
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
        xmppServiceListener.onDisconnect(null);
    }

    @Override
    public void fetchRoster() {
        try {
            roster.reload();
        } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not fetch roster", e);
        }
    }

    @Override
    public void processStanza(Stanza packet) {
        if (packet == null)
            return;
        if (packet instanceof IQ){
            Log.d("ReactNative","Rx IQ");
            this.xmppServiceListener.onIQ((IQ) packet);
        }else if (packet instanceof Presence){
            Log.d("ReactNative","Rx Presence");
            this.xmppServiceListener.onPresence((Presence) packet);
        }else if (packet instanceof Ping){
            Log.d("ReactNative","Rx Ping");
            try {
                Ping ping = (Ping) packet;
                connection.sendStanza(ping.getPong());
                Log.d("ReactNative","Sx Pong ");
            }catch(InterruptedException | SmackException.NotConnectedException e){
                Log.d("ReactNative", "Could not send Pong : "+e);
            }
        }else{

            logger.log(Level.WARNING, "### Got a Stanza, of unknown subclass ", packet.toXML("packet"));
        }
    }

    public class StanzaPacket extends Stanza {
         private String xmlString;

         public StanzaPacket(String xmlString) {
             super();
             this.xmlString = xmlString;
         }

        @Override
        public String toString() {
            return this.xmlString;
        }

        @Override
        public CharSequence toXML(String enclosingNamespace) {
            XmlStringBuilder xml = new XmlStringBuilder();
            xml.append(this.xmlString);
            return xml;
        }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            connection.sendStanza(packet);
        } catch (SmackException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send stanza", e);
        }
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    @Override
    public void connected(XMPPConnection connection) {
        Log.d("ReactNative", "connected : " + connection.isConnected());
        this.xmppServiceListener.onConnnect(this.userName, this.password);
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        this.xmppServiceListener.onLogin(this.userName, this.password);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        this.xmppServiceListener.onMessage(message);
        // logger.log(Level.INFO, "Received a new message", message.toString());
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        this.xmppServiceListener.onRosterReceived(roster);
    }

    @Override
    public void onRosterLoadingFailed(Exception exception) {

    }

    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        logger.log(Level.INFO, "Connection was closed.");
    }

}
