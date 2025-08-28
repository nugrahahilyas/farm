package models

import anorm._
import anorm.SqlParser._
import javax.inject.Inject
import play.api.db.DBApi
import scala.concurrent.{ExecutionContext, Future}

case class User(
                 id: Long = -1,
                 name: String,
                 email: String,
                 cityId: Long,
                 address: String
               )

object User {
  implicit val userFormat: play.api.libs.json.OFormat[User] =
    play.api.libs.json.Json.format[User]
}

class UsersModel @Inject()(dbApi: DBApi)(implicit ec: ExecutionContext) {
  private val db = dbApi.database("default")

  val userParser: RowParser[User] = {
    get[Long]("id") ~
      get[String]("name") ~
      get[String]("email") ~
      get[Long]("city_id") ~
      get[String]("address") map {
      case id ~ name ~ email ~ cityId ~ address =>
        User(id, name, email, cityId, address)
    }
  }

  def list(): Future[Seq[User]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM users ORDER BY id ASC".as(userParser.*)
    }
  }

  def find(id: Long): Future[Option[User]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM users WHERE id = $id".as(userParser.singleOpt)
    }
  }

  def insert(name: String, email: String, cityId: Long, address: String): Future[Long] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        INSERT INTO users (name, email, city_id, address)
        VALUES ($name, $email, $cityId, $address)
      """.executeInsert(scalar[Long].single)
    }
  }

  def update(id: Long, name: Option[String], email: Option[String], cityId: Option[Long], address: Option[String]): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      val parts = Seq(
        name.map(v => s"name = '$v'"),
        email.map(v => s"email = '$v'"),
        cityId.map(v => s"city_id = $v"),
        address.map(v => s"address = '$v'")
      ).flatten

      if (parts.nonEmpty) {
        val sql = s"UPDATE users SET ${parts.mkString(", ")} WHERE id = $id"
        SQL(sql).executeUpdate() > 0
      } else false
    }
  }

  def delete(id: Long): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM users WHERE id = $id".executeUpdate() > 0
    }
  }
}
