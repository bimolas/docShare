package com.subject77.docshare.config.security;

import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Custom HandlerMethodArgumentResolver that automatically resolves @CurrentUser
 * parameters in @RestController endpoints to either UserEntity or Jwt.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectProvider<UserService> userServiceProvider;

    public CurrentUserArgumentResolver(ObjectProvider<UserService> userServiceProvider) {
        this.userServiceProvider = userServiceProvider;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        Class<?> paramType = parameter.getParameterType();

        if (Jwt.class.isAssignableFrom(paramType)) {
            return jwt;
        }

        if (UserEntity.class.isAssignableFrom(paramType)) {
            UserService userService = userServiceProvider.getIfAvailable();
            if (userService != null) {
                return userService.getOrCreateCurrentUser(jwt);
            }
        }

        return null;
    }
}
