package com.metal.service;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.metal.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿里云短信服务 — 发送验证码（仅已注册用户）
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${sms.access-key-id:}")
    private String accessKeyId;

    @Value("${sms.access-key-secret:}")
    private String accessKeySecret;

    @Value("${sms.sign-name:深圳市昊昱精密机电}")
    private String signName;

    @Value("${sms.template-code:SMS_510625124}")
    private String templateCode;

    /** 万能验证码（应急通道）：短信账户不可用时允许固定验证码登录；配置为空则关闭 */
    @Value("${sms.master-code:}")
    private String masterCode;

    @Autowired
    private SysUserMapper sysUserMapper;

    private final Map<String, CodeEntry> codeCache = new ConcurrentHashMap<>();
    private static final long CODE_TTL_MS = 5 * 60 * 1000;

    /**
     * 发送验证码 — 仅已注册的手机号才会真正发送
     * 未注册的手机号静默返回 true，但不发送短信
     */
    public boolean sendCode(String phoneNumber) {
        // 只给已注册用户发验证码
        if (sysUserMapper.findByUsername(phoneNumber) == null) {
            log.info("手机号未注册，跳过发送: {}", phoneNumber);
            return true; // 不暴露用户是否存在
        }
        try {
            String code = String.format("%06d", new Random().nextInt(1000000));

            DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
            IAcsClient client = new DefaultAcsClient(profile);

            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain("dysmsapi.aliyuncs.com");
            request.setSysVersion("2017-05-25");
            request.setSysAction("SendSms");
            request.putQueryParameter("PhoneNumbers", phoneNumber);
            request.putQueryParameter("SignName", signName);
            request.putQueryParameter("TemplateCode", templateCode);
            request.putQueryParameter("TemplateParam", "{\"code\":\"" + code + "\"}");

            CommonResponse response = client.getCommonResponse(request);

            if (response.getHttpStatus() == 200 && response.getData() != null
                    && response.getData().contains("\"Code\":\"OK\"")) {
                codeCache.put(phoneNumber, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL_MS));
                log.info("验证码已发送: {}, code={}", phoneNumber, code);
                return true;
            } else {
                log.error("验证码发送失败: {} => {}", phoneNumber, response.getData());
                return false;
            }
        } catch (Exception e) {
            log.error("验证码发送异常", e);
            return false;
        }
    }

    public boolean verifyCode(String phoneNumber, String code) {
        // 万能验证码（应急通道）：配置 sms.master-code 后生效，仅已注册手机号可登录（未注册在 smsLogin 阶段被拦截）
        if (masterCode != null && !masterCode.isBlank() && masterCode.equals(code)) {
            log.warn("万能验证码登录: {}", phoneNumber);
            return true;
        }
        CodeEntry entry = codeCache.get(phoneNumber);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expireTime) {
            codeCache.remove(phoneNumber);
            return false;
        }
        if (!entry.code.equals(code)) return false;
        codeCache.remove(phoneNumber);
        return true;
    }

    private record CodeEntry(String code, long expireTime) {}
}
