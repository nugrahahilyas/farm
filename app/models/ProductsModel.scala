package models

import anorm._
import anorm.SqlParser._
import play.api.libs.json._
import javax.inject.Inject
import play.api.db.DBApi
import scala.concurrent.{ExecutionContext, Future}

// ===== Case Class =====
object ProductByItems {
  implicit val productByItemsFormat: OFormat[ProductByItems] =
    Json.format[ProductByItems]
}
case class ProductByItems(
    id: Long = -1,
    title: String,
    distributor: String,
    description: Option[String] = None,
    price: BigDecimal,
    imageUrl: Option[String] = None,
    stock: Int,
    farmItemType: String
)

object ProductByType {
  implicit val productByTypeFormat: OFormat[ProductByType] =
    Json.format[ProductByType]
}
case class ProductByType(
    typeId: Long = -1,
    typeName: String,
    description: Option[String] = None,
    items: Option[Seq[ProductByItems]] = None
)

// ===== Model Class =====
class ProductsModel @Inject() (dbApi: DBApi)(implicit ec: ExecutionContext) {
  private val db = dbApi.database("default")

  val productByItemsParser: RowParser[ProductByItems] = {
    get[Long]("id") ~
      get[String]("title") ~
      get[String]("distributor") ~
      get[Option[String]]("description") ~
      get[BigDecimal]("price") ~
      get[Option[String]]("image_url") ~
      get[Int]("stock") ~
      get[String]("type_name") map {
        case id ~ title ~ distributor ~ description ~ price ~ imageUrl ~ stock ~ typeName =>
          ProductByItems(
            id,
            title,
            distributor,
            description,
            price,
            imageUrl,
            stock,
            typeName
          )
      }
  }

  val productByTypeParser: RowParser[ProductByType] = {
    get[Long]("id") ~
      get[String]("name") ~
      get[Option[String]]("description") map {
        case typeId ~ typeName ~ description =>
          ProductByType(typeId, typeName, description, None)
      }
  }

  /** Get all types */
  def getAllTypes(): Future[Seq[ProductByType]] = Future {
    db.withConnection { implicit conn =>
      SQL"SELECT id, name, description FROM farm_item_type"
        .as(productByTypeParser.*)
    }
  }

  /** Get items by typeId */
  def getItemsByType(typeId: Long): Future[Seq[ProductByItems]] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        SELECT fi.id, fi.title, fi.distributor, fi.description, fi.price, fi.image_url, fi.stock, fit.name AS type_name
        FROM farm_items fi
        JOIN farm_item_type fit ON fi.farm_item_type_id = fit.id
        WHERE fi.farm_item_type_id = $typeId
      """.as(productByItemsParser.*)
    }
  }

  /** Get types with items */
  def getTypesWithItems(): Future[Seq[ProductByType]] = {
    getAllTypes().flatMap { types =>
      val futures = types.map { t =>
        getItemsByType(t.typeId).map { items =>
          t.copy(items = if (items.nonEmpty) Some(items) else None)
        }
      }
      Future.sequence(futures)
    }
  }

  def getListProduct(
      page: Int,
      limit: Int,
      search: String,
      typeId: Option[Long]
  ): Future[(Seq[ProductByItems], Long)] = Future {
    db.withConnection { implicit conn =>
      val offset = if (page > 0) (page - 1) * limit else 0

      var where = " WHERE TRUE "
      if (search.nonEmpty) {
        where += s" AND fi.title ILIKE '%$search%'"
      }
      if (typeId.isDefined) {
        where += s" AND fi.farm_item_type_id = ${typeId.get}"
      }

      val sqlQuery =
        s"""
            SELECT fi.id, fi.title, fi.distributor, fi.description, fi.price, fi.image_url, fi.stock,
                   fit.name AS type_name
            FROM farm_items fi
            JOIN farm_item_type fit ON fi.farm_item_type_id = fit.id
            $where
            ORDER BY fi.id DESC
            LIMIT $limit OFFSET $offset
          """

      val sqlCount =
        s"""
            SELECT COUNT(*) FROM farm_items fi
            JOIN farm_item_type fit ON fi.farm_item_type_id = fit.id
            $where
          """

      val total = SQL(sqlCount).as(scalar[Long].single)
      val list = SQL(sqlQuery).as(productByItemsParser.*)

      (list, total)
    }
  }

  /** Create new product */
  def createProduct(
      title: String,
      typeId: Long,
      distributor: String,
      description: Option[String],
      price: Option[BigDecimal],
      imageUrl: Option[String],
      stock: Int
  ): Future[Long] = Future {
    db.withConnection { implicit conn =>
      SQL"""
        INSERT INTO farm_items (title, farm_item_type_id, distributor, description, price, image_url, stock)
        VALUES ($title, $typeId, $distributor, $description, $price, $imageUrl, $stock)
      """.executeInsert(scalar[Long].single)
    }
  }

  /** Update product */
  def updateProduct(
      id: Long,
      title: Option[String],
      typeId: Option[Long],
      distributor: Option[String],
      description: Option[String],
      price: Option[BigDecimal],
      imageUrl: Option[String],
      stock: Option[Int]
  ): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      val setParts = Seq(
        title.map(v => s"title = '$v'"),
        typeId.map(v => s"farm_item_type_id = $v"),
        distributor.map(v => s"distributor = '$v'"),
        description.map(v => s"description = '$v'"),
        price.map(v => s"price = $v"),
        imageUrl.map(v => s"image_url = '$v'"),
        stock.map(v => s"stock = $v")
      ).flatten

      if (setParts.nonEmpty) {
        val setClause = setParts.mkString(", ")
        val query = s"UPDATE farm_items SET $setClause WHERE id = $id"
        SQL(query).executeUpdate() > 0
      } else {
        false
      }
    }
  }

  /** Delete product */
  def deleteProduct(id: Long): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL"DELETE FROM farm_items WHERE id = $id".executeUpdate() > 0
    }
  }

}
