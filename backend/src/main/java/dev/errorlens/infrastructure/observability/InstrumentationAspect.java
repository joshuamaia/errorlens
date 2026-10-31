package dev.errorlens.infrastructure.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Component;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.persistence.lab.LabRepository;

/**
 * Registra etapas reais de Controller, Service e Repository para as requisições de
 * laboratório instrumentadas. Fora de uma execução ativa o aspecto apenas delega.
 */
@Aspect
@Component
public class InstrumentationAspect {

    @Around("within(dev.errorlens.interfaces.lab..*) "
            + "&& @within(org.springframework.web.bind.annotation.RestController)")
    public Object controller(ProceedingJoinPoint pjp) throws Throwable {
        return record(pjp, StepLayer.CONTROLLER, pjp.getSignature().getDeclaringType().getSimpleName());
    }

    @Around("within(dev.errorlens.application.lab..*) && @within(org.springframework.stereotype.Service)")
    public Object service(ProceedingJoinPoint pjp) throws Throwable {
        return record(pjp, StepLayer.SERVICE, pjp.getSignature().getDeclaringType().getSimpleName());
    }

    @Around("this(dev.errorlens.infrastructure.persistence.lab.LabRepository)")
    public Object repository(ProceedingJoinPoint pjp) throws Throwable {
        return record(pjp, StepLayer.REPOSITORY, repositoryName(pjp));
    }

    private Object record(ProceedingJoinPoint pjp, StepLayer layer, String typeName) throws Throwable {
        if (!ExecutionContextHolder.isActive()) {
            return pjp.proceed();
        }
        String method = pjp.getSignature().getName();
        StepRecord step = ExecutionContextHolder.openStep(layer, typeName + "." + method,
                pjp.getSignature().getDeclaringTypeName() + "#" + method, null);
        try {
            Object result = pjp.proceed();
            ExecutionContextHolder.closeStep(step, StepStatus.OK, null, null);
            return result;
        } catch (Throwable ex) {
            ExecutionContextHolder.closeStep(step, StepStatus.ERROR,
                    "Exception propagada: " + ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    private static String repositoryName(ProceedingJoinPoint pjp) {
        for (Class<?> type : AopProxyUtils.proxiedUserInterfaces(pjp.getThis())) {
            if (LabRepository.class.isAssignableFrom(type) && type != LabRepository.class) {
                return type.getSimpleName();
            }
        }
        return AopProxyUtils.ultimateTargetClass(pjp.getThis()).getSimpleName();
    }
}
