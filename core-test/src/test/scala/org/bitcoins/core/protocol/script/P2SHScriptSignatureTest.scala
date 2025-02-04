package org.bitcoins.core.protocol.script

import org.bitcoins.core.script.constant.{
  BytesToPushOntoStack,
  OP_0,
  ScriptConstant
}
import org.bitcoins.crypto.ECPublicKey
import org.bitcoins.testkit.util.{BitcoinSAsyncTest, TestUtil}

/** Created by chris on 3/8/16.
  */
class P2SHScriptSignatureTest extends BitcoinSAsyncTest {

  "P2SHScriptSignature" must "find the public keys embedded inside of the redeemScript" in {
    val rawP2SHScriptSig = TestUtil.rawP2shInputScript2Of2
    val p2shScriptSig: P2SHScriptSignature =
      ScriptSignature(rawP2SHScriptSig) match {
        case x: P2SHScriptSignature => x
        case y                      => throw new RuntimeException("Must be p2sh script sig: " + y)
      }
    p2shScriptSig.publicKeys must be(
      Seq(
        ECPublicKey(
          "0369d26ebd086523384a0f89f293d4c327a65fa73332d8efd1097cb35231295b83"),
        ECPublicKey(
          "02480863e5c4a4e9763f5380c44fcfe6a3b7787397076cf9ea1049303a9d34f721")
      ))

  }

  it must "return a p2sh scriptSig with no serialized redeemScript" in {

    val p2shScriptSig = TestUtil.p2shInputScript2Of2 match {
      case s: P2SHScriptSignature => s
      case _                      => throw new RuntimeException("Should be p2sh scriptSig")
    }

    p2shScriptSig.scriptSignatureNoRedeemScript.asm must be(
      Seq(
        OP_0,
        BytesToPushOntoStack(71),
        ScriptConstant(
          "304402207d764cb90c9fd84b74d33a47cf3a0ffead9ded98333776becd6acd32c4426dac02203905a0d064e7f53d07793e86136571b6e4f700c1cfb888174e84d78638335b8101"),
        BytesToPushOntoStack(72),
        ScriptConstant(
          "3045022100906aaca39f022acd8b7a38fd2f92aca9e9f35cfeaee69a6f13e1d083ae18222602204c9ed96fc6c4de56fd85c679fc59c16ee1ccc80c42563b86174e1a506fc007c801")
      ))
  }

}
