package com.alenwifidata.core.device.radius;

import com.alenwifidata.core.device.model.RouterDevice;

/**
 * RADIUS 请求 DTO
 */
public class RadiusRequest {

    private String userName;
    private String password;
    private String nasIpAddress;
    private String nasIdentifier;
    private String calledStationId;
    private String callingStationId;   // MAC 地址
    private String framedIpAddress;
    private Long acctInputOctets;      // 上行字节
    private Long acctOutputOctets;     // 下行字节
    private Long acctSessionTime;      // 会话时长(秒)
    private String acctStatusType;     // Start/Stop/Interim-Update
    private String acctSessionId;

    // Getters and setters
    public String getUserName() { return userName; }
    public void setUserName(String v) { this.userName = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getNasIpAddress() { return nasIpAddress; }
    public void setNasIpAddress(String v) { this.nasIpAddress = v; }
    public String getNasIdentifier() { return nasIdentifier; }
    public void setNasIdentifier(String v) { this.nasIdentifier = v; }
    public String getCalledStationId() { return calledStationId; }
    public void setCalledStationId(String v) { this.calledStationId = v; }
    public String getCallingStationId() { return callingStationId; }
    public void setCallingStationId(String v) { this.callingStationId = v; }
    public String getFramedIpAddress() { return framedIpAddress; }
    public void setFramedIpAddress(String v) { this.framedIpAddress = v; }
    public Long getAcctInputOctets() { return acctInputOctets; }
    public void setAcctInputOctets(Long v) { this.acctInputOctets = v; }
    public Long getAcctOutputOctets() { return acctOutputOctets; }
    public void setAcctOutputOctets(Long v) { this.acctOutputOctets = v; }
    public Long getAcctSessionTime() { return acctSessionTime; }
    public void setAcctSessionTime(Long v) { this.acctSessionTime = v; }
    public String getAcctStatusType() { return acctStatusType; }
    public void setAcctStatusType(String v) { this.acctStatusType = v; }
    public String getAcctSessionId() { return acctSessionId; }
    public void setAcctSessionId(String v) { this.acctSessionId = v; }

    /** 计费状态类型常量 */
    public static final String ACCT_START = "Start";
    public static final String ACCT_STOP = "Stop";
    public static final String ACCT_INTERIM = "Interim-Update";
}
