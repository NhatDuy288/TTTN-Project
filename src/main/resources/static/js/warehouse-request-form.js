(function () {
    const operationSelect = document.querySelector("#operationTypeId");
    const reasonSelect = document.querySelector("#reasonId");
    const sourceField = document.querySelector("#source-warehouse-field");
    const destinationField = document.querySelector("#destination-warehouse-field");
    const sourceSelect = document.querySelector("#sourceWarehouseId");
    const destinationSelect = document.querySelector("#destinationWarehouseId");
    const poField = document.querySelector("#purchase-order-field");
    const poSelect = document.querySelector("#purchaseOrderId");
    const poHint = document.querySelector("#po-rule-hint");
    const note = document.querySelector("#note");
    const noteHint = document.querySelector("#note-rule-hint");
    const body = document.querySelector("#request-details tbody");
    const addButton = document.querySelector("#add-request-detail");
    if (!operationSelect || !reasonSelect || !body || !addButton) return;

    function selectedOperation() {
        const option = operationSelect.selectedOptions[0];
        return option && option.value ? option : null;
    }

    function csvSet(value) {
        return new Set((value || "").split(",").filter(Boolean));
    }

    function showField(wrapper, input, visible) {
        wrapper.hidden = !visible;
        input.disabled = !visible;
        if (!visible) input.value = "";
    }

    function filterOptions(select, predicate, clearInvalid) {
        Array.from(select.options).forEach(function (option) {
            if (!option.value) return;
            const visible = predicate(option);
            option.hidden = !visible;
            option.disabled = !visible;
        });
        const selected = select.selectedOptions[0];
        if (clearInvalid && selected && selected.value && selected.disabled) select.value = "";
    }

    function refreshRow(row, clearDependent) {
        const operation = selectedOperation();
        const allowedGroups = csvSet(operation && operation.dataset.groupIds);
        const allowedConditions = csvSet(operation && operation.dataset.conditions);
        const groupSelect = row.querySelector(".group-select");
        const materialSelect = row.querySelector(".request-material-select");
        const conditionSelect = row.querySelector(".condition-select");
        const poItemSelect = row.querySelector(".po-item-select");
        if (clearDependent) {
            materialSelect.value = "";
            conditionSelect.value = "";
            poItemSelect.value = "";
        }
        filterOptions(groupSelect, option => allowedGroups.has(option.value), true);
        groupSelect.disabled = !operation;
        const groupId = groupSelect.value;
        const poId = poSelect && !poSelect.disabled ? poSelect.value : (poSelect ? poSelect.value : "");
        const poMaterialIds = new Set(Array.from(poItemSelect.options)
                .filter(option => option.dataset.poId === poId)
                .map(option => option.dataset.materialId));
        filterOptions(materialSelect, option => option.dataset.groupId === groupId
                && (!poId || poMaterialIds.has(option.value)), true);
        materialSelect.disabled = !operation || !groupId;
        filterOptions(conditionSelect, option => allowedConditions.has(option.value), true);
        conditionSelect.disabled = !operation;
        const materialId = materialSelect.value;
        filterOptions(poItemSelect, option => option.dataset.poId === poId
                && option.dataset.materialId === materialId, true);
        poItemSelect.disabled = !poId || !materialId;
    }

    function refreshReason() {
        const operation = selectedOperation();
        const direction = operation ? operation.dataset.direction : "";
        filterOptions(reasonSelect, option => option.dataset.direction === direction, true);
        reasonSelect.disabled = !direction;
        const reason = reasonSelect.selectedOptions[0];
        const requiresNote = Boolean(reason && reason.dataset.requiresNote === "true");
        note.required = requiresNote;
        noteHint.textContent = requiresNote
                ? "Bắt buộc với lý do đã chọn; tối đa 300 ký tự."
                : "Tối đa 300 ký tự.";
    }

    function refreshOperation(clearValues) {
        const operation = selectedOperation();
        const direction = operation ? operation.dataset.direction : "";
        if (clearValues) {
            reasonSelect.value = "";
            sourceSelect.value = "";
            destinationSelect.value = "";
            if (poSelect && !poSelect.disabled) poSelect.value = "";
            body.querySelectorAll(".request-detail-row").forEach(function (row) {
                row.querySelectorAll("input, select").forEach(field => field.value = "");
            });
        }
        showField(sourceField, sourceSelect, direction === "EXPORT" || direction === "TRANSFER");
        showField(destinationField, destinationSelect, direction === "IMPORT" || direction === "TRANSFER");
        const showPo = direction === "IMPORT";
        poField.hidden = !showPo;
        if (poSelect) {
            if (!showPo) poSelect.value = "";
            if (!poSelect.hasAttribute("data-edit-locked")) poSelect.disabled = !showPo;
            poSelect.required = showPo && operation && operation.dataset.requiresPo === "true";
        }
        poHint.textContent = operation && operation.dataset.requiresPo === "true"
                ? "Bắt buộc chọn đúng một PO; PO không thể đổi sau lần lưu đầu tiên."
                : "Không bắt buộc tham chiếu PO; nếu chọn thì PO không thể đổi sau lần lưu đầu tiên.";
        refreshReason();
        body.querySelectorAll(".request-detail-row").forEach(row => refreshRow(row, false));
    }

    function reindex() {
        body.querySelectorAll(".request-detail-row").forEach(function (row, index) {
            row.querySelectorAll("[name]").forEach(function (field) {
                field.name = field.name.replace(/details\[\d+\]/, "details[" + index + "]");
                field.id = field.name.replace(/[\[\].]/g, "_");
            });
        });
    }

    operationSelect.addEventListener("change", () => refreshOperation(true));
    reasonSelect.addEventListener("change", refreshReason);
    if (poSelect) poSelect.addEventListener("change", function () {
        body.querySelectorAll(".request-detail-row").forEach(row => refreshRow(row, true));
    });
    body.addEventListener("change", function (event) {
        const row = event.target.closest(".request-detail-row");
        if (!row) return;
        if (event.target.classList.contains("group-select")) refreshRow(row, true);
        if (event.target.classList.contains("request-material-select")) refreshRow(row, false);
    });
    addButton.addEventListener("click", function () {
        const source = body.querySelector(".request-detail-row");
        if (!source) return;
        const row = source.cloneNode(true);
        row.querySelectorAll("input, select").forEach(field => field.value = "");
        row.querySelectorAll(".field-error").forEach(error => error.remove());
        body.appendChild(row);
        reindex();
        refreshRow(row, false);
    });
    body.addEventListener("click", function (event) {
        if (!event.target.classList.contains("remove-request-detail")) return;
        const rows = body.querySelectorAll(".request-detail-row");
        if (rows.length === 1) {
            rows[0].querySelectorAll("input, select").forEach(field => field.value = "");
            refreshRow(rows[0], false);
        } else {
            event.target.closest(".request-detail-row").remove();
            reindex();
        }
    });

    if (poSelect && poSelect.disabled) poSelect.setAttribute("data-edit-locked", "true");
    refreshOperation(false);
}());
