package org.bitcoins.oracle.server

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.bitcoins.core.number._
import org.bitcoins.core.protocol.tlv._
import org.bitcoins.dlc.oracle._
import org.bitcoins.dlc.oracle.config.DLCOracleAppConfig
import org.bitcoins.keymanager.WalletStorage
import org.bitcoins.server.routes.{Server, ServerCommand, ServerRoute}
import ujson._

import scala.util.{Failure, Success}

case class OracleRoutes(oracle: DLCOracle)(implicit
    system: ActorSystem,
    conf: DLCOracleAppConfig)
    extends ServerRoute {
  import system.dispatcher

  def handleCommand: PartialFunction[ServerCommand, StandardRoute] = {
    case ServerCommand("getpublickey", _) =>
      complete {
        Server.httpSuccess(oracle.publicKey.hex)
      }

    case ServerCommand("getstakingaddress", _) =>
      complete {
        val network = oracle.conf.network
        val address = oracle.stakingAddress(network)

        Server.httpSuccess(address.toString)
      }

    case ServerCommand("listevents", _) =>
      complete {
        oracle.listEvents().map { events =>
          val strs = events.map(_.announcementTLV.hex)
          val json = Arr.from(strs)

          Server.httpSuccess(json)
        }
      }

    case ServerCommand("createenumevent", arr) =>
      CreateEvent.fromJsArr(arr) match {
        case Failure(exception) =>
          reject(ValidationRejection("failure", Some(exception)))
        case Success(CreateEvent(label, maturationTime, outcomes)) =>
          complete {
            oracle.createNewEnumEvent(label, maturationTime, outcomes).map {
              announcementTLV =>
                Server.httpSuccess(announcementTLV.hex)
            }
          }
      }

    case ServerCommand("createdigitdecompevent", arr) =>
      CreateDigitDecompEvent.fromJsArr(arr) match {
        case Failure(exception) =>
          reject(ValidationRejection("failure", Some(exception)))
        case Success(
              CreateDigitDecompEvent(eventName,
                                     maturationTime,
                                     base,
                                     isSigned,
                                     numDigits,
                                     unit,
                                     precision)) =>
          complete {
            oracle
              .createNewLargeRangedEvent(eventName,
                                         maturationTime,
                                         UInt16(base),
                                         isSigned,
                                         numDigits,
                                         unit,
                                         Int32(precision))
              .map { announcementTLV =>
                Server.httpSuccess(announcementTLV.hex)
              }
          }
      }

    case ServerCommand("getevent", arr) =>
      GetEvent.fromJsArr(arr) match {
        case Failure(exception) =>
          reject(ValidationRejection("failure", Some(exception)))
        case Success(GetEvent(announcementTLV)) =>
          complete {
            oracle.findEvent(announcementTLV.eventTLV).map {
              case Some(event: OracleEvent) =>
                val outcomesJson = event.eventDescriptorTLV match {
                  case enum: EnumEventDescriptorV0TLV =>
                    enum.outcomes.map(outcome => Str(outcome.normStr))
                  case range: RangeEventDescriptorV0TLV =>
                    val outcomes: Vector[Long] = {
                      val startL = range.start.toLong
                      val stepL = range.step.toLong

                      val outcomeRange =
                        0L.until(range.count.toLong)
                          .map(num => startL + (num * stepL))

                      outcomeRange.toVector
                    }
                    outcomes.map(num => Num(num.toDouble))
                  case decomp: DigitDecompositionEventDescriptorV0TLV =>
                    val sign = decomp match {
                      case _: UnsignedDigitDecompositionEventDescriptor =>
                        Vector.empty
                      case _: SignedDigitDecompositionEventDescriptor =>
                        Vector(Str("+"), Str("-"))
                    }

                    val digits = 0.until(decomp.numDigits.toInt).map { _ =>
                      0
                        .until(decomp.base.toInt)
                        .map(s => Str(s.toString))
                        .toVector
                    }

                    val vecs = digits :+ sign
                    vecs.map(vec => Arr.from(vec))
                }

                val (attestationJson, signatureJson) = event match {
                  case completedEvent: CompletedOracleEvent =>
                    (Arr.from(completedEvent.attestations.map(a => Str(a.hex))),
                     Arr.from(completedEvent.signatures.map(s => Str(s.hex))))
                  case _: PendingOracleEvent =>
                    (ujson.Null, ujson.Null)
                }

                val json = Obj(
                  "nonces" -> event.nonces.map(n => Str(n.hex)),
                  "eventName" -> Str(event.eventName),
                  "signingVersion" -> Str(event.signingVersion.toString),
                  "maturationTime" -> Str(event.maturationTime.toString),
                  "announcementSignature" -> Str(
                    event.announcementSignature.hex),
                  "eventDescriptorTLV" -> Str(event.eventDescriptorTLV.hex),
                  "eventTLV" -> Str(event.eventTLV.hex),
                  "announcementTLV" -> Str(event.announcementTLV.hex),
                  "attestations" -> attestationJson,
                  "signatures" -> signatureJson,
                  "outcomes" -> outcomesJson
                )
                Server.httpSuccess(json)
              case None =>
                Server.httpSuccess(ujson.Null)
            }
          }
      }

    case ServerCommand("signevent", arr) =>
      SignEvent.fromJsArr(arr) match {
        case Failure(exception) =>
          reject(ValidationRejection("failure", Some(exception)))
        case Success(SignEvent(oracleAnnouncementTLV, outcome)) =>
          complete {
            oracle
              .signEvent(oracleAnnouncementTLV.eventTLV,
                         EnumAttestation(outcome))
              .map { eventDb =>
                val oracleEvent = OracleEvent.fromEventDbs(Vector(eventDb))
                oracleEvent match {
                  case _: PendingOracleEvent =>
                    throw new RuntimeException("Failed to sign event")
                  case event: CompletedOracleEvent =>
                    Server.httpSuccess(event.oracleAttestmentV0TLV.hex)
                }
              }
          }
      }

    case ServerCommand("signdigits", arr) =>
      SignDigits.fromJsArr(arr) match {
        case Failure(exception) =>
          reject(ValidationRejection("failure", Some(exception)))
        case Success(SignDigits(announcementTLV, num)) =>
          complete {
            oracle.signDigits(announcementTLV.eventTLV, num).map {
              case _: PendingOracleEvent =>
                throw new RuntimeException("Failed to sign event")
              case event: CompletedOracleEvent =>
                Server.httpSuccess(event.oracleAttestmentV0TLV.hex)
            }
          }
      }

    case ServerCommand("getsignatures", arr) =>
      GetEvent.fromJsArr(arr) match {
        case Failure(exception) =>
          reject(ValidationRejection("failure", Some(exception)))
        case Success(GetEvent(announcementTLV)) =>
          complete {
            oracle.findEvent(announcementTLV.eventTLV).map {
              case Some(completed: CompletedOracleEvent) =>
                Server.httpSuccess(completed.oracleAttestmentV0TLV.hex)
              case None | Some(_: PendingOracleEvent) =>
                Server.httpSuccess(ujson.Null)
            }
          }
      }

    case ServerCommand("keymanagerpassphrasechange", arr) =>
      KeyManagerPassphraseChange.fromJsArr(arr) match {
        case Failure(err) =>
          reject(ValidationRejection("failure", Some(err)))
        case Success(KeyManagerPassphraseChange(oldPassword, newPassword)) =>
          complete {
            val path = conf.seedPath
            WalletStorage.changeAesPassword(path,
                                            Some(oldPassword),
                                            Some(newPassword))

            Server.httpSuccess(ujson.Null)
          }
      }

    case ServerCommand("keymanagerpassphraseset", arr) =>
      KeyManagerPassphraseSet.fromJsArr(arr) match {
        case Failure(err) =>
          reject(ValidationRejection("failure", Some(err)))
        case Success(KeyManagerPassphraseSet(password)) =>
          complete {
            val path = conf.seedPath
            WalletStorage.changeAesPassword(path, None, Some(password))

            Server.httpSuccess(ujson.Null)
          }
      }
  }
}
