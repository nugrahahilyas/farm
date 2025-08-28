package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class ProductsViewController @Inject() (cc: ControllerComponents)
    extends AbstractController(cc) {

  def index: Action[AnyContent] = Action {
    Ok(views.html.products("Products"))
  }
}
