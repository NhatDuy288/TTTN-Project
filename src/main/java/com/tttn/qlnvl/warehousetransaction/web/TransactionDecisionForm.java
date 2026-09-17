package com.tttn.qlnvl.warehousetransaction.web;

import jakarta.validation.constraints.Size;

public class TransactionDecisionForm {
    @Size(max = 500, message = "Ý kiến duyệt tối đa 500 ký tự.")
    private String comment;

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
