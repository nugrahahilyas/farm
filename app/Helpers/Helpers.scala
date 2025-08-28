package Helpers

import anorm.NamedParameter
import play.api.libs.json.*
import play.api.mvc.{AnyContent, MultipartFormData, Request}

import java.sql.Connection
import java.text.SimpleDateFormat
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.{Calendar, Date, Locale}

/** Helper function to keep your code easy to read, and DRY */
object Helpers {

  val limit = 10

  val mySecretKey: String = "Li-An-RI-rY-Da-hA-Ex-Gi"

  /** Pagination helper function
    *
    * @param page
    * -> current page
    * @param limiter
    * -> boundaries of data limit
    * @return
    *   the first row nummber of the datahbase for pagination
    */
  def start(page: Int, limiter: Int = 0): Int =
    (page - 1) * (if (limiter != 0) limiter else limit)

  /** Fungsi untuk menangkap url query POST */
  def postData(request_body: AnyContent, param: String): String = {
    if (request_body.asFormUrlEncoded.get.contains(param)) {
      request_body.asFormUrlEncoded.get(param).head.replace("'", "`")
    } else {
      ""
    }

  }

  def postMultiPart(
      request_body: Request[MultipartFormData[AnyContent]],
      param: String
  ): String =
    request_body.body.asFormUrlEncoded(param).head.replace("'", "`")

  /** Fungsi untuk menangkap url query GET */
  def getData(request: Request[AnyContent], param: String): String =
    request.getQueryString(param).getOrElse("")

  def createMsg(list: List[String]): JsArray = {
    val x = list.map(v => JsString(v)).toIndexedSeq

    JsArray(x)
  }

  private def filterDataType(key: String): String =
    if (key.indexOf(".") == -1) key
    else key.substring(0, key.indexOf("."))

  private def parseDataDb(data: Map[String, Any]): Seq[NamedParameter] =
    data.map { case (key, value) =>
      import java.util.UUID
      val filteredKey = filterDataType(key)
//      println("jaajja: " + key)
//      println("halow: " + value.getClass)

      value match {
        case b: Boolean         => NamedParameter(filteredKey, b)
        case f: Float           => NamedParameter(filteredKey, f)
        case d: Double          => NamedParameter(filteredKey, d)
        case i: Int             => NamedParameter(filteredKey, i)
        case l: Long            => NamedParameter(filteredKey, l)
        case s: String          => NamedParameter(filteredKey, s)
        case ldt: LocalDateTime => NamedParameter(filteredKey, ldt)
        case lt: LocalDate      => NamedParameter(filteredKey, lt)
        case uuid: UUID         => NamedParameter(filteredKey, uuid)
        case jsValue: JsValue   => NamedParameter(filteredKey, Json.stringify(jsValue)) // tipe data Json harus di kasih .json
        case _                  => NamedParameter(filteredKey, null)
      }
    }.toSeq

  def insertDB(table: String, data: Map[String, Any], pkType: String = "Long")(implicit
      connection: Connection
  ): (Option[Any], String) = {
    import anorm.*
    import org.postgresql.util.PSQLException

    val keysList = data.keys.map(key => key).toList
    val keys     = keysList.map(v => filterDataType(v)).mkString(", ")
    val values = keysList.map { key =>
      if (data(key) == null) {
        null
      } else if (key.indexOf("Ω") != -1) {
        s"pgp_sym_encrypt('${data(key)}', '$mySecretKey')"
//        s"crypt($encryptPgp, gen_salt('bf'))"
      } else if (key.indexOf(".") != -1) {
        "{" + filterDataType(key) + "}::" + key.substring(
          key.indexOf(".") + 1
        ) + " "
      } else {
        "{" + filterDataType(key) + "}"
      }
    }
      .mkString(", ")

    var message         = ""
    var id: Option[Any] = None
    val tip = pkType match {
      case "Long"   => SqlParser.scalar[Long]
      case "String" => SqlParser.scalar[String]
      case _        => SqlParser.scalar[Long]
    }

    val statement =
      s"INSERT INTO $table ($keys) VALUES ($values) ".replace("Ω", "")

    val parseData = parseDataDb(data)

    println(statement)
    try
      id = SQL(statement).on(parseData: _*).executeInsert(tip.singleOpt)
    catch {
      case e1: PSQLException =>
        message = e1.toString
        id = None
      case e2: Exception =>
        {
          message = e2.toString
          id = None
        }

        val statementlog = statement.replace("'", "`")
        val messagelog   = message.replace("'", "`")

        // SQL(s"insert into ng_dblog (action, query, err_msg) values ('insert', '$statementlog', '$messagelog') ").executeInsert()
        println("ini err = " + message)
    }

    println(message)
    (id, message)
  }

