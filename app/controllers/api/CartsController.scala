package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import models.{CartsModel, CartFormat}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CartsController @Inject() (
    cc: ControllerComponents,
    cartModel: CartsModel
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  import CartFormat._

  def listCarts: Action[AnyContent] = Action.async {
    cartModel.list().map(carts => Ok(Json.toJson(carts)))
  }

  def getCartDetail(id: Long): Action[AnyContent] = Action.async {
    cartModel.find(id).map {
      case Some(c) => Ok(Json.toJson(c))
      case None    => NotFound(Json.obj("error" -> "Cart not found"))
    }
  }

  def insertCart: Action[JsValue] = Action.async(parse.json) { implicit req =>
    req.body
      .validate[CartReq]
      .fold(
        err => Future.successful(BadRequest(JsError.toJson(err))),
        value =>
          cartModel
            .insert(value)
            .map(id =>
              Created(Json.obj("message" -> "Cart created", "id" -> id))
            )
      )
  }

  def updateCart(id: Long): Action[JsValue] = Action.async(parse.json) {
    implicit req =>
      req.body
        .validate[CartReq]
        .fold(
          err => Future.successful(BadRequest(JsError.toJson(err))),
          value =>
            cartModel.update(id, value).map {
              case true  => Ok(Json.obj("message" -> "Cart updated"))
              case false => NotFound(Json.obj("error" -> "Cart not found"))
            }
        )
  }

  def deleteCart(id: Long): Action[AnyContent] = Action.async {
    cartModel.delete(id).map {
      case true  => Ok(Json.obj("message" -> "Cart deleted"))
      case false => NotFound(Json.obj("error" -> "Cart not found"))
    }
  }
}
