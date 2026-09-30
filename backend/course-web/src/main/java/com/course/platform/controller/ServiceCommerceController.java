package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.application.service.servicecommerce.ServiceCommerceService;
import com.course.platform.common.result.Result;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.*;
import com.course.platform.domain.servicecommerce.ServiceOrderFilter;
import com.course.platform.domain.vo.plugin.PluginSchoolPage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class ServiceCommerceController {
    private final ServiceCommerceService service;

    @GetMapping("/services")
    public ResponseEntity<Result<IPage<ProductView>>> products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String providerType) {
        return ok(
                providerType == null || providerType.isBlank()
                        ? service.products(page, pageSize, false)
                        : service.products(page, pageSize, false, providerType));
    }

    @PostMapping("/services/{id}/lookup")
    public ResponseEntity<Result<Lookup>> lookup(
            @PathVariable Long id,
            @RequestBody @NotNull @Size(max = 64)
                    Map<@Size(max = 40) String, @Size(max = 2048) String> fields) {
        return ok(service.lookup(id, fields));
    }

    @GetMapping("/services/{id}/schools")
    public ResponseEntity<Result<PluginSchoolPage>> schools(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "") String keyword) {
        return ok(service.schools(id, page, keyword));
    }

    @PostMapping("/services/{id}/quotes")
    public ResponseEntity<Result<QuoteView>> quote(
            @PathVariable Long id, @Valid @RequestBody OrderForm form) {
        return ok(service.quote(id, form));
    }

    @PostMapping("/service-fulfillment-material-drafts")
    public ResponseEntity<Result<MaterialDraftView>> materialDraft(
            @Valid @RequestBody MaterialDraftForm form) {
        return ok(service.createMaterialDraft(form));
    }

    @PostMapping("/service-orders/{id}/fulfillment/material-drafts")
    public ResponseEntity<Result<MaterialDraftView>> orderMaterialDraft(
            @PathVariable String id, @Valid @RequestBody OrderMaterialDraftForm form) {
        return ok(service.createOrderMaterialDraft(id, form));
    }

    @GetMapping("/service-orders")
    public ResponseEntity<Result<IPage<OrderView>>> orders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Valid @ModelAttribute ServiceOrderFilter filter) {
        return ok(filter.isEmpty() ? service.orders(page, pageSize, false)
                : service.orders(page, pageSize, false, filter));
    }

    @GetMapping("/service-orders/{id}")
    public ResponseEntity<Result<OrderView>> order(@PathVariable String id) {
        return ok(service.order(id));
    }

    @PostMapping("/service-orders/{id}/sync")
    public ResponseEntity<Result<OrderView>> sync(@PathVariable String id) {
        return ok(service.sync(id));
    }

    @PostMapping("/service-orders/{id}/fulfillment/materials")
    public ResponseEntity<Result<OrderView>> updateFulfillmentMaterials(
            @PathVariable String id, @Valid @RequestBody MaterialUpdateForm form) {
        return ok(service.updateFulfillmentMaterials(id, form));
    }

    @GetMapping("/service-orders/{id}/options")
    public ResponseEntity<Result<Lookup>> options(@PathVariable String id) {
        return ok(service.orderOptions(id));
    }

    @PostMapping("/service-orders/{id}/quotes")
    public ResponseEntity<Result<QuoteView>> action(
            @PathVariable String id, @Valid @RequestBody ActionForm form) {
        return ok(service.quoteAction(id, form));
    }

    @GetMapping("/service-orders/{id}/logs")
    public ResponseEntity<Result<RunLogPage>> logs(
            @PathVariable String id, @RequestParam(defaultValue = "1") int page) {
        return ok(service.logs(id, page));
    }

    @GetMapping("/service-orders/{id}/score-info")
    public ResponseEntity<Result<OrderText>> scoreInfo(@PathVariable String id) {
        return ok(service.scoreInfo(id));
    }

    @GetMapping("/service-orders/{id}/events")
    public ResponseEntity<Result<List<EventView>>> events(@PathVariable String id) {
        return ok(service.events(id));
    }

    @PostMapping("/service-order-operations/{id}/confirm")
    public ResponseEntity<Result<QuoteView>> confirm(@PathVariable String id) {
        return ok(service.confirm(id));
    }

    @GetMapping("/service-order-operations/{id}")
    public ResponseEntity<Result<QuoteView>> operation(@PathVariable String id) {
        return ok(service.operation(id));
    }

    @GetMapping("/admin/service-products")
    @PreAuthorize("hasAuthority('api-provider:update')")
    public ResponseEntity<Result<IPage<ProductView>>> adminProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ok(service.products(page, pageSize, true));
    }

    @PostMapping("/admin/service-products")
    @PreAuthorize("hasAuthority('api-provider:update')")
    public ResponseEntity<Result<ProductView>> create(@Valid @RequestBody ProductCommand form) {
        return ok(service.saveProduct(null, form));
    }

    @PutMapping("/admin/service-products/{id}")
    @PreAuthorize("hasAuthority('api-provider:update')")
    public ResponseEntity<Result<ProductView>> update(
            @PathVariable Long id, @Valid @RequestBody ProductCommand form) {
        return ok(service.saveProduct(id, form));
    }

    @GetMapping("/admin/service-orders")
    @PreAuthorize("hasAuthority('api-provider:update')")
    public ResponseEntity<Result<IPage<OrderView>>> adminOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @Valid @ModelAttribute ServiceOrderFilter filter) {
        return ok(filter.isEmpty() ? service.orders(page, pageSize, true)
                : service.orders(page, pageSize, true, filter));
    }

    @PostMapping("/admin/service-orders/{id}/fulfillment")
    @PreAuthorize("hasAuthority('service-order:fulfill')")
    public ResponseEntity<Result<OrderView>> manageFulfillment(
            @PathVariable String id, @Valid @RequestBody LocalFulfillmentForm form) {
        return ok(service.manageFulfillment(id, form));
    }

    @GetMapping("/admin/service-orders/{id}/fulfillment")
    @PreAuthorize("hasAuthority('service-order:fulfill')")
    public ResponseEntity<Result<FulfillmentAdminView>> fulfillmentDetails(@PathVariable String id) {
        return ok(service.fulfillmentAdmin(id));
    }

    @PostMapping("/admin/service-orders/{id}/fulfillment/verification")
    @PreAuthorize("hasAuthority('service-order:fulfill')")
    public ResponseEntity<Result<OrderView>> verifyFulfillment(
            @PathVariable String id, @Valid @RequestBody VerificationForm form) {
        return ok(service.verifyFulfillment(id, form));
    }

    @GetMapping("/admin/service-orders/{orderId}/fulfillment/assets/{assetId}")
    @PreAuthorize("hasAuthority('service-order:fulfill') and hasAuthority('service-order:biometric')")
    public ResponseEntity<byte[]> fulfillmentAsset(
            @PathVariable String orderId, @PathVariable String assetId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Content-Disposition", "inline; filename=qualification.png")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .body(service.fulfillmentAsset(orderId, assetId));
    }

    @GetMapping("/admin/service-order-operations/{id}")
    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    public ResponseEntity<Result<QuoteView>> adminOperation(@PathVariable String id) {
        return ok(service.adminOperation(id));
    }

    @PostMapping("/admin/service-order-operations/{id}/resolve")
    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    public ResponseEntity<Result<QuoteView>> resolve(
            @PathVariable String id, @Valid @RequestBody ResolveForm form) {
        return ok(service.resolve(id, form));
    }

    @GetMapping("/admin/service-orders/{id}/audit")
    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    public ResponseEntity<Result<OrderAuditView>> audit(@PathVariable String id) {
        return ok(service.audit(id));
    }

    @PostMapping("/admin/service-orders/{id}/refund-quotes")
    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    public ResponseEntity<Result<QuoteView>> refundQuote(
            @PathVariable String id, @Valid @RequestBody RefundSettlementForm form) {
        return ok(service.quoteRefundSettlement(id, form));
    }

    @PostMapping("/admin/service-order-operations/{id}/settle-refund")
    @PreAuthorize("hasAuthority('api-provider:update') and hasAuthority('payment:reconcile')")
    public ResponseEntity<Result<QuoteView>> settleRefund(@PathVariable String id) {
        return ok(service.confirmRefundSettlement(id));
    }

    // Account/password inputs must never be interpolated into parser exception logs.
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> malformedBody() {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(
                        Result.error(
                                com.course.platform.common.result.ResultCode.PARAM_ERROR.getCode(),
                                "请求体格式错误"));
    }

    private <T> ResponseEntity<Result<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.success(data));
    }
}