  def updateDB(table: String, data: Map[String, Any], query: String)(implicit
      connection: Connection
  ): (Int, String) = {
    import anorm.*
    import org.postgresql.util.PSQLException

    val set = data.keys.map { key =>
      if (data(key) == null) {
        s" ${filterDataType(key)} = NULL"
      } else if (key.indexOf("Ω") != -1) {
        key.replace("Ω", "") + " = " + data(key)
      } else if (key.indexOf(".") != -1) {
        s"${filterDataType(key)} = " +
          s"{${filterDataType(key)}}::${key.substring(key.indexOf(".") + 1)} "
      } else {
        s" $key = {$key} "
      }

    }.toList
      .mkString(", ")

    val parseData = parseDataDb(data)

    var message = ""
    var id: Int = -1

    val statement = s"UPDATE $table SET $set WHERE $query "

    try
      id = SQL(statement).on(parseData: _*).executeUpdate()
    catch {
      case e1: PSQLException =>
        message = e1.toString

      case e2: Exception =>
        {
          message = e2.toString
          id = -1
        }

        val statementlog = statement.replace("'", "`")
        val messagelog   = message.replace("'", "`")

      // SQL(s"insert into ng_dblog (action, query, err_msg) values ('update', '$statementlog', '$messagelog') ").executeInsert()
    }
    println(message)

    (id, message)
  }

  def deleteDB(table: String, query: String)(implicit
      connection: Connection
  ): (Int, String) = {
    import anorm.*
    import org.postgresql.util.PSQLException

    var message = ""
    var id: Int = -1

    val statement = s"DELETE FROM $table WHERE $query "

    try
      id = anorm.SQL(statement).executeUpdate()
    catch {
      case e1: PSQLException =>
        message = e1.toString

      case e2: Exception =>
        {
          message = e2.toString
          id = -1
        }

        val statementlog = statement.replace("'", "`")
        val messagelog   = message.replace("'", "`")
        println(message)
      // SQL(s"insert into ng_dblog (action, query, err_msg) values ('update', '$statementlog', '$messagelog') ").executeInsert()
    }

    (id, message)
  }

  def dateBetween(column: String, awal: String, akhir: String): String =
    " AND " + column + " BETWEEN '" + awal + " 00:00:00' AND '" + akhir + " 23:59:59' "

  /** Fungsi untuk mengkonversi format tanggal aplikasi (dd/mm/YYYY) menjadi format database (YYYY-mm-dd) */
  def date2DB(tanggal: String): String = {
    try {
      val parse = tanggal.split("/")
      parse(2) + "-" + parse(1) + "-" + parse(0)

    } catch {
      case e: Exception =>
        null
    }

  }

  /** Fungsi untuk mengkonversi format tanggal menjadi format database (YYYY-mm-dd) menjadi format aplikasi (dd/mm/YYYY)
    */
  def date2App(tanggal: String): String = {
    var format = ""
    try {
      val parse = tanggal.split("-")
      format = parse(2) + "/" + parse(1) + "/" + parse(0)
    } catch {
      case e: Exception =>
    }

    format
  }

  def getCalendar(date: String, format: String): Calendar = {
    val calendar = Calendar.getInstance()
    val sdf      = new SimpleDateFormat(format, Locale.ENGLISH)
    calendar.setTime(sdf.parse(date))
    calendar
  }

