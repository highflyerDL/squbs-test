import java.util.concurrent.TimeUnit

import com.github.blemale.scaffeine.{LoadingCache, Scaffeine}

import scala.util.Random

object CacheTest {

  def main(args: Array[String]): Unit = {

    def testFunc: Int = {
      val result = Random.nextInt(4)
      println(s"updated cache with $result")
      result
    }

    val ab: LoadingCache[Int, Int] = Scaffeine()
//      .expireAfterWrite(1, TimeUnit.SECONDS)
      .refreshAfterWrite(1, TimeUnit.SECONDS)
      .build((key: Int) => testFunc)

    def getAndPrint(key: Int) = {
      val result = ab.get(key)
      println(result)
    }

    getAndPrint(1)
    getAndPrint(1)
    Thread.sleep(1500)
    getAndPrint(1)
    getAndPrint(1)
  }
}
