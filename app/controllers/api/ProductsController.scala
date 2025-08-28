package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import models.ProductsModel
import scala.concurrent.ExecutionContext

@Singleton
class ProductsController @Inject() (
    cc: ControllerComponents,
    productsModel: ProductsModel
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def getProductsByType: Action[AnyContent] = Action.async {
    productsModel.getTypesWithItems().map { data =>
      Ok(Json.toJson(data))
    }
  }

  def getProductsByTypeId(id: Long): Action[AnyContent] = Action.async {
    for {
      types <- productsModel.getAllTypes()
      items <- productsModel.getItemsByType(id)
    } yield {
      types.find(_.typeId == id) match {
        case Some(t) =>
          Ok(
            Json.toJson(
              t.copy(items = if (items.nonEmpty) Some(items) else None)
            )
          )
        case None => NotFound(Json.obj("error" -> "Type not found"))
      }
    }
  }

  def getListProduct(page: Int): Action[AnyContent] = Action.async {
    implicit request =>
      val limit = request.getQueryString("limit").getOrElse("10").toInt
      val search = request.getQueryString("search").getOrElse("")
      val typeId = request.getQueryString("typeId").map(_.toLong)

      productsModel.getListProduct(page, limit, search, typeId).map {
        case (list, total) =>
          Ok(
            Json.obj(
              "data" -> list,
              "page" -> page,
              "limit" -> limit,
              "total" -> total
            )
          )
      }
  }

  def createProduct: Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      val title = (request.body \ "title").as[String]
      val typeId = (request.body \ "typeId").as[Long]
      val distributor = (request.body \ "distributor").as[String]
      val description = (request.body \ "description").asOpt[String]
      val price = (request.body \ "price").asOpt[BigDecimal]
      val imageUrl = (request.body \ "imageUrl").asOpt[String]
      val stock = (request.body \ "stock").as[Int]

      productsModel
        .createProduct(
          title,
          typeId,
          distributor,
          description,
          price,
          imageUrl,
          stock
        )
        .map { id =>
          Created(Json.obj("message" -> "Product created", "id" -> id))
        }
  }

  def updateProduct(id: Long): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      val title = (request.body \ "title").asOpt[String]
      val typeId = (request.body \ "typeId").asOpt[Long]
      val distributor = (request.body \ "distributor").asOpt[String]
      val description = (request.body \ "description").asOpt[String]
      val price = (request.body \ "price").asOpt[BigDecimal]
      val imageUrl = (request.body \ "imageUrl").asOpt[String]
      val stock = (request.body \ "stock").asOpt[Int]

      productsModel
        .updateProduct(
          id,
          title,
          typeId,
          distributor,
          description,
          price,
          imageUrl,
          stock
        )
        .map { updated =>
          if (updated) Ok(Json.obj("message" -> "Product updated"))
          else NotFound(Json.obj("error" -> "Product not found"))
        }
  }

  def deleteProduct(id: Long): Action[AnyContent] = Action.async {
    productsModel.deleteProduct(id).map { deleted =>
      if (deleted) Ok(Json.obj("message" -> "Product deleted"))
      else NotFound(Json.obj("error" -> "Product not found"))
    }
  }

}