  def getNextDate(curDate: String): String = {
    var nextDate = ""
    try {
      val today  = Calendar.getInstance()
      val format = new SimpleDateFormat("yyyy-MM-dd")
      val date   = format.parse(curDate)
      today.setTime(date)
      today.add(Calendar.DAY_OF_YEAR, 1)
      nextDate = format.format(today.getTime())
    } catch {
      case e: Exception =>
        return nextDate
    }
    nextDate
  }

  /** Fungsi untuk mengkonversi format bulan menjadi format database (YYYY-mm menjadi format bulan indonesia */
  def month2IndoFormat(tanggal: String): String = {
    var format = ""
    try {
      val baru  = tanggal.split("-")
      var month = ""
      if (baru(1) == "01")
        month = "Januari"
      if (baru(1) == "02")
        month = "Februari"
      if (baru(1) == "03")
        month = "Maret"
      if (baru(1) == "04")
        month = "April"
      if (baru(1) == "05")
        month = "Mei"
      if (baru(1) == "06")
        month = "Juni"
      if (baru(1) == "07")
        month = "Juli"
      if (baru(1) == "08")
        month = "Agustus"
      if (baru(1) == "09")
        month = "September"
      if (baru(1) == "10")
        month = "Oktober"
      if (baru(1) == "11")
        month = "November"
      if (baru(1) == "12")
        month = "Desember"

      format = month + " " + baru(0)
    } catch {
      case e: Exception =>
        format = ""
    }

    format

  }

  /** Fungsi untuk mengkonversi format Java Date ke String */
  def date2String(tanggal: Date, frm: String = "d/MM/y"): String = {
    var date: String = null
    if (tanggal != null) {
      val format = new SimpleDateFormat(frm)
      date = format.format(tanggal)
    }
    date
  }

  /** Fungsi untuk mengkonversi format Java Date ke String */
  def dateTime2String(
      tanggal: Date,
      frm: String = "d/MM/y HH:mm:ss"
  ): String = {
    var date: String = null
    if (tanggal != null) {
      val format = new SimpleDateFormat(frm)
      date = format.format(tanggal)
    }
    date
  }

  def dbDate2String[A](date: A, frm: String = "yyyy-MM-dd"): String = {
    val customFormater = DateTimeFormatter.ofPattern(frm)

    date match {
      case ldt: LocalDateTime => ldt.format(customFormater)
      case ld: LocalDate      => ld.format(customFormater)
      case _                  => null
    }
  }

  /** @param dateString
    * @param format
    * @return
    *   parsed_localDateTime
    *
    * Function for convert string to LocalDateTime
    */
  def stringToLocalDateTime(dateString: String): LocalDateTime = {
    val formatter = DateTimeFormatter.ISO_DATE_TIME
    // Parse String to LocalDateTime
    LocalDateTime.parse(dateString, formatter)
  }

  def stringToLocalDate(dateString: String): LocalDate = {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    // Parse String to LocalDateTime
    LocalDate.parse(dateString, formatter)
  }

  /** Fungsi untuk mengambil tanggal sekarang */
  def dateNow(frm: String = "yyyy-MM-dd"): String = {
    val format = new SimpleDateFormat(frm)
    println(format.format(Calendar.getInstance().getTime()))
    format.format(Calendar.getInstance().getTime())
  }

  def stringToDate(
      stringDate: String,
      format: String = "yyyy/MM/dd H:m:s-z"
  ): Date = {
    val simpleDateFormat = new SimpleDateFormat(format)
    val date             = simpleDateFormat.parse(stringDate)
    println(date)
    date
  }

  def durationBetweenTimes(
      dateStart: String,
      dateEnd: String,
      format: String = "yyyy-MM-dd HH:mm:ss"
  ): Long = {
    val daySeconds = 86400

    if (secondsBetweenDates(dateStart, dateEnd) < 0) {
      daySeconds + secondsBetweenDates(dateEnd, dateStart)
    } else {
      secondsBetweenDates(dateStart, dateEnd)
    }
  }
  //  def stringToDate(stringDate: String, format: String = "yyyy-MM-dd"): Date = {
  //    val simpleDateFormat = new SimpleDateFormat(format)
  //    val date = simpleDateFormat.parse(stringDate)
  //    date
  //  }

