package com.chatssl.anytime.common;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** 임시 구현: X-User-Id 헤더, 없으면 데모유저(1). 7단계에서 JWT로 교체한다. */
@Component
public class CurrentUserIdResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-User-Id";
    private static final long DEMO_USER_ID = 1L;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
            && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            return DEMO_USER_ID;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
