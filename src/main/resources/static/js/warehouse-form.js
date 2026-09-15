(function () {
    const codeInput = document.querySelector("#warehouseCode");
    if (!codeInput) return;
    codeInput.addEventListener("input", function () {
        codeInput.value = codeInput.value.toUpperCase().replace(/^\s+/, "");
    });
    codeInput.addEventListener("blur", function () {
        codeInput.value = codeInput.value.trim();
    });
}());
