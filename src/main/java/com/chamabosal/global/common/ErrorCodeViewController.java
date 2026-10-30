package com.chamabosal.global.common;

import com.chamabosal.ChamabosalApplication;
import com.chamabosal.global.common.exception.ErrorCode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;

/** 프론트 공유용 에러코드 페이지. ErrorCode 구현 enum을 자동 수집하므로 별도 수정이 필요 없다. */
@Controller
public class ErrorCodeViewController {

    private final Map<String, List<ErrorCode>> groupedErrorCodes;
    private final int totalCount;

    public ErrorCodeViewController() {
        List<ErrorCode> errorCodes = scanErrorCodes();
        validateNoDuplicateCodes(errorCodes);
        this.groupedErrorCodes = errorCodes.stream()
                .sorted(Comparator.comparing(ErrorCode::getCode))
                .collect(Collectors.groupingBy(
                        errorCode -> extractDomain(errorCode.getCode()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        this.totalCount = errorCodes.size();
    }

    @GetMapping("/docs/error-codes")
    public String errorCodesPage(Model model) {
        model.addAttribute("groupedErrorCodes", groupedErrorCodes);
        model.addAttribute("totalCount", totalCount);
        return "docs/error-codes";
    }

    /** 애플리케이션 패키지 하위에서 ErrorCode를 구현한 enum의 상수를 모두 모은다. */
    private List<ErrorCode> scanErrorCodes() {
        // 상수별 본문이 있는 enum은 abstract로 컴파일되므로 기본 필터(구체 클래스만)를 완화한다.
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));

        return scanner.findCandidateComponents(ChamabosalApplication.class.getPackageName()).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(className -> ClassUtils.resolveClassName(className, getClass().getClassLoader()))
                .filter(Class::isEnum)
                .flatMap(type -> Arrays.stream(type.getEnumConstants()))
                .map(ErrorCode.class::cast)
                .toList();
    }

    /** 프론트가 code로 분기하므로 같은 code가 두 번 정의되면 기동 시점에 실패시킨다. */
    static void validateNoDuplicateCodes(List<ErrorCode> errorCodes) {
        List<String> duplicatedCodes = errorCodes.stream()
                .collect(Collectors.groupingBy(ErrorCode::getCode, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!duplicatedCodes.isEmpty()) {
            throw new IllegalStateException("중복된 에러 코드가 있습니다: " + duplicatedCodes);
        }
    }

    /** 네이밍 가이드(DOMAIN_nnn)의 접두사로 묶는다. 접두사가 없으면 UNCLASSIFIED. */
    private String extractDomain(String code) {
        int delimiterIndex = code.indexOf('_');
        if (delimiterIndex <= 0) {
            return "UNCLASSIFIED";
        }
        return code.substring(0, delimiterIndex);
    }
}
