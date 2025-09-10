/* global $ */
/* eslint-disable no-undef */

;(() => {
  const $ = window.$ || window.jQuery

  if (!$) {
    console.error("jQuery is not loaded")
    return
  }

  $(document).ready(() => {
    // Configuration
    const API_BASE_URL = $('#base_url').val();
    let isDemo = false
    let selectedProductForCart = null
    $.getJSON(`${API_BASE_URL}/api/types`, function (data) {
            $.each(data, function (i, item) {
                $("#product-farmItemType").append(
                    $("<option>", {
                        value: item.typeId,
                        text: item.typeName,
                        title: item.description
                    })
                );
            });
        });

    const demoData = {
      products: [],
      users: [],
      carts: []
    }

    // Demo data
//    const demoData = {
//      products: [
//        {
//          id: 1,
//          title: "Tomat Cherry",
//          distributor: "CV Sayuran",
//          description: "Tomat segar",
//          price: 25000,
//          stock: 150,
//          farmItemType: "Sayuran",
//        },
//        {
//          id: 2,
//          title: "Beras Organik",
//          distributor: "PT Beras",
//          description: "Beras premium",
//          price: 45000,
//          stock: 200,
//          farmItemType: "Beras",
//        },
//        {
//          id: 3,
//          title: "Jagung Manis",
//          distributor: "Koperasi Tani",
//          description: "Jagung segar",
//          price: 18000,
//          stock: 80,
//          farmItemType: "Sayuran",
//        },
//      ],
//      users: [
//        { id: 1, name: "Andi Pratama", email: "andi@example.com", cityId: 1, address: "Jl. Merdeka No. 123" },
//        { id: 2, name: "Siti Aisyah", email: "siti@example.com", cityId: 2, address: "Jl. Sudirman No. 5" },
//        { id: 3, name: "Budi Santoso", email: "budi@example.com", cityId: 1, address: "Jl. Diponegoro No. 12" },
//      ],
//      carts: [
//        {
//          id: 1,
//          userId: 1,
//          price: 35000,
//          status: "Active",
//          items: [{ id: 1, farmItemsId: 1, qty: 2, unitPrice: 15000, totalPrice: 30000 }],
//        },
//        {
//          id: 2,
//          userId: 2,
//          price: 50000,
//          status: "Ordered",
//          items: [{ id: 2, farmItemsId: 2, qty: 1, unitPrice: 50000, totalPrice: 50000 }],
//        },
//      ],
//    }

    // Initialize
    init()

    function init() {
      setupNavigation()
      setupEventHandlers()
      loadData("products")
      loadUsersToSelect();
//      showDemoBadge()
    }

    function showDemoBadge() {
      if (!$(".demo-badge").length) {
        $("body").append(
          '<div style="position:fixed;top:20px;right:20px;background:#f59e0b;color:white;padding:8px 16px;border-radius:4px;font-size:12px;z-index:1000;">Demo Mode</div>',
        )
      }
    }

    function setupNavigation() {
      $(".nav-btn").click(function () {
        const page = $(this).data("page")
        $(".nav-btn").removeClass("active")
        $(this).addClass("active")
        $(".page").removeClass("active")
        $(`#${page}-page`).addClass("active")
        loadData(page)
      })
    }

    function setupEventHandlers() {
      // Add product form
      $("#product-form").submit((e) => {
        e.preventDefault()
        saveProduct(false)
      })

      // Edit product form
      $("#edit-product-form").submit((e) => {
        e.preventDefault()
        saveProduct(true)
      })

      // Cancel edit button
      $("#cancel-edit").click(() => {
        $("#edit-product-section").hide()
        $("#product-form")[0].reset()
      })

      // Add user button (still uses modal)
      $("#add-user-btn").click(() => openModal("user"))

      // Modal controls
      $(".modal-close").click(closeModal)
      $("#modal-save").click(saveItem)

      $("#add-to-cart-save").click(addToCart)
    }

    function loadData(entity) {
      showLoading()

      const endpoints = {
        products: "/api/products?page=1&limit=50",
        users: "/api/users",
        carts: "/api/carts",
      }

      $.ajax({
        url: API_BASE_URL +endpoints[entity],
        method: "GET",
        timeout: 3000,
      })
        .done((response) => {
          hideLoading()
          const data = response.data || response
          demoData[entity] = data
          displayData(entity, data)
        })
    }

    function displayData(entity, data) {
      const container = `#${entity}-table`
      $(container).empty()

      if (!data || data.length === 0) {
        $(container).html("<p>No data available</p>")
        return
      }

      const table = createTable(entity, data)
      $(container).html(table)

      $(container)
        .find(".btn-edit")
        .click(function () {
          const id = $(this).data("id")
          editItem(entity, id)
        })

      $(container)
        .find(".btn-danger")
        .click(function () {
          const id = $(this).data("id")
          if (confirm("Are you sure you want to delete this item?")) {
            deleteItem(entity, id)
          }
        })

      $(container)
        .find(".btn-add-cart")
        .click(function () {
          const id = $(this).data("id")
          openAddToCartModal(id)
        })

      $(container)
        .find(".btn-order")
        .click(function () {
          const id = $(this).data("id")
          orderCart(id)
        })
    }

    function createTable(entity, data) {
      const headers = getTableHeaders(entity)
      const rows = data.map((item) => createTableRow(entity, item)).join("")

      return `
        <table class="table">
          <thead>
            <tr>${headers.map((h) => `<th>${h}</th>`).join("")}<th>Actions</th></tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      `
    }

    function getTableHeaders(entity) {
      const headerMap = {
        products: ["ID", "Title", "Distributor", "Price", "Stock", "Type"],
        users: ["ID", "Name", "Email", "City ID", "Address"],
        carts: ["ID", "User ID", "Price", "Status", "Items Count"],
      }
      return headerMap[entity] || []
    }

    function createTableRow(entity, item) {
      const cells = getTableCells(entity, item)
      let actions = ""

      if (entity === "products") {
        actions = `
          <td class="table-actions">
            <button class="btn-add-cart" data-id="${item.id}">Add to Cart</button>
            <button class="btn-edit" data-id="${item.id}">Edit</button>
            <button class="btn-danger" data-id="${item.id}">Delete</button>
          </td>
        `
      } else if (entity === "carts") {
        actions = `
          <td class="table-actions">
            <button class="btn-order" data-id="${item.id}">Order</button>
            <button class="btn-edit" data-id="${item.id}">Edit</button>
            <button class="btn-danger" data-id="${item.id}">Delete</button>
          </td>
        `
      } else {
        actions = `
          <td class="table-actions">
            <button class="btn-edit" data-id="${item.id}">Edit</button>
            <button class="btn-danger" data-id="${item.id}">Delete</button>
          </td>
        `
      }

      return `<tr>${cells}${actions}</tr>`
    }

    function loadUsersToSelect() {
      $.ajax({
        url: $('#base_url').val() + "/api/users",
        method: "GET",
        timeout: 3000,
      })
        .done((response) => {
          const users = response.data || response
          const $select = $("#selected-user-id")

          $select.empty()
          $select.append('<option value="">-- Select User --</option>')

          users.forEach((user) => {
            $select.append(
              `<option value="${user.id}">${user.name}</option>`
            )
          })
        })
        .fail(() => {
          alert("Failed to load users")
        })
    }


    function getTableCells(entity, item) {
      switch (entity) {
        case "products":
          return `
            <td>${item.id}</td>
            <td>${item.title}</td>
            <td>${item.distributor}</td>
            <td>Rp ${formatPrice(item.price)}</td>
            <td>${item.stock}</td>
            <td><span class="status-badge">${item.farmItemType}</span></td>
          `
        case "users":
          return `
            <td>${item.id}</td>
            <td>${item.name}</td>
            <td>${item.email}</td>
            <td>${item.cityId}</td>
            <td>${item.address}</td>
          `
        case "carts":
          return `
            <td>${item.id}</td>
            <td>${item.userId}</td>
            <td>Rp ${formatPrice(item.price)}</td>
            <td><span class="status-badge status-${item.status.toLowerCase()}">${item.status}</span></td>
            <td>${item.items ? item.items.length : 0}</td>
          `
        default:
          return "<td>-</td>"
      }
    }

    function openAddToCartModal(productId) {
      selectedProductForCart = productId

      const users = isDemo ? demoData.users : []
      const userIcons = users
        .map(
          (user) => `
        <div class="user-icon" data-user-id="${user.id}">
          <i class="fas fa-user"></i>
          <span>${user.name}</span>
        </div>
      `,
        )
        .join("")

      $("#user-selector").html(userIcons)

      $(".user-icon").click(function () {
        $(".user-icon").removeClass("selected")
        $(this).addClass("selected")
        $("#selected-user-id").val($(this).data("user-id"))
      })

      $("#add-to-cart-modal").addClass("active")
    }

    function addToCart() {
      const userId = $("#selected-user-id").val()
      const quantity = $("#cart-quantity").val()

      if (!userId || !quantity) {
        alert("Please select user and quantity")
        return
      }

      showLoading()

      if (isDemo) {
        setTimeout(() => {
          hideLoading()
          $("#add-to-cart-modal").removeClass("active")
          alert("Product added to cart successfully (Demo Mode)")
        }, 1000)
        return
      }

      // API call to add to cart
      $.ajax({
        url: `${API_BASE_URL}/api/carts`,
        method: "POST",
        contentType: "application/json",
        data: JSON.stringify({
          userId: Number.parseInt($('#selected-user-id').val()),
          items: [
            {
              farmItemsId: selectedProductForCart,
              qty: Number.parseInt(quantity),
              unitPrice: 0
            },
          ],
        }),
        timeout: 5000,
      })
        .done(() => {
          hideLoading()
          $("#add-to-cart-modal").removeClass("active")
          alert("Product added to cart successfully")
        })
        .fail(() => {
          hideLoading()
          alert("Failed to add product to cart")
        })
    }

    function orderCart(cartId) {
      if (!confirm("Are you sure you want to order this cart?")) return

      showLoading()

      if (isDemo) {
        setTimeout(() => {
          hideLoading()
          alert("Cart ordered successfully (Demo Mode)")
          loadData("carts")
        }, 1000)
        return
      }

      // API call to create transaction
      $.ajax({
        url: `${API_BASE_URL}/api/transactions`,
        method: "POST",
        contentType: "application/json",
        data: JSON.stringify({
          cartId: cartId,
        }),
        timeout: 5000,
      })
        .done(() => {
          hideLoading()
          alert("Cart ordered successfully")
          loadData("carts")
        })
        .fail(() => {
          hideLoading()
          alert("Failed to order cart")
        })
    }

    function openModal(entity, id = null) {
      const isEdit = id !== null
      $("#modal-title").text(`${isEdit ? "Edit" : "Add"} ${entity.charAt(0).toUpperCase() + entity.slice(1)}`)

      const form = createForm(entity, isEdit ? getItemById(entity, id) : null)
      $("#modal-body").html(form)
      $("#crud-modal").addClass("active")

      // Store current operation
      $("#crud-modal").data("entity", entity).data("id", id)
    }

    function createForm(entity, item = null) {
      const forms = {
        product: `
          <div class="form-group">
            <label>Title</label>
            <input type="text" id="form-title" value="${item?.title || ""}" required>
          </div>
          <div class="form-group">
            <label>Distributor</label>
            <input type="text" id="form-distributor" value="${item?.distributor || ""}" required>
          </div>
          <div class="form-group">
            <label>Description</label>
            <textarea id="form-description" required>${item?.description || ""}</textarea>
          </div>
          <div class="form-group">
            <label>Price</label>
            <input type="number" id="form-price" value="${item?.price || ""}" required>
          </div>
          <div class="form-group">
            <label>Stock</label>
            <input type="number" id="form-stock" value="${item?.stock || ""}" required>
          </div>
          <div class="form-group">
            <label>Type</label>
            <select id="form-type" required>
              <option value="Sayuran" ${item?.farmItemType === "Sayuran" ? "selected" : ""}>Sayuran</option>
              <option value="Buah" ${item?.farmItemType === "Buah" ? "selected" : ""}>Buah</option>
              <option value="Produk Olahan" ${item?.farmItemType === "Produk Olahan" ? "selected" : ""}>Produk Olahan</option>
              <option value="Beras" ${item?.farmItemType === "Beras" ? "selected" : ""}>Beras</option>
            </select>
          </div>
        `,
        user: `
          <div class="form-group">
            <label>Name</label>
            <input type="text" id="form-name" value="${item?.name || ""}" required>
          </div>
          <div class="form-group">
            <label>Email</label>
            <input type="email" id="form-email" value="${item?.email || ""}" required>
          </div>
          <div class="form-group">
            <label>City ID</label>
            <input type="number" id="form-cityId" value="${item?.cityId || ""}" required>
          </div>
          <div class="form-group">
            <label>Address</label>
            <textarea id="form-address" required>${item?.address || ""}</textarea>
          </div>
        `,
        cart: `
          <div class="form-group">
            <label>User ID</label>
            <input type="number" id="form-userId" value="${item?.userId || ""}" required>
          </div>
          <div class="form-group">
            <label>Status</label>
            <select id="form-status" required>
              <option value="Active" ${item?.status === "Active" ? "selected" : ""}>Active</option>
              <option value="Ordered" ${item?.status === "Ordered" ? "selected" : ""}>Ordered</option>
            </select>
          </div>
        `,
      }
      return forms[entity] || "<p>Form not available</p>"
    }

    function closeModal() {
      $("#crud-modal").removeClass("active")
      $("#add-to-cart-modal").removeClass("active")
      $("#modal-body").empty()
    }

    function saveItem() {
      const entity = $("#crud-modal").data("entity")
      const id = $("#crud-modal").data("id")
      const isEdit = id !== null

      const data = getFormData(entity)
      if (!data) return

      showLoading()

      if (isDemo) {
        setTimeout(() => {
          hideLoading()
          closeModal()
          alert(`${entity} ${isEdit ? "updated" : "created"} successfully (Demo Mode)`)
          loadData(entity)
        }, 1000)
        return
      }

      const url = isEdit ? `${API_BASE_URL}${entity}/${id}` : `${API_BASE_URL}/${entity}`
      const method = isEdit ? "PUT" : "POST"

      $.ajax({
        url: url,
        method: method,
        contentType: "application/json",
        data: JSON.stringify(data),
        timeout: 5000,
      })
        .done(() => {
          hideLoading()
          closeModal()
          alert(`${entity} ${isEdit ? "updated" : "created"} successfully`)
          loadData(entity)
        })
        .fail(() => {
          hideLoading()
          alert(`Failed to ${isEdit ? "update" : "create"} ${entity}`)
        })
    }

    function getFormData(entity) {
      switch (entity) {
        case "product":
          return {
            title: $("#form-title").val(),
            distributor: $("#form-distributor").val(),
            description: $("#form-description").val(),
            price: Number.parseInt($("#form-price").val()),
            stock: Number.parseInt($("#form-stock").val()),
            farmItemType: $("#form-type").val(),
          }
        case "user":
          return {
            name: $("#form-name").val(),
            email: $("#form-email").val(),
            cityId: Number.parseInt($("#form-cityId").val()),
            address: $("#form-address").val(),
          }
        case "cart":
          return {
            userId: Number.parseInt($("#form-userId").val()),
            status: $("#form-status").val(),
          }
        default:
          return null
      }
    }

    function editItem(entity, id) {
      if (entity === "products") {
        const item = getItemById(entity, id)
        if (item) {
          $("#edit-product-id").val(item.id)
          $("#edit-product-title").val(item.title)
          $("#edit-product-distributor").val(item.distributor)
          $("#edit-product-description").val(item.description)
          $("#edit-product-price").val(item.price)
          $("#edit-product-imageUrl").val(item.imageUrl || "")
          $("#edit-product-stock").val(item.stock)
          $("#edit-product-farmItemType").val(item.farmItemType)
          $("#edit-product-section").show()
        }
      } else {
        openModal(entity, id)
      }
    }

    function deleteItem(entity, id) {
      showLoading()

      if (isDemo) {
        setTimeout(() => {
          hideLoading()
          alert(`${entity} deleted successfully (Demo Mode)`)
          loadData(entity)
        }, 1000)
        return
      }

      $.ajax({
        url: `${API_BASE_URL}/api/${entity}/${id}`,
        method: "DELETE",
        timeout: 5000,
      })
        .done(() => {
          hideLoading()
          alert(`${entity} deleted successfully`)
          loadData(entity)
        })
        .fail(() => {
          hideLoading()
          alert(`Failed to delete ${entity}`)
        })
    }

    function getItemById(entity, id) {
      return demoData[entity]?.find((item) => item.id == id) || null
    }

    function formatPrice(price) {
      return new Intl.NumberFormat("id-ID").format(price)
    }

    function showLoading() {
      $("#loading").addClass("active")
    }

    function hideLoading() {
      $("#loading").removeClass("active")
    }

    function saveProduct(isEdit) {
      const data = {
        title: isEdit ? $("#edit-product-title").val() : $("#product-title").val(),
        distributor: isEdit ? $("#edit-product-distributor").val() : $("#product-distributor").val(),
        description: isEdit ? $("#edit-product-description").val() : $("#product-description").val(),
        price: Number.parseInt(isEdit ? $("#edit-product-price").val() : $("#product-price").val()),
        stock: Number.parseInt(isEdit ? $("#edit-product-stock").val() : $("#product-stock").val()),
        typeId: Number.parseInt(isEdit ? $("#edit-product-farmItemType").val() : $("#product-farmItemType").val())
      }

      showLoading()

      const url = isEdit ? `${API_BASE_URL}/api/products/${$("#edit-product-id").val()}` : `${API_BASE_URL}/api/products`
      const method = isEdit ? "PUT" : "POST"

      $.ajax({
        url: url,
        method: method,
        contentType: "application/json",
        data: JSON.stringify(data),
        timeout: 5000,
      })
        .done(() => {
          hideLoading()
          alert(`Product ${isEdit ? "updated" : "created"} successfully`)
          if (isEdit) {
            $("#edit-product-section").hide()
          }
          $("#product-form")[0].reset()
          $("#edit-product-form")[0].reset()
          loadData("products")
        })
        .fail(() => {
          hideLoading()
          alert(`Failed to ${isEdit ? "update" : "create"} product`)
        })
    }
  })
})()
