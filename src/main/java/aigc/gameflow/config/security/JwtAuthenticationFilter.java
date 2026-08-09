package aigc.gameflow.config.security;

import aigc.gameflow.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 这些路径不需要 Token，直接放行
    private static final String[] WHITELIST = {
            "/user/login",
            "/user/register",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // 检查是否是白名单路径，如果是，直接放行，不进行 JWT 验证
        String requestUri = request.getRequestURI();
        for (String path : WHITELIST) {
            if (path.equals("/**") || requestUri.equals(path) || 
                (path.endsWith("/**") && requestUri.startsWith(path.substring(0, path.length() - 3)))) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 1. 获取 Header
        String authHeader = request.getHeader("Authorization");

        // 2. 校验格式 "Bearer xxxxx"
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            // 如果没有 Token，直接放行 (让 Spring Security 去处理认证)
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        if (!JwtUtils.validate(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "登录已过期或令牌无效");
            return;
        }

        Long userId = JwtUtils.getUserId(token);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }
}
