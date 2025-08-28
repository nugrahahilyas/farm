let currentPage = 1;
const limit = 10;

function fetchProducts() {
  $.get(`/api/products?page=${currentPage}&limit=${limit}`, function(data) {
    const products = data.data;
    const total = data.total;
    const totalPages = Math.ceil(total / limit);

    let rows = '';
    if (products.length === 0) {
      rows = '<tr><td colspan="6" class="text-center">No products available.</td></tr>';
    } else {
      products.forEach(product => {
        rows += `
          <tr>
            <td>${product.id}</td>
            <td>${product.title}</td>
            <td>${product.distributor}</td>
            <td>Rp ${product.price}</td>
            <td>${product.stock}</td>
            <td>${product.farmItemType}</td>
          </tr>
        `;
      });
    }

    $('#productTableBody').html(rows);
    $('#pageInfo').text(`Page ${currentPage} of ${totalPages}`);
    $('#prevPage').prop('disabled', currentPage === 1);
    $('#nextPage').prop('disabled', currentPage >= totalPages);
  });
}

$('#prevPage').on('click', function() {
  if (currentPage > 1) {
    currentPage--;
    fetchProducts();
  }
});

$('#nextPage').on('click', function() {
  currentPage++;
  fetchProducts();
});

function fetchProductsByType() {
  $.get('/api/products-by-type', function(data) {
    let html = '';
    data.forEach(type => {
      html += `
        <div class="mb-4">
          <h4>${type.typeName} ${type.description ? `- ${type.description}` : ''}</h4>
          ${type.items ? `
          <table class="table table-bordered mt-2">
            <thead class="table-light">
              <tr>
                <th>ID</th>
                <th>Title</th>
                <th>Distributor</th>
                <th>Price</th>
                <th>Stock</th>
              </tr>
            </thead>
            <tbody>
              ${type.items.map(item => `
                <tr>
                  <td>${item.id}</td>
                  <td>${item.title}</td>
                  <td>${item.distributor}</td>
                  <td>Rp ${item.price}</td>
                  <td>${item.stock}</td>
                </tr>`).join('')}
            </tbody>
          </table>
          ` : '<p class="text-muted">Tidak ada produk.</p>'}
        </div>
      `;
    });
    $('#typeContainer').html(html);
  });
}

$(document).ready(function() {
  fetchProducts();
  $('#type-tab').on('shown.bs.tab', function () {
    fetchProductsByType();
  });
});
