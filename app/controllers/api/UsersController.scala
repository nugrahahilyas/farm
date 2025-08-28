package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import models.{UsersModel, User}
import scala.concurrent.ExecutionContext

@Singleton
class UsersController @Inject() (
    cc: ControllerComponents,
    usersModel: UsersModel
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def listUsers: Action[AnyContent] = Action.async {
    usersModel.list().map(users => Ok(Json.toJson(users)))
  }

  def getUserDetail(id: Long): Action[AnyContent] = Action.async {
    usersModel.find(id).map {
      case Some(user) => Ok(Json.toJson(user))
      case None       => NotFound(Json.obj("error" -> "User not found"))
    }
  }

  def insertUser: Action[JsValue] = Action.async(parse.json) { implicit req =>
    val name = (req.body \ "name").as[String]
    val email = (req.body \ "email").as[String]
    val cityId = (req.body \ "cityId").as[Long]
    val address = (req.body \ "address").as[String]

    usersModel.insert(name, email, cityId, address).map { id =>
      Created(Json.obj("message" -> "User created", "id" -> id))
    }
  }

  def updateUser(id: Long): Action[JsValue] = Action.async(parse.json) {
    implicit req =>
      val name = (req.body \ "name").asOpt[String]
      val email = (req.body \ "email").asOpt[String]
      val cityId = (req.body \ "cityId").asOpt[Long]
      val address = (req.body \ "address").asOpt[String]

      usersModel.update(id, name, email, cityId, address).map { updated =>
        if (updated) Ok(Json.obj("message" -> "User updated"))
        else NotFound(Json.obj("error" -> "User not found"))
      }
  }

  def deleteUser(id: Long): Action[AnyContent] = Action.async {
    usersModel.delete(id).map { deleted =>
      if (deleted) Ok(Json.obj("message" -> "User deleted"))
      else NotFound(Json.obj("error" -> "User not found"))
    }
  }
}
