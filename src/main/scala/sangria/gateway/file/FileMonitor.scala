package sangria.gateway.file

import java.nio.file._

import better.files._
import com.sun.nio.file.SensitivityWatchEventModifier

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.NonFatal

class FileMonitor(val root: File, maxDepth: Int) extends File.Monitor {
  protected[this] val service = root.newWatchService

  def this(root: File, recursive: Boolean = true) = this(root, if (recursive) Int.MaxValue else 0)

  /**
    * If watching non-directory, don't react to siblings
    * @param target
    * @return
    */
  protected[this] def reactTo(target: File) = root.isDirectory || root.isSamePathAs(target)

  protected[this] def process(key: WatchKey) = {
    val path = key.watchable().asInstanceOf[Path]

    import scala.collection.JavaConverters._
    key.pollEvents().asScala foreach {
      case event: WatchEvent[Path] @unchecked ⇒
        val target: File = path.resolve(event.context())
        if (reactTo(target)) {
          if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            val depth = root.relativize(target).getNameCount
            watch(target, (maxDepth - depth) max 0) // auto-watch new files in a directory
          }
          onEvent(event.kind(), target, event.count())
        }
      case event ⇒ if (reactTo(path)) onUnknownEvent(event)
    }
    key.reset()
  }

  protected[this] def watch(file: File, depth: Int): Unit = {
    def toWatch: Iterator[File] = if (file.isDirectory) {
      file.walk(depth).filter(f ⇒ f.isDirectory && f.exists)
    } else {
      when(file.exists)(file.parent).iterator  // There is no way to watch a regular file; so watch its parent instead
    }

    try
      toWatch
        .foreach(f ⇒
          Try[Unit](f.path.register(service, File.Events.all.toArray,
            // this is com.sun internal, but the service is useless on OSX without it
            SensitivityWatchEventModifier.HIGH))
          .recover {case error ⇒ onException(error)}.get)
    catch {
      case NonFatal(e) ⇒ onException(e)
    }
  }

  @inline private def when[A](condition: Boolean)(f: => A): Option[A] = if (condition) Some(f) else None

  def start()(implicit executionContext: ExecutionContext) = {
    watch(root, maxDepth)
    executionContext.execute(new Runnable {
      override def run() =  {
        try Iterator.continually(service.take()).foreach(process) catch {
          case _: ClosedWatchServiceException ⇒ // just ignore, the service is already closed!
        }
      }
    })
  }

  def close() = service.close()

  // Although this class is abstract, we give provide implementations so user can choose to implement a subset of these
  def onCreate(file: File, count: Int) = {}
  def onModify(file: File, count: Int) = {}
  def onDelete(file: File, count: Int) = {}
  def onUnknownEvent(event: WatchEvent[_]) = {}
  def onException(exception: Throwable) = {}
}
