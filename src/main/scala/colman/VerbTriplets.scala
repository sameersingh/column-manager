package colman

import java.io.File

import scala.collection.immutable.HashSet
import scala.collection.mutable

/**
 * Created by sameer on 5/14/15.
 */
object VerbTriplets {
  val baseDir = "/home/sameer/work/data/gilette/triplets-0521/"
  val inputFiles = (0 until 75).map(i => baseDir + "part-r-%05d.gz".format(i))
  val polarityFile = baseDir + "../verbs_output.csv"
  // common
  val triplets = baseDir + "triplets.gz"
  val commonVerbs = baseDir + "common-verbs.gz"
  val commonSubjects = baseDir + "common-subjs.gz"
  val commonObjects = baseDir + "common-objs.gz"

  val urls = baseDir + "urls.gz"
  val urlObjectVerbs = baseDir + "objs.gz"
  val urlSubjectVerbs = baseDir + "subjs.gz"

  val nounPolarity = baseDir + "noun-polarity.gz"

  def run(action: Action, input: Seq[String], output: String) = {
    val as = Actions(action, new Writer(output, true))
    as(FileUtil.readFiles(input, true))
  }
}

object Lemmatize {
  def main(args: Array[String]): Unit = {
    def lemma(v: String) = {
      val split = v.split("\\|")
      val word = split.dropRight(1).mkString("|")
      val tag = split.last
      StanfordLemma.lemma(word, tag)
    }
    VerbTriplets.run(Actions(
      Transform(1, v => lemma(v)),
      Transform(2, v => lemma(v)),
      Transform(3, v => lemma(v))), VerbTriplets.inputFiles, VerbTriplets.triplets)
  }
}

object CommonVerbs {
  def main(args: Array[String]): Unit = {
    VerbTriplets.run(Actions(Cut(2, 4),
      SumLast(), Sort(1, true),
      Transform(1, _.toDouble.toInt.toString)),
      Seq(VerbTriplets.triplets),
      VerbTriplets.commonVerbs)
  }
}

object CommonSubjects {
  def main(args: Array[String]): Unit = {
    VerbTriplets.run(Actions(Cut(1, 4),
      SumLast(), Sort(1, true),
      Transform(1, _.toDouble.toInt.toString)),
      Seq(VerbTriplets.triplets),
      VerbTriplets.commonSubjects)
  }
}

object CommonObjects {
  def main(args: Array[String]): Unit = {
    VerbTriplets.run(Actions(Cut(3, 4),
      SumLast(), Sort(1, true),
      Transform(1, _.toDouble.toInt.toString)),
      Seq(VerbTriplets.triplets),
      VerbTriplets.commonObjects)
  }
}

object Common {
  def main(args: Array[String]): Unit = {
    println("lemma")
    Lemmatize.main(Array())
    println("verbs")
    CommonVerbs.main(Array())
    println("subjs")
    CommonSubjects.main(Array())
    println("objs")
    CommonObjects.main(Array())
  }
}

object Urls{
  def main(args: Array[String]): Unit = {
    VerbTriplets.run(Actions(Cut(0, 4),
      SumLast(), Sort(1, true),
      Transform(1, _.toDouble.toInt.toString),
      new Filter(fs => UrlPolarity(fs(0)).isDefined),
      Transform(0, s => s + "\t" + UrlPolarity(s).get)
    ),
      Seq(VerbTriplets.triplets),
      VerbTriplets.urls)
  }
}

// Data for motivation table
// object verb lcount rcount lcount-rcount total
object ObjectVerbs {
  def main(args: Array[String]): Unit = {
    VerbTriplets.run(Actions(
      new Filter(fs => UrlPolarity(fs(0)).isDefined),
      Transform(0, s => UrlPolarity(s).get),
      Cut(3, 2, 0, 4), // obj, verb, left/right, count
      Transform.all(fs => {
        val count = fs(3).toInt
        val left = (fs(2) == "left")
        assert(left || fs(2) == "right", fs.mkString("\t"))
        val countl = if(left) count else 0
        val countr = if(left) 0 else count
        val diff = if(left) count else -count
        val total = count
        Some(Seq(fs(0), fs(1), countl.toString, countr.toString, diff.toString, total.toString))
      }), // object verb lcount rcount lcount-rcount total
      SumLast(4), new Filter(fs => fs(5).toDouble > 4.0),
      Sort(5, true), Sort(0, false),
      Transform(2, _.toDouble.toInt.toString),
      Transform(3, _.toDouble.toInt.toString),
      Transform(4, _.toDouble.toInt.toString),
      Transform(5, _.toDouble.toInt.toString)),
      Seq(VerbTriplets.triplets),
      VerbTriplets.urlObjectVerbs)
  }
}