  /** Fungsi untuk menghitung selisih hari dari 2 waktu */
  def secondsBetweenDates(
      start: String,
      end: String,
      format: String = "yyyy-MM-dd HH:mm:ss"
  ): Long = {
    import java.util.concurrent.TimeUnit

    val simpleDateFormat = new SimpleDateFormat(format)
    val date1            = simpleDateFormat.parse(start)
    val date2            = simpleDateFormat.parse(end)

    val timeInMilis = date2.getTime() - date1.getTime()
    TimeUnit.SECONDS.convert(timeInMilis, TimeUnit.MILLISECONDS)
  }

  /** Fungsi untuk mengambil tanggal / bulan sesudah atau sebelum */
  def dateMode(mode: Int, numb: Int, frm: String = "dd/MM/yyyy"): String = {
    val cal = Calendar.getInstance()
    cal.add(mode, numb)
    val result = cal.getTime()
    val format = new SimpleDateFormat(frm)
    format.format(result)
  }

  /** Fungsi untuk menghitung selisih hari dari 2 waktu */
  def dayBetweenDates(
      start: String,
      end: String,
      format: String = "yyyy-MM-dd"
  ): Long = {
    import java.util.concurrent.TimeUnit

    val simpleDateFormat = new SimpleDateFormat(format)
    val date1            = simpleDateFormat.parse(start)
    val date2            = simpleDateFormat.parse(end)

    val timeInMilis = Math.abs(date2.getTime() - date1.getTime())
    TimeUnit.DAYS.convert(timeInMilis, TimeUnit.MILLISECONDS)
  }

  /** Fungsi untuk menghitung selisih dari 2 waktu nilai kembalian Map[String, Int] */
  def timeBetween(start: String, end: String): Map[String, Int] = {
    import org.joda.time.{DateTime, Interval, Period}
    var time: Map[String, Int] = Map()
    try {
      var buff  = start.split(" ")
      val date1 = buff(0).split("-")
      val time1 = buff(1).split(":")

      buff = end.split(" ")
      val date2 = buff(0).split("-")
      val time2 = buff(1).split(":")

      val i = new Interval(
        new DateTime(
          date1(0).toInt,
          date1(1).toInt,
          date1(2).toInt,
          time1(0).toInt,
          time1(1).toInt,
          time1(2).toInt
        ),
        new DateTime(
          date2(0).toInt,
          date2(1).toInt,
          date2(2).toInt,
          time2(0).toInt,
          time2(1).toInt,
          time2(2).toInt
        )
      )
      val p = i.toPeriod()

      time = Map(
        "days"    -> p.getDays(),
        "hours"   -> p.getHours,
        "minutes" -> p.getMinutes,
        "seconds" -> p.getSeconds
      )

    } catch {
      case e: Exception =>
        time = Map(
          "days"    -> 0,
          "hours"   -> 0,
          "minutes" -> 0,
          "seconds" -> 0
        )
    }

    time
  }

  /** Fungsi untuk memformat tampilan hasil dari fungsi timeBetween nilai kembalian String */
  def durationFormat(duration: Map[String, Int]): String = {
    var format = "Parameter yang anda masukkan salah"
    if (
      duration.contains("days") & duration.contains("hours") & duration
        .contains("minutes") & duration.contains("seconds")
    ) {
      format = ""
      if (duration("days") > 0)
        format = format + duration("days") + " hari "

      if (duration("hours") > 0)
        format = format + duration("hours") + " jam "

      if (duration("minutes") > 0) {
        format = format + duration("minutes") + " menit "
      }

      if (duration("hours") < 1) {
        if (duration("seconds") > 0) {
          format = format + duration("seconds") + " detik "
        }
      }
    }

    format
  }

  /** Fungsi untuk mengetahui jumlah hari dalam bulan tertentu parameter yang diisikan adalah tanggal */
  def daysOfMonth(tanggal: String): Int = {
    import org.joda.time.DateTime
    val dt = new DateTime(tanggal)
    dt.dayOfMonth().getMaximumValue()
  }

