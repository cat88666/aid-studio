package com.sakura.billing.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakura.billing.dto.BillingQuoteRequest;
import com.sakura.billing.service.BillingQuoteService;
import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.common.utils.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** C 端权威计费报价接口。 */
@RestController
@RequestMapping("/api/user/billing")
@RequiredArgsConstructor
public class BillingQuoteController extends BaseController
{
    private final BillingQuoteService billingQuoteService;

    @PostMapping("/quote")
    public AjaxResult quote(@Valid @RequestBody BillingQuoteRequest request)
    {
        return success(billingQuoteService.quote(request, SecurityUtils.getUserId()));
    }
}
