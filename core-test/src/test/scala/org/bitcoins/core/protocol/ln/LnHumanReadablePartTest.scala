package org.bitcoins.core.protocol.ln

import org.bitcoins.core.config.{MainNet, RegTest, TestNet3}
import org.bitcoins.core.protocol.ln.LnParams._
import org.bitcoins.core.protocol.ln.currency.{LnCurrencyUnits, MilliBitcoins}
import org.bitcoins.testkit.util.BitcoinSUnitTest

import scala.util.Try

class LnHumanReadablePartTest extends BitcoinSUnitTest {
  val mBtc = MilliBitcoins(1)
  val mBtcOpt = Some(mBtc)
  it must "match the correct hrp with the correct network" in {
    LnHumanReadablePart(MainNet) must be(LnHumanReadablePart(LnBitcoinMainNet))
    LnHumanReadablePart(TestNet3) must be(LnHumanReadablePart(LnBitcoinTestNet))
    LnHumanReadablePart(RegTest) must be(LnHumanReadablePart(LnBitcoinRegTest))

    LnHumanReadablePart(MainNet, mBtc) must be(
      LnHumanReadablePart(LnBitcoinMainNet, mBtcOpt))
    LnHumanReadablePart(TestNet3, mBtc) must be(
      LnHumanReadablePart(LnBitcoinTestNet, mBtcOpt))
    LnHumanReadablePart(RegTest, mBtc) must be(
      LnHumanReadablePart(LnBitcoinRegTest, mBtcOpt))
  }

  it must "correctly serialize the hrp to string" in {
    LnHumanReadablePart(LnBitcoinMainNet, mBtcOpt).toString must be("lnbc1m")
    LnHumanReadablePart(LnBitcoinTestNet, mBtcOpt).toString must be("lntb1m")
    LnHumanReadablePart(LnBitcoinRegTest, mBtcOpt).toString must be("lnbcrt1m")

    LnHumanReadablePart(LnBitcoinMainNet).toString must be("lnbc")
    LnHumanReadablePart(LnBitcoinTestNet).toString must be("lntb")
    LnHumanReadablePart(LnBitcoinRegTest).toString must be("lnbcrt")
  }

  it must "fail to create hrp from invalid amount" in {
    val tooBig = Some(MilliBitcoins(LnPolicy.maxAmountMSat.toBigInt + 1))
    val zero = Some(LnCurrencyUnits.zero)
    val tooSmall = Some(MilliBitcoins(-1))

    Try(LnHumanReadablePart(LnBitcoinMainNet, tooBig)).isFailure must be(true)
    Try(LnHumanReadablePart(LnBitcoinMainNet, zero)).isFailure must be(true)
    Try(LnHumanReadablePart(LnBitcoinMainNet, tooSmall)).isFailure must be(true)

  }

  it must "deserialize hrp from string" in {

    LnHumanReadablePart.fromString("lnbc") must be(
      LnHumanReadablePart(LnBitcoinMainNet))
    LnHumanReadablePart.fromString("lntb") must be(
      LnHumanReadablePart(LnBitcoinTestNet))
    LnHumanReadablePart.fromString("lnbcrt") must be(
      LnHumanReadablePart(LnBitcoinRegTest))

    LnHumanReadablePart.fromString("lnbc1m") must be(
      LnHumanReadablePart(LnBitcoinMainNet, mBtcOpt))
    LnHumanReadablePart.fromString("lntb1m") must be(
      LnHumanReadablePart(LnBitcoinTestNet, mBtcOpt))
    LnHumanReadablePart.fromString("lnbcrt1m") must be(
      LnHumanReadablePart(LnBitcoinRegTest, mBtcOpt))
  }

  it must "fail to deserialize hrp from invalid string" in {
    LnHumanReadablePart.fromStringT("invalid").isFailure must be(true)
    LnHumanReadablePart.fromStringT("lnbc9000").isFailure must be(true)
    LnHumanReadablePart.fromStringT("lnbc90z0m").isFailure must be(true)

  }
}