  def truncateAt(n: Double, p: Int): Double = {
    val s = math.pow(10, p);
    (math.floor(n * s)) / s
  }

  def countAge(birthdate: String): Long = {
    import java.time.LocalDate
    import java.time.temporal.ChronoUnit
    val buffDate = birthdate.split("-")
    val start =
      LocalDate.of(buffDate(0).toInt, buffDate(1).toInt, buffDate(2).toInt)
    val end = LocalDate.now()

    ChronoUnit.YEARS.between(start, end)
  }

  def countAgeDays(birthdate: String): Long = {
    import java.time.LocalDate
    import java.time.temporal.ChronoUnit
    val buffDate = birthdate.split("-")
    val start =
      LocalDate.of(buffDate(0).toInt, buffDate(1).toInt, buffDate(2).toInt)
    val end = LocalDate.now()

    ChronoUnit.DAYS.between(start, end)
  }

  def countAgeBetweenDates(birthdate: String, date: String): Long = {
    import java.time.LocalDate
    import java.time.temporal.ChronoUnit
    val buffDate = birthdate.split("-")
    val buffNow  = date.split("-")
    val start =
      LocalDate.of(buffDate(0).toInt, buffDate(1).toInt, buffDate(2).toInt)
    val end = LocalDate.of(buffNow(0).toInt, buffNow(1).toInt, buffNow(2).toInt)

    ChronoUnit.YEARS.between(start, end)
  }

  def countAgeDaysBetweenDates(birthdate: String, date: String): Long = {
    import java.time.LocalDate
    import java.time.temporal.ChronoUnit
    val buffDate = birthdate.split("-")
    val buffNow  = date.split("-")
    val start =
      LocalDate.of(buffDate(0).toInt, buffDate(1).toInt, buffDate(2).toInt)
    val end = LocalDate.of(buffNow(0).toInt, buffNow(1).toInt, buffNow(2).toInt)

    ChronoUnit.DAYS.between(start, end)
  }

  def fullDay2IndoFormat(tanggal: String): String = {
    // tanggal = Y-M-d HH:mm:ss

    import java.time.LocalDate
    import java.time.format.DateTimeFormatter

    var format = ""
    try {
      val buff = tanggal.split(" ")

      val formatter = DateTimeFormatter.ofPattern("yyyy-M-d")
      val date      = LocalDate.parse(buff(0), formatter)
      val day       = date.getDayOfWeek()

      var hari = ""
      day.toString match {
        case "MONDAY"    => hari = "Senin"
        case "TUESDAY"   => hari = "Selasa"
        case "WEDNESDAY" => hari = "Rabu"
        case "THURSDAY"  => hari = "Kamis"
        case "FRIDAY"    => hari = "Jumat"
        case "SATURDAY"  => hari = "Sabtu"
        case "SUNDAY"    => hari = "Minggu"
        case _           =>
      }

      format = hari + ", " + dateTime2IndoFormat(tanggal)
    } catch {
      case e: Exception =>
        println(e)
        format = ""
    }

    format

  }

  def dateTime2IndoFormat(tanggal: String): String = {
    // tanggal = Y-M-d HH:mm:ss
    var format = ""
    try {
      val buff = tanggal.split(" ")
      format = date2IndoFormat(buff(0)) + " " + buff(1)
    } catch {
      case e: Exception =>
        format = ""
    }

    format

  }

  /** Fungsi untuk mengkonversi format tanggal menjadi format database (YYYY-mm-dd) menjadi format tanggal indonesia */
  def date2IndoFormat(tanggal: String): String = {
    var format = ""
    try {
      val baru  = tanggal.split("-")
      var month = ""
      if (baru(1) == "01")
        month = "Januari"
      if (baru(1) == "02")
        month = "Februari"
      if (baru(1) == "03")
        month = "Maret"
      if (baru(1) == "04")
        month = "April"
      if (baru(1) == "05")
        month = "Mei"
      if (baru(1) == "06")
        month = "Juni"
      if (baru(1) == "07")
        month = "Juli"
      if (baru(1) == "08")
        month = "Agustus"
      if (baru(1) == "09")
        month = "September"
      if (baru(1) == "10")
        month = "Oktober"
      if (baru(1) == "11")
        month = "November"
      if (baru(1) == "12")
        month = "Desember"

      format = baru(2) + " " + month + " " + baru(0)
    } catch {
      case e: Exception =>
        format = ""
    }

    format

  }

