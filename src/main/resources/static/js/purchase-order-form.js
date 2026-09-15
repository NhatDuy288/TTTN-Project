(function () {
    const body = document.querySelector("#po-items tbody");
    const addButton = document.querySelector("#add-item");
    if (!body || !addButton) return;

    function reindex() {
        body.querySelectorAll(".po-item-row").forEach(function (row, index) {
            row.querySelectorAll("[name]").forEach(function (field) {
                field.name = field.name.replace(/items\[\d+\]/, "items[" + index + "]");
                field.id = field.name.replace(/[\[\].]/g, "_");
            });
        });
    }

    addButton.addEventListener("click", function () {
        const source = body.querySelector(".po-item-row");
        if (!source) return;
        const row = source.cloneNode(true);
        row.querySelectorAll("input, select").forEach(function (field) {
            field.value = "";
        });
        row.querySelectorAll(".field-error").forEach(function (error) {
            error.remove();
        });
        body.appendChild(row);
        reindex();
    });

    body.addEventListener("click", function (event) {
        if (!event.target.classList.contains("remove-item")) return;
        const rows = body.querySelectorAll(".po-item-row");
        if (rows.length === 1) {
            rows[0].querySelectorAll("input, select").forEach(function (field) {
                field.value = "";
            });
        } else {
            event.target.closest(".po-item-row").remove();
            reindex();
        }
    });
}());
