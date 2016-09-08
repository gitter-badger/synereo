/**
  * Created by ht on 9/6/16.
  */

import com.biosimilarity.lift.test.MonadicNodeUsage.FramedMsgDispatcherUseCase._

object TestyThingie {

  def setupAndRun(): Unit = {
    val ds = setup("localhost", 6672, "localhost", 5672)(true)
    runServer(ds)
  }
}

TestyThingie.setupAndRun()
