/**
  * Created by ht on 9/6/16.
  */

import com.biosimilarity.lift.test.MonadicNodeUsage.FramedMsgDispatcherUseCase._

object TestyThingie {

  def setupAndRun(): Unit = {
    val ds = setup("localhost", 5672, "localhost", 6672)(true)
    runClient(ds)
  }
}

TestyThingie.setupAndRun()
