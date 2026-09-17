package com.tttn.qlnvl.warehousetransfer.web;

import jakarta.validation.constraints.Size;

public class TransferDecisionForm {
    @Size(max = 500, message = "Ý kiến duyệt tối đa 500 ký tự.")
    private String comment;

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
