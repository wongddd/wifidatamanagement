package com.alenwifidata.core.notification.service;

import com.alenwifidata.core.member.mapper.MemberMapper;
import com.alenwifidata.core.member.model.Member;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireNotifyScheduler {

    private final MemberMapper memberMapper;
    private final WhatsAppService whatsappService;

    @Scheduled(cron = "0 0 10 * * ?")
    public void checkExpiringMembers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysLater = now.plusDays(7);

        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Member::getStatus, 1)
               .isNotNull(Member::getExpireAt)
               .between(Member::getExpireAt, now, sevenDaysLater);

        List<Member> expiringMembers = memberMapper.selectList(wrapper);
        for (Member member : expiringMembers) {
            long daysLeft = java.time.Duration.between(now, member.getExpireAt()).toDays();
            if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1 || daysLeft == 0) {
                String msg = String.format("*WiFi管家* 尊敬的%s，您的上网套餐将于%d天后到期，请及时续费。",
                        member.getRealName() != null ? member.getRealName() : member.getUsername(), daysLeft);
                whatsappService.send(member.getPhone(), msg);
            }
        }
        log.info("到期提醒检查完成，共{}个会员即将到期", expiringMembers.size());
    }
}
