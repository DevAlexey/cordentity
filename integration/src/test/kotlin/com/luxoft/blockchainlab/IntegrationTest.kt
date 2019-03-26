package com.luxoft.blockchainlab

import com.luxoft.blockchainlab.hyperledger.indy.utils.SerializationUtils
import org.junit.Test

class IntegrationTest {
    @Test
    fun `integrates correctly`() {
        // yes if can import classes from cordentity
        val yes = SerializationUtils.anyToJSON("YES")
        assert(SerializationUtils.jSONToAny<String>(yes) == "YES")
    }
}