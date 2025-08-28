package models

import anorm._
import anorm.SqlParser._
import play.api.libs.json._
import javax.inject.Inject
import play.api.db.DBApi
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime

case class Transaction(
    id: Long,
    cartId: Long,
    cartPrice: BigDecimal,
    totalPrice: BigDecimal,
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime
)

object Transaction {
  implicit val transactionFmt: OFormat[Transaction] = Json.format[Transaction]

  case class TransactionReq(
      cartId: Long,
      cartPrice: BigDecimal,
      deliveryServicePrice: BigDecimal
  )
  implicit val transactionReqFmt: OFormat[TransactionReq] =
    Json.format[TransactionReq]
}

class TransactionsModel @Inject() (dbApi: DBApi)(implicit
    ec: ExecutionContext
) {
  private val db = dbApi.database("default")

  val transactionParser: RowParser[Transaction] = {
    get[Long]("id") ~
      get[Long]("cart_id") ~
      get[BigDecimal]("cart_price") ~
      get[BigDecimal]("total_price") ~
      get[LocalDateTime]("created_at") ~
      get[LocalDateTime]("updated_at") map {
        case id ~ cartId ~ cartPrice ~ totalPrice ~ createdAt ~ updatedAt =>
          Transaction(id, cartId, cartPrice, totalPrice, createdAt, updatedAt)
      }
  }

  def list(): Future[Seq[Transaction]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM transactions ORDER BY id ASC".as(transactionParser.*)
    }
  }

  def find(id: Long): Future[Option[Transaction]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM transactions WHERE id = $id".as(
        transactionParser.singleOpt
      )
    }
  }

  /** Insert new transaction:
    * - cek stok farm_items
    * - kurangi stok
    * - insert ke transactions
    * - update cart status jadi 'Ordered'
    */
  def insert(cartId: Long): Future[Either[String, Long]] = Future {
    db.withTransaction { implicit conn =>
      // 1. ambil cart
      val cartOpt =
        SQL"SELECT id, price, status FROM cart WHERE id = $cartId"
          .as(
            (SqlParser.get[Long]("id") ~
              SqlParser.get[BigDecimal]("price") ~
              SqlParser.get[String]("status")).map { case id ~ price ~ status =>
              (id, price, status)
            }.singleOpt
          )

      cartOpt match {
        case None =>
          Left("Cart not found")

        case Some((_, _, status)) if status == "Ordered" =>
          Left("Cart already ordered")

        case Some((id, cartPrice, _)) =>
          // 2. ambil item2 dari cart
          val items =
            SQL"""
            SELECT farm_items_id, qty, unit_price, total_price
            FROM cart_farm_items
            WHERE cart_id = $cartId
          """.as(
              (long("farm_items_id") ~
                int("qty") ~
                get[BigDecimal]("unit_price") ~
                get[BigDecimal]("total_price")).map {
                case farmId ~ qty ~ unitPrice ~ totalPrice =>
                  (farmId, qty, unitPrice, totalPrice)
              }.*
            )

          if (items.isEmpty) {
            Left("Cart has no items")
          } else {
            // 3. cek stock
            val stockError: Option[String] = items.collectFirst {
              case (farmId, qty, _, _) =>
                val stockOpt =
                  SQL"SELECT stock FROM farm_items WHERE id = $farmId"
                    .as(SqlParser.int("stock").singleOpt)

                stockOpt match {
                  case None => Some(s"Farm item $farmId not found")
                  case Some(stock) if stock < qty =>
                    Some(s"Not enough stock for farm item $farmId")
                  case _ => None
                }
            }.flatten

            stockError match {
              case Some(err) => Left(err)

              case None =>
                // 4. kurangi stok farm_items
                items.foreach { case (farmId, qty, _, _) =>
                  SQL"""
                  UPDATE farm_items
                  SET stock = stock - $qty
                  WHERE id = $farmId
                """.executeUpdate()
                }

                // 5. insert ke transactions
                val trxId: Option[Long] = SQL"""
                INSERT INTO transactions (cart_id, cart_price, total_price)
                VALUES ($cartId, $cartPrice, $cartPrice)
              """.executeInsert(scalar[Long].singleOpt)

                // 6. update status cart
                SQL"""
                UPDATE cart
                SET status = 'Ordered', updated_at = CURRENT_TIMESTAMP
                WHERE id = $cartId
              """.executeUpdate()

                Right(trxId.get)
            }
          }
      }
    }
  }

}