// Data for motivation table
// subject verb lcount rcount lcount-rcount total
object SubjectVerbs {
  def main(args: Array[String]): Unit = {
    VerbTriplets.run(Actions(
      new Filter(fs => UrlPolarity(fs(0)).isDefined),
      Transform(0, s => UrlPolarity(s).get),
      Cut(1, 2, 0, 4), // subj verb left/right count
      Transform.all(fs => {
        val count = fs(3).toInt
        val left = (fs(2) == "left")
        assert(left || fs(2) == "right", fs.mkString("\t"))
        val countl = if(left) count else 0
        val countr = if(left) 0 else count
        val diff = if(left) count else -count
        val total = count
        Some(Seq(fs(0), fs(1), countl.toString, countr.toString, diff.toString, total.toString))
      }), // subj verb lcount rcount lcount-rcount total
      SumLast(4), new Filter(fs => fs(5).toDouble > 4.0),
      Sort(5, true), Sort(0, false),
      Transform(2, _.toDouble.toInt.toString),
      Transform(3, _.toDouble.toInt.toString),
      Transform(4, _.toDouble.toInt.toString),
      Transform(5, _.toDouble.toInt.toString)),
      Seq(VerbTriplets.triplets),
      VerbTriplets.urlSubjectVerbs)
  }
}

object DataExamples {
  def main(args: Array[String]): Unit = {
    println("objs")
    ObjectVerbs.main(Array())
    println("subjs")
    SubjectVerbs.main(Array())
  }
}

// Data for results table,
// noun url cum_total cum_polarity
object NounPolarity {

  val nouns = HashSet("Medicare", "Medicaid", "Iraq", "marijuana", "Obama", "taxes", "immigration",
    "abortion", "Israel", "Palestine", "marriage", "Parenthood", "welfare")

  def main(args: Array[String]): Unit = {
    VerbPolarity.read(VerbTriplets.polarityFile)

    VerbTriplets.run(Actions( // url subj verb obj count
    Transform.all(fs => {
      if(!nouns(fs(1)) && !nouns(fs(3))) None
      else {
        val polarity = VerbPolarity(fs(2))
        if(polarity.isEmpty) None
        else {
          val (noun, isSubj) = if(nouns(fs(1))) fs(1) -> true else fs(3) -> false
          val nounPolarity = if(isSubj) polarity.get.spSubj else polarity.get.spObj
          val count = fs(4).toInt
          Some(Seq(noun, fs(0), count, count*nounPolarity).map(_.toString))
        }}}), // noun url count count*polarity
    SumLast(2), Sort(3, true), Sort(0),
      Transform(2, _.toDouble.toInt.toString),
      Transform(3, _.toDouble.toInt.toString)
//      Transform(4, _.toDouble.toInt.toString),
//      Transform(5, _.toDouble.toInt.toString)
    ),
      Seq(VerbTriplets.triplets),
      VerbTriplets.nounPolarity)
  }
}

object VerbPolarity {
  case class Polarity(spSubj: Int, spObj: Int, subjObj: Int)

  val map = new mutable.HashMap[String, Polarity]

  def read(fname: String): Unit = {
    def str2int(s: String) = s match {
      case "N" => 0
      case "+" => 1
      case "-" => -1
    }
    map.clear()
    for(l <- FileUtil.read(fname, false, ",")) {
      assert(l.length == 4)
      assert(!map.contains(l(0)))
      map(l(0)) = Polarity(str2int(l(1)), str2int(l(2)), str2int(l(3)))
    }
  }

  def apply(verb: String) = map.get(verb)

}

object UrlPolarity {
  val left = Map("Huffington Post" -> "huffingtonpost.com",
    "Slate" -> "slate.com",
    "Think Progress"->"thinkprogress.org",
    "Newsweek" -> "newsweek.com",
    "Politico" -> "politico.com",
    "Washington Post" -> "washingtonpost.com",
    "New York Times" -> "nytimes.com",
    "Los Angeles Times" -> "latimes.com",
    "The New Yorker" -> "newyorker.com",
    "Salon" -> "salon.com",
    "Guardian" -> "theguardian.com",
    "The Daily Beast" -> "thedailybeast.com",
    "OpEd News" -> "opednews.com",
    "RhReality Check"->"rhrealitycheck.org",
    "NPR" -> "npr.org")
  val right = Map("Fox News" -> "foxnews.com", // changed from org
    "New York Post" -> "nypost.com",
    "Washington Times" -> "washingtontimes.com",
    "The American Conservative" -> "theamericanconservative.com",
    "Weekly Standard" -> "weeklystandard.com",
    "National Review" -> "nationalreview.com",
    "Townhall"->"townhall.com",
    "Life Site News" -> "lifesitenews.com",
    "National Right To Life News" -> "nationalrighttolifenews.org",
    "Breitbart" -> "breitbart.com",
    "World Net Daily" -> "wnd.com",
    "City Journal" -> "city-journal.org",
    "The Hill" -> "thehill.com",
    "Human Events" -> "humanevents.com",
    "The Blaze" -> "theblaze.com")

  def apply(str: String): Option[String] = {
    if(left.exists(kv => str.contains(kv._2))) return Some("left")
    if(right.exists(kv => str.contains(kv._2))) return Some("right")
    None
  }
}