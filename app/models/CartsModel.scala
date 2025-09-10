package models

import anorm._
import anorm.SqlParser._
import javax.inject.Inject
import play.api.db.DBApi

import scala.concurrent.{ExecutionContext, Future}

// --- Data case class ---

case class Cart(
    id: Long,
    userId: Long,
    price: BigDecimal,
    status: String = "Active"
)
case class CartItem(
    id: Long,
    cartId: Long,
    farmItemsId: Long,
    qty: Int,
    unitPrice: BigDecimal,
    totalPrice: BigDecimal
)

case class CartWithItems(
    id: Long,
    userId: Long,
    price: BigDecimal,
    status: String = "Active",
    items: Seq[CartItem]
)

// --- JSON Formatters ---
import play.api.libs.json._
object CartFormat {
  implicit val cartItemFmt: OFormat[CartItem] = Json.format[CartItem]

  // request insert/update body
  case class CartReq(
      userId: Long,
      status: String = "Active",
      items: Seq[CartItemReq]
  )
  case class CartItemReq(farmItemsId: Long, qty: Int, unitPrice: BigDecimal)
  implicit val cartItemReqFmt: OFormat[CartItemReq] = Json.format[CartItemReq]
  implicit val cartReqFmt: OFormat[CartReq] = Json.format[CartReq]

  implicit val cartWithItemsFmt: OFormat[CartWithItems] =
    Json.format[CartWithItems]
}

class CartsModel @Inject() (dbApi: DBApi)(implicit ec: ExecutionContext) {

  private val db = dbApi.database("default")

  val cartParser: RowParser[Cart] = {
    get[Long]("id") ~
      get[Long]("user_id") ~
      get[BigDecimal]("price") ~
      get[String]("status") map { case id ~ userId ~ price ~ status =>
        Cart(id, userId, price, status)
      }
  }

  val itemParser: RowParser[CartItem] = {
    get[Long]("id") ~
      get[Long]("cart_id") ~
      get[Long]("farm_items_id") ~
      get[Int]("qty") ~
      get[BigDecimal]("unit_price") ~
      get[BigDecimal]("total_price") map {
        case id ~ cartId ~ farmItemsId ~ qty ~ unitPrice ~ totalPrice =>
          CartItem(id, cartId, farmItemsId, qty, unitPrice, totalPrice)
      }
  }

  def list(): Future[Seq[CartWithItems]] = Future {
    db.withConnection { implicit conn =>
      val carts = SQL"SELECT * FROM cart ORDER BY id ASC".as(cartParser.*)
      carts.map { c =>
        val items = SQL"SELECT * FROM cart_farm_items WHERE cart_id = ${c.id}"
          .as(itemParser.*)
        CartWithItems(c.id, c.userId, c.price, c.status, items)
      }
    }
  }

  def find(id: Long): Future[Option[CartWithItems]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT * FROM cart WHERE id = $id".as(cartParser.singleOpt).map { c =>
        val items = SQL"SELECT * FROM cart_farm_items WHERE cart_id = $id"
          .as(itemParser.*)
        CartWithItems(c.id, c.userId, c.price, c.status, items)
      }
    }
  }

//  def insert(req: CartFormat.CartReq): Future[Long] = Future {
//    db.withConnection { implicit conn =>
//      val totalPrice: BigDecimal = req.items.map(x => x.unitPrice * x.qty).sum
//
//      val cartId: Long =
//        SQL"""
//          INSERT INTO cart (user_id, price)
//          VALUES (${req.userId}, $totalPrice)
//        """.executeInsert(scalar[Long].single)
//
//      req.items.foreach { it =>
//        val itemTotal = it.unitPrice * it.qty
//        SQL"""
//            INSERT INTO cart_farm_items (cart_id, farm_items_id, qty, unit_price, total_price)
//            VALUES ($cartId, ${it.farmItemsId}, ${it.qty}, ${it.unitPrice}, $itemTotal)
//         """.executeInsert()
//      }
//      cartId
//    }
//  }
  def insert(req: CartFormat.CartReq): Future[Long] = Future {
    db.withConnection { implicit conn =>
      // Hitung total price berdasarkan harga asli farm_items
      val itemsWithPrice = req.items.map { it =>
        val farmItemPrice =
          SQL"""
            SELECT price FROM farm_items WHERE id = ${it.farmItemsId}
          """.as(scalar[BigDecimal].single)

        val totalItem = farmItemPrice * it.qty
        (it.farmItemsId, it.qty, farmItemPrice, totalItem)
      }

      val totalPrice: BigDecimal = itemsWithPrice.map(_._4).sum

      // Insert cart
      val cartId: Long =
        SQL"""
            INSERT INTO cart (user_id, price)
            VALUES (${req.userId}, $totalPrice)
          """.executeInsert(scalar[Long].single)

      // Insert items ke cart_farm_items
      itemsWithPrice.foreach { case (farmItemsId, qty, unitPrice, totalItem) =>
        SQL"""
            INSERT INTO cart_farm_items (cart_id, farm_items_id, qty, unit_price, total_price)
            VALUES ($cartId, $farmItemsId, $qty, $unitPrice, $totalItem)
          """.executeInsert()
      }

      cartId
    }
  }

  def update(id: Long, req: CartFormat.CartReq): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      // hitung ulang total price
      val totalPrice: BigDecimal = req.items.map(x => x.unitPrice * x.qty).sum

      // update cart table
      val updated =
        SQL"UPDATE cart SET user_id = ${req.userId}, price = $totalPrice, status = ${req.status} WHERE id = $id"
          .executeUpdate() > 0

      if (updated) {
        // hapus semua item lama
        SQL"DELETE FROM cart_farm_items WHERE cart_id = $id".executeUpdate()

        // insert item baru
        req.items.foreach { it =>
          val itemTotal = it.unitPrice * it.qty
          SQL"""
              INSERT INTO cart_farm_items (cart_id, farm_items_id, qty, unit_price, total_price)
              VALUES ($id, ${it.farmItemsId}, ${it.qty}, ${it.unitPrice}, $itemTotal)
           """.executeInsert()
        }
      }
      updated
    }
  }

  def delete(id: Long): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM cart_farm_items WHERE cart_id = $id".executeUpdate()
      SQL"DELETE FROM cart WHERE id = $id".executeUpdate() > 0
    }
  }
}
