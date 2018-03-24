package sangria.gateway.file

import java.nio.file.{NoSuchFileException, StandardWatchEventKinds}

import akka.actor.{Actor, ActorRef, Cancellable, PoisonPill, Props}
import akka.event.Logging
import better.files._
import sangria.gateway.file.FileWatcher._

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class FileMonitorActor(paths: Seq[File], threshold: FiniteDuration, globs: Seq[String], cb: Vector[File] ⇒ Unit) extends Actor {
  import FileMonitorActor._

  import context.dispatcher

  val log = Logging(context.system, this)
  var watchers: Seq[ActorRef] = _
  val pendingFiles: mutable.HashSet[File] = mutable.HashSet[File]()
  var scheduled: Option[Cancellable] = None

  override def preStart(): Unit = {
    watchers = paths.map(_.newWatcher(recursive = true))

    watchers.foreach { watcher ⇒
      watcher ! when(events = StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE) {
        case (_, file) ⇒ self ! FileChange(file)
      }
    }
  }

  def receive = {
    case FileChange(file) ⇒
      try {
        if (file.exists && !file.isDirectory && globs.exists(file.glob(_, includePath = false).nonEmpty)) {
          pendingFiles += file

          if (scheduled.isEmpty)
            scheduled = Some(context.system.scheduler.scheduleOnce(threshold, self, Threshold))
        }
      } catch {
        case _: NoSuchFileException ⇒ // ignore, it's ok
      }

    case Threshold ⇒
      val files = pendingFiles.toVector.sortBy(_.name)

      if (files.nonEmpty)
        cb(files)

      pendingFiles.clear()
      scheduled = None
  }
}

object FileMonitorActor {
  case class FileChange(file: File)
  case object Threshold

  def props(paths: Seq[File], threshold: FiniteDuration, globs: Seq[String], cb: Vector[File] ⇒ Unit) = Props(new FileMonitorActor(paths, threshold, globs, cb))
}