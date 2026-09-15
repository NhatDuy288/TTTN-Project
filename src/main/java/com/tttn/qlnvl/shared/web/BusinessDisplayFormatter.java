package com.tttn.qlnvl.shared.web;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("businessDisplay")
public class BusinessDisplayFormatter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId businessZone;

    public BusinessDisplayFormatter(
            @Value("${app.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        this.businessZone = ZoneId.of(businessZone);
    }

    public String date(Instant value) {
        return value == null ? "-" : DATE_FORMAT.withZone(businessZone).format(value);
    }

    public String dateTime(Instant value) {
        return value == null ? "-" : DATE_TIME_FORMAT.withZone(businessZone).format(value);
    }

    public String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.####", symbols);
        format.setGroupingUsed(true);
        return format.format(value);
    }
}
