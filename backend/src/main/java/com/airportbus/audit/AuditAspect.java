package com.airportbus.audit;

import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditAspect {
    private final AuditService audit;

    public AuditAspect(AuditService audit) {
        this.audit = audit;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed(); // 仅成功后记录
        JwtPrincipal me = CurrentUser.require(); // actor 来自上下文,绝不信请求体(E10)
        audit.record(me.userId(), audited.action(), audited.target(), sourceIdArg(pjp), null, clientIp());
        return result;
    }

    private static String sourceIdArg(ProceedingJoinPoint pjp) {
        String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                if ("sourceId".equals(names[i]) && args[i] != null) return args[i].toString();
            }
        }
        return null;
    }

    private static String clientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            String xff = sra.getRequest().getHeader("X-Forwarded-For");
            return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : sra.getRequest().getRemoteAddr();
        }
        return null;
    }
}