  def monthJump(curDate: String, jump: Int): String = {
    import java.util.Calendar

    var newDate = ""
    try {
      val today  = Calendar.getInstance()
      val format = new SimpleDateFormat("yyyy-MM-dd")
      val date   = format.parse(curDate)
      today.setTime(date)
      today.add(Calendar.MONTH, jump)
      newDate = format.format(today.getTime())
    } catch {
      case e: Exception =>
        return newDate
    }
    newDate
  }

//  def numberToMoney(number: Double): String = {
//    val locale = new java.util.Locale("de", "DE")
//    val formatter = java.text.NumberFormat.getInstance(locale)
//    formatter.format(number)
//  }

  def generateSignature(appID: String, key: String): (String, String, Int) = {
    import java.util.Date

    val date: Date     = new Date()
    val timemilis: Int = (date.getTime() / 1000).toInt

    val data: String = appID + "&" + timemilis.toString
    val sign         = generateHmacSHA256Signature(data, key)
    (appID, sign, timemilis)
  }

  def generateHmacSHA256Signature(data: String, key: String): String = {
    import java.io.UnsupportedEncodingException
    import java.security.{InvalidKeyException, NoSuchAlgorithmException}
    import java.util.Base64
    import javax.crypto.Mac
    import javax.crypto.spec.SecretKeySpec

    var a: String = ""
    try {
      val secretKey = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256")
      val mac       = Mac.getInstance("HmacSHA256")
      mac.init(secretKey)
      val x: Array[Byte] =
        Base64.getEncoder().encode(mac.doFinal(data.getBytes("UTF-8")))
      a = new String(x)
    } catch {
      case e: Exception =>
    }

    a
  }

  def snakeToPascalCase(input: String): String = {
    val words            = input.split("_")
    val capitalizedWords = words.map(_.capitalize)
    capitalizedWords.mkString(" ")
  }

  def extractErrMessage(errorMessage: String): String = {
    val regex   = """\(([^)]+)\)=\(([^)]+)\)"""
    val pattern = regex.r

    pattern
      .findAllMatchIn(errorMessage)
      .map { matchResult =>
        println(matchResult)
        val columnName = matchResult.group(1)
        val value      = matchResult.group(2)
        s"${formatColumnName(columnName)} = $value"
      }
      .mkString(", ")
  }

  private def formatColumnName(columnName: String): String =
    columnName.split("_").map(_.capitalize).mkString(" ")

  def validateRequest[T](
      request: T,
      requiredKeys: List[String]
  ): Option[String] = {
    val missingKeys =
      requiredKeys.filterNot(requestBody => request.getClass.getDeclaredFields.map(_.getName).contains(requestBody))
    if (missingKeys.nonEmpty)
      Some(s"Required field(s) missing: ${missingKeys.mkString(", ")}")
    else None
  }

  def convertFloatToTimeString(hours: Float): String = {
    val totalMenit = (hours * 60).toInt
    val jam        = totalMenit / 60
    val menit      = totalMenit % 60

    f"$jam%02d:$menit%02d:00"
  }

//  def getFileContent(path: String): ReturnText = {
//    import java.io.IOException
//    import scala.io.Source
//
//    val fileSplit = path.split("/")
//    val fileName  = fileSplit(fileSplit.size - 1)
//
//    var status: Boolean = true
//    var message: String = ""
//
//    val text: String =
//      try
//        Source.fromFile(path).getLines.mkString("\n")
//      catch {
//        case ioe: IOException =>
//          status = false
//          message = s"File $fileName tidak ditemukan"
//          null
//
//        case e: Exception =>
//          status = false
//          message = s"File $fileName tidak ditemukan"
//          null
//      }
//
//    ReturnText(status, text, message)
//  }

}
