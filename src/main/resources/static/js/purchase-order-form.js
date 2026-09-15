(function () {
    const poCodeInput = document.querySelector("#poCode");
    if (poCodeInput) {
        poCodeInput.addEventListener("input", function () {
            poCodeInput.value = poCodeInput.value.toUpperCase().replace(/^\s+/, "");
        });
        poCodeInput.addEventListener("blur", function () {
            poCodeInput.value = poCodeInput.value.trim();
        });
    }

    const body = document.querySelector("#po-items tbody");
    const addButton = document.querySelector("#add-item");
    const groupSelect = document.querySelector("#materialGroupId");
    const priceHint = document.querySelector("#price-rule-hint");
    if (!body || !addButton || !groupSelect) return;

    function refreshMaterialOptions(clearRows) {
        const groupId = groupSelect.value;
        body.querySelectorAll(".po-item-row").forEach(function (row) {
            const materialSelect = row.querySelector(".material-select");
            if (clearRows) {
                row.querySelectorAll("input, select.material-select").forEach(function (field) {
                    field.value = "";
                });
            }
            materialSelect.querySelectorAll("option[data-group-id]").forEach(function (option) {
                const belongsToGroup = option.dataset.groupId === groupId;
                option.hidden = !belongsToGroup;
                option.disabled = !belongsToGroup;
            });
            if (materialSelect.selectedOptions.length
                    && materialSelect.selectedOptions[0].dataset.groupId
                    && materialSelect.selectedOptions[0].dataset.groupId !== groupId) {
                materialSelect.value = "";
            }
            materialSelect.disabled = !groupId;
        });

        const selectedGroup = groupSelect.selectedOptions[0];
        if (!groupId) {
            priceHint.textContent = "Chọn nhóm để lọc danh sách vật tư.";
        } else if (selectedGroup.dataset.requiresAccounting === "true") {
            priceHint.textContent = "Nhóm này yêu cầu hạch toán: đơn giá bắt buộc và phải lớn hơn 0.";
        } else {
            priceHint.textContent = "Nhóm này không yêu cầu hạch toán: đơn giá có thể để trống.";
        }
    }

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
        refreshMaterialOptions(false);
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

    groupSelect.addEventListener("change", function () {
        refreshMaterialOptions(true);
    });

    refreshMaterialOptions(false);
}());
