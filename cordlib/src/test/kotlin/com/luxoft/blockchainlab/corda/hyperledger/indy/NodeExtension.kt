package com.luxoft.blockchainlab.corda.hyperledger.indy

import com.luxoft.blockchainlab.corda.hyperledger.indy.service.IndyService
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode

fun TestStartedNode.getParty() = this.info.singleIdentity()

fun TestStartedNode.getName() = getParty().name

fun TestStartedNode.getPubKey() = getParty().owningKey

fun CordaX500Name.getNodeByName(net: InternalMockNetwork) =
    net.defaultNotaryNode.services.identityService.wellKnownPartyFromX500Name(this)!!

fun TestStartedNode.getPartyDid() =
    this.services.cordaService(IndyService::class.java).indyUser.did