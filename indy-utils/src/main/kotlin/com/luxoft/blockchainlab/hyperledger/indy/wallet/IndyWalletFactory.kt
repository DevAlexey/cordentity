package com.luxoft.blockchainlab.hyperledger.indy.wallet

import org.hyperledger.indy.sdk.wallet.Wallet

interface IndyWalletFactory {
    fun createWallet(did: String): Wallet
    fun getWallet(did: String): Wallet
}