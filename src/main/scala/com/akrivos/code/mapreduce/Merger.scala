package com.akrivos.code.mapreduce

import akka.actor._
import akka.pattern.PipeToSupport

import scala.concurrent.Future

case class MergerConfig(maxItemsPerJob: Int, numOfWorkers: Int)

object Merger {
  case class Merge[T](items: List[T])
  case class MergeResult[T](item: T)
  private case class Job[T](key: Int, items: List[T])
  private case class JobResult[T](key: Int, item: T)
}

class Merger[T](config: MergerConfig, resultReceiver: ActorRef)(merge: (T, T) => T)
  extends Actor with ActorLogging {
  import com.akrivos.code.mapreduce.Merger._

  require(config.maxItemsPerJob > 1)
  require(config.numOfWorkers > 0)

  implicit val ec = context.dispatcher
  private val masterName = "master"

  // create a Master along with its Workers
  val master = context.actorOf(Props[Master], masterName)
  1 to config.numOfWorkers foreach(_ => createWorker(masterName))

  var pendingJobs = Set.empty[Int]
  var merged = List.empty[T]

  override def receive: Receive = {
    case msg: Merge[T] =>
      msg.items.grouped(config.maxItemsPerJob).foreach(items => sendMergeJob(items))
    case msg: JobResult[T] =>
      pendingJobs -= msg.key
      merged +:= msg.item
      if (merged.size == 1 && pendingJobs.isEmpty)
        merged.headOption.foreach(r => resultReceiver ! MergeResult(r))
      else if (merged.size >= config.maxItemsPerJob || pendingJobs.isEmpty) {
        if (merged.size > config.maxItemsPerJob)
          log.warning(s"Queue size is ${merged.size} (> maxItemsPerJob -- ${config.maxItemsPerJob})")
        sendMergeJob(merged)
        merged = List.empty
      }
  }

  private def sendMergeJob(items: List[T]): Unit = {
    val key = items.hashCode()
    master ! Job(key, items)
    pendingJobs += key
  }

  private def createWorker(master: String) = context.actorOf(Props(new MergeWorker(ActorPath.fromString(
    "akka://%s/user/%s/%s".format(context.system.name, self.path.name, master)))))

  private class MergeWorker(masterLocation: ActorPath) extends Worker(masterLocation) with PipeToSupport {
    implicit val ec = context.dispatcher

    override def doWork(workSender: ActorRef, msg: Any): Unit = {
      Future {
        msg match {
          case msg: Job[T] =>
            val item = msg.items.reduceLeft(merge)
            workSender ! JobResult(msg.key, item)
            WorkComplete(item)
          case _ =>
        }
      } pipeTo self
    }
  }
}
