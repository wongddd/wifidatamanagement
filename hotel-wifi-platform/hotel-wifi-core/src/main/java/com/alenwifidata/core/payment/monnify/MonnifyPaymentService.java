package com.alenwifidata.core.payment.monnify;

import com.alenwifidata.common.exception.BusinessException;
import com.alenwifidata.core.billing.mapper.BillingOrderMapper;
import com.alenwifidata.core.billing.model.BillingOrder;
import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.alenwifidata.core.billingpackage.mapper.BillingPackageMapper;
import com.alenwifidata.core.billingpackage.model.BillingPackage;
import com.alenwifidata.core.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Monnify 支付服务 — 初始化交易、状态查询、Webhook 处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonnifyPaymentService {

    private final MonnifyClient monnify;
    private final BillingOrderMapper orderMapper;
    private final MemberMapper memberMapper;
    private final BillingPackageMapper packageMapper;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 创建 Monnify 支付订单
     *
     * @return { orderNo, transactionReference, checkoutUrl, amount }
     */
    public Map<String, Object> createOrder(Long memberId, Long packageId,
                                            String customerName, String customerEmail) {
        if (!monnify.isConfigured()) {
            throw new BusinessException(503, "在线支付暂未开放，请使用余额支付");
        }

        // 1. 查套餐和会员
        BillingPackage pkg = packageMapper.selectById(packageId);
        if (pkg == null || pkg.getStatus() != 1) {
            throw new BusinessException(400, "套餐不存在或已停用");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null || member.getStatus() != 1) {
            throw new BusinessException(400, "会员不存在或已停用");
        }

        // 2. 生成本地订单号
        String orderNo = "HW" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        BillingOrder order = new BillingOrder();
        order.setOrderNo(orderNo);
        order.setTenantId(TenantContext.get());
        order.setHotelId(member.getHotelId());
        order.setMemberId(memberId);
        order.setPackageId(packageId);
        order.setAmount(pkg.getPrice());
        order.setPayType("MONNIFY");
        order.setStatus("PENDING");
        orderMapper.insert(order);

        // 3. 调用 Monnify 初始化交易
        Map<String, Object> txBody = new HashMap<>();
        txBody.put("amount", pkg.getPrice().doubleValue());
        txBody.put("customerName", customerName != null ? customerName : member.getRealName());
        txBody.put("customerEmail", customerEmail != null ? customerEmail : (member.getUsername() + "@hotel.com"));
        txBody.put("paymentReference", orderNo);
        txBody.put("paymentDescription", pkg.getPackageName());
        txBody.put("currencyCode", "NGN");
        txBody.put("contractCode", monnify.getContractCode());
        txBody.put("redirectUrl", monnify.getBaseUrl() + "/checkout");
        txBody.put("paymentMethods", Arrays.asList("CARD", "ACCOUNT_TRANSFER", "PHONE_NUMBER"));
        txBody.put("metaData", Map.of("memberId", memberId, "packageId", packageId, "orderNo", orderNo));

        try {
            String json = mapper.writeValueAsString(txBody);
            JsonNode resp = monnify.post("/api/v1/merchant/transactions/init-transaction", json);

            if (!resp.get("requestSuccessful").asBoolean()) {
                throw new BusinessException(400, "支付初始化失败: " + resp.get("responseMessage").asText());
            }

            JsonNode body = resp.get("responseBody");

            Map<String, Object> result = new HashMap<>();
            result.put("orderNo", orderNo);
            result.put("transactionReference", body.get("transactionReference").asText());
            result.put("checkoutUrl", body.get("checkoutUrl").asText());
            result.put("amount", pkg.getPrice());
            result.put("totalPayable", body.get("totalPayable").asDouble());

            log.info("Monnify订单创建: orderNo={}, amount={}, txRef={}",
                    orderNo, pkg.getPrice(), body.get("transactionReference").asText());

            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Monnify支付请求失败", e);
            throw new BusinessException(500, "支付服务暂时不可用");
        }
    }

    /**
     * 查询交易状态（二次确认）
     */
    public Map<String, String> verifyTransaction(String transactionReference) {
        try {
            JsonNode resp = monnify.get("/api/v2/transactions/"
                    + java.net.URLEncoder.encode(transactionReference, "UTF-8"));

            if (!resp.get("requestSuccessful").asBoolean()) {
                return Map.of("status", "UNKNOWN");
            }

            JsonNode body = resp.get("responseBody");
            return Map.of(
                    "status", body.get("paymentStatus").asText(),
                    "amount", body.get("amountPaid").asText(),
                    "reference", body.get("paymentReference").asText()
            );
        } catch (Exception e) {
            log.error("Monnify查询交易失败", e);
            return Map.of("status", "UNKNOWN");
        }
    }

    /**
     * Webhook 回调处理 — 支付成功
     */
    @Transactional
    public void handlePaymentWebhook(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : "";
            JsonNode eventData = root.get("eventData");

            if (eventData == null) {
                log.warn("Monnify回调无eventData");
                return;
            }

            String paymentRef = eventData.has("paymentReference")
                    ? eventData.get("paymentReference").asText() : "";
            String txRef = eventData.has("transactionReference")
                    ? eventData.get("transactionReference").asText() : "";
            String status = eventData.has("paymentStatus")
                    ? eventData.get("paymentStatus").asText() : "";

            log.info("Monnify回调: eventType={}, paymentRef={}, txRef={}, status={}",
                    eventType, paymentRef, txRef, status);

            // 只处理成功的交易
            if (!"PAID".equals(status) && !"SUCCESSFUL_TRANSACTION".equals(eventType)) {
                log.info("Monnify回调跳过: status={}, eventType={}", status, eventType);
                return;
            }

            // 查找本地订单
            BillingOrder order = orderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BillingOrder>()
                            .eq(BillingOrder::getOrderNo, paymentRef)
            );

            if (order == null) {
                log.error("Monnify回调订单不存在: {}", paymentRef);
                // 如果是 Portal 自助充值（packageId=0），自动创建订单
                return;
            }

            if ("PAID".equals(order.getStatus())) {
                log.info("订单已处理(去重): {}", paymentRef);
                return;
            }

            // 更新订单状态
            order.setStatus("PAID");
            order.setPaidAt(LocalDateTime.now());
            orderMapper.updateById(order);

            // 激活会员套餐
            Member member = memberMapper.selectById(order.getMemberId());
            if (member != null) {
                if (order.getPackageId() != null && order.getPackageId() > 0) {
                    BillingPackage pkg = packageMapper.selectById(order.getPackageId());
                    if (pkg != null) {
                        // 充值余额
                        member.setBalance(member.getBalance().add(order.getAmount()));
                        // 更新到期时间（包时套餐）
                        if ("TIME".equals(pkg.getBillingType()) || "HYBRID".equals(pkg.getBillingType())) {
                            LocalDateTime now = LocalDateTime.now();
                            LocalDateTime newExpire = member.getExpireAt() != null
                                    && member.getExpireAt().isAfter(now)
                                    ? member.getExpireAt().plusSeconds(pkg.getDurationSeconds())
                                    : now.plusSeconds(pkg.getDurationSeconds());
                            member.setExpireAt(newExpire);
                        }
                    }
                } else {
                    // 纯余额充值
                    member.setBalance(member.getBalance().add(order.getAmount()));
                }
                memberMapper.updateById(member);
                log.info("Monnify支付完成: memberId={}, 余额={}, 到期={}",
                        member.getId(), member.getBalance(), member.getExpireAt());
            }
        } catch (Exception e) {
            log.error("Monnify回调处理异常", e);
            throw new BusinessException(500, "回调处理失败");
        }
    }
}
