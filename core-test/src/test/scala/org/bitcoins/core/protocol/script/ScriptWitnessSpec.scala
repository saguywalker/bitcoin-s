package org.bitcoins.core.protocol.script

import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.testkit.core.gen.{ScriptGenerators, WitnessGenerators}
import org.scalacheck.{Prop, Properties}

class ScriptWitnessSpec
    extends Properties("ScriptWitnessSpec")
    with BitcoinSLogger {

  property("serialization symmetry") = {
    Prop.forAll(WitnessGenerators.scriptWitness) { scriptWit =>
      val x = ScriptWitness(scriptWit.stack)
      scriptWit == x
    }
  }

  property("pull redeem script out of p2wsh witness") = {
    Prop.forAll(ScriptGenerators.rawScriptPubKey) { case (spk, _) =>
      P2WSHWitnessV0(spk).redeemScript == spk
    }
  }

  property("pull script signature out of p2wsh witness") = {
    Prop.forAll(ScriptGenerators.rawScriptPubKey,
                ScriptGenerators.rawScriptSignature) {
      case ((spk, _), scriptSig) =>
        P2WSHWitnessV0(spk, scriptSig).scriptSignature == scriptSig
    }
  }
}
