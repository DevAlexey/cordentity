package com.luxoft.blockchainlab.hyperledger.indy

import com.luxoft.blockchainlab.hyperledger.indy.helpers.GenesisHelper
import com.luxoft.blockchainlab.hyperledger.indy.helpers.PoolHelper
import com.luxoft.blockchainlab.hyperledger.indy.helpers.WalletHelper
import com.luxoft.blockchainlab.hyperledger.indy.models.*
import com.luxoft.blockchainlab.hyperledger.indy.utils.StorageUtils
import junit.framework.Assert.assertFalse
import org.hyperledger.indy.sdk.did.Did
import org.hyperledger.indy.sdk.did.DidResults
import org.hyperledger.indy.sdk.pool.Pool
import org.hyperledger.indy.sdk.wallet.Wallet
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue


class AnoncredsDemoTest : IndyIntegrationTest() {
    private val masterSecretId = "masterSecretId"
    private val gvtCredentialValues = GVT_CRED_VALUES
    private val xyzCredentialValues =
        """{"status":{"raw":"partial","encoded":"51792877103171595686471452153480627530895"},"period":{"raw":"8","encoded":"8"}}"""

    private val walletPassword = "password"
    private val issuerWalletName = "issuerWallet"
    private val issuer2WalletName = "issuer2Wallet"
    private val proverWalletName = "proverWallet"

    private lateinit var issuerWallet: Wallet
    private lateinit var issuer2Wallet: Wallet
    private lateinit var proverWallet: Wallet
    private lateinit var issuerDidInfo: DidResults.CreateAndStoreMyDidResult
    private lateinit var issuer2DidInfo: DidResults.CreateAndStoreMyDidResult
    private lateinit var proverDidInfo: DidResults.CreateAndStoreMyDidResult
    private lateinit var issuer1: IndyUser
    private lateinit var issuer2: IndyUser
    private lateinit var prover: IndyUser

    companion object {
        private lateinit var pool: Pool
        private lateinit var poolName: String

        @JvmStatic
        @BeforeClass
        fun setUpTest() {
            // Create and Open Pool
            poolName = PoolHelper.DEFAULT_POOL_NAME
            PoolHelper.createPoolIfMissing(GenesisHelper.getGenesis(TEST_GENESIS_FILE_PATH), poolName)
            pool = PoolHelper.openPoolIfCreated(poolName)
        }

        @JvmStatic
        @AfterClass
        fun tearDownTest() {
            // Close pool
            pool.closePoolLedger().get()
            Pool.deletePoolLedgerConfig(poolName)
        }
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        // Clean indy stuff
        StorageUtils.cleanupStorage()

        // Issuer Create and Open Wallet
        issuerWallet = WalletHelper.openWallet(issuerWalletName, walletPassword)
        issuer2Wallet = WalletHelper.openWallet(issuer2WalletName, walletPassword)

        // Prover Create and Open Wallet
        proverWallet = WalletHelper.openWallet(proverWalletName, walletPassword)

        val trusteeDidInfo = createTrusteeDid(issuerWallet)
        issuerDidInfo = createDid(issuerWallet)
        linkIssuerToTrustee(trusteeDidInfo.did, issuerWallet, issuerDidInfo)

        issuer2DidInfo = createDid(issuer2Wallet)
        linkIssuerToTrustee(trusteeDidInfo.did, issuerWallet, issuer2DidInfo)

        proverDidInfo = createDid(proverWallet)
        linkProverToIssuer(issuerDidInfo.did, issuerWallet, proverDidInfo)

        issuer1 = IndyUser(pool, issuerWallet, issuerDidInfo.did, tailsPath = "tails")
        issuer2 = IndyUser(pool, issuer2Wallet, issuer2DidInfo.did, tailsPath = "tails")
        prover = IndyUser(pool, proverWallet, proverDidInfo.did, tailsPath = "tails")
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        // Issuer Remove Wallet
        issuerWallet.closeWallet().get()
        issuer2Wallet.closeWallet().get()

        // Prover Remove Wallet
        proverWallet.closeWallet().get()

        // Clean indy stuff
        StorageUtils.cleanupStorage()
    }

    private fun createTrusteeDid(wallet: Wallet) = Did.createAndStoreMyDid(wallet, """{"seed":"$TRUSTEE_SEED"}""").get()
    private fun createDid(wallet: Wallet) = Did.createAndStoreMyDid(wallet, "{}").get()

    private fun linkIssuerToTrustee(
        trusteeDid: String,
        issuerWallet: Wallet,
        issuerDidInfo: DidResults.CreateAndStoreMyDidResult
    ) {
        val target = IdentityDetails(issuerDidInfo.did, issuerDidInfo.verkey, null, "TRUSTEE")
        LedgerService.addNym(trusteeDid, pool, issuerWallet, target)
    }

