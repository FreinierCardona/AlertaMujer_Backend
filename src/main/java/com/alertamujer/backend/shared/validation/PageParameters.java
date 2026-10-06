package com.alertamujer.backend.shared.validation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Reusable query parameters for paginated REST endpoints. Controllers should
 * receive it as {@code @Valid PageParameters} before calling a service.
 */
public class PageParameters {

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(50)
    private int size = 20;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
