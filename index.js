'use strict';
var React = require('react-native');
var {NativeAppEventEmitter, NativeModules} = React;
var RNXMPP = NativeModules.RNXMPP;

var map = {
    'message' : 'RNXMPPMessage',
    'iq': 'RNXMPPIQ',
    'presence': 'RNXMPPPresence',
    'connect': 'RNXMPPConnect',
    'disconnect': 'RNXMPPDisconnect',
    'error': 'RNXMPPError',
    'loginError': 'RNXMPPLoginError',
    'login': 'RNXMPPLogin',
    'roster': 'RNXMPPRoster',
    'roomjoined': 'RNXMPPRoomJoined',
    'invitedroomjoined': "RNXMPPInvitedRoomJoined",
    'occupantjoined': 'RNXMPPOccupantJoined'
}

const LOG = (message) => {
  if (__DEV__) {
    console.log('react-native-xmpp: ' + message);
  }
}

class XMPP {
    PLAIN = RNXMPP.PLAIN;
    SCRAM = RNXMPP.SCRAMSHA1;
    MD5 = RNXMPP.DigestMD5;

    constructor(){
        this.isConnected = false;
        this.isLogged = false;
        this.listeners = [
            NativeAppEventEmitter.addListener(map.connect, this.onConnected.bind(this)),
            NativeAppEventEmitter.addListener(map.disconnect, this.onDisconnected.bind(this)),
            NativeAppEventEmitter.addListener(map.error, this.onError.bind(this)),
            NativeAppEventEmitter.addListener(map.loginError, this.onLoginError.bind(this)),
            NativeAppEventEmitter.addListener(map.login, this.onLogin.bind(this)),
        ];
    }

    onConnected(){
        LOG("Connected");
        this.isConnected = true;
    }

    onLogin(){
        LOG("Login");
        this.isLogged = true;
    }

    onDisconnected(error){
        LOG("Disconnected, error: "+error);
        this.isConnected = false;
        this.isLogged = false;
    }

    onError(text){
        LOG("Error: "+text);
    }

    onLoginError(text){
        this.isLogged = false;
        LOG("LoginError: "+text);
    }

    on(type, callback){
        if (map[type]){
            const listener = NativeAppEventEmitter.addListener(map[type], callback);
            this.listeners.push(listener);
            return listener;
        } else {
            throw "No registered type: " + type;
        }
    }

    removeListener(type) {
        if (map[type]) {
            for (var i = 0; i < this.listeners.length; i++) {
                var listener = this.listeners[i];
                if (listener.eventType === map[type]) {
                    listener.remove();
                    var index = this.listeners.indexOf(listener);
                    if (index > -1) {
                        this.listeners.splice(index, 1);
                    }
                    LOG(`Event listener of type "${type}" removed`);
                }
            }
        }
    }

    removeListeners() {
        for (var i = 0; i < this.listeners.length; i++) {
            this.listeners[i].remove();
        }

        this.listeners = [
            NativeAppEventEmitter.addListener(map.connect, this.onConnected.bind(this)),
            NativeAppEventEmitter.addListener(map.disconnect, this.onDisconnected.bind(this)),
            NativeAppEventEmitter.addListener(map.error, this.onError.bind(this)),
            NativeAppEventEmitter.addListener(map.loginError, this.onLoginError.bind(this)),
            NativeAppEventEmitter.addListener(map.login, this.onLogin.bind(this)),
        ];
        
        LOG('All event listeners removed');
    }

    trustHosts(hosts){
        React.NativeModules.RNXMPP.trustHosts(hosts);
    }

    async connect(username, password, auth = RNXMPP.SCRAMSHA1, hostname = null, port = 5222, xmppDomainName = null){
        if (!hostname){
            hostname = (username+'@/').split('@')[1].split('/')[0];
        }
        return React.NativeModules.RNXMPP.connect(username, password, auth, hostname, port, xmppDomainName);
    }

    message(text, user, thread = null){
        LOG(`Message: "${text}" being sent to user: ${user}`);
        React.NativeModules.RNXMPP.message(text, user, thread);
    }

    sendStanza(stanza){
        RNXMPP.sendStanza(stanza);
    }

    fetchRoster(){
        RNXMPP.fetchRoster();
    }

    presence(to, type){
        React.NativeModules.RNXMPP.presence(to, type);
    }

    removeFromRoster(to){
        React.NativeModules.RNXMPP.removeRoster(to);
    }

    disconnect(){
        if (this.isConnected){
            React.NativeModules.RNXMPP.disconnect();
        }
    }
    disconnectAfterSending(){
      if (this.isConnected){
        React.NativeModules.RNXMPP.disconnectAfterSending();
      }
    }

    sendRoomInvite(roomJID, to, reason) {
        React.NativeModules.RNXMPP.sendRoomInvite(roomJID, to, reason);
    }

    joinRoom(roomJID, nickname, password, history) {
        React.NativeModules.RNXMPP.joinRoom(roomJID, nickname, password, history);
    }

    sendRoomMessage(roomJID, password,  message) {
        React.NativeModules.RNXMPP.sendRoomMessage(roomJID, password, message);
    }

    leaveRoom(roomJID) {
        React.NativeModules.RNXMPP.leaveRoom(roomJID);
    }
}

module.exports = new XMPP();
