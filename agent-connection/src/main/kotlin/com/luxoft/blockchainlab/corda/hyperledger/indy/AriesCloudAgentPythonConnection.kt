package com.luxoft.blockchainlab.corda.hyperledger.indy

import mu.KotlinLogging
import rx.Single
import java.util.concurrent.TimeoutException

/**
 * Implements Indy Agent Connection transport ([AgentConnection]).
 * Whenever the socket is closed on the remote side, the connection is scheduled to re-establish.
 * A dual-mode reconnection procedure ([doReconnect]) is called asynchronously with increasing delay between
 * attempts, when the underlying WebSocket reports it's been closed from remote. It is also called asynchronously
 * when the caller requests to listen for a certain message. When the caller requests to transmit a message,
 * the calling thread blocks for [operationTimeoutMs] or until the connection is re-established, whichever happens first.
 * The timeout is set in [connect] method (default value is 60000ms, i.e. 1 minute).
 * [TimeoutException] is thrown when no response is received within this timeframe.
 * The transport is therefore capable to survive network outages that happen for example when the device loses the network
 * coverage. It should be kept in mind that when the Agent (PythonRefAgent) has queued certain quantity of messages to be
 * sent to the client, and the connection was broken, the agent will route the queued messages to the first incoming
 * client, so that if another client connects to the Agent within the time of outage, it will consume messages pertaining
 * to the client that has been recently disconnected. This inevitably leads to loss of data. Avoid the situation when
 * multiple clients concurrently connect to a single Agent.
 */
class AriesCloudAgentPythonConnection : AgentConnection {
    private val log = KotlinLogging.logger {}

    private lateinit var url: String
    private lateinit var login: String
    private lateinit var password: String
    private var operationTimeoutMs: Long = 60000

    override fun connect(url: String, login: String, password: String, timeoutMs: Long): Single<Unit> {
        this.url = url
        this.login = login
        this.password = password
        operationTimeoutMs = timeoutMs
        return Single.just(Unit)
    }

    override fun disconnect() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun generateInvite(): Single<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun acceptInvite(invite: String): Single<IndyPartyConnection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun waitForInvitedParty(invite: String, timeout: Long): Single<IndyPartyConnection> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getIndyPartyConnection(partyDID: String): Single<IndyPartyConnection?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getConnectionStatus(): AgentConnectionStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