    private fun linkProverToIssuer(
        issuerDid: String,
        issuerWallet: Wallet,
        proverDidInfo: DidResults.CreateAndStoreMyDidResult
    ) {
        val target = IdentityDetails(proverDidInfo.did, proverDidInfo.verkey, null, null)
        LedgerService.addNym(issuerDid, pool, issuerWallet, target)
    }

    @Test
    @Throws(Exception::class)
    fun `revocation works fine`() {
        val gvtSchema = issuer1.createSchema(GVT_SCHEMA_NAME, SCHEMA_VERSION, GVT_SCHEMA_ATTRIBUTES)
        val credDef = issuer1.createCredentialDefinition(gvtSchema.getSchemaIdObject(), true)
        val revocationRegistry = issuer1.createRevocationRegistry(credDef.getCredentialDefinitionIdObject())
        val credOffer = issuer1.createCredentialOffer(credDef.getCredentialDefinitionIdObject())
        val credReq = prover.createCredentialRequest(prover.did, credOffer)
        val credentialInfo = issuer1.issueCredential(credReq, gvtCredentialValues, credOffer)
        prover.receiveCredential(credentialInfo, credReq, credOffer)

        Thread.sleep(3000)

        val field_name = CredentialFieldReference("name", gvtSchema.id, credDef.id)
        val field_sex = CredentialFieldReference("sex", gvtSchema.id, credDef.id)
        val field_age = CredentialFieldReference("age", gvtSchema.id, credDef.id)
        val proofReq = IndyUser.createProofRequest(
            version = "0.1",
            name = "proof_req_0.1",
            attributes = listOf(field_name, field_sex),
            predicates = listOf(CredentialPredicate(field_age, 18)),
            nonRevoked = Interval.recent()
        )

        val proof = prover.createProof(proofReq)

        val usedData = IndyUser.getDataUsedInProof(DID_MY1, pool, proofReq, proof)
        assertEquals("Alex", proof.getAttribyteValue("name")!!.raw)
        assertTrue(IndyUser.verifyProof(proofReq, proof, usedData))

        issuer1.revokeCredential(
            credentialInfo.credential.getRevocationRegistryIdObject()!!,
            credentialInfo.credRevocId!!
        )
        Thread.sleep(3000)

        val proofReqAfterRevocation = IndyUser.createProofRequest(
            version = "0.1",
            name = "proof_req_0.1",
            attributes = listOf(field_name, field_sex),
            predicates = listOf(CredentialPredicate(field_age, 18)),
            nonRevoked = Interval.recent()
        )
        val proofAfterRevocation = prover.createProof(proofReqAfterRevocation)

        val usedDataAfterRevocation =
            IndyUser.getDataUsedInProof(DID_MY1, pool, proofReqAfterRevocation, proofAfterRevocation)

        assertFalse(IndyUser.verifyProof(proofReqAfterRevocation, proofAfterRevocation, usedDataAfterRevocation))
    }

    @Test
    @Throws(Exception::class)
    fun `1 issuer 1 prover 1 credential setup works fine`() {
        val gvtSchema = issuer1.createSchema(GVT_SCHEMA_NAME, SCHEMA_VERSION, GVT_SCHEMA_ATTRIBUTES)
        val credDef = issuer1.createCredentialDefinition(gvtSchema.getSchemaIdObject(), false)
        val credOffer = issuer1.createCredentialOffer(credDef.getCredentialDefinitionIdObject())
        val credReq = prover.createCredentialRequest(prover.did, credOffer)
        val credentialInfo = issuer1.issueCredential(credReq, gvtCredentialValues, credOffer)
        prover.receiveCredential(credentialInfo, credReq, credOffer)

        val field_name = CredentialFieldReference("name", gvtSchema.id, credDef.id)
        val field_sex = CredentialFieldReference("sex", gvtSchema.id, credDef.id)
        val field_age = CredentialFieldReference("age", gvtSchema.id, credDef.id)
        val proofReq = IndyUser.createProofRequest(
            version = "0.1",
            name = "proof_req_0.1",
            attributes = listOf(field_name, field_sex),
            predicates = listOf(CredentialPredicate(field_age, 18)),
            nonRevoked = null
        )

        val proof = prover.createProof(proofReq)

        val usedData = IndyUser.getDataUsedInProof(DID_MY1, pool, proofReq, proof)

        assertEquals("Alex", proof.getAttribyteValue("name")!!.raw)
        assertTrue(IndyUser.verifyProof(proofReq, proof, usedData))
    }

