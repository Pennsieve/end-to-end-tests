package com.blackfynn.integration

import cats.data.EitherT
import cats.effect.{ IO, Timer }
import cats.implicits._
import com.blackfynn.integration.models.Login._
import com.blackfynn.dtos.PackageDTO
import com.blackfynn.integration.models.BfPackage.{ getPackage, processPackage }
import com.blackfynn.integration.models.{ createFile, DatasetId }
import com.blackfynn.models.PackageState
import com.blackfynn.models.PackageState.{ READY, UPLOADED }
import org.http4s.Header
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{ DurationLong, FiniteDuration }

package object processing {
  implicit val timer: Timer[IO] = IO.timer(global)

  def recurseCheckingForState(
    getPackage: IO[PackageDTO],
    state: PackageState,
    waitBetweenAttempts: FiniteDuration = 30 seconds,
    totalAttempts: Int = 10
  ): IO[Either[Exception, PackageState]] = {

    getPackage
      .map((_, 0))
      .tailRecM {
        _.map {
          case (packageDto, _) if packageDto.content.state == state =>
            packageDto.content.state.asRight

          case (notReadyPackageDto, attempts) if attempts == totalAttempts =>
            notReadyPackageDto.content.state.asRight

          case (_, attempts) => {
            IO.sleep(waitBetweenAttempts)
              .flatMap(_ => getPackage.map((_, attempts + 1)))
              .asLeft
          }
        }
      }
      .map {
        case finalState if finalState == state => state.asRight[Exception]
        case wrongState => new Exception(s"Found unexpected state $wrongState").asLeft
      }
  }
  def uploadAndAwaitProcessingResult(
    datasetId: DatasetId,
    fileName: String,
    nbAttempts: Int = 15,
    organization: String = organization,
    tokenHeader: Header = bearerTokenHeader,
    sessionHeader: Header = sessionHeader
  ): EitherT[IO, Exception, (PackageState, PackageState)] = {

    for {
      completeResponse <- {
        EitherT {
          upload
            .ioChunkUpload(
              createFile(fileName),
              datasetId,
              organization,
              tokenHeader,
              sessionHeader
            )
            .map(_._2)
        }
      }

      uploadedPackage <- {
        EitherT
          .fromEither[IO](completeResponse)
          .leftMap(_ => new Exception("Not all parts uploaded"))
          .subflatMap(_.headOption.toRight(new Exception("no response")))
          .subflatMap(_.`package`.toRight(new Exception("no package in response")))
      }

      packageId = uploadedPackage.content.id

      inUploadedState <- {
        EitherT {
          recurseCheckingForState(
            getPackage(packageId, tokenHeader, sessionHeader),
            UPLOADED,
            totalAttempts = nbAttempts
          )
        }
      }

      _ <- EitherT(processPackage(packageId, tokenHeader, sessionHeader))

      // CHECKING IF FINAL STATE ARRIVES AT READY
      terminalState <- EitherT {
        IO.sleep(3 minutes)
          .flatMap(
            _ =>
              recurseCheckingForState(
                getPackage(packageId, tokenHeader, sessionHeader),
                READY,
                totalAttempts = nbAttempts
              )
          )
      }

    } yield (inUploadedState, terminalState)
  }
}
