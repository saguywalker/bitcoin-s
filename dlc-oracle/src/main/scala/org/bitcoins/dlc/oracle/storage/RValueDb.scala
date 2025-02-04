package org.bitcoins.dlc.oracle.storage

import org.bitcoins.core.hd._
import org.bitcoins.crypto.SchnorrNonce

case class RValueDb(
    nonce: SchnorrNonce,
    eventName: String,
    purpose: HDPurpose,
    accountCoin: HDCoinType,
    accountIndex: Int,
    chainType: Int,
    keyIndex: Int) {

  val path: BIP32Path = BIP32Path.fromString(
    s"m/${purpose.constant}'/${accountCoin.toInt}'/$accountIndex'/$chainType'/$keyIndex'")
}

object RValueDbHelper {

  def apply(
      nonce: SchnorrNonce,
      eventName: String,
      account: HDAccount,
      chainType: Int,
      keyIndex: Int): RValueDb = {
    RValueDb(nonce,
             eventName,
             account.purpose,
             account.coin.coinType,
             account.index,
             chainType,
             keyIndex)
  }
}