    @Test
    @Throws(Exception::class)
    fun `2 issuers 1 prover 2 credentials setup works fine`() {
        val schema1 = issuer1.createSchema(GVT_SCHEMA_NAME, SCHEMA_VERSION, GVT_SCHEMA_ATTRIBUTES)
        val credDef1 = issuer1.createCredentialDefinition(schema1.getSchemaIdObject(), false)

        val schema2 = issuer2.createSchema(XYZ_SCHEMA_NAME, SCHEMA_VERSION, XYZ_SCHEMA_ATTRIBUTES)
        val credDef2 = issuer2.createCredentialDefinition(schema2.getSchemaIdObject(), false)
        val gvtCredOffer = issuer1.createCredentialOffer(credDef1.getCredentialDefinitionIdObject())
        val xyzCredOffer = issuer2.createCredentialOffer(credDef2.getCredentialDefinitionIdObject())

        val gvtCredReq = prover.createCredentialRequest(prover.did, gvtCredOffer)
        val gvtCredential = issuer1.issueCredential(gvtCredReq, gvtCredentialValues, gvtCredOffer)
        prover.receiveCredential(gvtCredential, gvtCredReq, gvtCredOffer)

        val xyzCredReq = prover.createCredentialRequest(prover.did, xyzCredOffer)
        val xyzCredential = issuer2.issueCredential(xyzCredReq, xyzCredentialValues, xyzCredOffer)
        prover.receiveCredential(xyzCredential, xyzCredReq, xyzCredOffer)

        val field_name = CredentialFieldReference("name", schema1.id, credDef1.id)
        val field_age = CredentialFieldReference("age", schema1.id, credDef1.id)
        val field_status = CredentialFieldReference("status", schema2.id, credDef2.id)
        val field_period = CredentialFieldReference("period", schema2.id, credDef2.id)

        val proofReq = IndyUser.createProofRequest(
            version = "0.1",
            name = "proof_req_0.1",
            attributes = listOf(field_name, field_status),
            predicates = listOf(CredentialPredicate(field_age, 18), CredentialPredicate(field_period, 5)),
            nonRevoked = null
        )

        val proof = prover.createProof(proofReq)

        // Verifier verify Proof
        val revealedAttr0 = proof.getAttribyteValue("name")!!
        assertEquals("Alex", revealedAttr0.raw)

        val revealedAttr1 = proof.getAttribyteValue("status")!!
        assertEquals("partial", revealedAttr1.raw)

        val usedData = prover.getDataUsedInProof(proofReq, proof)

        assertTrue(IndyUser.verifyProof(proofReq, proof, usedData))
    }

    @Test
    @Throws(Exception::class)
    fun `1 issuer 1 prover 2 credentials setup works fine`() {
        val gvtSchema = issuer1.createSchema(GVT_SCHEMA_NAME, SCHEMA_VERSION, GVT_SCHEMA_ATTRIBUTES)
        val gvtCredDef = issuer1.createCredentialDefinition(gvtSchema.getSchemaIdObject(), false)

        val xyzSchema = issuer1.createSchema(XYZ_SCHEMA_NAME, SCHEMA_VERSION, XYZ_SCHEMA_ATTRIBUTES)
        val xyzCredDef = issuer1.createCredentialDefinition(xyzSchema.getSchemaIdObject(), false)
        val gvtCredOffer = issuer1.createCredentialOffer(gvtCredDef.getCredentialDefinitionIdObject())
        val xyzCredOffer = issuer1.createCredentialOffer(xyzCredDef.getCredentialDefinitionIdObject())

        val gvtCredReq = prover.createCredentialRequest(prover.did, gvtCredOffer)
        val gvtCredential = issuer1.issueCredential(gvtCredReq, gvtCredentialValues, gvtCredOffer)
        prover.receiveCredential(gvtCredential, gvtCredReq, gvtCredOffer)

        val xyzCredReq = prover.createCredentialRequest(prover.did, xyzCredOffer)
        val xyzCredential = issuer1.issueCredential(xyzCredReq, xyzCredentialValues, xyzCredOffer)
        prover.receiveCredential(xyzCredential, xyzCredReq, xyzCredOffer)

        val field_name = CredentialFieldReference("name", gvtSchema.id, gvtCredDef.id)
        val field_age = CredentialFieldReference("age", gvtSchema.id, gvtCredDef.id)
        val field_status = CredentialFieldReference("status", xyzSchema.id, xyzCredDef.id)
        val field_period = CredentialFieldReference("period", xyzSchema.id, xyzCredDef.id)

        val proofReq = IndyUser.createProofRequest(
            version = "0.1",
            name = "proof_req_0.1",
            attributes = listOf(field_name, field_status),
            predicates = listOf(CredentialPredicate(field_age, 18), CredentialPredicate(field_period, 5)),
            nonRevoked = null
        )

        val proof = prover.createProof(proofReq)

        // Verifier verify Proof
        val revealedAttr0 = proof.getAttribyteValue("name")!!
        assertEquals("Alex", revealedAttr0.raw)

        val revealedAttr1 = proof.getAttribyteValue("status")!!
        assertEquals("partial", revealedAttr1.raw)

        val usedData = prover.getDataUsedInProof(proofReq, proof)

        assertTrue(IndyUser.verifyProof(proofReq, proof, usedData))
    }
}
