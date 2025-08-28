package controllers.api

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import models.{ProductsModel, TransactionsModel}

import scala.concurrent.ExecutionContext

@Singleton
class TransactionsController @Inject() (
    cc: ControllerComponents,
    transactionsModel: TransactionsModel
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def listTransactions: Action[AnyContent] = Action.async {
    transactionsModel.list().map(trx => Ok(Json.toJson(trx)))
  }

  def getTransaction(id: Long): Action[AnyContent] = Action.async {
    transactionsModel.find(id).map {
      case Some(t) => Ok(Json.toJson(t))
      case None    => NotFound(Json.obj("error" -> "Transaction not found"))
    }
  }

  def insertTransaction(cartId: Long): Action[AnyContent] = Action.async {
    transactionsModel.insert(cartId).map {
      case Right(id) =>
        Created(Json.obj("message" -> "Transaction created", "id" -> id))
      case Left(err) =>
        BadRequest(Json.obj("error" -> err))
    }
  }
}
