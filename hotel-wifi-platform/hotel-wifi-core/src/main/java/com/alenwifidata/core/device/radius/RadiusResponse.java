package com.alenwifidata.core.device.radius;

import com.alenwifidata.core.device.model.RouterDevice;

/**
 * RADIUS 适配响应 DTO
 */
public class RadiusResponse {

    public static RadiusResponse accept() {
        RadiusResponse resp = new RadiusResponse();
        resp.accessAccept = true;
        return resp;
    }

    public static RadiusResponse reject(String reason) {
        RadiusResponse resp = new RadiusResponse();
        resp.accessAccept = false;
        resp.rejectReason = reason;
        return resp;
    }

    public static RadiusResponse accountingOk() {
        RadiusResponse resp = new RadiusResponse();
        resp.accountingOk = true;
        return resp;
    }

    private boolean accessAccept = true;
    private boolean accountingOk = true;
    private String rejectReason;

    public boolean isAccessAccept() { return accessAccept; }
    public boolean isAccountingOk() { return accountingOk; }
    public String getRejectReason() { return rejectReason; }
}
