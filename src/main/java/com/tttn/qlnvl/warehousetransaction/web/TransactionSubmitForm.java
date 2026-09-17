package com.tttn.qlnvl.warehousetransaction.web;

import jakarta.validation.constraints.Size;

public class TransactionSubmitForm {
    @Size(max = 1000, message = "Ghi chú thực hiện tối đa 1000 ký tự.")
    private String executionNote;

    public TransactionSubmitForm() {
    }

    public TransactionSubmitForm(String executionNote) {
        this.executionNote = executionNote;
    }

    public String getExecutionNote() {
        return executionNote;
    }

    public void setExecutionNote(String executionNote) {
        this.executionNote = executionNote;
    }
}
